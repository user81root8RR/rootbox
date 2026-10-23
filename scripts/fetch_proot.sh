#!/usr/bin/env bash
# Downloads prebuilt proot + libtalloc (aarch64) from the Termux repo and packs them as jniLibs.
set -euo pipefail
BASE=https://packages.termux.dev/apt/termux-main
OUT="$(pwd)/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$OUT"
W=$(mktemp -d); cd "$W"
curl -fsSL "$BASE/dists/stable/main/binary-aarch64/Packages" -o Packages
fn() { awk -v p="$1" 'BEGIN{RS="";FS="\n"} $1=="Package: " p {for(i=1;i<=NF;i++) if(sub(/^Filename: /,"",$i)) print $i}' Packages; }
for pkg in proot libtalloc; do
  mkdir "$pkg"
  ( cd "$pkg"; f=$(fn "$pkg"); echo "fetch $f"; curl -fsSLO "$BASE/$f"; ar x "$(basename "$f")"; tar xf data.tar.* )
done
cp "$(find proot -path '*usr/bin/proot' -type f | head -1)" "$OUT/libproot.so"
cp "$(find proot -path '*libexec/proot/loader' -type f | head -1)" "$OUT/libproot-loader.so"
cp "$(find libtalloc -name 'libtalloc.so*' -type f | head -1)" "$OUT/libtalloc.so"
patchelf --replace-needed libtalloc.so.2 libtalloc.so "$OUT/libproot.so"
chmod 755 "$OUT"/*.so
echo "--- libproot.so dependencies:"; readelf -d "$OUT/libproot.so" | grep -E 'NEEDED|RUNPATH|RPATH' || true
ls -la "$OUT"
