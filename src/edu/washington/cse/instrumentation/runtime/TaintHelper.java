package edu.washington.cse.instrumentation.runtime;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import sun.misc.Signal;
import edu.columbia.cs.psl.phosphor.Configuration;
import edu.columbia.cs.psl.phosphor.TaintCombiner;
import edu.columbia.cs.psl.phosphor.runtime.Taint;
import edu.columbia.cs.psl.phosphor.struct.ControlTaintTagStack;
import edu.columbia.cs.psl.phosphor.struct.TaintedBooleanWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedByteWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedCharWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedDoubleWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedFloatWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedIntWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedLongWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedPrimitiveWithObjTag;
import edu.columbia.cs.psl.phosphor.struct.TaintedShortWithObjTag;
import edu.washington.cse.instrumentation.runtime.TaintPropagation.BoolRef;

/**
 * Runtime that makes the transactions happen
 * 
 * For experts only
 * 
 * XXX(jtoman): total god class
 * @author jtoman
 *
 */
public class TaintHelper {
	private static boolean LOG_FAILURES = System.getProperty("staccato.logfile") != null;
	private static final boolean withRandomPause;
	private static final BlockingQueue<Throwable> queue = new LinkedBlockingQueue<Throwable>(10000);
	private static final Map<String, String> linGroups = new HashMap<>();
	private static final Map<String, String[]> linGroupMembers = new HashMap<>();
	private static final Set<String> ignoredProperties = new HashSet<>();
	private static final boolean RECORD_TAGS = Boolean.parseBoolean(System.getProperty("staccato.record", "false"));
	private static final boolean CONFIG_AS_TAINT = Boolean.parseBoolean(System.getProperty("staccato.config-as-taint", "false"));
	
	static {
		Configuration.taintCombiner = new StaccatoTaintCombiner();
//		System.out.println(CONFIG_AS_TAINT);
		int linGroupCounter = 0;
		if(System.getProperty("staccato.lin-groups") != null) {
			String[] groups = System.getProperty("staccato.lin-groups").split(";");
			for(String g : groups) {
				String[] props = g.split(",");
				int groupNum = linGroupCounter++;
				String linGroupName = "$$StaccatoGroup-" + groupNum;
				for(String p : props) {
					if(linGroups.containsKey(p)) {
						throw new IllegalArgumentException(p + " is already contained in a property group");
					}
					linGroups.put(p, linGroupName);
				}
				linGroupMembers.put(linGroupName, props);
			}
		}
		
		if(System.getProperty("staccato.ignored-props") != null) {
			for(String s : System.getProperty("staccato.ignored-props").split(",")) {
				ignoredProperties.add(s);
			}
		}
		
		if(System.getProperty("staccato.record-mem") != null) {
			final String memFile = System.getProperty("staccato.mem-file");
			final Thread t = new Thread() {
				@Override
				public void run() {
					try{
						MemoryMXBean memoryBean = sun.management.ManagementFactoryHelper.getMemoryMXBean();
						@SuppressWarnings("resource")
						BufferedWriter w = new BufferedWriter(new FileWriter(new File(memFile)));
						while(true) {
							memoryBean.gc();
							long l = memoryBean.getHeapMemoryUsage().getUsed();
							w.write(l + "");
							w.newLine();
							w.flush();
							Thread.sleep(1000);
						}
					} catch(IOException e) {
						System.out.println("FATAL: failed to open memory recording file");
						e.printStackTrace();
						return;
					} catch(InterruptedException e) { /* do nothing jvm shutting down */ }
				}
			};
			t.setDaemon(true);
			sun.misc.Signal.handle(new sun.misc.Signal("USR2"), new sun.misc.SignalHandler() {
				@Override
				public void handle(Signal arg0) {
					t.start();
				}
			});
		}
		
		withRandomPause = Boolean.parseBoolean(System.getProperty("staccato.random-pause", "false"));
		
		if(LOG_FAILURES) {
			Runnable r = new Runnable() {
				HashSet<String> seenMessages = new HashSet<>();
				
				@Override
				public void run() {
					try(FileOutputStream fos = new FileOutputStream(new File(System.getProperty("staccato.logfile")), false)) {
						PrintStream ps = new PrintStream(fos);
						ArrayList<Throwable> coll = new ArrayList<>(10000);
						while(true) {
							int a = queue.drainTo(coll);
							if(a == 0) {
								Throwable t;
								try {
									t = queue.take();
								} catch (InterruptedException e) {
									return;
								}
								coll.add(t);
								queue.drainTo(coll);
							}
							for(Throwable t : coll) {
								if(seenMessages.contains(t.getMessage())) {
									continue;
								}
								t.printStackTrace(ps);
								seenMessages.add(t.getMessage());
							}
							coll.clear();
						}
					} catch(IOException e) {
						throw new RuntimeException("Failed to open log-file", e);
					}
				}
				
				public void run$$PHOSPHORTAGGED(ControlTaintTagStack o) {
					this.run();
				}
				
			};
			
			Thread writeThread = new Thread(r);
			writeThread.setName("Logging Thread");
			writeThread.setDaemon(true);
			writeThread.start();
		}
		
		if(RECORD_TAGS) {
			final int LINE_SIZE = 15;
			
			sun.misc.Signal.handle(new sun.misc.Signal("USR2"), new sun.misc.SignalHandler() {
				private void dumpSet(Set<String> s) {
					Iterator<String> it = s.iterator();
					StringBuilder sb = new StringBuilder();
					while(it.hasNext()) {
						int i = 0;
						while(it.hasNext() && i < LINE_SIZE) {
							String e = it.next();
							if(i != 0) {
								sb.append(", ");
							}
							sb.append('\'').append(e).append('\'');
							i++;
						}
						System.out.println(sb.toString());
						sb.setLength(0);
					}
				}
				
				@Override
				public void handle(Signal arg0) {
					System.out.println("--- ALL PROPS ---");
					dumpSet(allProps);
					System.out.println("--- WRITE PROPS ---");
					dumpSet(writeProps);
					System.out.println("--- STRICT CF ---");
					dumpSet(strictCF);
					System.out.println("--- STRICT PROPS ---");
					dumpSet(strictProp);
					System.out.println("--- LINEAR CF ---");
					dumpSet(linearCF);
					System.out.println("--- LINEAR PROPS ---");
					dumpSet(linearProp);
					/*					System.out.println("--- UPDATE PROPS ---");
										dumpSet(updateProps);*/
				}
			});
		}
	}
	private TaintHelper() {}
	
	private static final class StaccatoTaintCombiner implements TaintCombiner {
		@Override
		public void combineTagsOnObject(Object o, ControlTaintTagStack stack) {
			if(!TaintPropagation.isTaintTarget(o)) {
				return;
			}
			if(stack.taint == null) {
				return;
			}
			TaintPropagation.propagateTaint(o, stack.taint);
		}

		@Override
		public void combineTagsInPlace(Object arg0, Taint arg1) {
			TaintPropagation.propagateTaint(arg0, arg1);
		}

		@Override
		public Taint combineTags(Taint t1, Taint t2) {
			if(t2 == null) {
				return t1;
			} else if(t1 == null) {
				return t2;
			} else if(t1.lbl == null && t1.hasNoDependencies()) {
				return t2;
			} else if(t2.lbl == null && t2.hasNoDependencies()) {
				return t1;
			}
			assert t1 != null && t2 != null;
			StringIntHashMap lbl1 = (StringIntHashMap) t1.lbl;
			StringIntHashMap lbl2 = (StringIntHashMap) t2.lbl;
			assert t2.hasNoDependencies() && t1.hasNoDependencies();
			if(lbl1 == null || lbl1.isEmpty()) {
				return t2;
			} else if(lbl2 == null || lbl2.isEmpty()) {
				return t1;
			} else {
				StringIntHashMap newLabel = new StringIntHashMap(lbl1);
				TaintPropagation.mergeTaint(newLabel, lbl2);
				return new Taint(newLabel);
			}
		}

		private StringIntHashMap combineTagStack(ControlTaintTagStack tags) {
			/*Node<Taint> tList = tags.taint.dependencies.getFirst();
			assert tList != null;
			StringIntHashMap toRet = null;
			while(tList != null) {
				Taint t = tList.entry;
				assert t != null;
				assert t.hasNoDependencies();
				if(t.lbl != null) {
					if(toRet == null) {
						toRet = new StringIntHashMap((StringIntHashMap)t.lbl);
					} else {
						TaintPropagation.mergeTaint(toRet, (StringIntHashMap)t.lbl);
					}
				}
				tList = tList.next;
			}
			return toRet;*/
			throw new UnsupportedOperationException();
//			return new StringIntHashMap();
		}

		@Override
		public Taint combineTags(Taint t1, ControlTaintTagStack tags) {
			StringIntHashMap s = null;
			if((t1 == null || t1.lbl == null) && tags.isEmpty()) {
				return null;
			} else if((t1 == null || t1.lbl == null)) {
				assert tags.taint.lbl == null;
				return new Taint(combineTagStack(tags));
			} else if(tags.isEmpty()) {
				return t1;
			}
			s = combineTagStack(tags);
			TaintPropagation.mergeTaint(s, ((StringIntHashMap)t1.lbl));				
			return new Taint(s);
		}
	}

	private static class LockSnapshot {
		final ReentrantReadWriteLock lock;
		final int rCount;
		public LockSnapshot(ReentrantReadWriteLock l) {
			this.lock = l;
			this.rCount = l.getReadHoldCount();
		}
	}

	private static class PropLockState {
		final List<LockSnapshot> locks;
		final Set<String> lockedProps;
		
		StringIntHashMap taint;
		
		// for transaction support
		int ctxt;
		private PropLockState(boolean inTx) {
			this.locks = new ArrayList<>();
			this.lockedProps = new HashSet<>();
			this.ctxt = inTx ? 2 : 0;
			this.taint = null; 
		}
		
		private PropLockState(int a) {
			this.locks = new ArrayList<>();
			this.lockedProps = new HashSet<>();
			this.ctxt = a;
			this.taint = null; 
		}

		private PropLockState() {
			this(0);
		}
		
		@Override
		public String toString() {
			return "PropLockState [lockedProps=" + lockedProps + ", taint=" + taint
					+ ", ctxt=" + ctxt + "]";
		}
	}
	
	private static ThreadLocal<ObjectSet> repairSet = new ThreadLocal<ObjectSet>() {
		@Override
		protected ObjectSet initialValue() {
			return new ObjectSet();
		}

		@SuppressWarnings("unused")
		protected Object initialValue$$PHOSPHORTAGGED(ControlTaintTagStack o) {
			return initialValue();
		}
				
	};
	
	private static ThreadLocal<List<PropLockState>> tLocks = new ThreadLocal<List<PropLockState>>() {
		@Override
		protected List<PropLockState> initialValue() {
			return new ArrayList<>();
		}

		@SuppressWarnings("unused")
		protected Object initialValue$$PHOSPHORTAGGED(ControlTaintTagStack o) {
			return initialValue();
		}
	};
	
	public static void startUpdateSingle(String s) {
		if(s == null) {
			return;
		}
		AtomicInteger i = updatingProps.get(s);
		if(i == null) {
			updatingProps.putIfAbsent(s, new AtomicInteger(0));
			i = updatingProps.get(s);
		}
		i.getAndIncrement();
	}
	
	public static void endUpdateSingle(String s) {
		if(s == null) {
			return;
		}
		AtomicInteger i = updatingProps.get(s);
		if(i != null) {
			i.decrementAndGet();
		}
	}
	
	public static void startUpdate(String... args) {
		for(String s : args) {
			startUpdateSingle(s);
		}
	}
	
	public static void endUpdate(String... args) {
		for(String s : args) {
			endUpdateSingle(s);
		}
	}
	
	private static ConcurrentHashMap<String, AtomicInteger> updatingProps = new ConcurrentHashMap<>();
	
	private static interface KVAbstraction<T> {
		public String setValue(String key, String value, T abstraction);
		public String deleteValue(String key, T abstraction);
		public String getValue(String key, T abstraction);
		public boolean hasValue(String key, T abstraction);
	}
	
	private static final KVAbstraction<Properties> propertyAbstraction = new KVAbstraction<Properties>() {
		@Override
		public String setValue(String key, String value, Properties abstraction) {
			Object old = abstraction.put(key, value);
			return (String)old;
		}
		@Override
		public String deleteValue(String key, Properties abstraction) {
			return (String)abstraction.remove(key);
		}
		
		@Override
		public String getValue(String key, Properties abstraction) {
			return abstraction.getProperty(key);
		}
		
		@Override
		public boolean hasValue(String key, Properties abstraction) {
			return abstraction.containsKey(key);
		}
	};
	
	private static volatile boolean WRITE_RECORD_PAUSED = false;
	
	public static void pauseWriteRecord() {
		WRITE_RECORD_PAUSED = true;
	}
	
	public static void unpauseWriteRecord() {
		WRITE_RECORD_PAUSED = false;
	}
	
	private static final KVAbstraction<Map<String,String>> mapAbstraction = new KVAbstraction<Map<String,String>>() {
		@Override
		public String setValue(String key, String value, Map<String,String> abstraction) {
			return abstraction.put(key, value);
		}
		
		@Override
		public String deleteValue(String key, Map<String, String> abstraction) {
			 return abstraction.remove(key);
		}
		
		@Override
		public String getValue(String key, Map<String, String> abstraction) {
			return abstraction.get(key);
		}
		
		@Override
		public boolean hasValue(String key, Map<String, String> abstraction) {
			return abstraction.containsKey(key);
		}
	};
	
	private static boolean isPropBlocked(String s) {
		AtomicInteger i = updatingProps.get(s);
		if(i == null) { return false; }
		return i.get() > 0;
	}
	
	public static class MockProxy {
		public ReentrantReadWriteLock getPropLock(String prop) {
			return TaintHelper.getPropLock(prop);
		}
		
		public void acquireLocks(String... props) {
			openState();
			PropLockState pState = getPropState();
			StringIntHashMap m = new StringIntHashMap();
			for(String p : props) {
				m.add(p, 0);
			}
			acquirePropLocks(pState, m, false);
		}
		
		public void reset() {
			epochCounter = DEFAULT_VALUE_VERSION + 1;
			epochMap.clear();
		}
		
		public void releaseLocks() {
			popState();
		}
		
		private MockProxy() { }
	}
	
	@SuppressWarnings("unused")
	private static final MockProxy mock = new MockProxy();

	/*
	 * Property state
	 */
	
	private static final Map<String, Integer> epochMap = new HashMap<>();
	private static final Object LOCK_MONITOR = new Object();
	private static final Object MAP_MONITOR = new Object();
	private static final Object TX_MONITOR = new Object();
	private static final ReentrantLock updateLock = new ReentrantLock();
	private static final int DEFAULT_VALUE_VERSION = 1;
	private static int epochCounter = DEFAULT_VALUE_VERSION + 1;
	private static final Map<String, ReentrantReadWriteLock> locks = new HashMap<>();
	private static final boolean strictMode = System.getProperty("staccato.strict") != null;

	private static void acquireWriteLock(ReentrantReadWriteLock pLock) {
		if(strictMode) {
			boolean locked = pLock.writeLock().tryLock();
			if(!locked) {
				throw new ConcurrentModificationException(); // XXX(jtoman): bad exception
			}
		} else {
			pLock.writeLock().lock();
		}
	}
	
	/*
	 * GENERIC PROPERTY MANIPULATION
	 */
	
	private static <U> String setNewProp(String propName, String property, U kv, KVAbstraction<U> kvAccess, boolean returnNew) {
		if(ignoredProperties.contains(propName)) {
			synchronized(MAP_MONITOR) {
				String toRet = kvAccess.setValue(propName, property, kv);
				if(returnNew) {
					return property;
				} else {
					return toRet;
				}
			}
		}
		String toReturn;
		float waitFactor = 0.0f;
		synchronized(TX_MONITOR) {
			String trackingKey = propName;
			if(linGroups.containsKey(propName)) {
				trackingKey = linGroups.get(propName);
			}
			if(RECORD_TAGS && !WRITE_RECORD_PAUSED) {
				writeProps.add(trackingKey);
			}
			String canonPropKey = trackingKey.intern();
			boolean setOnProp = CONFIG_AS_TAINT;
			if(getObjectTaint(property) != null) {
//				throw new RuntimeException("Most unusual! The property you're trying to set already has taint!!!");
				setOnProp = false;
			}
			// XXX(jtoman): assume these strings are just always interned
			if(property == "true" || property == "false") {
				setOnProp = false;
			}
			StringIntHashMap parentMap = new StringIntHashMap();
			ReentrantReadWriteLock pLock;
			synchronized(LOCK_MONITOR) {
				pLock = getPropLock(canonPropKey);
				if(pLock.getReadHoldCount() > 0) {
					throw new ConcurrentModificationException("Attempt to write property " + propName + " while holding read lock");
				}
			}
			acquireWriteLock(pLock);
			try {
				synchronized(MAP_MONITOR) {
					if(withRandomPause && CONFIG_AS_TAINT) {
						waitFactor = sleep.nextFloat();
					}
					epochCounter++;
					epochMap.put(canonPropKey, epochCounter);
					parentMap.add(canonPropKey, epochCounter);
					String toSet = new String(property.toCharArray());
					if(setOnProp) {
						((TaintCarry)(Object)property)._staccato_set_taint(parentMap);
					}
					((TaintCarry)(Object)toSet)._staccato_set_taint(parentMap);
					String toRet = kvAccess.setValue(propName, toSet, kv);
					if(returnNew) {
						toReturn = toSet;
					} else {
						toReturn = toRet;
					}
				}
			} finally {
				pLock.writeLock().unlock();
			}
		}
		if(withRandomPause && CONFIG_AS_TAINT) {
			try {
				Thread.sleep((long) (50 * waitFactor));
			} catch (InterruptedException e) { }
		}
		return toReturn;
	}
	
	private static <U> String casProp(String propName, String property, U kv, KVAbstraction<U> kvAccess) {
		synchronized(TX_MONITOR) {
			if(!kvAccess.hasValue(propName, kv)) {
				return setNewProp(propName, property, kv, kvAccess, false);
			}
			String currValue = kvAccess.getValue(propName, kv);
			if(currValue == property ||
				(property != null && property.equals(currValue))) {
				return currValue;
			}
			return setNewProp(propName, property, kv, kvAccess, false);
		}
	}
	
	private static <U> String deleteProp(String propName, U kvAbs, KVAbstraction<U> kvAccess) {
		synchronized(TX_MONITOR) {
			String canonPropName = propName.intern();
			ReentrantReadWriteLock pLock;
			synchronized(LOCK_MONITOR) {
				pLock = getPropLock(canonPropName);
				if(pLock.getReadHoldCount() > 0) {
					throw new ConcurrentModificationException("Attempt to delete property " + propName + " while holding read lock");
				}
			}
			acquireWriteLock(pLock);
			try {
				synchronized(MAP_MONITOR) {
					epochCounter++;
					epochMap.put(canonPropName, epochCounter);
					return kvAccess.deleteValue(canonPropName, kvAbs);
				}
			} finally {
				pLock.writeLock().unlock();
			}
		}
	}
	
	private static final Random sleep = new Random();
	
	private static <U> String getProp(String propName, String defaultVal, U kvAbs, KVAbstraction<U> kvAccess) {
		if(RECORD_TAGS) {
			allProps.add(propName);
		}
		float sleepFactor = 0.0f;
		try {
			synchronized(MAP_MONITOR) {
				if(withRandomPause) {
					sleepFactor = sleep.nextFloat();
				}
				String value = kvAccess.getValue(propName, kvAbs);
				if(value != null) {
					return value;
				} else if(defaultVal != null) {
					/*
					 * a property can be in the epochMap but not in the propMap if a property deletion was done
					 */
					String toRet = new String(defaultVal.toCharArray());
					int e = DEFAULT_VALUE_VERSION;
					if(epochMap.containsKey(propName)) {
						e = epochMap.get(propName);
					}
					StringIntHashMap taintData = new StringIntHashMap();
					taintData.add(propName.intern(), e);
					((TaintCarry)(Object)toRet)._staccato_set_taint(taintData);
					return toRet;
				} else {
					return null;
				}
			}
		} finally {
			if(withRandomPause) {
				try {
					Thread.sleep((long) (50 * sleepFactor));
				} catch (InterruptedException e) { }
			}
		}
	}
	
	/*
	 * MAP<STRING, STRING> implementation
	 */
	
	private static String setNewProp(String propName, String property, Map<String, String> propMap, boolean returnNew) {
		return setNewProp(propName, property, propMap, mapAbstraction, returnNew);
	}
	
	public static String setNewProp(String propName, String property, Map<String, String> propMap) {
		return setNewProp(propName, property, propMap, false);
	}
	
	public static String updateProp(String propName, String property, Map<String, String> propMap) {
		return setNewProp(propName, property, propMap, true);
	}
	
	public static String deleteProp(String propName, Map<String, String> propMap) {
		return deleteProp(propName, propMap, mapAbstraction);
	}
	
	public static String getProp(String propName, Map<String, String> propMap, String defaultVal) {
		return getProp(propName, defaultVal, propMap, mapAbstraction);
	}
	
	public static String getProp(String propName, Map<String, String> propMap) {
		return getProp(propName, propMap, null);
	}
	
	public static String casProp(String propName, String property, Map<String, String> propmap) {
		return casProp(propName, propName, propmap, mapAbstraction);
	}
	
	/*
	 * PROPERTIES IMPLEMENTATION
	 */
	
	private static String setNewProp(String propName, String property, Properties prop, boolean returnNew) {
		return setNewProp(propName, property, prop, propertyAbstraction, returnNew);
	}
	
	public static String setNewProp(String propName, String property, Properties prop) {
		return setNewProp(propName, property, prop, false);
	}
	
	public static String updateProp(String propName, String property, Properties prop) {
		return setNewProp(propName, property, prop, true);
	}
	
	public static String deleteProp(String propName, Properties prop) {
		return deleteProp(propName, prop, propertyAbstraction);
	}
	
	public static String getProp(String propName, Properties props) {
		return getProp(propName, props, null);
	}
	
	public static String getProp(String propName, Properties props, String defaultValue) {
		return getProp(propName, defaultValue, props, propertyAbstraction);
	}
	
	public static String casProp(String propName, String property, Properties prop) {
		return casProp(propName, property, prop, propertyAbstraction);
	}
	
	public static void loadProperties(FileInputStream stream, Properties props) throws IOException {
		synchronized(TX_MONITOR) {
			Properties p = new Properties();
			p.load(stream);
			Map<String, String> toSet = new HashMap<>();
			for(Entry<Object, Object> kv : p.entrySet()) {
				if(!(kv.getKey() instanceof String)) {
					throw new IllegalArgumentException();
				}
				if(!(kv.getValue() instanceof String)) {
					throw new IllegalArgumentException();
				}
				String key = (String)kv.getKey();
				String value = (String)kv.getValue();
				if(props.containsKey(key) && props.getProperty(key).equals(value)) {
					continue;
				}
				toSet.put(key, value);
			}
			if(toSet.isEmpty()) {
				return;
			}
			ArrayList<String> setProps = new ArrayList<>(toSet.keySet());
			Collections.sort(setProps);
			ArrayList<ReentrantReadWriteLock> locks = new ArrayList<>(setProps.size());
			synchronized(LOCK_MONITOR) {
				for(String prop : setProps) {
					locks.add(TaintHelper.getPropLock(prop));
				}
			}
			boolean resetInterrupt = false;
			try {
				resetInterrupt = acquireTransactionLocks(locks);
				Properties toMerge = new Properties();
				synchronized(MAP_MONITOR) {
					for(Map.Entry<String, String> kv : toSet.entrySet()) {
						int newEpoch = epochCounter++;
						String propKey = kv.getKey();
						if(linGroups.containsKey(propKey)) {
							propKey = linGroups.get(propKey);
						}
						propKey = propKey.intern();
						String val = new String(kv.getValue());
						StringIntHashMap em = new StringIntHashMap(1);
						em.add(propKey, newEpoch);
						epochMap.put(propKey, newEpoch);
						((TaintCarry)(Object)val)._staccato_set_taint(em);
						toMerge.setProperty(kv.getKey(), val);
					}
					props.putAll(toMerge);
				}
			} finally {
				for(ReentrantReadWriteLock rwl : locks) {
					if(rwl.isWriteLockedByCurrentThread()) {
						rwl.writeLock().unlock();
					}
				}
				if(resetInterrupt) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}
	
	private static <U> String setNewProp(String key, String value, KVAbstraction<U> kvAccess, U abstraction, Object... objs) {
		float sleepFactor = 0.0f;
		String toRet = null;
		synchronized(TX_MONITOR) {
			ReentrantReadWriteLock pLock = getPropLock(key);
			StringIntHashMap eMap = new StringIntHashMap();
			String canonKey = key.intern();
			int[] locks = new int[objs.length];
			int nLocks = 0;
			for(int i = 0; i < objs.length; i++) {
				 if(objs[i] == null) {
					 continue;
				 }
				 if(TaintPropagation.isPrimitiveWrapper(objs)) {
					 throw new RuntimeException("Cannot use primitive objects");
				 }
				 locks[nLocks++] = System.identityHashCode(objs) % TaintPropagation.N_LOCKS;
			}
			Arrays.sort(locks, 0, nLocks);
			acquireWriteLock(pLock);
			int heldLock = 0;
			try {
				for(heldLock = 0; heldLock < nLocks; heldLock++) {
					TaintPropagation.objectLocks[locks[heldLock]].writeLock().lock();
				}
				int newEpoch;
				synchronized(MAP_MONITOR) {
					if(withRandomPause) {
						sleepFactor = sleep.nextFloat();
					}
					newEpoch = epochCounter++;
					eMap.add(canonKey, newEpoch);
					((TaintCarry)(Object)value)._staccato_set_taint(eMap);
					toRet = kvAccess.setValue(canonKey, value, abstraction);
					epochMap.put(canonKey, newEpoch);
				}
				for(Object obj : objs) {
					if(!(obj instanceof TaintCarry)) {
						continue;
					}
					StringIntHashMap em = unsafeGetObjectTaint((TaintCarry) obj);
					if(em == null) {
						em = new StringIntHashMap(eMap);
					} else {
						em = new StringIntHashMap(em);
						em.add(canonKey, newEpoch);
					}
					unsafeSetObjectTaint((TaintCarry) obj, em);
				}
			} finally {
				for(int i = 0; i < heldLock; i++) {
					TaintPropagation.objectLocks[locks[i]].writeLock().unlock();
				}
				pLock.writeLock().unlock();
			}
		}
		if(withRandomPause) {
			try {
				Thread.sleep((long)(50 * sleepFactor));
			} catch(InterruptedException e) { 
				Thread.currentThread().interrupt();
			}
		}
		return toRet;
	}
	
	private static StringIntHashMap unsafeGetObjectTaint(TaintCarry o) {
		BoolRef bref = TaintPropagation.blockPropagate.get();
		boolean saved = bref.flag;
		bref.flag = true;
		try {
			return (StringIntHashMap)o._staccato_get_taint();
		} finally {
			bref.flag = saved;
		}
	}
	
	private static void unsafeSetObjectTaint(TaintCarry o, StringIntHashMap t) {
		BoolRef bref = TaintPropagation.blockPropagate.get();
		boolean saved = bref.flag;
		bref.flag = true;
		try {
			o._staccato_set_taint(t);
		} finally {
			bref.flag = saved;
		}
	}
	
	public static String setNewProp(String key, String value, Map<String, String> map, Object... objs) {
		return setNewProp(key, value, mapAbstraction, map, objs);
	}
	
	public static String setNewProp(String key, String value, Properties prop, Object... objs) {
		return setNewProp(key, value, propertyAbstraction, prop, objs);
	}
	
	private static boolean acquireTransactionLocks(List<ReentrantReadWriteLock> toLock) {
		assert Thread.holdsLock(TX_MONITOR);
		boolean resetInterrupt = false;
		lock_loop: while(true) {
			for(int i = 0; i < toLock.size(); i++) {
				ReadWriteLock pLock = toLock.get(i);
				boolean acquired = pLock.writeLock().tryLock();
				if(!acquired) {
					for(int j = 0; j < i; j++) {
						toLock.get(j).writeLock().unlock();
					}
					if(strictMode) {
						throw new ConcurrentModificationException(); // XXX(jtoman): fix this
					}
					try {
						Thread.sleep(30);
					} catch (InterruptedException e) {
						resetInterrupt = true;
					}
					continue lock_loop;
				}
			}
			break;
		}
		return resetInterrupt;
	}
	
	public static void setNewProps(Map<String, String> newProperties, Map<String, String> propMap) {
		List<ReentrantReadWriteLock> toLock = new ArrayList<>();
		// Do acquire in order
		ArrayList<String> toLockProps = new ArrayList<>(newProperties.keySet());
		Collections.sort(toLockProps);
		synchronized(LOCK_MONITOR) {		
			for(String pName : toLockProps) {
				toLock.add(getPropLock(pName));
			}
		}
		boolean resetInterrupt = false;
		synchronized (TX_MONITOR) {
			try {
				resetInterrupt = acquireTransactionLocks(toLock);
				synchronized(MAP_MONITOR) {
					for(Map.Entry<String, String> kv : newProperties.entrySet()) {
						String canonPropName = kv.getKey().intern();
						epochCounter++;
						epochMap.put(canonPropName, epochCounter);
						StringIntHashMap parentMap = new StringIntHashMap();
						parentMap.add(canonPropName, epochCounter);
						String propCopy = new String(kv.getValue());
						((TaintCarry)(Object)propCopy)._staccato_set_taint(parentMap);
						propMap.put(canonPropName, propCopy);
					}
				}
			} finally {
				for(ReentrantReadWriteLock toRevert : toLock) {
					if(toRevert.writeLock().isHeldByCurrentThread()) {
						toRevert.writeLock().unlock();
					}
				}
				if(resetInterrupt) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private static PropLockState getPropState() {
		List<PropLockState> state = tLocks.get();
		assert state.size() != 0;
		return state.get(state.size() - 1);
	}
	
	private static void acquirePropLocks(PropLockState pState, StringIntHashMap prop, boolean isTxAcquire) {
		boolean inTx = pState.ctxt == 2;
		List<ReentrantReadWriteLock> toAcquire = null;
		
		List<String> l = new ArrayList<>(prop.m_entryCount);
		for(int i = 0; i < prop.m_entryCount; i++) {
			l.add(prop.m_keyTable[prop.m_index[i]]);
		}
		// this acquires in order
		Collections.sort(l);
		Iterator<String> it = l.iterator();
		while(it.hasNext()) {
			String str = it.next();
			if(pState.lockedProps.contains(str)) {
				it.remove();
				continue;
			}
			if(inTx && !isTxAcquire) {
				// TODO(jtoman): better error reporting here
				System.err.println("STACCATO: In transaction but acquiring new locks: " + l);
			}
			if(toAcquire == null) {
				toAcquire = new ArrayList<>();
			}
			synchronized(LOCK_MONITOR) {
				toAcquire.add(getPropLock(str));
			}
		}
		if(toAcquire != null) {
			for(int i = 0; i < toAcquire.size(); i++) {
				ReentrantReadWriteLock pLock = toAcquire.get(i);
				pState.locks.add(new LockSnapshot(pLock));
				pLock.readLock().lock();
			}
			pState.lockedProps.addAll(l);
		}
	}
	
	private static String errorString(StringIntHashMap props, Set<String> failingProps) {
		StringBuilder sb = new StringBuilder();
		sb.append("Epoch violation detected: {").append(props).append("}, bad props: ").append(failingProps);
		return sb.toString();
	}

	private static String repairString(StringIntHashMap props, Set<String> failingProps) {
		StringBuilder sb = new StringBuilder();
		sb.append("Epoch violation repaired: {").append(props).append("}, bad props: ").append(failingProps);
		return sb.toString();
	}
	
	/* hold onto your butts */
	private static abstract class UpdateReturn<U extends TaintedPrimitiveWithObjTag> { 
		final void update(U container, Object o) {
			StringIntHashMap props = TaintPropagation.getTaint(o);
			if(props == null) {
				container.taint = null;
			} else {
				container.taint = new Taint(props);
			}
			updateValue(container, o);
		}
		protected abstract void updateValue(U container, Object o);
	}
	
	private static final UpdateReturn<TaintedIntWithObjTag> intUpdate = new UpdateReturn<TaintedIntWithObjTag>() {
		@Override
		protected void updateValue(TaintedIntWithObjTag container, Object o) {
			container.val = (Integer)o;
		}
	};
	
	private static final UpdateReturn<TaintedFloatWithObjTag> floatUpdate = new UpdateReturn<TaintedFloatWithObjTag>() {
		@Override
		protected void updateValue(TaintedFloatWithObjTag container, Object o) {
			container.val = (Float)o;
		}
	};
	
	private static final UpdateReturn<TaintedByteWithObjTag> byteUpdate = new UpdateReturn<TaintedByteWithObjTag>() {
		@Override
		protected void updateValue(TaintedByteWithObjTag container, Object o) {
			container.val = (Byte)o;
		}
	};
	
	private static final UpdateReturn<TaintedBooleanWithObjTag> booleanUpdate = new UpdateReturn<TaintedBooleanWithObjTag>() {
		@Override
		protected void updateValue(TaintedBooleanWithObjTag container, Object o) {
			container.val = (Boolean)o;
		}
	};
	
	private static final UpdateReturn<TaintedShortWithObjTag> shortUpdate = new UpdateReturn<TaintedShortWithObjTag>() {
		@Override
		protected void updateValue(TaintedShortWithObjTag container, Object o) {
			container.val = (Short)o;
		}
	};
	
	private static final UpdateReturn<TaintedCharWithObjTag> charUpdate = new UpdateReturn<TaintedCharWithObjTag>() {
		@Override
		protected void updateValue(TaintedCharWithObjTag container, Object o) {
			container.val = (Character)o;
		}
	};
	
	private static final UpdateReturn<TaintedLongWithObjTag> longUpdate = new UpdateReturn<TaintedLongWithObjTag>() {
		@Override
		protected void updateValue(TaintedLongWithObjTag container, Object o) {
			container.val = (Long)o;
		}
	};
	
	private static final UpdateReturn<TaintedDoubleWithObjTag> doubleUpdate = new UpdateReturn<TaintedDoubleWithObjTag>() {
		@Override
		protected void updateValue(TaintedDoubleWithObjTag container, Object o) {
			container.val = (Double)o;
		}
	};
	
	
	private static boolean skipCheckForAccess(PropLockState pState) {
		if(pState.ctxt != 4) {
			return false;
		}
		List<PropLockState> ts = tLocks.get();
		if(ts.size() == 1) {
			return true;
		}
		PropLockState prevState = tLocks.get().get(ts.size() - 2);
		if(prevState.ctxt == 0) {
			return true;
		}
		return false;
	}
	
	private static <U extends TaintedPrimitiveWithObjTag> void doPrimitiveCheck(Object source, Taint t, Object o, String fieldName, 
			ReentrantReadWriteLock rwl, UpdateReturn<U> react, U container) {
		if(t == null) {
			return;
		}
		PropLockState pState = getPropState();
		if(skipCheckForAccess(pState)) {
			return;
		}
		StringIntHashMap props = (StringIntHashMap) t.lbl;
		if(RECORD_TAGS) {
			addTaintToSet(strictProp, props);
		}
		acquirePropLocks(pState, props, false);
		Set<String> failingStrings = LOG_FAILURES ? new HashSet<String>() : null;
		boolean checkResult = checkEpoch(props, failingStrings);
		if(checkResult) {
			return;
		}
		EpochViolationException e = new EpochViolationException(errorString(props, failingStrings));
		Object toRet = null;
		Method m = null;
		if(source instanceof Class<?>) {
			Class<?> theKlass = (Class<?>)source;
			try {
				m = theKlass.getMethod("__staccato_update_field_static", Set.class, String.class, Object.class, RuntimeException.class);
				
			} catch (NoSuchMethodException | SecurityException | IllegalArgumentException e1) {
				handleError(e); // really bad control flow, shame on me
			}
		}
		if(source instanceof StaccatoFieldRepair || m != null) {
			int lockCount = -1;
			try {
				updateLock.lock();
				if(rwl != null) {
					lockCount = rwl.getWriteHoldCount();
					rwl.writeLock().lock();
				}
				try {
					if(m != null) {
						toRet = m.invoke(null, failingStrings, fieldName, o, e);
					} else {
						toRet = ((StaccatoFieldRepair) source).__staccato_repair_field(failingStrings, fieldName, o, e);
					}
					if(RECORD_TAGS) {
						handleError(new EpochViolationException(repairString(props, failingStrings)));
					}
					react.update(container, toRet);
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e1) {
					handleError(e);
				} catch(EpochViolationException e2) {
					handleError(e);
				}
			} finally {
				if(rwl != null && rwl.getWriteHoldCount() > lockCount) {
					rwl.writeLock().unlock();
				}
				updateLock.unlock();
			}
		} else if(!isRepair()) {
			handleError(e);
		}
	}
	
	public static TaintedIntWithObjTag checkFieldTaint(Object source, Taint t,
			int v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedIntWithObjTag.valueOf(null, v);
		}
		TaintedIntWithObjTag ret = new TaintedIntWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, intUpdate, ret);
		return ret;
	}

	public static TaintedCharWithObjTag checkFieldTaint(Object source, Taint t,
			char v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedCharWithObjTag.valueOf(null, v);
		}
		TaintedCharWithObjTag ret = new TaintedCharWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, charUpdate, ret);
		return ret;
	}

	public static TaintedBooleanWithObjTag checkFieldTaint(Object source,
			Taint t, boolean v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedBooleanWithObjTag.valueOf(null, v);
		}
		TaintedBooleanWithObjTag ret = new TaintedBooleanWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, booleanUpdate, ret);
		return ret;
	}

	public static TaintedShortWithObjTag checkFieldTaint(Object source, Taint t,
			short v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedShortWithObjTag.valueOf(null, v);
		}
		TaintedShortWithObjTag ret = new TaintedShortWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, shortUpdate, ret);
		return ret;
	}

	public static TaintedFloatWithObjTag checkFieldTaint(Object source, Taint t,
			float v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedFloatWithObjTag.valueOf(null, v);
		}
		TaintedFloatWithObjTag ret = new TaintedFloatWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, floatUpdate, ret);
		return ret;
	}

	public static TaintedLongWithObjTag checkFieldTaint(Object source, Taint t,
			long v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedLongWithObjTag.valueOf(null, v);
		}
		TaintedLongWithObjTag ret = new TaintedLongWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, longUpdate, ret);
		return ret;
	}

	public static TaintedDoubleWithObjTag checkFieldTaint(Object source, Taint t,
			double v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedDoubleWithObjTag.valueOf(null, v);
		}
		TaintedDoubleWithObjTag ret = new TaintedDoubleWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, doubleUpdate, ret);
		return ret;
	}

	public static TaintedByteWithObjTag checkFieldTaint(Object source, Taint t,
			byte v, String fieldName, ReentrantReadWriteLock rwl) {
		if (t == null || t.lbl == null) {
			return TaintedByteWithObjTag.valueOf(null, v);
		}
		TaintedByteWithObjTag ret = new TaintedByteWithObjTag(t, v);
		doPrimitiveCheck(source, t, v, fieldName, rwl, byteUpdate, ret);
		return ret;
	}
	
	// phew!

	public static Object checkFieldTaint(Object source, Object fieldValue, String fieldName, ReentrantReadWriteLock rwl) {
		if(!doCheck(fieldValue)) {
			return fieldValue;
		}
		if(!TaintPropagation.isTaintSource(fieldValue)) {
			return fieldValue;
		}
		PropLockState pState = getPropState();
		if(skipCheckForAccess(pState)) {
			return fieldValue;
		}
		StringIntHashMap props = getObjectTaint(fieldValue);
		if(props == null) {
			return fieldValue;
		}
		if(skipCheckForAccess(pState)) {
			System.out.println("Ignoring: " + fieldName + " because of lack of scope");
			return fieldValue;
		}
		if(RECORD_TAGS) {
			addTaintToSet(strictProp, props);
		}
		acquirePropLocks(pState, props, false);
		Set<String> failingStrings = LOG_FAILURES ? new HashSet<String>() : null;
		boolean checkResult = checkEpoch(props, failingStrings);
		if(checkResult) {
			return fieldValue;
		}
		EpochViolationException e = new EpochViolationException(errorString(props, failingStrings));
		Object toRet = fieldValue;
		Method m = null;
		if(source instanceof Class<?>) {
			Class<?> theKlass = (Class<?>)source;
			try {
				m = theKlass.getMethod("__staccato_update_field_static", Set.class, String.class, Object.class, RuntimeException.class);
				
			} catch (NoSuchMethodException | SecurityException | IllegalArgumentException e1) {
				handleError(e); // really bad control flow, shame on me
			}
		}
		if(source instanceof StaccatoFieldRepair || m != null) {
			int lockCount = -1;
			try {
				updateLock.lock();
				if(rwl != null) {
					lockCount = rwl.getWriteHoldCount();
					rwl.writeLock().lock();
				}
				startRepair(fieldValue);
				try {
					props = getObjectTaint(fieldValue);
					if(checkEpoch(props, null)) {
						return fieldValue;
					}
					if(m != null) {
						m.invoke(null, failingStrings, fieldName, fieldValue, e);
					} else {
						toRet = ((StaccatoFieldRepair) source).__staccato_repair_field(failingStrings, fieldName, fieldValue, e);
					}
					if(RECORD_TAGS) {
						handleError(new EpochViolationException(repairString(props, failingStrings)));
					}
					if(toRet == fieldValue) {
						toRet = updateTaint(props, toRet);
					}
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e1) {
					handleError(e);
				} catch(EpochViolationException e2) {
					handleError(e);
				} finally {
					endRepair(fieldValue);
				}
			} finally {
				if(rwl != null && rwl.getWriteHoldCount() > lockCount) {
					rwl.writeLock().unlock();
				}
				updateLock.unlock();
			}
		} else if(!isRepair()) {
			handleError(e);
		}
		// add this back in when I have some self-updaters
		/*
		catch(EpochViolationException eve) {
			// intentionally left blank
		}
		if(fieldValue instanceof JTaintRepair && !fieldSuccess) {
			ReentrantReadWriteLock rwl = getObjectLock(fieldValue);
			rwl.writeLock().lock();
			startRepair(fieldValue);
			try {
				((JTaintRepair)fieldValue).__jtaint_repair(failingStrings, e);
			} finally {
				endRepair(fieldValue);
				rwl.writeLock().unlock();
			}
		} else if(!fieldSuccess) {
			throw e;
		}
		*/
		return toRet;
	}

	public static void openState(int d) {
		List<PropLockState> sp = tLocks.get();
		sp.add(new PropLockState(d));
	}
	
       
	public static void openState() {
		openState(0);
	}
	
	private static void releaseLocks(PropLockState lList) {
		for(int i = lList.locks.size() - 1; i >= 0; i--) {
			LockSnapshot ls = lList.locks.get(i);
			if(ls.lock.getReadHoldCount() > ls.rCount) {
				ls.lock.readLock().unlock();
				assert ls.lock.getReadHoldCount() == ls.rCount;
			}
		}
	}

	public static void popState() {
		assert tLocks.get().size() != 0;
		List<PropLockState> stack = tLocks.get();
		PropLockState lList = stack.remove(stack.size() - 1);
		releaseLocks(lList);
//		if(stack.isEmpty()) {
		/*} else {
			PropLockState prevFrame = stack.peek();
			prevFrame.lockedProps.addAll(lList.lockedProps);
			prevFrame.locks.addAll(lList.locks);
		}*/
	}

	public static void popAccessState() {
		assert tLocks.get().size() != 0;
		List<PropLockState> stack = tLocks.get();
		PropLockState lList = stack.remove(stack.size() - 1);
		if(stack.size() == 0 || stack.get(stack.size() - 1).ctxt == 0) {
			releaseLocks(lList);
		} else {
			PropLockState prevFrame = stack.get(stack.size() - 1);
			prevFrame.lockedProps.addAll(lList.lockedProps);
			prevFrame.locks.addAll(lList.locks);
		}
	}

	private static StringIntHashMap getObjectTaint(Object o) {
		boolean needLock = false;
		ReentrantReadWriteLock objLock = null;
		needLock = !TaintPropagation.noLockType(o);
		if(needLock) {
			objLock = TaintPropagation.getObjectLock(o);
			objLock.readLock().lock();
		}
		BoolRef r = TaintPropagation.blockPropagate.get();
		boolean saved = r.flag;
		r.flag = true;
		try {
			return TaintPropagation.getTaint(o);
		} finally {
			if(objLock != null) {
				objLock.readLock().unlock();
			}
			r.flag = saved;
		}
	}
	
	private static boolean isRepair() {
		return updateLock.isHeldByCurrentThread();
	}
	
	public static void checkArgTaint(Object... sources) {
		HashSet<String> failingProps = LOG_FAILURES ? new HashSet<String>() : null;
		for(Object src : sources) {
			checkObjectTaint(src, failingProps);
		}
	}
	
	public static void checkLinTaint(Object... sources) {
		HashSet<String> failingProps = LOG_FAILURES ? new HashSet<String>() : null;
		for(Object src : sources) {
			checkLinearRead(src, failingProps);
		}
		System.out.println("WARNING: WHAT ARE YOU DOING HERE?!?!?");
		(new Exception()).printStackTrace();
	}
	
	public static void checkLinGlobTaint(Object... sources) {
		PropLockState pState = getPropState();
		HashSet<String> failingProps = LOG_FAILURES ? new HashSet<String>() : null;
		for(Object src : sources) {
			if(!TaintPropagation.isTaintSource(src)) {
				continue;
			}
			recordReadInteral(src, pState, failingProps);
			if(LOG_FAILURES) {
				failingProps.clear();
			}
		}
	}
	
	public static void checkSingleTaint(Object source) {
		checkObjectTaint(source, LOG_FAILURES ? new HashSet<String>() : null);
	}
	
	public static void checkSingleLinTaint(Object source) {
		checkLinearRead(source, null);
		System.out.println("WARNING: WHAT ARE YOU DOING HERE?!?!?");
		(new Exception()).printStackTrace();
	}
	
	private static void checkObjectTaint(Object src, HashSet<String> failingProps) {
		if(TaintPropagation.maybeTaint(src) == null) {
			return;
		}
		if(!doCheck(src)) {
			return;
		}
		StringIntHashMap props = getObjectTaint(src);
		if(props == null) {
			return;
		}
		if(RECORD_TAGS) {
			addTaintToSet(strictProp, props);
		}
		PropLockState pState = getPropState();
		acquirePropLocks(pState, props, false);
		boolean canRepair = false;
		if(src instanceof StaccatoRepair) {
			canRepair = false;
//			(failingProps == null ? (failingProps = new HashSet<String>()) : failingProps).clear();
		}
		boolean checkResult;
		checkResult = checkEpoch(props, failingProps);
		EpochViolationException e = new EpochViolationException(errorString(props, failingProps)); 
		if(!checkResult && !canRepair && !isRepair()) {
			
			handleError(e);
		} else if(!checkResult && canRepair) {
			ReentrantReadWriteLock rwl = TaintPropagation.getObjectLock(src);
			rwl.writeLock().lock();
			try {
				startRepair(src);
				((StaccatoRepair)src).__staccato_repair(failingProps, e);
				updateTaint(props, src);
			} finally {
				endRepair(src);
				rwl.writeLock().unlock();
			}
		}
	}
	
	private static final ThreadLocal<HashSet<String>> blockSet = new ThreadLocal<HashSet<String>>() {
		@Override
		protected HashSet<String> initialValue() {
			return new HashSet<>();
		};
		
		@SuppressWarnings("unused")
		protected HashSet<String> initialValue$$PHOSPHORTAGGED(ControlTaintTagStack o) {
			return initialValue();
		}
	};
	
	private static void handleError(EpochViolationException e) {
		if(LOG_FAILURES) {
			if(blockSet.get().contains(e.getMessage())) {
				return;
			}
			boolean success;
			try {
				success = queue.offer(e, 1, TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				throw e;
			}
			if(!success) {
				throw e;
			}
		} else {
			throw e;
		}
	}
	
	private static void checkLinearRead(Object source, HashSet<String> failingProps) {
		if(TaintPropagation.maybeTaint(source) == null) {
			return;
		}
		if(!doCheck(source)) {
			return;
		}
		StringIntHashMap props = getObjectTaint(source);
		if(props == null) {
			return;
		}
		boolean checkResult;
		checkResult = checkLinearizable(props, failingProps);
		EpochViolationException e = new EpochViolationException(errorString(props, failingProps)); 
		if(!checkResult && !isRepair()) {
			handleError(e);
		}
	}
	
	private static boolean checkLinearizable(StringIntHashMap taint, Set<String> failingStrings) {
		boolean res = true;
		for(int i = 0; i < taint.m_entryCount; i++) {
			int ind = taint.m_index[i];
			String key = taint.m_keyTable[ind];
//			if(isPropBlocked(key)) { continue; }
			if(taint.m_valueTable[ind] < 0) {
				if(failingStrings == null) {
					return false;
				}
				res = false;
				failingStrings.add(key);
			}
		}
		return res;	
	}
	
	private static void startRepair(Object o) {
		ObjectSet b = repairSet.get();
		assert !b.contains(o);
		b.add(o);
	}
	
	private static void endRepair(Object o) {
		ObjectSet b = repairSet.get();
		assert b.contains(o);
		b.remove(o);
	}
	
	private static boolean doCheck(Object o) {
		return !repairSet.get().contains(o);
	}
	
	private static Object updateTaint(StringIntHashMap props, Object src) {
		synchronized(MAP_MONITOR) {
			for(int i = 0; i < props.m_entryCount; i++) {
				int index = props.m_index[i];
				String prop = props.m_keyTable[index];
				props.m_valueTable[index] = epochMap.get(prop);
			}
		}
		BoolRef bRef = TaintPropagation.blockPropagate.get();
		boolean saved = bRef.flag;
		bRef.flag = true;
		try {
			return TaintPropagation.setTaint(src, props);
		} finally {
			bRef.flag = saved;
		}
	}

	private static ReentrantReadWriteLock getPropLock(String s) {
		assert Thread.holdsLock(LOCK_MONITOR);
		if(locks.containsKey(s)) {
			return locks.get(s);
		}
		ReentrantReadWriteLock l = new ReentrantReadWriteLock();
		locks.put(s, l);
		return l;
	}
	
	public static void startTransaction() {
		assert getPropState().ctxt == 1;
		getPropState().ctxt = 2;
	}

	private static boolean checkEpoch(StringIntHashMap props, Set<String> failingStrings) {
		synchronized(MAP_MONITOR) {
			boolean res = true;
			for(int i = 0; i < props.m_entryCount; i++) {
				int index = props.m_index[i];
				String key = props.m_keyTable[index];
				int value = props.m_valueTable[index];
				if(isPropBlocked(key)) { continue; }
				int v = Math.max(value, value * -1);
				if(v == DEFAULT_VALUE_VERSION && !epochMap.containsKey(key)) {
					continue;
				}
				if(epochMap.get(key) > v) {
					if(failingStrings == null) {
						return false;
					}
					res = false;
					failingStrings.add(key);
				}
			}
			return res;
		}
	}
	
	private static void recordReadInteral(Object o, PropLockState pState, HashSet<String> fProps) {
		if(updateLock.isHeldByCurrentThread()) {
			return;
		}
		StringIntHashMap objTaint = getObjectTaint(o);
		if(objTaint == null) {
			return;
		}
		if(RECORD_TAGS) {
			addTaintToSet(linearProp, objTaint);
		}
//		StringIntHashMap old = null;
//		if(LOG_FAILURES) {
//			old = pState.taint == null ? null : new StringIntHashMap(pState.taint);
//		}
		if(pState.taint == null) {
			pState.taint = new StringIntHashMap(objTaint);
		} else {
			TaintPropagation.mergeTaint(pState.taint, objTaint);
		}
//		if(LOG_FAILURES) {
//			if(pState.os == null) {
//				pState.os = new ObjectSet();
//			}
//			// already seen this object and no new effect on errors...
//			if(pState.taint.equals(old) && pState.os.contains(o)) {
//				return;
//			}
//			pState.os.add(o);
//		}
		if(!checkLinearizable(pState.taint, fProps)) {
			handleError(new EpochViolationException(errorString(pState.taint, fProps)));
		}
	}
	
	private static void addTaintToSet(ConcurrentSkipListSet<String> m, StringIntHashMap taint) {
		for(int i = 0; i < taint.m_entryCount; i++) {
			String k = taint.m_keyTable[taint.m_index[i]];
			m.add(k);
		}
	}
	
	private static final ConcurrentSkipListSet<String> allProps = new ConcurrentSkipListSet<>();
	private static final ConcurrentSkipListSet<String> strictCF = new ConcurrentSkipListSet<>();
	private static final ConcurrentSkipListSet<String> strictProp = new ConcurrentSkipListSet<>();
	private static final ConcurrentSkipListSet<String> linearCF = new ConcurrentSkipListSet<>();
	private static final ConcurrentSkipListSet<String> linearProp = new ConcurrentSkipListSet<>();
	private static final ConcurrentSkipListSet<String> writeProps = new ConcurrentSkipListSet<>();
	private static final ConcurrentSkipListSet<String> updateProps = new ConcurrentSkipListSet<>();
	
	public static void recordCF(Object o, int tag) {
		if(!RECORD_TAGS) {
			return;
		}
		if(!TaintPropagation.isTaintSource(o)) {
			return;
		}
		StringIntHashMap taint = TaintPropagation.getTaint(o);
		if(taint == null || taint.m_entryCount == 0) {
			return;
		}
		if(tag == 0) {
			addTaintToSet(strictCF, taint);
		} else {
			addTaintToSet(linearCF, taint);
		}
	}
	
//	public static void recordRead(Object o, int tag) {
//		if(!TaintPropagation.isTaintSource(o)) {
//			return;
//		}
//		PropLockState pState = getPropState();
//		recordReadInteral(o, pState, new HashSet<String>(), tag);
//	}
	
	public static void recordRead(Object o) {
		if(!TaintPropagation.isTaintSource(o)) {
			return;
		}
		PropLockState pState = getPropState();
		recordReadInteral(o, pState, LOG_FAILURES ? new HashSet<String>() : null);
	}	
	
	public static String copyString(String s) {
		return new String(s.toCharArray());
	}
}
