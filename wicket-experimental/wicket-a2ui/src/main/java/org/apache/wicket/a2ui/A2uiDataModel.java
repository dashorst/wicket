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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.util.io.IClusterable;

/**
 * The data model of an A2UI surface: a JSON document that components bind to through JSON
 * Pointers. The agent writes it with {@code updateDataModel} messages, and input components write
 * the user's edits into it.
 * <p>
 * Objects are kept as {@link Map}s and arrays as {@link List}s, so the model is serializable along
 * with the page that renders it.
 *
 * @since 12.0.0
 */
public final class A2uiDataModel implements IClusterable
{
	private static final long serialVersionUID = 1L;

	private Object root = new LinkedHashMap<String, Object>();

	/**
	 * @param pointer
	 *            a JSON Pointer; empty or {@code "/"} for the whole document
	 * @return the value at the pointer, or {@code null} when nothing is there
	 */
	public Object get(String pointer)
	{
		Object current = root;
		for (String token : JsonPointer.parse(pointer))
		{
			current = child(current, token);
			if (current == null)
			{
				return null;
			}
		}
		return current;
	}

	/**
	 * Writes a value, creating the objects on the way that do not exist yet. Writing to the root
	 * replaces the whole document. An array index one past the end appends.
	 *
	 * @param pointer
	 *            a JSON Pointer
	 * @param value
	 *            a {@link Map}, {@link List}, {@link String}, {@link Number}, {@link Boolean} or
	 *            {@code null}
	 */
	public void set(String pointer, Object value)
	{
		List<String> tokens = JsonPointer.parse(pointer);
		if (tokens.isEmpty())
		{
			root = value != null ? value : new LinkedHashMap<String, Object>();
			return;
		}
		Object current = root;
		for (int i = 0; i < tokens.size() - 1; i++)
		{
			Object next = child(current, tokens.get(i));
			if (next instanceof Map == false && next instanceof List == false)
			{
				next = new LinkedHashMap<String, Object>();
				put(current, tokens.get(i), next);
			}
			current = next;
		}
		put(current, tokens.get(tokens.size() - 1), value);
	}

	/**
	 * Removes the value at the pointer. A removed array element becomes {@code null}, so the array
	 * keeps its length.
	 *
	 * @param pointer
	 *            a JSON Pointer
	 */
	public void remove(String pointer)
	{
		List<String> tokens = JsonPointer.parse(pointer);
		if (tokens.isEmpty())
		{
			root = new LinkedHashMap<String, Object>();
			return;
		}
		Object parent = get(pointerOf(tokens.subList(0, tokens.size() - 1)));
		String last = tokens.get(tokens.size() - 1);
		if (parent instanceof Map<?, ?> map)
		{
			map.remove(last);
		}
		else if (parent instanceof List<?> list)
		{
			int index = JsonPointer.index(last);
			if (index >= 0 && index < list.size())
			{
				put(parent, last, null);
			}
		}
	}

	/**
	 * @return the whole document as an openjson value
	 */
	public Object toJson()
	{
		return Json.toJson(root);
	}

	private static Object child(Object container, String token)
	{
		if (container instanceof Map<?, ?> map)
		{
			return map.get(token);
		}
		if (container instanceof List<?> list)
		{
			int index = JsonPointer.index(token);
			return index >= 0 && index < list.size() ? list.get(index) : null;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static void put(Object container, String token, Object value)
	{
		if (container instanceof Map<?, ?> map)
		{
			((Map<String, Object>)map).put(token, value);
		}
		else if (container instanceof List<?> list)
		{
			int index = JsonPointer.index(token);
			if (index < 0 || index > list.size())
			{
				throw new A2uiProtocolException("No array index " + token + " in an array of " +
					list.size());
			}
			List<Object> objects = (List<Object>)list;
			if (index == objects.size())
			{
				objects.add(value);
			}
			else
			{
				objects.set(index, value);
			}
		}
	}

	private static String pointerOf(List<String> tokens)
	{
		StringBuilder pointer = new StringBuilder();
		for (String token : tokens)
		{
			pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
		}
		return pointer.toString();
	}
}
