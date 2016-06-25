package edu.washington.cse.instrumentation.resolution;

import javassist.CtMethod;
import javassist.NotFoundException;

public class NamedTaintGenerator extends TaintFilter {
	private final String methodName;
	private final boolean isPhosphor;
	public NamedTaintGenerator(String methodName, boolean isPhosphor) {
		this.methodName = methodName;
		this.isPhosphor = isPhosphor;
	}
	
	@Override
	protected boolean filterMethod(CtMethod method) throws NotFoundException {
		return method.getName().equals(methodName) || (isPhosphor && method.getName().equals(methodName + "$$PHOSPHORTAGGED"));
	}
}
