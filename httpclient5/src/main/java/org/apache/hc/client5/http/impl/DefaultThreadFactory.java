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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @since 5.0
 */
public class DefaultThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final ThreadGroup group;
    private final AtomicLong count;
    private final boolean daemon;

    public DefaultThreadFactory(final String namePrefix, final ThreadGroup group, final boolean daemon) {
        this.namePrefix = namePrefix;
        this.group = group;
        this.count = new AtomicLong();
        this.daemon = daemon;
    }

    public DefaultThreadFactory(final String namePrefix) {
        this(namePrefix, null, false);
    }

    public DefaultThreadFactory(final String namePrefix, final boolean daemon) {
        this(namePrefix, null, daemon);
    }

    @Override
    public Thread newThread(final Runnable target) {
        final Thread thread = new Thread(this.group, target, this.namePrefix + "-"  + this.count.incrementAndGet());
        thread.setDaemon(this.daemon);
        return thread;
    }

}
