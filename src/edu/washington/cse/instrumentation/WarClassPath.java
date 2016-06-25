package edu.washington.cse.instrumentation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javassist.ClassPath;
import javassist.NotFoundException;

public class WarClassPath implements ClassPath {
	final Set<String> rawClasses = new HashSet<String>();
	final Map<String, String> libClasses = new HashMap<>();
	final Set<String> instrumentLibClasses = new HashSet<>();
	private final JarFile jf;
	private final String expandedWar;
	public WarClassPath(String expandedWar, String war, Set<String> libJars) throws IOException {
		jf = new JarFile(new File(war));
		Enumeration<JarEntry> entries = jf.entries();
		while(entries.hasMoreElements()) {
			JarEntry je = entries.nextElement();
			if(je.getName().startsWith("WEB-INF/classes") && !je.isDirectory() && je.getName().endsWith(".class")) {
				String className = je.getName();
				className = className.replaceFirst("WEB-INF/classes/", "");
				className = className.replaceAll(".class$", "");
				className = className.replace('/', '.');
				rawClasses.add(className);
			} else if(je.getName().matches("WEB-INF/lib/[^/]+.jar$") && !je.isDirectory()) {
				if(je.getName().endsWith("staccato.jar")) {
					continue;
				}
				String jarName = je.getName().replace("WEB-INF/lib/", "");
				try(JarFile libJar = new JarFile(expandedWar + je.getName())) {
					if(libJars.contains(jarName)) {
						System.out.println("Found library jar: " + jarName);
					}
					readJarFile(je.getName(), libJar, libJars.contains(jarName));
				}
			}
		}
		this.expandedWar = expandedWar;
	}

	private void readJarFile(String name, JarFile libJar, boolean addForInstrument) throws IOException {
		Enumeration<JarEntry> entries = libJar.entries();
		while(entries.hasMoreElements()) {
			JarEntry je = entries.nextElement();
			if(!je.isDirectory() && je.getName().endsWith(".class")) {
				String className = je.getName().replace(".class", "").replace('/', '.');
				if(addForInstrument) {
					instrumentLibClasses.add(className);
				}
				libClasses.put(className, name);
			}
		}
	}

	@Override
	public InputStream openClassfile(String classname) throws NotFoundException {
		assert rawClasses.contains(classname) || libClasses.containsKey(classname);
		
		if(rawClasses.contains(classname)) {
			ZipEntry ze = jf.getEntry("WEB-INF/classes/" + classname.replace('.', '/') + ".class");
			try {
				return jf.getInputStream(ze);
			} catch (IOException e) {
				throw new NotFoundException("IO error", e);
			}
		} else {
			String jarFile = libClasses.get(classname);
			JarFile libJar = null;
			try {
				libJar = new JarFile(this.expandedWar + jarFile);
				final JarFile libJar_f = libJar;
				ZipEntry e = libJar.getEntry(classname.replace('.', '/') + ".class");
				final InputStream is = libJar.getInputStream(e);
				return new InputStream() {
					@Override
					public int read() throws IOException {
						return is.read();
					}
					
					@Override
					public int read(byte[] b, int off, int len) throws IOException {
						return is.read(b, off, len);
					}
					
					@Override
					public void close() throws IOException {
						is.close();
						libJar_f.close();
					}
				};
			} catch (IOException e) {
				if(libJar != null) {
					try {
						libJar.close();
					} catch (IOException e1) { }
				}
				throw new NotFoundException("io error", e);
			}
		}
	}

	@Override
	public URL find(String classname) {
		if(rawClasses.contains(classname)) {
			try {
				return new URL("file:/" + expandedWar + "/WEB-INF/classes" + classname.replace('.', '/'));
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return null;
			}
		} else if(libClasses.containsKey(classname)) {
			String jarName = libClasses.get(classname);
			String url = "file:/" + expandedWar + "/WEB-INF/lib/" + jarName + "!/" + classname.replace('.', '/');
			try {
				return new URI(url).toURL();
			} catch (MalformedURLException | URISyntaxException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}

	@Override
	public void close() {
		try {
			jf.close();
		} catch (IOException e) {
		}
	}

}
