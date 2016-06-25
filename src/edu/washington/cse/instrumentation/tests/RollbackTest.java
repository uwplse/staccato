package edu.washington.cse.instrumentation.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class RollbackTest extends StaccatoTest {
	private static final Map<String, String> props = new HashMap<>();
	public static CountDownLatch latch = new CountDownLatch(2);
	
	public static class ReaderThread implements Runnable {
		private final String propName;

		public ReaderThread(String propName) {
			this.propName = propName;
		}
		
		@Override
		public void run() {
			proxy.acquireLocks(propName);
			latch.countDown();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			StringBuilder builder = new StringBuilder();
			builder.append("rar");
			builder.append("asdf");
			proxy.releaseLocks();
		}
	}
	
	public static class WriterThread implements Runnable {
		@Override
		public void run() {
			String blah = "foo" + "bar";
			try {
				latch.await();
			} catch (InterruptedException e) {
			}
			TaintHelper.setNewProp("foo", new String("whatever"), props, blah);
		}
	}
	
	public static void main(String[] args) {
		(new Thread(new ReaderThread("foo"))).start();
		(new Thread(new ReaderThread("bar"))).start();
		(new Thread(new WriterThread())).start();
	}
}
