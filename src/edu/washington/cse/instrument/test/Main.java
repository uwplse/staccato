package edu.washington.cse.instrument.test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class Main {
	public static void main(String[] args) {
		CountDownLatch latch = new CountDownLatch(2);
		CountDownLatch otherLatch = new CountDownLatch(2);
		Map<String, String> m = new HashMap<>();
		TaintHelper.setNewProp("foo", "baz", m);
		TaintHelper.setNewProp("bar", "gorp", m);
		Thread r = new Thread(new Mutator(latch, m, "foo"));
		Thread q = new Thread(new Mutator(otherLatch, m, "bar"));
		Thread s = new Thread(new PropUser(latch, otherLatch, m));
		r.start();
		s.start();
		q.start();
	}
}
