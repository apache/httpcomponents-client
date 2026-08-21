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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.Args;

/**
 * Parsed representation of the {@code Use-As-Dictionary} response header defined by Compression
 * Dictionary Transport. The origin returns this header alongside a resource it
 * offers as a future compression dictionary; it is a Structured Field Dictionary whose members
 * describe how the resource may later be matched and referenced. This type retains the members
 * the client acts on: {@code match} (a URL Pattern, mandatory) and {@code id} (an optional
 * opaque identifier echoed back in the {@code Dictionary-ID} request header), together with the
 * {@code type} that names the dictionary format.
 * <p>
 * Only the {@code raw} dictionary type is recognised; other values leave {@link #isSupported()}
 * {@code false} so the caller can ignore an offer it cannot honour rather than fail. Instances
 * are immutable and therefore safe to share between threads.
 */
final class UseAsDictionary {

    static final String RAW = "raw";

    private final String match;
    private final List<String> matchDest;
    private final String id;
    private final String type;

    UseAsDictionary(
            final String match,
            final List<String> matchDest,
            final String id,
            final String type) {
        this.match = match;
        this.matchDest = matchDest != null
                ? Collections.unmodifiableList(new ArrayList<>(matchDest))
                : Collections.emptyList();
        this.id = id != null ? id : "";
        this.type = type != null ? type : RAW;
    }

    String getMatch() {
        return match;
    }

    List<String> getMatchDest() {
        return matchDest;
    }

    String getId() {
        return id;
    }

    String getType() {
        return type;
    }

    boolean isSupported() {
        return RAW.equalsIgnoreCase(type);
    }
}