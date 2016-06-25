package edu.washington.cse.instrumentation.resolution;

import java.util.Arrays;

import javassist.CtBehavior;
import edu.washington.cse.instrumentation.runtime.CheckLevel;

public class CheckSpec extends MethodSpec {
	public final CheckLevel checkLevel;
	public final boolean checkBody;
	public CheckSpec(String name, String description, int[] taintIndex) {
		super(name, description, taintIndex);
		this.checkLevel = CheckLevel.LINEAR;
		this.checkBody = false;
	}
	public CheckSpec(String name, String description, int[] argIndices, CheckLevel checkType , boolean checkBody) {
		super(name, description, argIndices);
		this.checkLevel = checkType;
		this.checkBody = checkBody;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (checkBody ? 1231 : 1237);
		result = prime * result
				+ ((checkLevel == null) ? 0 : checkLevel.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		CheckSpec other = (CheckSpec) obj;
		if (checkBody != other.checkBody) {
			return false;
		}
		if (checkLevel != other.checkLevel) {
			return false;
		}
		return true;
	}

	
	
	@Override
	public String toString() {
		return "CheckSpec [checkLevel=" + checkLevel + ", checkBody=" + checkBody
				+ ", description=" + description + ", name=" + name + ", taintIndices="
				+ Arrays.toString(taintIndices) + "]";
	}
	public CheckSpec merge(CheckSpec other) {
		assert (this.name == other.name || this.name.equals(other.name)) &&
			this.description.equals(other.description);
		assert Arrays.equals(other.taintIndices, this.taintIndices);
		return new CheckSpec(this.name, this.description, this.taintIndices, this.checkLevel.merge(other.checkLevel), this.checkBody || other.checkBody);
	}
	
	public CheckSpec forMethod(CtBehavior cb) {
		return new CheckSpec(
			cb.getMethodInfo().isConstructor() ? null : cb.getName(),
			cb.getMethodInfo().getDescriptor(),
			this.taintIndices, this.checkLevel, this.checkBody);
	}
}
