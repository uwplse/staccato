package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import edu.washington.cse.instrumentation.runtime.CheckLevel;

public class CheckWrapper implements CheckGenerator {
	private final MethodSpecGenerator<MethodSpec> m;
	private final CheckLevel t;
	private final boolean checkBody;

	public CheckWrapper(MethodSpecGenerator<MethodSpec> m, CheckLevel t, boolean checkBody) {
		this.m = m;
		this.t = t;
		this.checkBody = checkBody;
	}

	@Override
	public Collection<CheckSpec> findMethods(CtClass theKlass, Set<CtClass> taintClasses) {
		Collection<MethodSpec> meths = this.m.findMethods(theKlass, taintClasses);
		List<CheckSpec> toRet = new ArrayList<>();
		for(MethodSpec ms : meths) {
			toRet.add(new CheckSpec(ms.name, ms.description, ms.taintIndices, t, this.checkBody));
		}
		return toRet;
	}

	@Override
	public String toString() {
		return "CheckWrapper [m=" + m + ", t=" + t + "]";
	}
}
