package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.TaintUtils;

public class TaintFilter implements MethodSpecGenerator<MethodSpec> {
	@Override
	public List<MethodSpec> findMethods(CtClass theKlass,
			Set<CtClass> taintClasses) {
		List<MethodSpec> toRet = new ArrayList<>();
		for(CtMethod method : theKlass.getDeclaredMethods()) {
			boolean methodMatches = false;
			try {
				methodMatches = filterMethod(method);
			} catch (NotFoundException e) {
				methodMatches = true;
			}
			int[] args;
			if((args = TaintUtils.extractTaintArgs(method, taintClasses)) != null && methodMatches) {
				toRet.add(MethodSpec.ofMethod(method, args));
			}
		}
		return toRet;
	}
	
	protected boolean filterMethod(CtMethod method) throws NotFoundException {
		return true;
	}

}
