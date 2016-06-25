package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;

public class TaintArgVisitor extends MethodVisitor implements Opcodes {
	protected final int[] checkIndexes;

	public TaintArgVisitor(MethodVisitor mv, int[] args) {
		super(ASM5, mv);
		this.checkIndexes = args;
	}
	

	private void pushInt(int v) {
		switch(v) {
		case 0:
			super.visitInsn(ICONST_0);
			break;
		case 1:
			super.visitInsn(ICONST_1);
			break;
		case 2:
			super.visitInsn(ICONST_2);
			break;
		case 3:
			super.visitInsn(ICONST_3);
			break;
		case 4:
			super.visitInsn(ICONST_4);
			break;
		case 5:
			super.visitInsn(ICONST_5);
			break;
		default:
			if(v < Byte.MAX_VALUE) {
				super.visitIntInsn(BIPUSH, v);
			} else if(v < Short.MAX_VALUE) {
				super.visitIntInsn(SIPUSH, v);
			} else {
				super.visitLdcInsn(new Integer(v));
			}
		}
	}
	
	protected void loadTaintedArgArray() {
		if(checkIndexes == null) {
			pushInt(0);
			super.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
			return;
		}
		int numArgs = 0;
		for(int i = 0; i < checkIndexes.length; i++) {
			if(checkIndexes[i] == -1) {
				break;
			}
			numArgs++;
		}
		assert numArgs > 0;
		pushInt(numArgs);
		super.visitTypeInsn(ANEWARRAY, Type.getInternalName(Object.class));
		for(int i = 0; i < numArgs; i++) {
			int argIndex = checkIndexes[i];
			super.visitInsn(DUP);
			this.pushInt(i);
			visitVarInsn(ALOAD, argIndex);
			super.visitInsn(AASTORE);
		}
	}
}
