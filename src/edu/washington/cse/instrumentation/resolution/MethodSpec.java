package edu.washington.cse.instrumentation.resolution;

import javassist.CtMethod;

/**
 * A MethodSpec exactly specifies a method. This includes the name and the
 * description string. Sub-classes of MethodSpec can add more information about
 * the method, such as the propagation target, the level of checking, etc.
 */
public class MethodSpec {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return name + ":" + description;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		MethodSpec other = (MethodSpec) obj;
		if (!description.equals(other.description)) {
			return false;
		}
		if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	public final String description;
	public final String name;
	public final int[] taintIndices;

	public MethodSpec(String name, String description, int[] taintIndices) {
		if(description == null) {
			throw new IllegalArgumentException();
		}
		if(name == null) {
			throw new IllegalArgumentException();
		}
		this.name = name;
		this.description = description;
		this.taintIndices = taintIndices;
	}
	public static MethodSpec ofMethod(CtMethod meth, int[] taintIndices) {
		return new MethodSpec(meth.getName(), meth.getMethodInfo().getDescriptor(), taintIndices);
	}
}