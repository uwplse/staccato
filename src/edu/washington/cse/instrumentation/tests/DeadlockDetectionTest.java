package edu.washington.cse.instrumentation.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import edu.washington.cse.instrumentation.runtime.TaintHelper;

public class DeadlockDetectionTest extends StaccatoTest {
	private static volatile CountDownLatch latch1 = new CountDownLatch(1);
	private static volatile CountDownLatch latch2 = new CountDownLatch(1);
	private static Map<String, String> props = new HashMap<>();
	
	public static class ReaderThread implements Runnable {
		@Override
		public void run() {
			proxy.acquireLocks("foo");
			latch1.countDown();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
			proxy.releaseLocks();
			latch1 = new CountDownLatch(1);
			try {
				latch2.await();
			} catch (InterruptedException e) {
			}
			proxy.acquireLocks("foo");
			latch1.countDown();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			proxy.acquireLocks("bar");
			proxy.releaseLocks();
			proxy.releaseLocks();
		}
	}
	
	public static class WriterThread implements Runnable {
		@Override
		public void run() {
			Map<String, String> toSet = new HashMap<>();
			toSet.put("foo", "baz"+"");
			toSet.put("bar", "bar"+"");
			try {
				latch1.await();
			} catch (InterruptedException e) {
			}
			TaintHelper.setNewProps(toSet, props);
			latch2.countDown();
			toSet = new HashMap<>();
			toSet.put("foo", "baz" + "");
			toSet.put("bar", "baz" + "");
			try {
				latch1.await();
			} catch (InterruptedException e) {
			}
			try {
				TaintHelper.setNewProps(toSet, props);
			} catch(RuntimeException e) {
				System.out.println("exception caught! " + e);
			}
		}
		
	}
	
	public static void main(String[] args) {
		(new Thread(new ReaderThread())).start();
		(new Thread(new WriterThread())).start();
	}
}
