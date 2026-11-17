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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.util.io.IClusterable;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONException;
import com.github.openjson.JSONObject;

/**
 * The surfaces of one conversation with an agent. Feed it the agent's server-to-client messages
 * with {@link #process(CharSequence)}, and render a surface with an {@link A2uiSurfacePanel}.
 * <p>
 * Everything in these messages is agent output, which may have been steered by whatever the agent
 * read. It is validated against the protocol here, and the catalogs decide what of it reaches the
 * page; nothing is ever written into the markup unescaped.
 *
 * @since 12.0.0
 */
public class A2uiSurfaces implements IClusterable
{
	private static final long serialVersionUID = 1L;

	/** The protocol version of the messages this module writes. */
	public static final String VERSION = "v0.9.1";

	private static final Set<String> MESSAGE_TYPES = Set.of("createSurface", "updateComponents",
		"updateDataModel", "deleteSurface");

	private final Map<String, A2uiSurface> surfaces = new LinkedHashMap<>();

	/**
	 * Applies a stream of messages in JSON Lines format: one message per line, blank lines
	 * ignored. Messages before a malformed one have been applied when the exception is thrown.
	 *
	 * @param messages
	 *            the messages
	 * @throws A2uiProtocolException
	 *             when a message is not valid A2UI
	 */
	public void process(CharSequence messages)
	{
		String[] lines = messages.toString().split("\\R");
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i].strip();
			if (line.isEmpty())
			{
				continue;
			}
			JSONObject message;
			try
			{
				message = new JSONObject(line);
			}
			catch (JSONException e)
			{
				throw new A2uiProtocolException("Line " + (i + 1) + " is not a JSON object", e);
			}
			process(message);
		}
	}

	/**
	 * Applies one message.
	 *
	 * @param message
	 *            the message
	 * @throws A2uiProtocolException
	 *             when the message is not valid A2UI
	 */
	public void process(JSONObject message)
	{
		if (!(message.opt("version") instanceof String version) ||
			version.startsWith("v0.9") == false)
		{
			throw new A2uiProtocolException("Unsupported A2UI version: " + message.opt("version"));
		}
		List<String> types = new ArrayList<>();
		for (String key : message.keySet())
		{
			if (key.equals("version") == false)
			{
				types.add(key);
			}
		}
		if (types.size() != 1 || MESSAGE_TYPES.contains(types.get(0)) == false)
		{
			throw new A2uiProtocolException("A message needs exactly one of " + MESSAGE_TYPES +
				", found " + types);
		}
		String type = types.get(0);
		if (!(message.opt(type) instanceof JSONObject body))
		{
			throw new A2uiProtocolException(type + " must be an object");
		}
		switch (type)
		{
			case "createSurface" -> createSurface(body);
			case "updateComponents" -> updateComponents(body);
			case "updateDataModel" -> updateDataModel(body);
			default -> surfaces.remove(requiredString(body, "surfaceId"));
		}
	}

	/**
	 * @param surfaceId
	 *            the surface id
	 * @return the surface, or {@code null} when it does not exist
	 */
	public A2uiSurface get(String surfaceId)
	{
		return surfaces.get(surfaceId);
	}

	/**
	 * @return all surfaces, in the order they were created
	 */
	public Collection<A2uiSurface> getAll()
	{
		return Collections.unmodifiableCollection(surfaces.values());
	}

	private void createSurface(JSONObject body)
	{
		String surfaceId = requiredString(body, "surfaceId");
		if (surfaces.containsKey(surfaceId))
		{
			throw new A2uiProtocolException("Surface " + surfaceId +
				" already exists; delete it before creating it again");
		}
		Object theme = Json.toJava(body.opt("theme"));
		if (theme != null && theme instanceof Map == false)
		{
			throw new A2uiProtocolException("theme must be an object");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> themeMap = theme != null ? (Map<String, Object>)theme : Map.of();
		surfaces.put(surfaceId, new A2uiSurface(surfaceId, requiredString(body, "catalogId"),
			themeMap, Boolean.TRUE.equals(body.opt("sendDataModel"))));
	}

	private void updateComponents(JSONObject body)
	{
		A2uiSurface surface = existingSurface(body);
		if (!(body.opt("components") instanceof JSONArray components))
		{
			throw new A2uiProtocolException("updateComponents needs a components array");
		}
		List<A2uiComponentDefinition> definitions = new ArrayList<>();
		for (int i = 0; i < components.length(); i++)
		{
			if (!(components.opt(i) instanceof JSONObject component))
			{
				throw new A2uiProtocolException("Component " + i + " is not an object");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> properties = (Map<String, Object>)Json.toJava(component);
			Object id = properties.remove("id");
			Object componentType = properties.remove("component");
			if (!(id instanceof String idString) || idString.isEmpty() ||
				!(componentType instanceof String typeString) || typeString.isEmpty())
			{
				throw new A2uiProtocolException("Component " + i +
					" needs a string id and component type");
			}
			definitions.add(new A2uiComponentDefinition(idString, typeString, properties));
		}
		surface.updateComponents(definitions);
	}

	private void updateDataModel(JSONObject body)
	{
		A2uiSurface surface = existingSurface(body);
		Object path = body.opt("path");
		if (path != null && path instanceof String == false)
		{
			throw new A2uiProtocolException("path must be a JSON Pointer string");
		}
		surface.updateDataModel((String)path, body.has("value"), Json.toJava(body.opt("value")));
	}

	private A2uiSurface existingSurface(JSONObject body)
	{
		String surfaceId = requiredString(body, "surfaceId");
		A2uiSurface surface = surfaces.get(surfaceId);
		if (surface == null)
		{
			throw new A2uiProtocolException("Surface " + surfaceId + " does not exist");
		}
		return surface;
	}

	private static String requiredString(JSONObject body, String key)
	{
		if (body.opt(key) instanceof String value && value.isEmpty() == false)
		{
			return value;
		}
		throw new A2uiProtocolException(key + " is required");
	}
}
