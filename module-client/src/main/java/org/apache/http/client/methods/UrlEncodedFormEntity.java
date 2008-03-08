/*
 * $Header$
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

package org.apache.http.client.methods;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;

public class UrlEncodedFormEntity extends StringEntity {

    /** The Content-Type for www-form-urlencoded. */
    public static final String FORM_URL_ENCODED_CONTENT_TYPE = 
        "application/x-www-form-urlencoded";

    public UrlEncodedFormEntity(
            final NameValuePair[] fields, 
            final String charset) throws UnsupportedEncodingException {
        super(URLUtils.formUrlEncode(fields, charset), charset);
        setContentType(FORM_URL_ENCODED_CONTENT_TYPE);
    }
    
    public UrlEncodedFormEntity(
            final NameValuePair[] fields) throws UnsupportedEncodingException {
        super(URLUtils.formUrlEncode(fields, HTTP.UTF_8), HTTP.US_ASCII);
        setContentType(FORM_URL_ENCODED_CONTENT_TYPE);
    }
    
    public UrlEncodedFormEntity (
            final Map<String, List<String>> parameters, 
            final String charset) throws UnsupportedEncodingException {
        super(URLUtils.format(parameters, charset), HTTP.US_ASCII);
        setContentType(FORM_URL_ENCODED_CONTENT_TYPE);
    }
    
    public UrlEncodedFormEntity (
            final Map<String, List<String>> parameters) throws UnsupportedEncodingException {
        super(URLUtils.format(parameters, HTTP.UTF_8), HTTP.US_ASCII);
        setContentType(FORM_URL_ENCODED_CONTENT_TYPE);
    }
    
}
