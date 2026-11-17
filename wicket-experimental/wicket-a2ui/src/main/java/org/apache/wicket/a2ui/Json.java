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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

/**
 * Converts between openjson values and the plain, serializable Java values a surface keeps:
 * {@link LinkedHashMap}, {@link ArrayList}, {@link String}, {@link Number}, {@link Boolean} and
 * {@code null}.
 */
final class Json
{
	private Json()
	{
	}

	static Object toJava(Object json)
	{
		if (json == null || json == JSONObject.NULL)
		{
			return null;
		}
		if (json instanceof JSONObject object)
		{
			Map<String, Object> map = new LinkedHashMap<>();
			Iterator<String> keys = object.keys();
			while (keys.hasNext())
			{
				String key = keys.next();
				map.put(key, toJava(object.opt(key)));
			}
			return map;
		}
		if (json instanceof JSONArray array)
		{
			List<Object> list = new ArrayList<>(array.length());
			for (int i = 0; i < array.length(); i++)
			{
				list.add(toJava(array.opt(i)));
			}
			return list;
		}
		if (json instanceof String || json instanceof Number || json instanceof Boolean)
		{
			return json;
		}
		throw new A2uiProtocolException("Unsupported JSON value: " + json.getClass().getName());
	}

	static Object toJson(Object java)
	{
		if (java == null)
		{
			return JSONObject.NULL;
		}
		if (java instanceof Map<?, ?> map)
		{
			JSONObject object = new JSONObject();
			map.forEach((key, value) -> object.put(String.valueOf(key), toJson(value)));
			return object;
		}
		if (java instanceof List<?> list)
		{
			JSONArray array = new JSONArray();
			list.forEach(value -> array.put(toJson(value)));
			return array;
		}
		return normalizeNumber(java);
	}

	/**
	 * Copies a value that has to go out as JSON, refusing anything that is not a JSON value.
	 *
	 * @throws IllegalArgumentException
	 *             for a value that has no JSON representation
	 */
	static Object copyJsonValue(Object value)
	{
		if (value == null || value instanceof String || value instanceof Boolean)
		{
			return value;
		}
		if (value instanceof Number number)
		{
			if (number instanceof Double d && (d.isNaN() || d.isInfinite()))
			{
				throw new IllegalArgumentException("Not a JSON number: " + d);
			}
			return number;
		}
		if (value instanceof Map<?, ?> map)
		{
			Map<String, Object> copy = new LinkedHashMap<>();
			map.forEach((key, item) -> {
				if (key instanceof String == false)
				{
					throw new IllegalArgumentException("Not a JSON object key: " + key);
				}
				copy.put((String)key, copyJsonValue(item));
			});
			return copy;
		}
		if (value instanceof List<?> list)
		{
			List<Object> copy = new ArrayList<>(list.size());
			list.forEach(item -> copy.add(copyJsonValue(item)));
			return copy;
		}
		throw new IllegalArgumentException("Not a JSON value: " + value.getClass().getName());
	}

	/**
	 * Renders a value the way A2UI interpolates it into a string: {@code null} becomes empty,
	 * objects and arrays become JSON.
	 */
	static String toDisplayString(Object value)
	{
		if (value == null)
		{
			return "";
		}
		if (value instanceof Map || value instanceof List)
		{
			return toJson(value).toString();
		}
		return String.valueOf(normalizeNumber(value));
	}

	private static Object normalizeNumber(Object value)
	{
		if (value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15)
		{
			return d.longValue();
		}
		if (value instanceof Float f && f == Math.rint(f) && Math.abs(f) < 1e7)
		{
			return f.longValue();
		}
		return value;
	}
}
