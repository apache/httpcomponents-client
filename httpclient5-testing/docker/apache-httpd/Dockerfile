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

FROM httpd:2.4
MAINTAINER dev@hc.apache.org

ENV httpd_home /usr/local/apache2
ENV var_dir /var/httpd
ENV www_dir ${var_dir}/www
ENV private_dir ${www_dir}/private

RUN apt-get update
RUN apt-get install -y subversion

RUN mkdir -p ${var_dir}
RUN svn co --depth immediates http://svn.apache.org/repos/asf/httpcomponents/site ${www_dir}
RUN svn up --set-depth infinity ${www_dir}/images
RUN svn up --set-depth infinity ${www_dir}/css

RUN mkdir ${httpd_home}/ssl
COPY server-cert.pem ${httpd_home}/ssl/
COPY server-key.pem ${httpd_home}/ssl/
COPY httpd.conf ${httpd_home}/conf/
COPY httpd-ssl.conf ${httpd_home}/conf/extra/

RUN mkdir -p ${private_dir}
# user: testuser; pwd: nopassword
RUN echo "testuser:{SHA}0Ybo2sSKJNARW1aNCrLJ6Lguats=" > ${private_dir}/.htpasswd
RUN echo "testuser:Restricted Files:73deccd22e07066db8c405e5364335f5" > ${private_dir}/.htpasswd_digest
RUN echo "Big Secret" > ${private_dir}/big-secret.txt

EXPOSE 8080
EXPOSE 8443
