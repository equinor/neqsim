"""
Graphical Abstract Composer — Generates a composite graphical abstract image
from a key figure and result highlights, suitable for journal submission.

Usage::

    from tools.graphical_abstract import compose_graphical_abstract

    path = compose_graphical_abstract(
        "papers/my_paper/",
        key_figure="figures/convergence_map.png",
        highlights=["2.3x faster convergence", "99.7% success rate"],
    )
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Optional

try:
    from PIL import Image, ImageDraw, ImageFont
    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    _HAS_MPL = True
except ImportError:
    _HAS_MPL = False


# Default layout dimensions (pixels)
_DEFAULT_WIDTH = 1600
_DEFAULT_HEIGHT = 900
_MARGIN = 40
_HIGHLIGHT_FONT_SIZE = 28
_TITLE_FONT_SIZE = 22
_BG_COLOR = (255, 255, 255)
_TEXT_COLOR = (30, 30, 30)
_ACCENT_COLOR = (0, 102, 179)  # Blue accent
_HIGHLIGHT_BG = (240, 248, 255)  # Light blue background


def _get_font(size=24):
    """Try to load a clean sans-serif font, falling back to default.

    Args:
        size: Font size in points.

    Returns:
        PIL ImageFont object.
    """
    font_candidates = [
        "arial.ttf", "Arial.ttf",
        "DejaVuSans.ttf",
        "LiberationSans-Regular.ttf",
        "Helvetica.ttf",
    ]
    for fname in font_candidates:
        try:
            return ImageFont.truetype(fname, size)
        except (IOError, OSError):
            continue
    return ImageFont.load_default()


def _auto_select_figure(paper_dir):
    """Auto-select the best figure for graphical abstract.

    Prefers convergence maps, parity plots, or the first figure.

    Args:
        paper_dir: Path to paper directory.

    Returns:
        Path to selected figure, or None.
    """
    fig_dir = Path(paper_dir) / "figures"
    if not fig_dir.exists():
        return None

    candidates = list(fig_dir.glob("*.png")) + list(fig_dir.glob("*.jpg"))
    if not candidates:
        return None

    # Prefer certain figure types
    preference_order = [
        "convergence", "parity", "comparison", "result", "benchmark",
        "performance", "overview", "summary",
    ]
    for keyword in preference_order:
        for c in candidates:
            if keyword in c.stem.lower():
                return c

    return candidates[0]


def _auto_extract_highlights(paper_dir, max_highlights=4):
    """Extract highlight phrases from results.json or plan.json.

    Args:
        paper_dir: Path to paper directory.
        max_highlights: Maximum number of highlights.

    Returns:
        List of highlight strings.
    """
    highlights = []
    paper_dir = Path(paper_dir)

    # Try results.json key_results
    results_file = paper_dir / "results.json"
    if results_file.exists():
        with open(results_file) as f:
            data = json.load(f)
        key_results = data.get("key_results", {})
        for key, val in list(key_results.items())[:max_highlights]:
            label = key.replace("_", " ").title()
            highlights.append(f"{label}: {val}")

    # Try plan.json highlights
    plan_file = paper_dir / "plan.json"
    if plan_file.exists() and len(highlights) < max_highlights:
        with open(plan_file) as f:
            plan = json.load(f)
        for h in plan.get("highlights", []):
            if isinstance(h, str):
                highlights.append(h.lstrip("- "))
            if len(highlights) >= max_highlights:
                break

    # Try paper.md highlights section
    paper_file = paper_dir / "paper.md"
    if paper_file.exists() and len(highlights) < max_highlights:
        text = paper_file.read_text(encoding="utf-8", errors="replace")
        hl_match = re.search(
            r'## Highlights?\s*\n((?:- .+\n?)+)', text, re.IGNORECASE)
        if hl_match:
            for line in hl_match.group(1).strip().split("\n"):
                line = line.strip().lstrip("- ")
                if line:
                    highlights.append(line)
                if len(highlights) >= max_highlights:
                    break

    return highlights[:max_highlights]


def compose_graphical_abstract(paper_dir, key_figure=None, highlights=None,
                                title=None, output_name="graphical_abstract.png",
                                width=_DEFAULT_WIDTH, height=_DEFAULT_HEIGHT):
    """Compose a graphical abstract image from a key figure and highlights.

    Layout: Left 60% = key figure, Right 40% = title + highlight bullet points.

    Args:
        paper_dir: Path to the paper directory.
        key_figure: Path to the key figure (auto-detected if None).
        highlights: List of highlight strings (auto-extracted if None).
        title: Paper title (read from plan.json if None).
        output_name: Output filename.
        width: Canvas width in pixels.
        height: Canvas height in pixels.

    Returns:
        Path to the generated graphical abstract, or error dict.
    """
    if not _HAS_PIL:
        return {"error": "Pillow required. Install: pip install Pillow"}

    paper_dir = Path(paper_dir)

    # Resolve inputs
    if key_figure is None:
        key_figure = _auto_select_figure(paper_dir)
    else:
        key_figure = Path(key_figure)
        if not key_figure.is_absolute():
            key_figure = paper_dir / key_figure

    if highlights is None:
        highlights = _auto_extract_highlights(paper_dir)

    if title is None:
        plan_file = paper_dir / "plan.json"
        if plan_file.exists():
            with open(plan_file) as f:
                plan = json.load(f)
            title = plan.get("title", "")

    # Create canvas
    canvas = Image.new("RGB", (width, height), _BG_COLOR)
    draw = ImageDraw.Draw(canvas)

    # Layout: figure on left (60%), text on right (40%)
    fig_width = int(width * 0.58)
    text_x = fig_width + _MARGIN

    # Draw figure
    if key_figure and Path(key_figure).exists():
        fig_img = Image.open(key_figure)
        # Resize to fit left panel
        fig_area_w = fig_width - 2 * _MARGIN
        fig_area_h = height - 2 * _MARGIN
        fig_img.thumbnail((fig_area_w, fig_area_h), Image.LANCZOS)
        # Center vertically
        fig_y = (height - fig_img.height) // 2
        canvas.paste(fig_img, (_MARGIN, fig_y))
    else:
        # Placeholder
        draw.rectangle(
            [_MARGIN, _MARGIN, fig_width - _MARGIN, height - _MARGIN],
            outline=(200, 200, 200), width=2)
        placeholder_font = _get_font(20)
        draw.text((fig_width // 2 - 60, height // 2),
                  "[Key Figure]", fill=(180, 180, 180), font=placeholder_font)

    # Draw separator line
    draw.line([(fig_width, _MARGIN), (fig_width, height - _MARGIN)],
              fill=_ACCENT_COLOR, width=3)

    # Draw title
    y_cursor = _MARGIN + 10
    if title:
        title_font = _get_font(_TITLE_FONT_SIZE)
        # Word wrap title
        max_text_w = width - text_x - _MARGIN
        words = title.split()
        lines = []
        current_line = ""
        for word in words:
            test_line = current_line + " " + word if current_line else word
            bbox = draw.textbbox((0, 0), test_line, font=title_font)
            if bbox[2] - bbox[0] > max_text_w:
                if current_line:
                    lines.append(current_line)
                current_line = word
            else:
                current_line = test_line
        if current_line:
            lines.append(current_line)

        for line in lines[:3]:  # Max 3 title lines
            draw.text((text_x, y_cursor), line, fill=_TEXT_COLOR, font=title_font)
            y_cursor += _TITLE_FONT_SIZE + 8
        y_cursor += 20

    # Draw accent bar
    draw.rectangle(
        [text_x, y_cursor, text_x + 60, y_cursor + 4],
        fill=_ACCENT_COLOR)
    y_cursor += 20

    # Draw highlights
    hl_font = _get_font(_HIGHLIGHT_FONT_SIZE)
    bullet_font = _get_font(_HIGHLIGHT_FONT_SIZE + 4)
    max_text_w = width - text_x - _MARGIN - 30

    for i, hl in enumerate(highlights):
        if y_cursor > height - _MARGIN - 60:
            break

        # Highlight background
        bg_h = _HIGHLIGHT_FONT_SIZE + 24
        draw.rounded_rectangle(
            [text_x - 5, y_cursor, width - _MARGIN, y_cursor + bg_h],
            radius=8, fill=_HIGHLIGHT_BG)

        # Bullet
        draw.text((text_x + 5, y_cursor + 4), "●", fill=_ACCENT_COLOR, font=bullet_font)

        # Text (truncate if too long)
        bbox = draw.textbbox((0, 0), hl, font=hl_font)
        text_w = bbox[2] - bbox[0]
        if text_w > max_text_w:
            while text_w > max_text_w and len(hl) > 10:
                hl = hl[:-4] + "..."
                bbox = draw.textbbox((0, 0), hl, font=hl_font)
                text_w = bbox[2] - bbox[0]

        draw.text((text_x + 35, y_cursor + 8), hl, fill=_TEXT_COLOR, font=hl_font)
        y_cursor += bg_h + 12

    # Save
    output_dir = paper_dir / "figures"
    output_dir.mkdir(exist_ok=True)
    output_path = output_dir / output_name
    canvas.save(str(output_path), dpi=(300, 300))

    return str(output_path)


def print_graphical_abstract_report(result):
    """Print result of graphical abstract generation.

    Args:
        result: Path string or error dict.
    """
    if isinstance(result, dict) and "error" in result:
        print(f"Error: {result['error']}")
        return

    print(f"  Graphical abstract saved: {result}")
    print("  Review and adjust in an image editor if needed.")
    print()
