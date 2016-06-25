package edu.washington.cse.instrumentation;

import java.util.Iterator;
import java.util.regex.Pattern;

import javassist.ClassPool;
import javassist.CtClass;

public class FilteredJarGenerator extends JarFileGenerator {

	private final Pattern classPattern;

	public FilteredJarGenerator(ClassPool cp, String inputJar, String classPattern) {
		super(cp, inputJar);
		this.classPattern = Pattern.compile(classPattern);
	}

	@Override
	public Iterator<CtClass> iterator() {
		final Iterator<CtClass> wrapped = super.iterator();
		return new Iterator<CtClass>() {
			
			private CtClass curr = null;
			
			{
				advance();
			}
			
			private void advance() {
				curr = null;
				while(wrapped.hasNext()) {
					CtClass t = wrapped.next();
					String name = t.getName().replace('.', '/');
					if(classPattern.matcher(name).matches()) {
						curr = t;
						break;
					}
				}
			}

			@Override
			public boolean hasNext() {
				return curr != null;
			}

			@Override
			public CtClass next() {
				CtClass toRet = curr;
				advance();
				return toRet;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
}
