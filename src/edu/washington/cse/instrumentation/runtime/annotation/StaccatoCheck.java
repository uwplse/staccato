package edu.washington.cse.instrumentation.runtime.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import edu.washington.cse.instrumentation.runtime.CheckLevel;

@Retention(RetentionPolicy.RUNTIME)
@Target(value={ElementType.METHOD,ElementType.TYPE})
public @interface StaccatoCheck {
	CheckLevel value() default CheckLevel.STRICT;
	boolean argsOnly() default false;
}
