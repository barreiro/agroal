// Copyright (C) 2017 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.pool.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.lang.System.arraycopy;
import static java.lang.reflect.Array.newInstance;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * @author <a href="lbarreiro@redhat.com">Luis Barreiro</a>
 */
public final class UncheckedCopyOnWriteArrayList<T> implements List<T> {

    private static final AtomicReferenceFieldUpdater<UncheckedCopyOnWriteArrayList, Object[]> DATA_UPDATER = newUpdater( UncheckedCopyOnWriteArrayList.class, Object[].class, "data" );

    private final Iterator<T> emptyIterator = new Iterator<T>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new NoSuchElementException();
        }
    };

    private volatile T[] data;

    // -- //

    @SuppressWarnings( "unchecked" )
    public UncheckedCopyOnWriteArrayList(Class<? extends T> clazz) {
        this.data = (T[]) newInstance( clazz, 0 );
    }

    public T[] getUnderlyingArray() {
        return data;
    }

    @Override
    public T get(int index) {
        return data[index];
    }

    @Override
    public int size() {
        return data.length;
    }

    // --- //

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean add(T element) {
        T[] array, newArray;
        do {
            array = data;
            newArray = Arrays.copyOf( array, array.length + 1 );
            newArray[array.length] = element;
        } while ( !DATA_UPDATER.compareAndSet( this, array, newArray ) );
        return true;
    }

    public T removeLast() {
        T[] array, newArray;
        T last;
        do {
            array = data;
            newArray = Arrays.copyOf( array, array.length - 1 );
            last = array[newArray.length];
        } while ( !DATA_UPDATER.compareAndSet( this, array, newArray ) );
        return last;
    }

    @Override
    public boolean remove(Object element) {
        T[] array, newArray = null;
        boolean found;
        do {
            array = data;
            found = false;
            for ( int index = array.length - 1; index >= 0; index-- ) {
                if ( element == array[index] ) {
                    found = true;
                    newArray = Arrays.copyOf( data, data.length - 1 );
                    arraycopy( data, index + 1, newArray, index, data.length - index - 1 );
                    break;
                }
            }
        } while ( found && !DATA_UPDATER.compareAndSet( this, array, newArray ) );
        return found;
    }

    @Override
    public void clear() {
        DATA_UPDATER.set( this, Arrays.copyOf( data, 0 ) );
    }

    @Override
    public boolean contains(Object o) {
        T[] array = data;
        for ( int index = array.length - 1; index >= 0; index-- ) {
            if ( o == array[index] ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        T[] array = data;
        return array.length == 0 ? emptyIterator : new UncheckedIterator<>( array );
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    // --- //

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException();
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
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> stream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> parallelStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for ( T element : this ) {
            action.accept( element );
        }
    }

    @Override
    public Spliterator<T> spliterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(Comparator<? super T> c) {
        throw new UnsupportedOperationException();
    }

    // --- //

    private static final class UncheckedIterator<T> implements Iterator<T> {

        private final int size;

        private final T[] data;

        private int index = 0;

        public UncheckedIterator(T[] data) {
            this.data = data;
            this.size = data.length;
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public T next() {
            if ( index < size ) {
                return data[index++];
            }
            throw new NoSuchElementException( "No more elements in this list" );
        }
    }
}
