package edu.washington.cse.instrument.test;

@SuppressWarnings("unused")
public class AnalysisTest {
	private String baz;
	
	private static class FieldHolder2 {
		public String c;
	}
	
	private static class FieldHolder {
		public String b;
		public FieldHolder2 f2 = new FieldHolder2();
		public void setB(String newB) {
			this.b = newB;
		}
	}
	
	private FieldHolder bar = new FieldHolder();

	public void foo(String bar, String baz) {
		
	}
	
	public void setBaz(String foo) {
		this.baz = foo;
	}
	
	public void setBar(FieldHolder bar) {
		this.bar = bar;
	}
	
	public void doFoo() {
		foo(this.baz, "ASDFADFADSF");
	}

	private void doBar() {
		foo(this.bar.b, "asdf");
	}
	
	public void baz(int a) {
		if(a == 0) {
			foo("bar", "baz");
		} else {
			String bar = "blah";
			foo(bar, "bals'z");
		}
	}
		
	public static void rar(AnalysisTest t, String prop) {
		AnalysisTest a = t;
		a.setBaz(prop);
	}
	
	public void thing(String blah) {
		int a = 40;
		String[] f = new String[1];
		f[0] = blah;
		if(a + 1 == 41) {
			foo(blah, "Adsfadsf");
		} else {
			foo(f[0], "Adfadfadfadsfadsfadsf");
		}
	}
	
	public void doThing() {
		int a = 0;
		if(a == 0) {
			String baz = getTheString("baz");
			foo(baz, "the gorp");
		}
	}
	
	public void fribfrob(String a) {
		int b = a.indexOf("a");
	}
	
	private String getTheString(String thing) {
		return thing;
	}

	public static void main(String[] args) {
		AnalysisTest t = new AnalysisTest();
		AnalysisTest u = new AnalysisTest();
		t.baz(4);
		t.thing("foobar");
		t.doThing();
		t.fribfrob("adsfadfadf");
		FieldHolder c = new FieldHolder();
		FieldHolder d = new FieldHolder();
		c.setB("asdfasdfasdf");
		d.setB("no");
		u.setBar(d);
		if(args.length == 1) {
			c = d;
		}
		rar(t, "asdf");
		rar(t, "prop2");
		rar(u, "wrong");
		t.doFoo();
		t.setBar(c);
		setField(t.bar.f2, "fizz");
		setField(u.bar.f2, "wrong2");
		t.bar.f2.c = "fizz";
		t.foo(t.bar.f2.c, "");
		t.doBar();
		doThing(new Object(), new Object());
		Object f = new Object();
		doThing(f, f);
	}
	
	private static void doThing(Object a, Object b) {
		
	}

	private static void setField(FieldHolder2 f2, String f) {
		f2.c = f;
	}
}
