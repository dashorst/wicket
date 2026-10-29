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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.MetaDataEntry;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Page;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;

/**
 * Hashes the serialized state of each component of a page separately, without keeping the
 * serialized bytes.
 * <p>
 * Every component is written through its own {@link ObjectOutputStream} segment, so an object
 * shared by two components counts towards the state of both. Other components of the page are
 * written as a reference to their path and recorded as a {@link DependencyKind dependency}.
 * <p>
 * This is a development aid: it runs Java serialization over the whole page, it reads private
 * fields reflectively and it is not thread safe.
 */
public class ComponentStateHasher
{
	private static final String COMPONENT = Component.class.getName();

	private static final String FIELD_PARENT = COMPONENT + "#parent";

	private static final String FIELD_FLAGS = COMPONENT + "#flags";

	private static final String FIELD_DATA = COMPONENT + "#data";

	private static final String FIELD_CHILDREN = MarkupContainer.class.getName() + "#children";

	private static final Field META_DATA_KEY = field(MetaDataEntry.class, "key");

	private static final Field META_DATA_OBJECT = field(MetaDataEntry.class, "object");

	private static final Class<?> COMPONENT_STATE = componentStateClass();

	private static final Field STATE_MODEL = field(COMPONENT_STATE, "model");

	private static final Field STATE_BEHAVIORS = field(COMPONENT_STATE, "behaviors");

	private static final Field STATE_META_DATA = field(COMPONENT_STATE, "metaData");

	private static final ClassValue<Field[]> ENCLOSING_INSTANCE_FIELDS = new ClassValue<>()
	{
		@Override
		protected Field[] computeValue(Class<?> type)
		{
			List<Field> fields = new ArrayList<>();
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
			{
				for (Field f : c.getDeclaredFields())
				{
					if (isEnclosingInstance(f) && Component.class.isAssignableFrom(f.getType()))
					{
						f.setAccessible(true);
						fields.add(f);
					}
				}
			}
			return fields.toArray(new Field[0]);
		}
	};

	private static final ClassValue<Boolean> CUSTOM_SERIALIZATION = new ClassValue<>()
	{
		@Override
		protected Boolean computeValue(Class<?> type)
		{
			for (Class<?> c = type; c != Component.class; c = c.getSuperclass())
			{
				for (Method m : c.getDeclaredMethods())
				{
					if ((m.getName().equals("writeObject") || m.getName().equals("writeReplace")) &&
						!Modifier.isStatic(m.getModifiers()))
					{
						return true;
					}
				}
			}
			return false;
		}
	};

	private final Set<String> excludedFields = new HashSet<>();

	private final Map<Class<?>, Integer> ignoredFlags = new LinkedHashMap<>();

	private final Set<MetaDataKey<?>> excludedMetaData = new HashSet<>();

	private boolean fullClassDescriptors;

	private boolean trackSharing;

	private boolean resetPerField = true;

	private final Map<Class<?>, Slot[]> layouts = new HashMap<>();

	private final Map<Class<?>, Integer> masks = new HashMap<>();

	/**
	 * A hasher that excludes nothing.
	 */
	public ComponentStateHasher()
	{
	}

	/**
	 * @return a hasher excluding the state Wicket changes on its own during a request
	 */
	public static ComponentStateHasher withDefaultExclusions()
	{
		ComponentStateHasher hasher = new ComponentStateHasher();
		hasher.excludeField(Page.class, "renderCount");
		hasher.excludeField(Page.class, "autoIndex");
		hasher.excludeField(Page.class, "numericId");
		// Page FLAG_IS_DIRTY and FLAG_PREVENT_DIRTY
		hasher.ignoreFlags(Page.class, 0x0400 | 0x0800);
		// Form FLAG_SUBMITTED
		hasher.ignoreFlags(Form.class, 0x0100);
		// recomputed multipart hints
		hasher.excludeField(Form.class, "multiPart");
		hasher.excludeField(FormComponent.class, "rawInput");
		hasher.excludeMetaData(staticValue(Component.class, "FEEDBACK_KEY"));
		return hasher;
	}

	/**
	 * Leaves a field out of the hash.
	 * 
	 * @param declaringClass
	 *            the class declaring the field
	 * @param name
	 *            the name of the field
	 * @return this
	 */
	public ComponentStateHasher excludeField(Class<?> declaringClass, String name)
	{
		excludedFields.add(declaringClass.getName() + "#" + name);
		layouts.clear();
		masks.clear();
		return this;
	}

	/**
	 * Leaves bits of {@code Component#flags} out of the hash for components of a type.
	 * 
	 * @param type
	 *            the component type, including subclasses
	 * @param mask
	 *            the bits to ignore
	 * @return this
	 */
	public ComponentStateHasher ignoreFlags(Class<? extends Component> type, int mask)
	{
		ignoredFlags.merge(type, mask, (a, b) -> a | b);
		layouts.clear();
		masks.clear();
		return this;
	}

	/**
	 * Leaves a meta data entry out of the hash.
	 * 
	 * @param key
	 *            the key of the entry
	 * @return this
	 */
	public ComponentStateHasher excludeMetaData(MetaDataKey<?> key)
	{
		excludedMetaData.add(key);
		return this;
	}

	/**
	 * Writes the class descriptors as Java serialization does, instead of their name only. Slower,
	 * but {@link StateSnapshot.ComponentState#getSize()} then equals the size of each component
	 * serialized on its own.
	 * 
	 * @param full
	 *            whether to write full descriptors
	 * @return this
	 */
	public ComponentStateHasher setFullClassDescriptors(boolean full)
	{
		this.fullClassDescriptors = full;
		return this;
	}

	/**
	 * Resets the stream's handle table per field, the default, or only per component. Per field
	 * keeps a change in one field from shifting the back references, and so the digest, of the
	 * fields after it; per component makes {@link StateSnapshot.ComponentState#getSize()} the size
	 * of the component serialized on its own.
	 * 
	 * @param perField
	 *            whether to reset per field
	 * @return this
	 */
	public ComponentStateHasher setResetPerField(boolean perField)
	{
		this.resetPerField = perField;
		return this;
	}

	/**
	 * Counts the objects reached from more than one component.
	 * 
	 * @param track
	 *            whether to track
	 * @return this
	 */
	public ComponentStateHasher setTrackSharing(boolean track)
	{
		this.trackSharing = track;
		return this;
	}

	/**
	 * Hashes the page and all its descendants, visible or not.
	 * 
	 * @param page
	 *            the page
	 * @return the snapshot
	 */
	public StateSnapshot snapshot(Page page)
	{
		long start = System.nanoTime();
		Map<String, StateSnapshot.ComponentState> states = new LinkedHashMap<>();
		try
		{
			List<Component> components = new ArrayList<>();
			components.add(page);
			page.visitChildren((component, visit) -> components.add(component));
			IdentityHashMap<Component, String> paths = new IdentityHashMap<>(components.size());
			paths.put(page, "");
			for (int i = 1; i < components.size(); i++)
			{
				paths.put(components.get(i), components.get(i).getPageRelativePath());
			}
			Walker walker = new Walker(page, paths);
			for (Component component : components)
			{
				states.put(paths.get(component), walker.hash(component));
			}
			StateSnapshot snapshot = new StateSnapshot(states, System.nanoTime() - start);
			lastSharedObjects = walker.shared;
			return snapshot;
		}
		catch (IOException e)
		{
			throw new WicketRuntimeException(e);
		}
	}

	private Map<Class<?>, Integer> lastSharedObjects = Map.of();

	/**
	 * @return per class, the number of objects the last snapshot reached from more than one
	 *         component, if {@link #setTrackSharing(boolean) tracked}
	 */
	public Map<Class<?>, Integer> getLastSharedObjects()
	{
		return lastSharedObjects;
	}

	private enum Kind
	{
		FLAGS, CHILDREN, DATA, INT, LONG, BOOLEAN, SHORT, BYTE, CHAR, FLOAT, DOUBLE, OBJECT
	}

	/**
	 * A serializable field of a component class that is hashed.
	 */
	private record Slot(Field field, String key, int keyHash, Kind kind, boolean enclosing)
	{
	}

	private Slot[] layout(Class<?> type)
	{
		return layouts.computeIfAbsent(type, c -> {
			List<Class<?>> hierarchy = new ArrayList<>();
			for (Class<?> h = c; h != Object.class; h = h.getSuperclass())
			{
				hierarchy.add(0, h);
			}
			List<Slot> slots = new ArrayList<>();
			for (Class<?> h : hierarchy)
			{
				ObjectStreamClass desc = ObjectStreamClass.lookup(h);
				if (desc == null)
				{
					continue;
				}
				for (ObjectStreamField streamField : desc.getFields())
				{
					String key = h.getName() + "#" + streamField.getName();
					if (excludedFields.contains(key) || key.equals(FIELD_PARENT))
					{
						continue;
					}
					Field field = field(h, streamField.getName());
					slots.add(new Slot(field, key, key.hashCode(), kind(key, field.getType()),
						isEnclosingInstance(field)));
				}
			}
			return slots.toArray(new Slot[0]);
		});
	}

	private static Kind kind(String key, Class<?> type)
	{
		if (key.equals(FIELD_FLAGS))
		{
			return Kind.FLAGS;
		}
		if (key.equals(FIELD_CHILDREN))
		{
			return Kind.CHILDREN;
		}
		if (key.equals(FIELD_DATA))
		{
			return Kind.DATA;
		}
		if (type == int.class)
		{
			return Kind.INT;
		}
		if (type == long.class)
		{
			return Kind.LONG;
		}
		if (type == boolean.class)
		{
			return Kind.BOOLEAN;
		}
		if (type == short.class)
		{
			return Kind.SHORT;
		}
		if (type == byte.class)
		{
			return Kind.BYTE;
		}
		if (type == char.class)
		{
			return Kind.CHAR;
		}
		if (type == float.class)
		{
			return Kind.FLOAT;
		}
		if (type == double.class)
		{
			return Kind.DOUBLE;
		}
		return Kind.OBJECT;
	}

	private int flagMask(Component component)
	{
		return masks.computeIfAbsent(component.getClass(), type -> {
			int mask = 0;
			for (Map.Entry<Class<?>, Integer> entry : ignoredFlags.entrySet())
			{
				if (entry.getKey().isAssignableFrom(type))
				{
					mask |= entry.getValue();
				}
			}
			return mask;
		});
	}

	private static boolean isEnclosingInstance(Field field)
	{
		return field.isSynthetic() && field.getName().startsWith("this$");
	}

	private static long mix(long hash, long value)
	{
		long h = (hash ^ value) * 0x9E3779B97F4A7C15L;
		return h ^ (h >>> 29);
	}

	private static Field field(Class<?> type, String name)
	{
		try
		{
			Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		}
		catch (NoSuchFieldException e)
		{
			throw new WicketRuntimeException(e);
		}
	}

	private static Class<?> componentStateClass()
	{
		try
		{
			return Class.forName("org.apache.wicket.ComponentState");
		}
		catch (ClassNotFoundException e)
		{
			throw new WicketRuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T staticValue(Class<?> type, String name)
	{
		try
		{
			return (T)field(type, name).get(null);
		}
		catch (IllegalAccessException e)
		{
			throw new WicketRuntimeException(e);
		}
	}

	/**
	 * Stands in for a component of the page reached from another component.
	 */
	private record ComponentRef(String path) implements Serializable
	{
	}

	private final class Walker
	{
		private final Page page;

		private final IdentityHashMap<Component, String> paths;

		private final HashingOutputStream bytes = new HashingOutputStream();

		private final StateOutputStream out;

		private final Map<Class<?>, Integer> shared;

		private final IdentityHashMap<Object, Component> owners;

		private final IdentityHashMap<Object, Boolean> counted;

		Walker(Page page, IdentityHashMap<Component, String> paths) throws IOException
		{
			this.page = page;
			this.paths = paths;
			out = new StateOutputStream(bytes);
			bytes.take();
			shared = trackSharing ? new HashMap<>() : Map.of();
			owners = trackSharing ? new IdentityHashMap<>() : null;
			counted = trackSharing ? new IdentityHashMap<>() : null;
		}

		StateSnapshot.ComponentState hash(Component component)
		{
			try
			{
				return hashUnchecked(component);
			}
			catch (IOException | IllegalAccessException e)
			{
				throw new WicketRuntimeException("Could not hash " + component, e);
			}
		}

		private StateSnapshot.ComponentState hashUnchecked(Component component)
			throws IOException, IllegalAccessException
		{
			out.reset();
			out.root = component;
			out.dependencies.clear();
			out.fallback = CUSTOM_SERIALIZATION.get(component.getClass());

			Map<String, Long> fields = new LinkedHashMap<>();
			long hash = 17;
			long size = 0;

			if (out.fallback)
			{
				out.rootPending = true;
				out.writeObject(component);
				out.flush();
				size += bytes.count();
				long digest = bytes.take();
				fields.put("*", digest);
				hash = mix(hash, digest);
			}
			else
			{
				for (Slot slot : layout(component.getClass()))
				{
					if (slot.kind == Kind.DATA)
					{
						Map<String, Object> elements = new LinkedHashMap<>();
						unpackData(slot.field.get(component), elements);
						for (Map.Entry<String, Object> element : elements.entrySet())
						{
							resetField();
							out.writeObject(element.getValue());
							out.flush();
							size += bytes.count();
							long digest = bytes.take();
							String elementKey = slot.key + "." + element.getKey();
							fields.put(elementKey, digest);
							hash = mix(mix(hash, elementKey.hashCode()), digest);
						}
						continue;
					}
					resetField();
					writeField(component, slot);
					out.flush();
					size += bytes.count();
					long digest = bytes.take();
					fields.put(slot.key, digest);
					hash = mix(mix(hash, slot.keyHash), digest);
				}
			}

			Map<String, DependencyKind> dependencies = new LinkedHashMap<>();
			for (Map.Entry<Component, DependencyKind> dependency : out.dependencies.entrySet())
			{
				dependencies.put(paths.get(dependency.getKey()), dependency.getValue());
			}
			return new StateSnapshot.ComponentState(paths.get(component),
				component.getClass(), hash, size, fields, dependencies);
		}

		private void writeField(Component component, Slot slot)
			throws IOException, IllegalAccessException
		{
			Field field = slot.field;
			switch (slot.kind)
			{
				case FLAGS -> out.writeInt(field.getInt(component) & ~flagMask(component));
				case CHILDREN -> {
					for (Component child : (MarkupContainer)component)
					{
						out.writeUTF(child.getId());
					}
				}
				case INT -> out.writeInt(field.getInt(component));
				case LONG -> out.writeLong(field.getLong(component));
				case BOOLEAN -> out.writeBoolean(field.getBoolean(component));
				case SHORT -> out.writeShort(field.getShort(component));
				case BYTE -> out.writeByte(field.getByte(component));
				case CHAR -> out.writeChar(field.getChar(component));
				case FLOAT -> out.writeFloat(field.getFloat(component));
				case DOUBLE -> out.writeDouble(field.getDouble(component));
				default -> {
					Object value = field.get(component);
					if (slot.enclosing && value instanceof Component other)
					{
						out.dependency(other, DependencyKind.INNER_CLASS);
					}
					out.writeObject(value);
				}
			}
		}

		/**
		 * Flattens {@code Component#data} into its model, behaviors and meta data entries, each
		 * under a label that stays the same as long as the element is the same kind of thing.
		 */
		private void unpackData(Object data, Map<String, Object> elements)
			throws IllegalAccessException
		{
			if (data == null)
			{
				return;
			}
			if (COMPONENT_STATE.isInstance(data))
			{
				unpackData(STATE_MODEL.get(data), elements);
				unpackData(STATE_BEHAVIORS.get(data), elements);
				unpackData(STATE_META_DATA.get(data), elements);
			}
			else if (data instanceof Object[] array)
			{
				for (Object element : array)
				{
					unpackData(element, elements);
				}
			}
			else if (data instanceof MetaDataEntry<?> entry)
			{
				Object key = META_DATA_KEY.get(entry);
				if (!excludedMetaData.contains(key))
				{
					elements.put("meta:" + key.getClass().getName(), META_DATA_OBJECT.get(entry));
				}
			}
			else if (data instanceof Behavior)
			{
				elements.put("behavior" + elements.size() + ":" + data.getClass().getName(), data);
			}
			else
			{
				elements.put("model", data);
			}
		}

		private void resetField() throws IOException
		{
			if (resetPerField)
			{
				out.reset();
			}
		}

		private boolean inPage(Component component)
		{
			return paths.containsKey(component);
		}

		private final class StateOutputStream extends ObjectOutputStream
		{
			private Component root;

			/** Whether the root is written as a whole, because it serializes itself. */
			private boolean fallback;

			private boolean rootPending;

			private final Map<Component, DependencyKind> dependencies = new IdentityHashMap<>();

			StateOutputStream(HashingOutputStream out) throws IOException
			{
				super(out);
				enableReplaceObject(true);
			}

			void dependency(Component component, DependencyKind kind)
			{
				if (component != root && inPage(component))
				{
					dependencies.putIfAbsent(component, kind);
				}
			}

			@Override
			protected Object replaceObject(Object obj) throws IOException
			{
				if (obj instanceof Component component)
				{
					if (component == root)
					{
						if (rootPending)
						{
							rootPending = false;
							return obj;
						}
						return new ComponentRef(".");
					}
					if (!inPage(component))
					{
						return obj;
					}
					boolean structural = fallback &&
						(component == root.getParent() || component.getParent() == root);
					if (!structural)
					{
						dependencies.putIfAbsent(component, DependencyKind.REFERENCE);
					}
					return new ComponentRef(paths.get(component));
				}

				if (obj instanceof SerializedLambda lambda)
				{
					for (int i = 0; i < lambda.getCapturedArgCount(); i++)
					{
						if (lambda.getCapturedArg(i) instanceof Component captured)
						{
							dependency(captured, DependencyKind.LAMBDA);
						}
					}
				}
				else
				{
					for (Field field : ENCLOSING_INSTANCE_FIELDS.get(obj.getClass()))
					{
						try
						{
							if (field.get(obj) instanceof Component enclosing)
							{
								dependency(enclosing, DependencyKind.INNER_CLASS);
							}
						}
						catch (IllegalAccessException e)
						{
							throw new WicketRuntimeException(e);
						}
					}
				}

				if (owners != null && !(obj instanceof String || obj instanceof Number ||
					obj instanceof Enum || obj instanceof Boolean || obj instanceof Character))
				{
					Component owner = owners.putIfAbsent(obj, root);
					if (owner != null && owner != root && counted.put(obj, Boolean.TRUE) == null)
					{
						shared.merge(obj.getClass(), 1, Integer::sum);
					}
				}
				return obj;
			}

			@Override
			protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException
			{
				if (fullClassDescriptors)
				{
					super.writeClassDescriptor(desc);
				}
				else
				{
					writeUTF(desc.getName());
				}
			}
		}
	}
}
