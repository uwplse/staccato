package edu.washington.cse.instrumentation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import edu.washington.cse.instrumentation.resolution.CheckSpec;
import edu.washington.cse.instrumentation.resolution.InstrumentationSpec;
import edu.washington.cse.instrumentation.resolution.TargetResolution.ResolutionResult;
import edu.washington.cse.instrumentation.runtime.CheckLevel;

public class TaintInstrumentation {
	private final Set<CtClass> taintedTypes;
	private final Set<CtClass> taintedClasses;
	private final Map<CtClass, InstrumentationSpec> ips;
	private final ClassPool cp;
	private final InstrumentationImpl instrumentationImpl;
	private final Map<CtClass, Set<String>> lockingFields;
	
	public TaintInstrumentation(final ResolutionResult toInstrument, ClassPool cp, boolean isPhosphor) {
		this.taintedTypes = toInstrument.taintedTypes;
		this.taintedClasses = toInstrument.taintedClasses;
		this.ips = toInstrument.instrumentation;
		this.lockingFields = toInstrument.lockingFields;
		this.instrumentationImpl = new InstrumentationImpl(cp);
		this.cp = cp;
	}
	
	public static <U> void addMethodForASM(Map<CtClass, List<U>> needsASM, CtClass declClass, U val) {
		List<U> l = needsASM.get(declClass);
		if(l == null) {
			needsASM.put(declClass, l = new ArrayList<>());
		}
		l.add(val);
	}
		
	public Set<CtClass> instrument() throws NotFoundException, CannotCompileException, IOException {
		Set<CtClass> instrumentedClasses = new HashSet<>(taintedClasses);
		instrumentedClasses.addAll(lockingFields.keySet());
		for(Map.Entry<CtClass, Set<String>> kv : lockingFields.entrySet()) {
			this.instrumentationImpl.instrumentLocks(kv.getKey(), kv.getValue());
		}
		if(!StaccatoConfig.IS_AUTO_TAINT) {
			for(CtClass taintedClass : taintedClasses) {
				this.instrumentationImpl.instrumentTaintFields(taintedClass);
			}
		}
		doASMInst(instrumentedClasses);
		return instrumentedClasses;
	}

	private void doASMInst(Set<CtClass> instrumentedClasses) throws IOException, CannotCompileException {
		ASMShim as = new ASMShim(cp, taintedTypes, lockingFields);
		
		Map<CtClass, List<CheckSpec>> toAnalyze = new HashMap<>();
		
		for(Map.Entry<CtClass, InstrumentationSpec> kv : ips.entrySet()) {
			if(kv.getValue().toCheck.size() == 0) {
				continue;
			}
			for(CheckSpec cs : kv.getValue().toCheck) {
				if(!cs.checkBody || cs.checkLevel == CheckLevel.LINEAR) {
					continue;
				}
				addMethodForASM(toAnalyze, kv.getKey(), cs);
			}
		}
		CheckActionRecorder car = new CheckActionRecorder() {
			@Override
			public void addMethod(String owner, String name, String desc) {
				try {
					CtClass ownerKlass = cp.get(owner);
					InstrumentationSpec instSpec = ips.get(ownerKlass);
					if(instSpec == null) {
						instSpec = new InstrumentationSpec(null, null, null, null);
						ips.put(ownerKlass, instSpec);
					}
					instSpec.toCheck.add(new CheckSpec(name, desc, null, CheckLevel.STRICT, true));
				} catch (NotFoundException e) {
					throw new RuntimeException("Failed to find class for access check: " + owner, e);
				}
			}
		};
		for(Map.Entry<CtClass, List<CheckSpec>> kv : toAnalyze.entrySet()) {
			as.analyzeClass(kv.getKey(), kv.getValue(), car);
		}
		Map<String, byte[]> inst = new HashMap<>();
		for(Map.Entry<CtClass, InstrumentationSpec> kv : ips.entrySet()) {
			instrumentedClasses.remove(kv.getKey());
			CtClass toInst = kv.getKey();
			byte[] newClass = as.rewriteClass(toInst, kv.getValue());
			inst.put(toInst.getName(), newClass);
		}
		for(byte[] classBuffer : inst.values()) {
			instrumentedClasses.add(cp.makeClass(new ByteArrayInputStream(classBuffer), false));
		}
	}
}
