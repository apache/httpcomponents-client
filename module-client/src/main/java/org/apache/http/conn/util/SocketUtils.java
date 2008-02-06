/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
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

package org.apache.http.conn.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SocketUtils {

    /**
     * Attempts to connects the socket to any of the {@link InetAddress}es the 
     * given host name resolves to. If connection to all addresses fail, the  
     * last I/O exception is propagated to the caller.
     * 
     * @param sock socket to connect to any of the given addresses
     * @param hostname Host name to connect to
     * @param port the port to connect to
     * @param timeout connection timeout
     * 
     * @throws  IOException if an error occurs during the connection
     * @throws  SocketTimeoutException if timeout expires before connecting
     */
    public static void connect(
            final Socket sock, 
            final String hostname,
            int port,
            int timeout) throws IOException {
        
        InetAddress[] adrs = InetAddress.getAllByName(hostname);
        List<InetAddress> list = new ArrayList<InetAddress>(adrs.length);
        for (InetAddress adr: adrs) {
            list.add(adr);
        }
        Collections.shuffle(list);
        connect(sock, list, port, timeout);
    }
    
    /**
     * Attempts to connects the socket to any of the {@link InetAddress}es given as a 
     * parameter, whichever succeeds first. If connection to all addresses fail, the 
     * last I/O exception is propagated to the caller.
     * 
     * @param sock socket to connect to any of the given addresses
     * @param addresses array of addresses
     * @param port the port to connect to
     * @param timeout connection timeout
     * 
     * @throws  IOException if an error occurs during the connection
     * @throws  SocketTimeoutException if timeout expires before connecting
     */
    public static void connect(
            final Socket sock, 
            final List<InetAddress> addresses,
            int port,
            int timeout) throws IOException {
        if (sock == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        if (addresses == null) {
            throw new IllegalArgumentException("List of addresses may not be null");
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("List of addresses may not be empty");
        }
        
        IOException lastEx = null;
        for (InetAddress address: addresses) {
            try {
                sock.connect(new InetSocketAddress(address, port), timeout);
                return;
            } catch (SocketTimeoutException ex) {
                throw ex;
            } catch (IOException ex) {
                // keep the last exception and retry
                lastEx = ex;
            }
        }
        if (lastEx != null) {
            throw lastEx;
        }
    }
    
}
