package edu.washington.cse.instrument.test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class Mutator implements Runnable {
	private final CountDownLatch latch;
	private final Map<String, String> m;
	private final String prop;
	public Mutator(CountDownLatch latch, Map<String, String> m, String prop) {
		this.latch = latch;
		this.m = m;
		this.prop = prop;
	}
	@Override
	public void run() {
		this.latch.countDown();
		try {
			this.latch.await();
		} catch (InterruptedException e) { }
		System.out.println("about to set prop: " + prop);
		TaintHelper.setNewProp(prop, new String("whatever"), m);
		System.out.println("Set prop: " + prop);
	}
}
