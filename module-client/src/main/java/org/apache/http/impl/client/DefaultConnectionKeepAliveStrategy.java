/*
 * $HeadURL: $
 * $Revision: $
 * $Date: $
 *
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
package org.apache.http.impl.client;

import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpResponse;
import org.apache.http.TokenIterator;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.message.BasicTokenIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

/**
 * Default implementation of a strategy deciding duration
 * that a connection can remain idle.
 * 
 * The default implementation looks solely at the 'Keep-Alive'
 * header's timeout token.
 *
 * @author <a href="mailto:sberlin at gmail.com">Sam Berlin</a>
 *
 * @version $Revision: $
 * 
 * @since 4.0
 */
public class DefaultConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {
    
    public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
        long duration = -1;
        HeaderIterator hit = response.headerIterator(HTTP.CONN_KEEP_ALIVE);
        
        while(hit.hasNext() && duration==-1) {
            Header header = hit.nextHeader();
            if(header.getValue() == null)
                continue;
            StringTokenizer tokenizer = new StringTokenizer(header.getValue(), ",");
            while(tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken().trim();
                if(token.toLowerCase(Locale.US).startsWith("timeout=")) {
                  duration = parseTimeout(token);
                  break;
                }
            }
        }
            
        // TODO: I'd prefer to do it this way, but BasicTokenIterator
        // freaks out on an '=' character.
//        if(hit.hasNext()) {
//            try {
//                TokenIterator it = createTokenIterator(hit);
//                while(it.hasNext()) {
//                    String token = it.nextToken();
//                    if(token.toLowerCase(Locale.US).startsWith("timeout=")) {
//                        duration = parseTimeout(token);
//                        break;
//                    }
//                }
//            } catch(ParseException pe) {
//                // Stop trying to find it and just fall-through.
//            }
//        }
        
        return duration;
    }
    
    /**
     * Parses the # out of the 'timeout=#' token.
     */
    protected long parseTimeout(String token) {
        // Make sure the length is valid.
        if(token.length() == "timeout=".length())
            return -1;
        
        try {
            return Long.parseLong(token.substring("timeout=".length()));
        } catch(NumberFormatException nfe) {
            return -1;
        }
    }

    public TimeUnit getTimeUnit() {
        return TimeUnit.SECONDS;
    }

    /**
     * Creates a token iterator from a header iterator.
     * This method can be overridden to replace the implementation of
     * the token iterator.
     *
     * @param hit       the header iterator
     *
     * @return  the token iterator
     */
    protected TokenIterator createTokenIterator(HeaderIterator hit) {
        return new BasicTokenIterator(hit);
    }
    
}
