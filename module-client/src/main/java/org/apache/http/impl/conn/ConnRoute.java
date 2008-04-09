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

package org.apache.http.impl.conn;

import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.util.LangUtils;

/**
 * A route for {@link ManagedClientConnection} along with the state information 
 * associated with that connection.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 */
public class ConnRoute {

    private final HttpRoute route;
    private final Object state;
    
    public ConnRoute(final HttpRoute route, final Object state) {
        super();
        if (route == null) {
            throw new IllegalArgumentException("HTTP route may not be null");
        }
        this.route = route;
        this.state = state;
    }

    public HttpRoute getRoute() {
        return this.route;
    }

    public Object getState() {
        return this.state;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof ConnRoute) {
            ConnRoute that = (ConnRoute) obj;
            return this.route.equals(that.route) 
                && LangUtils.equals(this.state, that.state);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.route);
        hash = LangUtils.hashCode(hash, this.state);
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(this.route);
        if (this.state != null) {
            buffer.append(" [");
            buffer.append(this.state);
            buffer.append("]");
        }
        return buffer.toString();
    }
    
}
