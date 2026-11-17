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
import java.util.regex.Pattern;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebComponent;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.CheckBoxMultipleChoice;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.RadioChoice;
import org.apache.wicket.markup.html.form.RangeTextField;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.io.IClusterable;

/**
 * The basic catalog of A2UI v0.9: text, media, layout and input components, rendered with plain
 * Wicket components and styled by {@code a2ui.css}.
 * <p>
 * Not everything in the catalog is supported yet, and the schema from
 * {@link A2uiCatalog#toJsonSchema()} leaves out what is not, so an agent given that schema does
 * not use it: {@code Modal}, input {@code checks}, {@code validationRegexp}, the
 * {@code displayStyle} and {@code filterable} of {@code ChoicePicker}, icons given as an SVG path,
 * and functions other than {@code formatString}. {@code Text} is shown as plain text; its Markdown
 * is not interpreted.
 *
 * @since 12.0.0
 */
public final class BasicCatalog
{
	/** The id of the basic catalog. */
	public static final String ID = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json";

	private static final Pattern ICON_NAME = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*");

	private static final A2uiCatalog CATALOG = A2uiCatalog.builder(ID)
		.component("Text", "A piece of text", TextProps.class, BasicCatalog::text)
		.component("Image", "An image from a URL", ImageProps.class, BasicCatalog::image)
		.component("Icon", "A Material Symbols icon", IconProps.class, BasicCatalog::icon)
		.component("Video", "A video player", MediaProps.class,
			(id, props, context) -> media(id, "video", props, context))
		.component("AudioPlayer", "An audio player", MediaProps.class,
			(id, props, context) -> media(id, "audio", props, context))
		.component("Row", "Lays its children out horizontally", FlexProps.class,
			(id, props, context) -> flex(id, "row", props.children(), props.justify(),
				props.align(), context))
		.component("Column", "Lays its children out vertically", FlexProps.class,
			(id, props, context) -> flex(id, "column", props.children(), props.justify(),
				props.align(), context))
		.component("List", "A list of children, typically a template over an array",
			ListProps.class, BasicCatalog::list)
		.component("Card", "Groups one child visually", CardProps.class, BasicCatalog::card)
		.component("Tabs", "Shows one of several children, selected by a tab", TabsProps.class,
			BasicCatalog::tabs)
		.component("Divider", "A separating line", DividerProps.class, BasicCatalog::divider)
		.component("Button", "Sends an event to the agent when clicked", ButtonProps.class,
			BasicCatalog::button)
		.component("TextField", "A text input", TextFieldProps.class, BasicCatalog::textField)
		.component("CheckBox", "A checkbox", CheckBoxProps.class, BasicCatalog::checkBox)
		.component("ChoicePicker", "Choose one or more options", ChoicePickerProps.class,
			BasicCatalog::choicePicker)
		.component("Slider", "Choose a number in a range", SliderProps.class, BasicCatalog::slider)
		.component("DateTimeInput", "Choose a date, a time, or both", DateTimeInputProps.class,
			BasicCatalog::dateTimeInput)
		.build();

	private BasicCatalog()
	{
	}

	/**
	 * @return the basic catalog
	 */
	public static A2uiCatalog get()
	{
		return CATALOG;
	}

	enum TextVariant
	{
		H1, H2, H3, H4, H5, CAPTION, BODY
	}

	record TextProps(
		@A2uiRequired @A2uiDescription("The text, shown as plain text") A2uiValue<String> text,
		@A2uiDescription("h1 to h5 for headings, caption for small print; body by default") TextVariant variant)
		implements
			IClusterable
	{
	}

	enum ImageFit
	{
		CONTAIN, COVER, FILL, NONE, SCALE_DOWN
	}

	enum ImageVariant
	{
		ICON, AVATAR, SMALL_FEATURE, MEDIUM_FEATURE, LARGE_FEATURE, HEADER
	}

	record ImageProps(
		@A2uiRequired @A2uiDescription("An absolute http or https URL") A2uiValue<String> url,
		@A2uiDescription("What the image shows, for users who cannot see it") A2uiValue<String> description,
		ImageFit fit, @A2uiDescription("The size of the image") ImageVariant variant)
		implements
			IClusterable
	{
	}

	record IconProps(
		@A2uiRequired @A2uiDescription("A Material Symbols icon name in camelCase, e.g. accountCircle") A2uiValue<String> name)
		implements
			IClusterable
	{
	}

	record MediaProps(
		@A2uiRequired @A2uiDescription("An absolute http or https URL") A2uiValue<String> url,
		@A2uiDescription("What the media is") A2uiValue<String> description)
		implements
			IClusterable
	{
	}

	enum Justify
	{
		START, CENTER, END, SPACE_AROUND, SPACE_BETWEEN, SPACE_EVENLY, STRETCH
	}

	enum Align
	{
		START, CENTER, END, STRETCH
	}

	enum Direction
	{
		VERTICAL, HORIZONTAL
	}

	record FlexProps(@A2uiRequired A2uiChildren children,
		@A2uiDescription("Distribution along the main axis") Justify justify,
		@A2uiDescription("Alignment across the main axis") Align align) implements IClusterable
	{
	}

	record ListProps(@A2uiRequired A2uiChildren children, Direction direction, Align align)
		implements
			IClusterable
	{
	}

	record CardProps(@A2uiRequired A2uiComponentId child) implements IClusterable
	{
	}

	record Tab(@A2uiRequired A2uiValue<String> title, @A2uiRequired A2uiComponentId child)
		implements
			IClusterable
	{
	}

	record TabsProps(@A2uiRequired List<Tab> tabs) implements IClusterable
	{
	}

	enum Axis
	{
		HORIZONTAL, VERTICAL
	}

	record DividerProps(Axis axis) implements IClusterable
	{
	}

	enum ButtonVariant
	{
		DEFAULT, PRIMARY, BORDERLESS
	}

	record ButtonProps(
		@A2uiRequired @A2uiDescription("The content of the button, usually a Text") A2uiComponentId child,
		@A2uiRequired A2uiAction action, ButtonVariant variant) implements IClusterable
	{
	}

	enum TextFieldVariant
	{
		SHORT_TEXT, LONG_TEXT, NUMBER, OBSCURED
	}

	record TextFieldProps(@A2uiRequired A2uiValue<String> label,
		@A2uiDescription("Bind to a path to receive what the user types") A2uiValue<String> value,
		TextFieldVariant variant) implements IClusterable
	{
	}

	record CheckBoxProps(@A2uiRequired A2uiValue<String> label,
		@A2uiRequired A2uiValue<Boolean> value) implements IClusterable
	{
	}

	enum ChoiceVariant
	{
		MULTIPLE_SELECTION, MUTUALLY_EXCLUSIVE
	}

	record ChoiceOption(@A2uiRequired A2uiValue<String> label, @A2uiRequired String value)
		implements
			IClusterable
	{
	}

	record ChoicePickerProps(A2uiValue<String> label, @A2uiRequired List<ChoiceOption> options,
		@A2uiRequired @A2uiDescription("The values of the chosen options") A2uiValue<List<String>> value,
		ChoiceVariant variant) implements IClusterable
	{
	}

	record SliderProps(A2uiValue<String> label, Double min, @A2uiRequired double max,
		@A2uiRequired A2uiValue<Double> value) implements IClusterable
	{
	}

	record DateTimeInputProps(A2uiValue<String> label,
		@A2uiRequired @A2uiDescription("ISO 8601 date, time or date-time") A2uiValue<String> value,
		Boolean enableDate, Boolean enableTime, A2uiValue<String> min, A2uiValue<String> max)
		implements
			IClusterable
	{
	}

	private static Component text(String id, TextProps props, A2uiRenderContext context)
	{
		TextVariant variant = props.variant() != null ? props.variant() : TextVariant.BODY;
		String tagName = variant.compareTo(TextVariant.H5) <= 0 ? ValueType.wireName(variant) : "p";
		return new BasicPanel(id, "text").with(new Label("text", context.model(props.text()))
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onComponentTag(ComponentTag tag)
			{
				super.onComponentTag(tag);
				tag.setName(tagName);
				tag.append("class", "a2ui-text-" + ValueType.wireName(variant), " ");
			}
		});
	}

	private static Component image(String id, ImageProps props, A2uiRenderContext context)
	{
		IModel<String> url = context.urlModel(props.url());
		IModel<String> description = context.model(props.description());
		String classes = "a2ui-image-" +
			ValueType.wireName(props.variant() != null ? props.variant()
				: ImageVariant.MEDIUM_FEATURE) +
			" a2ui-fit-" + ValueType.wireName(props.fit() != null ? props.fit() : ImageFit.FILL);
		return new BasicPanel(id, "image").with(new WebComponent("image")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure()
			{
				super.onConfigure();
				setVisible(url.getObject() != null);
			}

			@Override
			protected void onComponentTag(ComponentTag tag)
			{
				super.onComponentTag(tag);
				tag.put("src", url.getObject());
				String alt = description.getObject();
				tag.put("alt", alt != null ? alt : "");
				tag.append("class", classes, " ");
			}
		});
	}

	private static Component icon(String id, IconProps props, A2uiRenderContext context)
	{
		IModel<String> name = context.model(props.name());
		IModel<String> ligature = () -> {
			String value = name.getObject();
			if (value == null || ICON_NAME.matcher(value).matches() == false)
			{
				return "";
			}
			return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
		};
		return new BasicPanel(id, "icon").with(new Label("icon", ligature));
	}

	private static Component media(String id, String kind, MediaProps props,
		A2uiRenderContext context)
	{
		IModel<String> url = context.urlModel(props.url());
		IModel<String> description = context.model(props.description());
		return new BasicPanel(id, kind).with(new WebMarkupContainer("media")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onConfigure()
			{
				super.onConfigure();
				setVisible(url.getObject() != null);
			}

			@Override
			protected void onComponentTag(ComponentTag tag)
			{
				super.onComponentTag(tag);
				tag.put("src", url.getObject());
				String label = description.getObject();
				if (label != null)
				{
					tag.put("aria-label", label);
				}
			}
		});
	}

	private static Component flex(String id, String direction, A2uiChildren children,
		Justify justify, Align align, A2uiRenderContext context)
	{
		return container(id, "a2ui-flex a2ui-" + direction + " a2ui-justify-" +
			ValueType.wireName(justify != null ? justify : Justify.START) + " a2ui-align-" +
			ValueType.wireName(align != null ? align : Align.STRETCH), children, context);
	}

	private static Component list(String id, ListProps props, A2uiRenderContext context)
	{
		String direction = props.direction() == Direction.HORIZONTAL ? "row" : "column";
		return container(id, "a2ui-flex a2ui-list a2ui-" + direction + " a2ui-align-" +
			ValueType.wireName(props.align() != null ? props.align() : Align.STRETCH),
			props.children(), context);
	}

	private static Component container(String id, String classes, A2uiChildren children,
		A2uiRenderContext context)
	{
		WebMarkupContainer container = new WebMarkupContainer("container");
		container.add(AttributeModifier.replace("class", classes));
		RepeatingView view = new RepeatingView("children");
		context.addChildren(view, children);
		container.add(view);
		return new BasicPanel(id, "flex").with(container);
	}

	private static Component card(String id, CardProps props, A2uiRenderContext context)
	{
		return new BasicPanel(id, "card").with(context.newComponent("child", props.child()));
	}

	private static Component tabs(String id, TabsProps props, A2uiRenderContext context)
	{
		BasicPanel tabsPanel = new BasicPanel(id, "tabs");
		RepeatingView tabs = new RepeatingView("tabs");
		RepeatingView panels = new RepeatingView("panels");
		List<Tab> all = props.tabs();
		for (int i = 0; i < all.size(); i++)
		{
			Tab tab = all.get(i);
			if (tab == null)
			{
				continue;
			}
			int index = i;
			AjaxButton button = new AjaxButton("tab")
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected void onSubmit(AjaxRequestTarget target)
				{
					context.setViewState("selectedTab", index);
					target.add(tabsPanel);
				}

				@Override
				protected void onError(AjaxRequestTarget target)
				{
					context.getSurfacePanel().updateFeedback(target);
				}

				@Override
				protected void onComponentTag(ComponentTag tag)
				{
					super.onComponentTag(tag);
					tag.put("aria-selected", selectedTab(context) == index);
				}
			};
			button.add(new Label("title", context.model(tab.title())));
			tabs.add(new WebMarkupContainer(tabs.newChildId()).add(button));

			Component panel = context.newComponent(panels.newChildId(), tab.child());
			panel.add(new Behavior()
			{
				private static final long serialVersionUID = 1L;

				@Override
				public void onConfigure(Component component)
				{
					component.setVisibilityAllowed(selectedTab(context) == index);
				}
			});
			panels.add(panel);
		}
		return tabsPanel.with(tabs, panels);
	}

	private static int selectedTab(A2uiRenderContext context)
	{
		return context.getViewState("selectedTab") instanceof Integer index ? index : 0;
	}

	private static Component divider(String id, DividerProps props, A2uiRenderContext context)
	{
		boolean vertical = props.axis() == Axis.VERTICAL;
		WebComponent divider = new WebComponent("divider");
		if (vertical)
		{
			divider.add(AttributeModifier.append("class", "a2ui-divider-vertical"));
			divider.add(AttributeModifier.replace("aria-orientation", "vertical"));
		}
		return new BasicPanel(id, "divider").with(divider);
	}

	private static Component button(String id, ButtonProps props, A2uiRenderContext context)
	{
		String variant = ValueType.wireName(props.variant() != null ? props.variant()
			: ButtonVariant.DEFAULT);
		AjaxButton button = new AjaxButton("button")
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target)
			{
				context.fireAction(target, props.action());
			}

			@Override
			protected void onError(AjaxRequestTarget target)
			{
				context.getSurfacePanel().updateFeedback(target);
			}

			@Override
			protected void onComponentTag(ComponentTag tag)
			{
				super.onComponentTag(tag);
				tag.append("class", "a2ui-button-" + variant, " ");
			}
		};
		button.add(context.newComponent("child", props.child()));
		return new BasicPanel(id, "button").with(button);
	}

	private static Component textField(String id, TextFieldProps props, A2uiRenderContext context)
	{
		IModel<String> label = context.model(props.label());
		IModel<String> value = context.model(props.value());
		TextFieldVariant variant = props.variant() != null ? props.variant()
			: TextFieldVariant.SHORT_TEXT;
		if (variant == TextFieldVariant.LONG_TEXT)
		{
			TextArea<String> input = new TextArea<>("input", value);
			input.setLabel(label);
			return new BasicPanel(id, "textArea").with(new Label("label", label), input);
		}
		String type = switch (variant)
		{
			case NUMBER -> "number";
			case OBSCURED -> "password";
			default -> "text";
		};
		TextField<String> input = new TextField<>("input", value, String.class)
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onComponentTag(ComponentTag tag)
			{
				super.onComponentTag(tag);
				tag.put("type", type);
			}
		};
		input.setLabel(label);
		return new BasicPanel(id, "input").with(new Label("label", label), input);
	}

	private static Component checkBox(String id, CheckBoxProps props, A2uiRenderContext context)
	{
		IModel<String> label = context.model(props.label());
		CheckBox input = new CheckBox("input", context.model(props.value()));
		input.setLabel(label);
		return new BasicPanel(id, "checkBox").with(input, new Label("label", label));
	}

	private static Component choicePicker(String id, ChoicePickerProps props,
		A2uiRenderContext context)
	{
		List<String> values = new ArrayList<>();
		List<IModel<String>> labels = new ArrayList<>();
		for (ChoiceOption option : props.options())
		{
			if (option != null && values.contains(option.value()) == false)
			{
				values.add(option.value());
				labels.add(context.model(option.label()));
			}
		}
		ChoiceRenderer<String> renderer = new ChoiceRenderer<>()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public Object getDisplayValue(String value)
			{
				int index = values.indexOf(value);
				String label = index < 0 ? null : labels.get(index).getObject();
				return label != null ? label : value;
			}

			@Override
			public String getIdValue(String value, int index)
			{
				return String.valueOf(values.indexOf(value));
			}
		};
		IModel<List<String>> selected = context.model(props.value());
		Component input;
		if (props.variant() == ChoiceVariant.MULTIPLE_SELECTION)
		{
			input = new CheckBoxMultipleChoice<>("input", selected, values, renderer);
		}
		else
		{
			IModel<String> single = new IModel<>()
			{
				private static final long serialVersionUID = 1L;

				@Override
				public String getObject()
				{
					List<String> current = selected.getObject();
					return current == null || current.isEmpty() ? null : current.get(0);
				}

				@Override
				public void setObject(String object)
				{
					List<String> value = new ArrayList<>();
					if (object != null)
					{
						value.add(object);
					}
					selected.setObject(value);
				}
			};
			input = new RadioChoice<>("input", single, values, renderer);
		}
		IModel<String> label = context.model(props.label());
		return new BasicPanel(id, "choices").with(
			new Label("label", label).setVisible(props.label() != null), input);
	}

	private static Component slider(String id, SliderProps props, A2uiRenderContext context)
	{
		IModel<String> label = context.model(props.label());
		RangeTextField<Double> input = new RangeTextField<>("input", context.model(props.value()),
			Double.class);
		input.setMinimum(props.min() != null ? props.min() : 0d);
		input.setMaximum(props.max());
		input.setStep(NumberTextField.ANY);
		input.setLabel(label);
		return new BasicPanel(id, "slider").with(new Label("label", label), input);
	}

	private static Component dateTimeInput(String id, DateTimeInputProps props,
		A2uiRenderContext context)
	{
		boolean date = Boolean.TRUE.equals(props.enableDate());
		boolean time = Boolean.TRUE.equals(props.enableTime());
		String type = date && time ? "datetime-local" : time ? "time" : "date";
		IModel<String> label = context.model(props.label());
		IModel<String> min = context.model(props.min());
		IModel<String> max = context.model(props.max());
		TextField<String> input = new TextField<>("input", context.model(props.value()),
			String.class)
		{
			private static final long serialVersionUID = 1L;

			@Override
			protected void onComponentTag(ComponentTag tag)
			{
				super.onComponentTag(tag);
				tag.put("type", type);
				if (min.getObject() != null)
				{
					tag.put("min", min.getObject());
				}
				if (max.getObject() != null)
				{
					tag.put("max", max.getObject());
				}
			}
		};
		input.setLabel(label);
		return new BasicPanel(id, "input").with(new Label("label", label), input);
	}
}
