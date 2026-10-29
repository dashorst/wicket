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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.core.util.objects.statedelta.StateChangeCheck.Result;
import org.apache.wicket.util.tester.WicketTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How the cooperative change signal relates to the state that actually changed.
 */
class VersioningTest extends WicketTestCase
{
	private final List<Result> results = new ArrayList<>();

	private AttributionPage page;

	@BeforeEach
	void install()
	{
		StateChangeCheck check = new StateChangeCheck(ComponentStateHasher.withDefaultExclusions())
			.setReporter(results::add)
			.setVersionReporter(results::add);
		tester.getApplication().getRequestCycleListeners().add(check);
		tester.getApplication().getAjaxRequestTargetListeners().add(check);
		page = tester.startPage(new AttributionPage());
	}

	private Result click(String link, Runnable action)
	{
		results.clear();
		page.action = action;
		tester.clickLink(link);
		return results.get(0);
	}

	@Test
	void ajaxNeverCreatesANewVersion()
	{
		Result result = click("link", () -> page.unrelated.setDefaultModelObject("changed"));

		assertTrue(result.isAjax());
		assertEquals(result.getPageIdBefore(), result.getPageIdAfter());
		assertEquals(java.util.Set.of("unrelated"), result.getDelta().getChanged().keySet());
	}

	@Test
	void directMutationChangesStateWithoutANewVersion()
	{
		Result result = click("plainLink", () -> page.shared.getObject().setName("Bea"));

		assertEquals(result.getPageIdBefore(), result.getPageIdAfter());
		assertTrue(result.getDelta().getChanged().containsKey("a"));
	}

	@Test
	void cooperativeChangeCreatesANewVersion()
	{
		Result result = click("plainLink", () -> page.unrelated.setDefaultModelObject("changed"));

		assertNotEquals(result.getPageIdBefore(), result.getPageIdAfter());
		assertEquals(java.util.Set.of("unrelated"), result.getDelta().getChanged().keySet());
	}

	@Test
	void replacingAModelWithAnEqualOneStillCreatesANewVersion()
	{
		Result result = click("plainLink",
			() -> page.unrelated.setDefaultModel(org.apache.wicket.model.Model.of("static")));

		assertNotEquals(result.getPageIdBefore(), result.getPageIdAfter());
		assertTrue(result.getDelta().isEmpty());
	}
}
