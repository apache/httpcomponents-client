#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==========================================================================
#
# The default image has no init
kdb5_util -P unsafe create -s
echo Kerberos DB created
krb5kdc
echo KDC started
useradd testclient
echo testclient:testclient | chpasswd
kadmin.local addprinc -pw HTTP HTTP/localhost@EXAMPLE.ORG
kadmin.local addprinc -pw testclient testclient@EXAMPLE.ORG
kadmin.local addprinc -pw testpwclient testpwclient@EXAMPLE.ORG
rm /keytabs/testclient.keytab
rm /keytabs/HTTP.keytab
kadmin.local ktadd -k /keytabs/testclient.keytab testclient@EXAMPLE.ORG 
kadmin.local ktadd -k /keytabs/HTTP.keytab HTTP/localhost@EXAMPLE.ORG
chmod 666 /keytabs/testclient.keytab
chmod 666 /keytabs/HTTP.keytab
echo keytabs written
sleep 3600

