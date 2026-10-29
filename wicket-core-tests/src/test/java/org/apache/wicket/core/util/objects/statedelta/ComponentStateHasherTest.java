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

import java.util.EnumSet;
import java.util.Set;

import org.apache.wicket.core.util.objects.statedelta.StateSnapshot.Delta;
import org.apache.wicket.util.tester.WicketTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Attribution of state changes to components.
 */
class ComponentStateHasherTest extends WicketTestCase
{
	private AttributionPage page;

	private final ComponentStateHasher hasher = ComponentStateHasher.withDefaultExclusions();

	@BeforeEach
	void render()
	{
		page = tester.startPage(new AttributionPage());
	}

	private Delta change(Runnable mutation)
	{
		StateSnapshot before = hasher.snapshot(page);
		mutation.run();
		return hasher.snapshot(page).compareTo(before);
	}

	@Test
	void hashingTwiceGivesTheSameHashes()
	{
		Delta delta = change(() -> {
		});

		assertTrue(delta.isEmpty(), delta.getChanged().toString());
	}

	@Test
	void sharedModelMarksEveryComponentReachingIt()
	{
		Delta delta = change(() -> page.shared.getObject().setName("Bea"));

		assertEquals(Set.of("", "a", "b"), delta.getChanged().keySet());
		assertEquals(Set.of("org.apache.wicket.Component#data.model"), delta.getChanged().get("a"));
	}

	@Test
	void methodReferenceBoundToContainerModelIsPrecise()
	{
		Delta delta = change(() -> page.boxed.getObject().setName("Cat"));

		assertEquals(Set.of("", "box", "box:bound"), delta.getChanged().keySet());
		assertEquals(Set.of("org.apache.wicket.core.util.objects.statedelta.AttributionPage#boxed"),
			delta.getChanged().get(""));
		assertEquals(Set.of("", "box", "box:bound"), delta.getAffected(EnumSet.noneOf(DependencyKind.class)));
		assertEquals(Set.of("", "box", "box:bound", "capturing", "anonymous", "pageProperty", "link",
			"plainLink", "lambdaLink"), delta.getAffected());
	}

	@Test
	void lambdaCapturingThePageOnlyChangesThePage()
	{
		Delta delta = change(() -> page.items.add("x"));

		assertEquals(Set.of(""), delta.getChanged().keySet());
		assertEquals(Set.of("", "capturing"), delta.getAffected(EnumSet.of(DependencyKind.LAMBDA)));
		assertEquals(Set.of("", "anonymous", "link", "plainLink", "lambdaLink"),
			delta.getAffected(EnumSet.of(DependencyKind.INNER_CLASS)));
		assertEquals(Set.of("", "pageProperty"),
			delta.getAffected(EnumSet.of(DependencyKind.REFERENCE)));
	}

	@Test
	void dependenciesAreClassified()
	{
		StateSnapshot snapshot = hasher.snapshot(page);

		assertEquals(DependencyKind.LAMBDA,
			snapshot.getComponents().get("capturing").getDependencies().get(""));
		assertEquals(DependencyKind.INNER_CLASS,
			snapshot.getComponents().get("anonymous").getDependencies().get(""));
		assertEquals(DependencyKind.REFERENCE,
			snapshot.getComponents().get("pageProperty").getDependencies().get(""));
		assertTrue(snapshot.getComponents().get("box:bound").getDependencies().isEmpty());
		assertTrue(snapshot.getComponents().get("a").getDependencies().isEmpty());
	}

	@Test
	void cooperativeChangeIsSeenToo()
	{
		Delta delta = change(() -> page.unrelated.setDefaultModelObject("changed"));

		assertEquals(Set.of("unrelated"), delta.getChanged().keySet());
	}

	@Test
	void visibilityIsState()
	{
		Delta delta = change(() -> page.unrelated.setVisible(false));

		assertEquals(Set.of("org.apache.wicket.Component#flags"),
			delta.getChanged().get("unrelated"));
	}

	@Test
	void replacedComponentHeldInAFieldBecomesStateOfTheHolder()
	{
		Delta delta = change(() -> page.unrelated.replaceWith(
			new org.apache.wicket.markup.html.basic.Label("unrelated", "new")));

		assertEquals(Set.of("", "unrelated"), delta.getChanged().keySet());
		assertEquals(Set.of("org.apache.wicket.core.util.objects.statedelta.AttributionPage#unrelated"),
			delta.getChanged().get(""));
	}
}
