package edu.washington.cse.instrumentation.runtime;

import java.util.Set;

public interface StaccatoRepair {
	public void __staccato_repair(Set<String> failingProps, RuntimeException e);
}
