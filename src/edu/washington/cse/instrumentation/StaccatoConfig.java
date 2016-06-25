package edu.washington.cse.instrumentation;

import javassist.ClassPool;
import javassist.NotFoundException;

public class StaccatoConfig {
	public static boolean IS_AUTO_TAINT = System.getProperty("staccato.auto-taint") != null;
	public static boolean CONTROL_TAINT = false;
	public static boolean WEAK_CHECKING = System.getProperty("staccato.weak-checking") != null;
	public static boolean CHECK_METHOD_LINEAR = System.getProperty("staccato.method-linear") != null;
	public static boolean WITH_ENUMS = Boolean.parseBoolean(System.getProperty("staccato.check-enum", "false"));
	
	public static boolean STACCATO_VERBOSE = System.getProperty("staccato.verbose") != null;
	public static boolean TAG_CF_OPS = Boolean.parseBoolean(System.getProperty("staccato.tag-cf", "false"));
	
	public static void configureClassPath(ClassPool cp) throws NotFoundException {
		String tmp;
		if((tmp = System.getProperty("staccato.runtime-jar")) != null) {
			cp.appendClassPath(tmp);
		}
		if((tmp = System.getProperty("staccato.phosphor-jar")) != null) {
			cp.appendClassPath(tmp);
		}
		if((tmp = System.getProperty("staccato.jvm")) != null) {
			cp.appendClassPath(tmp);
		}
	}
	
	public static void dumpConfig() {
		System.out.println("Auto taint: " + IS_AUTO_TAINT);
		System.out.println("Control taint: " + CONTROL_TAINT);
		System.out.println("Weak checking: " + WEAK_CHECKING);
		System.out.println("Check linear: " + CHECK_METHOD_LINEAR);
		System.out.println("Tag cf: " + TAG_CF_OPS);
		System.out.println("Check Enums: " + WITH_ENUMS);
		System.out.println("Application class: " + System.getProperty("staccato.app-classes"));
//		System.out.println("Application class: " + System.getProperty("staccato.app-classes"));
		System.out.println("JVM path: " + System.getProperty("staccato.jvm"));
	}
}
