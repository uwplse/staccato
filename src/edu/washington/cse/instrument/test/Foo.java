package edu.washington.cse.instrument.test;

public class Foo {
	Whatever<String> foo;
	String rar = "rar".toUpperCase();
	public Foo(int a) {
		
	}
	public Foo(int a, int b) {
	}
}

interface Whatever<T> extends Test<T, Integer> {
	public void doThing(T whatever);
}

interface Test<U, T> {
	public void doThing(U a1, T a2);
}