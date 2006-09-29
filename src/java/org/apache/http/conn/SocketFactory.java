/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
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

package org.apache.http.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.http.ConnectTimeoutException;
import org.apache.http.Scheme;
import org.apache.http.params.HttpParams;

/**
 * A factory for creating Sockets.
 * 
 * <p>Both {@link java.lang.Object#equals(java.lang.Object) Object.equals()} and 
 * {@link java.lang.Object#hashCode() Object.hashCode()} should be overridden appropriately.  
 * Protocol socket factories are used to uniquely identify <code>Protocol</code>s and 
 * <code>HostConfiguration</code>s, and <code>equals()</code> and <code>hashCode()</code> are 
 * required for the correct operation of some connection managers.</p>
 * 
 * @see Scheme
 * 
 * @author Michael Becke
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * 
 * @since 2.0
 */
public interface SocketFactory {

    /**
     * Gets a new socket connection to the given host.
     * 
     * @param host the host name/IP
     * @param port the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort the port on the local machine
     * @param params {@link HttpParams Http parameters}
     * 
     * @return Socket a new socket
     * 
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     * @throws ConnectTimeoutException if socket cannot be connected within the
     *  given time limit
     * 
     * @since 3.0
     */
    Socket createSocket(
        String host, 
        int port, 
        InetAddress localAddress, 
        int localPort,
        HttpParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException;

}
