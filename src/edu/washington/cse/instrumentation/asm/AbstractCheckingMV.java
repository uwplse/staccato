package edu.washington.cse.instrumentation.asm;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveWithObjTag;
import edu.washington.cse.instrumentation.StaccatoConfig;
import edu.washington.cse.instrumentation.TaintProxy;
import edu.washington.cse.instrumentation.TaintUtils;
import edu.washington.cse.instrumentation.runtime.TaintHelper;


/**
 * Abstract class that:
 * 1) handles reading from arrays
 * 2) handles reading local variables
 * 3) handles checking return values
 * 
 * Subclasses should handle checking fields according to the appropriate check level
 * 
 * As a convenience, the code to generate the locking code is handled in this class
 */
public class AbstractCheckingMV extends TaintedFieldReadMV {
	protected LocalVariablesSorter lvs;
	protected AnalyzerAdapter aa;
	
	protected static class LockLabels {
		final Label start = new Label(), end = new Label(), handler = new Label(), next = new Label();
	}
	
	protected String fileName;
	protected Map<String, Set<String>> lockingFields;
	protected TaintProxy taintProxy;
	protected int lockIdx;
	private int lineNumber;
	private final String checkingMethod;
	
	private boolean ignoreReturn;
	
	protected boolean skipType(Object o) {
		Type t = null;
		if(!(o instanceof Type)) {
			if(o instanceof String) {
				t = Type.getObjectType((String)o);
			}
		} else if(o instanceof Type) {
			t = (Type) o;
		}
		if(t == null) {
			return false;
		}
		return t.getInternalName().equals(Type.getInternalName(ControlTaintTagStack.class));
	}

	public AbstractCheckingMV(MethodVisitor mv, String fileName, TaintProxy taintProxy, Map<String, Set<String>> lockingFields, String className, Set<String> volatileFields,
			String checkingMethod) {
		super(mv, className, volatileFields);
		this.fileName = fileName;
		this.lockingFields = lockingFields;
		this.lockIdx = -1;
		this.taintProxy = taintProxy;
		
		this.checkingMethod = checkingMethod;
	}
	
	private void addStaticLockOpen(String owner, LockLabels s) {
		super.visitFieldInsn(GETSTATIC, owner, edu.washington.cse.instrumentation.TaintUtils.STATIC_FIELD_LOCK_NAME, Type.getDescriptor(ReentrantReadWriteLock.class));
		this.addLockInsn(true);
		super.visitTryCatchBlock(s.start, s.end, s.handler, null);
		super.visitLabel(s.start);

	}
		
	/*
	 * Precondition:
	 * 	Stack := O
	 * 
	 * Post-Condition:
	 * 	Stack := O
	 *  LVar[lockIdx] = RWL
	 *  Lock held
	 */
	private void startInstanceLock(String owner, LockLabels s) {
		super.visitInsn(DUP); //O O
		super.visitFieldInsn(GETFIELD, owner, edu.washington.cse.instrumentation.TaintUtils.FIELD_LOCK_NAME, Type.getDescriptor(ReentrantReadWriteLock.class)); //RWL O
		super.visitInsn(DUP); //RWL RWL O
		if(lockIdx == -1) {
			lockIdx = lvs.newLocal(org.objectweb.asm.Type.getType(ReentrantReadWriteLock.class));
		}
		super.visitVarInsn(ASTORE, lockIdx); // RWL O
		this.addLockInsn(true); // O
		this.visitTryCatchBlock(s.start, s.end, s.handler, null);
		super.visitLabel(s.start);
	}

	private void endInstanceLocks(LockLabels s) {
		super.visitLabel(s.end);
		super.visitVarInsn(ALOAD, lockIdx); // RWL V T O
		this.addLockInsn(false); 
		super.visitJumpInsn(GOTO, s.next);
		super.visitLabel(s.handler); // Throwable
		super.visitVarInsn(ALOAD, lockIdx); // RWL Throwable
		this.addLockInsn(false); // Throwable
		super.visitInsn(ATHROW);
		super.visitLabel(s.next);
	}
	
	protected boolean skipCheck(String owner, String fieldName, String fieldDesc) {
		if(fieldDesc.equals(Type.getDescriptor(Taint.class))) {
			return true;
		}
		if(fieldName.equals("taint") && fieldDesc.equals(Type.getDescriptor(Object.class)) &&
			owner.startsWith("edu/columbia/cs/psl/phosphor/struct")) {
			return true;
		}
		return false;
	}

	
	private void addStaticLockClose(String owner, LockLabels s) {
		super.visitLabel(s.end);
		super.visitFieldInsn(GETSTATIC, owner, edu.washington.cse.instrumentation.TaintUtils.STATIC_FIELD_LOCK_NAME, Type.getDescriptor(ReentrantReadWriteLock.class));
		this.addLockInsn(false);
		super.visitJumpInsn(GOTO, s.next);
		super.visitLabel(s.handler);
		super.visitFieldInsn(GETSTATIC, owner, edu.washington.cse.instrumentation.TaintUtils.STATIC_FIELD_LOCK_NAME, Type.getDescriptor(ReentrantReadWriteLock.class));
		this.addLockInsn(false);
		super.visitInsn(ATHROW);
		super.visitLabel(s.next);
	}

	private void addLockInsn(boolean isLock) {
		super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(ReentrantReadWriteLock.class),
				"readLock", "()" + Type.getDescriptor(ReentrantReadWriteLock.ReadLock.class), false); // RL ...
		super.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(ReentrantReadWriteLock.ReadLock.class), isLock ? "lock" : "unlock", "()V", false); // ...
	}
	

	private boolean shouldAddLocking(String owner, String name) {
		return lockingFields.containsKey(owner) && lockingFields.get(owner).contains(name);
	}
	
	public static boolean isBookkeepingMethod(int opcode, String owner, String name, String desc) {
		if(opcode == INVOKESTATIC && owner.equals(Type.getInternalName(Taint.class))) {
			return true;
		}
		if(opcode == INVOKESTATIC && owner.equals(Type.getInternalName(TaintHelper.class))) {
			return true;
		}
		if(opcode == INVOKESTATIC && owner.equals(Type.getInternalName(edu.columbia.cs.psl.phosphor.TaintUtils.class)) && name.equals("ensureUnboxed")) {
			return true;
		}
		return false;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean itf) {
		super.visitMethodInsn(opcode, owner, name, desc, itf);
		if(this.ignoreReturn) {
			this.ignoreReturn = false;
			return;
		}
		if(pauseChecking) {
			return;
		}
		Type t = Type.getReturnType(desc);
		if(!taintProxy.isTypeTainted(t) && !taintProxy.subtypeOf(t, TaintedPrimitiveWithObjTag.class, true)) {
			return;
		}
		if(isBookkeepingMethod(opcode, owner, name, desc)) {
			return;
		}
		pauseChecking = true;
		// RET
		if(taintProxy.subtypeOf(t, TaintedPrimitiveWithObjTag.class, true)) { 
			super.visitInsn(DUP); // RET RET
			Label isNull = new Label();
			Label end = new Label();
			super.visitJumpInsn(IFNULL, isNull); // RET
			super.visitInsn(DUP); // RET RET
			super.visitFieldInsn(GETFIELD, Type.getInternalName(TaintedPrimitiveWithObjTag.class), "taint", Type.getDescriptor(Object.class)); // RET O
			super.visitJumpInsn(GOTO, end);
			super.visitLabel(isNull); // RET
			super.visitInsn(ACONST_NULL); // RET NULL
			super.visitLabel(end); // RET TAINTED
		} else {
			super.visitInsn(DUP);
		}
		// RET TAINTED
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), checkingMethod, "(Ljava/lang/Object;)V", false);
		pauseChecking = false;
	}
	
	
	@Override
	public void visitVarInsn(int opcode, int var) {
		if(opcode != ALOAD || pauseChecking || StaccatoConfig.WEAK_CHECKING) {
			super.visitVarInsn(opcode, var);
			return;
		}
		boolean check = true;
		if(aa.locals != null) {
			Object varType = aa.locals.get(var);
			if(varType instanceof String) {
				String name = (String)varType;
				Type objectType = Type.getObjectType(name);
				check = this.taintProxy.isTypeTainted(objectType) && !skipType(name);
			} else {
				check = false;
			}
		}
		super.visitVarInsn(opcode, var);
		if(!check) {
			return;
		}
		pauseChecking = true;
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), checkingMethod, "(Ljava/lang/Object;)V", false);
		pauseChecking = false;
	}
	

	@Override
	public void visitInsn(int opcode) {
		if(opcode == TaintUtils.SKIP_CHECK_OPCODE) {
			this.ignoreReturn = true;
			return;
		}
		if(opcode != AALOAD || pauseChecking || StaccatoConfig.WEAK_CHECKING) {
			super.visitInsn(opcode);
			return;
		}
		boolean check = true;
		if(aa.stack != null) {
			Object arrayType = aa.stack.get(aa.stack.size() - 2);
			if(arrayType instanceof String) {
				Type objectType = Type.getObjectType((String)arrayType);
				check = objectType.getSort() == Type.ARRAY && objectType.getDimensions() == 1
					&& this.taintProxy.isTypeTainted(objectType.getElementType()) && !skipType(objectType);
			} else {
				check = false;
			}
		}
		super.visitInsn(opcode);
		if(!check) {
			return;
		}
		pauseChecking = true;
		super.visitInsn(DUP);
		super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(TaintHelper.class), checkingMethod, "(Ljava/lang/Object;)V", false);
		pauseChecking = false;
	}
	
	protected LockLabels startLocking(int opcode, String owner, String fieldName) {
		boolean addLocks = shouldAddLocking(owner, fieldName);
		if(!addLocks) {
			return null;
		}
		LockLabels ll = new LockLabels();
		if(opcode == GETFIELD) {
			startInstanceLock(owner, ll);
		} else if(opcode == GETSTATIC) {
			addStaticLockOpen(owner, ll);
		} else {
			throw new IllegalArgumentException("Bad opcode: " + opcode);
		}
		return ll;
	}
	
	protected void endLocking(int opcode, String owner, LockLabels ll) {
		if(ll == null) {
			return;
		}
		if(opcode == GETFIELD) {
			endInstanceLocks(ll);
		} else if(opcode == GETSTATIC) {
			addStaticLockClose(owner, ll);
		} else {
			throw new IllegalArgumentException("Bad opcode: " + opcode);
		}
	}
	
	@Override
	public void visitLineNumber(int line, Label start) {
		this.lineNumber = line;
		super.visitLineNumber(line, start);
	}
	
	protected void debugFieldCheck(String fieldOwner, String fieldName, String fieldType) {
		if(!StaccatoConfig.STACCATO_VERBOSE) {
			return;
		}
		StringBuffer sb = new StringBuffer();
		sb.append("Instrumenting read of field: ").append(fieldOwner.replace('/', '.')).append(".").append(fieldName).append(":").append(fieldType);
		if(lineNumber != -1) {
			sb.append(" on line: ").append(lineNumber);
		}
		if(fileName != null) {
			sb.append(" in file: ").append(fileName);
		}
		System.out.println(sb.toString());
	}
}
