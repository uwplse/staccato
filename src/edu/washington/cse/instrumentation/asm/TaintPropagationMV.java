package edu.washington.cse.instrumentation.asm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.washington.cse.instrumentation.runtime.PropagationTarget;
import edu.washington.cse.instrumentation.runtime.TaintPropagation;
import edu.washington.cse.instrumentation.runtime.containers.StaccatoList;
import edu.washington.cse.instrumentation.runtime.containers.StaccatoMap;
import edu.washington.cse.instrumentation.runtime.containers.StaccatoSet;

public class TaintPropagationMV extends TaintArgVisitor implements Opcodes {
	private static final String PROPAGATE_DESC = "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";
	private static final String PROPAGATE_TAINT = "propagateTaint";
	private static final String PROPAGATION_OWNER = Type.getInternalName(TaintPropagation.class);

	private final PropagationTarget propagationTarget;
	
	private final static Map<String, String> wrapperTypes = new HashMap<String, String>() {
		private static final long serialVersionUID = 2094669607219415595L;
		{
			put(Type.getInternalName(List.class), Type.getInternalName(StaccatoList.class));
			put(Type.getInternalName(Set.class), Type.getInternalName(StaccatoSet.class));
			put(Type.getInternalName(Map.class), Type.getInternalName(StaccatoMap.class));
		}
	};
	
	private final Type returnType;

	public TaintPropagationMV(MethodVisitor mv, PropagationTarget propagationTarget, int[] args, Type type) {
		super(mv, args);
		this.propagationTarget = propagationTarget;
		this.returnType = type;
		if(propagationTarget == PropagationTarget.WRAPPED_RETURN) {
			if(!wrapperTypes.containsKey(type.getInternalName())) {
				throw new IllegalArgumentException(type.toString());
			}
		}
	}

	@Override
	public void visitInsn(int opcode) {
		if(!(opcode <= RETURN && opcode >= IRETURN)) {
			super.visitInsn(opcode);
			return;
		}
		if(propagationTarget == PropagationTarget.RECEIVER) {
			super.visitVarInsn(ALOAD, 0);
			this.loadTaintedArgArray();
			super.visitMethodInsn(INVOKESTATIC, PROPAGATION_OWNER, PROPAGATE_TAINT, PROPAGATE_DESC, false);
			super.visitInsn(POP);
		} else if(propagationTarget == PropagationTarget.RETURN) {
			this.loadTaintedArgArray();
			super.visitMethodInsn(INVOKESTATIC, PROPAGATION_OWNER, PROPAGATE_TAINT, PROPAGATE_DESC, false);
			super.visitTypeInsn(CHECKCAST, returnType.getInternalName());
		} else if(propagationTarget == PropagationTarget.WRAPPED_RETURN) {
			String wrapperType = wrapperTypes.get(returnType.getInternalName());
			super.visitTypeInsn(NEW, wrapperType);
			super.visitInsn(DUP_X1);
			super.visitInsn(SWAP);
			super.visitMethodInsn(INVOKESPECIAL, wrapperType, "<init>", "("+ returnType.getDescriptor() + ")V", false);
			this.loadTaintedArgArray();
			super.visitMethodInsn(INVOKESTATIC, PROPAGATION_OWNER, PROPAGATE_TAINT, PROPAGATE_DESC, false);
			super.visitTypeInsn(CHECKCAST, returnType.getInternalName());
		}
		super.visitInsn(opcode);
	}
}
