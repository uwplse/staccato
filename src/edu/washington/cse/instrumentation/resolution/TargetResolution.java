package edu.washington.cse.instrumentation.resolution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

import org.objectweb.asm.Type;

import edu.washington.cse.instrumentation.StaccatoConfig;
import edu.washington.cse.instrumentation.TaintUtils;
import edu.washington.cse.instrumentation.runtime.CheckLevel;
import edu.washington.cse.instrumentation.runtime.PropagationTarget;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoCheck;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoPropagate;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoTrack;

/*
 * How this class works:
 * 
 * 1) We parse all of the rule files and annotations found in our input set to get a set of check and propagation generators.
 *   Each generator describes, given a set of tainted types how to generate a set of specifications.
 *  1a) using the propagation specifications parsed in this step, construct a set of tainted types
 *  
 * (Note that we have to parse all propagation/check annotations before concretization so we have the full set of tainted types.
 *  In some cases we can skip instrumenting a method completely if we can prove it will never do anything because it doesn't accept
 *  types that can carry taint)
 *  
 * 2) After parsing we concretize the generators, which creates a mapping M of types to a set of specifications.
 *  Each specification uniquely identifies (within a class) a method/constructor and associated checking/propagation information
 *  
 * 3) For each input class C, for each type T' in M such that C < T' find methods defined in T' described by the specifications M(T').
 *   Add these methods (and the information contained in the specification to a map).
 * 
 */
public class TargetResolution {
	// TODO: make this configurable...
	private static final boolean TODO_MAKE_CONFIGURABLE = false;
	/* Tainted types can contain interface and classes */
	private final Set<CtClass> taintedTypes = new HashSet<>();
	private final Map<CtClass,List<PropagationGenerator>> propagationGen = new HashMap<>();
	private final Map<CtClass,List<CheckGenerator>> checkGen = new HashMap<>();
	
	private final Map<String, List<PropagationGenerator>> packageGen = new HashMap<>();

	private final Set<CtClass> excludeClasses = new HashSet<>();
	
	private final ClassPool cp;
	private final boolean isPhosphor;
	private final boolean defaultCheck;
	
	public TargetResolution(boolean isPhosphor, ClassPool cp) {
		this(isPhosphor, cp, false);
	}
	
	public TargetResolution(boolean isPhosphor, ClassPool cp, boolean defaultCheck) {
		this.isPhosphor = isPhosphor;
		this.cp = cp;
		this.defaultCheck = defaultCheck;
	}
	
	private void handleCheckDeclaration(String checkLine) throws NotFoundException {
		CheckLevel checkType = checkLine.startsWith("!!") ? CheckLevel.STRICT : CheckLevel.LINEAR;
		checkLine = checkLine.substring(checkType == CheckLevel.LINEAR ? 1 : 2);
		CheckGenerator generator;
		String klass;
		if(!checkLine.contains(":")) {
			throw new RuntimeException("Format error");
		}
		String[] methodSpec = checkLine.split(":");
		klass = methodSpec[0];
		String targetMethod = methodSpec[1];
		generator = new CheckWrapper(new NamedTaintGenerator(targetMethod, isPhosphor), checkType, false);
		this.addToMap(checkGen, cp.get(klass), generator);
	}
	

	private void handleTaintDeclaration(String line) throws NotFoundException {
		String sourceClass, destClass;
		PropagationGenerator select;
		if(line.startsWith("-")) {
			excludeClasses.add(cp.get(line.substring(1)));
			return;
		} else if(line.startsWith("+")) {
			taintedTypes.add(cp.get(line.substring(1)));
			return;
		}
		PropagationTarget target = PropagationTarget.RETURN;
		if(line.startsWith("^")) {
			target = PropagationTarget.RECEIVER;
			line = line.substring(1);
		}
		if(!line.startsWith("<")) {
			// return type selector
			sourceClass = destClass = line;
			select = new PropagateWrapper(new ReturnTypeSelector(cp.get(destClass)), target);
		} else if((line = line.substring(1, line.length() - 1)).contains(",")){
			String[] components = line.split(",");
			sourceClass = components[0];
			destClass = components[1];
			select = new PropagateWrapper(new ReturnTypeSelector(cp.get(destClass)), target);	
		} else if(line.contains(":") && line.contains("(")) {
			String[] components = line.split(":");
			sourceClass = components[0];
			int splitPos = components[1].indexOf("(");
			String methodName = components[1].substring(0, splitPos);
			String methodDescriptor = components[1].substring(splitPos);
			if(sourceClass.endsWith("." + methodName)) {
				destClass = sourceClass;
				select = new PropagateWrapper(new ConstructorDescSelector(methodDescriptor, isPhosphor), PropagationTarget.RECEIVER);
			} else {
				destClass = Descriptor.getReturnType(methodDescriptor, cp).getName();
				select = new PropagateWrapper(new MethodDescSelector(methodName, methodDescriptor, isPhosphor), target);
			}
		} else if(line.contains(":") ) {
			String[] components = line.split(":");
			sourceClass = components[0];
			String methodName = components[1];
			CtClass klass = cp.get(sourceClass);
			CtClass returnType = null;
			if(sourceClass.endsWith("." + methodName)) {
				destClass = sourceClass;
				select = new PropagateWrapper(new ConstructorSelector(), PropagationTarget.RECEIVER);
			} else {
				for(CtMethod m : klass.getDeclaredMethods()) {
					if(!m.getName().equals(methodName)) {
						continue;
					}
					CtClass r = m.getReturnType();
					if(returnType == null) {
						returnType = r;
					} else if(!returnType.equals(r)) {
						throw new IllegalArgumentException(line);
					}
				}
				destClass = returnType.getName();
				select = new PropagateWrapper(new MethodNameGenerator(methodName, isPhosphor), target);
			}
		} else {
			throw new IllegalArgumentException("Bad specification line: " + line);
		}
		CtClass dest = cp.get(destClass);
		if(!dest.isPrimitive() && !dest.isArray() && !dest.isEnum() && !destClass.equals("java.lang.Object") && !destClass.startsWith("edu.columbia.cs.psl.phosphor.struct.Tainted")) {
			this.taintedTypes.add(dest);
		}
		this.addToMap(propagationGen, cp.get(sourceClass), select);
	}
	
	private <K, T> void addToMap(Map<K,List<T>> map, K key, T value) {
		List<T> v = map.get(key);
		if(v == null) {
			v = new ArrayList<>();
		}
		v.add(value);
		map.put(key, v);
	}
	
	private void parseFile(String specFile) throws FileNotFoundException, IOException, NotFoundException {
		File inputFile = new File(specFile);
		try (
			BufferedReader reader = new BufferedReader(new FileReader(inputFile));
		) {
			String line;
			while((line = reader.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				if(line.startsWith("//")) {
					continue;
				}
				if(line.startsWith("#include")) {
					String includeFile = line.substring("#include".length()).trim();
					String includePath = inputFile.getParent() + File.separator + includeFile;
					parseFile(includePath);
				} else if(line.startsWith("!")) {
					handleCheckDeclaration(line);
				} else if(line.startsWith("@")) {
					handlePackageDeclaration(line);
				} else {
					handleTaintDeclaration(line);
				}
			}
		}
	}
	
	private void handlePackageDeclaration(String line) {
		String origLine = line;
		line = line.substring(1);
		String[] parts = line.split(":");
		if(parts.length != 2) {
			throw new IllegalArgumentException(origLine);
		}
		String pkgName = parts[0];
		if(!parts[1].endsWith("*")) {
			throw new IllegalArgumentException(origLine);
		}
		String setPattern = parts[1].substring(0, parts[1].length() - 1);
		List<PropagationGenerator> g = new ArrayList<>();
		g.add(new PropagateWrapper(new ConstructorSelector(), PropagationTarget.RECEIVER));
		g.add(new PropagateWrapper(new NamePrefixGenerator(setPattern), PropagationTarget.RECEIVER));
		if(packageGen.containsKey(pkgName)) {
			throw new IllegalArgumentException();
		}
		packageGen.put(pkgName, g);
	}

	private void collectAnnotations(Iterable<CtClass> targetClasses) throws ClassNotFoundException, NotFoundException {
		for(CtClass klass : targetClasses) {
			if(klass.hasAnnotation(StaccatoTrack.class)) {
				if(klass.isEnum()) {
					throw new IllegalArgumentException("It is not allowed to attach config to enums, found annotation on: " + klass.getName());
				}
				taintedTypes.add(klass);
			}
			collectAnnotations(Arrays.asList(klass.getNestedClasses()));
			{
				String pkgName = klass.getPackageName();
				if(packageGen.containsKey(pkgName)) {
					taintedTypes.add(klass);
					for(PropagationGenerator pg : packageGen.get(pkgName)) {
						this.addToMap(propagationGen, klass, pg);
					}
				}
			}
			if(klass.hasAnnotation(StaccatoCheck.class)) {
				StaccatoCheck checkAnnot = (StaccatoCheck)klass.getAnnotation(StaccatoCheck.class);
				if(checkAnnot.argsOnly()) {
					this.addToMap(checkGen, klass, new CheckWrapper(new TaintFilter(), checkAnnot.value(), false));
				} else {
					this.addToMap(checkGen, klass, new CheckWrapper(new AllMethods(), checkAnnot.value(), true));
				}
			} else if(defaultCheck) {
				if(System.getProperty("staccato.app-classes") != null) {
					String appClassPatt = System.getProperty("staccato.app-classes");
					if(klass.getName().replace('.', '/').matches(appClassPatt)) {
						this.addToMap(checkGen, klass, new CheckWrapper(new AllMethods(), CheckLevel.LINEAR, !StaccatoConfig.WEAK_CHECKING));
					} else {
						this.addToMap(checkGen, klass, new CheckWrapper(new TaintFilter(), CheckLevel.LINEAR, false));
					}
				} else {
					this.addToMap(checkGen, klass, new CheckWrapper(new AllMethods(), CheckLevel.LINEAR, !StaccatoConfig.WEAK_CHECKING));
				}
			}
			if(klass.hasAnnotation(StaccatoPropagate.class)) {
				StaccatoPropagate propAnnot = (StaccatoPropagate)klass.getAnnotation(StaccatoPropagate.class);
				if(propAnnot.value() != PropagationTarget.NONE) {
					throw new IllegalArgumentException("Illegal propagation annotation on type: " + klass.getName() + " expected NONE, found: " + propAnnot.value());
				}
				this.addToMap(propagationGen, klass, new PropagateWrapper(new AllBehaviors(), propAnnot.value()));
			}
			for(CtMethod meth : klass.getDeclaredMethods()) {
				if(meth.hasAnnotation(StaccatoCheck.class)) {
					StaccatoCheck checkAnnot = (StaccatoCheck)meth.getAnnotation(StaccatoCheck.class);
					CheckLevel checkLevel = checkAnnot.value();
					this.addToMap(checkGen, klass, new CheckWrapper(new MethodDescSelector(meth), checkLevel, !checkAnnot.argsOnly()));
				}
				if(meth.hasAnnotation(StaccatoPropagate.class)) {
					StaccatoPropagate prop = (StaccatoPropagate)meth.getAnnotation(StaccatoPropagate.class);
					this.addToMap(propagationGen, klass, new PropagateWrapper(new MethodDescSelector(meth), prop.value()));
					if(prop.value() == PropagationTarget.RECEIVER) {
						this.taintedTypes.add(klass);
					}
				}
			}
		}
	}
	
	private <T extends MethodSpec> CtBehavior resolveBehaviorSpec(CtClass theKlass, T spec) {
		if(!spec.name.equals("<init>")) {
			return this.resolveSpec(theKlass, spec);
		} else {
			CtBehavior m;
			try {
				m = theKlass.getConstructor(spec.description);
			} catch(NotFoundException e) {
				return null;
			}
			if((m.getModifiers() & Modifier.NATIVE) != 0) {
				return null;
			}
			return m;
		}
	}
	
	private <T extends MethodSpec> CtMethod resolveSpec(CtClass theKlass, T spec) { 
		CtMethod m;
		try {
			m = theKlass.getMethod(spec.name, spec.description);
		} catch(NotFoundException e) {
			// TODO(jtoman): logging
			return null;
		}
		if(!m.getDeclaringClass().equals(theKlass) || (m.getModifiers() & (Modifier.ABSTRACT | Modifier.NATIVE)) != 0) {
			return null;
		}
		return m;
	}
	
	private void updateTaintRoots(Set<CtClass> taintRoots, CtClass taintCarry) {
		List<CtClass> newChildren = null; 
		for(CtClass taintRoot : taintRoots) {
			if(taintCarry.subclassOf(taintRoot)) {
				return;
			}
			if(taintRoot.subclassOf(taintCarry)) {
				(newChildren == null ? (newChildren = new ArrayList<>()) : newChildren).add(taintRoot);
			}
		}
		if(newChildren != null) {
			for(CtClass toRemove : newChildren) {
				taintRoots.remove(toRemove);
			}
		}
		taintRoots.add(taintCarry);
	}
	
	public static class ResolutionResult {
		public final Map<CtClass, InstrumentationSpec> instrumentation;
		public final Set<CtClass> taintedClasses;
		public final Set<CtClass> taintedTypes;
		public final Map<CtClass, Set<String>> lockingFields;
		public ResolutionResult(
				Set<CtClass> tClass, Set<CtClass> tTypes,
				Map<CtClass, InstrumentationSpec> instrumentation, Map<CtClass, Set<String>> lockingFields) {
			this.taintedClasses = Collections.unmodifiableSet(tClass);
			this.taintedTypes = Collections.unmodifiableSet(new HashSet<>(tTypes));
			this.instrumentation = instrumentation;
			this.lockingFields = lockingFields;
		}
		
		public String dumpTaintedTypes() {
			StringBuilder sb = new StringBuilder();
			sb.append('[');
			boolean first = true;
			for(CtClass ty : taintedTypes) {
				if(!first) {
					sb.append(", ");
				}
				first = false;
				sb.append(ty.getName());
			}
			sb.append(']');
			return sb.toString();
		}
	}
	
	private boolean isExcludeMethod(String name) {
		return name.equals("__staccato_repair") ||
			   name.equals("__staccato_repair_field") ||
			   name.equals("__staccato_update_field_static") ||
			   name.equals("_staccato_set_taint") ||
			   name.equals("_staccato_get_taint") ||
			   name.equals("getPHOSPHOR_TAG") ||
			   name.equals("setPHOSPHOR_TAG");
	}
	
	@SuppressWarnings("unchecked")
	public ResolutionResult resolveActions(String ruleFile, Iterable<CtClass> targetClasses, boolean wrapPrimitiveField) 
			throws NotFoundException, FileNotFoundException, IOException, ClassNotFoundException {
		checkGen.clear();
		propagationGen.clear();
		excludeClasses.clear();
		taintedTypes.clear();
		
		if(ruleFile != null) {
			this.parseFile(ruleFile);
		}
		if(targetClasses != null) {
			this.collectAnnotations(targetClasses);
		}
		if(this.isPhosphor) {
			this.addPhosphorTypes();
		}
		if(StaccatoConfig.CONTROL_TAINT) {
			taintedTypes.add(cp.get("edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack"));
		}
		this.taintedTypes.removeAll(excludeClasses);
		// STEP 2
		Map<CtClass, Set<CheckSpec>> checkSpecs = this.resolveMethods(checkGen);
		Map<CtClass, Set<PropagationSpec>> propSpecs = this.resolveMethods(propagationGen);
		
		AutoPropagateSpec defaultAPS = null;
		if(System.getProperty("staccato.auto-propagate") != null) {
			String apsString = System.getProperty("staccato.auto-propagate");
			String[] parts = apsString.split(":");
			defaultAPS = new AutoPropagateSpec(parts[2], parts[0], parts[1]);
		}
		
		// STEP 3 (setup)
		
		Map<CtClass, InstrumentationSpec> instrumentSpecs = new HashMap<>();
		Map<CtClass, Set<String>> lockingFields = new HashMap<>();
		Set<String> fieldWrappingRef[] = new Set[1];
		AutoPropagateSpec autoPropagateSpecRef[] = new AutoPropagateSpec[1];
		Set<CtClass> taintRoots = new HashSet<>();
		FieldAnalyzer fa = new FieldAnalyzer(wrapPrimitiveField, lockingFields);

		if(targetClasses == null) {
			Set<CtClass> targetSet = new HashSet<>();
			targetSet.addAll(taintedTypes);
			targetSet.addAll(checkSpecs.keySet());
			targetSet.addAll(propSpecs.keySet());
			targetClasses = targetSet;
		}
		
		// Step 3
		
		for(CtClass klass : targetClasses) {
			try {
				if(klass.isInterface()) {
					continue;
				}
				if(isStaccatoClass(klass)) {
					continue;
				}
				Map<CtMethod, CheckSpec> checkActions = new HashMap<>();
				Map<CtBehavior, PropagationSpec> propagateActions = new HashMap<>();
				autoPropagateSpecRef[0] = null;
				fieldWrappingRef[0] = null;
				// Extract locking fields, wrapped volatile fields, and auto-propagation specs via ASM (use the gross "1-element-array-as-ref" idiom)
				boolean addForInst = fa.analyzeClass(klass, fieldWrappingRef, autoPropagateSpecRef);
				if(autoPropagateSpecRef[0] == null && defaultAPS != null) {
					autoPropagateSpecRef[0] = defaultAPS;
					addForInst = true;
				}
				for(Map.Entry<CtClass, Set<CheckSpec>> kv : checkSpecs.entrySet()) {
					if(!klass.subtypeOf(kv.getKey())) {
						continue;
					}
					for(CheckSpec cs : kv.getValue()) {
						CtMethod meth = this.resolveSpec(klass, cs);
						if(meth == null) { continue; }
						if(isExcludeMethod(meth.getName())) {
							continue;
						}
						CheckSpec existingCheck = checkActions.get(meth);
						if(existingCheck == null) {
							checkActions.put(meth, cs);
						} else {
							checkActions.put(meth, existingCheck.merge(cs));
						}
						addForInst = true;
					}
				}
				for(Map.Entry<CtClass, Set<PropagationSpec>> kv : propSpecs.entrySet()) {
					if(!klass.subtypeOf(kv.getKey())) {
						continue;
					}
					for(PropagationSpec ps : kv.getValue()) {
						CtBehavior behavior = this.resolveBehaviorSpec(klass, ps);
						if(behavior == null) { continue; }
						if(isExcludeMethod(behavior.getName())) {
							continue;
						}
						PropagationSpec pTarget = propagateActions.get(behavior);
						if(behavior instanceof CtConstructor && ps.target == PropagationTarget.RETURN) {
							throw new IllegalArgumentException(); // TODO(jtoman): logging
						}
						if(pTarget != null && pTarget.target == PropagationTarget.NONE) {
							continue;
						}
						if(pTarget != null && pTarget.target != ps.target && ps.target != PropagationTarget.NONE) {
							throw new RuntimeException("Conflicting flow annotations on method: " + behavior.getLongName());
						}
						propagateActions.put(behavior, ps);
					}
					addForInst = true;
				}
				{
					Set<Entry<CtMethod, CheckSpec>> entries = checkActions.entrySet();
					Iterator<Entry<CtMethod, CheckSpec>> it = entries.iterator();
					while(it.hasNext()) {
						Entry<CtMethod, CheckSpec> e = it.next();
						if(e.getValue().checkLevel == CheckLevel.NONE) {
							it.remove();
						}
					}
				}
				{
					Set<Entry<CtBehavior, PropagationSpec>> entries = propagateActions.entrySet();
					Iterator<Entry<CtBehavior, PropagationSpec>> it = entries.iterator();
					while(it.hasNext()) {
						Entry<CtBehavior, PropagationSpec> e = it.next();
						if(e.getValue().target == PropagationTarget.NONE) {
							it.remove();
						} else if(e.getValue().target == PropagationTarget.RETURN) {
							if(Type.getReturnType(e.getKey().getMethodInfo().getDescriptor()).getSort() != Type.OBJECT && TODO_MAKE_CONFIGURABLE) {
								it.remove();
							}
						}
					}
				}
				if(addForInst) {
					InstrumentationSpec ips = new InstrumentationSpec(
						new HashSet<>(checkActions.values()), 
						new HashSet<>(propagateActions.values()), fieldWrappingRef[0], autoPropagateSpecRef[0]);
					instrumentSpecs.put(klass, ips);
				}
				
				for(CtClass taintedType : taintedTypes) {
					if(klass.subtypeOf(taintedType)) {
						this.updateTaintRoots(taintRoots, klass);
						break;
					}
				}
			} catch(NotFoundException e) {
				if(System.getProperty("jtaint.quiet") == null) {
					System.out.println("NotFoundException hit while processing: " + klass.getName());
				}
			}
		}
		return new ResolutionResult(taintRoots, taintedTypes, instrumentSpecs, lockingFields);
	}

	private boolean isStaccatoClass(CtClass klass) {
		String klassName = klass.getName();
		return klassName.startsWith("edu.washington.cse.instrumentation") 
				&& !klassName.startsWith("edu.washington.cse.instrumentation.runtime.containers")
				&& !klassName.equals("edu.washington.cse.instrumentation.runtime.StaccatoRuntime");
	}

	private void addPhosphorTypes() throws NotFoundException {
		for(String t : TaintUtils.phosphorTaintedTypes) {
			this.taintedTypes.add(cp.get(t));
		}
	}

	private <T extends MethodSpec, U extends MethodSpecGenerator<T>> Map<CtClass, Set<T>> resolveMethods(
			Map<CtClass, ? extends Collection<U>> klassGenerators) {
		Map<CtClass, Set<T>> toReturn = new HashMap<>();
		for(Map.Entry<CtClass, ? extends Collection<U>> kv : klassGenerators.entrySet()) {
			CtClass theKlass = kv.getKey();
			Collection<U> generators = kv.getValue();
			Set<T> toSet = new HashSet<>();
			for(U gen : generators) {
				toSet.addAll(gen.findMethods(theKlass, taintedTypes));
			}
			if(toSet.size() == 0) {
				// TODO(jtoman): print warning
				continue;
			}
			toReturn.put(theKlass, toSet);
		}
		return toReturn;
	}
}
