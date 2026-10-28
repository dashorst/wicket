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

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The hashed state of every component of a page at one moment, keyed by page relative path.
 * <p>
 * The state of a component is its own serialized fields: its parent and children count as their
 * ids only, and any other component it reaches is recorded as a dependency instead of being
 * hashed along.
 */
public final class StateSnapshot
{
	/**
	 * The hashed state of a single component.
	 */
	public static final class ComponentState
	{
		private final String path;

		private final Class<?> type;

		private final long hash;

		private final long size;

		private final Map<String, Long> fields;

		private final Map<String, DependencyKind> dependencies;

		ComponentState(String path, Class<?> type, long hash, long size, Map<String, Long> fields,
			Map<String, DependencyKind> dependencies)
		{
			this.path = path;
			this.type = type;
			this.hash = hash;
			this.size = size;
			this.fields = fields;
			this.dependencies = dependencies;
		}

		/**
		 * @return the page relative path, the empty string for the page itself
		 */
		public String getPath()
		{
			return path;
		}

		/**
		 * @return the class of the component
		 */
		public Class<?> getType()
		{
			return type;
		}

		/**
		 * @return the hash over all fields
		 */
		public long getHash()
		{
			return hash;
		}

		/**
		 * @return the number of bytes hashed for this component
		 */
		public long getSize()
		{
			return size;
		}

		/**
		 * @return hash per field, keyed by {@code declaringClass#field}
		 */
		public Map<String, Long> getFields()
		{
			return Collections.unmodifiableMap(fields);
		}

		/**
		 * @return the paths of other components of the page this state reaches, with how it reaches
		 *         them
		 */
		public Map<String, DependencyKind> getDependencies()
		{
			return Collections.unmodifiableMap(dependencies);
		}

		@Override
		public String toString()
		{
			return path + "[" + type.getSimpleName() + "]";
		}
	}

	/**
	 * The difference between two snapshots of the same page.
	 */
	public static final class Delta
	{
		private final StateSnapshot before;

		private final StateSnapshot after;

		private final Map<String, Set<String>> changed = new LinkedHashMap<>();

		private final Set<String> added = new LinkedHashSet<>();

		private final Set<String> removed = new LinkedHashSet<>();

		Delta(StateSnapshot before, StateSnapshot after)
		{
			this.before = before;
			this.after = after;

			for (ComponentState state : after.components.values())
			{
				ComponentState old = before.components.get(state.path);
				if (old == null)
				{
					added.add(state.path);
				}
				else if (old.hash != state.hash)
				{
					Set<String> fields = new LinkedHashSet<>();
					for (Map.Entry<String, Long> field : state.fields.entrySet())
					{
						if (!Objects.equals(field.getValue(), old.fields.get(field.getKey())))
						{
							fields.add(field.getKey());
						}
					}
					changed.put(state.path, fields);
				}
			}
			for (String path : before.components.keySet())
			{
				if (!after.components.containsKey(path))
				{
					removed.add(path);
				}
			}
		}

		/**
		 * @return paths of the components whose own state changed, with the fields that changed
		 */
		public Map<String, Set<String>> getChanged()
		{
			return Collections.unmodifiableMap(changed);
		}

		/**
		 * @return paths of components only present after
		 */
		public Set<String> getAdded()
		{
			return Collections.unmodifiableSet(added);
		}

		/**
		 * @return paths of components only present before
		 */
		public Set<String> getRemoved()
		{
			return Collections.unmodifiableSet(removed);
		}

		/**
		 * @return whether nothing changed, was added or was removed
		 */
		public boolean isEmpty()
		{
			return changed.isEmpty() && added.isEmpty() && removed.isEmpty();
		}

		/**
		 * The components whose own state changed, together with the components that reach one
		 * of them through a dependency of the given kinds.
		 * 
		 * @param kinds
		 *            the kinds of dependency to follow, one level deep
		 * @return paths of affected components
		 */
		public Set<String> getAffected(Set<DependencyKind> kinds)
		{
			Set<String> affected = new LinkedHashSet<>(changed.keySet());
			affected.addAll(added);
			if (kinds.isEmpty())
			{
				return affected;
			}
			for (ComponentState state : after.components.values())
			{
				for (Map.Entry<String, DependencyKind> dependency : state.dependencies.entrySet())
				{
					if (kinds.contains(dependency.getValue()) &&
						changed.containsKey(dependency.getKey()))
					{
						affected.add(state.path);
						break;
					}
				}
			}
			return affected;
		}

		/**
		 * @return the components whose own state changed or that reach such a component in any way
		 */
		public Set<String> getAffected()
		{
			return getAffected(EnumSet.allOf(DependencyKind.class));
		}

		/**
		 * @return the snapshot compared against
		 */
		public StateSnapshot getBefore()
		{
			return before;
		}

		/**
		 * @return the snapshot compared
		 */
		public StateSnapshot getAfter()
		{
			return after;
		}
	}

	private final Map<String, ComponentState> components;

	private final long nanos;

	StateSnapshot(Map<String, ComponentState> components, long nanos)
	{
		this.components = components;
		this.nanos = nanos;
	}

	/**
	 * @return the state of each component, in hierarchy order
	 */
	public Map<String, ComponentState> getComponents()
	{
		return Collections.unmodifiableMap(components);
	}

	/**
	 * @return the total number of bytes hashed
	 */
	public long getSize()
	{
		long size = 0;
		for (ComponentState state : components.values())
		{
			size += state.size;
		}
		return size;
	}

	/**
	 * @return how long taking the snapshot took
	 */
	public long getNanos()
	{
		return nanos;
	}

	/**
	 * @param before
	 *            an earlier snapshot of the same page
	 * @return what changed from {@code before} to this snapshot
	 */
	public Delta compareTo(StateSnapshot before)
	{
		return new Delta(before, this);
	}
}
