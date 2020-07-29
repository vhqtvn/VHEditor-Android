#!/bin/sh
set -x

if [[ ! -f root_ca.key ]]; then
  openssl genrsa -des3 -passout 'pass:secret' -out root_ca.key 4096
fi
openssl req -x509 -new -nodes -key root_ca.key -sha256 -days 1024 -out my_ca -passin 'pass:secret' -subj /C=na/ST=na/O=na/OU=na/CN=127.0.0.1

openssl genrsa -out cert_key.bin 2048
cat <<CONF > cert.conf
[req]
default_bits  = 2048
distinguished_name = req_distinguished_name
req_extensions = req_ext
x509_extensions = v3_req
prompt = no
[req_distinguished_name]
countryName = XX
stateOrProvinceName = N/A
localityName = N/A
organizationName = Self-signed certificate
commonName = 127.0.0.1
[req_ext]
subjectAltName = @alt_names
[v3_req]
subjectAltName = @alt_names
[SAN]
subjectAltName = IP.1:127.0.0.1
[alt_names]
IP.1 = 127.0.0.1
CONF
openssl req -new -sha256 -key cert_key.bin -subj "/C=US/ST=CA/O=VHEditor/CN=127.0.0.1" -reqexts SAN -extensions SAN -config 'cert.conf' -out cert_csr.bin

cat <<CONF > ca.conf
[ca]
default_ca = CA_default

[CA_default]
dir = $(pwd)
database = $(pwd)/index.txt
new_certs_dir = $(pwd)/newcerts
serial = $(pwd)/serials
private_key = $(pwd)/root_ca.key
certificate = $(pwd)/my_ca
default_days = 3650
default_md = sha256
policy = policy_anything
copy_extensions = copyall

[policy_anything]
countryName = optional
stateOrProvinceName = optional
localityName = optional
organizationName = optional
organizationalUnitName = optional
commonName = supplied
emailAddress = optional

[req]
prompt = no
distinguished_name = req_distinguished_name
req_extensions = v3_ca

[req_distinguished_name]
CN = 127.0.0.1

[v3_ca]
subjectAltName = @alt_names

[alt_names]
IP.1 = 127.0.0.1
IP.2 = ::1
DNS.1 = localhost
CONF
#openssl x509 -req -in cert_csr.bin -CA my_ca -CAkey root_ca.key -passin 'pass:secret' -CAcreateserial -reqexts SAN -extensions SAN  -out cert_crt.crt -days 500 -sha256
touch index.txt
mkdir newcerts
openssl ca -config ca.conf -create_serial -batch -in cert_csr.bin -passin 'pass:secret' -out cert_crt.crt
cat cert_crt.crt | openssl x509 -outform pem > cert_crt.crt.tmp
rm -rf cert_crt.crt && mv cert_crt.crt.tmp cert_crt.crt
rm -rf cert.conf ca.conf my_ca.* index.txt* serials newcerts