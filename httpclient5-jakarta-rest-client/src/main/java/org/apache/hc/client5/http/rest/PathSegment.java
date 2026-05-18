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
package org.apache.hc.client5.http.rest;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

final class PathSegment {

    enum Type {
        VALUE,
        PARAMETER
    }

    static PathSegment asValue(final String value) {
        return new PathSegment(value, Type.VALUE);
    }

    static PathSegment asParam(final String param) {
        return new PathSegment(param, Type.PARAMETER);
    }

    private final String segment;
    private final Type type;

    PathSegment(final String segment, final Type type) {
        this.segment = Args.notNull(segment, "Path segment");
        this.type = Args.notNull(type, "Path segment type");
    }

    public String getSegment() {
        return segment;
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PathSegment) {
            final PathSegment that = (PathSegment) obj;
            return this.segment.equals(that.segment) &&
                    this.type.equals(that.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.segment);
        hash = LangUtils.hashCode(hash, this.type);
        return hash;
    }

    @Override
    public String toString() {
        if (type == Type.PARAMETER) {
            return "{" + segment + "}";
        } else {
            return segment;
        }
    }

}
