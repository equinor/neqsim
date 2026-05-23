"""Mark all ```python fences with <!-- noexec --> (unless already marked or skip-marked)."""
import re
import sys
from pathlib import Path

NOEXEC = "<!-- noexec -->"


def mark_file(path: Path) -> int:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    out = []
    n = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("```python"):
            # Look at previous non-empty line for existing skip marker
            j = len(out) - 1
            while j >= 0 and out[j].strip() == "":
                j -= 1
            prev = out[j] if j >= 0 else ""
            if NOEXEC in prev:
                out.append(line)
            else:
                out.append(NOEXEC + "\n")
                out.append(line)
                n += 1
        else:
            out.append(line)
        i += 1
    path.write_text("".join(out), encoding="utf-8")
    return n


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        p = Path(arg)
        n = mark_file(p)
        print(f"{p}: marked {n} blocks")
