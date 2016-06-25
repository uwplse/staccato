package edu.washington.cse.instrumentation.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;

import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type;


enum WritePatterns {
	SIZE2_WRITE,
	STATIC_WRITE,
	SIMPLE_WRITE,
	NULL_WRITE
}

public class VolatileFieldWriteMV extends FieldMatchingMV<WritePatterns> implements Opcodes {
	private static class FieldWritePattern extends MatchPattern<WritePatterns> {
		protected final VolatileProxy vp;
		
		public FieldWritePattern(WritePatterns tag, VolatileProxy vp, AbstractInsnNode... patt) {
			super(tag, patt);
			this.vp = vp;
		}

		@Override
		public boolean filterField(int opcode, String owner, String name,
				String desc, List<FieldInsnNode> fields) {
			if(fields.size() == 0) {
				return vp.isFieldVolatile(owner, name);
			} else {
        FieldInsnNode fi = fields.get(0);
        return fi.getOpcode() == opcode &&
          fi.owner.equals(owner) &&
          name.equals(fi.name + TaintUtils.TAINT_FIELD) &&
          desc.equals(TaintUtils.getShadowTaintType(fi.desc));

			}
		}
		
	}
	
	private static class VolatileProxy {
		private final Set<String> volatileFields;
		private final String className;
		public VolatileProxy(String className, Set<String> volatileFields) {
			this.className = className;
			this.volatileFields = volatileFields;
		}
		
		boolean isFieldVolatile(String owner, String name) {
			return owner.equals(className) && volatileFields.contains(name);
		}
	}
	
	private VolatileFieldWriteMV(MethodVisitor mv, VolatileProxy vp) {
		super(mv,
				new FieldWritePattern(WritePatterns.STATIC_WRITE, vp,
						new AbstractFieldInsn(PUTSTATIC),
						new AbstractFieldInsn(PUTSTATIC)
				),
				new FieldWritePattern(WritePatterns.NULL_WRITE, vp,
					new InsnNode(POP),
					new InsnNode(DUP),
					new InsnNode(ACONST_NULL),
					new AbstractFieldInsn(PUTFIELD),
					new InsnNode(ACONST_NULL),
					new AbstractFieldInsn(PUTFIELD)
				),
				new FieldWritePattern(WritePatterns.SIMPLE_WRITE, vp,
						new InsnNode(DUP2_X1),
						new InsnNode(POP2),
						new InsnNode(DUP_X2),
						new InsnNode(SWAP),
						new AbstractFieldInsn(PUTFIELD),
						new AbstractFieldInsn(PUTFIELD)
				),
				new FieldWritePattern(WritePatterns.SIZE2_WRITE, vp, new InsnNode(DUP2_X2), 
						new InsnNode(POP2),
						new InsnNode(SWAP),
						new InsnNode(DUP_X1),
						new InsnNode(SWAP),
						new AbstractFieldInsn(PUTFIELD),
						new InsnNode(DUP_X2),
						new InsnNode(POP),
						new AbstractFieldInsn(PUTFIELD)
				) {
					@Override
					public boolean filterField(int opcode, String owner, String name, String desc, List<FieldInsnNode> fields) {
             if(fields.size() == 0) {
                 return name.endsWith(TaintUtils.TAINT_FIELD);
             } else {
               assert fields.size() == 1;
               FieldInsnNode fi = fields.get(0);
               return owner.equals(fi.owner)
                 && vp.isFieldVolatile(owner, name)
                 && fi.desc.equals(TaintUtils.getShadowTaintType(desc))
                 && fi.name.equals(name + TaintUtils.TAINT_FIELD)
                 && fi.getOpcode() == opcode;
             }
					};
				}
		);
	}
	
	public VolatileFieldWriteMV(MethodVisitor mv, String className, Set<String> volatileFields) {
		this(mv, new VolatileProxy(className, volatileFields));
	}

	
	@Override
	protected void handleMatch(WritePatterns matched, ArrayList<FieldInsnNode> fields) {
		int fieldIdx = matched == WritePatterns.SIZE2_WRITE ? 1 : 0;
		FieldInsnNode baseField = fields.get(fieldIdx);
		Type containerType = TaintUtils.getContainerReturnType(baseField.desc);
		boolean isArray = Type.getType(baseField.desc).getSort() == Type.ARRAY;
		String fieldName = baseField.name;
		String taintDesc = (isArray ? "[" : "") + "Ljava/lang/Object;";
		String initDesc = "(" + taintDesc + baseField.desc + ")V";
		switch(matched) {
		case NULL_WRITE:
			// O NULL
			super.visitInsn(POP); // O 
			super.visitTypeInsn(NEW, containerType.getInternalName()); // O C?
			super.visitInsn(DUP); // O C?
			super.visitInsn(ACONST_NULL); // O C? C? NULL
			super.visitInsn(ACONST_NULL); // O C? C? NULL NULL
			super.visitMethodInsn(INVOKESPECIAL, containerType.getInternalName(), "<init>", initDesc, false); // O C
			super.visitFieldInsn(PUTFIELD, baseField.owner, fieldName, containerType.getDescriptor()); // done
			break;
		case STATIC_WRITE:
		case SIMPLE_WRITE:
		case SIZE2_WRITE:
			if(Type.getType(baseField.desc).getSize() == 2) {
				assert matched == WritePatterns.STATIC_WRITE || matched == WritePatterns.SIZE2_WRITE;
				super.visitTypeInsn(NEW, containerType.getInternalName()); // O? T VV C
				super.visitInsn(DUP); // O? T VV C C
				super.visitInsn(DUP); // O? T VV C C C
				super.visitMethodInsn(INVOKESPECIAL, containerType.getInternalName(), "<init>", "()V", false); // O? T VV C C
				super.visitInsn(DUP2_X2); // O? T C C VV C C
				super.visitInsn(POP2); // O? T C C VV
				super.visitFieldInsn(PUTFIELD, containerType.getInternalName(), "val", baseField.desc); // O? T C
				super.visitInsn(DUP_X1); // O? C T C
				super.visitInsn(SWAP); // O? C C T
				super.visitFieldInsn(PUTFIELD, containerType.getInternalName(), "taint", taintDesc); // O? C
				if(matched == WritePatterns.STATIC_WRITE) {
					// C 
					super.visitFieldInsn(PUTSTATIC, baseField.owner, fieldName, containerType.getDescriptor()); // done
				} else {
					// O C
					super.visitFieldInsn(PUTFIELD, baseField.owner, fieldName, containerType.getDescriptor()); // done
				}
				// done
				break;
			} else {
				// O? T V
				assert matched == WritePatterns.STATIC_WRITE || matched == WritePatterns.SIMPLE_WRITE;
				super.visitTypeInsn(NEW, containerType.getInternalName()); // O? T V C
				super.visitInsn(DUP); // O? T V C C
				super.visitInsn(DUP2_X2); // O? C C T V C C
				super.visitInsn(POP2); // O? C C T V
				super.visitMethodInsn(INVOKESPECIAL, containerType.getInternalName(), "<init>", initDesc, false); // O? C
				if(matched == WritePatterns.STATIC_WRITE) {
					super.visitFieldInsn(PUTSTATIC, baseField.owner, fieldName, containerType.getDescriptor());
				} else {
					super.visitFieldInsn(PUTFIELD, baseField.owner, fieldName, containerType.getDescriptor());
				}
				break;
			}
		}
	}
}
