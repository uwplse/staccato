package edu.washington.cse.instrumentation.asm;

import java.util.Set;

import org.objectweb.asm.MethodVisitor;

public class VolatileFieldReadMV extends TaintedFieldReadMV {
	public VolatileFieldReadMV(MethodVisitor mv, String thisClass, Set<String> volatileFields) {
		super(mv, thisClass, volatileFields);
	}
}
