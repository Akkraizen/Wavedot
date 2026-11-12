#!/bin/sh
set -eu

if [ "$#" -ne 5 ]; then
    echo "usage: $0 <private-key.pem> <version> <platform> <arch> <file>" >&2
    exit 64
fi

key=$1
version=$2
platform=$3
arch=$4
file=$5

size=$(wc -c < "$file" | tr -d ' ')
sha256=$(openssl dgst -sha256 -r "$file" | cut -d ' ' -f 1)

printf 'wavedot-update-v1\n%s\n%s\n%s\n%s\n%s\n%s' \
    "$version" "$platform" "$arch" "$(basename "$file")" "$size" "$sha256" \
    | openssl dgst -sha256 -sign "$key" \
    | openssl base64 -A
echo
