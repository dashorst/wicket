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
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;

/**
 * The panel every component of the {@link BasicCatalog} renders in: one of the fragments in its
 * markup, filled by the catalog.
 */
final class BasicPanel extends Panel
{
	private static final long serialVersionUID = 1L;

	private final Fragment content;

	BasicPanel(String id, String fragmentId)
	{
		super(id);
		content = new Fragment("content", fragmentId, this);
		add(content);
	}

	BasicPanel with(Component... components)
	{
		content.add(components);
		return this;
	}
}
