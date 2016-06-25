package edu.washington.cse.instrumentation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.commons.TryCatchBlockSorter;
import org.objectweb.asm.tree.AnnotationNode;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.runtime.TaintSentinel;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.washington.cse.instrumentation.asm.TaintArgVisitor;
import edu.washington.cse.instrumentation.runtime.TaintPropagation;

public class InstrumentContainers implements Opcodes {
	private static byte[] rewriteContainerClass(InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		ClassWriter cw = new ClassWriter(cr, 0);
		ClassVisitor cv = new ClassVisitor(ASM5, cw) {
			Map<String, List<AnnotationNode>> annotations = new HashMap<>();
			Map<String, List<String>> toOverride = new HashMap<>();
			Set<String> seenMethods = new HashSet<>();
			private Type wrappedType;
			private String name;
			
			@Override
			public void visit(int version, int access, String name,
					String signature, String superName, String[] interfaces) {
				super.visit(version, access, name, signature, superName, interfaces);
				this.name = name;
				this.wrappedType = Type.getObjectType(interfaces[0]);
				{
					ArrayList<String> s = new ArrayList<>();
					s.add("(Ljava/lang/Object;Ledu/columbia/cs/psl/phosphor/struct/TaintedBooleanWithObjTag;)Ledu/columbia/cs/psl/phosphor/struct/TaintedBooleanWithObjTag;");
					this.toOverride.put("equals:(Ljava/lang/Object;)Z", s);
					this.annotations.put("equals:(Ljava/lang/Object;)Z", new ArrayList<AnnotationNode>());
				}
			}
			
			@Override
			public MethodVisitor visitMethod(final int access, final String name,
					final String desc, final String signature, final String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				String origMethod = name + ":" + desc;
				if(name.equals("<clinit>")) {
					return mv;
				}
				seenMethods.add(origMethod);
				if(name.contains("$$PHOSPHOR")) {
					return mv;
				}
				boolean isCt = name.equals("<init>");
				
				final List<String> overrideMethods = new ArrayList<>();
				{
						StaccatoConfig.CONTROL_TAINT = false;
						String noControlDesc = isCt ? TaintUtils.transformPhosphorCt(desc) : TaintUtils.transformPhosphorMeth(desc);
						if(!noControlDesc.equals(desc)) {
							overrideMethods.add(noControlDesc);
						}
						StaccatoConfig.CONTROL_TAINT = true;
						String controlDesc = isCt ? TaintUtils.transformPhosphorCt(desc) : TaintUtils.transformPhosphorMeth(desc);
						overrideMethods.add(controlDesc);
				}
				toOverride.put(origMethod, overrideMethods);
				final List<AnnotationNode> seenAnnotations = new ArrayList<>();
				annotations.put(origMethod, seenAnnotations);
				return new MethodVisitor(ASM5, mv) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc,
							boolean visible) {
						final AnnotationVisitor wrappedAV = super.visitAnnotation(desc, visible);
						return new AnnotationNode(ASM5, desc) {
							@Override
							public void visitEnd() {
								seenAnnotations.add(this);
								this.accept(wrappedAV);
							};
						};
					}
				};
			}
			
			@Override
			public void visitEnd() {
				for(Map.Entry<String, List<String>> kv : toOverride.entrySet()) {
					String[] nameValue = kv.getKey().split(":");
					String origName = nameValue[0];
					String origDesc = nameValue[1];
					String taggedName = origName.equals("<init>") ? origName : origName + "$$PHOSPHORTAGGED";
					List<AnnotationNode> annot = annotations.get(kv.getKey());
					for(String newDesc : kv.getValue()) {
						String instKey = origName + ":" + newDesc;
						if(seenMethods.contains(instKey)) {
							continue;
						}
						// hold onto your butts;
						MethodVisitor mv = super.visitMethod(ACC_PUBLIC, taggedName, newDesc, null, null);
						for(AnnotationNode an : annot) {
							AnnotationVisitor av = mv.visitAnnotation(an.desc, true);
							an.accept(av);
						}
						mv.visitCode();
						int varIdx;
						int stackSize;
						if(origName.equals("<init>")) {
							mv.visitVarInsn(ALOAD, 0);
							varIdx = 1;
							stackSize = 1;
							for(Type t : Type.getArgumentTypes(newDesc)) {
//								System.out.println(t.getDescriptor());
								if(t.getSort() == Type.OBJECT && 
									(t.getInternalName().equals(Type.getInternalName(Taint.class)) ||
									 t.getInternalName().equals(Type.getInternalName(ControlTaintTagStack.class)) ||
									 t.getInternalName().startsWith("edu/columbia/cs/psl/phosphor/struct/Tainted") ||
									 t.getInternalName().equals(Type.getInternalName(TaintSentinel.class)))
								 ) {
									varIdx++;
									continue;
								}
								mv.visitVarInsn(t.getOpcode(ILOAD), varIdx);
								varIdx+=t.getSize();
								stackSize+=t.getSize();
							}
							mv.visitMethodInsn(INVOKESPECIAL, this.name, origName, origDesc, false);
							mv.visitInsn(RETURN);
						} else {
							mv.visitVarInsn(ALOAD, 0);
							mv.visitFieldInsn(GETFIELD, this.name, "wrapped", wrappedType.getDescriptor());
							varIdx = 1;
							stackSize = 1;
							for(Type t: Type.getArgumentTypes(newDesc)) {
								mv.visitVarInsn(t.getOpcode(ILOAD), varIdx);
								varIdx += t.getSize();
								stackSize += t.getSize();
							}
							mv.visitMethodInsn(INVOKEINTERFACE, this.wrappedType.getInternalName(), taggedName, newDesc, true);
							mv.visitInsn(Type.getReturnType(newDesc).getOpcode(IRETURN));
						}
						mv.visitMaxs(stackSize, varIdx);
						mv.visitEnd();
					}
				}
				super.visitEnd();
			}
		};
		cr.accept(cv, 0);
		return cw.toByteArray();
	}
	
	public static void main(String[] args) throws IOException {
		byte[] buffer = new byte[1024 * 1024 * 10];
		try(
			JarFile jf = new JarFile(args[0]);
			JarOutputStream jos = new JarOutputStream(new FileOutputStream(args[1]));
		) {
			Enumeration<JarEntry> jarEntries = jf.entries();
			while(jarEntries.hasMoreElements()) {
				JarEntry je = jarEntries.nextElement();
				if(je.isDirectory() || !je.getName().endsWith(".class") || 
					(!je.getName().startsWith("edu/washington/cse/instrumentation/runtime/containers/") &&
					 !je.getName().equals("edu/washington/cse/instrumentation/runtime/TaintHelper.class") &&
					 !je.getName().equals("edu/washington/cse/instrumentation/runtime/TaintHelper$StaccatoTaintCombiner.class") &&
					 !je.getName().equals("edu/washington/cse/instrumentation/runtime/StringIntHashMap.class") &&
					 !je.getName().equals("edu/washington/cse/instrumentation/runtime/StaccatoRuntime.class")
				  )
				) {
					jos.putNextEntry(je);
					InputStream is = jf.getInputStream(je);
					int read = 0;
					while((read = is.read(buffer)) != -1) {
						jos.write(buffer, 0, read);
					}
					jos.closeEntry();
					continue;
				}
				JarEntry newEntry = new JarEntry(je.getName());
				jos.putNextEntry(newEntry);
				InputStream is = jf.getInputStream(je);
				byte[] rewrittenClass;
				if(je.getName().startsWith("edu/washington/cse/instrumentation/runtime/containers/")) {
					rewrittenClass = rewriteContainerClass(is);
				} else if(je.getName().equals("edu/washington/cse/instrumentation/runtime/StringIntHashMap.class")) {
					rewrittenClass = rewriteHashCode(is);
				} else if(je.getName().equals("edu/washington/cse/instrumentation/runtime/StaccatoRuntime.class")) {
					rewrittenClass = rewriteRuntime(is);
				} else if(je.getName().equals("edu/washington/cse/instrumentation/runtime/TaintHelper.class")) {
					rewrittenClass = rewriteTaintHelper(is);
				} else {
					rewrittenClass = rewriteTaintCombiner(is);
				}
				jos.write(rewrittenClass);
				jos.closeEntry();
			}
		}
	}

	private static byte[] rewriteTaintCombiner(InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv = new ClassVisitor(ASM5, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc,
					String signature, String[] exceptions) {
				if((access & ACC_PUBLIC) == 0) {
					return super.visitMethod(access, name, desc, signature, exceptions);
				}
				final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				PropBlockingMV pmn = new PropBlockingMV(mv, access, name, desc, signature, exceptions);
				pmn.lvs = new LocalVariablesSorter(access, desc, pmn);
				return pmn.lvs;
			}
		};
		cr.accept(cv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	private static class PropBlockingMV extends MethodVisitor {
		private int refVar;
		private LocalVariablesSorter lvs;
		private int savedStateVar;
		Label start = new Label();
		Label end = new Label();

		public PropBlockingMV(MethodVisitor mv, int access, String name,
				String desc, String signature, String[] exceptions) {
			super(ASM5, new TryCatchBlockSorter(mv, access, name, desc, signature,
					exceptions));
		}

		private static final String BOOLREF_NAME = "edu/washington/cse/instrumentation/runtime/TaintPropagation$BoolRef";

		@Override
		public void visitCode() {
			refVar = lvs.newLocal(Type.getObjectType(BOOLREF_NAME));
			savedStateVar = lvs.newLocal(Type.getType("Z"));
			super.visitFieldInsn(GETSTATIC,
					Type.getInternalName(TaintPropagation.class), "blockPropagate",
					Type.getDescriptor(ThreadLocal.class));
			super.visitMethodInsn(INVOKEVIRTUAL,
					Type.getInternalName(ThreadLocal.class), "get",
					"()Ljava/lang/Object;", false);
			super.visitTypeInsn(CHECKCAST, BOOLREF_NAME);
			super.visitVarInsn(ASTORE, refVar);
			super.visitVarInsn(ALOAD, refVar);
			super.visitFieldInsn(GETFIELD, BOOLREF_NAME, "flag", "Z");
			super.visitVarInsn(ISTORE, savedStateVar);
			super.visitVarInsn(ALOAD, refVar);
			super.visitInsn(ICONST_1);
			super.visitFieldInsn(PUTFIELD, BOOLREF_NAME, "flag", "Z");
			super.visitTryCatchBlock(start, end, end, null);
			super.visitLabel(start);
			super.visitCode();
		}
		
		@Override
		public void visitInsn(int opcode) {
			if (opcode >= IRETURN && opcode <= RETURN) {
				super.visitVarInsn(ALOAD, refVar);
				super.visitVarInsn(ILOAD, savedStateVar);
				super.visitFieldInsn(PUTFIELD, BOOLREF_NAME, "flag", "Z");
			}
			super.visitInsn(opcode);
		}

		@Override
		public void visitMaxs(int maxStack, int maxLocals) {
			super.visitLabel(end);
			super.visitVarInsn(ALOAD, refVar);
			super.visitVarInsn(ILOAD, savedStateVar);
			super.visitFieldInsn(PUTFIELD, BOOLREF_NAME, "flag", "Z");
			super.visitInsn(ATHROW);
			super.visitMaxs(maxStack, maxLocals);
		}
	}

	private static byte[] rewriteRuntime(InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv = new ClassVisitor(ASM5, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc,
					String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				if(!name.startsWith("valueOf") && !name.startsWith("parse")) {
					return mv;
				}
				assert (access & ACC_STATIC) != 0;
				assert (Type.getReturnType(desc).getSort() == Type.OBJECT);
				Type[] argTypes = Type.getArgumentTypes(desc);
				int[] checkIdx = new int[argTypes.length];
				Arrays.fill(checkIdx, -1);
				int argIdx = 0;
				int writeIdx = 0;
				for(Type ty : argTypes) {
					if(ty.getSort() == Type.OBJECT) {
						checkIdx[writeIdx++] = argIdx;
					}
					argIdx += ty.getSize();
				}
				return new TaintArgVisitor(mv, checkIdx) {
					boolean seenReturn = false;
					@Override
					public void visitInsn(int opcode) {
						if(opcode >= IRETURN && opcode <= RETURN) {
							if(seenReturn) {
								throw new IllegalStateException();
							}
							seenReturn = true;
							this.loadTaintedArgArray();
							super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintPropagation.class), "propagateTaint", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
						}
						super.visitInsn(opcode);
					}
				};
			}
		};
		cr.accept(cv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}

	private static byte[] rewriteHashCode(InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		ClassWriter cw = new ClassWriter(cr, 0);
		ClassVisitor cv = new ClassVisitor(ASM5, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc,
					String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				return new MethodVisitor(ASM5, mv) {
					@Override
					public void visitMethodInsn(int opcode, String owner, String name,
							String desc, boolean itf) {
						if(opcode == INVOKEVIRTUAL && owner.equals("java/lang/String") && name.equals("hashCode") && desc.equals("()I")) {
							super.visitMethodInsn(opcode, owner, "hashCode$$PHOSPHORUNTAGGED", desc, itf);
						} else {
							super.visitMethodInsn(opcode, owner, name, desc, itf);
						}
					}
				};
			}
		};
		cr.accept(cv, 0);
		return cw.toByteArray();
	}

	private static byte[] rewriteTaintHelper(InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		ClassVisitor cv = new ClassVisitor(ASM5, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc,
					String signature, String[] exceptions) {
				if((access & ACC_PUBLIC) == 0) {
					return super.visitMethod(access, name, desc, signature, exceptions);
				}
				final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
				PropSkippingMV pmn = new PropSkippingMV(mv, access, name, desc, signature, exceptions);
				return pmn;
			}
		};
		cr.accept(cv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	private static class PropSkippingMV extends MethodVisitor {
		private final String desc;
		private final String name;
		
		public PropSkippingMV(MethodVisitor mv, int access, String name, String desc, String signature, String[] exceptions) {
			super(ASM5, new TryCatchBlockSorter(mv, access, name, desc, signature, exceptions));
			this.desc = desc;
			this.name = name;
		}
		
		private static final String BOOLREF_NAME = "edu/washington/cse/instrumentation/runtime/TaintPropagation$BoolRef";
		
		private static HashSet<String> CHECK_METHODS = new HashSet<>(Arrays.asList(
			"checkFieldTaint",
			"openState",
			"checkArgTaint",
			"checkkLinTaint",
			"checkLinGlobTaint",
			"checkSingleTaint",
			"checkSingleLinTaint",
			"recordCF",
			"recordRead",
			"copyString",
			"startTransaction",
			"popState"
		));
		
		@Override
		public void visitCode() {
			super.visitFieldInsn(GETSTATIC, Type.getInternalName(TaintPropagation.class), "blockPropagate", Type.getDescriptor(ThreadLocal.class));
			super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(ThreadLocal.class), "get", "()Ljava/lang/Object;", false);
			super.visitTypeInsn(CHECKCAST, BOOLREF_NAME);
			if(!name.equals("copyString")) {
				super.visitFieldInsn(GETFIELD, BOOLREF_NAME, "flag", "Z");
				Label doCheck = new Label();
				super.visitJumpInsn(IFEQ, doCheck);
				Type returnType = Type.getReturnType(desc);
				if(CHECK_METHODS.contains(name)) {
					if(returnType.getSort() == Type.VOID) {
						super.visitInsn(RETURN);
					} else if(returnType.getSort() == Type.OBJECT && 
						returnType.getInternalName().equals(Type.getInternalName(Object.class))) {
						super.visitVarInsn(ALOAD, 1);
						super.visitInsn(ARETURN);
					} else {
						Type[] argTypes = Type.getArgumentTypes(desc);
						assert argTypes[1].getDescriptor().equals(Type.getDescriptor(Taint.class)) : desc;
						super.visitTypeInsn(NEW, returnType.getInternalName());
						super.visitInsn(DUP);
						super.visitVarInsn(ALOAD, 1);
						Type primType = argTypes[2];
						super.visitVarInsn(primType.getOpcode(ILOAD), 2);
						super.visitMethodInsn(INVOKESPECIAL, returnType.getInternalName(), 
							"<init>", "(" + argTypes[1].getDescriptor() + primType.getDescriptor() +")V", false);
						super.visitInsn(ARETURN);
					}
				} else {
//					super.visitTypeInsn(NEW, Type.getInternalName(IllegalStateException.class));
//					super.visitInsn(DUP);
//					super.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(IllegalStateException.class),
//							"<init>", "()V", false);
//					super.visitInsn(DUP);
//					super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(IllegalStateException.class), "printStackTrace", "()V", false);
//					super.visitInsn(ATHROW);
					Label l1 = new Label();
					super.visitLabel(l1);
					super.visitInsn(NOP);
					super.visitJumpInsn(GOTO, l1);
				}
				super.visitLabel(doCheck);
			}
			super.visitCode();
		}
		
	}
}
