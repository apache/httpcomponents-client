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

package org.apache.http.entity.mime;

import java.nio.charset.Charset;

/**
 *
 * @since 4.3
 */
public class HttpMultipartFactory {

    /** Non-instantiable */
    private HttpMultipartFactory() {
    }
    
    /** Create an appropriate AbstractMultipartForm instance */
    public static AbstractMultipartForm getInstance(
        final String subType, final Charset charset, final String boundary,
        final HttpMultipartMode mode) {
        // If needed, this can be replaced with a registry in time
        switch (mode) {
            case STRICT:
                return new HttpStrictMultipart(subType, charset, boundary);
            case BROWSER_COMPATIBLE:
                return new HttpBrowserCompatibleMultipart(subType, charset, boundary);
            case RFC6532:
                return new HttpRFC6532Multipart(subType, charset, boundary);
            default:
                throw new IllegalArgumentException("Unknown multipart mode");
        }
    }

}
