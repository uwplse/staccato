package edu.washington.cse.instrumentation;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

public class JarFileGenerator implements Iterable<CtClass> {
	private final String jar;
	private final ClassPool cp;
	
	private Set<CtClass> jarClasses = null;
	
	public JarFileGenerator(ClassPool cp, String inputJar) {
		this.jar = inputJar;
		this.cp = cp;
	}

	@Override
	public Iterator<CtClass> iterator() {
		if(jarClasses == null) {
			try {
				this.readClasses();
			} catch (IOException | NotFoundException e) {
				throw new RuntimeException("Failed to parse jar file: " + jar, e);
			}
		}
		return jarClasses.iterator();
	}

	private void readClasses() throws IOException, NotFoundException {
		jarClasses = new HashSet<>();
		try(JarFile jf = new JarFile(new File(jar))) {
			Enumeration<JarEntry> entries = jf.entries();
			while(entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if(!entry.getName().endsWith(".class")) {
					continue;
				}
				if(entry.isDirectory()) {
					continue;
				}
				String name = entry.getName();
				name = name.replaceAll(".class$", "").replace('/', '.');
				jarClasses.add(cp.get(name));
			}
		}
	}

}
