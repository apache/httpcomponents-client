/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

package org.apache.http.cookie;

import java.util.Date;

import org.apache.http.util.CharArrayBuffer;

/**
 * <p>
 * HTTP "magic-cookie" represents a piece of state information
 * that the HTTP agent and the target server can exchange to maintain 
 * a session.
 * </p>
 * 
 * @author B.C. Holmes
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:dsale@us.britannica.com">Doug Sale</a>
 * @author Rod Waldhoff
 * @author dIon Gillard
 * @author Sean C. Sullivan
 * @author <a href="mailto:JEvans@Cyveillance.com">John Evans</a>
 * @author Marc A. Saegesser
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * 
 * @version $Revision$
 */
public class Cookie {

    /**
     * Default Constructor taking a name and a value. The value may be null.
     * 
     * @param name The name.
     * @param value The value.
     */
    public Cookie(final String name, final String value) {
        super();
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name.
     *
     * @return String name The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the value.
     *
     * @return String value The current value.
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Returns the comment describing the purpose of this cookie, or
     * <tt>null</tt> if no such comment has been defined.
     * 
     * @return comment 
     *
     * @see #setComment(String)
     */
    public String getComment() {
        return cookieComment;
    }

    /**
     * If a user agent (web browser) presents this cookie to a user, the
     * cookie's purpose will be described using this comment.
     * 
     * @param comment
     *  
     * @see #getComment()
     */
    public void setComment(String comment) {
        cookieComment = comment;
    }

    /**
     * Returns the expiration {@link Date} of the cookie, or <tt>null</tt>
     * if none exists.
     * <p><strong>Note:</strong> the object returned by this method is 
     * considered immutable. Changing it (e.g. using setTime()) could result
     * in undefined behaviour. Do so at your peril. </p>
     * @return Expiration {@link Date}, or <tt>null</tt>.
     *
     * @see #setExpiryDate(java.util.Date)
     *
     */
    public Date getExpiryDate() {
        return cookieExpiryDate;
    }

    /**
     * Sets expiration date.
     * <p><strong>Note:</strong> the object returned by this method is considered
     * immutable. Changing it (e.g. using setTime()) could result in undefined 
     * behaviour. Do so at your peril.</p>
     *
     * @param expiryDate the {@link Date} after which this cookie is no longer valid.
     *
     * @see #getExpiryDate
     *
     */
    public void setExpiryDate (Date expiryDate) {
        cookieExpiryDate = expiryDate;
    }


    /**
     * Returns <tt>false</tt> if the cookie should be discarded at the end
     * of the "session"; <tt>true</tt> otherwise.
     *
     * @return <tt>false</tt> if the cookie should be discarded at the end
     *         of the "session"; <tt>true</tt> otherwise
     */
    public boolean isPersistent() {
        return (null != cookieExpiryDate);
    }


    /**
     * Returns domain attribute of the cookie.
     * 
     * @return the value of the domain attribute
     *
     * @see #setDomain(java.lang.String)
     */
    public String getDomain() {
        return cookieDomain;
    }

    /**
     * Sets the domain attribute.
     * 
     * @param domain The value of the domain attribute
     *
     * @see #getDomain
     */
    public void setDomain(String domain) {
        if (domain != null) {
            cookieDomain = domain.toLowerCase();
        } else {
            cookieDomain = null;
        }
    }


    /**
     * Returns the path attribute of the cookie
     * 
     * @return The value of the path attribute.
     * 
     * @see #setPath(java.lang.String)
     */
    public String getPath() {
        return cookiePath;
    }

    /**
     * Sets the path attribute.
     *
     * @param path The value of the path attribute
     *
     * @see #getPath
     *
     */
    public void setPath(String path) {
        cookiePath = path;
    }

    /**
     * @return <code>true</code> if this cookie should only be sent over secure connections.
     * @see #setSecure(boolean)
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Sets the secure attribute of the cookie.
     * <p>
     * When <tt>true</tt> the cookie should only be sent
     * using a secure protocol (https).  This should only be set when
     * the cookie's originating server used a secure protocol to set the
     * cookie's value.
     *
     * @param secure The value of the secure attribute
     * 
     * @see #isSecure()
     */
    public void setSecure (boolean secure) {
        isSecure = secure;
    }

    /**
     * Returns the version of the cookie specification to which this
     * cookie conforms.
     *
     * @return the version of the cookie.
     * 
     * @see #setVersion(int)
     *
     */
    public int getVersion() {
        return cookieVersion;
    }

    /**
     * Sets the version of the cookie specification to which this
     * cookie conforms. 
     *
     * @param version the version of the cookie.
     * 
     * @see #getVersion
     */
    public void setVersion(int version) {
        cookieVersion = version;
    }

    /**
     * Returns true if this cookie has expired.
     * @param date Current time
     * 
     * @return <tt>true</tt> if the cookie has expired.
     */
    public boolean isExpired(final Date date) {
        if (date == null) {
            throw new IllegalArgumentException("Date may not be null");
        }
        return (cookieExpiryDate != null  
            && cookieExpiryDate.getTime() <= date.getTime());
    }

    /**
     * Indicates whether the cookie had a path specified in a 
     * path attribute of the <tt>Set-Cookie</tt> header. This value
     * is important for generating the <tt>Cookie</tt> header because 
     * some cookie specifications require that the <tt>Cookie</tt> header 
     * should only include a path attribute if the cookie's path 
     * was specified in the <tt>Set-Cookie</tt> header.
     *
     * @param value <tt>true</tt> if the cookie's path was explicitly 
     * set, <tt>false</tt> otherwise.
     * 
     * @see #isPathAttributeSpecified
     */
    public void setPathAttributeSpecified(boolean value) {
        hasPathAttribute = value;
    }

    /**
     * Returns <tt>true</tt> if cookie's path was set via a path attribute
     * in the <tt>Set-Cookie</tt> header.
     *
     * @return value <tt>true</tt> if the cookie's path was explicitly 
     * set, <tt>false</tt> otherwise.
     * 
     * @see #setPathAttributeSpecified
     */
    public boolean isPathAttributeSpecified() {
        return hasPathAttribute;
    }

    /**
     * Indicates whether the cookie had a domain specified in a 
     * domain attribute of the <tt>Set-Cookie</tt> header. This value
     * is important for generating the <tt>Cookie</tt> header because 
     * some cookie specifications require that the <tt>Cookie</tt> header 
     * should only include a domain attribute if the cookie's domain 
     * was specified in the <tt>Set-Cookie</tt> header.
     *
     * @param value <tt>true</tt> if the cookie's domain was explicitly 
     * set, <tt>false</tt> otherwise.
     *
     * @see #isDomainAttributeSpecified
     */
    public void setDomainAttributeSpecified(boolean value) {
        hasDomainAttribute = value;
    }

    /**
     * Returns <tt>true</tt> if cookie's domain was set via a domain 
     * attribute in the <tt>Set-Cookie</tt> header.
     *
     * @return value <tt>true</tt> if the cookie's domain was explicitly 
     * set, <tt>false</tt> otherwise.
     *
     * @see #setDomainAttributeSpecified
     */
    public boolean isDomainAttributeSpecified() {
        return hasDomainAttribute;
    }

    public String toString() {
        CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("[version: ");
        buffer.append(Integer.toString(this.cookieVersion));
        buffer.append("]");
        buffer.append("[name: ");
        buffer.append(this.name);
        buffer.append("]");
        buffer.append("[name: ");
        buffer.append(this.value);
        buffer.append("]");
        buffer.append("[domain: ");
        buffer.append(this.cookieDomain);
        buffer.append("]");
        buffer.append("[path: ");
        buffer.append(this.cookiePath);
        buffer.append("]");
        buffer.append("[expiry: ");
        buffer.append(this.cookieExpiryDate);
        buffer.append("]");
        return buffer.toString();
    }
    
    
   // ----------------------------------------------------- Instance Variables


    private final String name;
    private final String value;

   /** Comment attribute. */
   private String  cookieComment;

   /** Domain attribute. */
   private String  cookieDomain;

   /** Expiration {@link Date}. */
   private Date    cookieExpiryDate;

   /** Path attribute. */
   private String  cookiePath;

   /** My secure flag. */
   private boolean isSecure;

   /**
    * Specifies if the set-cookie header included a Path attribute for this
    * cookie
    */
   private boolean hasPathAttribute = false;

   /**
    * Specifies if the set-cookie header included a Domain attribute for this
    * cookie
    */
   private boolean hasDomainAttribute = false;

   /** The version of the cookie specification I was created from. */
   private int     cookieVersion = 0;

}

