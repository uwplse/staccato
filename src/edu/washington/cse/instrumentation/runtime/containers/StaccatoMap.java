package edu.washington.cse.instrumentation.runtime.containers;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import edu.washington.cse.instrumentation.runtime.PropagationTarget;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoPropagate;

public class StaccatoMap<K,V> implements Map<K, V> {
	private final Map<K,V> wrapped;
	
	public StaccatoMap(Map<K,V> toWrap) {
		this.wrapped = toWrap;
	}
	
	@Override
	public void clear() {
		this.wrapped.clear();
	}

	@Override
	public boolean containsKey(Object arg0) {
		return this.wrapped.containsKey(arg0);
	}

	@Override
	public boolean containsValue(Object arg0) {
		return this.wrapped.containsValue(arg0);
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return this.wrapped.entrySet();
	}

	@Override
	public V get(Object arg0) {
		return this.wrapped.get(arg0);
	}

	@Override
	public boolean isEmpty() {
		return this.wrapped.isEmpty();
	}

	@Override
	public Set<K> keySet() {
		return this.wrapped.keySet();
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public V put(K arg0, V arg1) {
		return this.wrapped.put(arg0, arg1);
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public void putAll(Map<? extends K, ? extends V> arg0) {
		this.wrapped.putAll(arg0);
	}

	@Override
	public V remove(Object arg0) {
		return this.wrapped.remove(arg0);
	}

	@Override
	public int size() {
		return this.wrapped.size();
	}

	@Override
	public Collection<V> values() {
		return this.wrapped.values();
	}
  
  @Override
	public String toString() {
  	return "StaccatoMap[" + this.wrapped.toString() + "]";
  }
}
