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
 *
 * ====================================================================
 */

package org.apache.http.client.fluent;

public class FluentHttp {
    public static final int GET_METHOD = 0;
    public static final int POST_METHOD = 1;
    public static final int DELETE_METHOD = 2;
    public static final int PUT_METHOD = 3;
    public static final int OPTION_METHOD = 4;
    public static final int TRACE_METHOD = 5;
    public static final String REQUEST_INTERCEPTORS = "httpclinet.request.interceptors";
    public static final String RESPONSE_INTERCEPTORS = "httpclinet.response.interceptors";
}
