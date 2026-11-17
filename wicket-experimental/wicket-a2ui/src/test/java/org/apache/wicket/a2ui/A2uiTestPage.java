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
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebPage;

/**
 * Renders the surface {@code main} and plays the agent: it records every action and answers it
 * with {@link #reply}.
 */
public class A2uiTestPage extends WebPage
{
	private static final long serialVersionUID = 1L;

	final A2uiSurfaces surfaces = new A2uiSurfaces();

	final List<A2uiActionMessage> actions = new ArrayList<>();

	String reply;

	/**
	 * @param messages
	 *            the agent's messages before the first render
	 * @param catalogs
	 *            catalogs besides the basic one
	 */
	public A2uiTestPage(String messages, A2uiCatalog... catalogs)
	{
		surfaces.process(messages);
		A2uiSurfacePanel panel = new A2uiSurfacePanel("surface", () -> surfaces, "main")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onAction(AjaxRequestTarget target, A2uiActionMessage action)
			{
				actions.add(action);
				if (reply != null)
				{
					surfaces.process(reply);
				}
			}
		};
		for (A2uiCatalog catalog : catalogs)
		{
			panel.addCatalog(catalog);
		}
		add(panel);
	}

	/**
	 * Builds the messages for a surface; single quotes stand for double quotes.
	 *
	 * @param catalogId
	 *            the catalog of the surface
	 * @param dataModel
	 *            the initial data model
	 * @param components
	 *            the components, comma separated
	 * @return the messages
	 */
	static String surface(String catalogId, String dataModel, String components)
	{
		return message("{'createSurface':{'surfaceId':'main','catalogId':'" + catalogId + "'}}") +
			"\n" + message("{'updateDataModel':{'surfaceId':'main','value':" + dataModel + "}}") +
			"\n" + components(components);
	}

	static String components(String components)
	{
		return message("{'updateComponents':{'surfaceId':'main','components':[" + components +
			"]}}");
	}

	static String message(String body)
	{
		return ("{'version':'v0.9.1'," + body.substring(1)).replace('\'', '"');
	}
}
