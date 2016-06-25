package edu.washington.cse.instrumentation.resolution;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;


public class ReturnTypeSelector extends TaintFilter {
	private final CtClass retType;
	public ReturnTypeSelector(CtClass retType) {
		this.retType = retType;
	}
	
	@Override
	protected boolean filterMethod(CtMethod method) throws NotFoundException {
		return super.filterMethod(method) && method.getReturnType().subtypeOf(retType);
	}
}
