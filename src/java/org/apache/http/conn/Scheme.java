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
package org.apache.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.io.SecureSocketFactory;
import org.apache.http.io.SocketFactory;
import org.apache.http.util.LangUtils;

/**
 * A class to encapsulate the specifics of a protocol scheme. This class also
 * provides the ability to customize the set and characteristics of the
 * schemes used.
 * 
 * <p>One use case for modifying the default set of protocols would be to set a
 * custom SSL socket factory.  This would look something like the following:
 * <pre> 
 * Scheme myHTTPS = new Scheme( "https", new MySSLSocketFactory(), 443 );
 * 
 * Scheme.registerScheme( "https", myHTTPS );
 * </pre>
 *
 * @author Michael Becke 
 * @author Jeff Dever
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 *  
 * @since 2.0 
 */
public class Scheme {

    /** The available schemes */
    private static final Map SCHEMES = Collections.synchronizedMap(new HashMap());

    /**
     * Registers a new scheme with the given identifier. If a scheme with
     * the given ID already exists it will be overridden.  This ID is the same
     * one used to retrieve the scheme from getScheme(String).
     * 
     * @param id the identifier for this scheme
     * @param scheme the scheme to register
     * 
     * @see #getScheme(String)
     */
    public static void registerScheme(final String id, final Scheme scheme) {
        if (id == null) {
            throw new IllegalArgumentException("Id may not be null");
        }
        if (scheme == null) {
            throw new IllegalArgumentException("Scheme may not be null");
        }
        SCHEMES.put(id, scheme);
    }

    /**
     * Unregisters the scheme with the given ID.
     * 
     * @param id the ID of the scheme to remove
     */
    public static void unregisterScheme(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("Id may not be null");
        }
        SCHEMES.remove(id);
    }

    /**
     * Gets the scheme with the given ID.
     * 
     * @param id the scheme ID
     * 
     * @return Scheme a scheme
     * 
     * @throws IllegalStateException if a scheme with the ID cannot be found
     */
    public static Scheme getScheme(String id) 
        throws IllegalStateException {

        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }
        Scheme scheme = (Scheme) SCHEMES.get(id);
        if (scheme == null) {
            throw new IllegalStateException("Unsupported scheme: '" + id + "'");
        }
        return scheme;
    } 

    /** the scheme of this scheme (e.g. http, https) */
    private String name;
    
    /** The socket factory for this scheme */
    private SocketFactory socketFactory;
    
    /** The default port for this scheme */
    private int defaultPort;
    
    /** True if this scheme is secure */
    private boolean secure;
  
    /**
     * Constructs a new Protocol. Whether the created scheme is secure depends on
     * the class of <code>factory</code>.
     * 
     * @param name the scheme name (e.g. http, https)
     * @param factory the factory for creating sockets for communication using
     * this scheme
     * @param defaultPort the port this scheme defaults to
     */
    public Scheme(final String name, final SocketFactory factory, int defaultPort) {
        
        if (name == null) {
            throw new IllegalArgumentException("Scheme name may not be null");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Socket factory may not be null");
        }
        if (defaultPort <= 0) {
            throw new IllegalArgumentException("Port is invalid: " + defaultPort);
        }
        
        this.name = name;
        this.socketFactory = factory;
        this.defaultPort = defaultPort;
        this.secure = (factory instanceof SecureSocketFactory);
    }
    
    /**
     * Returns the defaultPort.
     * @return int
     */
    public int getDefaultPort() {
        return defaultPort;
    }

    /**
     * Returns the socketFactory.  If secure the factory is a SecureSocketFactory.
     * @return SocketFactory
     */
    public SocketFactory getSocketFactory() {
        return socketFactory;
    }

    /**
     * Returns the scheme.
     * @return The scheme
     */
    public String getName() {
        return name;
    }

    /**
     * Returns true if this scheme is secure
     * @return true if this scheme is secure
     */
    public boolean isSecure() {
        return secure;
    }
    
    /**
     * Resolves the correct port for this scheme.  Returns the given port if
     * valid or the default port otherwise.
     * 
     * @param port the port to be resolved
     * 
     * @return the given port or the defaultPort
     */
    public int resolvePort(int port) {
        return port <= 0 ? getDefaultPort() : port;
    }

    /**
     * Return a string representation of this object.
     * @return a string representation of this object.
     */
    public String toString() {
    	CharArrayBuffer buffer = new CharArrayBuffer(32);
    	buffer.append(this.name);
    	buffer.append(':');
    	buffer.append(Integer.toString(this.defaultPort));
        return buffer.toString();
    }
    
    /**
     * Return true if the specified object equals this object.
     * @param obj The object to compare against.
     * @return true if the objects are equal.
     */
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof Scheme) {
            Scheme p = (Scheme) obj;
            return (
                defaultPort == p.getDefaultPort()
                && name.equalsIgnoreCase(p.getName())
                && secure == p.isSecure()
                && socketFactory.equals(p.getSocketFactory()));
            
        } else {
            return false;
        }
        
    }

    /**
     * Return a hash code for this object
     * @return The hash code.
     */
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.defaultPort);
        hash = LangUtils.hashCode(hash, this.name.toLowerCase());
        hash = LangUtils.hashCode(hash, this.secure);
        hash = LangUtils.hashCode(hash, this.socketFactory);
        return hash;
    }
}
