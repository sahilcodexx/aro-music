#!/usr/bin/env python3
"""Reject ARM64 native libraries with load segments below 16 KiB alignment."""
import struct
import sys
import zipfile

if len(sys.argv) < 2:
    raise SystemExit('Usage: check-apk-native.py path/to/apk [more APKs]')
failed = False
for path in sys.argv[1:]:
    count = 0
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if not (name.startswith('lib/arm64-v8a/') and name.endswith('.so')):
                continue
            data = apk.read(name)
            if data[:6] != b'\x7fELF\x02\x01':
                raise SystemExit(f'{path}: unexpected ARM64 ELF format: {name}')
            offset = struct.unpack_from('<Q', data, 32)[0]
            entry_size, entries = struct.unpack_from('<HH', data, 54)
            alignments = [struct.unpack_from('<Q', data, offset + i * entry_size + 48)[0]
                          for i in range(entries)
                          if struct.unpack_from('<I', data, offset + i * entry_size)[0] == 1]
            count += 1
            if not alignments or min(alignments) < 16384:
                print(f'FAIL {path}: {name}: PT_LOAD alignment {alignments}')
                failed = True
    print(f'{path}: checked {count} ARM64 native libraries')
raise SystemExit(1 if failed else 0)
