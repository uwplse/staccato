package edu.washington.cse.instrumentation.resolution;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.CtClass;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.washington.cse.instrumentation.runtime.annotation.AutoPropagate;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoLock;

public class FieldAnalyzer {
	private final boolean wrapVolatile;
	private final Map<CtClass, Set<String>> lockingFields;

	public FieldAnalyzer(boolean wrapVolatile, Map<CtClass, Set<String>> lockingFields) {
		this.wrapVolatile = wrapVolatile;
		this.lockingFields = lockingFields;
	}
	
	public boolean analyzeClass(CtClass klass, Set<String> wvfOut[], AutoPropagateSpec[] apsOut) {
		byte[] b;
		try {
			b = klass.toBytecode();
		} catch (CannotCompileException | IOException e) {
			throw new RuntimeException("Failed to get bytecode for class: " + klass.getName(), e);
		}
		klass.defrost();
		ClassReader cr = new ClassReader(b);
		final Set<String> fieldsToWrap = new HashSet<>();
		final Set<String> lockingFields = new HashSet<>();
		ClassVisitor cv = null;
		if(this.wrapVolatile) {
			cv = new ClassVisitor(Opcodes.ASM5, cv) {
				@Override
				public FieldVisitor visitField(int access, String name, String desc,
						String signature, Object value) {
					if(name.endsWith(edu.columbia.cs.psl.phosphor.TaintUtils.TAINT_FIELD)) {
						return null;
					}
					if((access & Opcodes.ACC_VOLATILE) == 0) {
						return null;
					}
					if((access & Opcodes.ACC_PRIVATE) == 0) {
						System.out.println("WARNING WARNING WARNING: non-private volatile field detected!!!");
					}
					Type fieldType = Type.getType(desc);
					if(fieldType.getSort() == Type.OBJECT ||
						(fieldType.getSort() == Type.ARRAY && (fieldType.getDimensions() > 1 || fieldType.getElementType().getSize() == Type.OBJECT))) {
							return null;
					}
					fieldsToWrap.add(name);
					return null;
				}
			};
		}
		final String[] args = new String[3];
		cv = new ClassVisitor(Opcodes.ASM5, cv) {
			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if(!desc.equals(Type.getDescriptor(AutoPropagate.class))) {
					return null;
				}
				return new AnnotationVisitor(Opcodes.ASM5) {
					@Override
					public void visit(String name, Object value) {
						if(name.equals("setterMethod")) {
							assert value instanceof String;
							args[2] = (String)value;
						} else if(name.equals("setterOwner")) {
							assert value instanceof Type;
							args[1] = ((Type)value).getInternalName();
						} else if(name.equals("pattern")) {
							assert value instanceof String;
							args[0] = (String)value;
						} else {
							throw new IllegalArgumentException();
						}
					}
				};
			}
			
			@Override
			public FieldVisitor visitField(final int access, final String name, String desc,
					String signature, Object value) {
				super.visitField(access, name, desc, signature, value);
				return new FieldVisitor(Opcodes.ASM5) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if(desc.equals(Type.getDescriptor(StaccatoLock.class))) {
							if((access & Opcodes.ACC_PRIVATE) == 0) {
									throw new RuntimeException("Locking annotations cannot be applied to non-private fields");
							}
							lockingFields.add(name);
						}
						return null;
					}
				};
			}
		};
		boolean inst = false;
		cr.accept(cv, ClassReader.SKIP_CODE);
		
		if(fieldsToWrap.size() > 0) {
			wvfOut[0] = fieldsToWrap;
			inst = true;
		}
		if(args[0] != null) {
			apsOut[0] = new AutoPropagateSpec(args[0], args[1], args[2]);
			inst = true;
		}
		if(lockingFields.size() > 0) {
			this.lockingFields.put(klass, lockingFields);
			inst = true;
		}
		return inst;
	}
}
