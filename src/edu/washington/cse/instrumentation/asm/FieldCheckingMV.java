package edu.washington.cse.instrumentation.asm;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.washington.cse.instrumentation.TaintProxy;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

/*
 * A checking implementation that checks according to the strict semantics
 */
public class FieldCheckingMV extends AbstractCheckingMV implements Opcodes {
	public FieldCheckingMV(MethodVisitor mv, String fileName, TaintProxy taintProxy, Map<String, Set<String>> lockingFields, String className, Set<String> volatileFields) {
		super(mv, fileName, taintProxy, lockingFields, className, volatileFields, "checkSingleTaint");
	}
	
	@Override
	protected void addFieldLoad(String owner, String fieldName, String fieldDesc) {
		Type t = Type.getType(fieldDesc);
		if(!taintProxy.isTypeTainted(t) || skipCheck(owner, fieldName, fieldDesc)) {
			super.visitFieldInsn(GETFIELD, owner, fieldName, fieldDesc);
			return;
		}
		this.debugFieldCheck(owner, fieldName, fieldDesc);
		// hold onto your butts
		LockLabels ll = startLocking(GETFIELD, owner, fieldName);
		// Stack: O
		super.visitInsn(DUP); // O O
		super.addFieldLoad(owner, fieldName, fieldDesc); // V O
		endLocking(GETFIELD, owner, ll);
		super.visitLdcInsn(fieldName);
		if(ll != null) {
			super.visitVarInsn(ALOAD, lockIdx);
		} else {
			super.visitInsn(ACONST_NULL);
		}
		this.addObjectTaintCheck();
		super.visitTypeInsn(CHECKCAST, t.getInternalName());
	}

	private void addObjectTaintCheck() {
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "checkFieldTaint", 
			"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;" + Type.getDescriptor(ReentrantReadWriteLock.class) + ")Ljava/lang/Object;", false);
	}

	@Override
	protected void addStaticFieldLoad(String owner, String fieldName, String fieldType) {
		Type t = Type.getType(fieldType);
		if(!taintProxy.isTypeTainted(t)) {
			super.visitFieldInsn(GETSTATIC, owner, fieldName, fieldType);
			return;
		}
		this.debugFieldCheck(owner, fieldName, fieldType);
		assert t.getSort() == Type.OBJECT;
		super.visitLdcInsn(Type.getObjectType(owner)); // Class<?>
		LockLabels ll = startLocking(GETSTATIC, owner, fieldName);
		super.addStaticFieldLoad(owner, fieldName, fieldType); // V Class<?>
		endLocking(GETSTATIC, owner, ll);
		super.visitLdcInsn(fieldName); // "name" V Class<?>
		if(ll != null) {
			super.addStaticFieldLoad(owner, edu.washington.cse.instrumentation.TaintUtils.STATIC_FIELD_LOCK_NAME, Type.getDescriptor(ReentrantReadWriteLock.class));
		} else {
			super.visitInsn(ACONST_NULL);
		}
		addObjectTaintCheck();
		super.visitTypeInsn(CHECKCAST, t.getInternalName());
	}

	private void addCheckCall(String desc) {
		// stack precondition: RWL "field name" V T O
		String checkDesc = "(Ljava/lang/Object;" + Configuration.TAINT_TAG_DESC + desc + "Ljava/lang/String;" + Type.getDescriptor(ReentrantReadWriteLock.class) +")";
		
		edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type returnContainer = TaintUtils.getContainerReturnType(desc);
		checkDesc += returnContainer.getDescriptor();
		
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), "checkFieldTaint", checkDesc, false); // W
		super.visitInsn(DUP); // W W
		super.visitFieldInsn(GETFIELD, returnContainer.getInternalName(), "taint", "Ljava/lang/Object;"); // O W
		super.visitTypeInsn(CHECKCAST, Type.getInternalName(Taint.class)); // T W
		super.visitInsn(SWAP); // W T
		super.visitFieldInsn(GETFIELD, returnContainer.getInternalName(), "val", desc); // V T
	}
	
	@Override
	protected void addPrimitiveStaticFieldRead(String name, String desc) {
		if(Type.getType(desc).getSort() == Type.ARRAY) {
			LockLabels ll = startLocking(GETSTATIC, seenOwner, name);
			super.addPrimitiveStaticFieldRead(name, desc);
			endLocking(GETSTATIC, seenOwner, ll);
			return;
		}
		// Nothing on the stack
		this.debugFieldCheck(seenOwner, name, desc);
		super.visitLdcInsn(Type.getObjectType(seenOwner));
		LockLabels ll = startLocking(GETSTATIC, seenOwner, name);
		super.addPrimitiveStaticFieldRead(name, desc); // V T
		endLocking(GETSTATIC, seenOwner, ll);
		super.visitLdcInsn(name);
		if(ll != null) {
			super.visitFieldInsn(GETSTATIC, seenOwner, edu.washington.cse.instrumentation.TaintUtils.STATIC_FIELD_LOCK_NAME, Type.getDescriptor(ReentrantReadWriteLock.class));
		} else {
			super.visitInsn(ACONST_NULL);
		}
		this.addCheckCall(desc);
	}
	
	@Override
	protected void addPrimitiveFieldRead(String name, String desc) {
		if(Type.getType(desc).getSort() == Type.ARRAY) {
			LockLabels ll = startLocking(GETFIELD, seenOwner, name);
			super.addPrimitiveFieldRead(name, desc);
			endLocking(GETFIELD, seenOwner, ll);
			return;
		}
		this.debugFieldCheck(seenOwner, name, desc);
		LockLabels ll = startLocking(GETFIELD, seenOwner, name);
		super.visitInsn(DUP); //O O
		super.addPrimitiveFieldRead(name, desc); // V T O
		endLocking(GETFIELD, seenOwner, ll);
		super.visitLdcInsn(name); // "field name" V T O
		if(ll != null) {
			super.visitVarInsn(ALOAD, lockIdx); // RWL "field name" V T O
		} else {
			super.visitInsn(ACONST_NULL); // null "field name" V T O
		}
		this.addCheckCall(desc);
	}
}
