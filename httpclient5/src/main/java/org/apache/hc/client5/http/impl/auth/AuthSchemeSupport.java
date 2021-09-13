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
package org.apache.hc.client5.http.impl.auth;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.core5.annotation.Internal;

/**
 * @since 5.2
 */
@Internal
public class AuthSchemeSupport {

    public static Charset parseCharset(
            final String charsetName, final Charset defaultCharset) throws AuthenticationException {
        try {
            return charsetName != null ? Charset.forName(charsetName) : defaultCharset;
        } catch (final UnsupportedCharsetException ex) {
            throw new AuthenticationException("Unsupported charset: " + charsetName);
        }
    }

}
