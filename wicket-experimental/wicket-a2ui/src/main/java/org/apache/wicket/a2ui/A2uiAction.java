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

/**
 * A property describing what happens when the user acts on a component: the agent gets an event
 * with this name and a context resolved against the data model at that moment. Fire it with
 * {@link A2uiRenderContext#fireAction(org.apache.wicket.ajax.AjaxRequestTarget, A2uiAction)}.
 *
 * @since 12.0.0
 */
public final class A2uiAction implements IClusterable
{
	private static final long serialVersionUID = 1L;

	private final String eventName;

	private final Map<String, Object> context;

	A2uiAction(String eventName, Map<String, Object> context)
	{
		this.eventName = eventName;
		this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
	}

	/**
	 * @return the name of the event the agent receives
	 */
	public String getEventName()
	{
		return eventName;
	}

	Map<String, Object> context()
	{
		return context;
	}
}
