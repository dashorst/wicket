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

import org.apache.wicket.Component;
import org.apache.wicket.util.io.IClusterable;

/**
 * Creates the Wicket component for one A2UI component type.
 *
 * @param <P>
 *            the record holding the properties of the component type
 * @since 12.0.0
 */
@FunctionalInterface
public interface A2uiComponentFactory<P extends Record> extends IClusterable
{
	/**
	 * @param wicketId
	 *            the Wicket id the component must have
	 * @param properties
	 *            the properties the agent sent, bound to the record; validated against its types,
	 *            but otherwise unchecked agent output
	 * @param context
	 *            turns bound values into models, child references into components, and actions
	 *            into events for the agent
	 * @return the component
	 */
	Component create(String wicketId, P properties, A2uiRenderContext context);
}
