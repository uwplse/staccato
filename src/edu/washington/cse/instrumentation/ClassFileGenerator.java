package edu.washington.cse.instrumentation;

import java.util.Iterator;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

public class ClassFileGenerator implements ClassGenerator {
	private final Iterable<String> klassFiles;
	private final ClassPool cp;
	public ClassFileGenerator(ClassPool cp, Iterable<String> klassFiles) {
		this.klassFiles = klassFiles;
		this.cp = cp;
	}
	private class ClassFileIterator implements Iterator<CtClass> {
		private final Iterator<String> it = klassFiles.iterator();
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public CtClass next() {
			String className = it.next();
			if(className.endsWith(".class") && className.contains("/")) {
				className = className.replaceFirst("\\.class$", "").replace("/", ".").replaceAll("^\\.*", "");
			}
			try {
				return cp.get(className);
			} catch (NotFoundException e) {
				throw new RuntimeException("couldn't find " + className);
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
		
	}
	@Override
	public Iterator<CtClass> iterator() {
		return new ClassFileIterator();
	}

}
