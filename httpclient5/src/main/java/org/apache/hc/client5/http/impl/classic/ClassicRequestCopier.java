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
package org.apache.hc.client5.http.impl.classic;

import java.util.Iterator;

import org.apache.hc.client5.http.impl.MessageCopier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;

/**
 * {@link ClassicHttpRequest} copier.
 *
 * @since 5.0
 */
public final class ClassicRequestCopier implements MessageCopier<ClassicHttpRequest> {

    public static final ClassicRequestCopier INSTANCE = new ClassicRequestCopier();

    @Override
    public ClassicHttpRequest copy(final ClassicHttpRequest original) {
        if (original == null) {
            return null;
        }
        final BasicClassicHttpRequest copy = new BasicClassicHttpRequest(original.getMethod(), original.getPath());
        copy.setVersion(original.getVersion());
        for (final Iterator<Header> it = original.headerIterator(); it.hasNext(); ) {
            copy.addHeader(it.next());
        }
        copy.setScheme(original.getScheme());
        copy.setAuthority(original.getAuthority());
        copy.setEntity(original.getEntity());
        return copy;
    }

}
