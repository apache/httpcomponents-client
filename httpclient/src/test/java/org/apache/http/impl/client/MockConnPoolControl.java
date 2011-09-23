/*
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

package org.apache.http.impl.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;

public final class MockConnPoolControl implements ConnPoolControl<HttpRoute> {

    private final ConcurrentHashMap<HttpRoute, Integer> maxPerHostMap;

    private volatile int totalMax;
    private volatile int defaultMax;

    public MockConnPoolControl() {
        super();
        this.maxPerHostMap = new ConcurrentHashMap<HttpRoute, Integer>();
        this.totalMax = 20;
        this.defaultMax = 2;
    }

    public void setMaxTotal(int max) {
        this.totalMax = max;
    }

    public int getMaxTotal() {
        return this.totalMax;
    }

    public PoolStats getTotalStats() {
        return new PoolStats(-1, -1, -1, this.totalMax);
    }

    public PoolStats getStats(final HttpRoute route) {
        return new PoolStats(-1, -1, -1, getMaxPerRoute(route));
    }

    public int getDefaultMaxPerRoute() {
        return this.defaultMax;
    }

    public void setDefaultMaxPerRoute(int max) {
        if (max < 1) {
            throw new IllegalArgumentException
                ("The maximum must be greater than 0.");
        }
        this.defaultMax = max;
    }

    public void setMaxPerRoute(final HttpRoute route, int max) {
        if (route == null) {
            throw new IllegalArgumentException
                ("HTTP route may not be null.");
        }
        if (max < 1) {
            throw new IllegalArgumentException
                ("The maximum must be greater than 0.");
        }
        this.maxPerHostMap.put(route, Integer.valueOf(max));
    }

    public int getMaxPerRoute(final HttpRoute route) {
        if (route == null) {
            throw new IllegalArgumentException
                ("HTTP route may not be null.");
        }
        Integer max = this.maxPerHostMap.get(route);
        if (max != null) {
            return max.intValue();
        } else {
            return this.defaultMax;
        }
    }

    public void setMaxForRoutes(final Map<HttpRoute, Integer> map) {
        if (map == null) {
            return;
        }
        this.maxPerHostMap.clear();
        this.maxPerHostMap.putAll(map);
    }

    @Override
    public String toString() {
        return this.maxPerHostMap.toString();
    }

}
