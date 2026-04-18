"""
Book Cover Generator — Programmatic professional book covers using Pillow.

No API keys, no GPU, no subscriptions. Creates publication-quality covers
from title, author, and optional style parameters. Includes geometric
patterns, gradient backgrounds, and typography composition.

Usage::

    from tools.book_cover_generator import generate_cover

    path = generate_cover(
        title="The Cubic Plus Association Equation of State",
        subtitle="Theory, Implementation, and Applications",
        authors=["Even Solbraa"],
        style="scientific",            # scientific | modern | classic | minimal
        accent_color="#1a5276",
        output_path="cover.png",
    )

Styles:
    scientific — geometric mesh + gradient, clean typography
    modern     — bold color blocks, large sans-serif title
    classic    — dark background, serif typography, gold accent
    minimal    — white/light background, thin rules, understated
"""

import math
import random
from pathlib import Path
from typing import List, Optional, Tuple

try:
    from PIL import Image, ImageDraw, ImageFont, ImageFilter
    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False


# ── Defaults ──────────────────────────────────────────────────────────────

_DEFAULT_WIDTH = 1600   # 6.3 in at 254 DPI (standard trade paperback)
_DEFAULT_HEIGHT = 2400  # 9.4 in at 254 DPI
_DPI = 300
_MARGIN = 120

# Color palettes per style
_PALETTES = {
    "scientific": {
        "bg_top": (20, 60, 100),
        "bg_bottom": (8, 25, 50),
        "accent": (0, 160, 220),
        "title": (255, 255, 255),
        "subtitle": (180, 210, 235),
        "author": (200, 220, 240),
        "mesh": (40, 100, 160),
    },
    "modern": {
        "bg_top": (230, 240, 250),
        "bg_bottom": (245, 248, 252),
        "accent": (0, 102, 179),
        "title": (20, 40, 70),
        "subtitle": (60, 80, 110),
        "author": (80, 100, 130),
        "mesh": (200, 215, 235),
    },
    "classic": {
        "bg_top": (25, 25, 35),
        "bg_bottom": (15, 15, 20),
        "accent": (200, 170, 80),
        "title": (240, 235, 220),
        "subtitle": (200, 195, 180),
        "author": (180, 175, 160),
        "mesh": (50, 50, 65),
    },
    "minimal": {
        "bg_top": (252, 252, 252),
        "bg_bottom": (240, 240, 240),
        "accent": (40, 40, 40),
        "title": (20, 20, 20),
        "subtitle": (80, 80, 80),
        "author": (100, 100, 100),
        "mesh": (225, 225, 225),
    },
}


# ── Font helpers ──────────────────────────────────────────────────────────

def _load_font(family="serif", size=48, bold=False):
    """Load a font with fallbacks.

    Args:
        family: 'serif' or 'sans'.
        size: Font size in points.
        bold: Whether to use bold weight.

    Returns:
        PIL ImageFont.
    """
    if family == "serif":
        candidates = [
            "timesbd.ttf" if bold else "times.ttf",
            "Times New Roman Bold.ttf" if bold else "Times New Roman.ttf",
            "Georgia Bold.ttf" if bold else "Georgia.ttf",
            "DejaVuSerif-Bold.ttf" if bold else "DejaVuSerif.ttf",
        ]
    else:
        candidates = [
            "arialbd.ttf" if bold else "arial.ttf",
            "Arial Bold.ttf" if bold else "Arial.ttf",
            "Helvetica Bold.ttf" if bold else "Helvetica.ttf",
            "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf",
            "LiberationSans-Bold.ttf" if bold else "LiberationSans-Regular.ttf",
        ]
    for fname in candidates:
        try:
            return ImageFont.truetype(fname, size)
        except (IOError, OSError):
            continue
    return ImageFont.load_default()


# ── Drawing primitives ────────────────────────────────────────────────────

def _draw_gradient(draw, width, height, color_top, color_bottom):
    """Fill with a vertical linear gradient.

    Args:
        draw: PIL ImageDraw instance.
        width: Image width.
        height: Image height.
        color_top: RGB tuple for top.
        color_bottom: RGB tuple for bottom.
    """
    for y in range(height):
        t = y / max(height - 1, 1)
        r = int(color_top[0] + (color_bottom[0] - color_top[0]) * t)
        g = int(color_top[1] + (color_bottom[1] - color_top[1]) * t)
        b = int(color_top[2] + (color_bottom[2] - color_top[2]) * t)
        draw.line([(0, y), (width, y)], fill=(r, g, b))


def _draw_mesh_pattern(draw, width, height, color, density=12, seed=42):
    """Draw a subtle geometric mesh/node pattern.

    Creates connected dots that suggest molecular or network structure —
    appropriate for scientific books.

    Args:
        draw: PIL ImageDraw instance.
        width: Image width.
        height: Image height.
        color: RGB tuple for mesh lines/dots.
        density: Grid density (lower = denser).
        seed: Random seed for reproducibility.
    """
    rng = random.Random(seed)
    step = max(width, height) // density

    # Generate nodes with slight random offset
    nodes = []
    for gx in range(-1, density + 2):
        for gy in range(-1, density + 2):
            x = gx * step + rng.randint(-step // 4, step // 4)
            y = gy * step + rng.randint(-step // 4, step // 4)
            nodes.append((x, y))

    # Draw connections (short edges only)
    max_dist = step * 1.6
    for i, (x1, y1) in enumerate(nodes):
        for j, (x2, y2) in enumerate(nodes):
            if j <= i:
                continue
            dist = math.hypot(x2 - x1, y2 - y1)
            if dist < max_dist:
                alpha = max(0, min(255, int(80 * (1 - dist / max_dist))))
                line_color = (*color, alpha)
                draw.line([(x1, y1), (x2, y2)], fill=line_color, width=1)

    # Draw nodes
    for x, y in nodes:
        r = rng.randint(2, 5)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(*color, 120))


def _draw_circles_pattern(draw, width, height, color, count=8, seed=42):
    """Draw overlapping translucent circles for a modern look.

    Args:
        draw: PIL ImageDraw instance.
        width: Image width.
        height: Image height.
        color: RGB tuple base color.
        count: Number of circles.
        seed: Random seed.
    """
    rng = random.Random(seed)
    for _ in range(count):
        cx = rng.randint(0, width)
        cy = rng.randint(0, height)
        radius = rng.randint(height // 8, height // 3)
        alpha = rng.randint(15, 40)
        draw.ellipse(
            [cx - radius, cy - radius, cx + radius, cy + radius],
            fill=(*color, alpha), outline=None)


def _draw_horizontal_rules(draw, width, y_positions, color, thickness=2):
    """Draw thin horizontal accent lines.

    Args:
        draw: PIL ImageDraw instance.
        width: Image width.
        y_positions: List of y coordinates.
        color: RGB tuple.
        thickness: Line width.
    """
    for y in y_positions:
        draw.line([(0, y), (width, y)], fill=color, width=thickness)


def _word_wrap(text, font, max_width, draw):
    """Break text into lines that fit within max_width.

    Args:
        text: Input string.
        font: PIL ImageFont.
        max_width: Maximum pixel width per line.
        draw: PIL ImageDraw for measurement.

    Returns:
        List of line strings.
    """
    words = text.split()
    lines = []
    current = ""
    for word in words:
        test = (current + " " + word).strip()
        bbox = draw.textbbox((0, 0), test, font=font)
        if bbox[2] - bbox[0] > max_width and current:
            lines.append(current)
            current = word
        else:
            current = test
    if current:
        lines.append(current)
    return lines


# ── Cover generators per style ────────────────────────────────────────────

def _render_scientific(canvas, draw, pal, title, subtitle, authors,
                       edition, publisher, width, height):
    """Render a scientific-style cover: gradient + mesh + clean type."""
    # Background gradient
    _draw_gradient(draw, width, height, pal["bg_top"], pal["bg_bottom"])

    # Mesh overlay (use RGBA layer for transparency)
    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ov_draw = ImageDraw.Draw(overlay)
    _draw_mesh_pattern(ov_draw, width, height, pal["mesh"], density=14)
    canvas.paste(Image.alpha_composite(
        canvas.convert("RGBA"), overlay).convert("RGB"))
    draw = ImageDraw.Draw(canvas)

    # Accent bar at top
    draw.rectangle([0, 0, width, 8], fill=pal["accent"])

    # Title
    title_font = _load_font("sans", 72, bold=True)
    max_w = width - 2 * _MARGIN
    lines = _word_wrap(title, title_font, max_w, draw)
    y = height // 4
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=title_font)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) // 2, y), line, fill=pal["title"], font=title_font)
        y += bbox[3] - bbox[1] + 16

    # Accent line under title
    y += 20
    bar_w = 200
    draw.rectangle([(width - bar_w) // 2, y, (width + bar_w) // 2, y + 4],
                   fill=pal["accent"])
    y += 40

    # Subtitle
    if subtitle:
        sub_font = _load_font("serif", 36)
        sub_lines = _word_wrap(subtitle, sub_font, max_w, draw)
        for line in sub_lines:
            bbox = draw.textbbox((0, 0), line, font=sub_font)
            lw = bbox[2] - bbox[0]
            draw.text(((width - lw) // 2, y), line,
                      fill=pal["subtitle"], font=sub_font)
            y += bbox[3] - bbox[1] + 10

    # Authors (bottom third)
    author_font = _load_font("sans", 32)
    auth_y = height - _MARGIN - len(authors) * 50 - 80
    for author in authors:
        bbox = draw.textbbox((0, 0), author, font=author_font)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) // 2, auth_y), author,
                  fill=pal["author"], font=author_font)
        auth_y += 50

    # Edition / publisher at very bottom
    if edition or publisher:
        small_font = _load_font("sans", 22)
        bottom_text = "  ·  ".join(
            x for x in [edition, publisher] if x)
        bbox = draw.textbbox((0, 0), bottom_text, font=small_font)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) // 2, height - _MARGIN + 10),
                  bottom_text, fill=pal["subtitle"], font=small_font)

    # Bottom accent bar
    draw.rectangle([0, height - 8, width, height], fill=pal["accent"])


def _render_modern(canvas, draw, pal, title, subtitle, authors,
                   edition, publisher, width, height):
    """Render a modern-style cover: color blocks + bold sans-serif."""
    # Light background
    _draw_gradient(draw, width, height, pal["bg_top"], pal["bg_bottom"])

    # Large accent block (top 40%)
    block_h = int(height * 0.42)
    draw.rectangle([0, 0, width, block_h], fill=pal["accent"])

    # Circles overlay on block
    overlay = Image.new("RGBA", (width, block_h), (0, 0, 0, 0))
    ov_draw = ImageDraw.Draw(overlay)
    _draw_circles_pattern(ov_draw, width, block_h,
                          (255, 255, 255), count=6)
    block_img = canvas.crop((0, 0, width, block_h)).convert("RGBA")
    blended = Image.alpha_composite(block_img, overlay).convert("RGB")
    canvas.paste(blended, (0, 0))
    draw = ImageDraw.Draw(canvas)

    # Title on accent block
    title_font = _load_font("sans", 68, bold=True)
    max_w = width - 2 * _MARGIN
    lines = _word_wrap(title, title_font, max_w, draw)
    y = _MARGIN + 30
    for line in lines:
        draw.text((_MARGIN, y), line, fill=(255, 255, 255), font=title_font)
        bbox = draw.textbbox((0, 0), line, font=title_font)
        y += bbox[3] - bbox[1] + 14

    # Subtitle below block
    y = block_h + 60
    if subtitle:
        sub_font = _load_font("serif", 34)
        sub_lines = _word_wrap(subtitle, sub_font, max_w, draw)
        for line in sub_lines:
            draw.text((_MARGIN, y), line, fill=pal["subtitle"], font=sub_font)
            bbox = draw.textbbox((0, 0), line, font=sub_font)
            y += bbox[3] - bbox[1] + 10

    # Authors
    author_font = _load_font("sans", 30)
    auth_y = height - _MARGIN - len(authors) * 48 - 60
    for author in authors:
        draw.text((_MARGIN, auth_y), author,
                  fill=pal["author"], font=author_font)
        auth_y += 48

    # Edition / publisher
    if edition or publisher:
        small_font = _load_font("sans", 22)
        bottom_text = "  |  ".join(x for x in [edition, publisher] if x)
        draw.text((_MARGIN, height - _MARGIN + 10),
                  bottom_text, fill=pal["author"], font=small_font)


def _render_classic(canvas, draw, pal, title, subtitle, authors,
                    edition, publisher, width, height):
    """Render a classic-style cover: dark background, serif, gold accent."""
    _draw_gradient(draw, width, height, pal["bg_top"], pal["bg_bottom"])

    # Double rules
    _draw_horizontal_rules(draw, width, [_MARGIN, _MARGIN + 6],
                           pal["accent"], thickness=2)
    _draw_horizontal_rules(draw, width,
                           [height - _MARGIN, height - _MARGIN - 6],
                           pal["accent"], thickness=2)

    # Title
    title_font = _load_font("serif", 64, bold=True)
    max_w = width - 2 * _MARGIN - 40
    lines = _word_wrap(title, title_font, max_w, draw)
    y = height // 5
    for line in lines:
        bbox = draw.textbbox((0, 0), line, font=title_font)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) // 2, y), line,
                  fill=pal["title"], font=title_font)
        y += bbox[3] - bbox[1] + 16

    # Ornamental divider
    y += 30
    div_w = 300
    draw.line([((width - div_w) // 2, y), ((width + div_w) // 2, y)],
              fill=pal["accent"], width=2)
    # Small diamond in center
    cx, cy = width // 2, y
    ds = 8
    draw.polygon([(cx, cy - ds), (cx + ds, cy),
                  (cx, cy + ds), (cx - ds, cy)], fill=pal["accent"])
    y += 40

    # Subtitle
    if subtitle:
        sub_font = _load_font("serif", 32)
        sub_lines = _word_wrap(subtitle, sub_font, max_w, draw)
        for line in sub_lines:
            bbox = draw.textbbox((0, 0), line, font=sub_font)
            lw = bbox[2] - bbox[0]
            draw.text(((width - lw) // 2, y), line,
                      fill=pal["subtitle"], font=sub_font)
            y += bbox[3] - bbox[1] + 10

    # Authors
    author_font = _load_font("serif", 30)
    auth_y = height - _MARGIN - len(authors) * 50 - 100
    for author in authors:
        bbox = draw.textbbox((0, 0), author, font=author_font)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) // 2, auth_y), author,
                  fill=pal["author"], font=author_font)
        auth_y += 50

    # Publisher
    if publisher:
        small_font = _load_font("serif", 22)
        bbox = draw.textbbox((0, 0), publisher, font=small_font)
        lw = bbox[2] - bbox[0]
        draw.text(((width - lw) // 2, height - _MARGIN - 40),
                  publisher, fill=pal["accent"], font=small_font)


def _render_minimal(canvas, draw, pal, title, subtitle, authors,
                    edition, publisher, width, height):
    """Render a minimal-style cover: light background, thin rules."""
    canvas.paste(pal["bg_top"], (0, 0, width, height))
    draw = ImageDraw.Draw(canvas)

    # Single top rule
    draw.line([(_MARGIN, _MARGIN), (width - _MARGIN, _MARGIN)],
              fill=pal["accent"], width=1)

    # Title — large, high on page
    title_font = _load_font("serif", 60, bold=True)
    max_w = width - 2 * _MARGIN - 40
    lines = _word_wrap(title, title_font, max_w, draw)
    y = _MARGIN + 80
    for line in lines:
        draw.text((_MARGIN + 20, y), line,
                  fill=pal["title"], font=title_font)
        bbox = draw.textbbox((0, 0), line, font=title_font)
        y += bbox[3] - bbox[1] + 14

    y += 30
    # Thin rule
    draw.line([(_MARGIN + 20, y), (_MARGIN + 220, y)],
              fill=pal["accent"], width=1)
    y += 30

    # Subtitle
    if subtitle:
        sub_font = _load_font("serif", 28)
        sub_lines = _word_wrap(subtitle, sub_font, max_w, draw)
        for line in sub_lines:
            draw.text((_MARGIN + 20, y), line,
                      fill=pal["subtitle"], font=sub_font)
            bbox = draw.textbbox((0, 0), line, font=sub_font)
            y += bbox[3] - bbox[1] + 8

    # Authors — bottom
    author_font = _load_font("serif", 28)
    auth_y = height - _MARGIN - len(authors) * 45 - 60
    for author in authors:
        draw.text((_MARGIN + 20, auth_y), author,
                  fill=pal["author"], font=author_font)
        auth_y += 45

    # Bottom rule
    draw.line([(_MARGIN, height - _MARGIN),
               (width - _MARGIN, height - _MARGIN)],
              fill=pal["accent"], width=1)

    # Publisher right-aligned at bottom
    if publisher:
        small_font = _load_font("serif", 20)
        bbox = draw.textbbox((0, 0), publisher, font=small_font)
        lw = bbox[2] - bbox[0]
        draw.text((width - _MARGIN - lw, height - _MARGIN + 15),
                  publisher, fill=pal["author"], font=small_font)


# ── Public API ────────────────────────────────────────────────────────────

_STYLE_RENDERERS = {
    "scientific": _render_scientific,
    "modern": _render_modern,
    "classic": _render_classic,
    "minimal": _render_minimal,
}


def generate_cover(title, subtitle="", authors=None, style="scientific",
                   accent_color=None, edition="", publisher="",
                   width=_DEFAULT_WIDTH, height=_DEFAULT_HEIGHT,
                   output_path=None, seed=42):
    """Generate a professional book cover image.

    Args:
        title: Book title (required).
        subtitle: Subtitle (optional).
        authors: List of author names (default: []).
        style: Visual style — 'scientific', 'modern', 'classic', or 'minimal'.
        accent_color: Override accent color as '#RRGGBB' hex string.
        edition: Edition text (e.g. '2nd Edition').
        publisher: Publisher name.
        width: Cover width in pixels (default: 1600).
        height: Cover height in pixels (default: 2400).
        output_path: Output file path (default: 'cover_{style}.png').
        seed: Random seed for reproducible patterns.

    Returns:
        Path to the generated cover image.

    Raises:
        ImportError: If Pillow is not installed.
        ValueError: If style is not recognized.
    """
    if not _HAS_PIL:
        raise ImportError("Pillow required. Install: pip install Pillow")

    if authors is None:
        authors = []

    if style not in _PALETTES:
        raise ValueError(
            "Unknown style '{}'. Choose from: {}".format(
                style, ", ".join(_PALETTES.keys())))

    pal = dict(_PALETTES[style])

    # Override accent color
    if accent_color:
        hex_clean = accent_color.lstrip("#")
        rgb = tuple(int(hex_clean[i:i+2], 16) for i in (0, 2, 4))
        pal["accent"] = rgb

    canvas = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)

    renderer = _STYLE_RENDERERS[style]
    renderer(canvas, draw, pal, title, subtitle, authors,
             edition, publisher, width, height)

    if output_path is None:
        output_path = "cover_{}.png".format(style)
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(str(output_path), dpi=(_DPI, _DPI))
    return str(output_path)


def generate_all_styles(title, subtitle="", authors=None, output_dir="covers",
                        **kwargs):
    """Generate covers in all four styles for comparison.

    Args:
        title: Book title.
        subtitle: Subtitle.
        authors: List of author names.
        output_dir: Output directory.
        **kwargs: Additional arguments passed to generate_cover.

    Returns:
        Dict mapping style name to output path.
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    results = {}
    for style in _PALETTES:
        path = generate_cover(
            title=title, subtitle=subtitle, authors=authors,
            style=style,
            output_path=output_dir / "cover_{}.png".format(style),
            **kwargs)
        results[style] = path
    return results


def generate_cover_from_book_yaml(book_dir, style="scientific", output_dir=None,
                                  **kwargs):
    """Generate a cover by reading metadata from book.yaml.

    Args:
        book_dir: Path to the book directory containing book.yaml.
        style: Visual style.
        output_dir: Output directory (default: book_dir/submission/).
        **kwargs: Additional arguments passed to generate_cover.

    Returns:
        Path to the generated cover image.
    """
    import yaml

    book_dir = Path(book_dir)
    yaml_path = book_dir / "book.yaml"
    if not yaml_path.exists():
        raise FileNotFoundError("book.yaml not found in {}".format(book_dir))

    with open(yaml_path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    title = cfg.get("title", "Untitled")
    subtitle = cfg.get("subtitle", "")
    authors = [a.get("name", "") for a in cfg.get("authors", [])]
    edition = cfg.get("edition", "")
    publisher = cfg.get("publisher", "")

    if output_dir is None:
        output_dir = book_dir / "submission"

    return generate_cover(
        title=title, subtitle=subtitle, authors=authors,
        style=style, edition=edition, publisher=publisher,
        output_path=Path(output_dir) / "cover_{}.png".format(style),
        **kwargs)
