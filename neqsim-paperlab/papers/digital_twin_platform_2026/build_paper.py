#!/usr/bin/env python3
"""Convert paper.md to publication-quality HTML with embedded figures."""

import base64
import re
from pathlib import Path

PAPER_DIR = Path(__file__).parent
FIGURES_DIR = PAPER_DIR / "figures"
SUBMISSION_DIR = PAPER_DIR / "submission"
SUBMISSION_DIR.mkdir(exist_ok=True)

# ── Read paper markdown ──
paper_md = (PAPER_DIR / "paper.md").read_text(encoding="utf-8")

# ── Strip YAML-like comments at top ──
paper_md = re.sub(r"^<!--.*?-->\s*\n", "", paper_md, flags=re.MULTILINE)

# ── Figure captions & insertion points ──
FIGURE_MAP = {
    "Fig. 1": "fig1_platform_architecture.png",
    "Fig. 2": "fig2_data_integration_workflow.png",
    "Fig. 3": "fig3_execution_strategies.png",
    "Fig. 4": "fig4_compressor_digital_twin.png",
    "Fig. 5": "fig5_capability_comparison_radar.png",
    "Fig. 6": "fig6_deployment_pipeline.png",
}

def embed_image_b64(filename):
    """Return base64-encoded img tag for embedding in HTML."""
    fpath = FIGURES_DIR / filename
    if not fpath.exists():
        return f'<p style="color:red">[Missing figure: {filename}]</p>'
    data = fpath.read_bytes()
    b64 = base64.b64encode(data).decode("ascii")
    return f'<img src="data:image/png;base64,{b64}" style="max-width:100%;margin:1em auto;display:block" alt="{filename}">'


def md_to_html(md_text):
    """Simple markdown-to-HTML converter for academic papers."""
    lines = md_text.split("\n")
    html_parts = []
    in_code_block = False
    in_table = False
    table_rows = []
    in_list = False
    list_items = []

    def flush_table():
        nonlocal table_rows, in_table
        if not table_rows:
            return ""
        # Parse header and rows
        header = table_rows[0]
        rows = table_rows[2:]  # skip separator
        cols_h = [c.strip() for c in header.strip("|").split("|")]
        out = '<table>\n<thead><tr>'
        for c in cols_h:
            out += f'<th>{process_inline(c)}</th>'
        out += '</tr></thead>\n<tbody>\n'
        for row in rows:
            cells = [c.strip() for c in row.strip("|").split("|")]
            out += '<tr>'
            for c in cells:
                out += f'<td>{process_inline(c)}</td>'
            out += '</tr>\n'
        out += '</tbody></table>\n'
        table_rows = []
        in_table = False
        return out

    def flush_list():
        nonlocal list_items, in_list
        if not list_items:
            return ""
        out = '<ol>\n'
        for item in list_items:
            out += f'<li>{process_inline(item)}</li>\n'
        out += '</ol>\n'
        list_items = []
        in_list = False
        return out

    def process_inline(text):
        """Process inline markdown: bold, italic, code, links, citations."""
        # Code spans
        text = re.sub(r'`([^`]+)`', r'<code>\1</code>', text)
        # Bold
        text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
        # Italic
        text = re.sub(r'\*(.+?)\*', r'<em>\1</em>', text)
        # Links
        text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', text)
        # Citation references [1,2] or [1]
        text = re.sub(r'\[(\d[\d,\s]*)\]', r'<span class="cite">[\1]</span>', text)
        return text

    i = 0
    while i < len(lines):
        line = lines[i]

        # Code blocks
        if line.strip().startswith("```"):
            if in_table:
                html_parts.append(flush_table())
            if in_list:
                html_parts.append(flush_list())
            if not in_code_block:
                lang = line.strip().lstrip("`").strip()
                html_parts.append(f'<pre><code class="{lang}">')
                in_code_block = True
            else:
                html_parts.append('</code></pre>\n')
                in_code_block = False
            i += 1
            continue

        if in_code_block:
            # Escape HTML in code
            escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            html_parts.append(escaped + "\n")
            i += 1
            continue

        # Table rows
        if line.strip().startswith("|") and "|" in line[1:]:
            if in_list:
                html_parts.append(flush_list())
            table_rows.append(line)
            in_table = True
            i += 1
            continue
        elif in_table:
            html_parts.append(flush_table())

        # Numbered list items
        m_list = re.match(r'^(\d+)\.\s+(.+)', line)
        if m_list:
            if in_table:
                html_parts.append(flush_table())
            list_items.append(m_list.group(2))
            in_list = True
            i += 1
            continue
        elif in_list and line.strip() == "":
            html_parts.append(flush_list())
            i += 1
            continue
        elif in_list and not line.startswith("   "):
            html_parts.append(flush_list())

        # Headings
        if line.startswith("# "):
            html_parts.append(f'<h1>{process_inline(line[2:].strip())}</h1>\n')
            i += 1
            continue
        if line.startswith("## "):
            html_parts.append(f'<h2>{process_inline(line[3:].strip())}</h2>\n')
            i += 1
            continue
        if line.startswith("### "):
            html_parts.append(f'<h3>{process_inline(line[4:].strip())}</h3>\n')
            i += 1
            continue

        # Horizontal rule
        if line.strip() == "---":
            html_parts.append('<hr>\n')
            i += 1
            continue

        # Empty line
        if line.strip() == "":
            i += 1
            continue

        # Regular paragraph
        para = process_inline(line.strip())
        html_parts.append(f'<p>{para}</p>\n')
        i += 1

    # Flush remaining
    if in_table:
        html_parts.append(flush_table())
    if in_list:
        html_parts.append(flush_list())

    return "".join(html_parts)


# ── Convert ──
html_body = md_to_html(paper_md)

# ── Insert figures at appropriate locations ──
# Insert figures after their first textual reference "(Fig. N)"
for fig_ref, fig_file in FIGURE_MAP.items():
    # Find the paragraph containing the first reference
    pattern = re.escape(f"({fig_ref})")
    img_html = (
        f'<div style="margin:1.5em 0;text-align:center">\n'
        f'{embed_image_b64(fig_file)}\n'
        f'<p style="font-size:0.9em;color:#555;margin-top:0.5em"><em>{fig_ref}.</em></p>\n'
        f'</div>\n'
    )
    # Insert after the paragraph containing the reference
    match = re.search(pattern, html_body)
    if match:
        # Find end of the enclosing paragraph
        end_p = html_body.find("</p>", match.end())
        if end_p != -1:
            insert_pos = end_p + len("</p>") + 1
            html_body = html_body[:insert_pos] + img_html + html_body[insert_pos:]

# ── Build complete HTML ──
html_doc = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8">
<title>Open-Source Digital Twin Platform for Oil and Gas</title>
<style>
body{{max-width:900px;margin:40px auto;font-family:Georgia,serif;line-height:1.7;color:#333;padding:0 20px}}
h1{{font-size:1.8em;border-bottom:2px solid #2196F3;padding-bottom:10px;margin-top:0}}
h2{{font-size:1.4em;color:#1565C0;margin-top:2em}}
h3{{font-size:1.15em;color:#1976D2}}
table{{border-collapse:collapse;margin:1em auto;font-size:.95em;width:auto}}
th,td{{border:1px solid #ccc;padding:6px 12px;text-align:left}}
th{{background:#E3F2FD;font-weight:600}}
tr:nth-child(even){{background:#FAFAFA}}
code{{background:#f5f5f5;padding:2px 5px;border-radius:3px;font-size:.9em}}
pre{{background:#263238;color:#eee;padding:16px;border-radius:6px;overflow-x:auto;font-size:.88em;line-height:1.5}}
pre code{{background:none;color:inherit;padding:0}}
hr{{border:none;border-top:1px solid #ddd;margin:2em 0}}
strong{{color:#1565C0}}
.cite{{color:#1565C0;font-size:.9em}}
img{{max-width:100%;height:auto;border:1px solid #eee;border-radius:4px}}
ol,ul{{padding-left:1.5em}}
li{{margin-bottom:0.3em}}
a{{color:#1565C0;text-decoration:none}}
a:hover{{text-decoration:underline}}
@media print{{
  body{{max-width:100%;margin:0;padding:10px;font-size:11pt}}
  pre{{font-size:8pt}}
  img{{max-width:100%}}
  h1{{font-size:1.5em}}
  h2{{font-size:1.2em;page-break-after:avoid}}
}}
</style></head><body>
{html_body}
</body></html>
"""

# ── Write output ──
out_path = SUBMISSION_DIR / "paper.html"
out_path.write_text(html_doc, encoding="utf-8")
print(f"Paper HTML written to: {out_path}")
print(f"File size: {out_path.stat().st_size / 1024:.0f} KB")

# Also copy supporting files to submission
import shutil
for fname in ["highlights.txt", "refs.bib", "cover_letter.md"]:
    src = PAPER_DIR / fname
    dst = SUBMISSION_DIR / fname
    if src.exists():
        shutil.copy2(str(src), str(dst))
        print(f"Copied: {fname}")

print("\nSubmission package complete in:", SUBMISSION_DIR)
