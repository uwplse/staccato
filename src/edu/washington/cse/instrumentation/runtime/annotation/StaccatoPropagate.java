package edu.washington.cse.instrumentation.runtime.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import edu.washington.cse.instrumentation.runtime.PropagationTarget;

@Retention(RetentionPolicy.RUNTIME)
@Target(value={ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface StaccatoPropagate {
	PropagationTarget value() default PropagationTarget.RETURN;
}
