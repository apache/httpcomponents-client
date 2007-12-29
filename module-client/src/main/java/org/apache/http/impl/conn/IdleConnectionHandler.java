/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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
package org.apache.http.impl.conn;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpConnection;


/**
 * A helper class for connection managers to track idle connections.
 * 
 * <p>This class is not synchronized.</p>
 * 
 * @see org.apache.http.conn.ClientConnectionManager#closeIdleConnections
 * 
 * @since 4.0
 */
public class IdleConnectionHandler {

    private final Log LOG = LogFactory.getLog(IdleConnectionHandler.class);
    
    /** Holds connections and the time they were added. */
    private Map<HttpConnection,Long> connectionToAdded;
    

    public IdleConnectionHandler() {
        super();
        connectionToAdded = new HashMap<HttpConnection,Long>();
    }
    
    /**
     * Registers the given connection with this handler.  The connection will be held until 
     * {@link #remove} or {@link #closeIdleConnections} is called.
     * 
     * @param connection the connection to add
     * 
     * @see #remove
     */
    public void add(HttpConnection connection) {
        
        Long timeAdded = new Long(System.currentTimeMillis());
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding connection at: " + timeAdded);
        }
        
        connectionToAdded.put(connection, timeAdded);
    }
    
    /**
     * Removes the given connection from the list of connections to be closed when idle.
     * @param connection
     */
    public void remove(HttpConnection connection) {
        connectionToAdded.remove(connection);
    }

    /**
     * Removes all connections referenced by this handler.
     */
    public void removeAll() {
        this.connectionToAdded.clear();
    }
    
    /**
     * Closes connections that have been idle for at least the given amount of time.
     * 
     * @param idleTime the minimum idle time, in milliseconds, for connections to be closed
     */
    //@@@ add TimeUnit argument here?
    public void closeIdleConnections(long idleTime) {
        
        // the latest time for which connections will be closed
        long idleTimeout = System.currentTimeMillis() - idleTime;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Checking for connections, idleTimeout: "  + idleTimeout);
        }
        
        Iterator<HttpConnection> connectionIter =
            connectionToAdded.keySet().iterator();
        
        while (connectionIter.hasNext()) {
            HttpConnection conn = connectionIter.next();
            Long connectionTime = connectionToAdded.get(conn);
            if (connectionTime.longValue() <= idleTimeout) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing connection, connection time: "  + connectionTime);
                }
                connectionIter.remove();
                try {
                    conn.close();
                } catch (IOException ex) {
                    LOG.debug("I/O error closing connection", ex);
                }
            }
        }
    }
}
