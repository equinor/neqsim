"""
Chapter Illustrations — Programmatic figures for book chapters.

No API keys, no GPU, no subscriptions. Generates professional conceptual
diagrams, chapter headers, section dividers, and schematic illustrations
using only Pillow and matplotlib.

These are *not* data plots (use figure_style.py for those). These are
decorative and conceptual figures: process schematics, concept maps,
comparison tables as images, timeline graphics, and chapter openers.

Usage::

    from tools.chapter_illustrations import (
        chapter_header,
        concept_diagram,
        comparison_table_figure,
        process_flow_figure,
        timeline_figure,
        equation_highlight_figure,
    )

    # Chapter opener with number and title
    chapter_header(3, "Thermodynamic Models",
                   subtitle="SRK, PR, and CPA equations of state",
                   output_path="figures/ch03_header.png")

    # Concept diagram with central topic and branches
    concept_diagram(
        center="CPA EOS",
        branches=["Physical term (SRK)", "Association term",
                  "Mixing rules", "Pure component parameters"],
        output_path="figures/cpa_concept.png")

    # Side-by-side comparison table as a figure
    comparison_table_figure(
        title="EOS Comparison",
        headers=["Property", "SRK", "PR", "CPA"],
        rows=[["Polar compounds", "Poor", "Poor", "Good"],
              ["Liquid density", "Fair", "Good", "Good"],
              ["Association", "No", "No", "Yes"]],
        output_path="figures/eos_comparison.png")
"""

import math
from pathlib import Path
from typing import List, Optional, Tuple

try:
    from PIL import Image, ImageDraw, ImageFont
    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    _HAS_MPL = True
except ImportError:
    _HAS_MPL = False


# ── Color schemes ─────────────────────────────────────────────────────────

_SCHEME = {
    "primary": (26, 82, 118),       # Deep blue
    "secondary": (0, 160, 220),     # Bright blue
    "accent": (231, 76, 60),        # Red accent
    "bg": (250, 251, 252),          # Near-white
    "text": (30, 30, 30),
    "text_light": (120, 120, 120),
    "border": (200, 210, 220),
    "highlight": (255, 248, 230),   # Warm highlight
    "success": (46, 139, 87),       # Green
    "node_colors": [
        (0, 102, 179), (0, 160, 220), (46, 139, 87),
        (200, 120, 50), (140, 80, 160), (180, 60, 60),
    ],
}


# ── Font helper ───────────────────────────────────────────────────────────

def _font(size=24, bold=False, serif=False):
    """Load a font with fallbacks.

    Args:
        size: Point size.
        bold: Bold weight.
        serif: Serif family (else sans).

    Returns:
        PIL ImageFont.
    """
    if serif:
        names = (["timesbd.ttf", "DejaVuSerif-Bold.ttf"] if bold
                 else ["times.ttf", "DejaVuSerif.ttf"])
    else:
        names = (["arialbd.ttf", "DejaVuSans-Bold.ttf",
                  "LiberationSans-Bold.ttf"] if bold
                 else ["arial.ttf", "DejaVuSans.ttf",
                       "LiberationSans-Regular.ttf"])
    for n in names:
        try:
            return ImageFont.truetype(n, size)
        except (IOError, OSError):
            continue
    return ImageFont.load_default()


def _text_size(draw, text, font):
    """Measure text width and height.

    Args:
        draw: PIL ImageDraw.
        text: String to measure.
        font: PIL ImageFont.

    Returns:
        (width, height) tuple.
    """
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def _wrap(text, font, max_w, draw):
    """Word-wrap text to fit max_w pixels.

    Args:
        text: Input string.
        font: PIL ImageFont.
        max_w: Max pixel width.
        draw: PIL ImageDraw for measurement.

    Returns:
        List of line strings.
    """
    words = text.split()
    lines, cur = [], ""
    for w in words:
        test = (cur + " " + w).strip()
        tw, _ = _text_size(draw, test, font)
        if tw > max_w and cur:
            lines.append(cur)
            cur = w
        else:
            cur = test
    if cur:
        lines.append(cur)
    return lines


# ── Chapter Header ────────────────────────────────────────────────────────

def chapter_header(chapter_num, title, subtitle="",
                   width=1800, height=500, output_path=None):
    """Generate a styled chapter opening header image.

    Args:
        chapter_num: Chapter number (int).
        title: Chapter title.
        subtitle: Optional subtitle.
        width: Image width.
        height: Image height.
        output_path: Output file path.

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    canvas = Image.new("RGB", (width, height), _SCHEME["bg"])
    draw = ImageDraw.Draw(canvas)

    # Left accent bar
    bar_w = 12
    draw.rectangle([0, 0, bar_w, height], fill=_SCHEME["primary"])

    # Chapter number — large, light
    num_font = _font(160, bold=True)
    num_text = str(chapter_num)
    nw, nh = _text_size(draw, num_text, num_font)
    draw.text((width - nw - 60, (height - nh) // 2 - 10),
              num_text, fill=(230, 235, 240), font=num_font)

    # "Chapter N" label
    label_font = _font(22)
    label = "CHAPTER {}".format(chapter_num)
    draw.text((bar_w + 50, 60), label,
              fill=_SCHEME["secondary"], font=label_font)

    # Title
    title_font = _font(52, bold=True)
    max_w = width - 250
    lines = _wrap(title, title_font, max_w, draw)
    y = 110
    for line in lines:
        draw.text((bar_w + 50, y), line,
                  fill=_SCHEME["text"], font=title_font)
        _, lh = _text_size(draw, line, title_font)
        y += lh + 12

    # Subtitle
    if subtitle:
        y += 10
        sub_font = _font(26)
        sub_lines = _wrap(subtitle, sub_font, max_w, draw)
        for line in sub_lines:
            draw.text((bar_w + 50, y), line,
                      fill=_SCHEME["text_light"], font=sub_font)
            _, lh = _text_size(draw, line, sub_font)
            y += lh + 6

    # Bottom rule
    draw.line([(bar_w + 50, height - 40),
               (width - 60, height - 40)],
              fill=_SCHEME["border"], width=1)

    if output_path is None:
        output_path = "ch{:02d}_header.png".format(chapter_num)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)


# ── Concept Diagram ───────────────────────────────────────────────────────

def concept_diagram(center, branches, output_path=None,
                    width=1200, height=900, title=None):
    """Generate a radial concept map with center topic and branches.

    Args:
        center: Central concept text.
        branches: List of branch labels (up to 8).
        output_path: Output file path.
        width: Image width.
        height: Image height.
        title: Optional title above the diagram.

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    cx, cy = width // 2, height // 2
    if title:
        cy += 30  # shift down to make room for title

    # Title
    if title:
        tf = _font(28, bold=True)
        tw, _ = _text_size(draw, title, tf)
        draw.text(((width - tw) // 2, 25), title,
                  fill=_SCHEME["text"], font=tf)

    # Draw branches
    n = len(branches)
    radius = min(width, height) * 0.32
    node_r = 70
    center_r = 85
    branch_font = _font(18, bold=True)
    colors = _SCHEME["node_colors"]

    for i, label in enumerate(branches):
        angle = 2 * math.pi * i / n - math.pi / 2
        nx = cx + int(radius * math.cos(angle))
        ny = cy + int(radius * math.sin(angle))

        # Connection line
        draw.line([(cx, cy), (nx, ny)], fill=_SCHEME["border"], width=2)

        # Node circle
        color = colors[i % len(colors)]
        draw.ellipse([nx - node_r, ny - node_r, nx + node_r, ny + node_r],
                     fill=color, outline=None)

        # Node label (word-wrapped)
        lines = _wrap(label, branch_font, node_r * 2 - 16, draw)
        total_h = len(lines) * 22
        ly = ny - total_h // 2
        for line in lines:
            lw, _ = _text_size(draw, line, branch_font)
            draw.text((nx - lw // 2, ly), line,
                      fill=(255, 255, 255), font=branch_font)
            ly += 22

    # Center node
    draw.ellipse([cx - center_r, cy - center_r,
                  cx + center_r, cy + center_r],
                 fill=_SCHEME["primary"], outline=None)
    center_font = _font(22, bold=True)
    c_lines = _wrap(center, center_font, center_r * 2 - 20, draw)
    total_h = len(c_lines) * 28
    ly = cy - total_h // 2
    for line in c_lines:
        lw, _ = _text_size(draw, line, center_font)
        draw.text((cx - lw // 2, ly), line,
                  fill=(255, 255, 255), font=center_font)
        ly += 28

    if output_path is None:
        output_path = "concept_diagram.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)


# ── Comparison Table Figure ───────────────────────────────────────────────

def comparison_table_figure(title, headers, rows, output_path=None,
                            width=1400, highlight_col=None):
    """Render a comparison table as a professional figure image.

    Args:
        title: Table title.
        headers: List of column header strings.
        rows: List of row lists (each row = list of cell strings).
        output_path: Output file path.
        width: Image width.
        highlight_col: Column index to highlight (0-based, optional).

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    n_cols = len(headers)
    n_rows = len(rows)
    col_w = (width - 80) // n_cols
    row_h = 50
    header_h = 55
    title_h = 70
    height = title_h + header_h + n_rows * row_h + 40

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    # Title
    tf = _font(24, bold=True)
    tw, _ = _text_size(draw, title, tf)
    draw.text(((width - tw) // 2, 18), title,
              fill=_SCHEME["text"], font=tf)

    x0 = 40
    y0 = title_h

    # Header row
    hf = _font(18, bold=True)
    draw.rectangle([x0, y0, x0 + n_cols * col_w, y0 + header_h],
                   fill=_SCHEME["primary"])
    for c, h in enumerate(headers):
        hx = x0 + c * col_w + col_w // 2
        hw, _ = _text_size(draw, h, hf)
        draw.text((hx - hw // 2, y0 + 15), h,
                  fill=(255, 255, 255), font=hf)

    # Data rows
    cf = _font(16)
    for r, row in enumerate(rows):
        ry = y0 + header_h + r * row_h
        bg = _SCHEME["bg"] if r % 2 == 0 else (255, 255, 255)
        draw.rectangle([x0, ry, x0 + n_cols * col_w, ry + row_h], fill=bg)

        for c, cell in enumerate(row):
            cell_x = x0 + c * col_w + col_w // 2
            cw, _ = _text_size(draw, str(cell), cf)

            # Highlight column
            if highlight_col is not None and c == highlight_col:
                draw.rectangle(
                    [x0 + c * col_w, ry,
                     x0 + (c + 1) * col_w, ry + row_h],
                    fill=_SCHEME["highlight"])

            draw.text((cell_x - cw // 2, ry + 14), str(cell),
                      fill=_SCHEME["text"], font=cf)

    # Border
    draw.rectangle(
        [x0, y0, x0 + n_cols * col_w, y0 + header_h + n_rows * row_h],
        outline=_SCHEME["border"], width=1)

    # Column separators
    for c in range(1, n_cols):
        lx = x0 + c * col_w
        draw.line([(lx, y0), (lx, y0 + header_h + n_rows * row_h)],
                  fill=_SCHEME["border"], width=1)

    if output_path is None:
        output_path = "comparison_table.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)


# ── Process Flow Figure ───────────────────────────────────────────────────

def process_flow_figure(steps, title=None, output_path=None,
                        width=1600, height=400):
    """Generate a horizontal process flow diagram (boxes with arrows).

    Args:
        steps: List of step labels (strings).
        title: Optional figure title.
        output_path: Output file path.
        width: Image width.
        height: Image height.

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    n = len(steps)
    if n == 0:
        return None

    margin = 60
    arrow_w = 40
    usable = width - 2 * margin - (n - 1) * arrow_w
    box_w = usable // n
    box_h = 70
    cy = height // 2
    if title:
        cy += 20

    # Title
    if title:
        tf = _font(24, bold=True)
        tw, _ = _text_size(draw, title, tf)
        draw.text(((width - tw) // 2, 15), title,
                  fill=_SCHEME["text"], font=tf)

    colors = _SCHEME["node_colors"]
    sf = _font(16, bold=True)

    for i, step in enumerate(steps):
        x = margin + i * (box_w + arrow_w)
        y = cy - box_h // 2
        color = colors[i % len(colors)]

        # Rounded box
        r = 12
        draw.rounded_rectangle([x, y, x + box_w, y + box_h],
                               radius=r, fill=color)

        # Label
        lines = _wrap(step, sf, box_w - 20, draw)
        total_h = len(lines) * 22
        ly = cy - total_h // 2
        for line in lines:
            lw, _ = _text_size(draw, line, sf)
            draw.text((x + (box_w - lw) // 2, ly), line,
                      fill=(255, 255, 255), font=sf)
            ly += 22

        # Arrow to next box
        if i < n - 1:
            ax = x + box_w + 5
            ay = cy
            # Arrow shaft
            draw.line([(ax, ay), (ax + arrow_w - 15, ay)],
                      fill=_SCHEME["text_light"], width=3)
            # Arrowhead
            ah = 10
            tip_x = ax + arrow_w - 10
            draw.polygon([(tip_x, ay - ah), (tip_x + 15, ay),
                          (tip_x, ay + ah)],
                         fill=_SCHEME["text_light"])

    if output_path is None:
        output_path = "process_flow.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)


# ── Timeline Figure ───────────────────────────────────────────────────────

def timeline_figure(events, title=None, output_path=None,
                    width=1600, height=500):
    """Generate a horizontal timeline figure.

    Args:
        events: List of (year_or_label, description) tuples.
        title: Optional title.
        output_path: Output file path.
        width: Image width.
        height: Image height.

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    n = len(events)
    if n == 0:
        return None

    margin = 100
    title_offset = 50 if title else 0

    if title:
        tf = _font(24, bold=True)
        tw, _ = _text_size(draw, title, tf)
        draw.text(((width - tw) // 2, 15), title,
                  fill=_SCHEME["text"], font=tf)

    # Timeline axis
    axis_y = height // 2 + title_offset // 2 - 20
    draw.line([(margin, axis_y), (width - margin, axis_y)],
              fill=_SCHEME["primary"], width=3)

    # Events
    spacing = (width - 2 * margin) / max(n - 1, 1)
    label_font = _font(16, bold=True)
    desc_font = _font(14)
    colors = _SCHEME["node_colors"]

    for i, (label, desc) in enumerate(events):
        x = int(margin + i * spacing)
        color = colors[i % len(colors)]

        # Dot
        dot_r = 10
        draw.ellipse([x - dot_r, axis_y - dot_r,
                      x + dot_r, axis_y + dot_r], fill=color)

        # Alternate above/below
        above = (i % 2 == 0)

        # Label (year)
        lw, lh = _text_size(draw, str(label), label_font)
        if above:
            draw.line([(x, axis_y - dot_r), (x, axis_y - 50)],
                      fill=_SCHEME["border"], width=1)
            draw.text((x - lw // 2, axis_y - 75), str(label),
                      fill=color, font=label_font)
            # Description
            d_lines = _wrap(desc, desc_font, int(spacing * 0.85), draw)
            dy = axis_y - 100
            for dl in reversed(d_lines):
                dw, dh = _text_size(draw, dl, desc_font)
                draw.text((x - dw // 2, dy - dh), dl,
                          fill=_SCHEME["text_light"], font=desc_font)
                dy -= dh + 3
        else:
            draw.line([(x, axis_y + dot_r), (x, axis_y + 50)],
                      fill=_SCHEME["border"], width=1)
            draw.text((x - lw // 2, axis_y + 55), str(label),
                      fill=color, font=label_font)
            d_lines = _wrap(desc, desc_font, int(spacing * 0.85), draw)
            dy = axis_y + 80
            for dl in d_lines:
                dw, _ = _text_size(draw, dl, desc_font)
                draw.text((x - dw // 2, dy), dl,
                          fill=_SCHEME["text_light"], font=desc_font)
                dy += 20

    if output_path is None:
        output_path = "timeline.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)


# ── Equation Highlight Figure ─────────────────────────────────────────────

def equation_highlight_figure(equation_text, label, description="",
                              output_path=None, width=1200, height=300):
    """Generate a highlighted equation card (for key equations in a chapter).

    Renders the equation as text in a styled box. For proper LaTeX rendering,
    use matplotlib's mathtext instead.

    Args:
        equation_text: The equation string (plain text or simple notation).
        label: Equation label (e.g. 'van der Waals equation').
        description: Brief description below the equation.
        output_path: Output file path.
        width: Image width.
        height: Image height.

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    # Background card
    pad = 30
    draw.rounded_rectangle(
        [pad, pad, width - pad, height - pad],
        radius=16, fill=_SCHEME["highlight"],
        outline=_SCHEME["border"], width=1)

    # Left accent bar
    draw.rectangle([pad, pad + 16, pad + 6, height - pad - 16],
                   fill=_SCHEME["secondary"])

    # Label
    lf = _font(18, bold=True)
    draw.text((pad + 30, pad + 20), label,
              fill=_SCHEME["primary"], font=lf)

    # Equation
    ef = _font(28, bold=True, serif=True)
    eq_y = pad + 60
    ew, _ = _text_size(draw, equation_text, ef)
    draw.text(((width - ew) // 2, eq_y), equation_text,
              fill=_SCHEME["text"], font=ef)

    # Description
    if description:
        df = _font(16)
        d_lines = _wrap(description, df, width - 2 * pad - 80, draw)
        dy = eq_y + 50
        for line in d_lines:
            dw, _ = _text_size(draw, line, df)
            draw.text(((width - dw) // 2, dy), line,
                      fill=_SCHEME["text_light"], font=df)
            dy += 22

    if output_path is None:
        output_path = "equation_highlight.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)


# ── LaTeX Equation Figure (matplotlib) ────────────────────────────────────

def latex_equation_figure(latex, label="", output_path=None,
                          width_inches=7.0, height_inches=2.0):
    """Render a LaTeX equation as a publication-quality figure using matplotlib.

    Args:
        latex: LaTeX equation string (e.g. r'P = \\frac{RT}{v-b}').
        label: Optional label below the equation.
        output_path: Output file path.
        width_inches: Figure width.
        height_inches: Figure height.

    Returns:
        Path to the generated image.
    """
    if not _HAS_MPL:
        raise ImportError("matplotlib required.")

    fig, ax = plt.subplots(figsize=(width_inches, height_inches))
    ax.set_axis_off()

    # Render equation
    ax.text(0.5, 0.55, r"${}$".format(latex),
            transform=ax.transAxes, fontsize=22,
            ha="center", va="center",
            fontfamily="serif")

    if label:
        ax.text(0.5, 0.12, label,
                transform=ax.transAxes, fontsize=12,
                ha="center", va="center",
                color="#555555", fontstyle="italic")

    # Light background box
    fig.patch.set_facecolor("#FFFDF5")
    fig.patch.set_edgecolor("#CCCCCC")
    fig.patch.set_linewidth(1)

    if output_path is None:
        output_path = "latex_equation.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(str(output_path), dpi=300, bbox_inches="tight",
                pad_inches=0.3, facecolor=fig.get_facecolor())
    plt.close(fig)
    return str(output_path)


# ── Section Divider ───────────────────────────────────────────────────────

def section_divider(text="", style="line", output_path=None,
                    width=1200, height=80):
    """Generate a section divider / ornamental break image.

    Args:
        text: Optional text in the divider (e.g. section number).
        style: 'line', 'dots', or 'ornament'.
        output_path: Output file path.
        width: Image width.
        height: Image height.

    Returns:
        Path to the generated image.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required.")

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)
    cy = height // 2

    if style == "dots":
        for i in range(5):
            x = width // 2 + (i - 2) * 30
            draw.ellipse([x - 4, cy - 4, x + 4, cy + 4],
                         fill=_SCHEME["text_light"])
    elif style == "ornament":
        # Diamond ornament
        cx = width // 2
        ds = 8
        draw.polygon([(cx, cy - ds), (cx + ds, cy),
                      (cx, cy + ds), (cx - ds, cy)],
                     fill=_SCHEME["primary"])
        draw.line([(cx - 120, cy), (cx - ds - 8, cy)],
                  fill=_SCHEME["border"], width=1)
        draw.line([(cx + ds + 8, cy), (cx + 120, cy)],
                  fill=_SCHEME["border"], width=1)
    else:  # line
        margin = 200
        draw.line([(margin, cy), (width - margin, cy)],
                  fill=_SCHEME["border"], width=1)

    if text:
        tf = _font(16, bold=True)
        tw, th = _text_size(draw, text, tf)
        # White background behind text
        draw.rectangle([(width - tw) // 2 - 10, cy - th // 2 - 4,
                        (width + tw) // 2 + 10, cy + th // 2 + 4],
                       fill=(255, 255, 255))
        draw.text(((width - tw) // 2, cy - th // 2), text,
                  fill=_SCHEME["primary"], font=tf)

    if output_path is None:
        output_path = "section_divider.png"
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(300, 300))
    return str(output_path)
