"""Render paper.md to a standalone HTML file with numbered citations."""
import re
from pathlib import Path

PAPER_DIR = Path(__file__).resolve().parent
paper = (PAPER_DIR / "paper.md").read_text(encoding="utf-8")
bib_text = (PAPER_DIR / "refs.bib").read_text(encoding="utf-8")

# ── Step 1: Build cite-key → number mapping (order of first appearance) ──
keys_in_order = []
for m in re.finditer(r"\\\\cite\{([^}]+)\}", paper):
    for key in m.group(1).split(","):
        key = key.strip()
        if key not in keys_in_order:
            keys_in_order.append(key)

key_to_num = {k: i + 1 for i, k in enumerate(keys_in_order)}

# ── Step 2: Parse refs.bib into a dict of key → formatted string ──
bib_entries = {}
for block in re.finditer(
    r"@\w+\{(\w+),\s*(.*?)\n\}", bib_text, flags=re.DOTALL
):
    bib_key = block.group(1)
    fields = {}
    for fld in re.finditer(r"(\w+)\s*=\s*\{(.*?)\}", block.group(2), flags=re.DOTALL):
        fields[fld.group(1).lower()] = fld.group(2).strip()

    # Build a short formatted citation
    author = fields.get("author", "Unknown")
    # Shorten: "Last, F. and Last2, F." → "Last et al."
    authors = [a.strip() for a in author.split(" and ")]
    if len(authors) > 2:
        short_author = authors[0].split(",")[0] + " et al."
    elif len(authors) == 2:
        short_author = (
            authors[0].split(",")[0] + " and " + authors[1].split(",")[0]
        )
    else:
        short_author = authors[0].split(",")[0]

    title = fields.get("title", "Untitled").replace("{", "").replace("}", "")
    journal = fields.get("journal", fields.get("booktitle", ""))
    journal = journal.replace("{", "").replace("}", "")
    year = fields.get("year", "n.d.")
    volume = fields.get("volume", "")
    pages = fields.get("pages", "")

    parts = [f"{short_author},"]
    parts.append(f'"{title},"')
    if journal:
        parts.append(f"<em>{journal}</em>,")
    if volume:
        vol_str = f"vol. {volume}"
        if pages:
            vol_str += f", pp. {pages}"
        parts.append(vol_str + ",")
    parts.append(f"{year}.")

    bib_entries[bib_key] = " ".join(parts)


# ── Step 3: Convert paper body ──
body = paper

# Remove HTML comments
body = re.sub(r"<!--.*?-->", "", body, flags=re.DOTALL)


# Convert \cite{key1, key2} → [n1, n2] with hyperlinks
def replace_cite(match):
    keys = [k.strip() for k in match.group(1).split(",")]
    nums = []
    for k in keys:
        n = key_to_num.get(k)
        if n:
            nums.append(f'<a href="#ref-{n}" class="cite">{n}</a>')
        else:
            nums.append(f'<span class="cite-unknown">{k}</span>')
    return "[" + ", ".join(nums) + "]"


body = re.sub(r"\\\\cite\{([^}]+)\}", replace_cite, body)

# Headers
body = re.sub(r"^### (.+)$", r"<h3>\1</h3>", body, flags=re.MULTILINE)
body = re.sub(r"^## (.+)$", r"<h2>\1</h2>", body, flags=re.MULTILINE)
body = re.sub(r"^# (.+)$", r"<h1>\1</h1>", body, flags=re.MULTILINE)

# Bold and italic
body = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", body)
body = re.sub(r"\*(.+?)\*", r"<em>\1</em>", body)

# Code blocks
body = re.sub(
    r"```(\w*)\n(.*?)```", r"<pre><code>\2</code></pre>", body, flags=re.DOTALL
)

# Inline code
body = re.sub(r"`([^`]+)`", r"<code>\1</code>", body)

# Horizontal rules
body = re.sub(r"^---$", "<hr>", body, flags=re.MULTILINE)


# Tables
def convert_table(match):
    lines = match.group(0).strip().split("\n")
    rows = [l for l in lines if l.strip() and not re.match(r"^\|[\s:\-]+\|$", l)]
    html_t = "<table>\n"
    for i, row in enumerate(rows):
        cells = [c.strip() for c in row.split("|")[1:-1]]
        tag = "th" if i == 0 else "td"
        html_t += (
            "<tr>"
            + "".join(f"<{tag}>{c}</{tag}>" for c in cells)
            + "</tr>\n"
        )
    html_t += "</table>"
    return html_t


body = re.sub(r"(\|.+\|(?:\n\|.+\|)+)", convert_table, body)

# Lists (bullet)
body = re.sub(r"^- (.+)$", r"<li>\1</li>", body, flags=re.MULTILINE)

# Paragraphs
body = re.sub(r"\n\n+", "\n</p>\n<p>\n", body)

# ── Step 4: Build reference list ──
ref_list = '\n<h2>References</h2>\n<ol class="references">\n'
for key in keys_in_order:
    n = key_to_num[key]
    entry = bib_entries.get(key, f"[{key}] — entry not found in refs.bib")
    ref_list += f'  <li id="ref-{n}" value="{n}">{entry}</li>\n'
ref_list += "</ol>\n"

# ── Step 5: Assemble HTML ──
header = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>Agentic Engineering — Multi-Agent LLM Framework</title>
<style>
body{max-width:900px;margin:40px auto;font-family:Georgia,serif;line-height:1.7;color:#333;padding:0 20px}
h1{font-size:1.8em;border-bottom:2px solid #2196F3;padding-bottom:10px}
h2{font-size:1.4em;color:#1565C0;margin-top:2em}
h3{font-size:1.15em;color:#1976D2}
table{border-collapse:collapse;margin:1em auto;font-size:.95em}
th,td{border:1px solid #ccc;padding:6px 12px;text-align:left}
th{background:#E3F2FD;font-weight:600}
tr:nth-child(even){background:#FAFAFA}
code{background:#f5f5f5;padding:2px 5px;border-radius:3px;font-size:.9em}
pre{background:#263238;color:#eee;padding:16px;border-radius:6px;overflow-x:auto;font-size:.88em;line-height:1.5}
pre code{background:none;color:inherit;padding:0}
hr{border:none;border-top:1px solid #ddd;margin:2em 0}
strong{color:#1565C0}
a.cite{color:#1565C0;text-decoration:none;font-weight:600}
a.cite:hover{text-decoration:underline}
.cite-unknown{color:red}
ol.references{font-size:.92em;line-height:1.6}
ol.references li{margin-bottom:4px}
</style></head><body>
"""

html = header + "<p>\n" + body + "\n</p>\n" + ref_list + "\n</body></html>"

out = PAPER_DIR / "submission" / "paper.html"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(html, encoding="utf-8")
print(f"HTML rendered: {out}")
print(f"  Size: {len(html):,} bytes")
print(f"  References: {len(keys_in_order)}")
