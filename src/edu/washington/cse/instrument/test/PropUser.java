package edu.washington.cse.instrument.test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import edu.washington.cse.instrumentation.runtime.CheckLevel;
import edu.washington.cse.instrumentation.runtime.TaintHelper;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoCheck;

public class PropUser implements Runnable {
	private final CountDownLatch latch;
	private final Map<String, String> m;
	private final String cached;
	private final CountDownLatch otherLatch;
	public PropUser(CountDownLatch latch, CountDownLatch otherLatch, Map<String, String> m) {
		this.latch = latch;
		this.otherLatch = otherLatch;
		this.m = m;
		this.cached = TaintHelper.getProp("bar", m);
	}
	@Override
	public void run() {
		String prop = TaintHelper.getProp("foo", m);
		this.doThing(prop);
	}
	
	@StaccatoCheck(CheckLevel.STRICT)
	private void doThing(String prop) {
		System.out.println("Using prop");
		this.latch.countDown();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) { }
		System.out.println(cached);
		this.otherLatch.countDown();
		System.out.println("The prop is " + prop);
		System.out.println("The two props are: " + prop + cached);
	}
}
