package edu.washington.cse.instrumentation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.resolution.TargetResolution;
import edu.washington.cse.instrumentation.resolution.TargetResolution.ResolutionResult;

public class InstrumentWar {
	private static final class WarWriter {

		private final String expandedWarPath;
		private final String warPath;

		public WarWriter(String expandedWarPath, String warPath) {
			this.expandedWarPath = expandedWarPath;
			this.warPath = warPath;
		}
		
		static private byte writeBuf[] = new byte[1024 * 100];

		public void writeWar(Map<String, CtClass> klassMap, Set<String> toRewrite, String destinationPath) throws IOException, CannotCompileException {
			try(
				ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(destinationPath)));
				ZipFile jf = new ZipFile(new File(warPath));
			) {
				copyZipFile(klassMap, jf, "WEB-INF/classes/", toRewrite, zos);
			}
		}
		
		private String getClassName(String base, String fName) {
			assert base == null || base.endsWith("/");
			if(base == null) {
				return fName.replace('/', '.').replaceFirst(".class$", "");
			}
			if(!fName.startsWith(base)) {
				return null;
			}
			return fName.replaceFirst("^" + base, "").replace('/', '.').replaceFirst(".class$", "");
		}
		
		private void copyZipFile(Map<String, CtClass> klassMap, ZipFile zf, String base, Set<String> toRewrite, ZipOutputStream zos) throws IOException, CannotCompileException {
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while(entries.hasMoreElements()) {
				ZipEntry ze = entries.nextElement();
				ZipEntry ze2 = new ZipEntry(ze.getName());
				zos.putNextEntry(ze2);
				String entryName = ze.getName();
				String className = null;
				if(!ze.isDirectory() && entryName.endsWith(".class") && ((className = getClassName(base, entryName)) != null) && klassMap.containsKey(className)) {
					System.out.println("Writing: " + className);
					CtClass toWrite = klassMap.get(className);
					toWrite.toBytecode(new DataOutputStream(zos));
				} else if(toRewrite.contains(ze.getName())) {
					writeComponentJar(klassMap, ze.getName(), zos);
				} else {
					InputStream is = zf.getInputStream(ze);
					int read;
					while((read = is.read(writeBuf)) > 0) {
						zos.write(writeBuf, 0, read);
					}
					is.close();
				}
			}
		}
		
		private void writeComponentJar(Map<String, CtClass> klassMap, String zipFile,	ZipOutputStream zos) throws IOException, CannotCompileException {
			try(
				ZipFile zf = new ZipFile(expandedWarPath + "/" + zipFile);
			) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ZipOutputStream innerJar = new ZipOutputStream(baos);
				copyZipFile(klassMap, zf, null, Collections.<String>emptySet(), innerJar);
				innerJar.close();
				zos.write(baos.toByteArray());
			}
		}
		
	}

	public static void main(String args[]) throws IOException, ClassNotFoundException, NotFoundException, CannotCompileException {
		StaccatoConfig.dumpConfig();
		InstrumentWar wi = new InstrumentWar();
		wi.instrument(args);
	}

	private void instrument(String[] args) throws ClassNotFoundException, NotFoundException, IOException, CannotCompileException {
		instrument(Arrays.asList(args));
	}
	
	private void instrument(List<String> args) throws ClassNotFoundException, NotFoundException, IOException, CannotCompileException {
		instrument(args.get(0), args.get(1), args.get(2), args.get(3), args.subList(4, args.size()));
	}

	private void instrument(String expandedWarPath, String warPath, String ruleFile, String outputPath, List<String> libJars) throws ClassNotFoundException, NotFoundException, IOException, CannotCompileException {
		ClassPool cp = new ClassPool();
		WarClassPath wcp = new WarClassPath(expandedWarPath, warPath, new HashSet<String>(libJars));
		cp.appendClassPath(wcp);
		StaccatoConfig.configureClassPath(cp);
		cp.appendClassPath(System.getProperty("servlet") + "/*");
		TargetResolution tr = new TargetResolution(true, cp, System.getProperty(TaintUtils.CHECK_ALL_LIN) != null);
		Set<String> instrumentClasses = new HashSet<>(wcp.rawClasses);
		instrumentClasses.addAll(wcp.instrumentLibClasses);
		ResolutionResult r = tr.resolveActions(ruleFile, new ClassFileGenerator(cp, instrumentClasses), System.getProperty(TaintUtils.WRAP_VOLATILE) != null);
		TaintInstrumentation ti = new TaintInstrumentation(r, cp, true);
		Set<CtClass> klasses = ti.instrument();
		Set<String> toRewrite = new HashSet<>();
		Map<String, CtClass> klassMap = new HashMap<>();
		for(CtClass klass : klasses) {
			klassMap.put(klass.getName(), klass);
			if(!wcp.libClasses.containsKey(klass.getName())) {
				continue;
			}
			toRewrite.add(wcp.libClasses.get(klass.getName()));
		}
		WarWriter ww = new WarWriter(expandedWarPath, warPath);
		ww.writeWar(klassMap, toRewrite, outputPath);
	}
}
