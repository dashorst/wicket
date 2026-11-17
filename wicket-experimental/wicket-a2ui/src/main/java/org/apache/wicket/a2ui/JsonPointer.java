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
import java.util.Collections;
import java.util.List;

/**
 * Parses RFC 6901 JSON Pointers. Both the empty pointer and {@code "/"} denote the root, as A2UI
 * uses them.
 */
final class JsonPointer
{
	private JsonPointer()
	{
	}

	static List<String> parse(String pointer)
	{
		if (pointer == null || pointer.isEmpty() || pointer.equals("/"))
		{
			return Collections.emptyList();
		}
		if (pointer.charAt(0) != '/')
		{
			throw new A2uiProtocolException("A JSON Pointer must start with '/': " + pointer);
		}
		List<String> tokens = new ArrayList<>();
		for (String token : pointer.substring(1).split("/", -1))
		{
			tokens.add(token.replace("~1", "/").replace("~0", "~"));
		}
		return tokens;
	}

	/**
	 * Resolves {@code path} against {@code scope}: an absolute path stands on its own, a relative
	 * one is appended to the scope, and an empty one or {@code "."} is the scope itself.
	 */
	static String resolve(String scope, String path)
	{
		if (path.startsWith("/"))
		{
			return path;
		}
		if (path.isEmpty() || path.equals("."))
		{
			return scope.isEmpty() ? "/" : scope;
		}
		return scope + "/" + path;
	}

	/**
	 * @return the array index the token denotes, or -1 when it is not an array index
	 */
	static int index(String token)
	{
		if (token.isEmpty() || token.length() > 9 || (token.length() > 1 && token.charAt(0) == '0'))
		{
			return -1;
		}
		for (int i = 0; i < token.length(); i++)
		{
			char c = token.charAt(i);
			if (c < '0' || c > '9')
			{
				return -1;
			}
		}
		return Integer.parseInt(token);
	}
}
