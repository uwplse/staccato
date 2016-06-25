package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.washington.cse.instrumentation.StaccatoConfig;
import edu.washington.cse.instrumentation.runtime.CheckLevel;
import edu.washington.cse.instrumentation.runtime.StaccatoRuntime;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class CheckMethodVisitor extends TaintArgVisitor implements Opcodes {
	final Label endLabel;
	final Label startLabel;
	private final CheckLevel checkLevel;
	private final boolean isAccessMethod;

	public CheckMethodVisitor(MethodVisitor mv, int[] args, CheckLevel v, boolean isAccessMethod) {
		super(mv, args);
		this.checkLevel = v;
		this.startLabel = new Label();
		this.endLabel = new Label();
		this.isAccessMethod = isAccessMethod;
	}
	
	@Override
	public void visitCode() {
		super.visitCode();
		if(isAccessMethod) {
			super.visitInsn(ICONST_4);
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "openState", "(I)V", false);
		} else if(this.checkLevel == CheckLevel.STRICT || this.checkLevel == CheckLevel.TRANSACT) {
			super.visitInsn(ICONST_1);
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "openState", "(I)V", false);
		} else {
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "openState", "()V", false);
		}
		super.visitTryCatchBlock(startLabel, endLabel, endLabel, null);
		super.visitLabel(startLabel);
		if(checkIndexes != null && checkIndexes.length != 0) {
			loadTaintedArgArray();
			String methodName;
			if(this.checkLevel == CheckLevel.LINEAR) {
				if(StaccatoConfig.CHECK_METHOD_LINEAR) {
					methodName = "checkLinGlobTaint";
				} else {
					methodName = "checkLinTaint";
				}
			} else {
				methodName = "checkArgTaint";
			}
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), methodName, "([Ljava/lang/Object;)V", false);
		}
	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if(!(opcode == INVOKESTATIC && owner.equals(Type.getInternalName(StaccatoRuntime.class)) && name.equals("commit"))) {
			super.visitMethodInsn(opcode, owner, name, desc, itf);
			return;
		}
		assert this.checkLevel == CheckLevel.TRANSACT;
		super.visitInsn(POP);
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "startTransaction", "()V", false);
	}
		
	@Override
	public void visitMaxs(int a, int b) {
//		System.out.println("Adding throws");
		super.visitLabel(endLabel);
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "popState", "()V", false);
		super.visitInsn(ATHROW);
		super.visitMaxs(a, b);
	}
}
