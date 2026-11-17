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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class A2uiSurfacesTest
{
	private static final String CREATE = """
		{"version":"v0.9.1","createSurface":{"surfaceId":"main","catalogId":"%s","theme":{"primaryColor":"#ff0000"},"sendDataModel":true}}
		""".formatted(BasicCatalog.ID);

	@Test
	void createsSurfaceAndAppliesComponents()
	{
		A2uiSurfaces surfaces = new A2uiSurfaces();
		surfaces.process(CREATE + """
			{"version":"v0.9.1","updateComponents":{"surfaceId":"main","components":[{"id":"root","component":"Text","text":"Hello","variant":"h1"}]}}
			""");

		A2uiSurface surface = surfaces.get("main");
		assertEquals(BasicCatalog.ID, surface.getCatalogId());
		assertEquals("#ff0000", surface.getTheme().get("primaryColor"));
		assertEquals(true, surface.isSendDataModel());
		A2uiComponentDefinition root = surface.getComponent("root");
		assertEquals("Text", root.type());
		assertEquals(Map.of("text", "Hello", "variant", "h1"), root.properties());
	}

	@Test
	void updatesComponentsById()
	{
		A2uiSurfaces surfaces = new A2uiSurfaces();
		surfaces.process(CREATE);
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateComponents\":{\"surfaceId\":\"main\",\"components\":[{\"id\":\"root\",\"component\":\"Text\",\"text\":\"a\"}]}}");
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateComponents\":{\"surfaceId\":\"main\",\"components\":[{\"id\":\"root\",\"component\":\"Text\",\"text\":\"b\"}]}}");

		assertEquals("b", surfaces.get("main").getComponent("root").property("text"));
		assertEquals(1, surfaces.get("main").getComponents().size());
	}

	@Test
	void updatesDataModel()
	{
		A2uiSurfaces surfaces = new A2uiSurfaces();
		surfaces.process(CREATE);
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateDataModel\":{\"surfaceId\":\"main\",\"value\":{\"user\":{\"name\":\"Ada\"},\"items\":[\"a\",\"b\"]}}}");
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateDataModel\":{\"surfaceId\":\"main\",\"path\":\"/user/age\",\"value\":36}}");
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateDataModel\":{\"surfaceId\":\"main\",\"path\":\"/items/2\",\"value\":\"c\"}}");
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateDataModel\":{\"surfaceId\":\"main\",\"path\":\"/user/name\"}}");
		surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateDataModel\":{\"surfaceId\":\"main\",\"path\":\"/items/0\"}}");

		A2uiDataModel model = surfaces.get("main").getDataModel();
		assertEquals(Map.of("age", 36), model.get("/user"));
		assertEquals(java.util.Arrays.asList(null, "b", "c"), model.get("/items"));
		assertNull(model.get("/user/name"));
	}

	@Test
	void writesThroughEscapedPointers()
	{
		A2uiDataModel model = new A2uiDataModel();
		model.set("/a~1b/c~0d", "x");

		assertEquals(Map.of("a/b", Map.of("c~d", "x")), model.get("/"));
	}

	@Test
	void refusesArrayWritesPastTheEnd()
	{
		A2uiDataModel model = new A2uiDataModel();
		model.set("/items", new java.util.ArrayList<>(List.of("a")));

		assertThrows(A2uiProtocolException.class, () -> model.set("/items/5", "x"));
	}

	@Test
	void deletesSurface()
	{
		A2uiSurfaces surfaces = new A2uiSurfaces();
		surfaces.process(CREATE);
		surfaces.process("{\"version\":\"v0.9.1\",\"deleteSurface\":{\"surfaceId\":\"main\"}}");

		assertNull(surfaces.get("main"));
	}

	@Test
	void rejectsInvalidMessages()
	{
		A2uiSurfaces surfaces = new A2uiSurfaces();
		surfaces.process(CREATE);

		assertThrows(A2uiProtocolException.class, () -> surfaces.process("not json"));
		assertThrows(A2uiProtocolException.class, () -> surfaces.process(
			"{\"version\":\"v0.8\",\"deleteSurface\":{\"surfaceId\":\"main\"}}"));
		assertThrows(A2uiProtocolException.class, () -> surfaces.process(
			"{\"version\":\"v0.9.1\",\"deleteSurface\":{\"surfaceId\":\"main\"},\"createSurface\":{}}"));
		assertThrows(A2uiProtocolException.class, () -> surfaces.process(CREATE));
		assertThrows(A2uiProtocolException.class, () -> surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateComponents\":{\"surfaceId\":\"other\",\"components\":[]}}"));
		assertThrows(A2uiProtocolException.class, () -> surfaces.process(
			"{\"version\":\"v0.9.1\",\"updateComponents\":{\"surfaceId\":\"main\",\"components\":[{\"id\":\"x\"}]}}"));
	}
}
