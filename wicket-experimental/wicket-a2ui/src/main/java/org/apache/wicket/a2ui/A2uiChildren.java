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
import java.util.List;

import org.apache.wicket.util.io.IClusterable;

/**
 * A property holding the children of a container: either a fixed list of component ids, or a
 * template component repeated for every element of an array in the data model. Add them to a
 * {@link org.apache.wicket.markup.repeater.RepeatingView} with
 * {@link A2uiRenderContext#addChildren(org.apache.wicket.markup.repeater.RepeatingView, A2uiChildren)}.
 *
 * @since 12.0.0
 */
public final class A2uiChildren implements IClusterable
{
	private static final long serialVersionUID = 1L;

	private final List<String> ids;

	private final String path;

	private final String componentId;

	private A2uiChildren(List<String> ids, String path, String componentId)
	{
		this.ids = ids;
		this.path = path;
		this.componentId = componentId;
	}

	static A2uiChildren ofIds(List<String> ids)
	{
		return new A2uiChildren(Collections.unmodifiableList(ids), null, null);
	}

	static A2uiChildren ofTemplate(String path, String componentId)
	{
		return new A2uiChildren(null, path, componentId);
	}

	/**
	 * @return whether the children are a template repeated over an array
	 */
	public boolean isTemplate()
	{
		return ids == null;
	}

	List<String> ids()
	{
		return ids;
	}

	String path()
	{
		return path;
	}

	String componentId()
	{
		return componentId;
	}
}
