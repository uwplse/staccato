package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class RetPopStateMV extends MethodVisitor implements Opcodes {
	private final boolean isAccessMethod;

	public RetPopStateMV(MethodVisitor mv, boolean isAccessMethod) {
		super(Opcodes.ASM5, mv);
		this.isAccessMethod = isAccessMethod;
	}
	
	@Override
	public void visitInsn(int opcode) {
		if(opcode <= RETURN && opcode >= IRETURN) {
			if(isAccessMethod) {
				super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "popAccessState", "()V", false);
			} else {
				super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "popState", "()V", false);
			}
		}
		super.visitInsn(opcode);
	}
}
