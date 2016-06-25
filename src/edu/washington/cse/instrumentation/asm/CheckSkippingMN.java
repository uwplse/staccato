package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import edu.washington.cse.instrumentation.TaintUtils;

public class CheckSkippingMN extends MethodNode implements Opcodes {
	private final MethodVisitor next;

	public CheckSkippingMN(final int access, final String name,
      final String desc, final String signature, final String[] exceptions,
      MethodVisitor mv) {
		super(ASM5, access, name, desc, signature, exceptions);
		this.next = mv;
	}
	
	@Override
	public void visitEnd() {
		super.visitEnd();
		
		AbstractInsnNode insn = this.instructions.getFirst();
		
		while(insn != null) {
			if(insn.getType() == AbstractInsnNode.METHOD_INSN) {
				MethodInsnNode min = (MethodInsnNode)insn;
				Type returnType = Type.getReturnType(min.desc);
				boolean discarded;
				if(returnType.getSize() == 2) {
					discarded = min.getNext() != null && min.getNext().getOpcode() == POP2;
				} else {
					discarded = min.getNext() != null && min.getNext().getOpcode() == POP;
				}
				if(discarded) {
					this.instructions.insertBefore(insn, new InsnNode(TaintUtils.SKIP_CHECK_OPCODE));
				}
			}
			insn = insn.getNext();
		}
		
		this.accept(next);
	}
}
