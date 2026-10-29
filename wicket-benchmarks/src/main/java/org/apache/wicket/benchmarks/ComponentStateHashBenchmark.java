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
package org.apache.wicket.benchmarks;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.core.util.objects.statedelta.ComponentStateHasher;
import org.apache.wicket.core.util.objects.statedelta.StateSnapshot;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.mock.MockApplication;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.serialize.java.JavaSerializer;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.apache.wicket.util.tester.WicketTester;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Compares hashing the state of every component with {@link ComponentStateHasher} against
 * serializing the page as {@code SerializingPageStore} does.
 * <p>
 * {@code simple} is the page of {@link PageSerializationBenchmark}: rows of a container and a
 * label. {@code form} rows hold a bean, a label and a text field reaching it, and an anonymous Ajax
 * link capturing the page, which is what an edit form looks like.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Threads(1)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class ComponentStateHashBenchmark
{
	@Param({"50", "500"})
	public int rows;

	@Param({"simple", "form"})
	public String shape;

	private WicketTester tester;

	private JavaSerializer serializer;

	private ComponentStateHasher hasher;

	private ComponentStateHasher fullDescriptors;

	private ComponentStateHasher resetPerComponent;

	private WebPage page;

	private StateSnapshot before;

	@Setup(Level.Trial)
	public void setUp()
	{
		tester = new WicketTester(new MockApplication());
		serializer = new JavaSerializer("benchmarks");
		hasher = ComponentStateHasher.withDefaultExclusions();
		fullDescriptors = ComponentStateHasher.withDefaultExclusions().setFullClassDescriptors(true);
		resetPerComponent = ComponentStateHasher.withDefaultExclusions().setResetPerField(false);
		page = shape.equals("simple") ? new SimplePage(rows) : new FormPage(rows);
		tester.startPage(page);
		before = hasher.snapshot(page);
		System.out.println("components=" + before.getComponents().size() + " serialized=" +
			serializer.serialize(page).length + " hashed=" + before.getSize() + " hashedFull=" +
			fullDescriptors.snapshot(page).getSize() + " hashedPerComponentReset=" +
			resetPerComponent.snapshot(page).getSize());
	}

	@TearDown(Level.Trial)
	public void tearDown()
	{
		tester.destroy();
	}

	@Benchmark
	public byte[] serializePage()
	{
		return serializer.serialize(page);
	}

	@Benchmark
	public StateSnapshot snapshot()
	{
		return hasher.snapshot(page);
	}

	@Benchmark
	public StateSnapshot snapshotFullDescriptors()
	{
		return fullDescriptors.snapshot(page);
	}

	@Benchmark
	public StateSnapshot snapshotResetPerComponent()
	{
		return resetPerComponent.snapshot(page);
	}

	/** What the check costs per Ajax request: one more snapshot and the comparison. */
	@Benchmark
	public Object snapshotAndCompare()
	{
		return hasher.snapshot(page).compareTo(before).getAffected();
	}

	private static class SimplePage extends WebPage implements IMarkupResourceStreamProvider
	{
		private static final long serialVersionUID = 1L;

		SimplePage(int rows)
		{
			RepeatingView view = new RepeatingView("rv");
			for (int i = 0; i < rows; i++)
			{
				WebMarkupContainer child = new WebMarkupContainer(view.newChildId());
				child.add(new Label("label", Model.of("value " + i)));
				view.add(child);
			}
			add(view);
		}

		@Override
		public IResourceStream getMarkupResourceStream(MarkupContainer container,
			Class< ? > containerClass)
		{
			return new StringResourceStream(
				"<html><body><div wicket:id=\"rv\"><span wicket:id=\"label\"></span></div></body></html>");
		}
	}

	public static class Bean implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private String name;

		Bean(String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}

		public void setName(String name)
		{
			this.name = name;
		}
	}

	private static class FormPage extends WebPage implements IMarkupResourceStreamProvider
	{
		private static final long serialVersionUID = 1L;

		private int clicks;

		FormPage(int rows)
		{
			Form<Void> form = new Form<>("form");
			add(form);
			RepeatingView view = new RepeatingView("rv");
			form.add(view);
			for (int i = 0; i < rows; i++)
			{
				IModel<Bean> bean = Model.of(new Bean("name " + i));
				WebMarkupContainer row = new WebMarkupContainer(view.newChildId(), bean);
				row.add(new Label("label", new PropertyModel<>(bean, "name")));
				row.add(new TextField<>("field", LambdaModel.of(bean, Bean::getName, Bean::setName)));
				row.add(new AjaxLink<Void>("link")
				{
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target)
					{
						clicks++;
					}
				});
				view.add(row);
			}
		}

		@Override
		public IResourceStream getMarkupResourceStream(MarkupContainer container,
			Class< ? > containerClass)
		{
			return new StringResourceStream("<html><body><form wicket:id=\"form\">" +
				"<div wicket:id=\"rv\"><span wicket:id=\"label\"></span><input wicket:id=\"field\"/>" +
				"<a wicket:id=\"link\">x</a></div></form></body></html>");
		}
	}
}
