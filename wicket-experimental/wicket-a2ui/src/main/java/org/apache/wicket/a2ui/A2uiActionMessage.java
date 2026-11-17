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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.openjson.JSONObject;

/**
 * The client-to-server message for a user action on a surface: the event the agent attached to a
 * component, with its context resolved against the data model at the moment the user acted.
 *
 * @param name
 *            the event name the agent chose
 * @param surfaceId
 *            the surface the action happened on
 * @param sourceComponentId
 *            the id of the component that fired it
 * @param timestamp
 *            when it happened
 * @param context
 *            the resolved context values
 * @param dataModel
 *            the surface's whole data model when the agent asked for it with
 *            {@code sendDataModel}, otherwise {@code null}
 * @since 12.0.0
 */
public record A2uiActionMessage(String name, String surfaceId, String sourceComponentId,
	Instant timestamp, Map<String, Object> context, Object dataModel)
{
	/**
	 * @param name
	 *            the event name
	 * @param surfaceId
	 *            the surface
	 * @param sourceComponentId
	 *            the component that fired it
	 * @param timestamp
	 *            when it happened
	 * @param context
	 *            the resolved context values
	 * @param dataModel
	 *            the data model, or {@code null}
	 */
	public A2uiActionMessage
	{
		context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
	}

	/**
	 * @return the client-to-server {@code action} message
	 */
	public JSONObject toJson()
	{
		JSONObject action = new JSONObject();
		action.put("name", name);
		action.put("surfaceId", surfaceId);
		action.put("sourceComponentId", sourceComponentId);
		action.put("timestamp", timestamp.toString());
		action.put("context", Json.toJson(context));

		JSONObject message = new JSONObject();
		message.put("version", A2uiSurfaces.VERSION);
		message.put("action", action);
		return message;
	}

	/**
	 * @return the {@code a2uiClientDataModel} transport metadata to send along with the action, or
	 *         {@code null} when the agent did not ask for the data model
	 */
	public JSONObject toClientDataModelJson()
	{
		if (dataModel == null)
		{
			return null;
		}
		JSONObject surfaces = new JSONObject();
		surfaces.put(surfaceId, Json.toJson(dataModel));
		JSONObject metadata = new JSONObject();
		metadata.put("surfaces", surfaces);
		return metadata;
	}
}
