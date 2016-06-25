package edu.washington.cse.instrumentation.runtime;

import java.util.Set;

public interface StaccatoFieldRepair {
	public Object __staccato_repair_field(Set<String> failingProps, String failingField, Object failingObject, RuntimeException e);
}
