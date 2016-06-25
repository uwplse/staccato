package edu.washington.cse.instrumentation.asm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LabelNode;


public abstract class FieldMatchingMV<T extends Enum<T>> extends MethodVisitor implements Opcodes {
	private static final int ABSTRACT_FIELD_INSN = 14;
	private static final int ABSTRACT_JUMP_INSN = 15;
	
	public static final int UNARY_INT_JUMP_OP = 200;
	public static final int BINARY_INT_JUMP_OP = 201;
	public static final int UNARY_REF_JUMP_OP = 202;
	public static final int BINARY_REF_JUMP_OP = 203;
	
	
	public static class SavedJump {
		final int opcode;
		final Label target;
		SavedJump(int opcode, Label target) {
			this.opcode = opcode;
			this.target = target;
		}
	}
	
	protected final Map<T, MatchPattern<T>> patterns;
	protected T currPattern;
	protected boolean pauseChecking = false;
	private int pattIdx = 0;
	protected List<FieldInsnNode> savedFields = new ArrayList<>();
	protected List<SavedJump> savedJumps = new ArrayList<>();
	
	private abstract static class AbstractMatchingNode extends AbstractInsnNode {
		protected AbstractMatchingNode() {
			super(255);
		}
		@Override
		public void accept(MethodVisitor arg0) {
			throw new UnsupportedOperationException();			
		}
		@Override
		public AbstractInsnNode clone(Map<LabelNode, LabelNode> arg0) {
			throw new UnsupportedOperationException();
		}
	}
	
	public static class AbstractFieldInsn extends AbstractMatchingNode {
		final int wrappedOpcode;
		protected AbstractFieldInsn(int opcode) {
			this.wrappedOpcode = opcode;
		}
		@Override
		public int getType() {
			return ABSTRACT_FIELD_INSN;
		}
	}
	
	public static class AbstractJumpInsn extends AbstractMatchingNode {
		private final int jumpType;
		public AbstractJumpInsn(int jumpType) {
			super();
			this.jumpType = jumpType;
		}
		@Override
		public int getType() {
			return ABSTRACT_JUMP_INSN;
		}
	}
	
	@SafeVarargs
	public FieldMatchingMV(MethodVisitor mv, MatchPattern<T>... patterns) {
		super(ASM5, mv);
		this.patterns = new HashMap<>();
		for(MatchPattern<T> patt : patterns) {
			this.patterns.put(patt.tag, patt);
		}
		currPattern = null;
	}
	
	@Override
	public void visitIntInsn(int opcode, int operand) {
		this.breakMatch();
		super.visitIntInsn(opcode, operand);
	}


	@Override
	public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
			Object... bsmArgs) {
		this.breakMatch();
		super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		if(pauseChecking) {
			super.visitJumpInsn(opcode, label);
			return;
		}
		this.tryMatch(opcode, label);
	}

	@Override
	public void visitLabel(Label label) {
		this.breakMatch();
		super.visitLabel(label);
	}

	@Override
	public void visitLdcInsn(Object cst) {
		this.breakMatch();
		super.visitLdcInsn(cst);
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		this.breakMatch();
		super.visitLookupSwitchInsn(dflt, keys, labels);
	}

	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		this.breakMatch();
		super.visitMaxs(maxStack, maxLocals);
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
//		if(pauseChecking) {
//			super.visitMethodInsn(opcode, owner, name, desc, itf);
//			return;
//		}
//		this.tryMatch(opcode, owner, name, desc, itf);
		breakMatch();
		super.visitMethodInsn(opcode, owner, name, desc, itf);
	}
	
	/*
	private boolean nodeMatchesMethod(int opcode, String owner, String name, String desc,
			boolean itf, AbstractInsnNode asn) {
		if(asn.getType() != AbstractInsnNode.METHOD_INSN) {
			return false;
		}
		MethodInsnNode n = (MethodInsnNode) asn;
		return n.getOpcode() == opcode &&
				n.owner.equals(owner) &&
				n.name.equals(name) &&
				n.desc.equals(desc) &&
				n.itf == itf;
	}
	
	private void tryMatch(int opcode, String owner, String name, String desc,
			boolean itf) {
		if(currPattern == null) {
			this.tryFreshMatch(opcode, owner, name, desc);
			return;
		} else {
			List<AbstractInsnNode> insns = patterns.get(currPattern).pattern;
			if(pattIdx > insns.size()) {
				throw new IllegalStateException();
			}
			AbstractInsnNode asn = insns.get(pattIdx);
			if(!nodeMatchesMethod(opcode, owner, name, desc, itf, asn)) {
				breakMatch();
				tryFreshMatch(opcode, owner, name, desc, itf);
				return;
			}
			advance();
		}
	}*/
/*
	private void tryFreshMatch(int opcode, String owner, String name,
			String desc, boolean itf) {
		assert currPattern == null;
		assert pattIdx == 0;
		for(Map.Entry<T, MatchPattern<T>> kv : patterns.entrySet()) {
			List<AbstractInsnNode> insns = kv.getValue().pattern;
			if(nodeMatchesMethod(opcode, owner, name, desc, itf, insns.get(0))) {
				currPattern = kv.getKey();
				advance();
				return;
			}
		}
		super.visitMethodInsn(opcode, owner, name, desc, itf);
	}*/

	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		this.breakMatch();
		super.visitMultiANewArrayInsn(desc, dims);
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt,
			Label... labels) {
		this.breakMatch();
		super.visitTableSwitchInsn(min, max, dflt, labels);
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		this.breakMatch();
		super.visitTypeInsn(opcode, type);
	}

	@Override
	public void visitVarInsn(int opcode, int var) {
		this.breakMatch();
		super.visitVarInsn(opcode, var);
	}
	
	@Override
	public void visitEnd() {
		breakMatch();
		super.visitEnd();
	}
	

	@Override
	public void visitFrame(int type, int nLocal, Object[] local, int nStack,
			Object[] stack) {
		assert type == Opcodes.F_FULL;
		this.breakMatch();
		super.visitFrame(type, nLocal, local, nStack, stack);
	}

	@Override
	public void visitIincInsn(int var, int increment) {
		this.breakMatch();
		super.visitIincInsn(var, increment);
	}
	
	private void breakMatch() {
		if(pauseChecking) {
			return;
		}
		pauseChecking = true;
		flush();
		pauseChecking = false;
	}
	
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if(pauseChecking) {
			super.visitFieldInsn(opcode, owner, name, desc);
			return;
		}
		this.tryMatch(opcode, owner, name, desc);
	}
	
	@Override
	public void visitInsn(int opcode) {
		if(pauseChecking) {
			super.visitInsn(opcode);
			return;
		}
		this.tryMatch(opcode);
	}
	
	private void flush() {
		if(currPattern == null) {
			return;
		}
		List<AbstractInsnNode> insns = patterns.get(currPattern).pattern;
		int fieldIdx = 0;
		int jumpIdx = 0;
		for(int j = 0; j < pattIdx; j++) {
			AbstractInsnNode ins = insns.get(j);
			int insType = ins.getType();
			if(insType == ABSTRACT_FIELD_INSN) {
				FieldInsnNode fi = savedFields.get(fieldIdx++);
				this.doFieldOp(fi.getOpcode(), fi.owner, fi.name, fi.desc);
			} else if(insType == ABSTRACT_JUMP_INSN) {
				SavedJump sj = savedJumps.get(jumpIdx++);
				super.visitJumpInsn(sj.opcode, sj.target);
			}/* else if(insType == AbstractFieldInsn.METHOD_INSN) {
				MethodInsnNode min = (MethodInsnNode)ins;
				super.visitMethodInsn(min.opcode, min.owner, min.name, min.desc, min.itf);
			}*/ else {
				assert insType == AbstractInsnNode.INSN;
				super.visitInsn(ins.getOpcode());
			}
		}
		reset();
	}
	
	private int getJumpType(int opcode) {
		int jumpType;
		switch(opcode) {
		case IF_ACMPEQ:
		case IF_ACMPNE:
			jumpType = BINARY_REF_JUMP_OP;
			break;
		case IF_ICMPEQ:
		case IF_ICMPGE:
		case IF_ICMPGT:
		case IF_ICMPLE:
		case IF_ICMPLT:
		case IF_ICMPNE:
			jumpType = BINARY_INT_JUMP_OP;
			break;
		case IFEQ:
		case IFNE:
		case IFGT:
		case IFGE:
		case IFLE:
		case IFLT:
			jumpType = UNARY_INT_JUMP_OP;
			break;
		case IFNONNULL:
		case IFNULL:
			jumpType = UNARY_REF_JUMP_OP;
			break;
		case GOTO:
			jumpType = -1;
			break;
		default:
			throw new IllegalArgumentException("Forgot: " + opcode);
		}
		return jumpType;
	}
	
	private void tryMatch(int opcode, Label target) {
		if(currPattern == null) {
			tryFreshMatch(opcode, target);
			return;
		} else {
			List<AbstractInsnNode> insns = patterns.get(currPattern).pattern;
			if(pattIdx > insns.size()) {
				throw new IllegalStateException();
			}
			AbstractInsnNode asn = insns.get(pattIdx);
			if(!insnMatchesJump(opcode, target, asn)) {
				breakMatch();
				tryFreshMatch(opcode, target);
				return;
			}
			this.savedJumps.add(new SavedJump(opcode, target));
			advance();
		}
	}
	
	private boolean insnMatchesJump(int opcode, Label target, AbstractInsnNode asn) {
		return asn.getType() == ABSTRACT_JUMP_INSN && ((AbstractJumpInsn)asn).jumpType == getJumpType(opcode);
	}
	
	private void tryFreshMatch(int opcode, Label target) {
		assert currPattern == null;
		assert pattIdx == 0;
		for(Map.Entry<T, MatchPattern<T>> kv : patterns.entrySet()) {
			List<AbstractInsnNode> insns = kv.getValue().pattern;
			if(insnMatchesJump(opcode, target, insns.get(0))) {
				currPattern = kv.getKey();
				this.savedJumps.add(new SavedJump(opcode, target));
				advance();
				return;
			}
		}
		super.visitJumpInsn(opcode, target);
	}
	
	protected void doFieldOp(int opcode, String owner, String name, String desc) {
		super.visitFieldInsn(opcode, owner, name, desc);
	}

	void tryMatch(int opcode) {
		if(currPattern == null) {
			tryFreshMatch(opcode);
			return;
		} else {
			List<AbstractInsnNode> insns = patterns.get(currPattern).pattern;
			if(pattIdx > insns.size()) {
				throw new IllegalStateException();
			}
			AbstractInsnNode asn = insns.get(pattIdx);
			if(asn.getType() != AbstractInsnNode.INSN || (asn.getType() == AbstractInsnNode.INSN && asn.getOpcode() != opcode)) {
				breakMatch();
				tryFreshMatch(opcode);
				return;
			}
			advance();
		}
	}
	
	private void tryFreshMatch(int opcode) {
		assert currPattern == null;
		assert pattIdx == 0;
		for(Map.Entry<T, MatchPattern<T>> kv : patterns.entrySet()) {
			List<AbstractInsnNode> insns = kv.getValue().pattern;
			if(insns.get(0).getOpcode() == opcode) {
				currPattern = kv.getKey();
				advance();
				return;
			}
		}
		super.visitInsn(opcode);
	}
	
	private void reset() {
		pattIdx = 0;
		currPattern = null;
		savedFields.clear();
		savedJumps.clear();
	}
	
	private void tryMatch(int opcode, String owner, String name, String desc) {
		if(currPattern == null) {
			this.tryFreshMatch(opcode, owner, name, desc);
			return;
		} else {
			MatchPattern<T> patt = patterns.get(currPattern);
			List<AbstractInsnNode> insns = patt.pattern;
			if(pattIdx > insns.size()) {
				throw new IllegalStateException();
			}
			AbstractInsnNode asn = insns.get(pattIdx);
			if(asn.getType() != ABSTRACT_FIELD_INSN || ((AbstractFieldInsn)asn).wrappedOpcode != opcode || !patt.filterField(opcode, owner, name, desc, savedFields)) {
				breakMatch();
				tryFreshMatch(opcode, owner, name, desc);
				return;
			}
			this.savedFields.add(new FieldInsnNode(opcode, owner, name, desc));
			advance();
		}
	}
	
	private void advance() {
		assert currPattern != null;
		if(pattIdx++ == patterns.get(currPattern).pattern.size() - 1) {
			pauseChecking = true;
			handleMatch(currPattern, new ArrayList<>(savedFields), new ArrayList<>(savedJumps));
			pauseChecking = false;
			reset();
		}
	}
	
	protected void handleMatch(T currPattern, ArrayList<FieldInsnNode> savedFields, ArrayList<SavedJump> jumps) {
		handleMatch(currPattern, savedFields);
	}

	protected abstract void handleMatch(T currPattern, ArrayList<FieldInsnNode> savedFields);

	private void tryFreshMatch(int opcode, String owner, String name,
			String desc) {
		assert currPattern == null;
		assert pattIdx == 0;
		for(Map.Entry<T, MatchPattern<T>> kv : patterns.entrySet()) {
			List<AbstractInsnNode> insns = kv.getValue().pattern;
			if(insns.get(0).getType() == ABSTRACT_FIELD_INSN 
				&& ((AbstractFieldInsn)insns.get(0)).wrappedOpcode == opcode
				&& kv.getValue().filterField(opcode, owner, name, desc, savedFields)) {
				savedFields.add(new FieldInsnNode(opcode, owner, name, desc));
				currPattern = kv.getKey();
				advance();
				return;
			}
		}
		pauseChecking = true;
		this.doFieldOp(opcode, owner, name, desc);
		pauseChecking = false;
	}
}
