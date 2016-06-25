package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import javassist.CtMethod;
import edu.washington.cse.instrumentation.TaintUtils;

public class AllMethods implements MethodSpecGenerator<MethodSpec> {
	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass,
			Set<CtClass> taintClasses) {
		List<MethodSpec> toRet = new ArrayList<>();
		for(CtMethod meth : theKlass.getDeclaredMethods()) {
			toRet.add(MethodSpec.ofMethod(meth, TaintUtils.extractTaintArgs(meth, taintClasses)));
		}
		return toRet;
	}
}
