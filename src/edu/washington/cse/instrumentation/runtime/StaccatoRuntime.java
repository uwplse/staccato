package edu.washington.cse.instrumentation.runtime;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.runtime.TaintInstrumented;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortWithObjTag;

@TaintInstrumented
public class StaccatoRuntime {
	public static void commit(Object... sources) {
		throw new Error("A call to StaccatoRuntime.commit was not replaced!");
	}
	
	public static <T> T propagateReflection(String className, Class<T> obj) throws InstantiationException, IllegalAccessException {
		T toReturn = obj.newInstance();
		TaintPropagation.propagateTaint(toReturn, className);
		return toReturn;
	}
	
	public interface ClassResolver {
		@SuppressWarnings("rawtypes")
		Class forName(String name) throws ClassNotFoundException;
	}
	
	public static <T> T propagateReflection(String className, ClassResolver resolver) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		Class<?> klass = resolver.forName(className);
		@SuppressWarnings("unchecked")
		T toReturn = (T)klass.newInstance();
		TaintPropagation.propagateTaint(toReturn, className);
		return toReturn;
	}
	
	public static <T> T propagateReflection(String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		@SuppressWarnings("unchecked")
		Class<T> klass = (Class<T>) Class.forName(className);
		T toReturn = klass.newInstance();
		TaintPropagation.propagateTaint(toReturn, className);
		return toReturn;
	}
	
	public static TaintedByteWithObjTag parseByte$$PHOSPHORTAGGED(String s, Taint t1, int radix, TaintedByteWithObjTag toRet) {
		toRet.val = Byte.parseByte(s, radix);
		toRet.taint = null;
		return (TaintedByteWithObjTag)TaintPropagation.propagateTaint(toRet, s, t1);
	}
	
	public static TaintedByteWithObjTag parseByte$$PHOSPHORTAGGED(String s, TaintedByteWithObjTag toRet) {
		toRet.val = Byte.parseByte(s);
		toRet.taint = null;
		return (TaintedByteWithObjTag)TaintPropagation.propagateTaint(toRet, s);
	}
	
	public static TaintedBooleanWithObjTag parseBoolean$$PHOSPHORTAGGED(String s, TaintedBooleanWithObjTag toRet) {
		toRet.val = Boolean.parseBoolean(s);
		toRet.taint = null;
		return (TaintedBooleanWithObjTag)TaintPropagation.propagateTaint(toRet, s);
	}
	
	public static TaintedShortWithObjTag parseShort$$PHOSPHORTAGGED(String s, TaintedShortWithObjTag toRet) {
		toRet.val = Short.parseShort(s);
		toRet.taint = null;
		return (TaintedShortWithObjTag)TaintPropagation.propagateTaint(toRet, s);	
	}
	
	public static TaintedShortWithObjTag parseShort$$PHOSPHORTAGGED(String s, Taint t1, int radix, TaintedShortWithObjTag toRet) {
		toRet.val = Short.parseShort(s, radix);
		toRet.taint = null;
		return (TaintedShortWithObjTag)TaintPropagation.propagateTaint(toRet, s, t1);
	}
	
	public static Short valueOfShort$$PHOSPHORTAGGED(String s, Taint t, int i) {
		return Short.valueOf(s, i);
	}
	
	public static Short valueOfShort(String s) {
		return Short.valueOf(s);
	}
	
	public static Boolean valueOfBoolean(String s) {
		return Boolean.valueOf(s);
	}
	
	public static Byte valueOfByte$$PHOSPHORTAGGED(String s, Taint t, int i) {
		return Byte.valueOf(s, i);
	}
	
	public static Byte valueOfByte(String s) {
		return Byte.valueOf(s);
	}
	
	/* Angels weep */
	
	public static TaintedByteWithObjTag parseByte$$PHOSPHORTAGGED(String s, Taint t1, int radix, ControlTaintTagStack taint, TaintedByteWithObjTag toRet) {
		toRet.val = Byte.parseByte(s, radix);
		toRet.taint = null;
		return (TaintedByteWithObjTag)TaintPropagation.propagateTaint(toRet, s, t1, taint.taint);
	}
	
	public static TaintedByteWithObjTag parseByte$$PHOSPHORTAGGED(String s, ControlTaintTagStack taint, TaintedByteWithObjTag toRet) {
		toRet.val = Byte.parseByte(s);
		toRet.taint = null;
		return (TaintedByteWithObjTag)TaintPropagation.propagateTaint(toRet, s, taint.taint);
	}
	
	public static TaintedBooleanWithObjTag parseBoolean$$PHOSPHORTAGGED(String s, ControlTaintTagStack taint, TaintedBooleanWithObjTag toRet) {
		toRet.val = Boolean.parseBoolean(s);
		toRet.taint = null;
		return (TaintedBooleanWithObjTag)TaintPropagation.propagateTaint(toRet, s, taint.taint);
	}
	
	public static TaintedShortWithObjTag parseShort$$PHOSPHORTAGGED(String s, ControlTaintTagStack taint, TaintedShortWithObjTag toRet) {
		toRet.val = Short.parseShort(s);
		toRet.taint = null;
		return (TaintedShortWithObjTag)TaintPropagation.propagateTaint(toRet, s, taint.taint);	
	}
	
	public static TaintedShortWithObjTag parseShort$$PHOSPHORTAGGED(String s, Taint t1, int radix, ControlTaintTagStack taint, TaintedShortWithObjTag toRet) {
		toRet.val = Short.parseShort(s, radix);
		toRet.taint = null;
		return (TaintedShortWithObjTag)TaintPropagation.propagateTaint(toRet, s, t1, taint.taint);
	}

	public static Short valueOfShort$$PHOSPHORTAGGED(String s, Taint t, int i, ControlTaintTagStack taint) {
		return Short.valueOf(s, i);
	}
	
	public static Short valueOfShort(String s, ControlTaintTagStack taint) {
		return Short.valueOf(s);
	}
	
	public static Boolean valueOfBoolean(String s, ControlTaintTagStack taint) {
		return Boolean.valueOf(s);
	}
	
	public static Byte valueOfByte$$PHOSPHORTAGGED(String s, Taint t, int i, ControlTaintTagStack taint) {
		return Byte.valueOf(s, i);
	}
	
	public static Byte valueOfByte(String s, ControlTaintTagStack taint) {
		return Byte.valueOf(s);
	}
}
