package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;

public class NoAction implements SetterAction {
	@Override
	public void onCallAction(MethodVisitor autoPropagateMV, int opcode,
			String owner, String name, String desc, boolean itf) {
		autoPropagateMV.visitMethodInsn(opcode, owner, name, desc, itf);
	}
}
