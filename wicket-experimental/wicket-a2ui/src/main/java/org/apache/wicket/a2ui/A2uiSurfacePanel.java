/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.a2ui;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.feedback.FencedFeedbackPanel;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.util.lang.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders one A2UI surface as Wicket components, server-side. The agent's messages go into an
 * {@link A2uiSurfaces}; this panel renders the surface with the given id from it, and rebuilds its
 * components whenever the agent has changed the surface.
 * <p>
 * Inputs write the user's edits into the surface's data model when the user triggers an action.
 * The action reaches {@link #onAction(AjaxRequestTarget, A2uiActionMessage)}: send it to the
 * agent, feed the agent's answer to {@link A2uiSurfaces#process(CharSequence)}, and the panel
 * re-renders what the answer changed. Components the agent left alone keep their state, both on
 * the server and in the browser.
 *
 * <pre>
 * add(new A2uiSurfacePanel("surface", surfaces, "booking")
 * {
 * 	&#64;Override
 * 	protected void onAction(AjaxRequestTarget target, A2uiActionMessage action)
 * 	{
 * 		surfaces.getObject().process(agent.send(action.toJson()));
 * 	}
 * });
 * </pre>
 *
 * The panel knows the {@link BasicCatalog}; register other catalogs with
 * {@link #addCatalog(A2uiCatalog)}. Nothing the agent sends is written into the markup unescaped,
 * and URLs pass {@link #isUrlAllowed(String)} first.
 *
 * @since 12.0.0
 */
public class A2uiSurfacePanel extends Panel
{
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(A2uiSurfacePanel.class);

	private static final CssResourceReference CSS = new CssResourceReference(
		A2uiSurfacePanel.class, "a2ui.css");

	private static final Pattern HEX_COLOR = Pattern.compile(
		"#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

	private final IModel<A2uiSurfaces> surfaces;

	private final String surfaceId;

	private final Map<String, A2uiCatalog> catalogs = new LinkedHashMap<>();

	private final Map<String, Serializable> viewState = new HashMap<>();

	private Form<Void> form;

	private FencedFeedbackPanel feedback;

	private RenderedComponent root;

	private String renderedInstance;

	private long seenDataVersion;

	/**
	 * @param id
	 *            the component id
	 * @param surfaces
	 *            the surfaces of the conversation
	 * @param surfaceId
	 *            the id of the surface to render; the panel is invisible while it does not exist
	 */
	public A2uiSurfacePanel(String id, IModel<A2uiSurfaces> surfaces, String surfaceId)
	{
		super(id);
		this.surfaces = Args.notNull(surfaces, "surfaces");
		this.surfaceId = Args.notEmpty(surfaceId, "surfaceId");
		addCatalog(BasicCatalog.get());
		setOutputMarkupPlaceholderTag(true);
	}

	/**
	 * Registers a catalog that surfaces may name, replacing one with the same id.
	 *
	 * @param catalog
	 *            the catalog
	 * @return this
	 */
	public A2uiSurfacePanel addCatalog(A2uiCatalog catalog)
	{
		catalogs.put(catalog.getId(), catalog);
		root = null;
		return this;
	}

	/**
	 * @return the surface, or {@code null} when it does not exist
	 */
	public A2uiSurface getSurface()
	{
		A2uiSurfaces all = surfaces.getObject();
		return all == null ? null : all.get(surfaceId);
	}

	/**
	 * @return the id of the surface this panel renders
	 */
	public String getSurfaceId()
	{
		return surfaceId;
	}

	@Override
	protected void onInitialize()
	{
		super.onInitialize();
		form = new Form<>("form");
		feedback = new FencedFeedbackPanel("feedback", form);
		feedback.setOutputMarkupId(true);
		form.add(feedback);
		add(form);
	}

	@Override
	protected void onConfigure()
	{
		super.onConfigure();
		A2uiSurface surface = getSurface();
		setVisible(surface != null && surface.getComponent(A2uiSurface.ROOT_ID) != null);
	}

	@Override
	protected void onBeforeRender()
	{
		update(null);
		super.onBeforeRender();
	}

	/**
	 * Brings the rendered surface up to date with the agent's changes. Components whose definition
	 * changed, or whose templates repeat over an array that changed length, are built again; the
	 * others keep their state. Those rebuilt and those bound to data the agent changed are added
	 * to the request handler, so the rest of the surface keeps its markup in the browser.
	 * <p>
	 * After an action this happens by itself. Call it when the agent's messages arrive otherwise,
	 * for example pushed over a WebSocket.
	 *
	 * @param target
	 *            the handler to add the changed components to; may be {@code null} when the whole
	 *            page is rendered anyway
	 */
	public void update(IPartialPageRequestHandler target)
	{
		A2uiSurface surface = getSurface();
		if (surface == null || surface.getComponent(A2uiSurface.ROOT_ID) == null)
		{
			if (root != null)
			{
				root = null;
				addTo(target, this);
			}
			return;
		}
		if (root == null || surface.getInstanceKey().equals(renderedInstance) == false)
		{
			if (getCatalog(surface.getCatalogId()) == null)
			{
				LOG.warn("Surface {} uses catalog {}, which is not registered", surfaceId,
					surface.getCatalogId());
			}
			root = A2uiRenderContext.renderRoot(this, "root");
			form.addOrReplace(root.component);
			renderedInstance = surface.getInstanceKey();
			seenDataVersion = surface.getDataVersion();
			addTo(target, this);
			return;
		}
		List<String> changedPaths = surface.getDataChangesSince(seenDataVersion);
		seenDataVersion = surface.getDataVersion();
		List<Component> changed = new ArrayList<>();
		root = refresh(root, surface, changedPaths != null ? changedPaths : List.of("/"), changed);
		changed.forEach(component -> addTo(target, component));
	}

	/**
	 * Re-renders the feedback messages of the surface, e.g. after a failed validation.
	 *
	 * @param target
	 *            the current request handler
	 */
	public void updateFeedback(IPartialPageRequestHandler target)
	{
		target.add(feedback);
	}

	private RenderedComponent refresh(RenderedComponent rendered, A2uiSurface surface,
		List<String> changedPaths, List<Component> changed)
	{
		if (rendered.isOutdated(surface))
		{
			RenderedComponent fresh = A2uiRenderContext.rerender(this, rendered);
			fresh.component.setMarkupId(rendered.component.getMarkupId());
			rendered.component.replaceWith(fresh.component);
			changed.add(fresh.component);
			return fresh;
		}
		if (changedPaths.isEmpty() == false && rendered.isBoundToAny(changedPaths))
		{
			changed.add(rendered.component);
		}
		rendered.children.replaceAll(child -> refresh(child, surface, changedPaths, changed));
		return rendered;
	}

	private static void addTo(IPartialPageRequestHandler target, Component component)
	{
		if (target != null)
		{
			target.add(component);
		}
	}

	@Override
	protected void onComponentTag(ComponentTag tag)
	{
		super.onComponentTag(tag);
		tag.append("class", "a2ui-surface", " ");
		A2uiSurface surface = getSurface();
		if (surface == null)
		{
			return;
		}
		if (surface.getTheme().get("primaryColor") instanceof String color &&
			HEX_COLOR.matcher(color).matches())
		{
			tag.append("style", "--a2ui-primary: " + color, ";");
		}
		if (surface.getTheme().get("agentDisplayName") instanceof String agentName)
		{
			tag.put("aria-label", agentName);
		}
	}

	@Override
	public void renderHead(IHeaderResponse response)
	{
		super.renderHead(response);
		response.render(CssHeaderItem.forReference(CSS));
	}

	/**
	 * Called when the user triggers an action on the surface. Send the action to the agent here,
	 * and process its answer; what the answer changed is re-rendered afterwards.
	 *
	 * @param target
	 *            the current Ajax request
	 * @param action
	 *            the action, to send to the agent as {@link A2uiActionMessage#toJson()}
	 */
	protected void onAction(AjaxRequestTarget target, A2uiActionMessage action)
	{
	}

	/**
	 * Decides whether a URL the agent sent may be used as the source of an image, video or audio
	 * element. The browser fetches that URL as the user, so by default only absolute http and https
	 * URLs pass; override to restrict it further, for example to known hosts.
	 *
	 * @param url
	 *            the URL
	 * @return whether it may be used
	 */
	protected boolean isUrlAllowed(String url)
	{
		try
		{
			URI uri = new URI(url);
			return ("http".equalsIgnoreCase(uri.getScheme()) ||
				"https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
		}
		catch (URISyntaxException e)
		{
			return false;
		}
	}

	@Override
	protected void onDetach()
	{
		surfaces.detach();
		super.onDetach();
	}

	A2uiCatalog getCatalog(String catalogId)
	{
		return catalogs.get(catalogId);
	}

	void dispatch(AjaxRequestTarget target, A2uiActionMessage action)
	{
		onAction(target, action);
		update(target);
		target.add(feedback);
	}

	Serializable getViewState(String key)
	{
		return viewState.get(key);
	}

	void setViewState(String key, Serializable state)
	{
		viewState.put(key, state);
	}
}
