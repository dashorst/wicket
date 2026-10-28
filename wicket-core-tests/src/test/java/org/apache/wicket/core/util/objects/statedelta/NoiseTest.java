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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.wicket.core.util.objects.statedelta.StateChangeCheck.Result;
import org.apache.wicket.core.util.objects.statedelta.StateSnapshot.Delta;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.serialize.java.JavaSerializer;
import org.apache.wicket.util.tester.WicketTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What changes during a request without the application changing anything.
 */
class NoiseTest extends WicketTestCase
{
	private final List<Result> raw = new ArrayList<>();

	private final List<Result> filtered = new ArrayList<>();

	private NoisePage page;

	@BeforeEach
	void install()
	{
		StateChangeCheck rawCheck = new StateChangeCheck(new ComponentStateHasher())
			.setReporter(raw::add);
		StateChangeCheck filteredCheck = new StateChangeCheck(
			ComponentStateHasher.withDefaultExclusions()).setReporter(filtered::add);
		for (StateChangeCheck check : List.of(rawCheck, filteredCheck))
		{
			tester.getApplication().getRequestCycleListeners().add(check);
			tester.getApplication().getAjaxRequestTargetListeners().add(check);
		}
		tester.getApplication().getResourceSettings().setThrowExceptionOnMissingResource(false);
		page = tester.startPage(new NoisePage());
	}

	private static Map<String, Set<String>> fields(Delta delta)
	{
		Map<String, Set<String>> fields = new TreeMap<>();
		delta.getChanged().forEach((path, changed) -> {
			for (String field : changed)
			{
				fields.computeIfAbsent(field.substring(field.lastIndexOf('.') + 1), k -> new TreeSet<>())
					.add(path.isEmpty() ? "<page>" : path);
			}
		});
		delta.getAdded().forEach(p -> fields.computeIfAbsent("+added", k -> new TreeSet<>()).add(p));
		delta.getRemoved()
			.forEach(p -> fields.computeIfAbsent("-removed", k -> new TreeSet<>()).add(p));
		return fields;
	}

	private void print(String scenario)
	{
		System.out.println("NOISE|" + scenario + "|raw     |" + fields(raw.get(0).getDelta()));
		System.out.println("NOISE|" + scenario + "|filtered|" + fields(filtered.get(0).getDelta()) +
			" changed=" + filtered.get(0).getDelta().getChanged().keySet() + " missing=" +
			filtered.get(0).getMissing() + " echoed=" + filtered.get(0).getEchoed());
	}

	@Test
	void deserializedCopyHashesTheSame()
	{
		JavaSerializer serializer = new JavaSerializer("test");
		NoisePage copy = (NoisePage)serializer.deserialize(serializer.serialize(page));

		ComponentStateHasher hasher = new ComponentStateHasher();
		Delta delta = hasher.snapshot(copy).compareTo(hasher.snapshot(page));

		assertTrue(delta.isEmpty(), delta.getChanged().toString());
	}

	@Test
	void identityHashedCollectionsDoNotSurviveACopy()
	{
		page.add(new IdentitySetLabel("identity"));
		JavaSerializer serializer = new JavaSerializer("test");
		NoisePage copy = (NoisePage)serializer.deserialize(serializer.serialize(page));

		ComponentStateHasher hasher = new ComponentStateHasher();
		int differing = 0;
		for (int i = 0; i < 20; i++)
		{
			copy = (NoisePage)serializer.deserialize(serializer.serialize(page));
			if (!hasher.snapshot(copy).compareTo(hasher.snapshot(page)).isEmpty())
			{
				differing++;
			}
		}
		System.out.println("NOISE|identity HashSet|copies hashing differently: " + differing + "/20");
		assertTrue(differing > 0);
	}

	private static class IdentitySetLabel extends org.apache.wicket.markup.html.basic.Label
	{
		private static final long serialVersionUID = 1L;

		private final Set<Object> keys = new java.util.HashSet<>();

		IdentitySetLabel(String id)
		{
			super(id);
			for (int i = 0; i < 16; i++)
			{
				keys.add(new Key(i));
			}
		}
	}

	private record Key(int i) implements java.io.Serializable
	{
		@Override
		public int hashCode()
		{
			return System.identityHashCode(this);
		}

		@Override
		public boolean equals(Object obj)
		{
			return this == obj;
		}
	}

	@Test
	void fullRender()
	{
		StateSnapshot rawBefore = new ComponentStateHasher().snapshot(page);
		StateSnapshot before = ComponentStateHasher.withDefaultExclusions().snapshot(page);
		tester.startPage(page);
		System.out.println("NOISE|full render|raw     |" +
			fields(new ComponentStateHasher().snapshot(page).compareTo(rawBefore)));
		Delta delta = ComponentStateHasher.withDefaultExclusions().snapshot(page).compareTo(before);
		System.out.println("NOISE|full render|filtered|" + fields(delta));
	}

	@Test
	void ajaxWithoutChange()
	{
		tester.clickLink("noop");
		print("noop click");

		assertTrue(filtered.get(0).getDelta().isEmpty());
	}

	@Test
	void invalidInput()
	{
		FormComponent<?> age = (FormComponent<?>)page.get("form:age");
		tester.getRequest().getPostParameters().setParameterValue(age.getInputName(), "x");
		tester.executeAjaxEvent(age, "change");
		print("invalid input");

		assertEquals(Set.of("org.apache.wicket.Component#data.meta:org.apache.wicket.Component$2",
			"org.apache.wicket.markup.html.form.FormComponent#rawInput"),
			raw.get(0).getDelta().getChanged().get("form:age"));

		assertTrue(filtered.get(0).getMissing().isEmpty());
	}

	@Test
	void validInput()
	{
		FormComponent<?> name = (FormComponent<?>)page.get("form:name");
		tester.getRequest().getPostParameters().setParameterValue(name.getInputName(), "Bea");
		tester.executeAjaxEvent(name, "change");
		print("valid input");

		assertEquals(Set.of("form:name"), filtered.get(0).getEchoed());
		assertTrue(filtered.get(0).getMissing().isEmpty());
	}
}
