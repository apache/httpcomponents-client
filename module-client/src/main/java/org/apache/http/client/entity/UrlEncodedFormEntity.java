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

package org.apache.http.client.entity;

import java.io.UnsupportedEncodingException;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;

public class UrlEncodedFormEntity extends StringEntity {
  
    public UrlEncodedFormEntity (
        final List <NameValuePair> parameters, 
        final String encoding) throws UnsupportedEncodingException {
        super(URLEncodedUtils.format(parameters, encoding), 
            encoding);
        setContentType(URLEncodedUtils.CONTENT_TYPE);
    }

    public UrlEncodedFormEntity (
        final List <NameValuePair> parameters) throws UnsupportedEncodingException {
        super(URLEncodedUtils.format(parameters, HTTP.DEFAULT_CONTENT_CHARSET), 
            HTTP.DEFAULT_CONTENT_CHARSET);
        setContentType(URLEncodedUtils.CONTENT_TYPE);
    }
    
}
