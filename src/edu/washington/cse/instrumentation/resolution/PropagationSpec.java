package edu.washington.cse.instrumentation.resolution;

import edu.washington.cse.instrumentation.runtime.PropagationTarget;

public class PropagationSpec extends MethodSpec {
	public final PropagationTarget target;
	public PropagationSpec(String name, String description, int[] args) {
		super(name, description, args);
		this.target = PropagationTarget.RETURN;
	}
	
	public PropagationSpec(String name, String description, int[] args, PropagationTarget target) {
		super(name, description, args);
		this.target = target;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return super.toString() + "->" + target;
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
		PropagationSpec other = (PropagationSpec) obj;
		if (target != other.target) {
			return false;
		}
		return true;
	}
	
	
}
