package edu.washington.cse.instrumentation.resolution;

import javassist.CtMethod;
import javassist.NotFoundException;

public class NamePrefixGenerator extends TaintFilter {
	private final String prefix;

	public NamePrefixGenerator(String prefix) {
		this.prefix = prefix;
	}
	
	@Override
	protected boolean filterMethod(CtMethod method) throws NotFoundException {
		return super.filterMethod(method) && method.getName().startsWith(prefix);
	}	
}
