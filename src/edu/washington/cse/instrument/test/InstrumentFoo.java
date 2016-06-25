
package edu.washington.cse.instrument.test;

import java.io.IOException;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;

public class InstrumentFoo {
	public static String f = "Whatever";
	public static void main(String[] args) throws NotFoundException, CannotCompileException, IOException, ClassNotFoundException, BadBytecode, NoSuchFieldException, SecurityException {
		ClassPool cp = ClassPool.getDefault();
		cp.get("java.lang.String");
		System.out.println(f.intern() == f);
		System.out.println(System.identityHashCode(null));
	}
}
