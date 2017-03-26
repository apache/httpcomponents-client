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

/**
 * Simple debugging utility class for CredSSP and NTLM implementations.
 */
class DebugUtil
{

    public static String dump( final ByteBuffer buf )
    {
        final ByteBuffer dup = buf.duplicate();
        final StringBuilder sb = new StringBuilder( dup.toString() );
        sb.append( ": " );
        while ( dup.position() < dup.limit() )
        {
            sb.append( String.format( "%02X ", dup.get() ) );
        }
        return sb.toString();
    }


    public static void dump( final StringBuilder sb, final byte[] bytes )
    {
        if ( bytes == null )
        {
            sb.append( "null" );
            return;
        }
        for ( final byte b : bytes )
        {
            sb.append( String.format( "%02X ", b ) );
        }
    }


    public static String dump( final byte[] bytes )
    {
        final StringBuilder sb = new StringBuilder();
        dump( sb, bytes );
        return sb.toString();
    }


    public static byte[] fromHex( final String hex )
    {
        int i = 0;
        final byte[] bytes = new byte[200000];
        int h = 0;
        while ( h < hex.length() )
        {
            if ( hex.charAt( h ) == ' ' )
            {
                h++;
            }
            final String str = hex.substring( h, h + 2 );
            bytes[i] = ( byte ) Integer.parseInt( str, 16 );
            i++;
            h = h + 2;
        }
        final byte[] outbytes = new byte[i];
        System.arraycopy( bytes, 0, outbytes, 0, i );
        return outbytes;
    }

}
