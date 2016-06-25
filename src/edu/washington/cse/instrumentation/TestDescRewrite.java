package edu.washington.cse.instrumentation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TestDescRewrite {
	private static final Set<String> ignoreMethods = new HashSet<String>() {
		private static final long serialVersionUID = -7489880889072161390L;
		{
			add("equals$$PHOSPHORTAGGED");
			add("hashode$$PHOSPHORTAGGED");
			add("toString$$PHOSPHORTAGGED");
			add("getClass$$PHOSPHORTAGGED");
		}
	};
	
	public static void main(String[] args) throws IOException {
		for(String s : args) {
			processJarFile(s);
		}
	}

	private static void processJarFile(String s) throws IOException {
		try(JarFile jf = new JarFile(s)) {
			Enumeration<JarEntry> e = jf.entries();
			while(e.hasMoreElements()) {
				JarEntry entry = e.nextElement();
				if(entry.isDirectory() || !entry.getName().endsWith(".class")) {
					continue;
				}
				try(InputStream is = jf.getInputStream(entry)) {
					processClass(is);
				}
			}
		}
	}

	private static void processClass(InputStream is) throws IOException {
		ClassReader r = new ClassReader(is);
		ClassVisitor cv = new ClassVisitor(Opcodes.ASM5) {
			String className;
			
			Set<String> constructors = new HashSet<>();
			Set<String> phConstructors = new HashSet<>(); 
			
			Set<String> meth = new HashSet<>();
			Set<String> phMeth = new HashSet<>();
			
			@Override
			public void visit(int version, int access, String name, String signature,
					String superName, String[] interfaces) {
				this.className = name; 
			}
			
			@Override
			public MethodVisitor visitMethod(int access, String name, String desc,
					String signature, String[] exceptions) {
				if(name.equals("<init>")) {
					if(desc.contains("TaintSentinel")) {
						phConstructors.add(desc);
					} else {
						constructors.add(desc);
					}
					return null;
				}
				if(ignoreMethods.contains(name)) {
					return null;
				}
				if(name.contains("$$PHOSPHOR")) {
					phMeth.add(name + ":" + desc);
				} else {
					meth.add(name + ":" + desc);
				}
				return null;
			}
			
			@Override
			public void visitEnd() {
				checkMethods(className, meth, phMeth);
				checkConstructors(className, constructors, phConstructors);
			}
		};
		r.accept(cv, 0);
	}
	private static void checkMethods(String className, Set<String> meths,
			Set<String> phMeths) {
		for(String meth : meths) {
			String[] nameAndDesc = meth.split(":");
			assert nameAndDesc.length == 2;
			phMeths.remove(nameAndDesc[0] + "$$PHOSPHORTAGGED:" + TaintUtils.transformPhosphorMeth(nameAndDesc[1]));
		}
		if(!phMeths.isEmpty()) {
			System.out.println("ERROR: Did not properly transform: " + phMeths.toString() + " for " + className);
		}
	}

	private static void checkConstructors(String className,
			Set<String> constructors, Set<String> phConstructors) {
		for(String ct : constructors) {
			phConstructors.remove(TaintUtils.transformPhosphorCt(ct));
		}
		if(!phConstructors.isEmpty()) {
			System.out.println("ERROR: Did not properly transform: " + phConstructors.toString() + " for " + className);
		}
	}
}
