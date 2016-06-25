package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.washington.cse.instrumentation.runtime.TaintHelper;

abstract public class TaintPropagator implements SetterAction, Opcodes {
	protected void setupCall(MethodVisitor mv, int opcode, String owner, String name, String desc, boolean itf) {
		mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "copyString", "(Ljava/lang/String;)Ljava/lang/String;", false);
		if(opcode == INVOKESTATIC) { 
			mv.visitInsn(DUP_X1); // N P N
		} else {
			mv.visitInsn(DUP_X2); // N O P N
		}
		mv.visitMethodInsn(opcode, owner, name, desc, itf);
		Type returnType = Type.getReturnType(desc);
		if(returnType.getSize() == 1) {
			mv.visitInsn(SWAP);
		} else if(returnType.getSize() == 2) {
			mv.visitInsn(DUP2_X1);
			mv.visitInsn(POP2);
		}
	}
}
