package edu.washington.cse.instrumentation.tests;

public class MemoryTests {
	public int foo = 1;
	public volatile boolean guard = false;
	
	class Reader implements Runnable {
		@Override
		public void run() {
			int a = 0;
			/* does this get it into cache? */
			for(int i = 0; i < 10000; i++) {
				a += foo;
			}
			System.out.println(a);
			a = 0;
			while(guard) { }
			System.out.println(foo);
			for(int i = 0; i < 10000; i++) {
				a += foo;
			}
			System.out.println("the total is: " + a);
		}
	}
	class Writer implements Runnable {
		@Override
		public void run() {
			int a = 0;
			/* does this get it into cache? */
			for(int i = 0; i < 10000; i++) {
				a += foo;
			}
			System.out.println("Starting mutate");
			guard = true;
			for(int i = 0; i < 10000; i++) {
				if(i % 2 == 0) {
					foo = -1;
				} else {
					foo = 1;
				}
			}
		}
	}
	public static void main(String[] args) throws InterruptedException {
		MemoryTests t = new MemoryTests();
		Thread r = new Thread(t.new Reader());
		Thread w = new Thread(t.new Writer());
		w.start();
		r.start();
		r.join();
		w.join();
		
	}
}
