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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.io.IClusterable;
import org.apache.wicket.util.lang.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a {@link A2uiComponentFactory} needs while it builds one component: models for bound
 * values, Wicket components for the children it references, and a way to send the agent an event.
 * Inside a template the context carries the array element the component stands for, and relative
 * data bindings resolve against it.
 * <p>
 * The context also records what the component depends on, so that an update from the agent
 * rebuilds or re-renders only what it affects. Read the data model through {@link #model(A2uiValue)}
 * and reference children through this context; a value read while building is not refreshed.
 *
 * @since 12.0.0
 */
public final class A2uiRenderContext implements IClusterable
{
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(A2uiRenderContext.class);

	/** Deeper nesting than this is cut off, so an agent cannot make the page arbitrarily deep. */
	static final int MAX_DEPTH = 32;

	/** A template renders at most this many array elements. */
	static final int MAX_TEMPLATE_ITEMS = 500;

	private final A2uiSurfacePanel surfacePanel;

	private final String scope;

	private final List<String> ancestors;

	private final RenderedComponent rendered;

	private A2uiRenderContext(A2uiSurfacePanel surfacePanel, String scope, List<String> ancestors,
		RenderedComponent rendered)
	{
		this.surfacePanel = surfacePanel;
		this.scope = scope;
		this.ancestors = ancestors;
		this.rendered = rendered;
	}

	/**
	 * @return the panel rendering the surface
	 */
	public A2uiSurfacePanel getSurfacePanel()
	{
		return surfacePanel;
	}

	/**
	 * @return the surface
	 */
	public A2uiSurface getSurface()
	{
		return surfacePanel.getSurface();
	}

	/**
	 * @return the id of the component being built
	 */
	public String getComponentId()
	{
		return ancestors.get(ancestors.size() - 1);
	}

	/**
	 * @return the JSON Pointer of the array element this component renders inside a template, or
	 *         the empty string outside of one
	 */
	public String getScope()
	{
		return scope;
	}

	/**
	 * Creates the Wicket component for a referenced child. A reference to a component that does
	 * not exist (yet), that the catalog cannot render, or that would nest the component inside
	 * itself becomes an invisible placeholder, which is replaced once the agent sends something
	 * renderable.
	 *
	 * @param wicketId
	 *            the Wicket id
	 * @param child
	 *            the child reference; may be {@code null}
	 * @return the component
	 */
	public Component newComponent(String wicketId, A2uiComponentId child)
	{
		return child == null ? placeholder(wicketId) : newChild(wicketId, child.id(), scope);
	}

	/**
	 * Adds the children to a repeating view, instantiating a template once per array element.
	 *
	 * @param view
	 *            the view
	 * @param children
	 *            the children; may be {@code null}
	 */
	public void addChildren(RepeatingView view, A2uiChildren children)
	{
		if (children == null)
		{
			return;
		}
		if (children.isTemplate() == false)
		{
			for (String id : children.ids())
			{
				view.add(newChild(view.newChildId(), id, scope));
			}
			return;
		}
		String arrayPath = JsonPointer.resolve(scope, children.path());
		int length = RenderedComponent.templateLength(getSurface(), arrayPath);
		rendered.templateLengths.put(arrayPath, length);
		for (int i = 0; i < Math.min(length, MAX_TEMPLATE_ITEMS); i++)
		{
			view.add(newChild(view.newChildId(), children.componentId(), arrayPath + "/" + i));
		}
	}

	/**
	 * Creates a model for a value. When the value is bound to a path in the data model, the model
	 * reads the current value on every render, the component is re-rendered when the agent changes
	 * that part of the data model, and writing the model, as an input component does, updates the
	 * data model. A literal value cannot be written.
	 *
	 * @param <T>
	 *            the type of the value
	 * @param value
	 *            the value; may be {@code null}
	 * @return the model
	 */
	@SuppressWarnings("unchecked")
	public <T> IModel<T> model(A2uiValue<T> value)
	{
		if (value == null)
		{
			return () -> null;
		}
		String path = value.getPath();
		if (path != null)
		{
			rendered.boundPaths.add(JsonPointer.resolve(scope, path));
		}
		else if (ValueType.functionName(value.raw()) != null)
		{
			rendered.boundPaths.add("/");
		}
		return new IModel<>()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public T getObject()
			{
				return (T)value.type().read(resolve(value.raw()));
			}

			@Override
			public void setObject(T object)
			{
				if (path != null)
				{
					getSurface().getDataModel().set(JsonPointer.resolve(scope, path),
						value.type().write(object));
				}
			}
		};
	}

	/**
	 * Creates a model for a URL that only gives the URL when
	 * {@link A2uiSurfacePanel#isUrlAllowed(String)} accepts it.
	 *
	 * @param url
	 *            the URL; may be {@code null}
	 * @return the model, giving {@code null} for a missing or rejected URL
	 */
	public IModel<String> urlModel(A2uiValue<String> url)
	{
		IModel<String> model = model(url);
		return () -> {
			String value = model.getObject();
			return value != null && surfacePanel.isUrlAllowed(value) ? value : null;
		};
	}

	/**
	 * Sends the agent the event of an action the agent attached to this component, with its
	 * context resolved against the data model now.
	 *
	 * @param target
	 *            the current Ajax request
	 * @param action
	 *            the action; nothing happens when it is {@code null}
	 * @see A2uiSurfacePanel#onAction(AjaxRequestTarget, A2uiActionMessage)
	 */
	public void fireAction(AjaxRequestTarget target, A2uiAction action)
	{
		if (action == null)
		{
			return;
		}
		Map<String, Object> context = new LinkedHashMap<>();
		action.context().forEach((name, value) -> context.put(name, Json.toJava(Json.toJson(
			resolve(value)))));
		fire(target, action.getEventName(), context);
	}

	/**
	 * Sends the agent an event with values the component supplies itself, such as the id of the
	 * row the user clicked in a server-side table. Use it for components whose events the agent
	 * cannot describe in advance; prefer {@link #fireAction(AjaxRequestTarget, A2uiAction)} for an
	 * action the agent attached.
	 * <p>
	 * The values go to the agent: send identifiers and what the agent needs to act on, not data
	 * the user is allowed to see but the agent is not.
	 *
	 * @param target
	 *            the current Ajax request
	 * @param name
	 *            the event name, as documented to the agent in the component's description
	 * @param values
	 *            the event context: strings, numbers, booleans, {@code null}, and lists and maps
	 *            of these
	 * @throws IllegalArgumentException
	 *             when a value has no JSON representation
	 * @see A2uiSurfacePanel#onAction(AjaxRequestTarget, A2uiActionMessage)
	 */
	public void fireEvent(AjaxRequestTarget target, String name, Map<String, ?> values)
	{
		Args.notEmpty(name, "name");
		@SuppressWarnings("unchecked")
		Map<String, Object> context = (Map<String, Object>)Json.copyJsonValue(values != null
			? values : Map.of());
		fire(target, name, context);
	}

	/**
	 * Reads state the rendered component keeps for itself, such as the selected tab. It survives
	 * the agent's changes to the component, which rebuild it.
	 *
	 * @param name
	 *            the name of the state, unique within the component
	 * @return the state, or {@code null}
	 */
	public Serializable getViewState(String name)
	{
		return surfacePanel.getViewState(viewStateKey(name));
	}

	/**
	 * Stores state the rendered component keeps for itself.
	 *
	 * @param name
	 *            the name of the state, unique within the component
	 * @param state
	 *            the state
	 */
	public void setViewState(String name, Serializable state)
	{
		surfacePanel.setViewState(viewStateKey(name), state);
	}

	static RenderedComponent renderRoot(A2uiSurfacePanel surfacePanel, String wicketId)
	{
		return render(surfacePanel, wicketId, A2uiSurface.ROOT_ID, "", Collections.emptyList());
	}

	/**
	 * Builds a component again, at the same place and for the same array element as before.
	 */
	static RenderedComponent rerender(A2uiSurfacePanel surfacePanel, RenderedComponent previous)
	{
		return render(surfacePanel, previous.wicketId, previous.componentId, previous.scope,
			previous.ancestors);
	}

	private static RenderedComponent render(A2uiSurfacePanel surfacePanel, String wicketId,
		String componentId, String itemScope, List<String> ancestors)
	{
		A2uiSurface surface = surfacePanel.getSurface();
		A2uiComponentDefinition definition = surface.getComponent(componentId);
		RenderedComponent rendered = new RenderedComponent(componentId, itemScope, wicketId,
			ancestors, definition);
		Component component = null;
		if (definition != null)
		{
			if (ancestors.contains(componentId) || ancestors.size() >= MAX_DEPTH)
			{
				LOG.warn("Not rendering component {} of surface {}: it nests too deep or inside itself",
					componentId, surface.getId());
			}
			else
			{
				List<String> path = new ArrayList<>(ancestors);
				path.add(componentId);
				A2uiCatalog catalog = surfacePanel.getCatalog(surface.getCatalogId());
				if (catalog != null)
				{
					component = catalog.newComponent(wicketId, definition,
						new A2uiRenderContext(surfacePanel, itemScope, path, rendered));
				}
			}
		}
		if (component == null)
		{
			component = placeholder(wicketId);
		}
		else if (definition.property("weight") instanceof Number weight && weight.doubleValue() > 0)
		{
			component.add(AttributeModifier.append("style",
				"flex: " + Json.toDisplayString(weight.doubleValue()) + " 1 0%"));
		}
		component.setOutputMarkupPlaceholderTag(true);
		rendered.component = component;
		return rendered;
	}

	private Component newChild(String wicketId, String componentId, String itemScope)
	{
		RenderedComponent child = render(surfacePanel, wicketId, componentId, itemScope, ancestors);
		rendered.children.add(child);
		return child.component;
	}

	private void fire(AjaxRequestTarget target, String name, Map<String, Object> context)
	{
		A2uiSurface surface = getSurface();
		Object dataModel = surface.isSendDataModel() ? Json.toJava(surface.getDataModel().toJson())
			: null;
		surfacePanel.dispatch(target, new A2uiActionMessage(name, surface.getId(),
			getComponentId(), Instant.now(), context, dataModel));
	}

	private Object resolve(Object raw)
	{
		String function = ValueType.functionName(raw);
		if (function != null)
		{
			return call(function, ((Map<?, ?>)raw).get("args"));
		}
		String path = ValueType.bindingPath(raw);
		if (path != null)
		{
			return getSurface().getDataModel().get(JsonPointer.resolve(scope, path));
		}
		return raw instanceof Map ? null : raw;
	}

	private Object call(String function, Object args)
	{
		if ("formatString".equals(function) && args instanceof Map<?, ?> map &&
			map.get("value") instanceof String template)
		{
			return formatString(template);
		}
		LOG.debug("Unsupported function {}", function);
		return null;
	}

	/**
	 * Interpolates {@code ${pointer}} placeholders; {@code \${} is a literal {@code ${}.
	 * Placeholders that call functions are not supported and render empty.
	 */
	private String formatString(String template)
	{
		StringBuilder result = new StringBuilder();
		int i = 0;
		while (i < template.length())
		{
			if (template.startsWith("\\${", i))
			{
				result.append("${");
				i += 3;
			}
			else if (template.startsWith("${", i))
			{
				int end = template.indexOf('}', i + 2);
				if (end < 0)
				{
					result.append(template, i, template.length());
					break;
				}
				String expression = template.substring(i + 2, end).strip();
				if (expression.isEmpty() == false && expression.indexOf('(') < 0)
				{
					result.append(Json.toDisplayString(getSurface().getDataModel().get(
						JsonPointer.resolve(scope, expression))));
				}
				i = end + 1;
			}
			else
			{
				result.append(template.charAt(i++));
			}
		}
		return result.toString();
	}

	private String viewStateKey(String name)
	{
		return scope + "#" + getComponentId() + "#" + name;
	}

	private static Component placeholder(String wicketId)
	{
		return new WebMarkupContainer(wicketId).setVisible(false).setOutputMarkupPlaceholderTag(
			true);
	}
}
