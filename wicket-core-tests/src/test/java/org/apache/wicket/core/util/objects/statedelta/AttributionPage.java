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
package org.apache.wicket.core.util.objects.statedelta;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 * One component per way of reaching state.
 */
public class AttributionPage extends WebPage implements IMarkupResourceStreamProvider
{
	private static final long serialVersionUID = 1L;

	final Person person = new Person("Ann");

	final List<String> items = new ArrayList<>();

	final IModel<Person> shared = Model.of(new Person("Bob"));

	final IModel<Person> boxed = Model.of(new Person("Cid"));

	final Label unrelated;

	/** What the next click on the link does. */
	transient Runnable action = () -> {
	};

	public AttributionPage()
	{
		add(new Label("a", new PropertyModel<>(shared, "name")));
		add(new Label("b", new PropertyModel<>(shared, "name")));
		add(new Label("capturing", () -> items.size()));

		WebMarkupContainer box = new WebMarkupContainer("box", boxed);
		add(box);
		box.add(new Label("bound", LambdaModel.of(boxed, Person::getName, Person::setName)));

		add(unrelated = new Label("unrelated", "static"));
		add(new Label("anonymous", new IModel<String>()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public String getObject()
			{
				return person.getName();
			}
		}));
		add(new Label("pageProperty", new PropertyModel<>(this, "person.name")));

		add(new AjaxLink<Void>("link")
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target)
			{
				if (action != null)
				{
					action.run();
				}
			}
		});
		add(new AjaxLink<Void>("lambdaLink")
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target)
			{
			}
		}.setOutputMarkupId(true));
	}

	@Override
	public IResourceStream getMarkupResourceStream(MarkupContainer container,
		Class<?> containerClass)
	{
		return new StringResourceStream("<html><body>" //
			+ "<span wicket:id='a'></span><span wicket:id='b'></span>" //
			+ "<span wicket:id='capturing'></span>" //
			+ "<div wicket:id='box'><span wicket:id='bound'></span></div>" //
			+ "<span wicket:id='unrelated'></span><span wicket:id='anonymous'></span>" //
			+ "<span wicket:id='pageProperty'></span>" //
			+ "<a wicket:id='link'>link</a><a wicket:id='lambdaLink'>link</a>" //
			+ "</body></html>");
	}
}
