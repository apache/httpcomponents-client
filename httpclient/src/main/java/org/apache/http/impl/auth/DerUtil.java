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

import org.apache.http.auth.MalformedChallengeException;

/**
 * Utilities for primitive (but working) ASN.1 DER encoding/decoding.
 * Used in CredSSP and NTLM implementation.
 */
public class DerUtil
{

    public static void getByteAndAssert( final ByteBuffer buf, final int expectedValue, final String errorMessage )
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

    public static int parseLength( final ByteBuffer buf )
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

    public static void getLengthAndAssert( final ByteBuffer buf, final int expectedValue, final String errorMessage )
        throws MalformedChallengeException
    {
        final int bufLength = parseLength( buf );
        if ( expectedValue != bufLength )
        {
            parseError( buf, errorMessage + expectMessage( expectedValue, bufLength ) );
        }
    }

    public static int getAndAssertContentSpecificTag( final ByteBuffer buf, final String errorMessage ) throws MalformedChallengeException
    {
        final byte bufByte = buf.get();
        if ( ( bufByte & 0xe0 ) != 0xa0 )
        {
            parseError( buf, errorMessage + ": wrong content-specific tag " + String.format( "%02X", bufByte ) );
        }
        final int tag = bufByte & 0x1f;
        return tag;
    }

    public static void parseError( final ByteBuffer buf, final String errorMessage ) throws MalformedChallengeException
    {
        throw new MalformedChallengeException(
            "Error parsing TsRequest (position:" + buf.position() + "): " + errorMessage );
    }

    public static byte[] encodeLength( final int length )
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
