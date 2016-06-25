package edu.washington.cse.instrumentation.asm;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class AutoPropagateMV extends MethodVisitor implements Opcodes {
	private final String setterMethod;
	private final String setterOwner;
	LocalVariablesSorter lvs;
	private final String methodOwner;
	private final String methodName;
	private final String methodDesc;
	private final SetterAction sa;
	
	public AutoPropagateMV(MethodVisitor mv,
		String methodOwner, String methodName, 
		String methodDesc, 
		String setterOwner, String setterMethod,
		SetterAction sa
	) {
		super(ASM5, mv);
		this.methodOwner = methodOwner;
		this.methodName = methodName;
		this.methodDesc = methodDesc;
		
		this.setterOwner = setterOwner;
		this.setterMethod = setterMethod;
		this.sa = sa;
	}
	
	private boolean seenFieldWrite = false;
	private boolean seenFieldRead = false;
	
	private final Set<String> readFields = new HashSet<>();
	private int propIndex;
	private Label start;
	private Label end;
	
	public String getLocationString() {
		return this.methodOwner + "." + methodName + ":" + methodDesc;
	}
	
	@Override
	public void visitCode() {
		super.visitCode();
		this.propIndex = lvs.newLocal(Type.getType(String.class));
		super.visitInsn(ACONST_NULL);
		super.visitVarInsn(ASTORE, propIndex);
		this.start = new Label();
		this.end = new Label();
		super.visitTryCatchBlock(start, end, end, null);
		super.visitLabel(start);
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		super.visitLabel(end);
		super.visitVarInsn(ALOAD, propIndex);
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "endUpdateSingle", "(Ljava/lang/String;)V", false);
		super.visitInsn(ATHROW);
		super.visitMaxs(maxStack, maxLocals);
	}
	
	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
		if(owner.equals(methodOwner)) {
			if(opcode == PUTFIELD || opcode == PUTSTATIC) {
//				if(readFields.contains(name)) {
//					System.out.println("WARNING: Found read of field in " + this.getLocationString() + " before write!");
//				}
				seenFieldWrite = true;
			} else {
				seenFieldRead = true;
				readFields.add(name);	
			}
		}
		super.visitFieldInsn(opcode, owner, name, desc);
	}
	
	@Override
	public void visitLdcInsn(Object cst) {
		super.visitLdcInsn(cst);
		if(!(cst instanceof String)) {
			return;
		}
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "copyString", "(Ljava/lang/String;)Ljava/lang/String;", false);
	}
	
	@Override
	public void visitInsn(int opcode) {
		if(opcode >= IRETURN && opcode <= RETURN) {
			super.visitVarInsn(ALOAD, propIndex);
			super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "endUpdateSingle", "(Ljava/lang/String;)V", false);
		}
		super.visitInsn(opcode);
	}
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if(!owner.equals(this.setterOwner) || !name.equals(setterMethod)) {
			super.visitMethodInsn(opcode, owner, name, desc, itf);
			return;
		}
		if(seenFieldRead || seenFieldWrite) {
			System.out.println("WARNING: Found set call AFTER field read/write in " + this.getLocationString());
		}
		super.visitInsn(SWAP);
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "startUpdateSingle", "(Ljava/lang/String;)V", false);
		super.visitInsn(DUP);
		super.visitVarInsn(ASTORE, propIndex);
		super.visitInsn(SWAP);
		// P V
		this.sa.onCallAction(this.mv, opcode, owner, name, desc, itf);
	}
}
