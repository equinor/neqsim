import pathlib

root = pathlib.Path("src/test/java")
files = []
for p in root.rglob("*.java"):
    t = p.read_text(encoding="utf-8", errors="ignore")
    if '@Tag("slow")' in t:
        files.append(p.relative_to(root).as_posix())
files.sort()
for shard in range(4):
    print("=== shard", shard, "(label %d/4)" % (shard + 1))
    for i, f in enumerate(files):
        if i % 4 == shard:
            print("   ", f[:-5].replace("/", "."))
