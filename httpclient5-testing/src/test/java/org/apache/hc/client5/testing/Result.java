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
package org.apache.hc.client5.testing;

import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;

public final class Result<T> {

    public final HttpRequest request;
    public final HttpResponse response;
    public final T content;
    public final Exception exception;

    public enum Status { OK, NOK }

    public Result(final HttpRequest request, final Exception exception) {
        this.request = request;
        this.response = null;
        this.content = null;
        this.exception = exception;
    }

    public Result(final HttpRequest request, final HttpResponse response, final T content) {
        this.request = request;
        this.response = response;
        this.content = content;
        this.exception = null;
    }

    public Status getStatus() {
        return exception != null ? Status.NOK : Status.OK;
    }

    public boolean isOK() {
        return exception == null;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(new RequestLine(request));
        buf.append(" -> ");
        if (exception != null) {
            buf.append("NOK: ").append(exception);
        } else {
            if (response != null) {
                buf.append("OK: ").append(new StatusLine(response));
            }
        }
        return buf.toString();
    }

}
