#!/usr/bin/env bash
# Exécute Gradle et remonte les lignes utiles dans les annotations GitHub.
set -o pipefail
log_file="${RUNNER_TEMP:-/tmp}/gradle-ci-$$.log"
set +e
bash ./gradlew --no-daemon "$@" --console=plain --stacktrace 2>&1 | tee "$log_file"
build_status=${PIPESTATUS[0]}
set -e
if [[ "$build_status" -ne 0 ]]; then
  python3 - "$log_file" <<'PY'
import re
import sys
from pathlib import Path

lines = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace").splitlines()
interesting = re.compile(
    r"(e: |error:|FAILURE: Build failed|What went wrong|Execution failed|"
    r"Could not resolve|Unresolved reference|Kotlin compilation error|"
    r"No matching variant|Could not find|tests? failed|FAILED|AssertionError|"
    r"NoSuchMethodException|expected|but was)"
)
selected = [line for line in lines if interesting.search(line)]
if not selected:
    selected = lines[-40:]
print("::group::Gradle failure details")
for line in selected[-60:]:
    safe = line.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::error title=Gradle build::{safe}")
print("::endgroup::")
PY
fi
exit "$build_status"
