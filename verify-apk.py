"""Check packaged native ELF load-segment alignment; no Android device required."""
from pathlib import Path
import struct
import sys
import zipfile

apk = Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk")
count = 0
classes = set()

def dex_classes(data):
    """Read class_defs, not string references: a missing class may still appear in strings."""
    def u32(offset):
        return struct.unpack_from("<I", data, offset)[0]
    strings = u32(60)
    types = u32(68)
    count, definitions = u32(96), u32(100)
    for index in range(count):
        type_index = u32(definitions + index * 32)
        string_index = u32(types + type_index * 4)
        position = u32(strings + string_index * 4)
        while data[position] & 0x80:
            position += 1
        position += 1
        end = data.index(b"\x00", position)
        yield data[position:end].decode("utf-8")

with zipfile.ZipFile(apk) as archive:
    for entry in archive.namelist():
        if entry.startswith("classes") and entry.endswith(".dex"):
            classes.update(dex_classes(archive.read(entry)))
        if not entry.startswith("lib/") or not entry.endswith(".so"):
            continue
        data = archive.read(entry)
        assert data[:4] == b"\x7fELF" and data[4] == 2, entry
        endian = "<" if data[5] == 1 else ">"
        offset = struct.unpack_from(endian + "Q", data, 32)[0]
        size, number = struct.unpack_from(endian + "HH", data, 54)
        for index in range(number):
            position = offset + index * size
            if struct.unpack_from(endian + "I", data, position)[0] == 1:
                align = struct.unpack_from(endian + "Q", data, position + 48)[0]
                assert align >= 16384, (entry, align)
        count += 1
required = {
    "Lcom/arthenica/ffmpegkit/FFmpegKitConfig;",
    "Lcom/arthenica/smartexception/java/Exceptions;",
    "Lcom/arthenica/smartexception/java/JavaStackTraceElementSerializer;",
    "Lcom/arthenica/smartexception/java/JavaClassLoader;",
    "Lcom/arthenica/smartexception/java/JavaPackageLoader;",
    "Lcom/arthenica/smartexception/AbstractExceptions;",
    "Lcom/arthenica/smartexception/ClassLoader;",
    "Lcom/arthenica/smartexception/PackageLoader;",
    "Lcom/arthenica/smartexception/StackTraceElementSerializer;",
    "Lcom/arthenica/smartexception/StackTraceElementWrapper;",
    "Lcom/arthenica/smartexception/ThrowableWrapper;",
}
assert required <= classes, "APK missing runtime definitions: " + str(sorted(required - classes))
print(f"PASS {len(required)} FFmpeg/exception runtime class definitions packaged")
assert count > 0
print(f"PASS {count} native ELF libraries aligned for 16 KB pages")
