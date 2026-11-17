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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.wicket.util.io.IClusterable;
import org.apache.wicket.util.lang.Args;

/**
 * One component of a surface as the agent described it: its id, its type in the catalog, and the
 * rest of its properties. Property values are unchecked agent output; a catalog validates what it
 * reads.
 *
 * @param id
 *            the id, unique within the surface
 * @param type
 *            the component type, e.g. {@code Text} or {@code Button}
 * @param properties
 *            all other properties, as plain Java values
 * @since 12.0.0
 */
public record A2uiComponentDefinition(String id, String type, Map<String, Object> properties)
	implements
		IClusterable
{
	/**
	 * @param id
	 *            the id, unique within the surface
	 * @param type
	 *            the component type
	 * @param properties
	 *            all other properties
	 */
	public A2uiComponentDefinition
	{
		Args.notEmpty(id, "id");
		Args.notEmpty(type, "type");
		properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
	}

	/**
	 * @param name
	 *            the property name
	 * @return the property value, or {@code null}
	 */
	public Object property(String name)
	{
		return properties.get(name);
	}

	/**
	 * @param name
	 *            the property name
	 * @param defaultValue
	 *            returned when the property is absent or not a string
	 * @return the property when it is a string literal, otherwise the default
	 */
	public String stringProperty(String name, String defaultValue)
	{
		return properties.get(name) instanceof String value ? value : defaultValue;
	}

	/**
	 * @param name
	 *            the property name
	 * @param defaultValue
	 *            returned when the property is absent or not a number
	 * @return the property when it is a number literal, otherwise the default
	 */
	public double numberProperty(String name, double defaultValue)
	{
		return properties.get(name) instanceof Number value ? value.doubleValue() : defaultValue;
	}

	/**
	 * @param name
	 *            the property name
	 * @param defaultValue
	 *            returned when the property is absent or not a boolean
	 * @return the property when it is a boolean literal, otherwise the default
	 */
	public boolean booleanProperty(String name, boolean defaultValue)
	{
		return properties.get(name) instanceof Boolean value ? value : defaultValue;
	}
}
