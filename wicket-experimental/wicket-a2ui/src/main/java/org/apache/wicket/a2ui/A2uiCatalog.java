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

import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.util.io.IClusterable;
import org.apache.wicket.util.lang.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

/**
 * The component types an agent may use on a surface, and how each becomes a Wicket component. A
 * surface names its catalog by id in {@code createSurface}; a component type the catalog does not
 * know is not rendered.
 * <p>
 * Each component type is described by a serializable record. The record components are the properties the
 * agent may set, and their types decide both how the agent's JSON is bound and what the catalog's
 * JSON Schema tells the agent. Give the agent that schema, from {@link #toJsonSchema()}, in its
 * instructions or as an inline catalog; it is all the agent knows about your components.
 *
 * <pre>
 * public record ChartProps(
 * 	&#64;A2uiRequired &#64;A2uiDescription("bar, line or pie") ChartType type,
 * 	&#64;A2uiRequired A2uiValue&lt;List&lt;String&gt;&gt; labels,
 * 	A2uiValue&lt;String&gt; title) {}
 *
 * A2uiCatalog catalog = A2uiCatalog.builder("https://example.com/catalogs/reporting/v1")
 * 	.include(BasicCatalog.get())
 * 	.component("Chart", "A chart of one or more series", ChartProps.class,
 * 		(wicketId, props, context) -&gt; new ChartPanel(wicketId, props, context))
 * 	.build();
 * </pre>
 *
 * Every property is agent output. Expose the few properties the agent needs rather than every
 * option of the underlying component, and never let a property reach markup or JavaScript
 * unescaped.
 *
 * @since 12.0.0
 */
public final class A2uiCatalog implements IClusterable
{
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory.getLogger(A2uiCatalog.class);

	private final String id;

	private final Map<String, ComponentType<?>> componentTypes;

	private A2uiCatalog(String id, Map<String, ComponentType<?>> componentTypes)
	{
		this.id = id;
		this.componentTypes = Collections.unmodifiableMap(new LinkedHashMap<>(componentTypes));
	}

	/**
	 * @param id
	 *            the catalog id surfaces refer to, conventionally the URL of its schema
	 * @return a builder for a new catalog
	 */
	public static Builder builder(String id)
	{
		return new Builder(id);
	}

	/**
	 * @return the catalog id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * @return the names of the component types in this catalog
	 */
	public Set<String> getComponentTypes()
	{
		return componentTypes.keySet();
	}

	/**
	 * Creates the Wicket component for a component the agent sent.
	 *
	 * @param wicketId
	 *            the Wicket id
	 * @param definition
	 *            the component as the agent sent it
	 * @param context
	 *            the render context of the component
	 * @return the component, or {@code null} when the type is unknown or its properties are invalid
	 */
	public Component newComponent(String wicketId, A2uiComponentDefinition definition,
		A2uiRenderContext context)
	{
		ComponentType<?> type = componentTypes.get(definition.type());
		if (type == null)
		{
			LOG.debug("Catalog {} has no component type {}", id, definition.type());
			return null;
		}
		try
		{
			return type.create(wicketId, definition, context);
		}
		catch (A2uiProtocolException e)
		{
			LOG.warn("Skipping component {} of type {}: {}", definition.id(), definition.type(),
				e.getMessage());
			return null;
		}
	}

	/**
	 * Describes this catalog as a JSON Schema: every component type with its properties, their
	 * types and descriptions, and the common definitions they refer to.
	 *
	 * @return the schema
	 */
	public JSONObject toJsonSchema()
	{
		JSONObject components = new JSONObject();
		componentTypes.forEach((name, type) -> components.put(name, type.schema()));

		JSONObject schema = new JSONObject();
		schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
		schema.put("$id", id);
		schema.put("catalogId", id);
		schema.put("components", components);
		schema.put("$defs", commonDefinitions());
		return schema;
	}

	private static JSONObject commonDefinitions()
	{
		JSONObject defs = new JSONObject();
		defs.put("ComponentId", new JSONObject().put("type", "string")
			.put("description", "The id of another component on the same surface"));

		defs.put("DataBinding", new JSONObject().put("type", "object")
			.put("properties", new JSONObject().put("path", new JSONObject().put("type", "string")
				.put("description",
					"A JSON Pointer into the data model; relative to the current item inside a template")))
			.put("required", new JSONArray().put("path"))
			.put("additionalProperties", false));

		defs.put("FunctionCall", new JSONObject().put("type", "object")
			.put("properties", new JSONObject().put("call", new JSONObject().put("const", "formatString"))
				.put("args", new JSONObject().put("type", "object")
					.put("properties", new JSONObject().put("value", new JSONObject().put("type", "string")
						.put("description", "Text with ${/json/pointer} placeholders")))
					.put("required", new JSONArray().put("value"))))
			.put("required", new JSONArray().put("call").put("args")));

		JSONObject template = new JSONObject().put("type", "object")
			.put("properties", new JSONObject().put("path", new JSONObject().put("type", "string")
				.put("description", "JSON Pointer to an array in the data model"))
				.put("componentId", new JSONObject().put("$ref", "#/$defs/ComponentId")
					.put("description", "The component repeated for every array element")))
			.put("required", new JSONArray().put("path").put("componentId"))
			.put("additionalProperties", false);
		defs.put("ChildList", new JSONObject().put("oneOf", new JSONArray()
			.put(new JSONObject().put("type", "array")
				.put("items", new JSONObject().put("$ref", "#/$defs/ComponentId")))
			.put(template)));

		JSONObject contextValue = new JSONObject().put("oneOf", new JSONArray()
			.put(new JSONObject().put("type", new JSONArray().put("string").put("number").put("boolean")))
			.put(new JSONObject().put("$ref", "#/$defs/DataBinding")));
		defs.put("Action", new JSONObject().put("type", "object")
			.put("properties", new JSONObject().put("event", new JSONObject().put("type", "object")
				.put("properties", new JSONObject().put("name", new JSONObject().put("type", "string"))
					.put("context", new JSONObject().put("type", "object")
						.put("additionalProperties", contextValue)))
				.put("required", new JSONArray().put("name"))))
			.put("required", new JSONArray().put("event")));
		return defs;
	}

	private record ComponentType<P extends Record>(String name, String description,
		Class<P> propertiesClass, A2uiComponentFactory<P> factory) implements IClusterable
	{
		Component create(String wicketId, A2uiComponentDefinition definition,
			A2uiRenderContext context)
		{
			P properties = ValueType.bindRecord(propertiesClass, definition.properties());
			return factory.create(wicketId, properties, context);
		}

		JSONObject schema()
		{
			JSONObject schema = ValueType.recordSchema(propertiesClass);
			if (description != null)
			{
				schema.put("description", description);
			}
			JSONObject properties = schema.getJSONObject("properties");
			properties.put("component", new JSONObject().put("const", name));
			properties.put("id", new JSONObject().put("type", "string"));
			properties.put("weight", new JSONObject().put("type", "number").put("description",
				"Share of the free space in a Row or Column"));
			JSONArray required = schema.optJSONArray("required");
			if (required == null)
			{
				required = new JSONArray();
				schema.put("required", required);
			}
			required.put("component").put("id");
			return schema;
		}
	}

	/**
	 * Builds an {@link A2uiCatalog}.
	 *
	 * @since 12.0.0
	 */
	public static final class Builder
	{
		private final String id;

		private final Map<String, ComponentType<?>> componentTypes = new LinkedHashMap<>();

		private Builder(String id)
		{
			this.id = Args.notEmpty(id, "id");
		}

		/**
		 * Adds all component types of another catalog, typically the {@link BasicCatalog}.
		 *
		 * @param catalog
		 *            the catalog
		 * @return this
		 */
		public Builder include(A2uiCatalog catalog)
		{
			componentTypes.putAll(catalog.componentTypes);
			return this;
		}

		/**
		 * Adds a component type, replacing one of the same name.
		 *
		 * @param <P>
		 *            the property record
		 * @param name
		 *            the component type name the agent uses, e.g. {@code Chart}
		 * @param propertiesClass
		 *            the record whose components are the properties
		 * @param factory
		 *            creates the Wicket component
		 * @return this
		 * @throws IllegalArgumentException
		 *             when the record is not serializable or has a property type the catalog
		 *             cannot bind
		 */
		public <P extends Record> Builder component(String name, Class<P> propertiesClass,
			A2uiComponentFactory<P> factory)
		{
			return component(name, null, propertiesClass, factory);
		}

		/**
		 * Adds a component type, replacing one of the same name.
		 *
		 * @param <P>
		 *            the property record
		 * @param name
		 *            the component type name the agent uses, e.g. {@code Chart}
		 * @param description
		 *            what the component is for, for the agent; may be {@code null}
		 * @param propertiesClass
		 *            the record whose components are the properties
		 * @param factory
		 *            creates the Wicket component
		 * @return this
		 * @throws IllegalArgumentException
		 *             when the record is not serializable or has a property type the catalog
		 *             cannot bind
		 */
		public <P extends Record> Builder component(String name, String description,
			Class<P> propertiesClass, A2uiComponentFactory<P> factory)
		{
			Args.notEmpty(name, "name");
			Args.notNull(propertiesClass, "propertiesClass");
			Args.notNull(factory, "factory");
			ValueType.of(propertiesClass);
			for (RecordComponent component : propertiesClass.getRecordComponents())
			{
				if (ValueType.COMMON_PROPERTIES.contains(component.getName()))
				{
					throw new IllegalArgumentException(propertiesClass.getName() +
						" declares the common property " + component.getName());
				}
			}
			componentTypes.put(name,
				new ComponentType<>(name, description, propertiesClass, factory));
			return this;
		}

		/**
		 * @return the catalog
		 */
		public A2uiCatalog build()
		{
			return new A2uiCatalog(id, componentTypes);
		}
	}
}
