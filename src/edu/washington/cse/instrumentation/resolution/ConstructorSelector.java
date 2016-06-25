package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import javassist.CtClass;
import javassist.CtConstructor;
import edu.washington.cse.instrumentation.TaintUtils;

public class ConstructorSelector implements MethodSpecGenerator<MethodSpec> {

	public ConstructorSelector() {
	}

	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass,
			Set<CtClass> taintClasses) {
		ArrayList<MethodSpec> spec = new ArrayList<>();
		for(CtConstructor constr: theKlass.getDeclaredConstructors()) {
			int args[] = null;
			if((args = TaintUtils.extractTaintArgs(constr, taintClasses)) == null) {
				continue;
			}
			spec.add(new MethodSpec("<init>", constr.getMethodInfo().getDescriptor(), args));
		}
		return spec;
	}

}
