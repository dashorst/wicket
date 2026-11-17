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

import org.apache.wicket.util.io.IClusterable;

/**
 * A property whose value is either a literal or bound to the data model, through a JSON Pointer
 * ({@code {"path": "/user/name"}}) or a function call. Turn it into a model with
 * {@link A2uiRenderContext#model(A2uiValue)}; for an input component that model writes the user's
 * edits back into the data model.
 *
 * @param <T>
 *            the type of the value: {@link String}, {@link Boolean}, {@link Double},
 *            {@link Integer}, {@link Long}, an enum, a record, or a {@link java.util.List} of these
 * @since 12.0.0
 */
public final class A2uiValue<T> implements IClusterable
{
	private static final long serialVersionUID = 1L;

	private final Object raw;

	private final ValueType type;

	A2uiValue(Object raw, ValueType type)
	{
		this.raw = raw;
		this.type = type;
	}

	/**
	 * @return the JSON Pointer the value is bound to, or {@code null} when it is not a data binding
	 */
	public String getPath()
	{
		return ValueType.bindingPath(raw);
	}

	Object raw()
	{
		return raw;
	}

	ValueType type()
	{
		return type;
	}
}
