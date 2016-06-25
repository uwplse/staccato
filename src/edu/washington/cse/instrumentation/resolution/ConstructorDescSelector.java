package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.TaintUtils;

public class ConstructorDescSelector implements MethodSpecGenerator<MethodSpec> {
	private final String desc;
	private final boolean isPhosphor;

	public ConstructorDescSelector(String desc, boolean isPhosphor) {
		this.desc = desc;
		this.isPhosphor = isPhosphor;
	}
	
	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass,
			Set<CtClass> taintClasses) {
		List<MethodSpec> simpleRet = collectSimpleDesc(theKlass, taintClasses, desc);
		if(!isPhosphor) {
			return simpleRet;
		}
		String phosphorDesc = TaintUtils.transformPhosphorCt(desc);
		if(phosphorDesc.equals(desc)) {
			return simpleRet;
		}
		List<MethodSpec> extRet = collectSimpleDesc(theKlass, taintClasses, phosphorDesc);
		if(extRet.size() == 0) {
			return simpleRet;
		} else if(simpleRet.isEmpty()) {
			return extRet;
		} else {
			extRet.add(simpleRet.get(0));
			return extRet;
		}
	}

	private List<MethodSpec> collectSimpleDesc(CtClass theKlass,
			Set<CtClass> taintClasses, String ctDesc) {
		CtConstructor ct;
		try {
			ct = theKlass.getConstructor(ctDesc);
		} catch (NotFoundException e) {
			throw new IllegalArgumentException("Could not find constructor: " + ctDesc, e);
		}
		int[] args = TaintUtils.extractTaintArgs(ct, taintClasses);
		if(args != null) {
			ArrayList<MethodSpec> toRet = new ArrayList<>();
			toRet.add(new MethodSpec("<init>", ctDesc, args));
			return toRet;
		} else {
			return new ArrayList<>();
		}
	}
}
