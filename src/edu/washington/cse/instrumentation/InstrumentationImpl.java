package edu.washington.cse.instrumentation;

import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.SignatureAttribute;
import edu.washington.cse.instrumentation.runtime.TaintCarry;

public class InstrumentationImpl {
	private final ClassPool cp;
	private final CtClass taintedWithObjIntf;
	private final boolean isPhosphor;
		
	public InstrumentationImpl(ClassPool cp) {
		this.cp = cp;
		this.isPhosphor = true;
		try {
			this.taintedWithObjIntf = isPhosphor ? cp.get("edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag") : null;
		} catch(NotFoundException e) {
			throw new RuntimeException("How did you even get here?!?!", e);
		}
	}
	
	public void instrumentTaintFields(CtClass klass) throws NotFoundException, CannotCompileException {
		if(TaintUtils.isIgnoredClass(klass)) {
			return;
		}
		klass.addInterface(cp.get(TaintCarry.class.getName()));
		AttributeInfo ai = klass.getClassFile().getAttribute("Signature");
		if(ai != null) {
			assert ai instanceof SignatureAttribute;
			SignatureAttribute sa = (SignatureAttribute)ai;
			String sig = sa.getSignature();
			sig += "L" + TaintCarry.class.getName().replace('.', '/') + ";";
			sa.setSignature(sig);
		}
		if(klass.subtypeOf(taintedWithObjIntf) && isPhosphor) {
			System.out.println("Reusing existing Phosphor taint for: " + klass.getName());
			klass.addMethod(CtMethod.make(
					"public void _staccato_set_taint(java.lang.Object new_taint) {" +
						"if(new_taint == null) {" +
							"this.setPHOSPHOR_TAG(null);" +
						"} else { " +
							"this.setPHOSPHOR_TAG(new edu.columbia.cs.psl.phosphor.runtime.Taint(new_taint));" +
						"} " +
					"}", klass));
			klass.addMethod(CtMethod.make(
				"public java.lang.Object _staccato_get_taint() {" +
					"edu.columbia.cs.psl.phosphor.runtime.Taint tmp = (edu.columbia.cs.psl.phosphor.runtime.Taint)this.getPHOSPHOR_TAG();" +
					"if(tmp == null) {" +
						"return null;" +
					"} else {" +
						"return tmp.lbl;" +
					"}" +
				"}", klass));
			return;
		}
		if(StaccatoConfig.STACCATO_VERBOSE) {
			System.out.println("Instrumenting taint field for " + klass.getName());
		}
		CtField parentField = CtField.make("public volatile java.lang.Object _jtaint_parent_map = null;", klass);
		klass.addField(parentField);
		klass.addMethod(CtMethod.make(
			"public void _staccato_set_taint(java.lang.Object new_taint) {" +
				"this._jtaint_parent_map = new_taint;" +
			"}",
			klass));
		klass.addMethod(CtMethod.make("public java.lang.Object _staccato_get_taint() { return this._jtaint_parent_map; }", klass));
	}
	
	public void instrumentLocks(CtClass klass, Set<String> fields) throws NotFoundException, CannotCompileException {
		boolean needStaticLock = false;
		boolean needInstanceLock = false;
		for(String f : fields) {
			if((klass.getDeclaredField(f).getModifiers() & Modifier.STATIC) == 0) {
				needInstanceLock = true;
			} else {
				needStaticLock = true;
			}
		}
		if(needStaticLock) {
			klass.addField(CtField.make("private static final java.util.concurrent.locks.ReentrantReadWriteLock " + TaintUtils.STATIC_FIELD_LOCK_NAME + ";", klass), 
				CtField.Initializer.byExpr("new java.util.concurrent.locks.ReentrantReadWriteLock()"));
		}
		if(needInstanceLock) {
			klass.addField(CtField.make("private final java.util.concurrent.locks.ReentrantReadWriteLock " + TaintUtils.FIELD_LOCK_NAME + ";", klass), 
				CtField.Initializer.byExpr("new java.util.concurrent.locks.ReentrantReadWriteLock()"));
		}
	}
}
