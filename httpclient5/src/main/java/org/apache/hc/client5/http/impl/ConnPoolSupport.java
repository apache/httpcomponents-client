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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Identifiable;

/**
 * Connection pooling support methods.
 *
 * @since 5.0
 */
@Internal
public final class ConnPoolSupport {

    public static String getId(final Object object) {
        if (object == null) {
            return null;
        }
        return object instanceof Identifiable
                        ? ((Identifiable) object).getId()
                        : object.getClass().getSimpleName() + "-"
                                        + Integer.toHexString(System.identityHashCode(object));
    }

    public static String formatStats(
            final HttpRoute route,
            final Object state,
            final ConnPoolControl<HttpRoute> connPool) {
        final StringBuilder buf = new StringBuilder();
        buf.append("[route: ").append(route).append("]");
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        final PoolStats totals = connPool.getTotalStats();
        final PoolStats stats = connPool.getStats(route);
        buf.append("[total available: ").append(totals.getAvailable()).append("; ");
        buf.append("route allocated: ").append(stats.getLeased() + stats.getAvailable());
        buf.append(" of ").append(stats.getMax()).append("; ");
        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
        buf.append(" of ").append(totals.getMax()).append("]");
        return buf.toString();
    }

}
