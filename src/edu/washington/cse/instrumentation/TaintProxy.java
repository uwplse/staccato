package edu.washington.cse.instrumentation;

import java.util.Set;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import org.objectweb.asm.Type;

public class TaintProxy {
	private final ClassPool cp;
	private final Set<CtClass> taintedTypes;

	public TaintProxy(ClassPool cp, Set<CtClass> taintedTypes) {
		this.cp = cp;
		this.taintedTypes = taintedTypes;
	}
	
	public boolean isTypeTainted(Type t) {
		if(t.getSort() != Type.OBJECT) {
			return false;
		}
		try {
			CtClass klass = typeToClass(t);
			return TaintUtils.canTaint(klass, taintedTypes);
		} catch(NotFoundException e) {
			return false;
		}
	}
	
	public boolean subtypeOf(Type t, Class<?> klass, boolean assumeNo) {
		try {
			CtClass s = typeToClass(t);
			CtClass other = cp.get(klass.getName());
			return s.subtypeOf(other);
		} catch (NotFoundException e) {
			if(assumeNo) {
				return false;
			}
			throw new RuntimeException("failed subtype check for " + t + " and " + klass, e);
		}		
	}
	
	private CtClass typeToClass(Type t) throws NotFoundException {
		assert t.getSort() == Type.OBJECT;
		return cp.get(t.getClassName().replace('/','.'));
	}

	public boolean isEnum(Type type) {
		try {
			return typeToClass(type).isEnum();
		} catch (NotFoundException e) {
			throw new RuntimeException("I really hope I don't see this", e);
		}
	}
}
