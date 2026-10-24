import collections, sys

audit_file = sys.argv[1] if len(sys.argv) > 1 else "build/tmp/audit_etape3.txt"
counts = collections.defaultdict(int)

with open(audit_file, "r", encoding="utf-8", errors="ignore") as f:
    for line in f:
        line_s = line.strip()
        if "[" in line_s and "]" in line_s:
            # e.g.   [METHODE] com.lagradost.cloudstream3.MovieSearchResponse ...
            idx = line_s.find("]")
            if idx != -1:
                sig = line_s[idx+1:].strip()
                counts[sig] += 1

sorted_items = sorted(counts.items(), key=lambda x: x[1], reverse=True)
print(f"Top 40 missing items in {audit_file}:")
for item, count in sorted_items[:40]:
    print(f"{count:3d} : {item}")
