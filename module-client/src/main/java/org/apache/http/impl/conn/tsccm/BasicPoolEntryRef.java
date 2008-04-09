/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn.tsccm;


import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;

import org.apache.http.impl.conn.ConnRoute;



/**
 * A weak reference to a {@link BasicPoolEntry BasicPoolEntry}.
 * This reference explicitly keeps the planned route, so the connection
 * can be reclaimed if it is lost to garbage collection.
 */
public class BasicPoolEntryRef extends WeakReference<BasicPoolEntry> {

    /** The planned route of the entry. */
    private final ConnRoute route;


    /**
     * Creates a new reference to a pool entry.
     *
     * @param entry   the pool entry, must not be <code>null</code>
     * @param queue   the reference queue, or <code>null</code>
     */
    public BasicPoolEntryRef(BasicPoolEntry entry,
                             ReferenceQueue<Object> queue) {
        super(entry, queue);
        if (entry == null) {
            throw new IllegalArgumentException
                ("Pool entry must not be null.");
        }
        route = entry.getPlannedRoute();
    }


    /**
     * Obtain the planned route for the referenced entry.
     * The planned route is still available, even if the entry is gone.
     *
     * @return      the planned route
     */
    public final ConnRoute getRoute() {
        return this.route;
    }


    /* *
     * Indicates whether this reference is still valid.
     *
     * @return <code>true</code> if the pool entry is still referenced,
     *         <code>false</code> otherwise
     * /
    public final boolean isValid() {
        //@@@ better sematics: allow explicit invalidation
        return (super.get() != null);
    }
    */
        
} // class BasicPoolEntryRef

