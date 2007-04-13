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
package org.apache.http.client.utils;

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.net.URLCodec;
import org.apache.http.NameValuePair;
import org.apache.http.util.CharArrayBuffer;

/**
 * The home for utility methods that handle various URL encoding tasks.
 * 
 * @author Michael Becke
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @since 2.0 final
 */
public class URLUtils {

    /** Default content encoding chatset */
    private static final String DEFAULT_CHARSET = "ISO-8859-1";

    /**
     * Form-urlencoding routine.
     *
     * The default encoding for all forms is `application/x-www-form-urlencoded'. 
     * A form data set is represented in this media type as follows:
     *
     * The form field names and values are escaped: space characters are replaced 
     * by `+', and then reserved characters are escaped as per [URL]; that is, 
     * non-alphanumeric characters are replaced by `%HH', a percent sign and two 
     * hexadecimal digits representing the ASCII code of the character. Line breaks, 
     * as in multi-line text field values, are represented as CR LF pairs, i.e. `%0D%0A'.
     * 
     * <p>
     * if the given charset is not supported, ISO-8859-1 is used instead.
     * </p>
     * 
     * @param pairs the values to be encoded
     * @param charset the character set of pairs to be encoded
     * 
     * @return the urlencoded pairs
     * 
     * @since 4.0
     */
     public static String formUrlEncode(final NameValuePair[] pairs, final String charset) {
        try {
            return doFormUrlEncode(pairs, charset);
        } catch (UnsupportedEncodingException e) {
            try {
                return doFormUrlEncode(pairs, DEFAULT_CHARSET);
            } catch (UnsupportedEncodingException fatal) {
                // Should never happen. ISO-8859-1 must be supported on all JVMs
                throw new Error("HttpClient requires " + DEFAULT_CHARSET + " support");
            }
        }
    }

    /**
     * Form-urlencoding routine.
     *
     * The default encoding for all forms is `application/x-www-form-urlencoded'. 
     * A form data set is represented in this media type as follows:
     *
     * The form field names and values are escaped: space characters are replaced 
     * by `+', and then reserved characters are escaped as per [URL]; that is, 
     * non-alphanumeric characters are replaced by `%HH', a percent sign and two 
     * hexadecimal digits representing the ASCII code of the character. Line breaks, 
     * as in multi-line text field values, are represented as CR LF pairs, i.e. `%0D%0A'.
     * 
     * @param pairs the values to be encoded
     * @param charset the character set of pairs to be encoded
     * 
     * @return the urlencoded pairs
     * @throws UnsupportedEncodingException if charset is not supported
     * 
     * @since 2.0 final
     */
     private static String doFormUrlEncode(
             final NameValuePair[] pairs, 
             final String charset) throws UnsupportedEncodingException {
        CharArrayBuffer buf = new CharArrayBuffer(32);
        for (int i = 0; i < pairs.length; i++) {
            URLCodec codec = new URLCodec();
            NameValuePair pair = pairs[i];
            if (pair.getName() != null) {
                if (i > 0) {
                    buf.append("&");
                }
                buf.append(codec.encode(pair.getName(), charset));
                buf.append("=");
                if (pair.getValue() != null) {
                    buf.append(codec.encode(pair.getValue(), charset));
                }
            }
        }
        return buf.toString();
    }
    
    /**
     * This class should not be instantiated.
     */
    private URLUtils() {
    }

}
