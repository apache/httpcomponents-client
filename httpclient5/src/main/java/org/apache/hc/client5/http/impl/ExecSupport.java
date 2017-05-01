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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;

public final class ExecSupport {

    private static final AtomicLong COUNT = new AtomicLong(0);

    public static long getNextExecNumber() {
        return COUNT.incrementAndGet();
    }

    private static void copyMessageProperties(final HttpMessage original, final HttpMessage copy) {
        copy.setVersion(original.getVersion());
        for (final Iterator<Header> it = original.headerIterator(); it.hasNext(); ) {
            copy.addHeader(it.next());
        }
    }

    private static void copyRequestProperties(final HttpRequest original, final HttpRequest copy) {
        copyMessageProperties(original, copy);
        if (copy.getVersion() == null) {
            copy.setVersion(HttpVersion.DEFAULT);
        }
        copy.setScheme(original.getScheme());
        copy.setAuthority(original.getAuthority());
    }

    private static void copyResponseProperties(final HttpResponse original, final HttpResponse copy) {
        copyMessageProperties(original, copy);
        copy.setLocale(copy.getLocale());
        copy.setReasonPhrase(copy.getReasonPhrase());
    }

    public static HttpRequest copy(final HttpRequest original) {
        if (original == null) {
            return null;
        }
        final BasicHttpRequest copy = new BasicHttpRequest(original.getMethod(), original.getPath());
        copyRequestProperties(original, copy);
        return copy;
    }

    public static HttpResponse copy(final HttpResponse original) {
        if (original == null) {
            return null;
        }
        final BasicHttpResponse copy = new BasicHttpResponse(original.getCode());
        copyResponseProperties(original, copy);
        return copy;
    }

    public static ClassicHttpRequest copy(final ClassicHttpRequest original) {
        if (original == null) {
            return null;
        }
        final BasicClassicHttpRequest copy = new BasicClassicHttpRequest(original.getMethod(), original.getPath());
        copyRequestProperties(original, copy);
        copy.setEntity(original.getEntity());
        return copy;
    }

    public static ClassicHttpResponse copy(final ClassicHttpResponse original) {
        if (original == null) {
            return null;
        }
        final BasicClassicHttpResponse copy = new BasicClassicHttpResponse(original.getCode());
        copyResponseProperties(original, copy);
        copy.setEntity(original.getEntity());
        return copy;
    }

}
