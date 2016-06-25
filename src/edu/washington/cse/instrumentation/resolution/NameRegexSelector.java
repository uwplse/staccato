package edu.washington.cse.instrumentation.resolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javassist.CtClass;
import javassist.CtMethod;
import edu.washington.cse.instrumentation.TaintUtils;

public class NameRegexSelector implements MethodSpecGenerator<MethodSpec> {

	private final Pattern p;

	public NameRegexSelector(Pattern p) {
		this.p = p;
	}
	
	@Override
	public Collection<MethodSpec> findMethods(CtClass theKlass, Set<CtClass> taintClasses) {
		List<MethodSpec> toRet = new ArrayList<>();
		for(CtMethod m : theKlass.getDeclaredMethods()) {
			if(p.matcher(m.getName()).matches()) {
				toRet.add(MethodSpec.ofMethod(m, TaintUtils.extractTaintArgs(m, taintClasses)));
			}
		}
		return toRet;
	}
}
