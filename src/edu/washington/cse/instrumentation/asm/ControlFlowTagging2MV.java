package edu.washington.cse.instrumentation.asm;

import java.util.ArrayList;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;

import edu.washington.cse.instrumentation.runtime.CheckLevel;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class ControlFlowTagging2MV extends FieldMatchingMV<ComparePatterns> implements Opcodes {
	
	private final CheckLevel checkLevel;
	
	public ControlFlowTagging2MV(MethodVisitor mv, CheckLevel checkLevel) {
		super(mv,
			new MatchPattern<>(ComparePatterns.BI_ACMP,
				new InsnNode(SWAP),
				new InsnNode(POP),
				new InsnNode(DUP2_X1),
				new InsnNode(POP2),
				new InsnNode(POP),
				new AbstractJumpInsn(FieldMatchingMV.BINARY_INT_JUMP_OP)
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
	protected void handleMatch(
			ComparePatterns currPattern,
			ArrayList<FieldInsnNode> savedFields,
			ArrayList<SavedJump> jumps) {
		assert currPattern == ComparePatterns.BINARY_INT;
		assert jumps.size() == 1;
		assert savedFields.size() == 0;
		super.visitInsn(SWAP);
		pushTag();
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
		mv.visitInsn(DUP2_X1);
		mv.visitInsn(POP2);
		pushTag();
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "recordCF", "(Ljava/lang/Object;I)V", false);
		super.visitJumpInsn(jumps.get(0).opcode, jumps.get(0).target);
	}
}
