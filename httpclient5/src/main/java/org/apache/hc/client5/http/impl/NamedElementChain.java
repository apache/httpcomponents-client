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

package org.apache.hc.client5.http.impl;

import org.apache.hc.core5.util.Args;

/**
 * Chain of doubly linked elements.
 * <p>
 * This implementation makes no attempts to ensure uniqueness of element names.
 *
 * @param <E>
 */
public class NamedElementChain<E> {

    private final Node master;
    private int size;

    public NamedElementChain() {
        this.master = new Node("master", null);
        this.master.previous = this.master;
        this.master.next = this.master;
        this.size = 0;
    }

    public Node getFirst() {
        if (master.next != master) {
            return master.next;
        } else {
            return null;
        }
    }

    public Node getLast() {
        if (master.previous != master) {
            return master.previous;
        } else {
            return null;
        }
    }

    public Node addFirst(final E value, final String name) {
        Args.notBlank(name, "Name");
        Args.notNull(value, "Value");
        final Node newNode = new Node(name, value);
        final Node oldNode = master.next;
        master.next = newNode;
        newNode.previous = master;
        newNode.next = oldNode;
        oldNode.previous = newNode;
        size++;
        return newNode;
    }

    public Node addLast(final E value, final String name) {
        Args.notBlank(name, "Name");
        Args.notNull(value, "Value");
        final Node newNode = new Node(name, value);
        final Node oldNode = master.previous;
        master.previous = newNode;
        newNode.previous = oldNode;
        newNode.next = master;
        oldNode.next = newNode;
        size++;
        return newNode;
    }

    public Node find(final String name) {
        Args.notBlank(name, "Name");
        return doFind(name);
    }

    private Node doFind(final String name) {
        Node current = master.next;
        while (current != master) {
            if (name.equals(current.name)) {
                return current;
            }
            current = current.next;
        }
        return null;
    }

    public Node addBefore(final String existing, final E value, final String name) {
        Args.notBlank(name, "Name");
        Args.notNull(value, "Value");
        final Node current = doFind(existing);
        if (current == null) {
            return null;
        }
        final Node newNode = new Node(name, value);
        final Node previousNode = current.previous;
        previousNode.next = newNode;
        newNode.previous = previousNode;
        newNode.next = current;
        current.previous = newNode;
        size++;
        return newNode;
    }

    public Node addAfter(final String existing, final E value, final String name) {
        Args.notBlank(name, "Name");
        Args.notNull(value, "Value");
        final Node current = doFind(existing);
        if (current == null) {
            return null;
        }
        final Node newNode = new Node(name, value);
        final Node nextNode = current.next;
        current.next = newNode;
        newNode.previous = current;
        newNode.next = nextNode;
        nextNode.previous = newNode;
        size++;
        return newNode;
    }

    public boolean remove(final String name) {
        final Node node = doFind(name);
        if (node == null) {
            return false;
        }
        node.previous.next = node.next;
        node.next.previous = node.previous;
        node.previous = null;
        node.next = null;
        size--;
        return true;
    }

    public boolean replace(final String existing, final E value) {
        final Node node = doFind(existing);
        if (node == null) {
            return false;
        }
        node.value = value;
        return true;
    }

    public int getSize() {
        return size;
    }

    public class Node {

        private final String name;
        private E value;
        private Node previous;
        private Node next;

        Node(final String name, final E value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public E getValue() {
            return value;
        }

        public Node getPrevious() {
            if (previous != master) {
                return previous;
            } else {
                return null;
            }
        }

        public Node getNext() {
            if (next != master) {
                return next;
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return name + ": " + value;
        }

    }

}