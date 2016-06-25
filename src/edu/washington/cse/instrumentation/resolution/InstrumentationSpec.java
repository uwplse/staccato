package edu.washington.cse.instrumentation.resolution;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class InstrumentationSpec {
	public final AutoPropagateSpec autoPropagateSpec;
	public final Set<String> wrappedVolatileFields;
	public final Collection<PropagationSpec> toPropagate;
	public final Collection<CheckSpec> toCheck;

	public InstrumentationSpec(
		Collection<CheckSpec> toCheck,
		Collection<PropagationSpec> toPropagate,
		Set<String> wrappedVolatileFields,
		AutoPropagateSpec autoPropagateSpec
	) {
		this.toCheck = toCheck == null ? new HashSet<CheckSpec>() : toCheck;
		this.toPropagate = toPropagate;
		this.wrappedVolatileFields = wrappedVolatileFields;
		this.autoPropagateSpec = autoPropagateSpec;
	}
}
