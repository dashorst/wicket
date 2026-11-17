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
import org.apache.wicket.util.lang.Args;

/**
 * A property that references another component on the surface by its id. Turn it into a Wicket
 * component with {@link A2uiRenderContext#newComponent(String, A2uiComponentId)}.
 *
 * @param id
 *            the referenced component id
 * @since 12.0.0
 */
public record A2uiComponentId(String id) implements IClusterable
{
	/**
	 * @param id
	 *            the referenced component id
	 */
	public A2uiComponentId
	{
		Args.notEmpty(id, "id");
	}
}
