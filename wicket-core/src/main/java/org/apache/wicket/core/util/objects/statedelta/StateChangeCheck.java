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
package org.apache.wicket.core.util.objects.statedelta;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.wicket.Component;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.core.request.handler.ListenerRequestHandler;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development check reporting components whose state changed during an Ajax request but that
 * were not added to the {@link AjaxRequestTarget}.
 * <p>
 * The page is hashed with a {@link ComponentStateHasher} twice: when the listener request handler
 * is resolved, and after the listener ran but before the Ajax response is rendered. A component
 * counts as repainted when it or one of its ancestors was rendered into the response, and a form
 * component counts as up to date when its input was part of the request.
 * <p>
 * The check is opt-in through {@link #install(WebApplication)} and refuses to install outside
 * development mode.
 */
public class StateChangeCheck implements IRequestCycleListener, AjaxRequestTarget.IListener
{
	private static final Logger log = LoggerFactory.getLogger(StateChangeCheck.class);

	private static final MetaDataKey<Map<StateChangeCheck, Result>> KEY = new MetaDataKey<>()
	{
		private static final long serialVersionUID = 1L;
	};

	private final ComponentStateHasher hasher;

	private Set<DependencyKind> followed = EnumSet.noneOf(DependencyKind.class);

	private Consumer<Result> reporter = StateChangeCheck::log;

	private Consumer<Result> versionReporter;

	/**
	 * @param hasher
	 *            how to hash the page
	 */
	public StateChangeCheck(ComponentStateHasher hasher)
	{
		this.hasher = hasher;
	}

	/**
	 * Installs a check with the {@link ComponentStateHasher#withDefaultExclusions() default
	 * exclusions}, in development mode only.
	 * 
	 * @param application
	 *            the application
	 * @return the installed check, or {@code null} outside development mode
	 */
	public static StateChangeCheck install(WebApplication application)
	{
		if (application.getConfigurationType() != RuntimeConfigurationType.DEVELOPMENT)
		{
			log.warn("{} is only installed in development mode", StateChangeCheck.class.getSimpleName());
			return null;
		}
		StateChangeCheck check = new StateChangeCheck(ComponentStateHasher.withDefaultExclusions());
		application.getRequestCycleListeners().add(check);
		application.getAjaxRequestTargetListeners().add(check);
		return check;
	}

	/**
	 * @param kinds
	 *            the dependencies through which a changed component also marks the components
	 *            reaching it
	 * @return this
	 */
	public StateChangeCheck follow(Set<DependencyKind> kinds)
	{
		this.followed = EnumSet.noneOf(DependencyKind.class);
		this.followed.addAll(kinds);
		return this;
	}

	/**
	 * @param reporter
	 *            receives the result of every checked Ajax request, instead of the log
	 * @return this
	 */
	public StateChangeCheck setReporter(Consumer<Result> reporter)
	{
		this.reporter = reporter;
		return this;
	}

	/**
	 * @param reporter
	 *            receives the result of every non-Ajax listener request, comparing the state
	 *            before and after the listener ran, or {@code null} to not check those
	 * @return this
	 */
	public StateChangeCheck setVersionReporter(Consumer<Result> reporter)
	{
		this.versionReporter = reporter;
		return this;
	}

	@Override
	public void onRequestHandlerResolved(RequestCycle cycle, IRequestHandler handler)
	{
		if (handler instanceof ListenerRequestHandler listener &&
			(isAjax(cycle) || versionReporter != null) && result(cycle) == null &&
			listener.getPage() instanceof Page page)
		{
			Map<StateChangeCheck, Result> results = cycle.getMetaData(KEY);
			if (results == null)
			{
				results = new IdentityHashMap<>();
				cycle.setMetaData(KEY, results);
			}
			results.put(this, new Result(page, hasher.snapshot(page), followed, handler,
				isAjax(cycle)));
		}
	}

	@Override
	public void onRequestHandlerExecuted(RequestCycle cycle, IRequestHandler handler)
	{
		Result result = result(cycle);
		if (result != null && !result.ajax && result.handler == handler && result.after == null)
		{
			result.after = hasher.snapshot(result.page);
		}
	}

	@Override
	public void onBeforeRespond(Map<String, Component> map, AjaxRequestTarget target)
	{
		Result result = result(RequestCycle.get());
		if (result != null && result.page == target.getPage())
		{
			result.after = hasher.snapshot(result.page);
		}
	}

	@Override
	public void onAfterRespond(Map<String, Component> map, AjaxRequestTarget target)
	{
		Result result = result(RequestCycle.get());
		if (result != null && result.after != null)
		{
			result.repainted(map.values(), RequestCycle.get().getRequest().getRequestParameters());
		}
	}

	@Override
	public void onDetach(RequestCycle cycle)
	{
		Result result = result(cycle);
		if (result == null || result.after == null)
		{
			return;
		}
		result.pageIdAfter = result.page.getPageId();
		if (!result.ajax)
		{
			versionReporter.accept(result);
		}
		else if (result.repainted != null)
		{
			reporter.accept(result);
		}
	}

	private Result result(RequestCycle cycle)
	{
		Map<StateChangeCheck, Result> results = cycle.getMetaData(KEY);
		return results == null ? null : results.get(this);
	}

	private static boolean isAjax(RequestCycle cycle)
	{
		return cycle.getRequest() instanceof WebRequest request && request.isAjax();
	}

	private static void log(Result result)
	{
		Set<String> missing = result.getMissing();
		if (!missing.isEmpty())
		{
			log.warn(
				"Ajax request on {} changed the state of components that were not added to the AjaxRequestTarget: {}",
				result.page.getClass().getName(), missing);
		}
	}

	/**
	 * The outcome of one checked Ajax request.
	 */
	public static final class Result
	{
		private final Page page;

		private final StateSnapshot before;

		private final Set<DependencyKind> followed;

		private final int pageIdBefore;

		private final IRequestHandler handler;

		private final boolean ajax;

		private int pageIdAfter;

		private StateSnapshot after;

		private Set<String> repainted;

		private Set<String> echoed;

		private Result(Page page, StateSnapshot before, Set<DependencyKind> followed,
			IRequestHandler handler, boolean ajax)
		{
			this.page = page;
			this.before = before;
			this.followed = followed;
			this.handler = handler;
			this.ajax = ajax;
			this.pageIdBefore = page.getPageId();
		}

		private void repainted(Collection<Component> components, IRequestParameters parameters)
		{
			repainted = new LinkedHashSet<>();
			for (Component component : components)
			{
				repainted.add(component.getPageRelativePath());
			}
			echoed = new LinkedHashSet<>();
			Set<String> names = parameters.getParameterNames();
			page.visitChildren(FormComponent.class, (component, visit) -> {
				if (names.contains(((FormComponent<?>)component).getInputName()))
				{
					echoed.add(component.getPageRelativePath());
				}
			});
		}

		/**
		 * @return the page
		 */
		public Page getPage()
		{
			return page;
		}

		/**
		 * @return the state at the start of the request
		 */
		public StateSnapshot getBefore()
		{
			return before;
		}

		/**
		 * @return whether this was an Ajax request
		 */
		public boolean isAjax()
		{
			return ajax;
		}

		/**
		 * @return the state before the response was rendered
		 */
		public StateSnapshot getAfter()
		{
			return after;
		}

		/**
		 * @return the difference between {@link #getBefore()} and {@link #getAfter()}
		 */
		public StateSnapshot.Delta getDelta()
		{
			return after.compareTo(before);
		}

		/**
		 * @return paths of the components rendered into the Ajax response
		 */
		public Set<String> getRepainted()
		{
			return Collections.unmodifiableSet(repainted);
		}

		/**
		 * @return paths of the form components whose input was part of the request
		 */
		public Set<String> getEchoed()
		{
			return Collections.unmodifiableSet(echoed);
		}

		/**
		 * @param path
		 *            a page relative path
		 * @return whether the component or one of its ancestors was rendered into the response
		 */
		public boolean isRepainted(String path)
		{
			return isRepainted(path, Set.of());
		}

		private boolean isRepainted(String path, Set<String> notRepainted)
		{
			if (repainted.contains("") || echoed.contains(path))
			{
				return true;
			}
			for (String p = path; !p.isEmpty(); p = parent(p))
			{
				if (repainted.contains(p) && !notRepainted.contains(p))
				{
					return true;
				}
			}
			return false;
		}

		/**
		 * What the check would have reported had some of the repainted components not been added.
		 * 
		 * @param kinds
		 *            the dependencies to follow
		 * @param notRepainted
		 *            paths of repainted components to treat as not added
		 * @return paths of affected components that were not repainted
		 */
		public Set<String> getMissing(Set<DependencyKind> kinds, Set<String> notRepainted)
		{
			Set<String> missing = new LinkedHashSet<>();
			for (String path : getDelta().getAffected(kinds))
			{
				if (!path.isEmpty() && !isRepainted(path, notRepainted))
				{
					missing.add(path);
				}
			}
			return missing;
		}

		/**
		 * @param kinds
		 *            the dependencies to follow
		 * @return paths of affected components that were not repainted
		 */
		public Set<String> getMissing(Set<DependencyKind> kinds)
		{
			return getMissing(kinds, Set.of());
		}

		/**
		 * @return paths of affected components that were not repainted, following the configured
		 *         dependencies
		 */
		public Set<String> getMissing()
		{
			return getMissing(followed);
		}

		/**
		 * @return the page id when the request started
		 */
		public int getPageIdBefore()
		{
			return pageIdBefore;
		}

		/**
		 * @return the page id when the request ended
		 */
		public int getPageIdAfter()
		{
			return pageIdAfter;
		}

		private static String parent(String path)
		{
			int colon = path.lastIndexOf(Component.PATH_SEPARATOR);
			return colon < 0 ? "" : path.substring(0, colon);
		}
	}
}
