package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveWithObjTag;
import edu.washington.cse.instrumentation.CheckActionRecorder;
import edu.washington.cse.instrumentation.TaintProxy;

public class AccessMethodFinder extends MethodVisitor implements Opcodes {
	private final TaintProxy tp;
	private final CheckActionRecorder car;
	public AccessMethodFinder(TaintProxy tp, CheckActionRecorder car) {
		super(Opcodes.ASM5);
		this.tp = tp;
		this.car = car;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		if(opcode != INVOKESTATIC) {
			return;
		}
		if(!name.startsWith("access$")) {
			return;
		}
		Type retType = Type.getReturnType(desc);
		if(tp.isTypeTainted(retType) || tp.subtypeOf(retType, TaintedPrimitiveWithObjTag.class, true)) {
			car.addMethod(owner.replace('/', '.'), name, desc);
		}
	}
}
