package edu.washington.cse.instrumentation.runtime;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.columbia.cs.psl.phosphor.TaintUtils;
import edu.columbia.cs.psl.phosphor.runtime.BoxedPrimitiveStoreWithObjTags;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedWithObjTag;

public class TaintPropagation {
	static final int N_LOCKS = 1000;
	static ReentrantReadWriteLock objectLocks[] = null;//new ReentrantReadWriteLock[N_LOCKS];
	private static volatile boolean initFlag = false;
	private static volatile boolean INIT = false;
	private static Object INIT_LOCK = new Object();
	
	
	static class BoolRef { boolean flag; }
	
	static ThreadLocal<BoolRef> blockPropagate;
	public static Throwable lastError;

	
	private static void initRuntime() {
		synchronized(INIT_LOCK) {
			if(initFlag) {
				return;
			}
			INIT = true;
			objectLocks = new ReentrantReadWriteLock[N_LOCKS];
			for(int i = 0; i < N_LOCKS; i++) {
				objectLocks[i] = new ReentrantReadWriteLock();
			}
			blockPropagate = new ThreadLocal<BoolRef>() {
				@Override
				protected BoolRef initialValue() {
					return new BoolRef();
				}
				
				@SuppressWarnings("unused")
				protected Object initialValue$$PHOSPHORTAGGED(ControlTaintTagStack o) {
					return initialValue();
				}
				
			};
			INIT = false;
			initFlag = true;
		}
	}
	
	public static Object propagateTaint(Object target, Object... source) {
		if(!sun.misc.VM.isBooted() || INIT) {
			return target;
		}
		if(!initFlag) {
			initRuntime();
		}
		if(lastError != null) {
			return target;
		}
		if(blockPropagate.get().flag) {
			return target;
		}
		if(source.length == 1 && source[0] == target) {
			return target;
		}
		if(!isTaintTarget(target)) {
			return target;
		}
		blockPropagate.get().flag = true;
		try {
			return propagateTaintThrow(target, source);	
		} catch(Throwable t) {
			lastError = t;
			return target;
		} finally {
			blockPropagate.get().flag = false;
		}
		
	}
	

	public static Object maybeTaint(Object target) {
		if(target == null) {
			return null;
		}
		if(isTaintSource(target)) {
			return target;
		} else {
			return null;
		}
	}
	

	static boolean noLockType(Object o) {
		return
			!(o instanceof TaintCarry) ||
			(o instanceof TaintCarry && isPrimitiveWrapper(o));
	}
	

	static boolean isTaintSource(Object o) {
		return o instanceof TaintCarry || o instanceof Taint || isPrimitiveWrapper(o) || o instanceof ControlTaintTagStack || (o instanceof Enum && o instanceof TaintedWithObjTag); 
	}
	
	static boolean isPrimitiveWrapper(Object o) {
		return 
			o instanceof Short ||
			o instanceof Byte ||
			o instanceof Boolean ||
			o instanceof Integer ||
			o instanceof Float ||
			o instanceof Long ||
			o instanceof Double;
	}
	

	static boolean isTaintTarget(Object o) {
		return o instanceof TaintCarry || o instanceof TaintedPrimitiveWithObjTag || isPrimitiveWrapper(o);
	}

	
	private static Object propagateTaintThrow(Object target, Object... source) {
		// pretty sure this is redundant now
		if(target == null) {
			return target;
		}
		int[] heldLocks = new int[source.length + 1];
		int targetPos, numLocks;
		if(noLockType(target)) {
			targetPos = -1;
			numLocks = 0;
		} else {
			numLocks = 1;
			targetPos = heldLocks[0] = System.identityHashCode(target) % N_LOCKS;
		}
		for(int i = 0; i < source.length; i++) {
			source[i] = maybeTaint(source[i]);
			if(source[i] == null) {
				continue;
			} else if(noLockType(source[i])) {
				continue;
			}
			heldLocks[numLocks++] = System.identityHashCode(source[i]) % N_LOCKS;
		}
		Arrays.sort(heldLocks, 0, numLocks);
		int acqIndex = 0;
		try {
			for(; acqIndex < numLocks; acqIndex++) {
				if(heldLocks[acqIndex] == targetPos) {
					objectLocks[heldLocks[acqIndex]].writeLock().lock();
				} else {
					objectLocks[heldLocks[acqIndex]].readLock().lock();
				}
			}
			
			StringIntHashMap seed;
			// merge taint with existing taint
			StringIntHashMap taintArgs = null;
			if(!(target instanceof TaintedPrimitiveWithObjTag)) {
				taintArgs = getTaint(target);
			}
			if(taintArgs != null) {
				taintArgs = new StringIntHashMap(taintArgs);
			}
			
			for(Object src : source) {
				if(src == null) {
					continue;
				}
				if(src == target) { continue; }
				seed = getTaint(src);
				if(seed == null) {
					continue;
				}
				if(taintArgs != null) {
					mergeTaint(taintArgs, seed);
				} else {
					taintArgs = new StringIntHashMap(seed);
				}
			}
			return setTaint(target, taintArgs);
		} finally {
			for(int i = 0; i < acqIndex; i++) {
				if(heldLocks[i] == targetPos) {
					objectLocks[heldLocks[i]].writeLock().unlock();
				} else {
					objectLocks[heldLocks[i]].readLock().unlock();
				}
			}
		}
	}
	

	@SuppressWarnings("unchecked")
	// MUST be called with the the object loock for target held in write mode
	// and with propagation disabled
	static Object setTaint(Object target, StringIntHashMap taintArgs) {
		boolean isWrap = false;
		if(target == "true" || target == "false") {
			String toRet = new String(((String)target).toCharArray());
			((TaintCarry)(Object)toRet)._staccato_set_taint(taintArgs);
			return toRet;
		} else if(target instanceof TaintedPrimitiveWithObjTag) {
			((TaintedPrimitiveWithObjTag) target).taint = taintArgs != null ? new Taint(taintArgs) : null;
			return target;
		} else if(target instanceof Enum && target instanceof TaintedWithObjTag) {
			Enum<?> eTarget = (Enum<?>)target;
			Class<? extends Enum> eClass;
			if((eClass = eTarget.getClass()).getSuperclass() != Enum.class) {
//				eClass = 
				eClass = (Class<? extends Enum>) eClass.getSuperclass();
			}
			String s = new String(((Enum<?>)target).name());
			((TaintCarry)(Object)s)._staccato_set_taint(taintArgs);
			return TaintUtils.enumValueOf(eClass, s);
		} else if(!(isWrap = isPrimitiveWrapper(target)) && target instanceof TaintCarry) {
			((TaintCarry)target)._staccato_set_taint(taintArgs);
			return target;
		} else if(isWrap && !(target instanceof TaintCarry)) {
			if(taintArgs == null) {
				return target;
			}
//			assert Instrumenter.isClassWithHashmapTag(target.getClass().getName().replace('.', '/'));
			if(target instanceof Character) {
				return BoxedPrimitiveStoreWithObjTags.valueOf(new Taint(taintArgs), ((Character) target).charValue());
			} else if(target instanceof Byte) {
				return BoxedPrimitiveStoreWithObjTags.valueOf(new Taint(taintArgs), ((Byte) target).byteValue());
			} else if(target instanceof Boolean) {
				return BoxedPrimitiveStoreWithObjTags.valueOf(new Taint(taintArgs), ((Boolean) target).booleanValue());
			} else if(target instanceof Short) {
				return BoxedPrimitiveStoreWithObjTags.valueOf(new Taint(taintArgs), ((Short) target).shortValue());
			} else {
				throw new IllegalArgumentException();
			}
		} else {
//			assert isWrap;
//			assert target instanceof TaintCarry && target instanceof TaintedWithObjTag;
			if(taintArgs == null) {
				return target;
			}
			Object toRet = null;
			if(target instanceof Integer) {
				toRet = new Integer(((Integer) target).intValue());
			} else if(target instanceof Double) {
				toRet = new Double(((Double) target).doubleValue());
			} else if(target instanceof Long) {
				toRet = new Long(((Long) target).longValue());
			} else {
//				assert target instanceof Float;
				toRet = new Float(((Float)target).floatValue());
			}
			((TaintCarry)toRet)._staccato_set_taint(taintArgs);
			return toRet;
		}
	}

	
	public static Object propagateMultiTaint(Object returnVal, Object receiver, Object... source) {
		if(!sun.misc.VM.isBooted() || INIT) {
			return returnVal;
		}
		if(!initFlag) {
			initRuntime();
		}
		if(blockPropagate.get().flag) {
			return returnVal;
		}
		blockPropagate.get().flag = true;
		try {
			return propagateMultiTaintThrow(returnVal, receiver, source);
		} finally {
			blockPropagate.get().flag = false;	
		}
	}
	
	private static Object propagateMultiTaintThrow(Object returnVal, Object receiver, Object... source) {
		int[] heldLocks = new int[source.length + 2];
		int retPos = -1, receiverPos = -1, numLocks = 0;
		if(returnVal != null && !noLockType(returnVal) && isTaintTarget(returnVal)) {
			retPos = heldLocks[numLocks++] = System.identityHashCode(returnVal) % N_LOCKS;
		}
		if(!noLockType(receiver) && isTaintTarget(receiver)) {
			receiverPos = heldLocks[numLocks++] = System.identityHashCode(receiver) % N_LOCKS;
		}
		for(int i = 0; i < source.length; i++) {
			source[i] = maybeTaint(source[i]);
			if(source[i] == null) {
				continue;
			// do not lock for primitive wrappers or the Taint object
			} else if(noLockType(source[i])) {
				continue;
			}
			heldLocks[numLocks++] = System.identityHashCode(source[i]) % N_LOCKS;
		}
		Arrays.sort(heldLocks, 0, numLocks);
		int acqIndex = 0;
		try {
			for(; acqIndex < numLocks; acqIndex++) {
				if(heldLocks[acqIndex] == retPos || heldLocks[acqIndex] == receiverPos) {
					objectLocks[heldLocks[acqIndex]].writeLock().lock();
				} else {
					objectLocks[heldLocks[acqIndex]].readLock().lock();
				}
			}
			StringIntHashMap seed;
			StringIntHashMap taintArgs = null;
			for(Object src : source) {
				if(src == null) {
					continue;
				}
//				assert isTaintSource(src);
				seed = getTaint(src);
				if(seed == null) {
					continue;
				}
				if(taintArgs != null) {
					mergeTaint(taintArgs, seed);
				} else {
					taintArgs = new StringIntHashMap(seed);
				}
			}
			{
				if(returnVal != null && isTaintTarget(returnVal)) {
					StringIntHashMap currTaint = null;
					if(isTaintSource(returnVal)) {
						currTaint = getTaint(returnVal);
					}
					if(currTaint != null && taintArgs != null) {
						currTaint = new StringIntHashMap(currTaint);
						mergeTaint(currTaint, taintArgs);
					} else if(currTaint == null) {
						currTaint = taintArgs;
					}
					returnVal = setTaint(returnVal, currTaint);
				}
			}
			if(isTaintTarget(receiver)) {
				StringIntHashMap currTaint = getTaint(receiver);
				if(currTaint != null && taintArgs != null) {
					currTaint = new StringIntHashMap(currTaint);
					mergeTaint(currTaint, taintArgs);
				} else if(currTaint == null) {
					currTaint = taintArgs;
				}
				setTaint(receiver, currTaint);
			}
			return returnVal;
		} finally {
			for(int i = 0; i < acqIndex; i++) {
				if(heldLocks[i] == retPos || heldLocks[i] == receiverPos) {
					objectLocks[heldLocks[i]].writeLock().unlock();
				} else {
					objectLocks[heldLocks[i]].readLock().unlock();
				}
			}
		}
	}
	
	public static StringIntHashMap unwrapTaint(Object o) { 
		if(o == null) {
			return null;
		}
		return (StringIntHashMap)((Taint)o).lbl;
	}
	
	// MUST be called with the read lock for target held in (at least) read mode
	// and block propagate must be true
	public static StringIntHashMap getTaint(Object target) {
		if(target == null) {
			return null;
		}
//		assert canCarryTaint(target) || target instanceof TaintedPrimitiveWithObjTag;
		if(target instanceof TaintCarry) {
			return (StringIntHashMap)((TaintCarry) target)._staccato_get_taint();
		} else if(target instanceof TaintedPrimitiveWithObjTag) {
			Object t = ((TaintedPrimitiveWithObjTag)target).taint;
			return unwrapTaint(t);
		} else if(target instanceof Enum && target instanceof TaintedWithObjTag) {
			Object o = ((TaintedWithObjTag)target).getPHOSPHOR_TAG();
			return unwrapTaint(o);
		} else if(target instanceof Taint) {
			return (StringIntHashMap)((Taint) target).getLabel();
		} else if(target instanceof Character) {
			return unwrapTaint(BoxedPrimitiveStoreWithObjTags.charValue((Character) target).taint);
		} else if(target instanceof Byte) {
			return unwrapTaint(BoxedPrimitiveStoreWithObjTags.byteValue((Byte) target).taint);
		} else if(target instanceof Short) {
			return unwrapTaint(BoxedPrimitiveStoreWithObjTags.shortValue((Short) target).taint);
		} else if(target instanceof Boolean) {
			return unwrapTaint(BoxedPrimitiveStoreWithObjTags.booleanValue((Boolean) target).taint);
		} else if(target instanceof ControlTaintTagStack) {
			Taint t = ((ControlTaintTagStack)target).taint;
			if(t == null) {
				return null;
			}
			return (StringIntHashMap) t.lbl;
		} else {
			throw new IllegalArgumentException("Unexpected value: " + target + " with " + target.getClass().getName());
		}
	}

	static public void mergeTaint(StringIntHashMap target, StringIntHashMap source) {
		
		for(int i = 0, ent = 0; i < source.m_arraySize && ent < source.m_entryCount; i++) {
			if(source.m_keyTable[i] == null) {
				continue;
			}
			String sourceKey = source.m_keyTable[i];
			int sourceVersion = source.m_valueTable[i];
			if(!target.containsKey(sourceKey)) {
				target.add(sourceKey, sourceVersion);
			} else {
				int targetVersion = target.get(sourceKey);
				if(sourceVersion == targetVersion) {
					continue;
				}
				boolean mixVersions = false;
				if(sourceVersion < 0) {
					sourceVersion *= -1;
					mixVersions = true;
				}
				if(targetVersion < 0) {
					targetVersion *= -1;
					mixVersions = true;
				}
				if(targetVersion != sourceVersion) {
					mixVersions = true;
				}
				int newVersion = Math.min(targetVersion, sourceVersion);
				if(mixVersions) {
					newVersion *= -1;
				}
				target.add(sourceKey, newVersion);
			}
		}
	}
	
	public static boolean __block_prop() {
		BoolRef r = null;
		boolean toRet = (r = blockPropagate.get()).flag;
		r.flag = true;
		return toRet;
	}
	
	public static void __restore_prop(boolean b) {
		blockPropagate.get().flag = b;
	}

	static ReentrantReadWriteLock getObjectLock(Object obj) {
		return objectLocks[System.identityHashCode(obj) % N_LOCKS];
	}
}
