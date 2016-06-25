package edu.washington.cse.instrumentation;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javassist.CannotCompileException;
import javassist.CtClass;

public class JarWriter {
	private final byte writeBuf[] = new byte[1024 * 100];

	public void writeJar(String inputJar, Set<CtClass> instrumented,
			String outputJar) throws FileNotFoundException, IOException, CannotCompileException {
		Map<String, CtClass> toRewrite = new HashMap<>();
		for(CtClass c : instrumented) {
			toRewrite.put(c.getName(), c);
		}
		try(
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputJar));
			JarFile jf = new JarFile(inputJar)
		) {
			Enumeration<JarEntry> entries = jf.entries();
			while(entries.hasMoreElements()) {
				JarEntry je = entries.nextElement();
				ZipEntry ze2 = new ZipEntry(je.getName());
				zos.putNextEntry(ze2);
				String entryName = je.getName();
				String className = null;
				if(!je.isDirectory() && entryName.endsWith(".class") 
					&& toRewrite.containsKey(className = entryName.replaceAll(".class$", "").replace('/', '.'))) {
					if(StaccatoConfig.STACCATO_VERBOSE) {
						System.out.println("Writing: " + className);
					}
					CtClass toWrite = toRewrite.get(className);
					toWrite.toBytecode(new DataOutputStream(zos));
				} else {
					InputStream is = jf.getInputStream(je);
					int read;
					while((read = is.read(writeBuf)) > 0) {
						zos.write(writeBuf, 0, read);
					}
					is.close();
				}
			}
		}
	}
}
