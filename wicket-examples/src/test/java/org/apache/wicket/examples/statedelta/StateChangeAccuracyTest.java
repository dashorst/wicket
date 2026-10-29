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
package org.apache.wicket.examples.statedelta;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.wicket.Component;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxNewWindowNotifyingBehavior;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.core.util.objects.statedelta.ComponentStateHasher;
import org.apache.wicket.core.util.objects.statedelta.DependencyKind;
import org.apache.wicket.core.util.objects.statedelta.StateChangeCheck;
import org.apache.wicket.core.util.objects.statedelta.StateChangeCheck.Result;
import org.apache.wicket.core.util.objects.statedelta.StateSnapshot;
import org.apache.wicket.examples.ajax.builtin.AjaxApplication;
import org.apache.wicket.examples.ajax.builtin.AutoCompletePage;
import org.apache.wicket.examples.ajax.builtin.ChoicePage;
import org.apache.wicket.examples.ajax.builtin.ClockPage;
import org.apache.wicket.examples.ajax.builtin.EditableLabelPage;
import org.apache.wicket.examples.ajax.builtin.EffectsPage;
import org.apache.wicket.examples.ajax.builtin.FormComponentPanelPage;
import org.apache.wicket.examples.ajax.builtin.FormPage;
import org.apache.wicket.examples.ajax.builtin.GuestBook;
import org.apache.wicket.examples.ajax.builtin.LazyLoadingPage;
import org.apache.wicket.examples.ajax.builtin.LinksPage;
import org.apache.wicket.examples.ajax.builtin.OnChangeAjaxBehaviorPage;
import org.apache.wicket.examples.ajax.builtin.PageablesPage;
import org.apache.wicket.examples.ajax.builtin.RatingsPage;
import org.apache.wicket.examples.ajax.builtin.TabbedPanelPage;
import org.apache.wicket.examples.ajax.builtin.TodoList;
import org.apache.wicket.examples.ajax.builtin.WorldClockPage;
import org.apache.wicket.examples.ajax.builtin.modal.ModalDialogPage;
import org.apache.wicket.markup.html.form.AbstractChoice;
import org.apache.wicket.markup.html.form.AbstractTextComponent;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.serialize.java.JavaSerializer;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs the development check over the Ajax examples and compares what it reports against what a
 * full render of the page shows, measured against a model of the browser's document that only
 * Ajax responses update.
 * <p>
 * Spike measurement, prints its numbers instead of asserting them.
 */
class StateChangeAccuracyTest
{
	private static final List<Class<? extends Page>> PAGES = List.of(LinksPage.class,
		EditableLabelPage.class, FormPage.class, ChoicePage.class, OnChangeAjaxBehaviorPage.class,
		TodoList.class, GuestBook.class, TabbedPanelPage.class, PageablesPage.class, ClockPage.class,
		WorldClockPage.class, RatingsPage.class, FormComponentPanelPage.class, LazyLoadingPage.class,
		EffectsPage.class, AutoCompletePage.class, ModalDialogPage.class);

	private static final int MAX_REQUESTS = 30;

	private static final Map<String, Set<DependencyKind>> POLICIES = new LinkedHashMap<>();

	static
	{
		POLICIES.put("own", EnumSet.noneOf(DependencyKind.class));
		POLICIES.put("own+reference", EnumSet.of(DependencyKind.REFERENCE));
		POLICIES.put("own+all", EnumSet.allOf(DependencyKind.class));
	}

	private WicketTester tester;

	private final List<Result> results = new ArrayList<>();

	private final Map<String, int[]> tally = new LinkedHashMap<>();

	private final Map<String, List<String>> samples = new TreeMap<>();

	private final Map<String, Integer> counters = new TreeMap<>();

	private final List<long[]> timings = new ArrayList<>();

	@BeforeEach
	void setUp()
	{
		tester = new WicketTester(new AjaxApplication());
		tester.getApplication().getDebugSettings()
			.setComponentPathAttributeName(MarkupModel.PATH_ATTRIBUTE);
		StateChangeCheck check = StateChangeCheck.install(tester.getApplication());
		if (check == null)
		{
			check = new StateChangeCheck(ComponentStateHasher.withDefaultExclusions());
			tester.getApplication().getRequestCycleListeners().add(check);
			tester.getApplication().getAjaxRequestTargetListeners().add(check);
		}
		check.setReporter(results::add);
		for (String policy : POLICIES.keySet())
		{
			tally.put(policy, new int[4]);
		}
	}

	@AfterEach
	void tearDown()
	{
		tester.destroy();
	}

	private void count(String counter)
	{
		counters.merge(counter, 1, Integer::sum);
	}

	private void sample(String kind, String text)
	{
		samples.computeIfAbsent(kind, k -> new ArrayList<>()).add(text);
	}

	/** An Ajax interaction the driver can trigger. */
	private record Action(String path, Behavior behavior, String event)
	{
		String describe()
		{
			return path + (event != null ? "@" + event : "#" + behavior.getClass().getSimpleName());
		}
	}

	private List<Action> actions(Page page, MarkupModel browser)
	{
		List<Action> actions = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		List<Component> components = new ArrayList<>();
		components.add(page);
		page.visitChildren((Component c, org.apache.wicket.util.visit.IVisit<Void> v) -> components.add(c));
		for (Component component : components)
		{
			String markupId = component.getMarkupId(false);
			if (component != page && (markupId == null || !browser.byId.containsKey(markupId) ||
				!component.isVisibleInHierarchy() || !component.isEnabledInHierarchy()))
			{
				continue;
			}
			for (Behavior behavior : component.getBehaviors())
			{
				if (behavior instanceof AjaxNewWindowNotifyingBehavior)
				{
					continue;
				}
				String path = component.getPageRelativePath();
				if (behavior instanceof AjaxEventBehavior event)
				{
					String name = event.getEvent().trim().split("\\s+")[0];
					if (seen.add(path + "@" + name))
					{
						actions.add(new Action(path, behavior, name));
					}
				}
				else if (behavior instanceof AbstractAjaxTimerBehavior ||
					behavior instanceof AbstractDefaultAjaxBehavior)
				{
					if (seen.add(path + "#" + behavior.getClass().getName()))
					{
						actions.add(new Action(path, behavior, null));
					}
				}
			}
		}
		return actions;
	}

	private static String inputValue(FormComponent<?> component, int round)
	{
		if (component instanceof CheckBox)
		{
			return round % 2 == 0 ? "on" : null;
		}
		if (component instanceof AbstractChoice<?, ?> choice)
		{
			@SuppressWarnings("unchecked")
			List<Object> choices = (List<Object>)choice.getChoices();
			if (choices.isEmpty())
			{
				return null;
			}
			int index = (round + 1) % choices.size();
			@SuppressWarnings("unchecked")
			IChoiceRenderer<Object> renderer = (IChoiceRenderer<Object>)choice.getChoiceRenderer();
			return renderer.getIdValue(choices.get(index), index);
		}
		if (component instanceof AbstractTextComponent<?> text)
		{
			Class<?> type = text.getType();
			if (type != null && (Number.class.isAssignableFrom(type) || type.isPrimitive()))
			{
				return Integer.toString(round + 2);
			}
			return "value" + round;
		}
		return null;
	}

	private void fire(Page page, Action action, int round)
	{
		Component component = action.path.isEmpty() ? page : page.get(action.path);
		List<FormComponent<?>> inputs = new ArrayList<>();
		if (component instanceof FormComponent<?> formComponent && !(component instanceof Button))
		{
			inputs.add(formComponent);
		}
		if (component instanceof Button || action.behavior.getClass().getName().contains("Submit"))
		{
			org.apache.wicket.markup.html.form.Form<?> form = component instanceof Button button
				? button.getForm() : component.findParent(org.apache.wicket.markup.html.form.Form.class);
			if (form != null)
			{
				form.visitFormComponents((fc, visit) -> {
					if (fc instanceof AbstractTextComponent)
					{
						inputs.add(fc);
					}
				});
			}
		}
		for (FormComponent<?> input : inputs)
		{
			String value = inputValue(input, round);
			if (value != null)
			{
				tester.getRequest().getPostParameters().setParameterValue(input.getInputName(),
					value);
			}
		}
		if (action.event != null)
		{
			tester.executeAjaxEvent(component, action.event);
		}
		else
		{
			tester.getRequest().getPostParameters().setParameterValue("q", "a");
			tester.executeBehavior((AbstractDefaultAjaxBehavior)action.behavior);
		}
	}

	private static String encode(String path)
	{
		return path.replace("_", "__").replace(':', '_');
	}

	private static String parent(String path)
	{
		int colon = path.lastIndexOf(':');
		return colon < 0 ? "" : path.substring(0, colon);
	}

	private static String unit(String path, MarkupModel a, MarkupModel b)
	{
		for (String p = path; !p.isEmpty(); p = parent(p))
		{
			String encoded = encode(p);
			if (a.byPath.containsKey(encoded) || b.byPath.containsKey(encoded))
			{
				return encoded;
			}
		}
		return "";
	}

	private static MarkupModel applyAjax(MarkupModel browser, String response)
	{
		return applyAjax(browser, response, Set.of());
	}

	private static MarkupModel applyAjax(MarkupModel browser, String response, Set<String> skipIds)
	{
		String html = browser.html;
		int i = 0;
		String open = "<component id=\"";
		while ((i = response.indexOf(open, i)) >= 0)
		{
			int idEnd = response.indexOf('"', i + open.length());
			String id = response.substring(i + open.length(), idEnd);
			int contentStart = response.indexOf('>', idEnd) + 1;
			int contentEnd = response.indexOf("</component>", contentStart);
			String content = response.substring(contentStart, contentEnd);
			if (content.startsWith("<![CDATA["))
			{
				content = content.substring(9, content.length() - 3);
			}
			content = content.replace("]]]]><![CDATA[>", "]]>");
			String replaced = skipIds.contains(id) ? null : new MarkupModel(html).replace(id, content);
			if (replaced != null)
			{
				html = replaced;
			}
			i = contentEnd;
		}
		return new MarkupModel(html);
	}

	@Test
	void accuracy()
	{
		for (Class<? extends Page> pageClass : PAGES)
		{
			try
			{
				run(pageClass);
			}
			catch (Throwable t)
			{
				count("page failed");
				sample("page failed", pageClass.getSimpleName() + ": " + t);
			}
		}

		System.out.println("ACC|counters|" + counters);
		tally.forEach((policy, t) -> System.out.println("ACC|" + policy + "|TP=" + t[0] +
			" FP=" + t[1] + " FN=" + t[2] + " TN=" + t[3]));
		seeds.forEach((policy, t) -> System.out.println("ACC|seeded " + policy +
			"|needed+detected=" + t[0] + " unneeded+detected=" + t[1] + " needed+missed=" + t[2] +
			" unneeded+silent=" + t[3]));
		long[] sums = new long[4];
		long max = 0;
		for (long[] t : timings)
		{
			for (int k = 0; k < 4; k++)
			{
				sums[k] += t[k];
			}
			max = Math.max(max, t[0]);
		}
		if (!timings.isEmpty())
		{
			System.out.println("ACC|timing|requests=" + timings.size() + " avgSnapshotUs=" +
				sums[0] / timings.size() / 1000 + " maxSnapshotUs=" + max / 1000 +
				" avgComponents=" + sums[1] / timings.size() + " avgSerializedBytes=" +
				sums[2] / timings.size() + " avgHashedBytes=" + sums[3] / timings.size());
		}
		samples.forEach((kind, list) -> list.forEach(s -> System.out.println("ACC|" + kind + "|" + s)));
	}

	/**
	 * Measures, across consecutive versions of each page, how many of the serialized bytes sit in
	 * components whose state did not change. Every component is serialized on its own, with full
	 * class descriptors and nothing excluded, which is what an independent chunk would cost.
	 */
	@Test
	void deltaStorage()
	{
		ComponentStateHasher chunker = new ComponentStateHasher().setFullClassDescriptors(true)
			.setResetPerField(false)
			.setTrackSharing(true);
		ComponentStateHasher compact = new ComponentStateHasher().setResetPerField(false);
		JavaSerializer serializer = new JavaSerializer("delta");
		long[] totals = new long[9];
		Map<String, Integer> sharedTypes = new TreeMap<>();
		for (Class<? extends Page> pageClass : PAGES)
		{
			long[] t = new long[9];
			try
			{
				tester.startPage(pageClass);
				Page page = tester.getLastRenderedPage();
				MarkupModel browser = new MarkupModel(tester.getLastResponseAsString());
				StateSnapshot previous = chunker.snapshot(page);
				StateSnapshot previousCompact = compact.snapshot(page);
				int requests = 0;
				Set<String> done = new LinkedHashSet<>();
				for (int round = 0; round < 3 && requests < MAX_REQUESTS; round++)
				{
					for (Action action : actions(page, browser))
					{
						if (requests >= MAX_REQUESTS || !done.add(round + action.describe()))
						{
							continue;
						}
						try
						{
							fire(page, action, round);
						}
						catch (Throwable e)
						{
							continue;
						}
						requests++;
						String response = tester.getLastResponseAsString();
						if (!response.contains("<ajax-response>") || response.contains("<redirect>") ||
							tester.getLastRenderedPage() != page)
						{
							break;
						}
						browser = applyAjax(browser, response);
						StateSnapshot current = chunker.snapshot(page);
						t[0]++;
						t[1] += serializer.serialize(page).length;
						for (StateSnapshot.ComponentState state : current.getComponents().values())
						{
							StateSnapshot.ComponentState old = previous.getComponents()
								.get(state.getPath());
							t[2] += state.getSize();
							t[4]++;
							if (old != null && old.getHash() == state.getHash())
							{
								t[3] += state.getSize();
							}
							else
							{
								t[5]++;
							}
						}
						for (Map.Entry<Class<?>, Integer> shared : chunker.getLastSharedObjects()
							.entrySet())
						{
							t[6] += shared.getValue();
							sharedTypes.merge(shared.getKey().getName(), shared.getValue(),
								Integer::sum);
						}
						previous = current;
						StateSnapshot currentCompact = compact.snapshot(page);
						for (StateSnapshot.ComponentState state : currentCompact.getComponents()
							.values())
						{
							StateSnapshot.ComponentState old = previousCompact.getComponents()
								.get(state.getPath());
							t[7] += state.getSize();
							if (old == null || old.getHash() != state.getHash())
							{
								t[8] += state.getSize();
							}
						}
						previousCompact = currentCompact;
					}
				}
			}
			catch (Throwable e)
			{
				System.out.println("DELTA|" + pageClass.getSimpleName() + "|failed " + e);
			}
			if (t[0] > 0)
			{
				System.out.println("DELTA|" + pageClass.getSimpleName() + "|" + line(t));
				for (int k = 0; k < t.length; k++)
				{
					totals[k] += t[k];
				}
			}
		}
		System.out.println("DELTA|total|" + line(totals));
		sharedTypes.entrySet()
			.stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.limit(15)
			.forEach(e -> System.out.println("DELTA|shared|" + e.getKey() + "=" + e.getValue()));
	}

	private static final List<String> NON_AJAX_PAGES = List.of("compref.ButtonPage",
		"compref.CheckBoxMultipleChoicePage", "compref.CheckBoxPage", "compref.CheckGroupPage",
		"compref.DropDownChoicePage", "compref.FormPage", "compref.FragmentPage", "compref.LabelPage",
		"compref.LinkPage", "compref.ListChoicePage", "compref.ListMultipleChoicePage",
		"compref.MultiLineLabelPage", "compref.PalettePage", "compref.PanelPage",
		"compref.RadioChoicePage", "compref.RadioGroupPage", "compref.SelectPage",
		"compref.SubmitLinkPage", "compref.TabbedPanelPage", "compref.TextAreaPage",
		"compref.TextFieldPage", "repeater.DataGridPage", "repeater.DataTablePage",
		"repeater.FormPage", "repeater.GridViewPage", "repeater.OIRPage", "repeater.PagingPage",
		"repeater.RefreshingPage", "repeater.SortingPage", "forminput.FormInput",
		"guestbook.GuestBook", "linkomatic.Home");

	/**
	 * Counts, for non-Ajax listener requests, how often the page got a new id without its state
	 * changing, and the other way around.
	 */
	@Test
	void versions()
	{
		List<Result> versions = new ArrayList<>();
		StateChangeCheck check = new StateChangeCheck(ComponentStateHasher.withDefaultExclusions())
			.setReporter(r -> {
			})
			.setVersionReporter(versions::add);
		tester.getApplication().getRequestCycleListeners().add(check);
		Map<String, Integer> outcome = new TreeMap<>();
		for (String name : NON_AJAX_PAGES)
		{
			try
			{
				@SuppressWarnings("unchecked")
				Class<? extends Page> pageClass = (Class<? extends Page>)Class
					.forName("org.apache.wicket.examples." + name);
				if (name.startsWith("repeater.") && !(tester
					.getApplication() instanceof org.apache.wicket.examples.repeater.RepeaterApplication))
				{
					tester.destroy();
					tester = new WicketTester(new org.apache.wicket.examples.repeater.RepeaterApplication());
					tester.getApplication().getDebugSettings()
						.setComponentPathAttributeName(MarkupModel.PATH_ATTRIBUTE);
					tester.getApplication().getRequestCycleListeners().add(check);
				}
				else if (!name.startsWith("repeater.") && tester
					.getApplication() instanceof org.apache.wicket.examples.repeater.RepeaterApplication)
				{
					tester.destroy();
					tester = new WicketTester(new AjaxApplication());
					tester.getApplication().getDebugSettings()
						.setComponentPathAttributeName(MarkupModel.PATH_ATTRIBUTE);
					tester.getApplication().getRequestCycleListeners().add(check);
				}
				tester.startPage(pageClass);
				Page page = tester.getLastRenderedPage();
				int requests = 0;
				Set<String> done = new LinkedHashSet<>();
				boolean progress = true;
				while (progress && requests < 20)
				{
					progress = false;
					MarkupModel browser = new MarkupModel(tester.getLastResponseAsString());
					for (Component target : listeners(page, browser))
					{
						String path = target.getPageRelativePath();
						if (!done.add(path) || requests >= 20)
						{
							continue;
						}
						versions.clear();
						try
						{
							if (target instanceof org.apache.wicket.markup.html.link.Link)
							{
								tester.clickLink(target.getPageRelativePath(), false);
							}
							else
							{
								org.apache.wicket.markup.html.form.Form<?> form = target.findParent(
									org.apache.wicket.markup.html.form.Form.class);
								org.apache.wicket.util.tester.FormTester formTester = tester
									.newFormTester(form.getPageRelativePath(), false);
								form.visitFormComponents((fc, visit) -> {
									String value = inputValue(fc, 0);
									if (value != null && fc.isVisibleInHierarchy() &&
										fc.isEnabledInHierarchy() && !(fc instanceof Button))
									{
										try
										{
											formTester.setValue(fc.getPath().substring(
												form.getPath().length() + 1), value);
										}
										catch (RuntimeException e)
										{
											// not settable through the form tester
										}
									}
								});
								formTester.submit(path.substring(form.getPageRelativePath().length() + 1));
							}
						}
						catch (Throwable e)
						{
							outcome.merge("request failed", 1, Integer::sum);
							tester.startPage(page);
							continue;
						}
						requests++;
						for (Result result : versions)
						{
							boolean dirty = result.getPageIdBefore() != result.getPageIdAfter();
							boolean changed = !result.getDelta().isEmpty();
							String key = (dirty ? "new page id" : "same page id") + ", " +
								(changed ? "state changed" : "state unchanged");
							outcome.merge(key, 1, Integer::sum);
							if (dirty != changed)
							{
								System.out.println("VERSION|" + key + "|" + name + " " + path + " " +
									result.getDelta().getChanged() + " added=" +
									result.getDelta().getAdded().size() + " removed=" +
									result.getDelta().getRemoved().size());
							}
						}
						if (tester.getLastRenderedPage() != page)
						{
							tester.startPage(page);
						}
						progress = true;
						break;
					}
				}
			}
			catch (Throwable e)
			{
				outcome.merge("page failed", 1, Integer::sum);
				System.out.println("VERSION|page failed|" + name + " " + e);
			}
		}
		System.out.println("VERSION|outcome|" + outcome);
	}

	private List<Component> listeners(Page page, MarkupModel browser)
	{
		List<Component> listeners = new ArrayList<>();
		page.visitChildren((Component c, org.apache.wicket.util.visit.IVisit<Void> v) -> {
			boolean rendered = browser.byPath.containsKey(encode(c.getPageRelativePath()));
			if (rendered && c.isVisibleInHierarchy() && c.isEnabledInHierarchy() &&
				(c instanceof org.apache.wicket.markup.html.link.Link || c instanceof Button) &&
				!(c instanceof org.apache.wicket.ajax.markup.html.AjaxLink))
			{
				listeners.add(c);
			}
		});
		return listeners;
	}

	private static String line(long[] t)
	{
		return "versions=" + t[0] + " avgSerialized=" + t[1] / t[0] + " avgChunked=" + t[2] / t[0] +
			" unchangedChunkBytes=" + (100 * t[3] / Math.max(1, t[2])) + "% avgChangedChunkBytes=" +
			(t[2] - t[3]) / t[0] + " changedChunks=" + t[5] + "/" + t[4] + " sharedObjectsPerVersion=" +
			t[6] / t[0] + " avgCompactChunked=" + t[7] / t[0] +
			" avgCompactChangedBytes=" + t[8] / t[0];
	}

	private void run(Class<? extends Page> pageClass)
	{
		String name = pageClass.getSimpleName();
		tester.startPage(pageClass);
		Page page = tester.getLastRenderedPage();
		StateSnapshot initial = ComponentStateHasher.withDefaultExclusions().snapshot(page);
		for (StateSnapshot.ComponentState state : initial.getComponents().values())
		{
			count("components");
			if (state.getPath().isEmpty())
			{
				continue;
			}
			DependencyKind onPage = state.getDependencies().get("");
			if (onPage != null)
			{
				count("components depending on the page: " + onPage);
			}
			if (!state.getDependencies().isEmpty())
			{
				count("components with any dependency");
			}
		}
		MarkupModel browser = new MarkupModel(tester.getLastResponseAsString());
		JavaSerializer serializer = new JavaSerializer("accuracy");
		int requests = 0;
		Set<String> done = new LinkedHashSet<>();

		for (int round = 0; round < 3 && requests < MAX_REQUESTS; round++)
		{
			for (Action action : actions(page, browser))
			{
				if (requests >= MAX_REQUESTS || !done.add(round + action.describe()))
				{
					continue;
				}
				results.clear();
				try
				{
					fire(page, action, round);
				}
				catch (Throwable t)
				{
					count("action failed");
					sample("action failed", name + " " + action.describe() + ": " + t);
					tester.startPage(page);
					browser = new MarkupModel(tester.getLastResponseAsString());
					continue;
				}
				requests++;
				count("requests");
				String response = tester.getLastResponseAsString();
				if (!response.contains("<ajax-response>") || response.contains("<redirect>"))
				{
					count("non-ajax or redirect response");
					if (tester.getLastRenderedPage() != page)
					{
						return;
					}
					tester.startPage(page);
					browser = new MarkupModel(tester.getLastResponseAsString());
					continue;
				}
				Result result = results.isEmpty() ? null : results.get(results.size() - 1);

				MarkupModel before = browser;
				browser = applyAjax(browser, response);
				tester.startPage(page);
				MarkupModel truth = new MarkupModel(tester.getLastResponseAsString());

				if (result == null)
				{
					count("request without check result");
				}
				else
				{
					evaluate(name + " " + action.describe() + " r" + round, result, browser, truth);
					seed(name + " " + action.describe() + " r" + round, result, before, response, truth);
					timings.add(new long[] { result.getAfter().getNanos(),
							result.getAfter().getComponents().size(),
							serializer.serialize(page).length, result.getAfter().getSize() });
					boolean dirty = result.getPageIdBefore() != result.getPageIdAfter();
					boolean changed = !result.getDelta().isEmpty();
					count("versions: " + (dirty ? "new page id" : "same page id") + ", " +
						(changed ? "state changed" : "state unchanged"));
					if (dirty && !changed)
					{
						sample("dirty without change", name + " " + action.describe());
					}
					if (!dirty && changed)
					{
						sample("change without dirty", name + " " + action.describe() + " " +
							result.getDelta().getChanged().keySet());
					}
				}
				browser = truth;
			}
		}
	}

	private void evaluate(String where, Result result, MarkupModel browser, MarkupModel truth)
	{
		Map<String, String> stale = new LinkedHashMap<>();
		List<String> units = new ArrayList<>();
		units.add("");
		for (String path : truth.byPath.keySet())
		{
			if (browser.byPath.containsKey(path))
			{
				units.add(path);
			}
		}
		for (String unit : units)
		{
			MarkupModel.Element t = unit.isEmpty() ? truth.body : truth.byPath.get(unit);
			MarkupModel.Element b = unit.isEmpty() ? browser.body : browser.byPath.get(unit);
			if (t == null || b == null)
			{
				continue;
			}
			String own = truth.own(t, browser);
			String shown = browser.own(b, truth);
			if (!own.equals(shown))
			{
				stale.put(unit, diff(shown, own));
			}
		}

		Map<String, String> encodedToPath = new HashMap<>();
		encodedToPath.put("", "");
		result.getPage().visitChildren(
			(c, v) -> encodedToPath.put(encode(c.getPageRelativePath()), c.getPageRelativePath()));

		// the browser shows what the user typed, which the document model does not
		for (String echoed : result.getEchoed())
		{
			if (units.remove(encode(echoed)))
			{
				count("units skipped: input echo");
			}
		}

		for (Map.Entry<String, Set<DependencyKind>> policy : POLICIES.entrySet())
		{
			Map<String, List<String>> flagged = new LinkedHashMap<>();
			for (String path : result.getMissing(policy.getValue()))
			{
				flagged.computeIfAbsent(unit(path, truth, browser), k -> new ArrayList<>()).add(path);
			}
			int[] t = tally.get(policy.getKey());
			for (String unit : units)
			{
				boolean isStale = stale.containsKey(unit);
				boolean isFlagged = flagged.containsKey(unit);
				t[isFlagged ? (isStale ? 0 : 1) : (isStale ? 2 : 3)]++;
				if (isFlagged && !isStale)
				{
					sample("FP " + policy.getKey(), where + " unit=" + unit + " because " +
						reasons(result, flagged.get(unit)));
				}
				if (!isFlagged && isStale)
				{
					String path = encodedToPath.getOrDefault(unit, unit);
					sample("FN " + policy.getKey(), where + " unit=" + unit + " repainted=" +
						result.isRepainted(path) + " " + stale.get(unit));
				}
			}
		}
	}

	private final Map<String, int[]> seeds = new LinkedHashMap<>();

	/**
	 * Replays the request as if one of the components the example added had been forgotten: the
	 * browser does not get its markup, and the check does not count it as repainted.
	 */
	private void seed(String where, Result result, MarkupModel before, String response,
		MarkupModel truth)
	{
		for (String omitted : result.getRepainted())
		{
			if (omitted.isEmpty() || result.getEchoed().contains(omitted))
			{
				continue;
			}
			Component component = result.getPage().get(omitted);
			String markupId = component == null ? null : component.getMarkupId(false);
			if (markupId == null)
			{
				continue;
			}
			MarkupModel browser = applyAjax(before, response, Set.of(markupId));
			boolean needed = false;
			for (String path : truth.byPath.keySet())
			{
				MarkupModel.Element b = browser.byPath.get(path);
				if (b != null && inside(path, omitted) &&
					!truth.own(truth.byPath.get(path), browser).equals(browser.own(b, truth)))
				{
					needed = true;
					break;
				}
			}
			for (Map.Entry<String, Set<DependencyKind>> policy : POLICIES.entrySet())
			{
				boolean detected = false;
				for (String path : result.getMissing(policy.getValue(), Set.of(omitted)))
				{
					if (path.equals(omitted) || path.startsWith(omitted + ":"))
					{
						detected = true;
						break;
					}
				}
				int[] t = seeds.computeIfAbsent(policy.getKey(), k -> new int[4]);
				t[detected ? (needed ? 0 : 1) : (needed ? 2 : 3)]++;
				if (needed && !detected && policy.getKey().equals("own"))
				{
					sample("seed missed", where + " omitted=" + omitted);
				}
				if (!needed && detected && policy.getKey().equals("own"))
				{
					sample("seed flagged unneeded", where + " omitted=" + omitted);
				}
			}
		}
	}

	private static boolean inside(String encodedPath, String path)
	{
		String encoded = encode(path);
		return encodedPath.equals(encoded) || encodedPath.startsWith(encoded + "_") &&
			!encodedPath.startsWith(encoded + "__");
	}

	private static String reasons(Result result, List<String> paths)
	{
		StringBuilder b = new StringBuilder();
		StateSnapshot.Delta delta = result.getDelta();
		for (String path : paths)
		{
			Set<String> fields = delta.getChanged().get(path);
			if (fields != null)
			{
				b.append(path).append(" own").append(shorten(fields)).append(' ');
			}
			else if (delta.getAdded().contains(path))
			{
				b.append(path).append(" added ");
			}
			else
			{
				StateSnapshot.ComponentState state = result.getAfter().getComponents().get(path);
				b.append(path).append(" via");
				state.getDependencies().forEach((dependency, kind) -> {
					if (delta.getChanged().containsKey(dependency))
					{
						b.append(' ').append(kind).append("->").append(
							dependency.isEmpty() ? "<page>" : dependency);
					}
				});
				b.append(' ');
			}
		}
		return b.toString().trim();
	}

	private static String shorten(Set<String> fields)
	{
		List<String> list = new ArrayList<>();
		for (String field : fields)
		{
			list.add(field.substring(field.lastIndexOf('.', field.indexOf('#')) + 1));
		}
		return list.toString();
	}

	private static String diff(String before, String after)
	{
		int start = 0;
		while (start < before.length() && start < after.length() &&
			before.charAt(start) == after.charAt(start))
		{
			start++;
		}
		int from = Math.max(0, start - 30);
		return "browser[" + snippet(before, from) + "] truth[" + snippet(after, from) + "]";
	}

	private static String snippet(String s, int from)
	{
		return s.substring(Math.min(from, s.length()), Math.min(s.length(), from + 110))
			.replace('\n', ' ');
	}
}
