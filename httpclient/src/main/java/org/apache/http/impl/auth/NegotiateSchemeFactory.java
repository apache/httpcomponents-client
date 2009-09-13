/*
 * ====================================================================
 *
 *  Copyright 2002-2009 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

package org.apache.http.impl.auth;

import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.params.HttpParams;

public class NegotiateSchemeFactory implements AuthSchemeFactory {
    
    private boolean stripPort = false; // strip port off kerb name
    private boolean spnegoCreate = false; // generate an SPNEGO wrapper for JDKs < 1.6.
    private SpnegoTokenGenerator spengoGenerator = null;
    
    public AuthScheme newInstance(final HttpParams params) {
        NegotiateScheme negotiateScheme = new NegotiateScheme();
        negotiateScheme.setStripPort(stripPort);
        negotiateScheme.setSpnegoCreate(spnegoCreate);
        negotiateScheme.setSpengoGenerator(spengoGenerator);
        return new NegotiateScheme();
    }

    public NegotiateSchemeFactory(){
        super();
    }
    
    public void setStripPort(boolean stripPort) {
        this.stripPort = stripPort;
    }

    public boolean isStripPort() {
        return stripPort;
    }

    public void setSpnegoCreate(boolean spnegoCreate) {
        this.spnegoCreate = spnegoCreate;
    }

    public boolean isSpnegoCreate() {
        return spnegoCreate;
    }

    public void setSpengoGenerator(SpnegoTokenGenerator spengoGenerator) {
        this.spengoGenerator = spengoGenerator;
    }

    public SpnegoTokenGenerator getSpengoGenerator() {
        return spengoGenerator;
    }
    
}
