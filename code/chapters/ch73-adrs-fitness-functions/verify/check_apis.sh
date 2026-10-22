#!/usr/bin/env bash
# Checks what chapter 73 relies on against its sources (sparse clones made by build-libs.sh): adr-tools 3.0.0, MADR
# 4.0.0, Build Your Own Radar 1.2.1, and the JDK 25 ClassFile API (from $JAVA_HOME/lib/src.zip).
# Files are read with whitespace collapsed. Usage: ./check_apis.sh <sources dir>
set -uo pipefail
SRC=$1
fail=0
check() {   # description, file, extended regex (over the file with runs of whitespace collapsed to one space)
  if tr -s ' \t\n' '   ' < "$2" 2>/dev/null | grep -Eq -- "$3"; then printf 'OK       %s\n' "$1"; else printf 'MISSING  %s  (%s)\n' "$1" "${2#$SRC/}"; fail=$((fail + 1)); fi
}

echo "-- adr-tools (Nygard's format)"
check "a record: number and title, Date, Status, Context, Decision, Consequences"  $SRC/adr-tools/src/template.md '^# NUMBER\. TITLE Date: DATE ## Status STATUS ## Context .* ## Decision .* ## Consequences'
check "superseding changes the old record's status"            $SRC/adr-tools/src/adr-new 'The status of the superceded ADR is changed to record that ## +it has been superceded by the new ADR'
check "adr link writes a reverse link into the older record"   $SRC/adr-tools/src/adr-link '"\$adr_bin_dir/_adr_add_link" "\$target" "\$reverse_link" "\$source"'

echo "-- MADR"
check "status values, including superseded by ADR-NNNN"         $SRC/madr/template/adr-template.md 'status: "\{proposed \| rejected \| accepted \| deprecated \| … \| superseded by ADR-0123'
check "sections: context, considered options, decision outcome" $SRC/madr/template/adr-template.md '## Context and Problem Statement .* ## Considered Options .* ## Decision Outcome'

echo "-- Build Your Own Radar"
check "CSV columns name,ring,quadrant,isNew,description"       $SRC/byor/README.md 'name,ring,quadrant,isNew,description'
check "the Hold ring is read as Caution"                       $SRC/byor/src/util/ringNormalizer.js "if \(\(ring \|\| ''\)\.trim\(\)\.toLowerCase\(\) === 'hold'\) \{ return 'Caution'"

echo "-- JDK 25 ClassFile API"
CF=$(mktemp); unzip -p "${JAVA_HOME:?}/lib/src.zip" java.base/java/lang/classfile/ClassFile.java > "$CF"
check "ClassFile.of() and parse(byte[]), final since JDK 24"   "$CF" '@since 24 \*/ public sealed interface ClassFile .* static ClassFile of\(\) .* ClassModel parse\(byte\[\] bytes\)'
rm -f "$CF"

echo "apis: $((8 - fail)) of 8 found"
exit $fail
