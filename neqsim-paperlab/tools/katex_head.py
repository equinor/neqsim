"""Canonical KaTeX HTML head block for all paperlab HTML renderers.

Every HTML generator in this folder (``book_render_html.py``,
``render_all.py``, ``render_html_generic.py``) must use this exact block so
that math renders identically and robustly across:

* CDN-loaded multi-file output (``book.html`` + external KaTeX), and
* self-contained single-file output (``book_single.html`` where the KaTeX
  CSS/JS and images are inlined as ``<style>`` / inline ``<script>`` / data
  URIs).

Why not the auto-render ``onload="renderMathInElement(...)"`` trigger?
--------------------------------------------------------------------
1. When the page is post-processed into a single self-contained file, the
   inlined ``<script>`` has no ``src`` and therefore never fires ``onload``,
   so an ``onload``-based trigger is silently dropped and equations show as
   raw ``$$ ... $$`` text.
2. Naive ``$``-delimiter injection during inlining (e.g. PowerShell
   ``-replace``) can corrupt ``$$`` into ``$``.

This block sidesteps both problems: it calls ``katex.render`` directly on
tokenized text nodes (immune to delimiter corruption) and polls for
``window.katex`` (works whether KaTeX is deferred from a CDN or inlined
ahead of this block). See the repo memory note
``paperlab_single_html_katex_render.md``.
"""

# KaTeX version pinned across every renderer.
KATEX_VERSION = "0.16.9"

# The <link> + <script> head block. Plain string (NO f-string / .format),
# so the JavaScript braces need no escaping. Concatenate or insert verbatim.
KATEX_HEAD_BLOCK = (
    '<link rel="stylesheet" '
    'href="https://cdn.jsdelivr.net/npm/katex@' + KATEX_VERSION + '/dist/katex.min.css"/>\n'
    '<script defer '
    'src="https://cdn.jsdelivr.net/npm/katex@' + KATEX_VERSION + '/dist/katex.min.js"></script>\n'
    + """<script>
// Render math with katex.render directly instead of the auto-render delimiter
// splitter. This is immune to inlining quirks (an inlined <script> never fires
// `onload`, so an auto-render `onload` trigger is silently dropped) and to
// delimiter corruption. We walk text nodes, tokenize $$...$$ (display) and
// $...$ (inline), and call katex.render on each. Polling for `window.katex`
// works whether KaTeX loads from the CDN (deferred) or is inlined ahead of
// this block.
(function () {
  var SKIP = {SCRIPT:1, STYLE:1, TEXTAREA:1, PRE:1, CODE:1, OPTION:1, NOSCRIPT:1};
  function processTextNode(node) {
    var text = node.nodeValue;
    if (text.indexOf("$") < 0) return;
    var parts = [], i = 0, n = text.length, last = 0;
    while (i < n) {
      if (text.charAt(i) === "$") {
        var display = (text.charAt(i + 1) === "$");
        var delim = display ? "$$" : "$";
        var start = i + delim.length;
        var end = text.indexOf(delim, start);
        if (end < 0) { i++; continue; }
        if (i > last) parts.push({literal: text.slice(last, i)});
        parts.push({tex: text.slice(start, end), display: display});
        i = end + delim.length;
        last = i;
      } else { i++; }
    }
    if (!parts.length) return;
    if (last < n) parts.push({literal: text.slice(last)});
    var frag = document.createDocumentFragment();
    for (var k = 0; k < parts.length; k++) {
      var pt = parts[k];
      if (pt.literal != null) {
        frag.appendChild(document.createTextNode(pt.literal));
      } else {
        var span = document.createElement("span");
        try {
          window.katex.render(pt.tex, span, {displayMode: pt.display, throwOnError: false});
        } catch (e) {
          span.textContent = (pt.display ? "$$" : "$") + pt.tex + (pt.display ? "$$" : "$");
        }
        frag.appendChild(span);
      }
    }
    node.parentNode.replaceChild(frag, node);
  }
  function walk(el) {
    if (el.nodeType === 1 && (SKIP[el.nodeName] || (el.classList && el.classList.contains("katex")))) return;
    var child = el.firstChild;
    while (child) {
      var next = child.nextSibling;
      if (child.nodeType === 3) processTextNode(child);
      else if (child.nodeType === 1) walk(child);
      child = next;
    }
  }
  function renderMath() {
    if (!window.katex || typeof window.katex.render !== "function") return false;
    walk(document.body);
    return true;
  }
  function waitAndRender() {
    if (renderMath()) return;
    var tries = 0;
    var timer = setInterval(function () {
      if (renderMath() || ++tries > 100) clearInterval(timer);
    }, 50);
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", waitAndRender);
  } else {
    waitAndRender();
  }
})();
</script>
"""
)
