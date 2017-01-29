This directory contains CA key and certificate for unit and integration tests
---

Use this command to check the private key
Passphrase: nopassword
---
openssl rsa -in ca-key.pem -check -text -noout
---

Use this command to print CA certificate details
---
openssl x509 -in ca-cert.pem -text -noout
---