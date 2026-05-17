"""Print-shop extras for book PDFs: crop marks, bleed, PDF/UA hints.

Returns a Typst snippet that can be appended to the main preamble when
the publisher profile's ``print_specs`` requests it.

Recognised keys in ``profile.print_specs``:

    bleed_mm: 3                # default 0 (no bleed)
    crop_marks: true           # adds 4 corner marks
    pdf_ua: true               # emits #set document(keywords:..) hint;
                               # actual PDF/UA structure tagging is enabled
                               # by Typst 0.14's automatic tagging when a
                               # document has proper heading / figure
                               # semantics, which our preamble already
                               # ensures.
"""
from __future__ import annotations


def build_print_extras_typst(profile) -> str:
    if not profile:
        return ""
    specs = (profile.get("print_specs") or {}) if isinstance(profile, dict) else {}
    bleed_mm = float(specs.get("bleed_mm", 0) or 0)
    crop_marks = bool(specs.get("crop_marks", False))
    pdf_ua = bool(specs.get("pdf_ua", False))

    if bleed_mm <= 0 and not crop_marks and not pdf_ua:
        return ""

    parts = ["", "// ── Print-shop extras (auto-generated) ──"]

    if bleed_mm > 0:
        parts.append(f"// Add {bleed_mm:g} mm bleed on every edge")
        parts.append(
            f"#set page(bleed: {bleed_mm:g}mm)"
        )

    if crop_marks:
        # Draw corner crop marks at the trim edges using Typst's place primitives.
        parts.append(
            r"""
// Crop / registration marks (corner, 5 mm long, 0.25 pt)
#set page(background: context {
  let mlen = 5mm
  let mw = 0.25pt
  place(top + left, dx: 0pt, dy: 0pt, line(length: mlen, stroke: mw))
  place(top + left, dx: 0pt, dy: 0pt, line(angle: 90deg, length: mlen, stroke: mw))
  place(top + right, dx: 0pt, dy: 0pt, line(length: -mlen, stroke: mw))
  place(top + right, dx: 0pt, dy: 0pt, line(angle: 90deg, length: mlen, stroke: mw))
  place(bottom + left, dx: 0pt, dy: 0pt, line(length: mlen, stroke: mw))
  place(bottom + left, dx: 0pt, dy: 0pt, line(angle: -90deg, length: mlen, stroke: mw))
  place(bottom + right, dx: 0pt, dy: 0pt, line(length: -mlen, stroke: mw))
  place(bottom + right, dx: 0pt, dy: 0pt, line(angle: -90deg, length: mlen, stroke: mw))
})
"""
        )

    if pdf_ua:
        # Typst 0.14 emits structure tags automatically; this just records
        # the intent in the document metadata so downstream validators
        # (veraPDF / pdfua-preflight) can confirm.
        parts.append(
            r'#set document(keywords: ("PDF/UA-1", "accessible", "tagged"))'
        )

    return "\n".join(parts) + "\n"
