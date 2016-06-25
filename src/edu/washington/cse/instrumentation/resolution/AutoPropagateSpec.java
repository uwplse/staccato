package edu.washington.cse.instrumentation.resolution;

import java.util.regex.Pattern;

public class AutoPropagateSpec {
	public final String owner;
	public final String method;
	public final Pattern setterPattern;
	
	public AutoPropagateSpec(String setterPattern, String owner, String method) {
		this.owner = owner;
		this.method = method;
		if(setterPattern.endsWith("*")) {
			String subPattern = setterPattern.substring(0, setterPattern.length() - 1);
			this.setterPattern = Pattern.compile("^" + Pattern.quote(subPattern) + ".+$");
		} else {
			this.setterPattern = Pattern.compile("^" + Pattern.quote(setterPattern) + "$");
		}
	}
}
