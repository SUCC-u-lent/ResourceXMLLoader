package org.ubunifu.resourcexmlloader;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

class WeakIdentityHashMap<K, V> {
    private final Map<IdentityWeakReference<K>, V> internalMap = new HashMap<>();

    public boolean containsKey(K clazz)
    {
        return internalMap.keySet().stream().anyMatch(ref -> ref.get() == clazz);
    }
    public boolean containsValue(V value)
    {
        return internalMap.values().stream().anyMatch(v -> v == value);
    }

    public Set<IdentityWeakReference<K>> keySet()
    {
        return internalMap.keySet();
    }
    public Collection<V> values()
    {
        return internalMap.values();
    }
    public Set<Map.Entry<IdentityWeakReference<K>,V>> entrySet()
    {
        return internalMap.entrySet();
    }

    public V computeIfAbsent(K key,
                                    Function<IdentityWeakReference<? super K>, ? extends V> mappingFunction) {
        return internalMap.computeIfAbsent(new IdentityWeakReference<>(key),mappingFunction);
    }

    public void clear() {
        this.internalMap.clear();
    }

    public static class IdentityWeakReference<T> extends WeakReference<T> {
        private final int hash;

        IdentityWeakReference(T referent) {
            super(referent);
            hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IdentityWeakReference<?>)) return false;
            Object thatReferent = ((IdentityWeakReference<?>) obj).get();
            Object thisReferent = this.get();
            return thisReferent == thatReferent;
        }
    }

    public void put(K key, V value) {
        expungeStaleEntries();
        internalMap.put(new IdentityWeakReference<>(key), value);
    }

    public V get(K key) {
        expungeStaleEntries();
        return internalMap.get(new IdentityWeakReference<>(key));
    }

    public V remove(K key) {
        expungeStaleEntries();
        return internalMap.remove(new IdentityWeakReference<>(key));
    }

    private void expungeStaleEntries() {
        Iterator<IdentityWeakReference<K>> it = internalMap.keySet().iterator();
        while (it.hasNext()) {
            IdentityWeakReference<K> ref = it.next();
            if (ref.get() == null) {
                it.remove();
            }
        }
    }

    public int size() {
        expungeStaleEntries();
        return internalMap.size();
    }

    public boolean isEmpty() {
        expungeStaleEntries();
        return internalMap.isEmpty();
    }
}