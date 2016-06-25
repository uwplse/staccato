package edu.washington.cse.instrumentation.runtime;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class ObjectSet implements Set<Object> {
	private static class Bucket {
		Object val;
		Bucket next;
	}
	private static final int N_BUCKETS = 100;
	
	private Bucket[] buckets = new Bucket[N_BUCKETS];
	private int size = 0;

	@Override
	public boolean add(Object e) {
		if(e == null) {
			throw new NullPointerException();
		}
		int bucket = System.identityHashCode(e) % N_BUCKETS;
		if(buckets[bucket] == null) {
			Bucket b = new Bucket();
			b.next = null;
			b.val = e;
			buckets[bucket] = b;
			size++;
			return true;
		}
		Bucket it = buckets[bucket];
		while(it != null) {
			if(it.val == e) {
				return false;
			}
			if(it.next == null) {
				Bucket b = new Bucket();
				it.next = b;
				b.val = e;
				size++;
				return true;
			} else {
				it = it.next;
			}
		}
		throw new Error("Impossible");
	}

	@Override
	public boolean addAll(Collection<? extends Object> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		this.buckets = new Bucket[N_BUCKETS];
		size = 0;
	}

	@Override
	public boolean contains(Object o) {
		int bucket = System.identityHashCode(o) % N_BUCKETS;
		Bucket it = buckets[bucket];
		while(it != null) {
			if(it.val == o) {
				return true;
			}
			it = it.next;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for(Object o : c) {
			if(!contains(o)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isEmpty() {
		return size != 0;
	}

	@Override
	public Iterator<Object> iterator() {
		return new Iterator<Object>() {
			private int b_it = 0;
			private Bucket it = null;
			@Override
			public boolean hasNext() {
				return b_it != N_BUCKETS;
			}

			@Override
			public Object next() {
				if(!hasNext()) {
					throw new NoSuchElementException();
				}
				Object ret = it.val;
				if(it.next != null) {
					it = it.next;
					return ret;
				}
				b_it++;
				while(b_it < N_BUCKETS) {
					if(buckets[b_it] != null) {
						it = buckets[b_it];
						break;
					} else {
						b_it++;
					}
				}
				return ret;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
			
			{
				for(b_it = 0; b_it < N_BUCKETS; b_it++) {
					if(buckets[b_it] != null) {
						break;
					}
				}
				if(b_it != N_BUCKETS) {
					it = buckets[b_it];
				}
			}
		};
	}

	@Override
	public boolean remove(Object o) {
		int bucket = System.identityHashCode(o) % N_BUCKETS;
		if(buckets[bucket] == null) {
			return false;
		}
		Bucket it = buckets[bucket];
		if(it.val == o) {
			buckets[bucket] = it.next;
			size--;
			return true;
		}
		while(it.next != null) {
			if(it.next.val == o) {
				it.next = it.next.next;
				size--;
				return true;
			} else {
				it = it.next;
			}
		}
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public Object[] toArray() {
		Object[] toRet = new Object[size];
		int i = 0;
		for(Object o : this) {
			toRet[i++] = o;
		}
		return toRet;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] toArray(T[] a) {
		if(a.length < size) {
			a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
		}
		int i = 0;
		for(Object o : this) {
			a[i++] = (T)o;
		}
		if(i < a.length) {
			a[i] = null;
		}
		return a;
	}
}
