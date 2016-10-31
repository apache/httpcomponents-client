/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.osgi.impl;

import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * Implementation of a list backed by WeakReference objects.
 * This is not a general purpose list and is only meant to be used by this package. It cannot correctly manage null entries by design.
 */
class WeakList<T> extends AbstractList<T> {

    private final List<WeakReference<T>> innerList;

    public WeakList() {
        this.innerList = new ArrayList<WeakReference<T>>();
    }

    @Override
    public T get(final int index) {
        return innerList.get(index).get();
    }

    @Override
    public int size() {
        checkReferences();
        return innerList.size();
    }

    @Override
    public boolean add(final T t) {
        return innerList.add(new WeakReference<T>(t));
    }

    @Override
    public void clear() {
        innerList.clear();
    }

    private void checkReferences() {
        final ListIterator<WeakReference<T>> references = innerList.listIterator();
        while (references.hasNext()) {
            final WeakReference<T> reference = references.next();
            if (reference.get() == null) {
                references.remove();
            }
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new WeakIterator<T>(innerList.iterator());
    }

    private class WeakIterator<T> implements Iterator<T> {

        private final Iterator<WeakReference<T>> innerIterator;

        private WeakReference<T> next;

        public WeakIterator(final Iterator<WeakReference<T>> innerIterator) {
            this.innerIterator = innerIterator;
            fetchNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next != null) {
                final T result = next.get();
                fetchNext();
                return result;
            } else {
                throw new NoSuchElementException();
            }
        }

        private void fetchNext() {
            while (innerIterator.hasNext()) {
                final WeakReference<T> ref = innerIterator.next();
                final T obj = ref.get();
                if (obj != null) {
                    next = ref;
                    return;
                }
            }
            next = null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}
