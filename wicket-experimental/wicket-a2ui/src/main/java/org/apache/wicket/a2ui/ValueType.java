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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.util.io.IClusterable;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

/**
 * The type of a component property, derived from a record component. It binds what the agent sent
 * to that type, and describes the type in the catalog's JSON Schema, so both always agree.
 */
final class ValueType implements IClusterable
{
	private static final long serialVersionUID = 1L;

	/** Properties every component has, which a property record must not declare. */
	static final Set<String> COMMON_PROPERTIES = Set.of("id", "component", "weight");

	enum Kind
	{
		STRING, BOOLEAN, DOUBLE, INTEGER, LONG, ENUM, LIST, RECORD, COMPONENT_ID, CHILDREN, ACTION,
		VALUE
	}

	private final Kind kind;

	private final Class<?> javaClass;

	private final ValueType element;

	private ValueType(Kind kind, Class<?> javaClass, ValueType element)
	{
		this.kind = kind;
		this.javaClass = javaClass;
		this.element = element;
	}

	static ValueType of(Type type)
	{
		if (type instanceof Class<?> c)
		{
			if (c == String.class)
			{
				return new ValueType(Kind.STRING, c, null);
			}
			if (c == boolean.class || c == Boolean.class)
			{
				return new ValueType(Kind.BOOLEAN, c, null);
			}
			if (c == double.class || c == Double.class)
			{
				return new ValueType(Kind.DOUBLE, c, null);
			}
			if (c == int.class || c == Integer.class)
			{
				return new ValueType(Kind.INTEGER, c, null);
			}
			if (c == long.class || c == Long.class)
			{
				return new ValueType(Kind.LONG, c, null);
			}
			if (c.isEnum())
			{
				return new ValueType(Kind.ENUM, c, null);
			}
			if (c == A2uiComponentId.class)
			{
				return new ValueType(Kind.COMPONENT_ID, c, null);
			}
			if (c == A2uiChildren.class)
			{
				return new ValueType(Kind.CHILDREN, c, null);
			}
			if (c == A2uiAction.class)
			{
				return new ValueType(Kind.ACTION, c, null);
			}
			if (c.isRecord())
			{
				if (Serializable.class.isAssignableFrom(c) == false)
				{
					throw new IllegalArgumentException(c.getName() +
						" must be serializable, e.g. implement IClusterable: components keep it");
				}
				for (RecordComponent component : c.getRecordComponents())
				{
					of(component.getGenericType());
				}
				return new ValueType(Kind.RECORD, c, null);
			}
		}
		else if (type instanceof ParameterizedType parameterized &&
			parameterized.getActualTypeArguments().length == 1)
		{
			Type raw = parameterized.getRawType();
			ValueType argument = of(parameterized.getActualTypeArguments()[0]);
			if (raw == List.class)
			{
				return new ValueType(Kind.LIST, List.class, argument);
			}
			if (raw == A2uiValue.class && argument.isLiteralType())
			{
				return new ValueType(Kind.VALUE, A2uiValue.class, argument);
			}
		}
		throw new IllegalArgumentException("Unsupported A2UI property type: " + type);
	}

	private boolean isLiteralType()
	{
		return switch (kind)
		{
			case COMPONENT_ID, CHILDREN, ACTION, VALUE -> false;
			case LIST -> element.isLiteralType();
			default -> true;
		};
	}

	/**
	 * Binds a value the agent sent to this type.
	 *
	 * @return the bound value, or {@code null} when the agent sent none
	 * @throws A2uiProtocolException
	 *             when the value does not fit the type
	 */
	Object bind(Object raw)
	{
		if (raw == null)
		{
			return null;
		}
		switch (kind)
		{
			case STRING :
				if (raw instanceof String)
				{
					return raw;
				}
				break;
			case BOOLEAN :
				if (raw instanceof Boolean)
				{
					return raw;
				}
				break;
			case DOUBLE :
				if (raw instanceof Number number)
				{
					return number.doubleValue();
				}
				break;
			case INTEGER :
			case LONG :
				if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue()))
				{
					return kind == Kind.INTEGER ? (Object)number.intValue() : (Object)number.longValue();
				}
				break;
			case ENUM :
				if (raw instanceof String name)
				{
					for (Object constant : javaClass.getEnumConstants())
					{
						if (wireName((Enum<?>)constant).equals(name))
						{
							return constant;
						}
					}
				}
				break;
			case LIST :
				if (raw instanceof List<?> list)
				{
					List<Object> bound = new ArrayList<>(list.size());
					for (Object item : list)
					{
						bound.add(element.bind(item));
					}
					return Collections.unmodifiableList(bound);
				}
				break;
			case RECORD :
				if (raw instanceof Map<?, ?> map)
				{
					return bindRecord(javaClass, map);
				}
				break;
			case COMPONENT_ID :
				if (raw instanceof String id && id.isEmpty() == false)
				{
					return new A2uiComponentId(id);
				}
				break;
			case CHILDREN :
				return bindChildren(raw);
			case ACTION :
				return bindAction(raw);
			case VALUE :
				if (bindingPath(raw) == null && functionName(raw) == null)
				{
					element.bind(raw);
				}
				return new A2uiValue<>(raw, element);
		}
		throw new A2uiProtocolException("Expected " + describe() + ", got " + raw);
	}

	/**
	 * Reads a value resolved from the data model as this type. Data the user or the agent wrote
	 * may not match exactly, so this is lenient where A2UI is, and gives {@code null} otherwise.
	 */
	Object read(Object resolved)
	{
		if (resolved == null)
		{
			return null;
		}
		switch (kind)
		{
			case STRING :
				return Json.toDisplayString(resolved);
			case BOOLEAN :
				return resolved instanceof Boolean ? resolved : Boolean.valueOf(
					"true".equals(resolved));
			case DOUBLE :
			case INTEGER :
			case LONG :
				Double number = resolved instanceof Number n ? n.doubleValue() : parseDouble(resolved);
				if (number == null || kind == Kind.DOUBLE)
				{
					return number;
				}
				return kind == Kind.INTEGER ? (Object)number.intValue() : (Object)number.longValue();
			case LIST :
				if (resolved instanceof List<?> list)
				{
					List<Object> read = new ArrayList<>(list.size());
					for (Object item : list)
					{
						read.add(element.read(item));
					}
					return read;
				}
				return null;
			default :
				try
				{
					return bind(resolved);
				}
				catch (A2uiProtocolException e)
				{
					return null;
				}
		}
	}

	/**
	 * Converts a value of this type into what the data model keeps.
	 */
	Object write(Object value)
	{
		if (value == null)
		{
			return null;
		}
		return switch (kind)
		{
			case ENUM -> wireName((Enum<?>)value);
			case LIST -> {
				List<Object> written = new ArrayList<>();
				for (Object item : (List<?>)value)
				{
					written.add(element.write(item));
				}
				yield written;
			}
			case STRING, BOOLEAN, DOUBLE, INTEGER, LONG -> value;
			default -> throw new IllegalArgumentException("Cannot write a " + javaClass.getName() +
				" into the data model");
		};
	}

	JSONObject schema()
	{
		JSONObject schema = new JSONObject();
		switch (kind)
		{
			case STRING -> schema.put("type", "string");
			case BOOLEAN -> schema.put("type", "boolean");
			case DOUBLE -> schema.put("type", "number");
			case INTEGER, LONG -> schema.put("type", "integer");
			case ENUM -> {
				JSONArray names = new JSONArray();
				JSONArray described = new JSONArray();
				boolean anyDescription = false;
				for (Object constant : javaClass.getEnumConstants())
				{
					String name = wireName((Enum<?>)constant);
					names.put(name);
					JSONObject option = new JSONObject().put("const", name);
					String description = describe((Enum<?>)constant);
					if (description != null)
					{
						option.put("description", description);
						anyDescription = true;
					}
					described.put(option);
				}
				schema.put("type", "string");
				schema.put(anyDescription ? "oneOf" : "enum", anyDescription ? described : names);
			}
			case LIST -> {
				schema.put("type", "array");
				schema.put("items", element.schema());
			}
			case RECORD -> {
				return recordSchema(javaClass);
			}
			case COMPONENT_ID -> schema.put("$ref", "#/$defs/ComponentId");
			case CHILDREN -> schema.put("$ref", "#/$defs/ChildList");
			case ACTION -> schema.put("$ref", "#/$defs/Action");
			case VALUE -> {
				JSONArray oneOf = new JSONArray();
				oneOf.put(element.schema());
				oneOf.put(new JSONObject().put("$ref", "#/$defs/DataBinding"));
				oneOf.put(new JSONObject().put("$ref", "#/$defs/FunctionCall"));
				schema.put("oneOf", oneOf);
			}
		}
		return schema;
	}

	static JSONObject recordSchema(Class<?> recordClass)
	{
		JSONObject properties = new JSONObject();
		JSONArray required = new JSONArray();
		for (RecordComponent component : recordClass.getRecordComponents())
		{
			JSONObject property = of(component.getGenericType()).schema();
			A2uiDescription description = component.getAnnotation(A2uiDescription.class);
			if (description != null)
			{
				property.put("description", description.value());
			}
			properties.put(component.getName(), property);
			if (component.isAnnotationPresent(A2uiRequired.class))
			{
				required.put(component.getName());
			}
		}
		JSONObject schema = new JSONObject();
		schema.put("type", "object");
		schema.put("properties", properties);
		if (required.length() > 0)
		{
			schema.put("required", required);
		}
		schema.put("additionalProperties", false);
		return schema;
	}

	/**
	 * Binds a component's properties to its property record. Properties the record does not
	 * declare are ignored.
	 */
	static <R> R bindRecord(Class<R> recordClass, Map<?, ?> values)
	{
		RecordComponent[] components = recordClass.getRecordComponents();
		Class<?>[] types = new Class<?>[components.length];
		Object[] arguments = new Object[components.length];
		for (int i = 0; i < components.length; i++)
		{
			RecordComponent component = components[i];
			types[i] = component.getType();
			Object value;
			try
			{
				value = of(component.getGenericType()).bind(values.get(component.getName()));
			}
			catch (A2uiProtocolException e)
			{
				throw new A2uiProtocolException(component.getName() + ": " + e.getMessage(), e);
			}
			if (value == null && component.isAnnotationPresent(A2uiRequired.class))
			{
				throw new A2uiProtocolException(component.getName() + " is required");
			}
			arguments[i] = value == null ? defaultValue(types[i]) : value;
		}
		try
		{
			Constructor<R> constructor = recordClass.getDeclaredConstructor(types);
			constructor.trySetAccessible();
			return constructor.newInstance(arguments);
		}
		catch (InvocationTargetException e)
		{
			throw new A2uiProtocolException(recordClass.getSimpleName() + " rejected the properties",
				e.getCause());
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot create " + recordClass.getName() +
				"; is it public, or in a package open to org.apache.wicket.a2ui?", e);
		}
	}

	/**
	 * The name of an enum constant on the wire: {@code SPACE_BETWEEN} becomes
	 * {@code spaceBetween}, {@code H1} becomes {@code h1}.
	 */
	static String wireName(Enum<?> constant)
	{
		String[] words = constant.name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder name = new StringBuilder(words[0]);
		for (int i = 1; i < words.length; i++)
		{
			if (words[i].isEmpty() == false)
			{
				name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i], 1,
					words[i].length());
			}
		}
		return name.toString();
	}

	private static String describe(Enum<?> constant)
	{
		try
		{
			A2uiDescription description = constant.getDeclaringClass()
				.getField(constant.name())
				.getAnnotation(A2uiDescription.class);
			return description != null ? description.value() : null;
		}
		catch (NoSuchFieldException e)
		{
			return null;
		}
	}

	static String bindingPath(Object raw)
	{
		return raw instanceof Map<?, ?> map && map.get("call") == null &&
			map.get("path") instanceof String path ? path : null;
	}

	static String functionName(Object raw)
	{
		return raw instanceof Map<?, ?> map && map.get("call") instanceof String call ? call : null;
	}

	private static A2uiChildren bindChildren(Object raw)
	{
		if (raw instanceof List<?> list)
		{
			List<String> ids = new ArrayList<>(list.size());
			for (Object id : list)
			{
				if (id instanceof String string && string.isEmpty() == false)
				{
					ids.add(string);
				}
				else
				{
					throw new A2uiProtocolException("Expected component ids, got " + id);
				}
			}
			return A2uiChildren.ofIds(ids);
		}
		if (raw instanceof Map<?, ?> map && map.get("path") instanceof String path &&
			map.get("componentId") instanceof String componentId)
		{
			return A2uiChildren.ofTemplate(path, componentId);
		}
		throw new A2uiProtocolException("Expected a list of component ids or a template, got " + raw);
	}

	private static A2uiAction bindAction(Object raw)
	{
		if (raw instanceof Map<?, ?> map && map.get("event") instanceof Map<?, ?> event &&
			event.get("name") instanceof String name && name.isEmpty() == false)
		{
			Object context = event.get("context");
			if (context == null)
			{
				return new A2uiAction(name, Map.of());
			}
			if (context instanceof Map<?, ?> contextMap)
			{
				@SuppressWarnings("unchecked")
				Map<String, Object> values = (Map<String, Object>)contextMap;
				return new A2uiAction(name, values);
			}
		}
		throw new A2uiProtocolException("Expected an action with an event name, got " + raw);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (type == boolean.class)
		{
			return false;
		}
		if (type == double.class)
		{
			return 0d;
		}
		if (type == int.class)
		{
			return 0;
		}
		if (type == long.class)
		{
			return 0L;
		}
		return null;
	}

	private static Double parseDouble(Object value)
	{
		try
		{
			return Double.valueOf(String.valueOf(value).strip());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private String describe()
	{
		return switch (kind)
		{
			case ENUM, RECORD -> javaClass.getSimpleName();
			default -> kind.name().toLowerCase(Locale.ROOT);
		};
	}
}
