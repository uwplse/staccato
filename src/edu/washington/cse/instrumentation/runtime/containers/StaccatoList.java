package edu.washington.cse.instrumentation.runtime.containers;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import edu.washington.cse.instrumentation.runtime.PropagationTarget;
import edu.washington.cse.instrumentation.runtime.annotation.StaccatoPropagate;

public class StaccatoList<E> implements List<E> {

	private final List<E> wrapped; 
	
	public StaccatoList(List<E> l) {
		this.wrapped = l;
	}
	
	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public boolean add(E arg0) {
		return this.wrapped.add(arg0);
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public void add(int arg0, E arg1) {
		this.wrapped.add(arg0, arg1);
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public boolean addAll(Collection<? extends E> arg0) {
		return this.wrapped.addAll(arg0);
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public boolean addAll(int arg0, Collection<? extends E> arg1) {
		return this.wrapped.addAll(arg0, arg1);
	}

	@Override
	public void clear() {
		this.wrapped.clear();
	}

	@Override
	public boolean contains(Object arg0) {
		return this.wrapped.contains(arg0);
	}

	@Override
	public boolean containsAll(Collection<?> arg0) {
		return this.wrapped.contains(arg0);
	}

	@Override
	public E get(int arg0) {
		return this.wrapped.get(arg0);
	}

	@Override
	public int indexOf(Object arg0) {
		return this.wrapped.indexOf(arg0);
	}

	@Override
	public boolean isEmpty() {
		return this.wrapped.isEmpty();
	}

	@Override
	public Iterator<E> iterator() {
		return this.wrapped.iterator();
	}

	@Override
	public int lastIndexOf(Object arg0) {
		return this.wrapped.lastIndexOf(arg0);
	}

	@Override
	public ListIterator<E> listIterator() {
		return this.wrapped.listIterator();
	}

	@Override
	public ListIterator<E> listIterator(int arg0) {
		return this.wrapped.listIterator(arg0);
	}

	@Override
	public boolean remove(Object arg0) {
		return this.wrapped.remove(arg0);
	}

	@Override
	public E remove(int arg0) {
		return this.wrapped.remove(arg0);
	}

	@Override
	public boolean removeAll(Collection<?> arg0) {
		return this.wrapped.removeAll(arg0);
	}

	@Override
	public boolean retainAll(Collection<?> arg0) {
		return this.wrapped.retainAll(arg0);
	}

	@Override
	@StaccatoPropagate(PropagationTarget.RECEIVER)
	public E set(int arg0, E arg1) {
		return this.wrapped.set(arg0, arg1);
	}

	@Override
	public int size() {
		return this.wrapped.size();
	}

	@Override
	public List<E> subList(int arg0, int arg1) {
		return this.wrapped.subList(arg0, arg1);
	}

	@Override
	public Object[] toArray() {
		return this.wrapped.toArray();
	}

	@Override
	public <T> T[] toArray(T[] arg0) {
		return this.wrapped.toArray(arg0);
	}
	
	@Override
	public String toString() {
		return wrapped.toString();
	}
}
