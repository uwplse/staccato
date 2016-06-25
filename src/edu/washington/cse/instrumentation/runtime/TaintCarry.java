package edu.washington.cse.instrumentation.runtime;

public interface TaintCarry {
	public void _staccato_set_taint(Object taint);
	public Object _staccato_get_taint();
}
