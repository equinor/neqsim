"""Render paper.md to a standalone HTML file with KaTeX math and embedded figures.

Generic version that works with any paper project directory.

Usage:
    python render_html.py <paper_dir>
    python render_html.py papers/tpflash_algorithms_2026
    python render_html.py papers/gibbs_minimization_2026
"""
import re
import sys
import json
from pathlib import Path


def get_html_header(title="Paper"):
    """Generate HTML header with KaTeX and styling."""
    return r"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>""" + title + r"""</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"
  onload="renderMathInElement(document.body, {
    delimiters: [
      {left: '$$', right: '$$', display: true},
      {left: '$', right: '$', display: false}
    ]
  })"></script>
<style>
  body { max-width: 900px; margin: 40px auto; font-family: 'Georgia', serif;
         line-height: 1.7; color: #333; padding: 0 20px; }
  h1 { font-size: 1.8em; border-bottom: 2px solid #2196F3; padding-bottom: 10px; }
  h2 { font-size: 1.4em; color: #1565C0; margin-top: 2em; }
  h3 { font-size: 1.15em; color: #1976D2; }
  table { border-collapse: collapse; margin: 1em auto; font-size: 0.95em; }
  th, td { border: 1px solid #ccc; padding: 6px 12px; text-align: left; }
  th { background: #E3F2FD; font-weight: 600; }
  tr:nth-child(even) { background: #FAFAFA; }
  code { background: #f5f5f5; padding: 2px 5px; border-radius: 3px; font-size: 0.9em; }
  pre { background: #263238; color: #eee; padding: 16px; border-radius: 6px;
        overflow-x: auto; font-size: 0.88em; line-height: 1.5; }
  pre code { background: none; color: inherit; padding: 0; }
  blockquote { border-left: 4px solid #2196F3; margin: 1em 0; padding: 8px 16px;
               background: #E3F2FD; }
  img { max-width: 100%; display: block; margin: 1em auto; }
  .fig-container { text-align: center; margin: 2em 0; }
  .fig-caption { font-style: italic; color: #666; margin-top: 0.5em; }
  hr { border: none; border-top: 1px solid #ddd; margin: 2em 0; }
  strong { color: #1565C0; }
</style>
</head>
<body>
"""


def build_figures_section(paper_dir):
    """Build HTML for figures from results.json captions and figure files."""
    paper_dir = Path(paper_dir)
    fig_dir = paper_dir / "figures"

    # Try to load captions from results.json
    captions = {}
    results_file = paper_dir / "results.json"
    if results_file.exists():
        data = json.loads(results_file.read_text(encoding="utf-8"))
        captions = data.get("figure_captions", {})

    # Find figure files
    if not fig_dir.exists():
        return ""

    fig_files = sorted(fig_dir.glob("fig*.png")) + sorted(fig_dir.glob("fig*.pdf"))
    if not fig_files:
        return ""

    html = '\n<div style="background: #E3F2FD; padding: 20px; border-radius: 8px; margin: 2em 0;">\n'
    html += '<h2 style="margin-top: 0;">Figures</h2>\n\n'

    for i, fig_path in enumerate(fig_files, 1):
        fname = fig_path.name
        caption = captions.get(fname, f"Figure {i}")
        rel_path = f"../figures/{fname}"
        html += f'<div class="fig-container">\n'
        html += f'<img src="{rel_path}" alt="Fig. {i}">\n'
        html += f'<p class="fig-caption">Fig. {i}. {caption}</p>\n'
        html += f'</div>\n\n'

    html += '</div>\n'
    return html


def convert_table(match):
    """Convert a markdown table to HTML."""
    lines = match.group(0).strip().split("\n")
    rows = [l for l in lines if l.strip() and not re.match(r'^\|[\s:\-]+\|$', l)]
    html_t = '<table>\n'
    for i, row in enumerate(rows):
        cells = [c.strip() for c in row.split('|')[1:-1]]
        tag = 'th' if i == 0 else 'td'
        html_t += '<tr>' + ''.join('<{0}>{1}</{0}>'.format(tag, c) for c in cells) + '</tr>\n'
    html_t += '</table>'
    return html_t


def render_paper_html(paper_dir):
    """Render paper.md to standalone HTML.

    Args:
        paper_dir: Path to the paper project directory

    Returns:
        Path to the generated HTML file
    """
    paper_dir = Path(paper_dir)
    paper_file = paper_dir / "paper.md"

    if not paper_file.exists():
        print(f"Error: {paper_file} not found")
        sys.exit(1)

    paper = paper_file.read_text(encoding="utf-8")

    # Extract title from H1
    title_match = re.search(r'^# (.+)$', paper, re.MULTILINE)
    title = title_match.group(1) if title_match else "Paper"

    body = paper

    # Remove HTML comments
    body = re.sub(r'<!--.*?-->', '', body, flags=re.DOTALL)

    # Headers
    body = re.sub(r'^### (.+)$', r'<h3>\1</h3>', body, flags=re.MULTILINE)
    body = re.sub(r'^## (.+)$', r'<h2>\1</h2>', body, flags=re.MULTILINE)
    body = re.sub(r'^# (.+)$', r'<h1>\1</h1>', body, flags=re.MULTILINE)

    # Bold and italic
    body = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', body)
    body = re.sub(r'\*(.+?)\*', r'<em>\1</em>', body)

    # Code blocks
    body = re.sub(r'```(\w*)\n(.*?)```', r'<pre><code>\2</code></pre>',
                  body, flags=re.DOTALL)

    # Inline code
    body = re.sub(r'`([^`]+)`', r'<code>\1</code>', body)

    # Horizontal rules
    body = re.sub(r'^---$', '<hr>', body, flags=re.MULTILINE)

    # Tables
    body = re.sub(r'(\|.+\|(?:\n\|.+\|)+)', convert_table, body)

    # Lists (bullet)
    body = re.sub(r'^- (.+)$', r'<li>\1</li>', body, flags=re.MULTILINE)

    # Markdown images -> HTML figures
    def _img_replace(m):
        alt = m.group(1)
        src = m.group(2)
        return f'<div class="fig-container"><img src="{src}" alt="{alt}">' \
               f'<p class="fig-caption">{alt}</p></div>'
    body = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', _img_replace, body)

    # Paragraphs (double newline)
    body = re.sub(r'\n\n+', '\n</p>\n<p>\n', body)

    # Build figures section from actual files
    figures_html = build_figures_section(paper_dir)

    # Insert figures before conclusions (if section exists)
    conclusions_patterns = [
        r'<h2>\d+\.\s*Conclusions?</h2>',
        r'<h2>Conclusions?</h2>',
    ]
    inserted = False
    for pattern in conclusions_patterns:
        match = re.search(pattern, body)
        if match:
            body = body[:match.start()] + figures_html + '\n' + body[match.start():]
            inserted = True
            break
    if not inserted and figures_html:
        body += figures_html

    html = get_html_header(title) + "<p>\n" + body + "\n</p>\n</body>\n</html>"

    out_dir = paper_dir / "submission"
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / "paper.html"
    out.write_text(html, encoding="utf-8")

    print(f"HTML rendered to {out}")
    print(f"  Size: {len(html):,} bytes")
    return out


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python render_html.py <paper_dir>")
        print("Example: python render_html.py papers/tpflash_algorithms_2026")
        sys.exit(1)

    render_paper_html(sys.argv[1])
