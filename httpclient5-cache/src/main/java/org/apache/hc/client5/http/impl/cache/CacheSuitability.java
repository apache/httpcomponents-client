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

package org.apache.hc.client5.http.impl.cache;

/**
 * @since 5.4
 */
enum CacheSuitability {

    MISMATCH, // the cache entry does not match the request properties and cannot be used
              // to satisfy the request
    FRESH, // the cache entry is fresh and can be used to satisfy the request
    FRESH_ENOUGH, // the cache entry is deemed fresh enough and can be used to satisfy the request
    STALE, // the cache entry is stale and may be unsuitable to satisfy the request
    STALE_WHILE_REVALIDATED, // the cache entry is stale but may be unsuitable to satisfy the request
                              // while being re-validated at the same time
    REVALIDATION_REQUIRED
           // the cache entry is stale and must not be used to satisfy the request
           // without revalidation

}