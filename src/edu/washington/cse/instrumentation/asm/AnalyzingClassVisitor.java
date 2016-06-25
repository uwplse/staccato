package edu.washington.cse.instrumentation.asm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import edu.washington.cse.instrumentation.CheckActionRecorder;
import edu.washington.cse.instrumentation.TaintProxy;
import edu.washington.cse.instrumentation.TaintUtils;
import edu.washington.cse.instrumentation.resolution.CheckSpec;
import edu.washington.cse.instrumentation.runtime.CheckLevel;

public class AnalyzingClassVisitor extends ClassVisitor {
	private final Map<String, CheckLevel> toCheck = new HashMap<>();
	private final TaintProxy tp;
	private final CheckActionRecorder car;
	private String sourceFile;
	private String className;
	public AnalyzingClassVisitor(TaintProxy tp, Collection<CheckSpec> toAnalyze, CheckActionRecorder car) {
		super(Opcodes.ASM5);
		for(CheckSpec csa : toAnalyze) {
			toCheck.put(csa.name + ":" + csa.description, csa.checkLevel);
		}
		this.tp = tp;
		this.car = car;
	}
	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.className = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		String mKey = name + ":" + desc;
		if(!toCheck.containsKey(mKey)) {
			return null;
		}
		CheckLevel cl = toCheck.get(mKey);
		if(cl == CheckLevel.LINEAR) {
			return null;
		}
//		assert cl == CheckLevel.STRICT || cl == CheckLevel.TRANSACT;
		MethodVisitor mv = new AccessMethodFinder(tp, car);
		if(cl == CheckLevel.TRANSACT) {
			String phosphorDesc = null;
			if(!name.endsWith("$$PHOSPHORTAGGED") && !desc.equals(phosphorDesc = TaintUtils.transformPhosphorMeth(desc))) {
				mv = new MethodProxySanityChecker(sourceFile, this.className, name + "$$PHOSPHORTAGGED", phosphorDesc, mv);
			} else {
				mv = new TransactionSanityChecker(sourceFile, mv);
			}
		}
		return mv;
	}
	
	@Override
	public void visitSource(String source, String debug) {
		this.sourceFile = source;
	}
}
