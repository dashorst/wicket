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
package org.apache.wicket.examples.statedelta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tolerant scan of an HTML document into elements, enough to find a component's element by
 * markup id or component path and to compare its own markup.
 */
final class MarkupModel
{
	static final String PATH_ATTRIBUTE = "data-wicket-path";

	private static final Pattern TAG = Pattern.compile(
		"<(/?)([a-zA-Z][a-zA-Z0-9:_-]*)((?:\\s+[^\\s=/>]+(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?)*)\\s*(/?)>");

	private static final Pattern ATTRIBUTE = Pattern
		.compile("([^\\s=/>]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");

	private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img",
		"input", "link", "meta", "source", "track", "wbr", "param");

	private static final Set<String> RAW_TEXT = Set.of("script", "style", "textarea");

	/** Listener URLs carry the page id and render count, both of which move on every render. */
	private static final Pattern LISTENER_URL = Pattern.compile("([?&])\\d+(-\\d+)?\\.");

	/** Generated markup ids end in a hexadecimal session sequence number. */
	private static final Pattern GENERATED_ID = Pattern.compile("\\b(id|for)=\"([^\"]*?)[0-9a-f]+\"");

	static final class Element
	{
		final String name;

		final int start;

		int end;

		final String id;

		final String path;

		final List<Element> children = new ArrayList<>();

		Element(String name, int start, String id, String path)
		{
			this.name = name;
			this.start = start;
			this.id = id;
			this.path = path;
		}
	}

	final String html;

	final Element root;

	final Map<String, Element> byPath = new HashMap<>();

	final Map<String, Element> byId = new HashMap<>();

	Element body;

	MarkupModel(String html)
	{
		this.html = html;
		root = new Element("#root", 0, null, null);
		root.end = html.length();
		parse();
	}

	private void parse()
	{
		Deque<Element> open = new ArrayDeque<>();
		open.push(root);
		Matcher tag = TAG.matcher(html);
		int i = 0;
		while ((i = html.indexOf('<', i)) >= 0)
		{
			if (html.startsWith("<!--", i))
			{
				i = skip(i, "-->");
				continue;
			}
			if (html.startsWith("<![CDATA[", i))
			{
				i = skip(i, "]]>");
				continue;
			}
			if (html.startsWith("<!", i) || html.startsWith("<?", i))
			{
				i = skip(i, ">");
				continue;
			}
			tag.region(i, html.length());
			if (!tag.lookingAt())
			{
				i++;
				continue;
			}
			String name = tag.group(2).toLowerCase(Locale.ROOT);
			if (!tag.group(1).isEmpty())
			{
				for (Element e : open)
				{
					if (e.name.equals(name))
					{
						while (open.peek() != e)
						{
							open.pop().end = i;
						}
						open.pop().end = tag.end();
						break;
					}
				}
				i = tag.end();
				continue;
			}
			Map<String, String> attributes = attributes(tag.group(3));
			Element element = new Element(name, i, attributes.get("id"),
				attributes.get(PATH_ATTRIBUTE));
			open.peek().children.add(element);
			if (element.id != null)
			{
				byId.putIfAbsent(element.id, element);
			}
			if (element.path != null)
			{
				byPath.putIfAbsent(element.path, element);
			}
			if (name.equals("body") && body == null)
			{
				body = element;
			}
			i = tag.end();
			if (!tag.group(4).isEmpty() || VOID.contains(name))
			{
				element.end = i;
			}
			else if (RAW_TEXT.contains(name))
			{
				int close = html.toLowerCase(Locale.ROOT).indexOf("</" + name, i);
				int gt = close < 0 ? -1 : html.indexOf('>', close);
				element.end = gt < 0 ? html.length() : gt + 1;
				i = element.end;
			}
			else
			{
				open.push(element);
			}
		}
		while (open.peek() != root)
		{
			open.pop().end = html.length();
		}
	}

	private int skip(int from, String until)
	{
		int end = html.indexOf(until, from);
		return end < 0 ? html.length() : end + until.length();
	}

	private static Map<String, String> attributes(String source)
	{
		Map<String, String> attributes = new HashMap<>();
		Matcher m = ATTRIBUTE.matcher(source);
		while (m.find())
		{
			String value = m.group(2) != null ? m.group(2)
				: m.group(3) != null ? m.group(3) : m.group(4);
			attributes.put(m.group(1).toLowerCase(Locale.ROOT), value == null ? "" : value);
		}
		return attributes;
	}

	/**
	 * @return the document with the element of the given markup id replaced, or {@code null} if
	 *         no element has that id
	 */
	String replace(String id, String markup)
	{
		Element element = byId.get(id);
		if (element == null)
		{
			return null;
		}
		return html.substring(0, element.start) + markup + html.substring(element.end);
	}

	/**
	 * The markup of an element, with every descendant component element that the other document
	 * also has replaced by a placeholder, so only the element's own markup is compared. Component
	 * elements the other document lacks, like rebuilt repeater items, stay inline.
	 */
	String own(Element element, MarkupModel other)
	{
		StringBuilder b = new StringBuilder();
		appendOwn(element, other, b);
		String own = LISTENER_URL.matcher(b).replaceAll("$1P.");
		return GENERATED_ID.matcher(own).replaceAll("$1=\"$2#\"");
	}

	private void appendOwn(Element element, MarkupModel other, StringBuilder b)
	{
		int position = element.start;
		for (Element child : element.children)
		{
			b.append(html, position, child.start);
			if (child.path != null && other.byPath.containsKey(child.path))
			{
				b.append("<#>");
			}
			else
			{
				appendOwn(child, other, b);
			}
			position = child.end;
		}
		b.append(html, position, element.end);
	}
}
