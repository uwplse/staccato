package edu.washington.cse.instrumentation.tests;

import java.lang.reflect.Field;

import edu.washington.cse.instrumentation.runtime.TaintHelper;
import edu.washington.cse.instrumentation.runtime.TaintHelper.MockProxy;

public class StaccatoTest {
	protected static MockProxy proxy;
	static {
		Class<?> klass = TaintHelper.class;
		Field f;
		try {
			f = klass.getDeclaredField("mock");
			f.setAccessible(true);
			proxy = (MockProxy)f.get(null);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException("Oh god", e);
		}
		
	}
}
