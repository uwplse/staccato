package edu.washington.cse.instrumentation.resolution;

import java.util.Collection;
import java.util.Set;

import javassist.CtClass;
import javassist.CtConstructor;
import edu.washington.cse.instrumentation.TaintUtils;

public class AllBehaviors extends AllMethods {
	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass,
			Set<CtClass> taintClasses) {
		Collection<MethodSpec> cms = super.findMethods(theKlass, taintClasses);
		for(CtConstructor ctor : theKlass.getDeclaredConstructors()) {
			cms.add(new MethodSpec("<init>", ctor.getMethodInfo().getDescriptor(), TaintUtils.extractTaintArgs(ctor, taintClasses)));
		}
		return cms;
	}
}
