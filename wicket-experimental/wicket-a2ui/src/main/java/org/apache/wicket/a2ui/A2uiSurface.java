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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.wicket.util.io.IClusterable;

/**
 * The state of one A2UI surface: the catalog it draws from, its components and its data model.
 * Surfaces are created and changed by {@link A2uiSurfaces#process(CharSequence)}; an
 * {@link A2uiSurfacePanel} renders one.
 *
 * @since 12.0.0
 */
public final class A2uiSurface implements IClusterable
{
	private static final long serialVersionUID = 1L;

	/** The id of the component every surface renders from. */
	public static final String ROOT_ID = "root";

	private final String id;

	private final String catalogId;

	private final Map<String, Object> theme;

	private final boolean sendDataModel;

	private final Map<String, A2uiComponentDefinition> components = new LinkedHashMap<>();

	private final A2uiDataModel dataModel = new A2uiDataModel();

	/** Data model changes older than this many are forgotten; a panel that missed them redraws all. */
	private static final int MAX_DATA_CHANGES = 1000;

	private final String instanceKey = UUID.randomUUID().toString();

	private final Deque<String> dataChanges = new ArrayDeque<>();

	private long dataVersion;

	A2uiSurface(String id, String catalogId, Map<String, Object> theme, boolean sendDataModel)
	{
		this.id = id;
		this.catalogId = catalogId;
		this.theme = Collections.unmodifiableMap(new LinkedHashMap<>(theme));
		this.sendDataModel = sendDataModel;
	}

	/**
	 * @return the surface id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * @return the id of the catalog the components come from
	 */
	public String getCatalogId()
	{
		return catalogId;
	}

	/**
	 * @return the theme properties, e.g. {@code primaryColor}; unchecked agent output
	 */
	public Map<String, Object> getTheme()
	{
		return theme;
	}

	/**
	 * @return whether the agent asked to receive the data model with every action
	 */
	public boolean isSendDataModel()
	{
		return sendDataModel;
	}

	/**
	 * @param componentId
	 *            the component id
	 * @return the component, or {@code null} when the agent has not sent it (yet)
	 */
	public A2uiComponentDefinition getComponent(String componentId)
	{
		return components.get(componentId);
	}

	/**
	 * @return all components, in the order they first arrived
	 */
	public Map<String, A2uiComponentDefinition> getComponents()
	{
		return Collections.unmodifiableMap(components);
	}

	/**
	 * @return the data model the components bind to
	 */
	public A2uiDataModel getDataModel()
	{
		return dataModel;
	}

	/**
	 * @return a key that differs between any two surfaces, even when they have the same id
	 */
	String getInstanceKey()
	{
		return instanceKey;
	}

	/**
	 * @return the number of data model updates the agent has made
	 */
	long getDataVersion()
	{
		return dataVersion;
	}

	/**
	 * @param version
	 *            a data version seen earlier
	 * @return the JSON Pointers the agent wrote since that version, or {@code null} when they are no
	 *         longer known
	 */
	List<String> getDataChangesSince(long version)
	{
		long missed = dataVersion - version;
		if (missed > dataChanges.size())
		{
			return null;
		}
		List<String> changes = new ArrayList<>(dataChanges);
		return changes.subList(changes.size() - (int)missed, changes.size());
	}

	void updateComponents(Iterable<A2uiComponentDefinition> updates)
	{
		for (A2uiComponentDefinition component : updates)
		{
			components.put(component.id(), component);
		}
	}

	void updateDataModel(String path, boolean hasValue, Object value)
	{
		if (hasValue)
		{
			dataModel.set(path, value);
		}
		else
		{
			dataModel.remove(path);
		}
		dataChanges.addLast(path == null || path.isEmpty() ? "/" : path);
		if (dataChanges.size() > MAX_DATA_CHANGES)
		{
			dataChanges.removeFirst();
		}
		dataVersion++;
	}
}
