package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.TaintUtils;

public class MethodNameGenerator implements MethodSpecGenerator<MethodSpec> {
	private final String methodName;
	private final boolean isPhosphor;
	public MethodNameGenerator(String methodName, boolean isPhosphor) {
		this.methodName = methodName;
		this.isPhosphor = isPhosphor;
	}
	
	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass, Set<CtClass> taintClasses) {
		try {
			return findMethodsThrow(theKlass, taintClasses);
		} catch (NotFoundException e) {
			throw new IllegalArgumentException("Could not find method named " + methodName + " in class " + theKlass.getName(), e);
		}
	}
	
	
	private Collection<MethodSpec> findMethodsThrow(CtClass theKlass, Set<CtClass> taintClasses) throws NotFoundException {
		List<MethodSpec> toRet = new ArrayList<>();
		for(CtMethod meth : theKlass.getDeclaredMethods()) {
			if(meth.getName().equals(methodName) || (isPhosphor && meth.getName().equals(methodName + "$$PHOSPHORTAGGED"))) {
				toRet.add(MethodSpec.ofMethod(meth, TaintUtils.extractTaintArgs(meth, taintClasses)));
			}
		}
		return toRet;
	}
}
