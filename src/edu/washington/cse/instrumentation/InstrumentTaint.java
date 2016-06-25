package edu.washington.cse.instrumentation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.resolution.TargetResolution;
import edu.washington.cse.instrumentation.resolution.TargetResolution.ResolutionResult;

public class InstrumentTaint {
	private final ClassPool cp;
	private InstrumentTaint(ClassPool cp) {
		this.cp = cp;
	}

	@SuppressWarnings("unused")
	private void dumpSet(String message, Collection<CtClass> set) {
		System.out.println(message + ":");
		for(CtClass klass : set) {
			System.out.println(klass.getName());
		}
	}
	
	private void instrumentTaint(String specFile, String outputDir, Iterable<CtClass> classGenerator) throws NotFoundException, 
		CannotCompileException, FileNotFoundException, IOException, ClassNotFoundException {
		TargetResolution resolver = new TargetResolution(true, cp);
		ResolutionResult resolvedTargets = resolver.resolveActions(specFile, classGenerator, false);
		TaintInstrumentation inst = new TaintInstrumentation(resolvedTargets, cp, true);
		Set<CtClass> instrumentedClasses = inst.instrument();
		for(CtClass instrumentedClass : instrumentedClasses) {
			if(TaintUtils.isIgnoredClass(instrumentedClass)) {
				continue;
			}
			System.out.println("writing: " + instrumentedClass.getName());
			instrumentedClass.writeFile(outputDir);
		}
	}

	public static void main(String[] args) throws FileNotFoundException, IOException, NotFoundException, CannotCompileException, ClassNotFoundException {
		StaccatoConfig.dumpConfig();
		ClassPool cp = new ClassPool();
		Iterable<CtClass> generator;
		if(args[2].equals("--rt")) {
			cp.appendClassPath(args[3] + "/*");
			generator = null;
			cp.appendClassPath(System.getProperty("staccato.phosphor-jar"));
			cp.appendClassPath(System.getProperty("staccato.runtime-jar"));
		} else {
//			throw new IllegalArgumentException();
			cp.appendSystemPath();
			generator = new ClassFileGenerator(cp, Arrays.asList(args).subList(2, args.length));
		}
		InstrumentTaint it = new InstrumentTaint(cp);
		it.instrumentTaint(args[0], args[1], generator);
	}
}
