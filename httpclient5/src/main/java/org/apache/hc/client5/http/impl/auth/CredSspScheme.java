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

package org.apache.hc.client5.http.impl.auth;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
public class CredSspScheme implements AuthScheme
{
    private static final Charset UNICODE_LITTLE_UNMARKED = Charset.forName( "UnicodeLittleUnmarked" );
    public static final String SCHEME_NAME = "CredSSP";

    private final Logger log = LogManager.getLogger( CredSspScheme.class );

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
    private NTCredentials ntcredentials;
    private NTLMEngineImpl.Type1Message type1Message;
    private NTLMEngineImpl.Type2Message type2Message;
    private NTLMEngineImpl.Type3Message type3Message;
    private CredSspTsRequest lastReceivedTsRequest;
    private NTLMEngineImpl.Handle ntlmOutgoingHandle;
    private NTLMEngineImpl.Handle ntlmIncomingHandle;
    private byte[] peerPublicKey;


    public CredSspScheme() {
        state = State.UNINITIATED;
    }


    @Override
    public String getName()
    {
        return SCHEME_NAME;
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
        final SSLContext sslContext;
        try
        {
            sslContext = SSLContexts.custom().build();
        }
        catch ( NoSuchAlgorithmException | KeyManagementException e )
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
        catch ( final KeyManagementException e )
        {
            throw new RuntimeException( "SSL Context initialization error: " + e.getMessage(), e );
        }
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode( true );
        return sslEngine;
    }


    @Override
    public void processChallenge(
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException
    {
        final String inputString = authChallenge.getValue();

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
        }

        if ( state == State.PUB_KEY_AUTH_SENT )
        {
            final ByteBuffer buf = unwrap( inputString );
            state = State.PUB_KEY_AUTH_RECEIVED;
            lastReceivedTsRequest = CredSspTsRequest.createDecoded( buf );
        }
    }

    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {
        Args.notNull(host, "Auth host");
        Args.notNull(credentialsProvider, "CredentialsProvider");

        final Credentials credentials = credentialsProvider.getCredentials(
                new AuthScope(host, null, getName()), context);
        if (credentials instanceof NTCredentials) {
            this.ntcredentials = (NTCredentials) credentials;
            return true;
        }
        return false;
    }

    @Override
    public Principal getPrincipal() {
        return ntcredentials != null ? ntcredentials.getUserPrincipal() : null;
    }


    @Override
    public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException
    {
        if (ntcredentials == null) {
            throw new AuthenticationException("NT credentials not available");
        }

        final String outputString;

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
            type1Message = new NTLMEngineImpl.Type1Message(
                ntcredentials.getNetbiosDomain(), ntcredentials.getWorkstation(), ntlmFlags);
            final byte[] ntlmNegoMessageEncoded = type1Message.getBytes();
            final CredSspTsRequest req = CredSspTsRequest.createNegoToken( ntlmNegoMessageEncoded );
            req.encode( buf );
            buf.flip();
            outputString = wrap( buf );
            state = State.NEGO_TOKEN_SENT;

        }
        else if ( state == State.NEGO_TOKEN_RECEIVED )
        {
            final ByteBuffer buf = allocateOutBuffer();
            type2Message = new NTLMEngineImpl.Type2Message(
                lastReceivedTsRequest.getNegoToken());

            final Certificate peerServerCertificate = getPeerServerCertificate();

            type3Message = new NTLMEngineImpl.Type3Message(
                ntcredentials.getNetbiosDomain(),
                ntcredentials.getWorkstation(),
                ntcredentials.getUserName(),
                ntcredentials.getPassword(),
                type2Message.getChallenge(),
                type2Message.getFlags(),
                type2Message.getTarget(),
                type2Message.getTargetInfo(),
                peerServerCertificate,
                type1Message.getBytes(),
                type2Message.getBytes());

            final byte[] ntlmAuthenticateMessageEncoded = type3Message.getBytes();

            final byte[] exportedSessionKey = type3Message.getExportedSessionKey();

            ntlmOutgoingHandle = new NTLMEngineImpl.Handle(exportedSessionKey, NTLMEngineImpl.Mode.CLIENT, true);
            ntlmIncomingHandle = new NTLMEngineImpl.Handle(exportedSessionKey, NTLMEngineImpl.Mode.SERVER, true);

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
        return outputString;
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


    private Certificate getPeerServerCertificate() throws AuthenticationException
    {
        final Certificate[] peerCertificates;
        try
        {
            peerCertificates = sslEngine.getSession().getPeerCertificates();
        }
        catch ( final SSLPeerUnverifiedException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
        for ( final Certificate peerCertificate : peerCertificates )
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
        final byte[] domainOctetStringBytesLengthBytes = encodeLength( domainBytes.length );
        final int domainNameLength = 1 + domainOctetStringBytesLengthBytes.length + domainBytes.length;
        final byte[] domainNameLengthBytes = encodeLength( domainNameLength );

        final byte[] usernameBytes = encodeUnicode( ntcredentials.getUserName() );
        final byte[] usernameOctetStringBytesLengthBytes = encodeLength( usernameBytes.length );
        final int userNameLength = 1 + usernameOctetStringBytesLengthBytes.length + usernameBytes.length;
        final byte[] userNameLengthBytes = encodeLength( userNameLength );

        final byte[] passwordBytes = encodeUnicode( ntcredentials.getPassword() );
        final byte[] passwordOctetStringBytesLengthBytes = encodeLength( passwordBytes.length );
        final int passwordLength = 1 + passwordOctetStringBytesLengthBytes.length + passwordBytes.length;
        final byte[] passwordLengthBytes = encodeLength( passwordLength );

        final int tsPasswordLength = 1 + domainNameLengthBytes.length + domainNameLength +
            1 + userNameLengthBytes.length + userNameLength +
            1 + passwordLengthBytes.length + passwordLength;
        final byte[] tsPasswordLengthBytes = encodeLength( tsPasswordLength );
        final int credentialsOctetStringLength = 1 + tsPasswordLengthBytes.length + tsPasswordLength;
        final byte[] credentialsOctetStringLengthBytes = encodeLength( credentialsOctetStringLength );
        final int credentialsLength = 1 + credentialsOctetStringLengthBytes.length + credentialsOctetStringLength;
        final byte[] credentialsLengthBytes = encodeLength( credentialsLength );
        final int tsCredentialsLength = 5 + 1 + credentialsLengthBytes.length + credentialsLength;
        final byte[] tsCredentialsLengthBytes = encodeLength( tsCredentialsLength );

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
        catch ( final NTLMEngineException e )
        {
            throw new AuthenticationException( e.getMessage(), e );
        }
    }

    private final static byte[] EMPTYBUFFER = new byte[0];

    private byte[] encodeUnicode( final String string )
    {
        if (string == null) {
            return EMPTYBUFFER;
        }
        return encodeUnicode( CharBuffer.wrap(string) );
    }


    private byte[] encodeUnicode( final char[] chars )
    {
        if (chars == null) {
            return EMPTYBUFFER;
        }
        return encodeUnicode( CharBuffer.wrap(chars) );
    }


    private byte[] encodeUnicode( final CharBuffer charBuffer )
    {
        if (charBuffer == null) {
            return EMPTYBUFFER;
        }
        final ByteBuffer encoded = UNICODE_LITTLE_UNMARKED.encode(charBuffer);
        if (!encoded.hasRemaining()) {
            return EMPTYBUFFER;
        }
        final byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
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
            getByteAndAssert( buf, 0x30, "initial sequence" );
            parseLength( buf );
            getByteAndAssert( buf, 0x30, "AlgorithmIdentifier sequence" );
            final int algIdSeqLength = parseLength( buf );
            buf.position( buf.position() + algIdSeqLength );
            getByteAndAssert( buf, 0x03, "subjectPublicKey type" );
            int subjectPublicKeyLegth = parseLength( buf );
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
        catch ( final MalformedChallengeException e )
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
        catch ( final SSLException e )
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
        catch ( final SSLException e )
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
        catch ( final SSLException e )
        {
            throw new MalformedChallengeException( "SSL Engine unwrap error: " + e.getMessage(), e );
        }
    }


    private String encodeBase64( final ByteBuffer buffer )
    {
        final int limit = buffer.limit();
        final byte[] bytes = new byte[limit];
        buffer.get( bytes );
        return new String(Base64.encodeBase64(bytes), StandardCharsets.US_ASCII);
    }


    private ByteBuffer decodeBase64( final String inputString )
    {
        final byte[] inputBytes = Base64.decodeBase64(inputString.getBytes(StandardCharsets.US_ASCII));
        final ByteBuffer buffer = ByteBuffer.wrap( inputBytes );
        return buffer;
    }


    @Override
    public boolean isChallengeComplete()
    {
        return state == State.CREDENTIALS_SENT;
    }

    /**
     * Implementation of the TsRequest structure used in CredSSP protocol.
     * It is specified in [MS-CPPS] section 2.2.1.
     */
    static class CredSspTsRequest
    {

        private static final int VERSION = 3;

        private byte[] negoToken;
        private byte[] authInfo;
        private byte[] pubKeyAuth;


        protected CredSspTsRequest()
        {
            super();
        }


        public static CredSspTsRequest createNegoToken( final byte[] negoToken )
        {
            final CredSspTsRequest req = new CredSspTsRequest();
            req.negoToken = negoToken;
            return req;
        }


        public static CredSspTsRequest createAuthInfo( final byte[] authInfo )
        {
            final CredSspTsRequest req = new CredSspTsRequest();
            req.authInfo = authInfo;
            return req;
        }


        public static CredSspTsRequest createDecoded( final ByteBuffer buf ) throws MalformedChallengeException
        {
            final CredSspTsRequest req = new CredSspTsRequest();
            req.decode( buf );
            return req;
        }


        public byte[] getNegoToken()
        {
            return negoToken;
        }


        public void setNegoToken( final byte[] negoToken )
        {
            this.negoToken = negoToken;
        }


        public byte[] getAuthInfo()
        {
            return authInfo;
        }


        public void setAuthInfo( final byte[] authInfo )
        {
            this.authInfo = authInfo;
        }


        public byte[] getPubKeyAuth()
        {
            return pubKeyAuth;
        }


        public void setPubKeyAuth( final byte[] pubKeyAuth )
        {
            this.pubKeyAuth = pubKeyAuth;
        }


        public void decode( final ByteBuffer buf ) throws MalformedChallengeException
        {
            negoToken = null;
            authInfo = null;
            pubKeyAuth = null;

            getByteAndAssert( buf, 0x30, "initial sequence" );
            parseLength( buf );

            while ( buf.hasRemaining() )
            {
                final int contentTag = getAndAssertContentSpecificTag( buf, "content tag" );
                parseLength( buf );
                switch ( contentTag )
                {
                    case 0:
                        processVersion( buf );
                        break;
                    case 1:
                        parseNegoTokens( buf );
                        break;
                    case 2:
                        parseAuthInfo( buf );
                        break;
                    case 3:
                        parsePubKeyAuth( buf );
                        break;
                    case 4:
                        processErrorCode( buf );
                        break;
                    default:
                        parseError( buf, "unexpected content tag " + contentTag );
                }
            }
        }


        private void processVersion( final ByteBuffer buf ) throws MalformedChallengeException
        {
            getByteAndAssert( buf, 0x02, "version type" );
            getLengthAndAssert( buf, 1, "version length" );
            getByteAndAssert( buf, VERSION, "wrong protocol version" );
        }


        private void parseNegoTokens( final ByteBuffer buf ) throws MalformedChallengeException
        {
            getByteAndAssert( buf, 0x30, "negoTokens sequence" );
            parseLength( buf );
            // I have seen both 0x30LL encoding and 0x30LL0x30LL encoding. Accept both.
            byte bufByte = buf.get();
            if ( bufByte == 0x30 )
            {
                parseLength( buf );
                bufByte = buf.get();
            }
            if ( ( bufByte & 0xff ) != 0xa0 )
            {
                parseError( buf, "negoTokens: wrong content-specific tag " + String.format( "%02X", bufByte ) );
            }
            parseLength( buf );
            getByteAndAssert( buf, 0x04, "negoToken type" );

            final int tokenLength = parseLength( buf );
            negoToken = new byte[tokenLength];
            buf.get( negoToken );
        }


        private void parseAuthInfo( final ByteBuffer buf ) throws MalformedChallengeException
        {
            getByteAndAssert( buf, 0x04, "authInfo type" );
            final int length = parseLength( buf );
            authInfo = new byte[length];
            buf.get( authInfo );
        }


        private void parsePubKeyAuth( final ByteBuffer buf ) throws MalformedChallengeException
        {
            getByteAndAssert( buf, 0x04, "pubKeyAuth type" );
            final int length = parseLength( buf );
            pubKeyAuth = new byte[length];
            buf.get( pubKeyAuth );
        }


        private void processErrorCode( final ByteBuffer buf ) throws MalformedChallengeException
        {
            getLengthAndAssert( buf, 3, "error code length" );
            getByteAndAssert( buf, 0x02, "error code type" );
            getLengthAndAssert( buf, 1, "error code length" );
            final byte errorCode = buf.get();
            parseError( buf, "Error code " + errorCode );
        }


        public void encode( final ByteBuffer buf )
        {
            final ByteBuffer inner = ByteBuffer.allocate( buf.capacity() );

            // version tag [0]
            inner.put( ( byte ) ( 0x00 | 0xa0 ) );
            inner.put( ( byte ) 3 ); // length

            inner.put( ( byte ) ( 0x02 ) ); // INTEGER tag
            inner.put( ( byte ) 1 ); // length
            inner.put( ( byte ) VERSION ); // value

            if ( negoToken != null )
            {
                int len = negoToken.length;
                final byte[] negoTokenLengthBytes = encodeLength( len );
                len += 1 + negoTokenLengthBytes.length;
                final byte[] negoTokenLength1Bytes = encodeLength( len );
                len += 1 + negoTokenLength1Bytes.length;
                final byte[] negoTokenLength2Bytes = encodeLength( len );
                len += 1 + negoTokenLength2Bytes.length;
                final byte[] negoTokenLength3Bytes = encodeLength( len );
                len += 1 + negoTokenLength3Bytes.length;
                final byte[] negoTokenLength4Bytes = encodeLength( len );

                inner.put( ( byte ) ( 0x01 | 0xa0 ) ); // negoData tag [1]
                inner.put( negoTokenLength4Bytes ); // length

                inner.put( ( byte ) ( 0x30 ) ); // SEQUENCE tag
                inner.put( negoTokenLength3Bytes ); // length

                inner.put( ( byte ) ( 0x30 ) ); // .. of SEQUENCE tag
                inner.put( negoTokenLength2Bytes ); // length

                inner.put( ( byte ) ( 0x00 | 0xa0 ) ); // negoToken tag [0]
                inner.put( negoTokenLength1Bytes ); // length

                inner.put( ( byte ) ( 0x04 ) ); // OCTET STRING tag
                inner.put( negoTokenLengthBytes ); // length

                inner.put( negoToken );
            }

            if ( authInfo != null )
            {
                final byte[] authInfoEncodedLength = encodeLength( authInfo.length );

                inner.put( ( byte ) ( 0x02 | 0xa0 ) ); // authInfo tag [2]
                inner.put( encodeLength( 1 + authInfoEncodedLength.length + authInfo.length ) ); // length

                inner.put( ( byte ) ( 0x04 ) ); // OCTET STRING tag
                inner.put( authInfoEncodedLength );
                inner.put( authInfo );
            }

            if ( pubKeyAuth != null )
            {
                final byte[] pubKeyAuthEncodedLength = encodeLength( pubKeyAuth.length );

                inner.put( ( byte ) ( 0x03 | 0xa0 ) ); // pubKeyAuth tag [3]
                inner.put( encodeLength( 1 + pubKeyAuthEncodedLength.length + pubKeyAuth.length ) ); // length

                inner.put( ( byte ) ( 0x04 ) ); // OCTET STRING tag
                inner.put( pubKeyAuthEncodedLength );
                inner.put( pubKeyAuth );
            }

            inner.flip();

            // SEQUENCE tag
            buf.put( ( byte ) ( 0x10 | 0x20 ) );
            buf.put( encodeLength( inner.limit() ) );
            buf.put( inner );
        }


        public String debugDump()
        {
            final StringBuilder sb = new StringBuilder( "TsRequest\n" );
            sb.append( "  negoToken:\n" );
            sb.append( "    " );
            DebugUtil.dump( sb, negoToken );
            sb.append( "\n" );
            sb.append( "  authInfo:\n" );
            sb.append( "    " );
            DebugUtil.dump( sb, authInfo );
            sb.append( "\n" );
            sb.append( "  pubKeyAuth:\n" );
            sb.append( "    " );
            DebugUtil.dump( sb, pubKeyAuth );
            return sb.toString();
        }


        @Override
        public String toString()
        {
            return "TsRequest(negoToken=" + Arrays.toString( negoToken ) + ", authInfo="
                + Arrays.toString( authInfo ) + ", pubKeyAuth=" + Arrays.toString( pubKeyAuth ) + ")";
        }
    }

    static void getByteAndAssert( final ByteBuffer buf, final int expectedValue, final String errorMessage )
        throws MalformedChallengeException
    {
        final byte bufByte = buf.get();
        if ( bufByte != expectedValue )
        {
            parseError( buf, errorMessage + expectMessage( expectedValue, bufByte ) );
        }
    }

    private static String expectMessage( final int expectedValue, final int realValue )
    {
        return "(expected " + String.format( "%02X", expectedValue ) + ", got " + String.format( "%02X", realValue )
            + ")";
    }

    static int parseLength( final ByteBuffer buf )
    {
        byte bufByte = buf.get();
        if ( bufByte == 0x80 )
        {
            return -1; // infinite
        }
        if ( ( bufByte & 0x80 ) == 0x80 )
        {
            final int size = bufByte & 0x7f;
            int length = 0;
            for ( int i = 0; i < size; i++ )
            {
                bufByte = buf.get();
                length = ( length << 8 ) + ( bufByte & 0xff );
            }
            return length;
        }
        else
        {
            return bufByte;
        }
    }

    static void getLengthAndAssert( final ByteBuffer buf, final int expectedValue, final String errorMessage )
        throws MalformedChallengeException
    {
        final int bufLength = parseLength( buf );
        if ( expectedValue != bufLength )
        {
            parseError( buf, errorMessage + expectMessage( expectedValue, bufLength ) );
        }
    }

    static int getAndAssertContentSpecificTag( final ByteBuffer buf, final String errorMessage ) throws MalformedChallengeException
    {
        final byte bufByte = buf.get();
        if ( ( bufByte & 0xe0 ) != 0xa0 )
        {
            parseError( buf, errorMessage + ": wrong content-specific tag " + String.format( "%02X", bufByte ) );
        }
        final int tag = bufByte & 0x1f;
        return tag;
    }

    static void parseError( final ByteBuffer buf, final String errorMessage ) throws MalformedChallengeException
    {
        throw new MalformedChallengeException(
            "Error parsing TsRequest (position:" + buf.position() + "): " + errorMessage );
    }

    static byte[] encodeLength( final int length )
    {
        if ( length < 128 )
        {
            final byte[] encoded = new byte[1];
            encoded[0] = ( byte ) length;
            return encoded;
        }

        int size = 1;

        int val = length;
        while ( ( val >>>= 8 ) != 0 )
        {
            size++;
        }

        final byte[] encoded = new byte[1 + size];
        encoded[0] = ( byte ) ( size | 0x80 );

        int shift = ( size - 1 ) * 8;
        for ( int i = 0; i < size; i++ )
        {
            encoded[i + 1] = ( byte ) ( length >> shift );
            shift -= 8;
        }

        return encoded;
    }

}
