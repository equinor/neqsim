"""Render paper.md to a standalone HTML file with KaTeX math and embedded figures."""
import re
from pathlib import Path

PAPER_DIR = Path(__file__).resolve().parent.parent / "papers" / "tpflash_algorithms_2026"

paper = (PAPER_DIR / "paper.md").read_text(encoding="utf-8")

HTML_HEADER = r"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>TPflash Algorithm Characterization - NeqSim</title>
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

FIGURES_SECTION = """
<div style="background: #E3F2FD; padding: 20px; border-radius: 8px; margin: 2em 0;">
<h2 style="margin-top: 0;">Figures</h2>

<div class="fig-container">
<img src="../figures/fig1_convergence_by_family.png" alt="Fig. 1">
<p class="fig-caption">Fig. 1. Convergence rate and two-phase fraction by fluid family.</p>
</div>

<div class="fig-container">
<img src="../figures/fig2_convergence_maps.png" alt="Fig. 2">
<p class="fig-caption">Fig. 2. Temperature-pressure phase maps for all six families.</p>
</div>

<div class="fig-container">
<img src="../figures/fig3_timing_distribution.png" alt="Fig. 3">
<p class="fig-caption">Fig. 3. CPU timing distribution by family (box plots) and overall histogram.</p>
</div>

<div class="fig-container">
<img src="../figures/fig4_beta_distribution.png" alt="Fig. 4">
<p class="fig-caption">Fig. 4. Vapor fraction distribution for two-phase cases.</p>
</div>

<div class="fig-container">
<img src="../figures/fig5_time_vs_components.png" alt="Fig. 5">
<p class="fig-caption">Fig. 5. Median CPU time scaling with number of components.</p>
</div>

<div class="fig-container">
<img src="../figures/fig6_near_critical_analysis.png" alt="Fig. 6">
<p class="fig-caption">Fig. 6. Near-critical behavior: timing vs. pressure colored by phase count.</p>
</div>
</div>
"""

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
body = re.sub(r'```(\w*)\n(.*?)```', r'<pre><code>\2</code></pre>', body, flags=re.DOTALL)

# Inline code
body = re.sub(r'`([^`]+)`', r'<code>\1</code>', body)

# Horizontal rules
body = re.sub(r'^---$', '<hr>', body, flags=re.MULTILINE)


# Tables
def convert_table(match):
    lines = match.group(0).strip().split("\n")
    rows = [l for l in lines if l.strip() and not re.match(r'^\|[\s:\-]+\|$', l)]
    html_t = '<table>\n'
    for i, row in enumerate(rows):
        cells = [c.strip() for c in row.split('|')[1:-1]]
        tag = 'th' if i == 0 else 'td'
        html_t += '<tr>' + ''.join('<{0}>{1}</{0}>'.format(tag, c) for c in cells) + '</tr>\n'
    html_t += '</table>'
    return html_t


body = re.sub(r'(\|.+\|(?:\n\|.+\|)+)', convert_table, body)

# Lists (bullet)
body = re.sub(r'^- (.+)$', r'<li>\1</li>', body, flags=re.MULTILINE)

# Paragraphs (double newline)
body = re.sub(r'\n\n+', '\n</p>\n<p>\n', body)

# Insert figures before conclusions
body = body.replace(
    '<h2>6. Conclusions</h2>',
    FIGURES_SECTION + '\n<h2>6. Conclusions</h2>'
)

html = HTML_HEADER + "<p>\n" + body + "\n</p>\n</body>\n</html>"

out = PAPER_DIR / "submission" / "paper.html"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(html, encoding="utf-8")
print(f"HTML rendered to {out}")
print(f"  Size: {len(html):,} bytes")
