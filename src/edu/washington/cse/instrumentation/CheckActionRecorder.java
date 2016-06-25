package edu.washington.cse.instrumentation;

public interface CheckActionRecorder {
	public void addMethod(String owner, String name, String desc);
}
