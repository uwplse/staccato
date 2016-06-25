package edu.washington.cse.instrumentation.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadLockOverheadTest {
	private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private AtomicLong l = new AtomicLong();
	private CountDownLatch cdl = new CountDownLatch(1);
	private int dummy = 0;
	private void runTest() throws InterruptedException {
		Thread[] threads = new Thread[100];
		for(int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(new TestThread());
		}
		for(int i = 0; i < threads.length; i++) {
			threads[i].start();
		}
		cdl.countDown();
		for(int i = 0; i < threads.length; i++) {
			threads[i].join();
		}
		System.out.println("Dummy = " + dummy);
		System.out.println(l.doubleValue());
	}
	private class TestThread implements Runnable {
		public void run() {
			try {
				cdl.await();
			} catch (InterruptedException e) {
			}
			long start = System.currentTimeMillis();
			for(int i = 0; i < 100000; i++) {
//				rwl.readLock().lock();
				for(int j = 0; j < 100; j++) {
					if(i + j % 2 == 0) {
						dummy++;
					} else {
						dummy--;
					}
				}
//				rwl.readLock().unlock();
			}
			long end = System.currentTimeMillis();
			l.getAndAdd(end - start);
		}
	}
	public static void main(String... args) throws InterruptedException {
		for(int i = 0; i < 10; i++) {
			(new ReadLockOverheadTest()).runTest();
		}
		System.out.println("Start test!");
		(new ReadLockOverheadTest()).runTest();
	}
}
