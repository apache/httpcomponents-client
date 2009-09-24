/*
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
 */

package org.apache.http.impl.auth;

import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BasicHeader;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

/**
 * SPNEGO (Simple and Protected GSSAPI Negotiation Mechanism) authentication 
 * scheme.
 * 
 * @since 4.1
 */
public class NegotiateScheme implements AuthScheme {
    
    private static final int UNINITIATED         = 0;
    private static final int INITIATED           = 1;
    private static final int NEGOTIATING         = 3;
    private static final int ESTABLISHED         = 4;
    private static final int FAILED              = Integer.MAX_VALUE;
    private static final String SPNEGO_OID        = "1.3.6.1.5.5.2";
    private static final String KERBEROS_OID        = "1.2.840.113554.1.2.2";
    
    private final Log log;
    
    /* stripPort removes ports from the generated Service Name.
     * Helpful if you don't/can't have all SN:port combo's in AD/Directory.
     * Probably a debatable addition.
    */
    private boolean stripPort = false;
    /* spnegoCreate is used to generate an SPNEGO wrapper around
     * for JDKs < 1.6.
     */
    private boolean spnegoCreate = false;
    
    private SpnegoTokenGenerator spengoGenerator = null;
    
    private GSSContext context = null;

    /** Authentication process state */
    private int state;

    /** base64 decoded challenge **/
    byte[] token = new byte[0];
    
    private Oid negotiationOid = null;
    
    /**
     * Default constructor for the Negotiate authentication scheme.
     * 
     */
    public NegotiateScheme() {
        super();
        log = LogFactory.getLog(getClass());
        state = UNINITIATED;
    }

    /**
     * Init GSSContext for negotiation.
     * 
     * @param server servername only (e.g: radar.it.su.se)
     */
    protected void init(String server) throws GSSException {
        if (log.isDebugEnabled()) {
            log.debug("init " + server);
        }
        /* Using the SPNEGO OID is the correct method.
         * Kerberos v5 works for IIS but not JBoss. Unwrapping
         * the initial token when using SPNEGO OID looks like what is
         * described here... 
         * 
         * http://msdn.microsoft.com/en-us/library/ms995330.aspx
         * 
         * Another helpful URL...
         * 
         * http://publib.boulder.ibm.com/infocenter/wasinfo/v7r0/index.jsp?topic=/com.ibm.websphere.express.doc/info/exp/ae/tsec_SPNEGO_token.html
         * 
         * Unfortunately SPNEGO is JRE >=1.6.
         */
        
        /** Try SPNEGO by default, fall back to Kerberos later if error */
        negotiationOid  = new Oid(SPNEGO_OID);
        
        boolean tryKerberos = false;
        try{
            GSSManager manager = GSSManager.getInstance();
            GSSName serverName = manager.createName("HTTP/"+server, null); 
            context = manager.createContext(
                    serverName.canonicalize(negotiationOid), negotiationOid, null,
                    GSSContext.DEFAULT_LIFETIME);
            context.requestMutualAuth(true); 
            context.requestCredDeleg(true);
        } catch (GSSException ex){
            // BAD MECH means we are likely to be using 1.5, fall back to Kerberos MECH.
            // Rethrow any other exception.
            if (ex.getMajor() == GSSException.BAD_MECH ){
                log.debug("GSSException BAD_MECH, retry with Kerberos MECH");
                tryKerberos = true;
            } else {
                throw ex;
            }
            
        }
        if (tryKerberos){
            /* Kerberos v5 GSS-API mechanism defined in RFC 1964.*/
            log.debug("Using Kerberos MECH " + KERBEROS_OID);
            negotiationOid  = new Oid(KERBEROS_OID);
            GSSManager manager = GSSManager.getInstance();
            GSSName serverName = manager.createName("HTTP/"+server, null); 
            context = manager.createContext(
                    serverName.canonicalize(negotiationOid), negotiationOid, null,
                    GSSContext.DEFAULT_LIFETIME);
            context.requestMutualAuth(true); 
            context.requestCredDeleg(true);
        }
        state = INITIATED;
    }

    /**
     * Tests if the Negotiate authentication process has been completed.
     * 
     * @return <tt>true</tt> if authorization has been processed,
     *   <tt>false</tt> otherwise.
     * 
     */
    public boolean isComplete() {
        return this.state == ESTABLISHED || this.state == FAILED;
    }

    /**
     * Returns textual designation of the Negotiate authentication scheme.
     * 
     * @return <code>Negotiate</code>
     */
    public String getSchemeName() {
        return "Negotiate";
    }

    /**
     * Produces Negotiate authorization Header based on token created by 
     * processChallenge.
     * 
     * @param credentials Never used be the Negotiate scheme but must be provided to 
     * satisfy common-httpclient API. Credentials from JAAS will be used instead.
     * @param request The request being authenticated
     * 
     * @throws AuthenticationException if authorisation string cannot 
     *   be generated due to an authentication failure
     * 
     * @return an Negotiate authorisation Header
     */

    public Header authenticate(Credentials credentials,
            HttpRequest request) throws AuthenticationException {
        if (state == UNINITIATED) {
            throw new IllegalStateException(
                    "Negotiation authentication process has not been initiated");
        }

        try {

            if (context==null) {
                if (isStripPort()) {
                    init( (request.getLastHeader("Host")).getValue().replaceAll(":[0-9]+$", "") );
                } else {
                    init( (request.getLastHeader("Host")).getValue());
                }
            }
            
            // HTTP 1.1 issue:
            // Mutual auth will never complete to do 200 instead of 401 in 
            // return from server. "state" will never reach ESTABLISHED
            // but it works anyway

            token = context.initSecContext(token, 0, token.length);
            
            /* 
             * IIS accepts Kerberos and SPNEGO tokens. Some other servers Jboss, Glassfish?
             * seem to only accept SPNEGO. Below wraps Kerberos into SPNEGO token.
             */
            if(isSpnegoCreate() && negotiationOid.toString().equals(KERBEROS_OID)
                    && spengoGenerator != null )
                token = spengoGenerator.generateSpnegoDERObject(token);

            if (log.isDebugEnabled()) {
                log.info("got token, sending " + token.length + " bytes to server");
            }
        } catch (GSSException gsse) {
            state = FAILED;
            if (gsse.getMajor() == GSSException.DEFECTIVE_CREDENTIAL
                    || gsse.getMajor() == GSSException.CREDENTIALS_EXPIRED)
                throw new InvalidCredentialsException(gsse.getMessage(), gsse);
            if (gsse.getMajor() == GSSException.NO_CRED )
                throw new InvalidCredentialsException(gsse.getMessage(), gsse);
            if (gsse.getMajor() == GSSException.DEFECTIVE_TOKEN
                    || gsse.getMajor() == GSSException.DUPLICATE_TOKEN
                    || gsse.getMajor() == GSSException.OLD_TOKEN)
                throw new AuthenticationException(gsse.getMessage(), gsse);
            // other error
            throw new AuthenticationException(gsse.getMessage());
        } catch (IOException ex){
            state = FAILED;
            throw new AuthenticationException(ex.getMessage());
        }
        return new BasicHeader("Authorization", "Negotiate " + 
                new String(new Base64().encode(token)) );
    }


    /**
     * Returns the authentication parameter with the given name, if available.
     * 
     * <p>There are no valid parameters for Negotiate authentication so this 
     * method always returns <tt>null</tt>.</p>
     * 
     * @param name The name of the parameter to be returned
     * 
     * @return the parameter with the given name
     */
    public String getParameter(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Parameter name may not be null"); 
        }
        return null;
    }

    /**
     * The concept of an authentication realm is not supported by the Negotiate 
     * authentication scheme. Always returns <code>null</code>.
     * 
     * @return <code>null</code>
     */
    public String getRealm() {
        return null;
    }

    /**
     * Returns <tt>true</tt>. 
     * Negotiate authentication scheme is connection based.
     * 
     * @return <tt>true</tt>.
     */
    public boolean isConnectionBased() {
        return true;
    }

    /**
     * Processes the Negotiate challenge.
     *  
     */
    public void processChallenge(Header header) throws MalformedChallengeException {
        if (log.isDebugEnabled()) {
            log.debug("Challenge header: " + header);
        }
        String challenge = header.getValue();
        
        if (challenge.startsWith("Negotiate")) {
            if(isComplete() == false)
                state = NEGOTIATING;

            if (challenge.startsWith("Negotiate ")){
                token = new Base64().decode(challenge.substring(10).getBytes());
                if (log.isDebugEnabled()) {
                    log.debug("challenge = " + challenge.substring(10));
                }
            } else {
                token = new byte[0];
            }
        }
    }

    public boolean isStripPort() {
        return stripPort;
    }

    /**
     * Strips the port off the Kerberos service name e.g. HTTP/webserver.ad.net:8080 -> HTTP/webserver.ad.net
     * @param stripport
     */
    public void setStripPort(boolean stripport) {
        if (stripport){
            log.debug("Will strip ports off Service Names e.g. HTTP/server:8080 -> HTTP/server");
        } else{
            log.debug("Will NOT strip ports off Service Names e.g. HTTP/server:8080 -> HTTP/server");
        }
        stripPort = stripport;
    }

    /**
     * Sould an attempt be made to wrap Kerberos ticket up as an SPNEGO token.
     * Use only with Java <= 1.5
     * @return
     */
    public boolean isSpnegoCreate() {
        return spnegoCreate;
    }

    /**
     * Set to true if an attempt should be made to wrap Kerberos ticket up as an SPNEGO token.
     * Use only with Java <= 1.5
     * @param spnegocreate - set to true do attempt SPNEGO wrapping 
     */
    public void setSpnegoCreate(boolean spnegocreate) {
        spnegoCreate = spnegocreate;
    }

    /**
     * Inject the class to be used to generate an SPNEGO token from a Kerberos ticket.
     * Use only with Java <= 1.5 , tested against Jboss Negotiate.
     * @param spengoGenerator - An SpnegoTokenGenerator implementation Class
     */
    public void setSpengoGenerator(SpnegoTokenGenerator SpengoGenerator) {
        this.spengoGenerator = SpengoGenerator;
    }
    
}
