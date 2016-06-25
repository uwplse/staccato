package edu.washington.cse.instrumentation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javassist.CtClass;

public class SystemClassGenerator implements ClassGenerator {
	private Set<CtClass> klassSet;

	@SafeVarargs
	public SystemClassGenerator(Collection<CtClass>... klasses) {
		klassSet = new HashSet<>();
		for(Collection<CtClass> klassColle : klasses) {
			klassSet.addAll(klassColle);
		}
	}
	
	@Override
	public Iterator<CtClass> iterator() {
		return klassSet.iterator();
	}

}
