package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class MethodExceptionFixer extends MethodNode implements Opcodes {
	private final MethodVisitor next;
	CheckMethodVisitor cmv;

	public MethodExceptionFixer(int access, String name, String desc, String signature, String[] exceptions, MethodVisitor mv) {
		super(ASM5, access, name, desc, signature, exceptions);
		this.next = mv;
	}
	
	@Override
	public void visitEnd() {
		super.visitEnd();
		int index = 0;
		for(; index < this.tryCatchBlocks.size(); index++) {
			TryCatchBlockNode tcbn = this.tryCatchBlocks.get(index);
			if(tcbn.start == cmv.startLabel.info) {
				tryCatchBlocks.remove(index);
				tryCatchBlocks.add(tcbn);
				this.accept(next);
				return;
			}
		}
		throw new RuntimeException("Not found?!");
	}

}
