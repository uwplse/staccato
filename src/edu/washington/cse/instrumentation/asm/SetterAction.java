package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;

public interface SetterAction {
	public void onCallAction(MethodVisitor autoPropagateMV, int opcode, String owner, String name, String desc, boolean itf);
}
