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

package org.apache.hc.client5.http.impl.async;

import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.reactor.IOSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LoggingIOSessionDecorator implements Decorator<IOSession> {

    public final static LoggingIOSessionDecorator INSTANCE = new LoggingIOSessionDecorator();

    private static final Logger WIRE_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http.wire");

    private LoggingIOSessionDecorator() {
    }

    @Override
    public IOSession decorate(final IOSession ioSession) {
        final Logger sessionLog = LoggerFactory.getLogger(ioSession.getClass());
        if (sessionLog.isDebugEnabled() || WIRE_LOG.isDebugEnabled()) {
            return new LoggingIOSession(ioSession, sessionLog, WIRE_LOG);
        } else {
            return ioSession;
        }
    }
}
