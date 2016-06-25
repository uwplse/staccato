package edu.washington.cse.instrumentation.asm;

import java.util.Map;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.washington.cse.instrumentation.StaccatoConfig;
import edu.washington.cse.instrumentation.TaintProxy;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class LinearCheckingMV extends AbstractCheckingMV {
	private static final String THELPER_INAME = Type.getInternalName(TaintHelper.class);
	private static final String LIN_CHECK_SIG = "(Ljava/lang/Object;)V";
	private static final String CHECK_SINGLE_LIN_TAINT = StaccatoConfig.CHECK_METHOD_LINEAR ? "recordRead" :  "checkSingleLinTaint";

	public LinearCheckingMV(MethodVisitor mv, String fileName, TaintProxy taintProxy, Map<String, Set<String>> lockingFields, String className, Set<String> volatileFields) {
		super(mv, fileName, taintProxy, lockingFields, className, volatileFields, CHECK_SINGLE_LIN_TAINT);
	}

	@Override
	protected void addFieldLoad(String owner, String fieldName, String fieldDesc) {
		Type t = Type.getType(fieldDesc);
		if(!taintProxy.isTypeTainted(t) || skipCheck(owner, fieldName, fieldDesc)) {
			super.visitFieldInsn(GETFIELD, owner, fieldName, fieldDesc);
			return;
		}
		LockLabels ll = startLocking(GETFIELD, owner, fieldName);
		super.addFieldLoad(owner, fieldName, fieldDesc); // V
		endLocking(GETFIELD, owner, ll);
		
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, THELPER_INAME, CHECK_SINGLE_LIN_TAINT, LIN_CHECK_SIG, false);
	}
	
	@Override
	protected void addStaticFieldLoad(String owner, String name, String desc) {
		Type t = Type.getType(desc);
		if(!taintProxy.isTypeTainted(t)) {
			super.addStaticFieldLoad(owner, name, desc);
			return;
		}
		LockLabels ll = startLocking(GETSTATIC, owner, name);
		super.addStaticFieldLoad(owner, name, desc);
		endLocking(GETSTATIC, owner, ll);
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, THELPER_INAME, CHECK_SINGLE_LIN_TAINT, LIN_CHECK_SIG, false);
	}
	
	private void checkVolatileField(int opcode, String name, String desc) {
		Type containerType = Type.getType(TaintUtils.getContainerReturnType(desc).getDescriptor());
		String taintType = Type.getInternalName(Taint.class);
		LockLabels ll = startLocking(opcode, seenOwner, name);
		super.visitFieldInsn(opcode, seenOwner, name, containerType.getDescriptor());
		endLocking(opcode, seenOwner, ll);
		super.visitInsn(DUP);
		super.visitFieldInsn(GETFIELD, containerType.getInternalName(), "taint", Type.getDescriptor(Object.class));
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, THELPER_INAME, CHECK_SINGLE_LIN_TAINT, LIN_CHECK_SIG, false);
		super.visitTypeInsn(CHECKCAST, taintType);
		super.visitInsn(SWAP);
		super.visitFieldInsn(GETFIELD, containerType.getInternalName(), "val", desc);
	}
	
	@Override
	protected void addPrimitiveFieldRead(String name, String desc) {
		if(Type.getType(desc).getSort() == Type.ARRAY){
			LockLabels ll = startLocking(GETFIELD, seenOwner, name);
			super.addPrimitiveFieldRead(name, desc);
			endLocking(GETFIELD, seenOwner, ll);
			return;
		}
		if(volatileFields != null && seenOwner.equals(this.className) && volatileFields.contains(name)) {
			this.checkVolatileField(GETFIELD, name, desc);
			return;
		}
		LockLabels ll = startLocking(GETFIELD, seenOwner, name);
		super.visitInsn(DUP);
		super.visitFieldInsn(GETFIELD, seenOwner, seenField, seenType);
		super.visitInsn(SWAP);
		super.visitFieldInsn(GETFIELD, seenOwner, name, desc);
		endLocking(GETFIELD, seenOwner, ll); // T VV?
		Type t = Type.getType(desc);
		if(t.getSize() == 2) { // T VV
			super.visitInsn(DUP2_X1); // VV T VV
			super.visitInsn(POP2); // VV T
		} else {
			// T V
			super.visitInsn(SWAP); // V T
		}
		super.visitInsn(DUP); // VV? T T
		super.visitMethodInsn(INVOKESTATIC, THELPER_INAME, CHECK_SINGLE_LIN_TAINT, LIN_CHECK_SIG, false);
		// VV T?
		if(t.getSize() == 2) {
			super.visitInsn(DUP_X2);
			super.visitInsn(POP);
		} else {
			super.visitInsn(SWAP);
		}
	}
	
	@Override
	protected void addPrimitiveStaticFieldRead(String name, String desc) {
		Type fieldType = Type.getType(desc);
		if(fieldType.getSort() == Type.ARRAY) {
			LockLabels ll = startLocking(GETSTATIC, seenOwner, name);
			super.addPrimitiveStaticFieldRead(name, desc);
			endLocking(GETSTATIC, seenOwner, ll);
			return;
		}
		if(volatileFields != null && seenOwner.equals(this.className) && this.volatileFields.contains(name)) {
			checkVolatileField(GETSTATIC, name, desc);
			return;
		}
		LockLabels ll = startLocking(GETSTATIC, seenOwner, name);
		super.visitFieldInsn(GETSTATIC, seenOwner, name, desc);
		super.visitFieldInsn(GETSTATIC, seenOwner, seenField, seenType);
		endLocking(GETSTATIC, seenOwner, ll);
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, THELPER_INAME, CHECK_SINGLE_LIN_TAINT, LIN_CHECK_SIG, false);
		if(fieldType.getSize() == 2) {
			super.visitInsn(DUP_X2);
			super.visitInsn(POP);
		} else {
			super.visitInsn(SWAP);
		}
	}
}
