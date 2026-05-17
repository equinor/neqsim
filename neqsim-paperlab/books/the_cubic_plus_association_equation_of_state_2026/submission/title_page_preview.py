"""Generate a standalone HTML preview of the CPA book title page."""
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', '..', 'tools'))
import book_builder
from book_render_html import _generate_title_page, _get_book_icon_svg

book_dir = os.path.join(os.path.dirname(__file__), '..')
cfg = book_builder.load_book_config(book_dir)

html = _generate_title_page(cfg)

preview = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>CPA Book — Title Page Preview</title>
<style>
body {
  font-family: Georgia, 'Times New Roman', serif;
  margin: 0;
  background: #fafafa;
}
.title-page {
  min-height: 90vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 4rem 2rem;
  position: relative;
  background: #fff;
  max-width: 700px;
  margin: 2rem auto;
  box-shadow: 0 2px 20px rgba(0,0,0,0.08);
}
.title-page::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 8px;
  background: linear-gradient(90deg, #0d3b66, #1a5276, #2980b9, #1a5276, #0d3b66);
}
.title-page::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 4px;
  background: linear-gradient(90deg, #0d3b66, #1a5276, #2980b9, #1a5276, #0d3b66);
}
.title-page .tp-decoration {
  width: 150px;
  height: 150px;
  margin: 0 auto 2rem;
}
.title-page .tp-decoration svg {
  width: 100%;
  height: 100%;
}
.title-page .tp-title {
  font-size: 2.2rem;
  font-weight: bold;
  color: #0d3b66;
  letter-spacing: -0.3px;
  line-height: 1.2;
  max-width: 600px;
}
.title-page .tp-subtitle {
  font-size: 1.15rem;
  font-style: italic;
  color: #666;
  margin-top: 0.5rem;
}
.title-page .tp-rule {
  width: 200px;
  height: 2px;
  background: linear-gradient(90deg, #0d3b66, #2980b9);
  margin: 1.5rem auto;
  border: none;
}
.title-page .tp-author {
  font-size: 1.1rem;
  letter-spacing: 1px;
  color: #333;
  font-variant: small-caps;
}
.title-page .tp-affiliation {
  font-size: 0.9rem;
  font-style: italic;
  color: #888;
  margin-bottom: 0.5rem;
}
.title-page .tp-edition {
  font-size: 0.85rem;
  color: #888;
  margin-top: 1rem;
}
.title-page .tp-year {
  font-size: 0.85rem;
  color: #888;
}
.title-page .tp-publisher {
  font-size: 1rem;
  letter-spacing: 2px;
  color: #444;
  text-transform: uppercase;
  font-weight: 600;
  margin-top: 1rem;
}
</style>
</head>
<body>
""" + html + """
</body>
</html>
"""

out_path = os.path.join(os.path.dirname(__file__), 'title_page_preview.html')
with open(out_path, 'w', encoding='utf-8') as f:
    f.write(preview)
print(f"Preview written to: {out_path}")
print("Open in a browser to see the CPA molecular association emblem.")
