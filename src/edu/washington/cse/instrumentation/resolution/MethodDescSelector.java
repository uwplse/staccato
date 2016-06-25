package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.TaintUtils;

public class MethodDescSelector implements MethodSpecGenerator<MethodSpec> {
	private final String methodName, methodDescriptor;
	private final boolean isPhosphor;
	public MethodDescSelector(String methodName, String methodDescriptor, boolean isPhosphor) {
		this.methodDescriptor = methodDescriptor;
		this.methodName = methodName;
		this.isPhosphor = isPhosphor;
	}
	
	public MethodDescSelector(CtMethod meth) {
		this(meth.getName(), meth.getMethodInfo().getDescriptor(), false);
	}

	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass, Set<CtClass> taintClasses) {
		try {
			return findMethodsThrow(theKlass, taintClasses);
		} catch (NotFoundException e) {
			throw new IllegalArgumentException("Could not find specified method: " + methodName + ":" + methodDescriptor, e);
		}
	}
	
	private Collection<MethodSpec> findMethodsThrow(CtClass theKlass, Set<CtClass> taintClasses) throws NotFoundException {
		CtMethod meth = theKlass.getMethod(methodName, methodDescriptor);
		List<MethodSpec> toRet = new ArrayList<>();
		toRet.add(MethodSpec.ofMethod(meth, TaintUtils.extractTaintArgs(meth, taintClasses)));
		if(!isPhosphor) {
			return toRet;
		}
		String phosphorDesc = TaintUtils.transformPhosphorMeth(methodDescriptor);
		if(phosphorDesc.equals(methodDescriptor)) {
			return toRet;
		}
		CtMethod phosMeth = theKlass.getMethod(methodName + "$$PHOSPHORTAGGED", phosphorDesc);
		toRet.add(MethodSpec.ofMethod(phosMeth, TaintUtils.extractTaintArgs(phosMeth, taintClasses)));
		return toRet;
	}

	@Override
	public String toString() {
		return "MethodDescSelector [methodName=" + methodName
				+ ", methodDescriptor=" + methodDescriptor + "]";
	}
}
