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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.util.io.IClusterable;
import org.apache.wicket.core.util.lang.WicketObjects;
import org.apache.wicket.util.tester.FormTester;
import org.apache.wicket.util.tester.WicketTestCase;
import org.apache.wicket.util.visit.IVisitor;
import org.junit.jupiter.api.Test;

class A2uiSurfacePanelTest extends WicketTestCase
{
	private A2uiTestPage page;

	private void start(String dataModel, String components)
	{
		page = new A2uiTestPage(A2uiTestPage.surface(BasicCatalog.ID, dataModel, components));
		tester.startPage(page);
		tester.assertNoErrorMessage();
	}

	@Test
	void rendersHeadingWithEscapedText()
	{
		start("{}", "{'id':'root','component':'Text','text':'<script>alert(1)</script>','variant':'h2'}");

		tester.assertContains("<h2 [^>]*class=\"a2ui-text a2ui-text-h2\">&lt;script&gt;alert\\(1\\)&lt;/script&gt;</h2>");
	}

	@Test
	void staysInvisibleUntilTheRootArrives()
	{
		page = new A2uiTestPage(A2uiTestPage.message(
			"{'createSurface':{'surfaceId':'main','catalogId':'" + BasicCatalog.ID + "'}}"));
		tester.startPage(page);

		tester.assertInvisible("surface");
	}

	@Test
	void repeatsTemplateForEveryArrayElement()
	{
		start("{'people':[{'name':'Ada'},{'name':'Grace'}]}",
			"{'id':'root','component':'List','children':{'path':'/people','componentId':'person'}}," +
				"{'id':'person','component':'Text','text':{'path':'name'}}");

		tester.assertContains("Ada");
		tester.assertContains("Grace");
	}

	@Test
	void sendsActionWithInputsAndResolvedContext()
	{
		start("{'form':{'name':'','subscribe':false},'people':[{'id':7}]}",
			"{'id':'root','component':'Column','children':['name','subscribe','people']}," +
				"{'id':'name','component':'TextField','label':'Name','value':{'path':'/form/name'}}," +
				"{'id':'subscribe','component':'CheckBox','label':'Subscribe','value':{'path':'/form/subscribe'}}," +
				"{'id':'people','component':'List','children':{'path':'/people','componentId':'pick'}}," +
				"{'id':'pick','component':'Button','child':'pickLabel','action':{'event':{'name':'pick'," +
				"'context':{'person':{'path':'id'},'name':{'path':'/form/name'},'source':'list'}}}}," +
				"{'id':'pickLabel','component':'Text','text':'Pick'}");
		page.reply = A2uiTestPage.components("{'id':'root','component':'Text','text':'Thanks'}");

		FormTester form = tester.newFormTester("surface:form");
		form.setValue(relativeTo(form, find(TextField.class)), "Ada");
		form.setValue(relativeTo(form, find(CheckBox.class)), "true");
		tester.executeAjaxEvent(find(AjaxButton.class), "click");

		assertEquals(1, page.actions.size());
		A2uiActionMessage action = page.actions.get(0);
		assertEquals("pick", action.name());
		assertEquals("main", action.surfaceId());
		assertEquals("pick", action.sourceComponentId());
		assertEquals(Map.of("person", 7, "name", "Ada", "source", "list"), action.context());
		assertNull(action.dataModel());
		assertEquals(Map.of("name", "Ada", "subscribe", true),
			page.surfaces.get("main").getDataModel().get("/form"));

		tester.assertComponentOnAjaxResponse("surface:form:root");
		tester.assertContains("Thanks");
	}

	@Test
	void rerendersOnlyWhatTheAgentChanged()
	{
		start("{'message':'old message','people':['Ada']}",
			"{'id':'root','component':'Column','children':['kept','bound','changed','late','people','send']}," +
				"{'id':'kept','component':'Text','text':'kept text'}," +
				"{'id':'bound','component':'Text','text':{'path':'/message'}}," +
				"{'id':'changed','component':'Text','text':'old text'}," +
				"{'id':'people','component':'List','children':{'path':'/people','componentId':'person'}}," +
				"{'id':'person','component':'Text','text':{'path':''}}," +
				"{'id':'send','component':'Button','child':'sendLabel','action':{'event':{'name':'send'}}}," +
				"{'id':'sendLabel','component':'Text','text':'Send'}");
		Component kept = findText("kept text");
		Component ada = findText("Ada");
		page.reply = A2uiTestPage.components(
			"{'id':'changed','component':'Text','text':'new text'}," +
				"{'id':'late','component':'Text','text':'arrived late'}") + "\n" +
			A2uiTestPage.message("{'updateDataModel':{'surfaceId':'main','path':'/message','value':'new message'}}") +
			"\n" +
			A2uiTestPage.message("{'updateDataModel':{'surfaceId':'main','path':'/people/1','value':'Grace'}}");

		tester.executeAjaxEvent(find(AjaxButton.class), "click");

		String response = tester.getLastResponseAsString();
		assertTrue(response.contains("new text"), response);
		assertTrue(response.contains("arrived late"), response);
		assertTrue(response.contains("new message"), response);
		assertTrue(response.contains("Grace"), response);
		assertFalse(response.contains("kept text"), response);
		assertFalse(response.contains("Send"), response);
		assertSame(kept, findText("kept text"));
		assertNotSame(ada, findText("Ada"));
	}

	@Test
	void sendsEventsWithValuesOfServerSideComponents()
	{
		A2uiCatalog catalog = A2uiCatalog.builder("https://example.com/rows")
			.component("Row", "A server-side row; sends rowClicked with its rowId", RowProps.class,
				(id, props, context) -> new AjaxLink<Void>(id)
				{
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target)
					{
						context.fireEvent(target, "rowClicked", Map.of("rowId", 4711));
					}
				})
			.build();
		page = new A2uiTestPage(A2uiTestPage.surface("https://example.com/rows", "{}",
			"{'id':'root','component':'Row'}"), catalog);
		tester.startPage(page);

		tester.executeAjaxEvent(find(AjaxLink.class), "click");

		assertEquals(1, page.actions.size());
		assertEquals("rowClicked", page.actions.get(0).name());
		assertEquals("root", page.actions.get(0).sourceComponentId());
		assertEquals(Map.of("rowId", 4711), page.actions.get(0).context());
		assertThrows(IllegalArgumentException.class,
			() -> Json.copyJsonValue(Map.of("student", new Object())));
	}

	/** A component without properties. */
	public record RowProps() implements IClusterable
	{
	}

	@Test
	void switchesTabsKeepingTheSelectionAcrossUpdates()
	{
		start("{}",
			"{'id':'root','component':'Tabs','tabs':[{'title':'One','child':'one'},{'title':'Two','child':'two'}]}," +
				"{'id':'one','component':'Text','text':'first tab'}," +
				"{'id':'two','component':'Text','text':'second tab'}");
		tester.assertContains("first tab");
		tester.assertContainsNot("second tab");

		List<AjaxButton> tabs = findAll(AjaxButton.class);
		tester.executeAjaxEvent(tabs.get(1), "click");
		tester.assertContains("second tab");
		tester.assertContainsNot("first tab");

		page.surfaces.process(A2uiTestPage.components(
			"{'id':'two','component':'Text','text':'updated second tab'}"));
		tester.startPage(page);
		tester.assertContains("updated second tab");
	}

	@Test
	void dropsCyclesAndUnknownComponents()
	{
		start("{}",
			"{'id':'root','component':'Column','children':['loop','unknown','missing','text']}," +
				"{'id':'loop','component':'Card','child':'root'}," +
				"{'id':'unknown','component':'Hologram'}," +
				"{'id':'text','component':'Text','text':'rendered'}");

		tester.assertContains("rendered");
	}

	@Test
	void rendersOnlyAllowedUrls()
	{
		start("{}",
			"{'id':'root','component':'Column','children':['good','bad']}," +
				"{'id':'good','component':'Image','url':'https://example.com/a.png','description':'A \\\"quoted\\\" cat'}," +
				"{'id':'bad','component':'Image','url':'javascript:alert(1)'}");

		tester.assertContains("src=\"https://example.com/a.png\"");
		tester.assertContains("alt=\"A &quot;quoted&quot; cat\"");
		tester.assertContainsNot("javascript");
	}

	@Test
	void appliesThemeColorOnlyWhenItIsAColor()
	{
		page = new A2uiTestPage(A2uiTestPage.message(
			"{'createSurface':{'surfaceId':'main','catalogId':'" + BasicCatalog.ID +
				"','theme':{'primaryColor':'red;background:url(x)'}}}") + "\n" +
			A2uiTestPage.components("{'id':'root','component':'Text','text':'x'}"));
		tester.startPage(page);

		tester.assertContainsNot("--a2ui-primary");
	}

	@Test
	void rendersEveryBasicComponentInASerializablePage()
	{
		start("{'when':'2026-11-17','volume':3,'colors':['red'],'sizes':['s','m']}",
			"{'id':'root','component':'Column','children':['row','list','card','divider','media']}," +
				"{'id':'row','component':'Row','justify':'spaceBetween','align':'center','children':['icon','slider']}," +
				"{'id':'icon','component':'Icon','name':'accountCircle','weight':2}," +
				"{'id':'slider','component':'Slider','label':'Volume','min':0,'max':10,'value':{'path':'/volume'}}," +
				"{'id':'list','component':'List','direction':'horizontal','children':['color','size','date']}," +
				"{'id':'color','component':'ChoicePicker','label':'Color','options':[{'label':'Red','value':'red'},{'label':'Blue','value':'blue'}],'value':{'path':'/colors'}}," +
				"{'id':'size','component':'ChoicePicker','variant':'multipleSelection','options':[{'label':'S','value':'s'},{'label':'M','value':'m'}],'value':{'path':'/sizes'}}," +
				"{'id':'date','component':'DateTimeInput','label':'When','enableDate':true,'value':{'path':'/when'}}," +
				"{'id':'card','component':'Card','child':'notes'}," +
				"{'id':'notes','component':'TextField','label':'Notes','variant':'longText'}," +
				"{'id':'divider','component':'Divider','axis':'vertical'}," +
				"{'id':'media','component':'Video','url':'https://example.com/a.mp4'}");

		tester.assertContains("account_circle");
		tester.assertContains("style=\"flex: 2 1 0%\"");
		tester.assertContains("type=\"range\"[^>]*value=\"3.0\"");
		tester.assertContains("type=\"date\"");
		tester.assertContains("2026-11-17");
		tester.assertContains("type=\"radio\"[^>]*checked=\"checked\"");
		tester.assertContains("<textarea");
		tester.assertContains("aria-orientation=\"vertical\"");
		tester.assertContains("<video[^>]*src=\"https://example.com/a.mp4\"");

		WicketObjects.cloneObject(tester.getLastRenderedPage());
	}

	private Component findText(String text)
	{
		return findAll(Label.class).stream()
			.filter(label -> text.equals(label.getDefaultModelObjectAsString()))
			.findFirst()
			.orElseThrow();
	}

	private <T extends Component> T find(Class<T> type)
	{
		return findAll(type).get(0);
	}

	private <T extends Component> List<T> findAll(Class<T> type)
	{
		List<T> found = new java.util.ArrayList<>();
		((MarkupContainer)tester.getLastRenderedPage()).visitChildren(type,
			(IVisitor<T, Void>)(component, visit) -> found.add(component));
		return found;
	}

	private static String relativeTo(FormTester form, Component component)
	{
		return component.getPageRelativePath()
			.substring(form.getForm().getPageRelativePath().length() + 1);
	}
}
