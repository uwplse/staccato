package edu.washington.cse.instrumentation;

import java.io.IOException;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

public class FixupRuntime {
	public static void main(String[] args) throws NotFoundException, IOException, CannotCompileException {
		ClassPool cp = new ClassPool();
		cp.appendClassPath(args[0]);
		cp.appendSystemPath();
		CtClass klass = cp.get("java.util.Hashtable");
		CtMethod meth = klass.getDeclaredMethod("put");
		try {
			meth.insertBefore("{ if(edu.washington.cse.instrumentation.runtime.TaintPropagation.lastError != null) { throw edu.washington.cse.instrumentation.runtime.TaintPropagation.lastError; }}");
		} catch (CannotCompileException e) {
			System.out.println(e);
		}
		klass.writeFile(args[1]);
	}
}
