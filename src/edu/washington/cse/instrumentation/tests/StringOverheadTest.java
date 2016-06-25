package edu.washington.cse.instrumentation.tests;

import java.util.Random;

public class StringOverheadTest { 
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		long start = System.currentTimeMillis();
		Random r = new Random();
		for(int i = 0; i < 1000000; i++) {
			boolean foo = r.nextBoolean();
			String s = foo ? "gorp" : "qux";
			String res = "foo" + s + "baz";
			
		}
		long end = System.currentTimeMillis();
		System.out.println(end - start);
	}
}
