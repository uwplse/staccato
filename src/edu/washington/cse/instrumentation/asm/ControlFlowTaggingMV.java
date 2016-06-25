package edu.washington.cse.instrumentation.asm;

import java.util.ArrayList;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;

import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.washington.cse.instrumentation.runtime.CheckLevel;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

enum ComparePatterns {
	BI_ACMP,
	U_ACMP,
	BINARY_INT,
	UNARY_INT
}

public class ControlFlowTaggingMV extends FieldMatchingMV<ComparePatterns> implements Opcodes {
	private final CheckLevel checkLevel;

	public ControlFlowTaggingMV(MethodVisitor mv, CheckLevel checkLevel) {
		super(mv,
			new MatchPattern<>(ComparePatterns.BI_ACMP, new AbstractJumpInsn(FieldMatchingMV.BINARY_REF_JUMP_OP)),
//			new MatchPattern<>(ComparePatterns.U_ACMP, new AbstractJumpInsn(FieldMatchingMV.UNARY_REF_JUMP_OP)),
			new MatchPattern<>(ComparePatterns.UNARY_INT,
				new InsnNode(SWAP),
				new InsnNode(POP),
				new AbstractJumpInsn(FieldMatchingMV.UNARY_INT_JUMP_OP)
			)
		);
		this.checkLevel = checkLevel;
	}

	private void pushTag() {
		if(checkLevel == CheckLevel.LINEAR) {
			super.visitInsn(ICONST_1);
		} else if(checkLevel == CheckLevel.STRICT || checkLevel == CheckLevel.TRANSACT) {
			super.visitInsn(ICONST_0);
		} else {
			throw new IllegalArgumentException();
		}
	}
	
	@Override
	protected void handleMatch(ComparePatterns currPattern,
			ArrayList<FieldInsnNode> savedFields) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if(opcode == INVOKESTATIC &&
			owner.equals(Type.getInternalName(TaintUtils.class)) &&
			name.equals("ensureUnboxed") &&
			desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;") &&
			itf == false) {
			super.visitInsn(DUP);
			pushTag();
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
		}
		super.visitMethodInsn(opcode, owner, name, desc, itf);
	}
	
	@Override
	protected void handleMatch(
			ComparePatterns currPattern,
			ArrayList<FieldInsnNode> savedFields,
			ArrayList<SavedJump> jumps) {
		assert savedFields.size() == 0;
		assert currPattern != ComparePatterns.BINARY_INT;
		if(currPattern == ComparePatterns.U_ACMP) {
			super.visitInsn(DUP);
			pushTag();
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
		} else if(currPattern == ComparePatterns.BI_ACMP) {
			super.visitInsn(DUP);
			pushTag();
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
			super.visitInsn(SWAP);
			super.visitInsn(DUP);
			pushTag();
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
			super.visitInsn(SWAP);
		} else if(currPattern == ComparePatterns.UNARY_INT) {
			super.visitInsn(SWAP);
			pushTag();
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
		} else {
			throw new IllegalArgumentException("Bad variant: " + currPattern);
		}
		assert jumps.size() == 1;
		super.visitJumpInsn(jumps.get(0).opcode, jumps.get(0).target);
	}
}
