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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.client5.testing.redirect.RedirectResolver;
import org.apache.hc.core5.net.URIBuilder;

public class OldPathRedirectResolver implements RedirectResolver {

    private final String oldPath;
    private final String newPath;
    private final int status;
    private final Redirect.ConnControl connControl;

    public OldPathRedirectResolver(
            final String oldPath, final String newPath, final int status, final Redirect.ConnControl connControl) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.status = status;
        this.connControl = connControl;
    }

    public OldPathRedirectResolver(final String oldPath, final String newPath, final int status) {
        this(oldPath, newPath, status, Redirect.ConnControl.PROTOCOL_DEFAULT);
    }

    @Override
    public Redirect resolve(final URI requestUri) throws URISyntaxException {
        final String path = requestUri.getPath();
        if (path.startsWith(oldPath)) {
            final URI location = new URIBuilder(requestUri)
                    .setPath(newPath + path.substring(oldPath.length()))
                    .build();
            return new Redirect(status, location.toString(), connControl);

        }
        return null;
    }
}
