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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.util.io.IClusterable;
import org.apache.wicket.util.tester.WicketTestCase;
import org.junit.jupiter.api.Test;

import com.github.openjson.JSONObject;

class A2uiCatalogTest extends WicketTestCase
{
	enum ChartType
	{
		BAR, STACKED_BAR, LINE
	}

	enum Dataset
	{
		@A2uiDescription("Unweighted average of all final grades")
		AVERAGE_GRADE, ABSENCE
	}

	public record DatasetProps(Dataset dataset) implements IClusterable
	{
	}

	public record Series(@A2uiRequired String name, @A2uiRequired List<Double> values)
		implements
			IClusterable
	{
	}

	public record ChartProps(
		@A2uiRequired @A2uiDescription("How the series are drawn") ChartType type,
		@A2uiRequired A2uiValue<List<String>> labels, A2uiValue<List<Series>> series,
		A2uiValue<String> title) implements IClusterable
	{
	}

	public record NotSerializable(String text)
	{
	}

	public record ClaimsCommonProperty(String id) implements IClusterable
	{
	}

	static final String REPORTING = "https://example.com/catalogs/reporting/v1";

	static A2uiCatalog reporting()
	{
		return A2uiCatalog.builder(REPORTING)
			.include(BasicCatalog.get())
			.component("Chart", "A chart of one or more series", ChartProps.class,
				(wicketId, props, context) -> new Label(wicketId, () -> props.type() + " of " +
					context.model(props.labels()).getObject() + " " +
					context.model(props.series()).getObject() + " titled " +
					context.model(props.title()).getObject()))
			.build();
	}

	@Test
	void describesComponentsAsJsonSchema()
	{
		JSONObject schema = reporting().toJsonSchema();

		assertEquals(REPORTING, schema.getString("$id"));
		JSONObject chart = schema.getJSONObject("components").getJSONObject("Chart");
		assertEquals("A chart of one or more series", chart.getString("description"));
		assertEquals("[\"type\",\"labels\",\"component\",\"id\"]",
			chart.getJSONArray("required").toString());
		assertFalse(chart.getBoolean("additionalProperties"));

		JSONObject properties = chart.getJSONObject("properties");
		JSONObject type = properties.getJSONObject("type");
		assertEquals("[\"bar\",\"stackedBar\",\"line\"]", type.getJSONArray("enum").toString());
		assertEquals("How the series are drawn", type.getString("description"));

		JSONObject labels = properties.getJSONObject("labels");
		assertEquals(
			"[{\"type\":\"array\",\"items\":{\"type\":\"string\"}},{\"$ref\":\"#/$defs/DataBinding\"},{\"$ref\":\"#/$defs/FunctionCall\"}]",
			labels.getJSONArray("oneOf").toString());

		JSONObject series = properties.getJSONObject("series").getJSONArray("oneOf")
			.getJSONObject(0).getJSONObject("items");
		assertEquals("[\"name\",\"values\"]", series.getJSONArray("required").toString());

		assertTrue(schema.getJSONObject("components").has("Text"));
		assertTrue(schema.getJSONObject("$defs").has("ChildList"));
	}

	@Test
	void describesEnumConstants()
	{
		JSONObject schema = A2uiCatalog.builder(REPORTING)
			.component("Data", DatasetProps.class, (id, props, context) -> new Label(id))
			.build()
			.toJsonSchema();

		assertEquals(
			"{\"type\":\"string\",\"oneOf\":[{\"const\":\"averageGrade\",\"description\":\"Unweighted average of all final grades\"},{\"const\":\"absence\"}]}",
			schema.getJSONObject("components").getJSONObject("Data").getJSONObject("properties")
				.getJSONObject("dataset").toString());
	}

	@Test
	void rendersCustomComponentWithBoundValues()
	{
		tester.startPage(new A2uiTestPage(A2uiTestPage.surface(REPORTING,
			"{'months':['jan','feb'],'sales':[{'name':'NL','values':[1,2.5]}]}",
			"{'id':'root','component':'Chart','type':'stackedBar','labels':{'path':'/months'}," +
				"'series':{'path':'/sales'},'title':{'call':'formatString','args':{'value':'Sales in ${/months/0}'}}}"),
			reporting()));

		tester.assertContains(
			"STACKED_BAR of \\[jan, feb\\] \\[Series\\[name=NL, values=\\[1.0, 2.5\\]\\]\\] titled Sales in jan");
	}

	@Test
	void skipsComponentWithInvalidProperties()
	{
		tester.startPage(new A2uiTestPage(A2uiTestPage.surface(REPORTING, "{}",
			"{'id':'root','component':'Column','children':['bad','good']}," +
				"{'id':'bad','component':'Chart','type':'pie','labels':[]}," +
				"{'id':'good','component':'Text','text':'still here'}"),
			reporting()));

		tester.assertContains("still here");
		tester.assertContainsNot("pie");
	}

	@Test
	void rejectsRecordsItCannotKeep()
	{
		A2uiCatalog.Builder builder = A2uiCatalog.builder(REPORTING);

		assertThrows(IllegalArgumentException.class, () -> builder.component("X",
			NotSerializable.class, (id, props, context) -> new Label(id)));
		assertThrows(IllegalArgumentException.class, () -> builder.component("X",
			ClaimsCommonProperty.class, (id, props, context) -> new Label(id)));
	}

	@Test
	void bindsLiteralProperties()
	{
		ChartProps props = ValueType.bindRecord(ChartProps.class,
			Map.of("type", "line", "labels", List.of("a")));

		assertEquals(ChartType.LINE, props.type());
		assertEquals(null, props.labels().getPath());
		assertThrows(A2uiProtocolException.class,
			() -> ValueType.bindRecord(ChartProps.class, Map.of("labels", List.of("a"))));
		assertThrows(A2uiProtocolException.class, () -> ValueType.bindRecord(ChartProps.class,
			Map.of("type", "line", "labels", List.of(1))));
	}
}
