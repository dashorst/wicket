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
package org.apache.wicket.examples.ajax.builtin.modal;

import java.util.Date;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.yui.calendar.DateTimeField;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

/**
 * @author Matej Knopp
 */
public class ModalPanel1 extends Panel
{

	/**
	 * @param id
	 */
	public ModalPanel1(String id)
	{
		super(id);

		Form<?> form = new Form<>("form");
		add(form);

		add(new AjaxSubmitLink("closeOK", form)
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit(AjaxRequestTarget target, Form<?> form)
			{
				ModalWindow.closeCurrent(target);
			}
		});

		add(new AjaxLink<Void>("closeCancel")
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target)
			{
				ModalWindow.closeCurrent(target);
			}
		});

		String longText = "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10000; i++)
			sb.append("a");
		form.add(new TextArea<>("bigFingTextField", Model.of(sb.toString())));
		form.add(new DateTimeField("dateTimeField", Model.of(new Date())));
	}

}
