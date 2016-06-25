package edu.washington.cse.instrumentation;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import edu.washington.cse.instrumentation.asm.AnalyzingClassVisitor;
import edu.washington.cse.instrumentation.asm.StaccatoClassVisitor;
import edu.washington.cse.instrumentation.resolution.CheckSpec;
import edu.washington.cse.instrumentation.resolution.InstrumentationSpec;


public class ASMShim {
	
	class StaccatoClassWriter extends ClassWriter {
		public StaccatoClassWriter(ClassReader classReader, int flags) {
			super(classReader, flags);
		}
		
		@Override
		protected String getCommonSuperClass(String type1, String type2) {
			try {
				CtClass c1 = cp.get(type1.replace('/', '.'));
				CtClass c2 = cp.get(type2.replace('/', '.'));
				if(c2.subclassOf(c1)) {
					return type1;
				}
				if(c1.subclassOf(c2)) {
					return type2;
				}
				if(c1.isInterface() || c2.isInterface()) {
					return "java/lang/Object";
				}
				while(!c2.subclassOf(c1)) {
					c1 = c1.getSuperclass();
				}
				return c1.getName().replace('.', '/');
			} catch(NotFoundException e) {
				return "java/lang/Object";
			}
		}
	}
	
	private final TaintProxy taintProxy;
	private final ClassPool cp;
	private final HashMap<String, Set<String>> lockingFields;
	public ASMShim(final ClassPool cp, Set<CtClass> taintedTypes, Map<CtClass, Set<String>> lockingFields) {
		this.taintProxy = new TaintProxy(cp, taintedTypes);
		this.lockingFields = new HashMap<>();
		this.cp = cp;
		for (Map.Entry<CtClass, Set<String>> kv : lockingFields.entrySet()) {
			this.lockingFields.put(
				kv.getKey().getName().replace('.', '/'),
				kv.getValue());
		}
	}
	
	public void analyzeClass(CtClass key, Collection<CheckSpec> list,
			CheckActionRecorder car) throws IOException, CannotCompileException {
		ClassReader cr = new ClassReader(key.toBytecode());
		AnalyzingClassVisitor acv = new AnalyzingClassVisitor(taintProxy, list, car);
		cr.accept(acv, 0);
	}

	public byte[] rewriteClass(CtClass toRewrite, InstrumentationSpec ips) throws IOException, CannotCompileException {
		ClassReader cr = new ClassReader(toRewrite.toBytecode());
		short version = cr.readShort(6);
		int flags;
		if(version <= 50) {
			flags = ClassWriter.COMPUTE_MAXS;
		} else {
			flags = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
		}
		ClassWriter cw = new StaccatoClassWriter(cr, flags);
		ClassVisitor cv = new StaccatoClassVisitor(cw, taintProxy, lockingFields, ips);
		cr.accept(cv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
}
