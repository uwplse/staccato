package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import edu.washington.cse.instrumentation.runtime.PropagationTarget;

public class PropagateWrapper implements PropagationGenerator {
	private final MethodSpecGenerator<MethodSpec> m;
	private final PropagationTarget t;

	public PropagateWrapper(MethodSpecGenerator<MethodSpec> m, PropagationTarget t) {
		this.m = m;
		this.t = t;
	}

	@Override
	public Collection<PropagationSpec> findMethods(CtClass theKlass,
			Set<CtClass> taintClasses) {
		List<PropagationSpec> toRet = new ArrayList<>();
		Collection<MethodSpec> meths = this.m.findMethods(theKlass, taintClasses);
		for(MethodSpec ms : meths) {
			toRet.add(new PropagationSpec(ms.name, ms.description, ms.taintIndices, t));
		}
		return toRet;
	}
}
