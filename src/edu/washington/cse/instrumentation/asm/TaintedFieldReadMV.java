package edu.washington.cse.instrumentation.asm;

import java.util.ArrayList;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;

import edu.columbia.cs.psl.phosphor.TaintUtils;

enum FieldReadState {
	INSTANCE_LOAD,
	STATIC_LOAD
}

/*
 * A class that handles detecting primitive value reads, and properly unwrapping primitive volatile fields.
 * 
 * By itself it can be used to just handle volatile fields
 */
public class TaintedFieldReadMV extends FieldMatchingMV<FieldReadState> implements Opcodes {
	protected final Set<String> volatileFields;
	protected final String className;
	
	protected String seenOwner, seenField, seenType;
	
	public TaintedFieldReadMV(MethodVisitor mv, String className, Set<String> volatileFields) {
		super(mv,
			new MatchPattern<>(FieldReadState.INSTANCE_LOAD,
				new InsnNode(DUP),
				new AbstractFieldInsn(GETFIELD),
				new InsnNode(SWAP),
				new AbstractFieldInsn(GETFIELD)
			),
			new MatchPattern<>(FieldReadState.STATIC_LOAD,
				new AbstractFieldInsn(GETSTATIC),
				new AbstractFieldInsn(GETSTATIC)
			)
		);
		this.className = className;
		this.volatileFields = volatileFields;
	}

	@Override
	protected void doFieldOp(int opcode, String owner, String name, String desc) {
		Type t = Type.getType(desc);
		boolean isUnboxedVolatileRead = 
			(t.getSort() != Type.OBJECT && (t.getSort() != Type.ARRAY || t.getElementType().getSort() != Type.OBJECT)) &&
			owner.equals(className) && volatileFields != null && volatileFields.contains(name);
		if(opcode == PUTSTATIC || opcode == PUTFIELD) {
			super.doFieldOp(opcode, owner, name, desc);
			return;
		} else if(opcode == GETFIELD) {
			if(isUnboxedVolatileRead) {
				String containerTypeDesc = TaintUtils.getContainerReturnType(edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type.getType(desc)).getDescriptor();
				Type cType = Type.getType(containerTypeDesc);
				super.doFieldOp(GETFIELD, owner, name, cType.getDescriptor());
				super.doFieldOp(GETFIELD, cType.getInternalName(), "val", desc);
			} else {
				this.addFieldLoad(owner, name, desc);
			}
		} else {
			assert opcode == GETSTATIC;
			if(isUnboxedVolatileRead) {
				String containerTypeDesc = TaintUtils.getContainerReturnType(edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type.getType(desc)).getDescriptor();
				Type cType = Type.getType(containerTypeDesc);
				super.doFieldOp(GETSTATIC, owner, name, cType.getDescriptor());
				super.doFieldOp(GETFIELD, cType.getInternalName(), "val", desc);
			}
			this.addStaticFieldLoad(owner, name, desc);
		}
	}
	
	protected void addFieldLoad(String owner, String name, String desc) {
		super.visitFieldInsn(GETFIELD, owner, name, desc);
	}
	
	protected void addStaticFieldLoad(String owner, String name, String desc) {
		super.visitFieldInsn(GETSTATIC, owner, name, desc);
	}
	
	protected void addPrimitiveStaticFieldRead(String name, String desc) {
		if(volatileFields != null && seenOwner.equals(className) && volatileFields.contains(name)) {
			this.addUnpack(GETSTATIC, name, desc);
		} else {
			super.visitFieldInsn(GETSTATIC, seenOwner, seenField, seenType);
			super.visitFieldInsn(GETSTATIC, seenOwner, name, desc);
		}
	}
	
	protected void addUnpack(int opcode, String name, String desc) {
		Type containerType = Type.getType(TaintUtils.getContainerReturnType(desc).getDescriptor());
		String taintType = TaintUtils.getShadowTaintType(desc);
		assert taintType != null;
		boolean isArray = Type.getType(desc).getSort() == Type.ARRAY;
		if(!isArray) {
			taintType = Type.getType(taintType).getInternalName();
		}
		super.visitFieldInsn(opcode, seenOwner, name, containerType.getDescriptor());
		super.visitInsn(DUP);
		super.visitFieldInsn(GETFIELD, containerType.getInternalName(), "taint", (isArray ? "[" : "") + Type.getDescriptor((Object.class)));
		super.visitTypeInsn(CHECKCAST, taintType);
		super.visitInsn(SWAP);
		super.visitFieldInsn(GETFIELD, containerType.getInternalName(), "val", desc);
	}
	
	protected void addPrimitiveFieldRead(String name, String desc) {
		if(volatileFields != null && seenOwner.equals(className) && volatileFields.contains(name)) {
			this.addUnpack(GETFIELD, name, desc);
		} else {
			super.visitInsn(DUP);
			super.visitFieldInsn(GETFIELD, seenOwner, seenField, seenType);
			super.visitInsn(SWAP);
			super.visitFieldInsn(GETFIELD, seenOwner, name, desc);
		}
	}

	@Override
	protected void handleMatch(FieldReadState currPattern, ArrayList<FieldInsnNode> savedFields) {
		seenOwner = savedFields.get(0).owner;
		seenField = savedFields.get(0).name;
		seenType = savedFields.get(0).desc;
		if(currPattern == FieldReadState.INSTANCE_LOAD) {
			this.addPrimitiveFieldRead(savedFields.get(1).name, savedFields.get(1).desc);
		} else {
			assert currPattern == FieldReadState.STATIC_LOAD;
			this.addPrimitiveStaticFieldRead(savedFields.get(1).name, savedFields.get(1).desc);
		}
	}
}
