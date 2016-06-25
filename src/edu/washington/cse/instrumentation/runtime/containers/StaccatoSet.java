package edu.washington.cse.instrumentation.runtime.containers;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import edu.washington.cse.instrumentation.runtime.PropagationTarget;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoPropagate;

public class StaccatoSet<T> implements Set<T> {
	private final Set<T> wrapped;
	
	public StaccatoSet(Set<T> toWrap) {
		this.wrapped = toWrap;
	}
		
	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public boolean add(T e) {
		return this.wrapped.add(e);
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public boolean addAll(Collection<? extends T> c) {
		return this.wrapped.addAll(c);
	}

	@Override
	public void clear() {
		this.wrapped.clear();
	}

	@Override
	public boolean contains(Object o) {
		return this.wrapped.contains(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return this.containsAll(c);
	}

	@Override
	public boolean isEmpty() {
		return this.wrapped.isEmpty();
	}

	@Override
	public Iterator<T> iterator() {
		return this.wrapped.iterator();
	}

	@Override
	public boolean remove(Object o) {
		return this.wrapped.remove(o);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return this.wrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return this.wrapped.retainAll(c);
	}

	@Override
	public int size() {
		return this.wrapped.size();
	}

	@Override
	public Object[] toArray() {
		return this.wrapped.toArray();
	}

	@Override
	public <U> U[] toArray(U[] a) {
		return this.wrapped.toArray(a);
	}

	@Override
	public String toString() {
		return this.wrapped.toString();
	}
}
