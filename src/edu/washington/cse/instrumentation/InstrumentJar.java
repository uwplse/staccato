package edu.washington.cse.instrumentation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.resolution.TargetResolution;
import edu.washington.cse.instrumentation.resolution.TargetResolution.ResolutionResult;

public class InstrumentJar {
	private final ClassPool cp;

	public InstrumentJar(ClassPool cp) {
		this.cp = cp;
	}

	public static void main(String[] args) throws NotFoundException, FileNotFoundException, ClassNotFoundException, IOException, CannotCompileException {
		StaccatoConfig.dumpConfig();
		ClassPool cp = new ClassPool();
		StaccatoConfig.configureClassPath(cp);
		cp.appendClassPath(args[0]);
		cp.appendSystemPath();
		InstrumentJar jr = new InstrumentJar(cp);
		String classPattern = args.length > 3 ? args[3] : null;
		jr.instrument(args[0], args[1], args[2], classPattern);
	}

	private void instrument(String inputJar, String ruleFile, String outputJar, String classPattern) throws FileNotFoundException, ClassNotFoundException, NotFoundException, IOException, CannotCompileException {
		TargetResolution tr = new TargetResolution(true, cp, System.getProperty(TaintUtils.CHECK_ALL_LIN) != null);
		Iterable<CtClass> generator = classPattern != null ? new FilteredJarGenerator(cp, inputJar, classPattern) : new JarFileGenerator(cp, inputJar);
		ResolutionResult rr = tr.resolveActions(ruleFile, generator, System.getProperty(TaintUtils.WRAP_VOLATILE) != null);
		TaintInstrumentation ti = new TaintInstrumentation(rr, cp, true);
		Set<CtClass> instrumented = ti.instrument();
		JarWriter jw = new JarWriter();
		jw.writeJar(inputJar, instrumented, outputJar);
	}
}
