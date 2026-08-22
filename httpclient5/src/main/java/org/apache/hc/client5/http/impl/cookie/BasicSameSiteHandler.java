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

package org.apache.hc.client5.http.impl.cookie;

import org.apache.hc.client5.http.cookie.CommonCookieAttributeHandler;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.cookie.SameSite;
import org.apache.hc.client5.http.cookie.SetCookie;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Cookie {@code SameSite} attribute handler. The raw attribute value is retained by the cookie
 * specification and exposed through {@link Cookie#getSameSite()}; this handler enforces that a
 * {@code SameSite=None} cookie must also be secure.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class BasicSameSiteHandler extends AbstractCookieAttributeHandler implements CommonCookieAttributeHandler {

    /**
     * Default instance of {@link BasicSameSiteHandler}.
     */
    public static final BasicSameSiteHandler INSTANCE = new BasicSameSiteHandler();

    public BasicSameSiteHandler() {
        super();
    }

    @Override
    public void parse(final SetCookie cookie, final String value) throws MalformedCookieException {
        Args.notNull(cookie, "Cookie");
    }

    @Override
    public void validate(final Cookie cookie, final CookieOrigin origin) throws MalformedCookieException {
        Args.notNull(cookie, "Cookie");
        if (SameSite.NONE == cookie.getSameSite() && !cookie.isSecure()) {
            throw new MalformedCookieException("Cookie '" + cookie.getName()
                    + "' has SameSite=None but is not marked secure");
        }
    }

    @Override
    public String getAttributeName() {
        return Cookie.SAME_SITE_ATTR;
    }

}
