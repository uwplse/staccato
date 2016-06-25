package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.columbia.cs.psl.phosphor.runtime.Taint;

public class MethodProxySanityChecker extends MethodVisitor implements Opcodes {
	private final String expectedDesc;
	private final String expectedName;
	
	private boolean foundForwardCall = false;
	private final String expectedOwner;
	private final String sourceFile;
	private int lineNumber = -1;

	public MethodProxySanityChecker(String sourceFile, String expectedOwner, String expectedName, String expectedDesc, MethodVisitor mv) {
		super(ASM5, mv);
		this.expectedName = expectedName;
		this.expectedDesc = expectedDesc;
		this.expectedOwner = expectedOwner;
		this.sourceFile = sourceFile;
	}
	
	@Override
	public void visitLineNumber(int line, Label start) {
		super.visitLineNumber(line, start);
		this.lineNumber = line;
	}
	
	private void reportError(String operation, String operand) {
		throw new RuntimeException("Illegal " + operation + ": " + operand + (lineNumber != -1 ? " on line " + lineNumber : "") + " in file: " + sourceFile);
	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if(owner.equals(expectedOwner) && name.equals(expectedName) && desc.equals(expectedDesc)) {
			if(foundForwardCall) {
				throw new RuntimeException("Found an extra forwarded call");
			}
			foundForwardCall = true;
			mv.visitMethodInsn(opcode, owner, name, desc, itf);
		} else if(name.equals("<init>") && opcode == Opcodes.INVOKESPECIAL && owner.startsWith("edu/columbia/cs/psl/phosphor/struct")) {
			mv.visitMethodInsn(opcode, owner, name, desc, itf);
		} else if(opcode == INVOKESTATIC && owner.equals("edu/columbia/cs/psl/phosphor/struct/multid/MultiDTaintedArrayWithObjTag") && name.equals("initWithEmptyTaints")) {
			mv.visitMethodInsn(opcode, owner, name, desc, itf);
		} else if(opcode == INVOKESTATIC && owner.equals("edu/columbia/cs/psl/phosphor/runtime/NativeHelper") && (name.equals("ensureIsBoxed") || name.equals("ensureIsUnBoxed"))) {
			mv.visitMethodInsn(opcode, owner, name, desc, itf);
		} else {
			reportError("call to", owner + "." + name + ":" + desc);
		}
	}
	
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if(!owner.equals(Type.getReturnType(expectedDesc).getInternalName()) || !name.equals("val")) {
			reportError("read of field", owner + "." + name + ":" + desc);
		}
		super.visitFieldInsn(opcode, owner, name, desc);
	}
	
	@Override
	public void visitTypeInsn(int opcode, String type) {
		switch(opcode) {
		case NEW:
			if(!type.startsWith("edu/columbia/cs/psl/phosphor/struct")) {
				reportError("creation of", type);
			}
			break;
		case CHECKCAST:
			break;
		case ANEWARRAY:
			if(!type.equals(Type.getInternalName(Taint.class))) {
				reportError("new array of", type);
			}
			break;
		case INSTANCEOF:
			reportError("instance of check", type);
		}
		super.visitTypeInsn(opcode, type);
	}
	
	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		reportError("creation of multi-dimensional array", desc);
	}
	
	@Override
	public void visitEnd() {
		if(!foundForwardCall) {
			throw new RuntimeException("Did not find call to method: " + expectedName + ":" + expectedDesc);
		}
		super.visitEnd();
	}
}
