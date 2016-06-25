package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ReturnCoalescerMV extends MethodVisitor implements Opcodes {
	private int foundOpcode = -1;
	private Label endLabel = null;

	public ReturnCoalescerMV(MethodVisitor mv) {
		super(ASM5, mv);
	}

	@Override
	public void visitInsn(int opcode) {
		if(opcode <= RETURN && opcode >= IRETURN) {
			if(foundOpcode != -1) {
				assert foundOpcode == opcode;
			} else {
				foundOpcode = opcode;
			}
			if(endLabel == null) {
				endLabel = new Label();
			}
			super.visitJumpInsn(GOTO, endLabel);
			return;
		} else {
			super.visitInsn(opcode);
		}
	}

	@Override
	public void visitMaxs(int a, int b) {
		if(foundOpcode == -1) {
			assert endLabel == null;
			super.visitMaxs(a, b);
			return;
		}
		super.visitLabel(endLabel);
		super.visitInsn(foundOpcode);
		super.visitMaxs(a, b);
	}
	
}
