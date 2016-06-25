package edu.washington.cse.instrumentation.asm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.commons.TryCatchBlockSorter;

import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.washington.cse.instrumentation.StaccatoConfig;
import edu.washington.cse.instrumentation.TaintProxy;
import edu.washington.cse.instrumentation.resolution.AutoPropagateSpec;
import edu.washington.cse.instrumentation.resolution.CheckSpec;
import edu.washington.cse.instrumentation.resolution.InstrumentationSpec;
import edu.washington.cse.instrumentation.resolution.PropagationSpec;
import edu.washington.cse.instrumentation.runtime.CheckLevel;

public class StaccatoClassVisitor extends ClassVisitor {
	
	private final Map<String, Set<String>> lockingFields;
	private final Map<String, CheckSpec> checkMethods;
	private final Map<String, PropagationSpec> propagateMethods;
	private final TaintProxy taintProxy;
	private String sourceFile;
	private final Set<String> volatileFields;
	private String className;
	private final AutoPropagateSpec autoPropagateSpec;
	
	public StaccatoClassVisitor(ClassVisitor cw, 
			TaintProxy taintProxy,
			Collection<CheckSpec> checkSpecs,
			Collection<PropagationSpec> propagateSpecs,
			Map<String, Set<String>> lockingFields, Set<String> volatileFields,
			AutoPropagateSpec autoPropagateSpec) {
		super(Opcodes.ASM5, cw);
		this.taintProxy = taintProxy;
		this.lockingFields = lockingFields;
		this.checkMethods = new HashMap<>();
		this.propagateMethods = new HashMap<>();
		if(checkSpecs != null) {
			for(CheckSpec csa : checkSpecs) {
				checkMethods.put(csa.name + ":" + csa.description, csa);
			}
		}
		if(propagateSpecs != null && !edu.washington.cse.instrumentation.StaccatoConfig.IS_AUTO_TAINT) {
			for(PropagationSpec psa : propagateSpecs) {
				propagateMethods.put(psa.name + ":" + psa.description, psa);
			}
		}
		this.volatileFields = volatileFields;
		this.autoPropagateSpec = autoPropagateSpec;
	}
	
	public StaccatoClassVisitor(ClassWriter cw, TaintProxy taintProxy,
			HashMap<String, Set<String>> lockingFields, InstrumentationSpec ips) {
		this(cw, taintProxy, ips.toCheck, ips.toPropagate, lockingFields, ips.wrappedVolatileFields, ips.autoPropagateSpec);
	}

	@Override
	public void visitSource(String source, String debug) {
		super.visitSource(source, debug);
		this.sourceFile = source;
	}
	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		this.className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}
	
	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {
		if(volatileFields == null) {
			return super.visitField(access, name, desc, signature, value);
		}
		if(name.endsWith(TaintUtils.TAINT_FIELD)) {
			int length = name.length();
			String baseName = name.substring(0, length - TaintUtils.TAINT_FIELD.length());
			if(volatileFields.contains(baseName)) {
				return null;
			} else {
				return super.visitField(access, baseName, desc, signature, value);
			}
		} else if(volatileFields.contains(name)) {
			return super.visitField(access, name, TaintUtils.getContainerReturnType(desc).getDescriptor(), signature, value);
		} else {
			return super.visitField(access, name, desc, signature, value);
		}
	}
	
	private MethodVisitor addAutoPropagate(MethodVisitor mv, int access, String name, String desc) {
		if(autoPropagateSpec != null && autoPropagateSpec.setterPattern.matcher(name).matches()) {
			Type[] argTypes = Type.getArgumentTypes(desc);
			boolean isPrimitiveSetter = argTypes.length == 2 && argTypes[0].getSort() == Type.OBJECT && 
					argTypes[0].getInternalName().equals(Type.getInternalName(Taint.class));
			boolean isEnumSetter = argTypes.length == 1 && argTypes[0].getSort() == Type.OBJECT && taintProxy.isEnum(argTypes[0]);
			SetterAction sa = null;
			if(isPrimitiveSetter) {
				sa = new PrimitivePropagateAction((access & Opcodes.ACC_STATIC) == 0 ? 1 : 0);
			} else if(isEnumSetter) {
				sa = new EnumPropagateAction((access & Opcodes.ACC_STATIC) == 0 ? 1 : 0, argTypes[0].getInternalName());
			} else {
				sa = new NoAction();
			}
			AutoPropagateMV apmv = new AutoPropagateMV(mv, className, name, desc, 
				autoPropagateSpec.owner, autoPropagateSpec.method, sa);
			return apmv.lvs = new LocalVariablesSorter(access, desc, apmv);
		}
		return mv;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
		String methodKey = name + ":" + desc;
		mv = new TryCatchBlockSorter(mv, access, name, desc, signature, exceptions);
		mv = addAutoPropagate(mv, access, name, desc);
		if(!checkMethods.containsKey(methodKey) && !propagateMethods.containsKey(methodKey)) {
			if(volatileFields != null) {
				mv = new VolatileFieldWriteMV(
						new VolatileFieldReadMV(mv, className, volatileFields),
					className, volatileFields);
			}
			return mv;
		}
		if(volatileFields != null) {
			mv = new VolatileFieldWriteMV(mv, className, volatileFields);
		}
		boolean isAccessMethod = name.startsWith("access$") && (access & Opcodes.ACC_SYNTHETIC) != 0;
		if(checkMethods.containsKey(methodKey)) {
			mv = new RetPopStateMV(mv, isAccessMethod);
		}
		if(propagateMethods.containsKey(methodKey)) {
			PropagationSpec ps = propagateMethods.get(methodKey);
			mv = new TaintPropagationMV(mv, ps.target, ps.taintIndices, Type.getReturnType(desc));
		}
		mv = new ReturnCoalescerMV(mv);
		boolean needReadMV = true;
		if(checkMethods.containsKey(methodKey)) {
			CheckSpec cs = checkMethods.get(methodKey);
			CheckMethodVisitor cmv = new CheckMethodVisitor(mv, cs.taintIndices, cs.checkLevel, isAccessMethod);
			mv = cmv;
			if(cs.checkBody) {
				needReadMV = false;
				AbstractCheckingMV checkMV;
				if(cs.checkLevel == CheckLevel.STRICT || cs.checkLevel == CheckLevel.TRANSACT) {
					checkMV = new FieldCheckingMV(mv, sourceFile, taintProxy, lockingFields, className, volatileFields);
				} else {
					assert cs.checkLevel == CheckLevel.LINEAR;
					checkMV = new LinearCheckingMV(mv, sourceFile, taintProxy, lockingFields, className, volatileFields);
				}
				checkMV.aa = new AnalyzerAdapter(Opcodes.ASM5, className, access, name, desc, checkMV) {
					@Override
					public void visitInsn(int opcode) {
						if(opcode == edu.washington.cse.instrumentation.TaintUtils.SKIP_CHECK_OPCODE) {
							this.mv.visitInsn(opcode);
							return;
						}
						super.visitInsn(opcode);
					}
				};
				mv = checkMV.lvs = new LocalVariablesSorter(access, desc, checkMV.aa);
				mv = new CheckSkippingMN(access, name, desc, signature, exceptions, mv);
				if(StaccatoConfig.TAG_CF_OPS) {
					mv = new ControlFlowTagging2MV(new ControlFlowTaggingMV(mv, cs.checkLevel), cs.checkLevel);
				}

			}
		}
		if(needReadMV && volatileFields != null) {
			mv = new VolatileFieldReadMV(mv, className, volatileFields);
		}
		return mv;
	}

}
