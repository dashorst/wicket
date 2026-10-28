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
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RefreshingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.apache.wicket.validation.validator.StringValidator;

/**
 * Components Wicket changes on its own during a request.
 */
public class NoisePage extends WebPage implements IMarkupResourceStreamProvider
{
	private static final long serialVersionUID = 1L;

	final Person person = new Person("Ann");

	final List<String> names = new ArrayList<>(List.of("x", "y"));

	public NoisePage()
	{
		Form<Void> form = new Form<>("form");
		add(form);
		TextField<String> age = new TextField<>("age", Model.of("old"));
		age.add(StringValidator.minimumLength(3));
		age.add(new AjaxFormComponentUpdatingBehavior("change")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget target)
			{
			}
		});
		form.add(age);
		TextField<String> name = new TextField<>("name", new PropertyModel<>(person, "name"));
		name.add(new AjaxFormComponentUpdatingBehavior("change")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget target)
			{
			}
		});
		form.add(name);
		add(new FeedbackPanel("feedback").setOutputMarkupId(true));

		add(new ListView<>("list", names)
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<String> item)
			{
				item.add(new Label("label", item.getModel()).setOutputMarkupId(true));
			}
		});
		add(new RefreshingView<String>("refreshing")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected java.util.Iterator<IModel<String>> getItemModels()
			{
				return names.stream().map(n -> (IModel<String>)Model.of(n)).iterator();
			}

			@Override
			protected void populateItem(Item<String> item)
			{
				item.add(new Label("label", item.getModel()));
			}
		});
		add(new Label("enclosed", "e"));

		add(new AjaxLink<Void>("noop")
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target)
			{
			}
		});
	}

	@Override
	public IResourceStream getMarkupResourceStream(MarkupContainer container,
		Class<?> containerClass)
	{
		return new StringResourceStream("<html><body>" //
			+ "<form wicket:id='form'><input wicket:id='age'/><input wicket:id='name'/></form>" //
			+ "<div wicket:id='feedback'></div>" //
			+ "<ul><li wicket:id='list'><span wicket:id='label'></span></li></ul>" //
			+ "<ul><li wicket:id='refreshing'><span wicket:id='label'></span></li></ul>" //
			+ "<wicket:enclosure child='enclosed'><span wicket:id='enclosed'></span></wicket:enclosure>" //
			+ "<wicket:message key='missing'>default</wicket:message>" //
			+ "<a wicket:id='noop'>noop</a>" //
			+ "</body></html>");
	}
}
