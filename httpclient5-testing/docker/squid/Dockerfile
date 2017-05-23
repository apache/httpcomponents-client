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

FROM sameersbn/squid:3.3.8-22
MAINTAINER dev@hc.apache.org

ENV conf_dir /etc/squid3

RUN apt-get update
RUN apt-get install -y apache2-utils

COPY squid.conf ${conf_dir}/

RUN htpasswd -b -c ${conf_dir}/htpasswd squid nopassword

EXPOSE 8888
EXPOSE 8889
