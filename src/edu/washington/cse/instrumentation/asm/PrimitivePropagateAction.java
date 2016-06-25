package edu.washington.cse.instrumentation.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag;

public class PrimitivePropagateAction extends TaintPropagator {
	private final int taintIdx;

	public PrimitivePropagateAction(int taintIdx) {
		this.taintIdx = taintIdx;
	}
	
	@Override
	public void onCallAction(MethodVisitor mv, int opcode,
			String owner, String name, String desc, boolean itf) {
		setupCall(mv, opcode, owner, name, desc, itf);
		mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Object.class)); // N
		mv.visitTypeInsn(CHECKCAST, Type.getInternalName(TaintedWithObjTag.class));
		mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(TaintedWithObjTag.class), "getPHOSPHOR_TAG", "()Ljava/lang/Object;", true);
		mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Taint.class));
		mv.visitVarInsn(ASTORE, taintIdx);
	}
}
