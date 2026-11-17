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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.util.io.IClusterable;

/**
 * What was rendered for one component of a surface, and what that depended on. A rendered
 * component stays as long as its definition and the lengths of the arrays its templates repeat
 * over do not change; only then is it built again.
 */
final class RenderedComponent implements IClusterable
{
	private static final long serialVersionUID = 1L;

	final String componentId;

	final String scope;

	final String wicketId;

	/** The ids of the components this one is nested in, outermost first. */
	final List<String> ancestors;

	final A2uiComponentDefinition definition;

	final Map<String, Integer> templateLengths = new HashMap<>();

	final Set<String> boundPaths = new LinkedHashSet<>();

	final List<RenderedComponent> children = new ArrayList<>();

	Component component;

	RenderedComponent(String componentId, String scope, String wicketId, List<String> ancestors,
		A2uiComponentDefinition definition)
	{
		this.componentId = componentId;
		this.scope = scope;
		this.wicketId = wicketId;
		this.ancestors = ancestors;
		this.definition = definition;
	}

	boolean isOutdated(A2uiSurface surface)
	{
		if (Objects.equals(definition, surface.getComponent(componentId)) == false)
		{
			return true;
		}
		for (Map.Entry<String, Integer> template : templateLengths.entrySet())
		{
			if (templateLength(surface, template.getKey()) != template.getValue())
			{
				return true;
			}
		}
		return false;
	}

	boolean isBoundToAny(List<String> changedPaths)
	{
		for (String bound : boundPaths)
		{
			for (String changed : changedPaths)
			{
				if (overlaps(bound, changed))
				{
					return true;
				}
			}
		}
		return false;
	}

	static int templateLength(A2uiSurface surface, String arrayPath)
	{
		return surface.getDataModel().get(arrayPath) instanceof List<?> items ? items.size() : -1;
	}

	/**
	 * Whether a change at one pointer can affect a value at the other: one contains the other.
	 */
	private static boolean overlaps(String a, String b)
	{
		return contains(a, b) || contains(b, a);
	}

	private static boolean contains(String outer, String inner)
	{
		if (outer.equals("/") || outer.isEmpty() || outer.equals(inner))
		{
			return true;
		}
		return inner.startsWith(outer + "/");
	}
}
