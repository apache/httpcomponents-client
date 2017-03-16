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

package org.apache.http.impl.auth;


import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.NTCredentials;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.CharsetUtils;


/**
 * <p>
 * Client implementation of the CredSSP protocol specified in [MS-CSSP].
 * </p>
 * <p>
 * Note: This is implementation is NOT GSS based. It should be. But there is no Java NTLM
 * implementation as GSS module. Maybe the NTLMEngine can be converted to GSS and then this
 * can be also switched to GSS. In fact it only works in CredSSP+NTLM case.
 * </p>
 * <p>
 * Based on [MS-CSSP]: Credential Security Support Provider (CredSSP) Protocol (Revision 13.0, 7/14/2016).
 * The implementation was inspired by Python CredSSP and NTLM implementation by Jordan Borean.
 * </p>
 */
public class CredSspScheme extends AuthSchemeBase
{
    private static final Charset UNICODE_LITTLE_UNMARKED = CharsetUtils.lookup( "UnicodeLittleUnmarked" );
    public static final String SCHEME_NAME = "CredSSP";

    private final Log log = LogFactory.getLog( CredSspScheme.class );

    enum State
    {
        // Nothing sent, nothing received
        UNINITIATED,

        // We are handshaking. Several messages are exchanged in this state
        TLS_HANDSHAKE,

        // TLS handshake finished. Channel established
        TLS_HANDSHAKE_FINISHED,

        // NTLM NEGOTIATE message sent (strictly speaking this should be SPNEGO)
        NEGO_TOKEN_SENT,

        // NTLM CHALLENGE message received  (strictly speaking this should be SPNEGO)
        NEGO_TOKEN_RECEIVED,

        // NTLM AUTHENTICATE message sent together with a server public key
        PUB_KEY_AUTH_SENT,

        // Server public key authentication message received
        PUB_KEY_AUTH_RECEIVED,

        // Credentials message sent. Protocol exchange finished.
        CREDENTIALS_SENT;
    }

    private State state;
    private SSLEngine sslEngine;
    private NTLMEngineImpl ntlmEngine;
    private CredSspTsRequest lastReceivedTsRequest;
    private NTLMEngineImpl.Handle ntlmOutgoingHandle;
    private NTLMEngineImpl.Handle ntlmIncomingHandle;
    private byte[] peerPublicKey;

    /**
     * Enabling or disabling the development trace (extra logging).
     * We do NOT want this to be enabled by default.
     * We do not want to enable it even if full logging is turned on.
     * This may leak sensitive key material to the log files. It is supposed to be used only
     * for development purposes. We really need this to diagnose protocol issues. Most of the
     * protocol is TLS-encrypted. Some parts are encrypted several times. We cannot use packet
     * sniffer or other tools for diagnostics. We need to see the values before encryption.
     */
    private static boolean develTrace = false;


    public CredSspScheme()
    {
        state = State.UNINITIATED;
    }


    @Override
    public String getSchemeName()
    {
        return SCHEME_NAME;
    }


    @Override
    public String getParameter( final String name )
    {
        return null;
    }


    @Override
    public String getRealm()
    {
        return null;
    }


    @Override
    public boolean isConnectionBased()
    {
        return true;
    }


    private SSLEngine getSSLEngine()
    {
        if ( sslEngine == null )
        {
            sslEngine = createSSLEngine();
        }
        return sslEngine;
    }


    private SSLEngine createSSLEngine()
    {
        SSLContext sslContext;
        try
        {
            sslContext = SSLContexts.custom().build();
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new RuntimeException( "Error creating SSL Context: " + e.getMessage(), e );
        }
        catch ( KeyManagementException e )
        {
            throw new RuntimeException( "Error creating SSL Context: " + e.getMessage(), e );
        }

        final X509TrustManager tm = new X509TrustManager()
        {

            @Override
            public void checkClientTrusted( final X509Certificate[] chain, final String authType )
                throws CertificateException
            {
                // Nothing to do.
            }


            @Override
            public void checkServerTrusted( final X509Certificate[] chain, final String authType )
                throws CertificateException
            {
                // Nothing to do, accept all. CredSSP server is using its own certificate without any
                // binding to the PKI trust chains. The public key is verified as part of the CredSSP
                // protocol exchange.
            }


            @Override
            public X509Certificate[] getAcceptedIssuers()
            {
                return null;
            }

        };
        try
        {
            sslContext.init( null, new TrustManager[]
                { tm }, null );
        }
        catch ( KeyManagementException e )
        {
            throw new RuntimeException( "SSL Context initialization error: " + e.getMessage(), e );
        }
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode( true );
        return sslEngine;
    }


    @Override
    protected void parseChallenge( final CharArrayBuffer buffer, final int beginIndex, final int endIndex )
        throws MalformedChallengeException
    {
        final String inputString = buffer.substringTrimmed( beginIndex, endIndex );
        if ( develTrace )
        {
            log.trace( "<< Received: " + inputString );
        }

        if ( inputString.isEmpty() )
        {
            if ( state == State.UNINITIATED )
            {
                // This is OK, just send out first message. That should start TLS handshake
            }
            else
            {
                final String msg = "Received unexpected empty input in state " + state;
                log.error( msg );
                throw new MalformedChallengeException( msg );
            }
        }

        if ( state == State.TLS_HANDSHAKE )
        {
            unwrapHandshake( inputString );
            if ( develTrace )
            {
                log.trace( "TLS handshake status: " + getSSLEngine().getHandshakeStatus() );
            }
            if ( getSSLEngine().getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING )
            {
                log.trace( "TLS handshake finished" );
                state = State.TLS_HANDSHAKE_FINISHED;
            }
        }

        if ( state == State.NEGO_TOKEN_SENT )
        {
            final ByteBuffer buf = unwrap( inputString );
            state = State.NEGO_TOKEN_RECEIVED;
            lastReceivedTsRequest = CredSspTsRequest.createDecoded( buf );
            if ( develTrace )
            {
                log.trace( "Received tsrequest(negotoken:CHALLENGE):\n" + lastReceivedTsRequest.debugDump() );
            }
        }

        if ( state == State.PUB_KEY_AUTH_SENT )
        {
            final ByteBuffer buf = unwrap( inputString );
            state = State.PUB_KEY_AUTH_RECEIVED;
            lastReceivedTsRequest = CredSspTsRequest.createDecoded( buf );
            if ( develTrace )
            {
                log.trace( "Received tsrequest(pubKeyAuth):\n" + lastReceivedTsRequest.debugDump() );
            }
        }
    }


    @Override
    @Deprecated
    public Header authenticate(
        final Credentials credentials,
        final HttpRequest request ) throws AuthenticationException
    {
        return authenticate( credentials, request, null );
    }


    @Override
    public Header authenticate(
        final Credentials credentials,
        final HttpRequest request,
        final HttpContext context ) throws AuthenticationException
    {
        NTCredentials ntcredentials = null;
        try
        {
            ntcredentials = ( NTCredentials ) credentials;
        }
        catch ( final ClassCastException e )
        {
            throw new InvalidCredentialsException(
                "Credentials cannot be used for CredSSP authentication: "
                    + credentials.getClass().getName() );
        }

        if ( ntlmEngine == null )
        {

            ntlmEngine = new NTLMEngineImpl( ntcredentials, true );
        }

        String outputString = null;

        if ( state == State.UNINITIATED )
        {
            beginTlsHandshake();
            outputString = wrapHandshake();
            state = State.TLS_HANDSHAKE;

        }
        else if ( state == State.TLS_HANDSHAKE )
        {
            outputString = wrapHandshake();

        }
        else if ( state == State.TLS_HANDSHAKE_FINISHED )
        {

            final int ntlmFlags = getNtlmFlags();
            final ByteBuffer buf = allocateOutBuffer();
            final NTLMEngineImpl.Type1Message ntlmNegoMessage = ntlmEngine.generateType1MsgObject( ntlmFlags );
            final byte[] ntlmNegoMessageEncoded = ntlmNegoMessage.getBytes();
            final CredSspTsRequest req = CredSspTsRequest.createNegoToken( ntlmNegoMessageEncoded );
            req.encode( buf );
            buf.flip();
            outputString = wrap( buf );
            state = State.NEGO_TOKEN_SENT;

        }
        else if ( state == State.NEGO_TOKEN_RECEIVED )
        {
            final ByteBuffer buf = allocateOutBuffer();
            final NTLMEngineImpl.Type2Message ntlmType2Message = ntlmEngine
                .parseType2Message( lastReceivedTsRequest.getNegoToken() );

            final X509Certificate peerServerCertificate = getPeerServerCertificate();

            final NTLMEngineImpl.Type3Message ntlmAuthenticateMessage = ntlmEngine
                .generateType3MsgObject( peerServerCertificate );
            final byte[] ntlmAuthenticateMessageEncoded = ntlmAuthenticateMessage.getBytes();

            ntlmOutgoingHandle = ntlmEngine.createClientHandle();
            ntlmIncomingHandle = ntlmEngine.createServer();

            final CredSspTsRequest req = CredSspTsRequest.createNegoToken( ntlmAuthenticateMessageEncoded );
            peerPublicKey = getSubjectPublicKeyDer( peerServerCertificate.getPublicKey() );
            final byte[] pubKeyAuth = createPubKeyAuth();
            req.setPubKeyAuth( pubKeyAuth );

            req.encode( buf );
            buf.flip();
            outputString = wrap( buf );
            state = State.PUB_KEY_AUTH_SENT;

        }
        else if ( state == State.PUB_KEY_AUTH_RECEIVED )
        {
            verifyPubKeyAuthResponse( lastReceivedTsRequest.getPubKeyAuth() );
            final byte[] authInfo = createAuthInfo( ntcredentials );
            final CredSspTsRequest req = CredSspTsRequest.createAuthInfo( authInfo );

            final ByteBuffer buf = allocateOutBuffer();
            req.encode( buf );
            buf.flip();
            outputString = wrap( buf );
            state = State.CREDENTIALS_SENT;
        }
        else
        {
            throw new AuthenticationException( "Wrong state " + state );
        }
        final CharArrayBuffer buffer = new CharArrayBuffer( 32 );
        if ( isProxy() )
        {
            buffer.append( AUTH.PROXY_AUTH_RESP );
        }
        else
        {
            buffer.append( AUTH.WWW_AUTH_RESP );
        }
        buffer.append( ": CredSSP " );
        buffer.append( outputString );
        return new BufferedHeader( buffer );
    }


    private int getNtlmFlags()
    {
        return NTLMEngineImpl.FLAG_REQUEST_OEM_ENCODING |
            NTLMEngineImpl.FLAG_REQUEST_SIGN |
            NTLMEngineImpl.FLAG_REQUEST_SEAL |
            NTLMEngineImpl.FLAG_DOMAIN_PRESENT |
            NTLMEngineImpl.FLAG_REQUEST_ALWAYS_SIGN |
            NTLMEngineImpl.FLAG_REQUEST_NTLM2_SESSION |
            NTLMEngineImpl.FLAG_TARGETINFO_PRESENT |
            NTLMEngineImpl.FLAG_REQUEST_VERSION |
            NTLMEngineImpl.FLAG_REQUEST_128BIT_KEY_EXCH |
            NTLMEngineImpl.FLAG_REQUEST_EXPLICIT_KEY_EXCH |
            NTLMEngineImpl.FLAG_REQUEST_56BIT_ENCRYPTION;
    }


    private X509Certificate getPeerServerCertificate() throws AuthenticationException
    {
        Certificate[] peerCertificates;
        try
        {
            peerCertificates = sslEngine.getSession().getPeerCertificates();
        }
        catch ( SSLPeerUnverifiedException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
        for ( Certificate peerCertificate : peerCertificates )
        {
            if ( !( peerCertificate instanceof X509Certificate ) )
            {
                continue;
            }
            final X509Certificate peerX509Cerificate = ( X509Certificate ) peerCertificate;
            if ( peerX509Cerificate.getBasicConstraints() != -1 )
            {
                continue;
            }
            return peerX509Cerificate;
        }
        return null;
    }


    private byte[] createPubKeyAuth() throws AuthenticationException
    {
        return ntlmOutgoingHandle.signAndEncryptMessage( peerPublicKey );
    }


    private void verifyPubKeyAuthResponse( final byte[] pubKeyAuthResponse ) throws AuthenticationException
    {
        final byte[] pubKeyReceived = ntlmIncomingHandle.decryptAndVerifySignedMessage( pubKeyAuthResponse );

        // assert: pubKeyReceived = peerPublicKey + 1
        // The following algorithm is a bit simplified. But due to the ASN.1 encoding the first byte
        // of the public key will be 0x30 we can pretty much rely on a fact that there will be no carry
        if ( peerPublicKey.length != pubKeyReceived.length )
        {
            throw new AuthenticationException( "Public key mismatch in pubKeyAuth response" );
        }
        if ( ( peerPublicKey[0] + 1 ) != pubKeyReceived[0] )
        {
            throw new AuthenticationException( "Public key mismatch in pubKeyAuth response" );
        }
        for ( int i = 1; i < peerPublicKey.length; i++ )
        {
            if ( peerPublicKey[i] != pubKeyReceived[i] )
            {
                throw new AuthenticationException( "Public key mismatch in pubKeyAuth response" );
            }
        }
        log.trace( "Received public key response is valid" );
    }


    private byte[] createAuthInfo( final NTCredentials ntcredentials ) throws AuthenticationException
    {

        final byte[] domainBytes = encodeUnicode( ntcredentials.getDomain() );
        final byte[] domainOctetStringBytesLengthBytes = DerUtil.encodeLength( domainBytes.length );
        final int domainNameLength = 1 + domainOctetStringBytesLengthBytes.length + domainBytes.length;
        final byte[] domainNameLengthBytes = DerUtil.encodeLength( domainNameLength );

        final byte[] usernameBytes = encodeUnicode( ntcredentials.getUserName() );
        final byte[] usernameOctetStringBytesLengthBytes = DerUtil.encodeLength( usernameBytes.length );
        final int userNameLength = 1 + usernameOctetStringBytesLengthBytes.length + usernameBytes.length;
        final byte[] userNameLengthBytes = DerUtil.encodeLength( userNameLength );

        final byte[] passwordBytes = encodeUnicode( ntcredentials.getPassword() );
        final byte[] passwordOctetStringBytesLengthBytes = DerUtil.encodeLength( passwordBytes.length );
        final int passwordLength = 1 + passwordOctetStringBytesLengthBytes.length + passwordBytes.length;
        final byte[] passwordLengthBytes = DerUtil.encodeLength( passwordLength );

        final int tsPasswordLength = 1 + domainNameLengthBytes.length + domainNameLength +
            1 + userNameLengthBytes.length + userNameLength +
            1 + passwordLengthBytes.length + passwordLength;
        final byte[] tsPasswordLengthBytes = DerUtil.encodeLength( tsPasswordLength );
        final int credentialsOctetStringLength = 1 + tsPasswordLengthBytes.length + tsPasswordLength;
        final byte[] credentialsOctetStringLengthBytes = DerUtil.encodeLength( credentialsOctetStringLength );
        final int credentialsLength = 1 + credentialsOctetStringLengthBytes.length + credentialsOctetStringLength;
        final byte[] credentialsLengthBytes = DerUtil.encodeLength( credentialsLength );
        final int tsCredentialsLength = 5 + 1 + credentialsLengthBytes.length + credentialsLength;
        final byte[] tsCredentialsLengthBytes = DerUtil.encodeLength( tsCredentialsLength );

        final ByteBuffer buf = ByteBuffer.allocate( 1 + tsCredentialsLengthBytes.length + tsCredentialsLength );

        // TSCredentials structure [MS-CSSP] section 2.2.1.2
        buf.put( ( byte ) 0x30 ); // seq
        buf.put( tsCredentialsLengthBytes );

        buf.put( ( byte ) ( 0x00 | 0xa0 ) ); // credType tag [0]
        buf.put( ( byte ) 3 ); // credType length
        buf.put( ( byte ) 0x02 ); // type: INTEGER
        buf.put( ( byte ) 1 ); // credType inner length
        buf.put( ( byte ) 1 ); // credType value: 1 (password)

        buf.put( ( byte ) ( 0x01 | 0xa0 ) ); // credentials tag [1]
        buf.put( credentialsLengthBytes );
        buf.put( ( byte ) 0x04 ); // type: OCTET STRING
        buf.put( credentialsOctetStringLengthBytes );

        // TSPasswordCreds structure [MS-CSSP] section 2.2.1.2.1
        buf.put( ( byte ) 0x30 ); // seq
        buf.put( tsPasswordLengthBytes );

        buf.put( ( byte ) ( 0x00 | 0xa0 ) ); // domainName tag [0]
        buf.put( domainNameLengthBytes );
        buf.put( ( byte ) 0x04 ); // type: OCTET STRING
        buf.put( domainOctetStringBytesLengthBytes );
        buf.put( domainBytes );

        buf.put( ( byte ) ( 0x01 | 0xa0 ) ); // userName tag [1]
        buf.put( userNameLengthBytes );
        buf.put( ( byte ) 0x04 ); // type: OCTET STRING
        buf.put( usernameOctetStringBytesLengthBytes );
        buf.put( usernameBytes );

        buf.put( ( byte ) ( 0x02 | 0xa0 ) ); // password tag [2]
        buf.put( passwordLengthBytes );
        buf.put( ( byte ) 0x04 ); // type: OCTET STRING
        buf.put( passwordOctetStringBytesLengthBytes );
        buf.put( passwordBytes );

        final byte[] authInfo = buf.array();
        try
        {
            return ntlmOutgoingHandle.signAndEncryptMessage( authInfo );
        }
        catch ( NTLMEngineException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
    }


    private byte[] encodeUnicode( final String string )
    {
        return string.getBytes( UNICODE_LITTLE_UNMARKED );
    }


    private byte[] getSubjectPublicKeyDer( final PublicKey publicKey ) throws AuthenticationException
    {
        // The publicKey.getEncoded() returns encoded SubjectPublicKeyInfo structure. But the CredSSP expects
        // SubjectPublicKey subfield. I have found no easy way how to get just the SubjectPublicKey from
        // java.security libraries. So let's use a primitive way and parse it out from the DER.

        try
        {
            final byte[] encodedPubKeyInfo = publicKey.getEncoded();

            final ByteBuffer buf = ByteBuffer.wrap( encodedPubKeyInfo );
            DerUtil.getByteAndAssert( buf, 0x30, "initial sequence" );
            DerUtil.parseLength( buf );
            DerUtil.getByteAndAssert( buf, 0x30, "AlgorithmIdentifier sequence" );
            final int algIdSeqLength = DerUtil.parseLength( buf );
            buf.position( buf.position() + algIdSeqLength );
            DerUtil.getByteAndAssert( buf, 0x03, "subjectPublicKey type" );
            int subjectPublicKeyLegth = DerUtil.parseLength( buf );
            // There may be leading padding byte ... or whatever that is. Skip that.
            final byte b = buf.get();
            if ( b == 0 )
            {
                subjectPublicKeyLegth--;
            }
            else
            {
                buf.position( buf.position() - 1 );
            }
            final byte[] subjectPublicKey = new byte[subjectPublicKeyLegth];
            buf.get( subjectPublicKey );
            return subjectPublicKey;
        }
        catch ( MalformedChallengeException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
    }


    private void beginTlsHandshake() throws AuthenticationException
    {
        try
        {
            getSSLEngine().beginHandshake();
        }
        catch ( SSLException e )
        {
            throw new AuthenticationException( "SSL Engine error: " + e.getMessage(), e );
        }
    }


    private ByteBuffer allocateOutBuffer()
    {
        final SSLEngine sslEngine = getSSLEngine();
        final SSLSession sslSession = sslEngine.getSession();
        return ByteBuffer.allocate( sslSession.getApplicationBufferSize() );
    }


    private String wrapHandshake() throws AuthenticationException
    {
        final ByteBuffer src = allocateOutBuffer();
        src.flip();
        final SSLEngine sslEngine = getSSLEngine();
        final SSLSession sslSession = sslEngine.getSession();
        // Needs to be twice the size as there may be two wraps during handshake.
        // Primitive and inefficient solution, but it works.
        final ByteBuffer dst = ByteBuffer.allocate( sslSession.getPacketBufferSize() * 2 );
        while ( sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP )
        {
            wrap( src, dst );
        }
        dst.flip();
        return encodeBase64( dst );
    }


    private String wrap( final ByteBuffer src ) throws AuthenticationException
    {
        final SSLEngine sslEngine = getSSLEngine();
        final SSLSession sslSession = sslEngine.getSession();
        final ByteBuffer dst = ByteBuffer.allocate( sslSession.getPacketBufferSize() );
        wrap( src, dst );
        dst.flip();
        return encodeBase64( dst );
    }


    private void wrap( final ByteBuffer src, final ByteBuffer dst ) throws AuthenticationException
    {
        final SSLEngine sslEngine = getSSLEngine();
        try
        {
            final SSLEngineResult engineResult = sslEngine.wrap( src, dst );
            if ( engineResult.getStatus() != Status.OK )
            {
                throw new AuthenticationException( "SSL Engine error status: " + engineResult.getStatus() );
            }
        }
        catch ( SSLException e )
        {
            throw new AuthenticationException( "SSL Engine wrap error: " + e.getMessage(), e );
        }
    }


    private void unwrapHandshake( final String inputString ) throws MalformedChallengeException
    {
        final SSLEngine sslEngine = getSSLEngine();
        final SSLSession sslSession = sslEngine.getSession();
        final ByteBuffer src = decodeBase64( inputString );
        final ByteBuffer dst = ByteBuffer.allocate( sslSession.getApplicationBufferSize() );
        while ( sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP )
        {
            unwrap( src, dst );
        }
    }


    private ByteBuffer unwrap( final String inputString ) throws MalformedChallengeException
    {
        final SSLEngine sslEngine = getSSLEngine();
        final SSLSession sslSession = sslEngine.getSession();
        final ByteBuffer src = decodeBase64( inputString );
        final ByteBuffer dst = ByteBuffer.allocate( sslSession.getApplicationBufferSize() );
        unwrap( src, dst );
        dst.flip();
        return dst;
    }


    private void unwrap( final ByteBuffer src, final ByteBuffer dst ) throws MalformedChallengeException
    {

        try
        {
            final SSLEngineResult engineResult = sslEngine.unwrap( src, dst );
            if ( engineResult.getStatus() != Status.OK )
            {
                throw new MalformedChallengeException( "SSL Engine error status: " + engineResult.getStatus() );
            }

            if ( sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK )
            {
                final Runnable task = sslEngine.getDelegatedTask();
                task.run();
            }

        }
        catch ( SSLException e )
        {
            throw new MalformedChallengeException( "SSL Engine unwrap error: " + e.getMessage(), e );
        }
    }


    private String encodeBase64( final ByteBuffer buffer )
    {
        final int limit = buffer.limit();
        final byte[] bytes = new byte[limit];
        buffer.get( bytes );
        return Base64.getEncoder().encodeToString( bytes );
    }


    private ByteBuffer decodeBase64( final String inputString )
    {
        final byte[] inputBytes = Base64.getDecoder().decode( inputString );
        final ByteBuffer buffer = ByteBuffer.wrap( inputBytes );
        return buffer;
    }


    @Override
    public boolean isComplete()
    {
        return state == State.CREDENTIALS_SENT;
    }

}
