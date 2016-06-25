package edu.washington.cse.instrumentation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

import org.objectweb.asm.Type;

import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.Instrumenter;
import edu.columbia.cs.psl.phosphor.runtime.TaintSentinel;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.multid.MultiDTaintedArray;

public class TaintUtils {
	
	static {
			try {
				Class<?> confKlass = Class.forName("edu.columbia.cs.psl.phosphor.Configuration");
				{
//				Configuration.IMPLICIT_TRACKING = false;
					Field itF = confKlass.getField("IMPLICIT_TRACKING");
					itF.set(null, false);
				}
				{
//				Configuration.MULTI_TAINTING = true;
					Field mtF = confKlass.getField("MULTI_TAINTING");
					mtF.set(null, true);
				}
				{
//					Configuration.IMPLICIT_TRACKING = StaccatoConfig.CONTROL_TAINT;
					Field impTF = confKlass.getField("IMPLICIT_TRACKING");
					impTF.set(null, StaccatoConfig.CONTROL_TAINT);
				}
				{
//					 Configuration.init();
					Method m = confKlass.getMethod("init");
					m.invoke(null);
				}
			} catch (ClassNotFoundException | NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			}
	}

	
	public static boolean canTaint(CtClass klass, Set<CtClass> taintedClasses) {
		if(StaccatoConfig.IS_AUTO_TAINT) {
			return !klass.isArray() &&
				(!Instrumenter.isIgnoredClass(klass.getName().replace('.', '/')) ||
					klass.getName().equals("edu.columbia.cs.psl.phosphor.runtime.Taint"));
		}
		if(StaccatoConfig.WITH_ENUMS && klass.isEnum()) {
			return true;
		}
		try {
			for(CtClass taintedClass : taintedClasses) {
				if(klass.subtypeOf(taintedClass) || taintedClass.subtypeOf(klass)) {
					return true;
				}
			}
			return false;
		} catch (NotFoundException e) {
			return true;
		}
	}
	

	public static boolean canTaint(String klassName, ClassPool cp,
			Set<CtClass> taintedTypes) {
		try {
			CtClass k = cp.get(klassName);
			return canTaint(k, taintedTypes);
		} catch(NotFoundException e) {
			return true;
		}
	}
		
	public static int[] extractTaintArgs(CtBehavior meth, Set<CtClass> taintedClasses) {
		boolean isStatic = Modifier.isStatic(meth.getModifiers());
		int[] toRet = null;
		int writeIndex = 0;
		CtClass declaringClass = meth.getDeclaringClass();
		ClassPool cp = declaringClass.getClassPool();
		int argIndex;
		String descriptor = meth.getMethodInfo().getDescriptor();
		int argSize = Descriptor.numOfParameters(descriptor) + (isStatic ? 0 : 1); 
		if(isStatic) {
			argIndex = 0;
		} else {
			if(canTaint(declaringClass, taintedClasses)) {
				toRet = new int[argSize];
				Arrays.fill(toRet, -1);
				toRet[writeIndex++] = 0;
			}
			argIndex = 1;
		}
		Type[] ty = Type.getArgumentTypes(descriptor);
		for(Type t : ty) {
			if(t.getSort() == Type.OBJECT) {
				if(canTaint(t.getInternalName().replace('/', '.'), cp, taintedClasses)) {
					if(toRet == null) {
						toRet = new int[argSize];
						Arrays.fill(toRet, -1);
					}
					toRet[writeIndex++] = argIndex;
				}
			}
			argIndex += t.getSize();
		}
		return toRet;
	}
	
	private static Type toASMType(edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type phType) {
		return Type.getType(phType.getDescriptor());
	}
	
	private static edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type toPHType(Type aType) {
		return edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type.getType(aType.getDescriptor());
	}
	
	public static boolean isIgnoredClass(CtClass klass) {
		String className = klass.getName();
		return Instrumenter.isClassWithHashmapTag(klass.getName().replace(".", "/")) ||
				className.equals("edu.columbia.cs.psl.phosphor.runtime.Taint") ||
				className.startsWith("edu.columbia.cs.psl.phosphor.struct");
	}
		
	private static String rewriteDescriptor(String descriptor, boolean isConstructor) {
		boolean modified = false;
		List<Type> newArgs = new ArrayList<>();
		Type[] argTypes = Type.getArgumentTypes(descriptor);
		for(Type t : argTypes) {
			if(t.getSort() == Type.ARRAY) {
				if(t.getElementType().getSort() != Type.OBJECT) {
					if(t.getDimensions() == 1) {
						newArgs.add(Type.getType(Configuration.TAINT_TAG_ARRAYDESC));
						modified = true;
					} else {
						edu.columbia.cs.psl.phosphor.org.objectweb.asm.Type transformedPH = MultiDTaintedArray.getTypeForType(toPHType(t));
						newArgs.add(toASMType(transformedPH));
						modified = true;
						continue;
					}
				}
			} else if(t.getSort() != Type.OBJECT) {
				newArgs.add(Type.getType(Configuration.TAINT_TAG_DESC));
				modified = true;
			}
			newArgs.add(t);
		}
		Type origRetType = Type.getReturnType(descriptor);
		if(StaccatoConfig.CONTROL_TAINT) {
			newArgs.add(Type.getType(ControlTaintTagStack.class));
			modified = true;
		}
		if(edu.columbia.cs.psl.phosphor.TaintUtils.isPreAllocReturnType(descriptor)) {
			newArgs.add(toASMType(edu.columbia.cs.psl.phosphor.TaintUtils.getContainerReturnType(toPHType(origRetType))));
		} else if(isConstructor && modified) {
			newArgs.add(Type.getType(TaintSentinel.class));
		}
		Type retType = toASMType(edu.columbia.cs.psl.phosphor.TaintUtils.getContainerReturnType(toPHType(origRetType)));
		Type[] t = new Type[newArgs.size()];
		newArgs.toArray(t);
		return Type.getMethodDescriptor(retType, t);
	}
	
	public static String transformPhosphorCt(String desc) {
		return rewriteDescriptor(desc, true);
	}
	
	public static String transformPhosphorMeth(String methodDescriptor) {
		return rewriteDescriptor(methodDescriptor, false);
	}

	public static final String STATIC_FIELD_LOCK_NAME = "_static_field_lock";
	public static final String FIELD_LOCK_NAME = "_field_lock";
	
	static public final String[] phosphorTaintedTypes = new String[]{
		"java.lang.Integer",
		"java.lang.Byte",
		"java.lang.Character",
		"java.lang.Short",
		"java.lang.Double",
		"java.lang.Float",
		"java.lang.Boolean",
		"java.lang.Long",
		"edu.columbia.cs.psl.phosphor.runtime.Taint"
	};
	static final String CHECK_ALL_LIN = "staccato.check-all-lin";
	static final String WRAP_VOLATILE = "staccato.wrap-volatile";

	public static final int SKIP_CHECK_OPCODE = 212;
	
}
