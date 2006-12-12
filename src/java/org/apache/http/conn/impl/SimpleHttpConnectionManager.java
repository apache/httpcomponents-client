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

package org.apache.http.conn.impl;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.conn.HostConfiguration;
import org.apache.http.conn.HttpConnectionManager;
import org.apache.http.conn.HttpHostConnection;
import org.apache.http.impl.DefaultHttpParams;
import org.apache.http.params.HttpParams;

/**
 * A connection manager that provides access to a single HttpConnection.  This
 * manager makes no attempt to provide exclusive access to the contained
 * HttpConnection.
 *
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author Eric Johnson
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author Laura Werner
 * 
 * @since 2.0
 */
public class SimpleHttpConnectionManager implements HttpConnectionManager {

    private static final Log LOG = LogFactory.getLog(SimpleHttpConnectionManager.class);

    private static final String MISUSE_MESSAGE = 
        "SimpleHttpConnectionManager being used incorrectly.  Be sure that"
        + " HttpMethod.releaseConnection() is always called and that only one thread"
        + " and/or method is using this connection manager at a time.";
    
    /**
     * Since the same connection is about to be reused, make sure the
     * previous request was completely processed, and if not
     * consume it now.
     * @param conn The connection
     */
    protected static void finishLastResponse(HttpHostConnection conn) {
        HttpResponse lastResponse = conn.getLastResponse();
        if (lastResponse != null) {
            conn.setLastResponse(null);
            HttpEntity entity = lastResponse.getEntity();
            if (entity != null) {
                try {
                    entity.consumeContent();
                } catch (IOException ex) {
                    LOG.debug("I/O error consuming response content", ex);
                }
            }
        }
    }
    
    /** The http connection */
    protected DefaultHttpHostConnection httpConnection;

    /**
     * Collection of parameters associated with this connection manager.
     */
    private HttpParams params = new DefaultHttpParams(); 

    /**
     * The time the connection was made idle.
     */
    private long idleStartTime = Long.MAX_VALUE;
    
    /**
     * Used to test if {@link #httpConnection} is currently in use 
     * (i.e. checked out).  This is only used as a sanity check to help
     * debug cases where this connection manager is being used incorrectly.
     * It will not be used to enforce thread safety.
     */
    private volatile boolean inUse = false;

    private boolean alwaysClose = false;

    /**
     * The connection manager created with this constructor will try to keep the 
     * connection open (alive) between consecutive requests if the alwaysClose 
     * parameter is set to <tt>false</tt>. Otherwise the connection manager will 
     * always close connections upon release.
     * 
     * @param alwaysClose if set <tt>true</tt>, the connection manager will always
     *    close connections upon release.
     */
    public SimpleHttpConnectionManager(boolean alwaysClose) {
        super();
        this.alwaysClose = alwaysClose;
    }
    
    /**
     * The connection manager created with this constructor will always try to keep 
     * the connection open (alive) between consecutive requests.
     */
    public SimpleHttpConnectionManager() {
        super();
    }
    
    /**
     * @see HttpConnectionManager#getConnection(HostConfiguration)
     */
    public HttpHostConnection getConnection(HostConfiguration hostConfiguration) {
        return getConnection(hostConfiguration, 0);
    }

    private void closeConnection() {
        try {
            this.httpConnection.close();
        } catch (IOException ex) {
            LOG.debug("I/O error closing connection", ex);
        }
    }
    
    /**
     * This method always returns the same connection object. If the connection is already
     * open, it will be closed and the new host configuration will be applied.
     * 
     * @param hostConfiguration The host configuration specifying the connection
     *        details.
     * @param timeout this parameter has no effect. The connection is always returned
     *        immediately.
     * @since 3.0
     */
    public HttpHostConnection getConnection(
        HostConfiguration hostConfiguration, long timeout) {

        if (httpConnection == null) {
            httpConnection = new DefaultHttpHostConnection();
            httpConnection.setHttpConnectionManager(this);
        } else {
            // make sure the host and proxy are correct for this connection
            // close it and set the values if they are not
            if (!hostConfiguration.equals(httpConnection)) {
                if (httpConnection.isOpen()) {
                    closeConnection();
                }
                httpConnection.setHostConfiguration(hostConfiguration);
            } else {
                finishLastResponse(httpConnection);
            }
        }

        // remove the connection from the timeout handler
        idleStartTime = Long.MAX_VALUE;

        if (inUse) LOG.warn(MISUSE_MESSAGE);
        inUse = true;
        
        return httpConnection;
    }

    /**
     * @see HttpConnectionManager#releaseConnection(org.apache.commons.httpclient.HttpConnection)
     */
    public void releaseConnection(HttpHostConnection conn) {
        if (conn != httpConnection) {
            throw new IllegalStateException("Unexpected release of an unknown connection.");
        }
        if (this.alwaysClose) {
            closeConnection();
        } else {
            // make sure the connection is reuseable
            finishLastResponse(httpConnection);
        }
        
        inUse = false;

        // track the time the connection was made idle
        idleStartTime = System.currentTimeMillis();
    }

    public HttpParams getParams() {
        return this.params;
    }

    public void setParams(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        this.params = params;
    }
    
    /**
     * @since 3.0
     */
    public void closeIdleConnections(long idleTimeout) {
        long maxIdleTime = System.currentTimeMillis() - idleTimeout;
        if (idleStartTime <= maxIdleTime) {
            closeConnection();
        }
    }
    
    /**
     * since 3.1
     */
    public void shutdown() {
        closeConnection();
    }
    
}