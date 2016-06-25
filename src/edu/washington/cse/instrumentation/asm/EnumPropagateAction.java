package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import edu.columbia.cs.psl.phosphor.TaintUtils;

public class EnumPropagateAction extends TaintPropagator {
	private final String enumName;
	private final int argIndex;
	public EnumPropagateAction(int argIndex, String internalName) {
		this.enumName = internalName;
		this.argIndex = argIndex;
	}

	@Override
	public void onCallAction(MethodVisitor mv, int opcode,
			String owner, String name, String desc, boolean itf) {
		this.setupCall(mv, opcode, owner, name, desc, itf);
		mv.visitLdcInsn(Type.getObjectType(enumName));
		mv.visitInsn(SWAP);
		mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintUtils.class), "enumValueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
		mv.visitTypeInsn(CHECKCAST, enumName);
		mv.visitVarInsn(ASTORE, argIndex);
	}
}
