package edu.washington.cse.instrumentation.resolution;

import java.util.Collection;
import java.util.Set;

import javassist.CtClass;

/**
 * This class is responsible for extracting methods (according to some criteria) from a given class.
 * This class is used to implement the rule file selection and Check/Propagate annotations on classes.
 */
public interface MethodSpecGenerator<T extends MethodSpec> {
	public Collection<T> findMethods(CtClass theKlass, Set<CtClass> taintClasses);
}
