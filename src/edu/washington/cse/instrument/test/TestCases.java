package edu.washington.cse.instrument.test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import edu.columbia.cs.psl.phosphor.runtime.MultiTainter;
import edu.washington.cse.instrumentation.runtime.CheckLevel;
import edu.washington.cse.instrumentation.runtime.PropagationTarget;
import edu.washington.cse.instrumentation.runtime.StringIntHashMap;
import edu.washington.cse.instrumentation.runtime.TaintHelper;
import edu.washington.cse.instrumentation.runtime.TaintHelper.MockProxy;
import edu.washington.cse.instrumentation.runtime.TaintPropagation;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoCheck;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoPropagate;

@StaccatoCheck(CheckLevel.NONE)
public class TestCases {
	private static MockProxy mockProxy;
	private static final StringIntHashMap expectedVMap = new StringIntHashMap();
	private static final StringIntHashMap expectedFMap = new StringIntHashMap();
	
	static {
		try {
			Field f = TaintHelper.class.getDeclaredField("mock");
			f.setAccessible(true);
			mockProxy = (MockProxy)f.get(null);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException("Failed to initialize mock", e);
		}
		
		expectedVMap.add("v", 3);
		expectedFMap.add("f", 5);
	}
	
	public static void main(String[] args) {
		testInteger();
		testShort();
		testByte();
		testLong();
		testDouble();
		testFloat();
	}
	
	static Map<String, String> getMap() {
		Map<String, String> toReturn = new HashMap<>();
		mockProxy.reset();
		TaintHelper.setNewProp("v", "1", toReturn);
		TaintHelper.setNewProp("b", "true", toReturn);
		TaintHelper.setNewProp("f", "0.5", toReturn);
		TaintHelper.setNewProp("c", "a", toReturn);
		return toReturn;
	}
	
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static short propagateshort(short x) {
    return 4;
  }
  
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static Short propagateShort(Short x) {
     return 4;
  }

  public static void testShort() {
     Map<String, String> m = getMap();
     short v1 = Short.parseShort(TaintHelper.getProp("v", m));
      short v2 = Short.parseShort(TaintHelper.getProp("v", m), 10);

     Short w1 = Short.valueOf(TaintHelper.getProp("v", m));
     Short w2 = Short.valueOf(v1);
     Short w3 = 1;
     short uw = w2.shortValue();
     Short w4 = v1;
     assert TaintPropagation.getTaint(w1).equals(expectedVMap);
     assert TaintPropagation.getTaint(w2).equals(expectedVMap);
     assert TaintPropagation.getTaint(w4).equals(expectedVMap);
     assert TaintPropagation.getTaint(w3) == null;

     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
      assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v2)));

     Short w5 = propagateShort(w1);
     assert TaintPropagation.getTaint(w5).equals(expectedVMap);
     Short ut = 1;
     assert TaintPropagation.getTaint(ut) == null;

     short wr = propagateshort(v1);
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
  }


  @StaccatoPropagate(PropagationTarget.RETURN)
  public static int propagateint(int x) {
    return 4;
  }
  
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static Integer propagateInteger(Integer x) {
     return 4;
  }

  public static void testInteger() {
     Map<String, String> m = getMap();
     int v1 = Integer.parseInt(TaintHelper.getProp("v", m));
      int v2 = Integer.parseInt(TaintHelper.getProp("v", m), 10);

     Integer w1 = Integer.valueOf(TaintHelper.getProp("v", m));
     Integer w2 = Integer.valueOf(v1);
     Integer w3 = 1;
     int uw = w2.intValue();
     Integer w4 = v1;
     assert TaintPropagation.getTaint(w1).equals(expectedVMap);
     assert TaintPropagation.getTaint(w2).equals(expectedVMap);
     assert TaintPropagation.getTaint(w4).equals(expectedVMap);
     assert TaintPropagation.getTaint(w3) == null;

     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
      assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v2)));

     Integer w5 = propagateInteger(w1);
     assert TaintPropagation.getTaint(w5).equals(expectedVMap);
     Integer ut = 1;
     assert TaintPropagation.getTaint(ut) == null;

     int wr = propagateint(v1);
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
  }


  @StaccatoPropagate(PropagationTarget.RETURN)
  public static long propagatelong(long x) {
    return 4l;
  }
  
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static Long propagateLong(Long x) {
     return 4l;
  }

  public static void testLong() {
     Map<String, String> m = getMap();
     long v1 = Long.parseLong(TaintHelper.getProp("v", m));
      long v2 = Long.parseLong(TaintHelper.getProp("v", m), 10);

     Long w1 = Long.valueOf(TaintHelper.getProp("v", m));
     Long w2 = Long.valueOf(v1);
     Long w3 = 1l;
     long uw = w2.longValue();
     Long w4 = v1;
     assert TaintPropagation.getTaint(w1).equals(expectedVMap);
     assert TaintPropagation.getTaint(w2).equals(expectedVMap);
     assert TaintPropagation.getTaint(w4).equals(expectedVMap);
     assert TaintPropagation.getTaint(w3) == null;

     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
      assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v2)));

     Long w5 = propagateLong(w1);
     assert TaintPropagation.getTaint(w5).equals(expectedVMap);
     Long ut = 1l;
     assert TaintPropagation.getTaint(ut) == null;

     long wr = propagatelong(v1);
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
  }


  @StaccatoPropagate(PropagationTarget.RETURN)
  public static byte propagatebyte(byte x) {
    return 4;
  }
  
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static Byte propagateByte(Byte x) {
     return 4;
  }

  public static void testByte() {
     Map<String, String> m = getMap();
     byte v1 = Byte.parseByte(TaintHelper.getProp("v", m));
      byte v2 = Byte.parseByte(TaintHelper.getProp("v", m), 10);

     Byte w1 = Byte.valueOf(TaintHelper.getProp("v", m));
     Byte w2 = Byte.valueOf(v1);
     Byte w3 = 1;
     byte uw = w2.byteValue();
     Byte w4 = v1;
     assert TaintPropagation.getTaint(w1).equals(expectedVMap);
     assert TaintPropagation.getTaint(w2).equals(expectedVMap);
     assert TaintPropagation.getTaint(w4).equals(expectedVMap);
     assert TaintPropagation.getTaint(w3) == null;

     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
      assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v2)));

     Byte w5 = propagateByte(w1);
     assert TaintPropagation.getTaint(w5).equals(expectedVMap);
     Byte ut = 1;
     assert TaintPropagation.getTaint(ut) == null;

     byte wr = propagatebyte(v1);
     assert expectedVMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
  }


  @StaccatoPropagate(PropagationTarget.RETURN)
  public static float propagatefloat(float x) {
    return 0.5f;
  }
  
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static Float propagateFloat(Float x) {
     return 0.5f;
  }

  public static void testFloat() {
     Map<String, String> m = getMap();
     float v1 = Float.parseFloat(TaintHelper.getProp("f", m));
  
     Float w1 = Float.valueOf(TaintHelper.getProp("f", m));
     Float w2 = Float.valueOf(v1);
     Float w3 = 2.5f;
     float uw = w2.floatValue();
     Float w4 = v1;
     assert TaintPropagation.getTaint(w1).equals(expectedFMap);
     assert TaintPropagation.getTaint(w2).equals(expectedFMap);
     assert TaintPropagation.getTaint(w4).equals(expectedFMap);
     assert TaintPropagation.getTaint(w3) == null;

     assert expectedFMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
     assert expectedFMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
  
     Float w5 = propagateFloat(w1);
     assert TaintPropagation.getTaint(w5).equals(expectedFMap);
     Float ut = 2.5f;
     assert TaintPropagation.getTaint(ut) == null;

     float wr = propagatefloat(v1);
     assert expectedFMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
  }


  @StaccatoPropagate(PropagationTarget.RETURN)
  public static double propagatedouble(double x) {
    return 0.5;
  }
  
  @StaccatoPropagate(PropagationTarget.RETURN)
  public static Double propagateDouble(Double x) {
     return 0.5;
  }

  public static void testDouble() {
     Map<String, String> m = getMap();
     double v1 = Double.parseDouble(TaintHelper.getProp("f", m));
  
     Double w1 = Double.valueOf(TaintHelper.getProp("f", m));
     Double w2 = Double.valueOf(v1);
     Double w3 = 2.5;
     double uw = w2.doubleValue();
     Double w4 = v1;
     assert TaintPropagation.getTaint(w1).equals(expectedFMap);
     assert TaintPropagation.getTaint(w2).equals(expectedFMap);
     assert TaintPropagation.getTaint(w4).equals(expectedFMap);
     assert TaintPropagation.getTaint(w3) == null;

     assert expectedFMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(v1)));
     assert expectedFMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(uw)));
  
     Double w5 = propagateDouble(w1);
     assert TaintPropagation.getTaint(w5).equals(expectedFMap);
     Double ut = 2.5;
     assert TaintPropagation.getTaint(ut) == null;

     double wr = propagatedouble(v1);
     assert expectedFMap.equals(TaintPropagation.unwrapTaint(MultiTainter.getTaint(wr)));
  }

}
