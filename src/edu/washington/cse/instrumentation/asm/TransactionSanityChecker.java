package edu.washington.cse.instrumentation.asm;

import java.util.regex.Pattern;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;
import edu.columbia.cs.psl.phosphor.runtime.BoxedPrimitiveStoreWithObjTags;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.washington.cse.instrumentation.runtime.StaccatoRuntime;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class TransactionSanityChecker extends MethodVisitor implements Opcodes {
	private final String sourceFile;
	
	public TransactionSanityChecker(String sourceFile, MethodVisitor mv) {
		super(Opcodes.ASM5, mv);
		this.sourceFile = sourceFile;
	}
	
	private boolean foundCommit = false;
	int lineNumber = -1;
	
	@Override
	public void visitLineNumber(int line, Label start) {
		lineNumber = line;
	}
	
	private final static Pattern m = Pattern.compile("^java/lang/(Float|Integer|Long|Double)$");
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if(this.foundCommit) {
			return;
		}
		if(opcode == INVOKESTATIC && name.startsWith("access$") && Type.getReturnType(desc).getSort() != Type.VOID) {
			return;
		}
		if(owner.equals(Type.getInternalName(StaccatoRuntime.class)) && name.equals("commit")) {
			this.foundCommit = true;
			return;
		}
		if(owner.equals(Type.getInternalName(BoxedPrimitiveStoreWithObjTags.class)) && name.equals("valueOf")) {
			return;
		}
		if(isWrapperType(owner) && (name.equals("<init>") || name.equals("valueOf"))) {
			return;
		}
		if(owner.equals(Type.getInternalName(ControlTaintTagStack.class)) && name.equals("<init>")) {
			return;
		}
		if(owner.equals(Type.getInternalName(TaintHelper.class))){ 
			return;
		}
//		throw new RuntimeException("Illegal call to method: " + name + getLocationString());
	}
	
	private String getLocationString() {
		return ((lineNumber != -1) ? " on line " + lineNumber : "") + " in file " + sourceFile;
	}
	
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if(this.foundCommit) {
			return;
		}
		if(opcode != GETFIELD && opcode != GETSTATIC) {
			throw new RuntimeException("Illegal read of field: " + owner + "." + name + getLocationString());
		}
	}
	
	@Override
	public void visitTypeInsn(int opcode, String type) {
		if(foundCommit) {
			return;
		}
		if(opcode == NEW && (isWrapperType(type) || type.equals(Type.getInternalName(ControlTaintTagStack.class)))) {
			return;
		}
		if(opcode == ANEWARRAY && type.equals("java/lang/Object")) {
			return;
		}
		String errorString;
		switch(opcode) {
		case ANEWARRAY:
			errorString = "creation of array " + type + "[]";
			break;
		case NEW:
			errorString = "creation of " + type;
			break;
		case CHECKCAST:
			errorString = "cast to " + type;
			break;
		case INSTANCEOF:
			errorString = "check of type " + type;
		default:
			throw new IllegalArgumentException("Bad opcode: " + opcode);
		}
		throw new RuntimeException("Illegal " + errorString + " " + getLocationString());
	}
	
	@Override
	public void visitMultiANewArrayInsn(String desc, int dims) {
		if(!foundCommit) {
			throw new RuntimeException("Illegal creation of multi-dimensional array " + desc + " " + getLocationString());
		}
	}
	
	@Override
	public void visitEnd() {
		if(!foundCommit) {
			throw new RuntimeException("Failed to find call to commit() in " + sourceFile);
		}
		super.visitEnd();
	}
	
	private boolean isWrapperType(String kl) {
		return m.matcher(kl).matches() || kl.startsWith("edu/columbia/cs/psl/phosphor/struct/Tainted");
	}
}
