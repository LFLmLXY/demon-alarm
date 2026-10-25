"""Pack classes.dex into an APK produced by aapt2 link, preserving entry
compression types (resources.arsc must stay STORED for Android 11+).

usage: python tools/pack.py <base.apk> <classes.dex> <out.apk>
"""
import sys
import zipfile

DEX_NAME = "classes.dex"


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: pack.py <base.apk> <classes.dex> <out.apk>")
        return 2
    base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]

    with open(dex, "rb") as fh:
        dex_bytes = fh.read()

    src = zipfile.ZipFile(base, "r")
    entries = src.infolist()
    if any(e.filename == DEX_NAME for e in entries):
        print("base apk already contains " + DEX_NAME)
        return 3

    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        zi = zipfile.ZipInfo(DEX_NAME)
        zi.compress_type = zipfile.ZIP_DEFLATED
        dst.writestr(zi, dex_bytes)
        for e in entries:
            data = src.read(e.filename)
            keep = zipfile.ZipInfo(e.filename, date_time=e.date_time)
            keep.compress_type = e.compress_type
            keep.external_attr = e.external_attr
            keep.internal_attr = e.internal_attr
            keep.create_system = e.create_system
            dst.writestr(keep, data)
    src.close()

    print("packed %d bytes of dex into %s" % (len(dex_bytes), out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
