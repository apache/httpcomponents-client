<!--
   ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.
   ====================================================================
   This software consists of voluntary contributions made by many
   individuals on behalf of the Apache Software Foundation.  For more
   information on the Apache Software Foundation, please see
   <http://www.apache.org />.
 -->
Apache HttpComponents Client
============================

Welcome to the HttpClient component of the Apache HttpComponents project.

[![GitHub Actions Status](https://github.com/apache/httpcomponents-client/workflows/Java%20CI/badge.svg)](https://github.com/apache/httpcomponents-client/actions)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.httpcomponents.client5/httpclient5/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.apache.httpcomponents.client5/httpclient5)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Building Instructions
---------------------

For building from source instructions please refer to [BUILDING.txt](./BUILDING.txt).

Dependencies
------------

HttpClient main module requires Java 8 compatible runtime and
depends on the following external libraries:

* [Apache HttpComponents HttpCore](https://github.com/apache/httpcomponents-core)
* [SLF4J API](http://www.slf4j.org/)
* [Apache Commons Codec](https://github.com/apache/commons-codec)

Other dependencies are optional.

(for detailed information on external dependencies please see [pom.xml](./pom.xml))

Licensing
---------

Apache HttpComponents Client is licensed under the Apache License 2.0.
See the files [LICENSE.txt](./LICENSE.txt) and [NOTICE.txt](./NOTICE.txt) for more information.

Contact
-------

- For general information visit the main project site at  
  https://hc.apache.org/
- For current status information visit the status page at  
  https://hc.apache.org/status.html
- If you want to contribute visit  
  https://hc.apache.org/get-involved.html

Cryptographic Software Notice
-----------------------------

This distribution may include software that has been designed for use
with cryptographic software. The country in which you currently reside
may have restrictions on the import, possession, use, and/or re-export
to another country, of encryption software. BEFORE using any encryption
software, please check your country's laws, regulations and policies
concerning the import, possession, or use, and re-export of encryption
software, to see if this is permitted. See https://www.wassenaar.org/
for more information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms. The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS
Export Administration Regulations, Section 740.13) for both object
code and source code.

The following provides more details on the included software that
may be subject to export controls on cryptographic software:

> Apache HttpComponents Client interfaces with the
> Java Secure Socket Extension (JSSE) API to provide
> - HTTPS support
> 
> Apache HttpComponents Client does not include any
> implementation of JSSE.
