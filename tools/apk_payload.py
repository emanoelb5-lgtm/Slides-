#!/usr/bin/env python3
"""Digest uncompressed APK payload, excluding signing-only META-INF entries."""
import hashlib, sys, zipfile

def payload_digest(path):
    digest = hashlib.sha256()
    with zipfile.ZipFile(path) as apk:
        for name in sorted(apk.namelist()):
            if name.startswith('META-INF/') or name.endswith('/'):
                continue
            data = apk.read(name)
            digest.update(name.encode() + b'\0')
            digest.update(hashlib.sha256(data).digest())
    return digest.hexdigest()

if __name__ == '__main__':
    print(payload_digest(sys.argv[1]))
