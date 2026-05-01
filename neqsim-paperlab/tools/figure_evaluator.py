"""
Figure Evaluator — Automated multi-layer evaluation of scientific figures.

Three evaluation layers:
  Layer 1: Technical validation (DPI, resolution, color, readability) — free, fast
  Layer 2: Structural analysis (OCR labels, chart type detection, element checks)
  Layer 3: LLM-powered critique (clarity, caption alignment, scientific quality)

Usage::

    from tools.figure_evaluator import evaluate_figure, evaluate_all_figures

    # Single figure
    report = evaluate_figure("figures/fig01.png",
                             caption="Phase envelope of CO2-N2 mixture",
                             context="This figure shows the PT diagram...")

    # All figures in a paper
    reports = evaluate_all_figures("papers/my_paper/",
                                  llm_provider="openai",
                                  model="gpt-4o")
"""

import base64
import io
import json
import os
import re
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    from PIL import Image, ImageStat
    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False

try:
    import numpy as np
    _HAS_NUMPY = True
except ImportError:
    _HAS_NUMPY = False

try:
    import pytesseract
    _HAS_TESSERACT = True
except ImportError:
    _HAS_TESSERACT = False

# ── LLM provider imports (optional) ─────────────────────────────────────
_HAS_LITELLM = False
_HAS_OPENAI = False
_HAS_ANTHROPIC = False

try:
    import litellm
    _HAS_LITELLM = True
except ImportError:
    pass

try:
    import openai as _openai_mod
    _HAS_OPENAI = True
except ImportError:
    pass

try:
    import anthropic as _anthropic_mod
    _HAS_ANTHROPIC = True
except ImportError:
    pass


# ── Quality metric imports (optional) ────────────────────────────────────
_HAS_PYIQA = False
try:
    import pyiqa
    _HAS_PYIQA = True
except ImportError:
    pass


# ── Data classes ─────────────────────────────────────────────────────────

@dataclass
class TechnicalCheck:
    """Result of a single technical check on a figure."""
    check: str
    passed: bool
    value: Any = None
    message: str = ""
    severity: str = "INFO"  # INFO, WARNING, FAIL


@dataclass
class StructuralAnalysis:
    """Structural elements detected in a figure."""
    detected_text: List[str] = field(default_factory=list)
    has_axis_labels: bool = False
    has_title: bool = False
    has_legend: bool = False
    has_units: bool = False
    has_grid: bool = False
    chart_type_guess: str = "unknown"
    element_count: int = 0
    ocr_confidence: float = 0.0


@dataclass
class LLMCritique:
    """LLM-generated figure critique."""
    provider: str = ""
    model: str = ""
    clarity_score: int = 0          # 1-5
    completeness_score: int = 0     # 1-5
    scientific_quality_score: int = 0  # 1-5
    caption_alignment_score: int = 0   # 1-5 (0 if no caption)
    accessibility_score: int = 0    # 1-5
    overall_score: float = 0.0      # weighted average
    strengths: List[str] = field(default_factory=list)
    issues: List[str] = field(default_factory=list)
    suggested_caption: str = ""
    suggested_improvements: List[str] = field(default_factory=list)
    context_narrative: str = ""     # how figure connects to the paper
    raw_response: str = ""


@dataclass
class FigureEvaluation:
    """Complete evaluation report for a single figure."""
    figure_path: str
    figure_name: str
    caption: str = ""
    technical: List[TechnicalCheck] = field(default_factory=list)
    structural: Optional[StructuralAnalysis] = None
    llm_critique: Optional[LLMCritique] = None
    overall_grade: str = "PENDING"  # A, B, C, D, F
    summary: str = ""

    def to_dict(self):
        d = asdict(self)
        return d


# ── Layer 1: Technical Validation ────────────────────────────────────────

def _check_technical(fig_path: Path) -> List[TechnicalCheck]:
    """Run technical checks on a figure file."""
    checks = []
    name = fig_path.name

    if not fig_path.exists():
        checks.append(TechnicalCheck("file_exists", False,
                                     message="File not found", severity="FAIL"))
        return checks

    # File size
    size_mb = fig_path.stat().st_size / (1024 * 1024)
    checks.append(TechnicalCheck(
        "file_size", size_mb < 20,
        value=round(size_mb, 2),
        message=f"File size: {size_mb:.2f} MB",
        severity="INFO" if size_mb < 20 else "WARNING"
    ))

    # Format
    ext = fig_path.suffix.lower().lstrip(".")
    good_formats = {"png", "tif", "tiff", "eps", "pdf", "svg"}
    checks.append(TechnicalCheck(
        "format", ext in good_formats,
        value=ext,
        message=f"Format: .{ext}",
        severity="INFO" if ext in good_formats else "WARNING"
    ))

    if not _HAS_PIL:
        checks.append(TechnicalCheck("pillow", False,
                                     message="Pillow not installed — skipping image checks",
                                     severity="WARNING"))
        return checks

    if ext not in {"png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp"}:
        return checks

    try:
        with Image.open(fig_path) as img:
            width, height = img.size

            # DPI
            dpi = img.info.get("dpi", (72, 72))
            x_dpi = dpi[0] if isinstance(dpi, (tuple, list)) else dpi
            checks.append(TechnicalCheck(
                "dpi", x_dpi >= 299,
                value=int(round(x_dpi)),
                message=f"DPI: {x_dpi:.0f} (need >=300)",
                severity="INFO" if x_dpi >= 299 else "FAIL"
            ))

            # Resolution
            checks.append(TechnicalCheck(
                "resolution", width >= 500,
                value=f"{width}x{height}",
                message=f"Resolution: {width}×{height}px",
                severity="INFO" if width >= 500 else "WARNING"
            ))

            # Color mode
            checks.append(TechnicalCheck(
                "color_mode", img.mode != "RGBA",
                value=img.mode,
                message=f"Color mode: {img.mode}",
                severity="INFO" if img.mode != "RGBA" else "WARNING"
            ))

            # Aspect ratio (warn if extreme)
            ratio = max(width, height) / max(min(width, height), 1)
            checks.append(TechnicalCheck(
                "aspect_ratio", ratio < 4.0,
                value=round(ratio, 2),
                message=f"Aspect ratio: {ratio:.2f}:1",
                severity="INFO" if ratio < 4.0 else "WARNING"
            ))

            # Contrast / brightness analysis
            if _HAS_NUMPY and img.mode in ("RGB", "L", "RGBA"):
                arr = np.array(img.convert("L"), dtype=float)
                mean_brightness = arr.mean()
                std_brightness = arr.std()
                checks.append(TechnicalCheck(
                    "contrast", std_brightness > 20,
                    value=round(std_brightness, 1),
                    message=f"Contrast (std): {std_brightness:.1f} (need >20)",
                    severity="INFO" if std_brightness > 20 else "WARNING"
                ))
                checks.append(TechnicalCheck(
                    "brightness", 30 < mean_brightness < 240,
                    value=round(mean_brightness, 1),
                    message=f"Mean brightness: {mean_brightness:.1f}",
                    severity="INFO" if 30 < mean_brightness < 240 else "WARNING"
                ))

            # White-space ratio (excessive borders)
            if _HAS_NUMPY and img.mode in ("RGB", "RGBA"):
                arr = np.array(img.convert("RGB"))
                white_mask = (arr > 250).all(axis=2)
                white_ratio = white_mask.sum() / white_mask.size
                checks.append(TechnicalCheck(
                    "whitespace", white_ratio < 0.5,
                    value=round(white_ratio * 100, 1),
                    message=f"White-space: {white_ratio*100:.1f}%",
                    severity="INFO" if white_ratio < 0.5 else "WARNING"
                ))

    except Exception as e:
        checks.append(TechnicalCheck("image_read", False,
                                     message=f"Could not read image: {e}",
                                     severity="FAIL"))

    return checks


# ── Layer 1b: No-Reference Quality Score (pyiqa) ────────────────────────

def _check_quality_score(fig_path: Path) -> Optional[TechnicalCheck]:
    """Compute a no-reference image quality score using pyiqa BRISQUE."""
    if not _HAS_PYIQA:
        return None
    try:
        import torch
        metric = pyiqa.create_metric("brisque", device="cpu")
        score = float(metric(str(fig_path)).item())
        # BRISQUE: lower is better (0-100 typical, <30 good)
        passed = score < 50
        return TechnicalCheck(
            "brisque_quality", passed,
            value=round(score, 2),
            message=f"BRISQUE quality score: {score:.2f} (lower=better, <30=good)",
            severity="INFO" if passed else "WARNING"
        )
    except Exception as e:
        return TechnicalCheck("brisque_quality", False,
                              message=f"BRISQUE failed: {e}", severity="INFO")


# ── Layer 2: Structural Analysis (OCR + heuristics) ─────────────────────

def _analyze_structure(fig_path: Path) -> StructuralAnalysis:
    """Detect structural elements in a figure using OCR and heuristics."""
    result = StructuralAnalysis()

    if not _HAS_PIL:
        return result

    ext = fig_path.suffix.lower().lstrip(".")
    if ext not in {"png", "jpg", "jpeg", "tif", "tiff", "bmp", "webp"}:
        return result

    try:
        img = Image.open(fig_path)
    except Exception:
        return result

    # OCR with Tesseract if available
    if _HAS_TESSERACT:
        try:
            ocr_data = pytesseract.image_to_data(img, output_type=pytesseract.Output.DICT)
            texts = [t.strip() for t in ocr_data["text"] if t.strip()]
            confs = [c for c, t in zip(ocr_data["conf"], ocr_data["text"])
                     if t.strip() and c > 0]
            result.detected_text = texts
            result.ocr_confidence = sum(confs) / len(confs) if confs else 0.0

            full_text = " ".join(texts).lower()

            # Detect axis labels (look for unit patterns)
            unit_patterns = [
                r'\b(bar|mpa|kpa|psi)\b', r'\b(°?[ck]|kelvin|celsius)\b',
                r'\b(kg|mol|g/l|mg)\b', r'\b(m/s|km/h|ft/s)\b',
                r'\b(s|ms|min|hr|hours?)\b', r'\b(j|kj|mj|kw|mw)\b',
                r'\b(mol%|wt%|vol%)\b', r'\b(m³|m3|l|ml)\b',
            ]
            result.has_units = any(re.search(p, full_text) for p in unit_patterns)

            # Detect axis label patterns (words near edges)
            label_words = ["temperature", "pressure", "time", "concentration",
                           "flow", "rate", "density", "viscosity", "composition",
                           "mole fraction", "mass", "volume", "enthalpy",
                           "entropy", "speed", "velocity", "depth", "distance"]
            result.has_axis_labels = any(w in full_text for w in label_words)

            # Detect legend
            result.has_legend = any(w in full_text for w in
                                    ["legend", "experimental", "calculated",
                                     "model", "simulation", "data", "reference",
                                     "neqsim", "hysys", "aspen"])

            # Detect title
            result.has_title = any(w in full_text for w in
                                   ["figure", "fig.", "fig "])

            # Detect grid (analyze pixel patterns — simple heuristic)
            result.has_grid = "grid" in full_text

            result.element_count = sum([
                result.has_axis_labels, result.has_units,
                result.has_legend, result.has_title, result.has_grid
            ])

        except Exception:
            pass  # OCR not available or failed

    # Chart type heuristics from image analysis
    if _HAS_NUMPY:
        try:
            arr = np.array(img.convert("RGB"))
            # Simple color histogram for chart type detection
            unique_colors = len(np.unique(arr.reshape(-1, 3), axis=0))
            if unique_colors < 50:
                result.chart_type_guess = "schematic/diagram"
            elif unique_colors < 500:
                result.chart_type_guess = "bar/line chart"
            else:
                result.chart_type_guess = "scatter/heatmap/photo"
        except Exception:
            pass

    img.close()
    return result


# ── Layer 3: LLM Critique ───────────────────────────────────────────────

_FIGURE_EVALUATION_PROMPT = """You are an expert scientific figure reviewer for a thermodynamics and process engineering textbook/paper.

Evaluate this figure and return a JSON object with these exact keys:

{{
  "clarity_score": <1-5>,
  "completeness_score": <1-5>,
  "scientific_quality_score": <1-5>,
  "caption_alignment_score": <1-5 or 0 if no caption provided>,
  "accessibility_score": <1-5>,
  "strengths": ["strength1", "strength2"],
  "issues": ["issue1", "issue2"],
  "suggested_caption": "A concise 1-2 sentence caption for this figure",
  "suggested_improvements": ["improvement1", "improvement2"],
  "context_narrative": "2-3 sentences explaining what this figure shows, how it connects to the broader topic, and what the reader should take away"
}}

Scoring rubric:
- clarity (1-5): Are axes labeled? Is text readable? Is the layout clean?
- completeness (1-5): Are all necessary elements present (legend, units, title)?
- scientific_quality (1-5): Does the data presentation follow best practices?
- caption_alignment (1-5): Does the figure match the provided caption? (0 if no caption)
- accessibility (1-5): Colorblind-safe? Sufficient contrast? Distinguishable markers?

{caption_section}
{context_section}

Return ONLY valid JSON, no markdown fences or explanation."""


def _encode_image_base64(fig_path: Path) -> str:
    """Read image and encode to base64 for LLM vision API."""
    with open(fig_path, "rb") as f:
        return base64.b64encode(f.read()).decode("utf-8")


def _get_mime_type(fig_path: Path) -> str:
    """Get MIME type for common image formats."""
    ext = fig_path.suffix.lower().lstrip(".")
    mime_map = {
        "png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg",
        "gif": "image/gif", "webp": "image/webp", "tif": "image/tiff",
        "tiff": "image/tiff", "bmp": "image/bmp",
    }
    return mime_map.get(ext, "image/png")


def _call_llm_vision(fig_path: Path, prompt: str,
                     provider: str = "openai",
                     model: str = "gpt-4o") -> Optional[str]:
    """Call an LLM vision API with a figure image.

    Supports: litellm (preferred), openai, anthropic.

    Parameters
    ----------
    fig_path : Path
        Path to the figure image.
    prompt : str
        Text prompt for the LLM.
    provider : str
        LLM provider: 'litellm', 'openai', or 'anthropic'.
    model : str
        Model name (e.g., 'gpt-4o', 'claude-sonnet-4-20250514').

    Returns
    -------
    str or None
        LLM response text, or None on failure.
    """
    img_b64 = _encode_image_base64(fig_path)
    mime = _get_mime_type(fig_path)

    # Build messages in OpenAI-compatible format
    messages = [{
        "role": "user",
        "content": [
            {"type": "text", "text": prompt},
            {"type": "image_url", "image_url": {
                "url": f"data:{mime};base64,{img_b64}"
            }}
        ]
    }]

    try:
        if provider == "litellm" and _HAS_LITELLM:
            response = litellm.completion(
                model=model, messages=messages,
                max_tokens=2000, temperature=0.1
            )
            return response.choices[0].message.content

        elif provider == "openai" and _HAS_OPENAI:
            client = _openai_mod.OpenAI()
            response = client.chat.completions.create(
                model=model, messages=messages,
                max_tokens=2000, temperature=0.1
            )
            return response.choices[0].message.content

        elif provider == "anthropic" and _HAS_ANTHROPIC:
            client = _anthropic_mod.Anthropic()
            # Anthropic uses a different image format
            response = client.messages.create(
                model=model,
                max_tokens=2000,
                messages=[{
                    "role": "user",
                    "content": [
                        {"type": "image", "source": {
                            "type": "base64",
                            "media_type": mime,
                            "data": img_b64
                        }},
                        {"type": "text", "text": prompt}
                    ]
                }]
            )
            return response.content[0].text

        else:
            return None

    except Exception as e:
        return f"LLM_ERROR: {e}"


def _critique_figure(fig_path: Path, caption: str = "",
                     context: str = "",
                     provider: str = "openai",
                     model: str = "gpt-4o") -> LLMCritique:
    """Get LLM critique of a scientific figure."""
    critique = LLMCritique(provider=provider, model=model)

    caption_section = f"Caption: \"{caption}\"" if caption else "No caption was provided."
    context_section = f"Context: {context}" if context else ""

    prompt = _FIGURE_EVALUATION_PROMPT.format(
        caption_section=caption_section,
        context_section=context_section
    )

    response = _call_llm_vision(fig_path, prompt, provider, model)
    if not response or response.startswith("LLM_ERROR"):
        critique.raw_response = response or "No LLM available"
        return critique

    critique.raw_response = response

    # Parse JSON from response
    try:
        # Strip markdown fences if present
        cleaned = re.sub(r'^```(?:json)?\s*', '', response.strip())
        cleaned = re.sub(r'\s*```$', '', cleaned)
        data = json.loads(cleaned)

        critique.clarity_score = int(data.get("clarity_score", 0))
        critique.completeness_score = int(data.get("completeness_score", 0))
        critique.scientific_quality_score = int(data.get("scientific_quality_score", 0))
        critique.caption_alignment_score = int(data.get("caption_alignment_score", 0))
        critique.accessibility_score = int(data.get("accessibility_score", 0))
        critique.strengths = data.get("strengths", [])
        critique.issues = data.get("issues", [])
        critique.suggested_caption = data.get("suggested_caption", "")
        critique.suggested_improvements = data.get("suggested_improvements", [])
        critique.context_narrative = data.get("context_narrative", "")

        # Overall = weighted average
        scores = [
            critique.clarity_score,
            critique.completeness_score,
            critique.scientific_quality_score,
            critique.accessibility_score,
        ]
        if critique.caption_alignment_score > 0:
            scores.append(critique.caption_alignment_score)
        weights = [0.25, 0.20, 0.30, 0.10, 0.15][:len(scores)]
        total_w = sum(weights)
        critique.overall_score = round(
            sum(s * w for s, w in zip(scores, weights)) / total_w, 2
        )

    except (json.JSONDecodeError, ValueError, TypeError):
        pass  # Keep defaults

    return critique


# ── Main Evaluation Functions ────────────────────────────────────────────

def evaluate_figure(fig_path, caption="", context="",
                    provider="openai", model="gpt-4o",
                    skip_llm=False, skip_ocr=False) -> FigureEvaluation:
    """Evaluate a single figure through all layers.

    Parameters
    ----------
    fig_path : str or Path
        Path to the figure file.
    caption : str
        Expected caption for the figure.
    context : str
        Surrounding text context from the manuscript.
    provider : str
        LLM provider ('litellm', 'openai', 'anthropic').
    model : str
        Model identifier.
    skip_llm : bool
        Skip Layer 3 (LLM critique).
    skip_ocr : bool
        Skip Layer 2 (structural analysis).

    Returns
    -------
    FigureEvaluation
        Complete evaluation report.
    """
    fig_path = Path(fig_path)
    report = FigureEvaluation(
        figure_path=str(fig_path),
        figure_name=fig_path.name,
        caption=caption
    )

    # Layer 1: Technical validation
    report.technical = _check_technical(fig_path)

    # Layer 1b: Quality score (if pyiqa available)
    quality_check = _check_quality_score(fig_path)
    if quality_check:
        report.technical.append(quality_check)

    # Layer 2: Structural analysis
    if not skip_ocr:
        report.structural = _analyze_structure(fig_path)

    # Layer 3: LLM critique
    if not skip_llm:
        report.llm_critique = _critique_figure(
            fig_path, caption, context, provider, model
        )

    # Overall grade
    report.overall_grade = _compute_grade(report)
    report.summary = _generate_summary(report)

    return report


def _compute_grade(report: FigureEvaluation) -> str:
    """Compute overall letter grade from all layers."""
    score = 0.0
    components = 0

    # Technical: count passes
    if report.technical:
        tech_checks = [c for c in report.technical if c.severity != "INFO"]
        fail_count = sum(1 for c in report.technical if c.severity == "FAIL")
        warn_count = sum(1 for c in report.technical if c.severity == "WARNING")
        if fail_count > 0:
            score += 1.0
        elif warn_count > 2:
            score += 2.5
        elif warn_count > 0:
            score += 3.5
        else:
            score += 5.0
        components += 1

    # Structural: element completeness
    if report.structural and report.structural.element_count > 0:
        structural_score = min(report.structural.element_count / 4.0, 1.0) * 5.0
        score += structural_score
        components += 1

    # LLM overall
    if report.llm_critique and report.llm_critique.overall_score > 0:
        score += report.llm_critique.overall_score
        components += 1

    if components == 0:
        return "PENDING"

    avg = score / components
    if avg >= 4.5:
        return "A"
    elif avg >= 3.5:
        return "B"
    elif avg >= 2.5:
        return "C"
    elif avg >= 1.5:
        return "D"
    else:
        return "F"


def _generate_summary(report: FigureEvaluation) -> str:
    """Generate a one-line summary of the evaluation."""
    parts = []

    fails = sum(1 for c in report.technical if c.severity == "FAIL")
    warns = sum(1 for c in report.technical if c.severity == "WARNING")
    if fails:
        parts.append(f"{fails} technical failure(s)")
    elif warns:
        parts.append(f"{warns} warning(s)")
    else:
        parts.append("technical OK")

    if report.structural:
        parts.append(f"{report.structural.element_count}/5 elements detected")

    if report.llm_critique and report.llm_critique.overall_score > 0:
        parts.append(f"LLM score: {report.llm_critique.overall_score}/5")

    return f"Grade {report.overall_grade}: {'; '.join(parts)}"


# ── Batch evaluation ─────────────────────────────────────────────────────

def evaluate_all_figures(paper_dir, provider="openai", model="gpt-4o",
                         skip_llm=False, skip_ocr=False) -> List[FigureEvaluation]:
    """Evaluate all figures in a paper or book chapter.

    Parameters
    ----------
    paper_dir : str or Path
        Path to paper directory (expects figures/ subdirectory).
    provider, model : str
        LLM settings.
    skip_llm, skip_ocr : bool
        Skip layers.

    Returns
    -------
    list of FigureEvaluation
    """
    paper_dir = Path(paper_dir)
    figures_dir = paper_dir / "figures"
    if not figures_dir.exists():
        return []

    # Load captions from paper.md or results.json
    captions = _load_captions(paper_dir)

    fig_extensions = {"*.png", "*.jpg", "*.jpeg", "*.tif", "*.tiff", "*.svg"}
    fig_files = []
    for pattern in fig_extensions:
        fig_files.extend(figures_dir.glob(pattern))
    fig_files.sort()

    reports = []
    for fig_path in fig_files:
        caption = captions.get(fig_path.name, "")
        report = evaluate_figure(
            fig_path, caption=caption,
            provider=provider, model=model,
            skip_llm=skip_llm, skip_ocr=skip_ocr
        )
        reports.append(report)

    return reports


def _load_captions(paper_dir: Path) -> Dict[str, str]:
    """Load figure captions from results.json or paper.md."""
    captions = {}

    # Try results.json first
    results_file = paper_dir / "results.json"
    if results_file.exists():
        try:
            with open(results_file) as f:
                data = json.load(f)
            for fname, cap in data.get("figure_captions", {}).items():
                captions[fname] = cap
        except (json.JSONDecodeError, OSError):
            pass

    # Fallback: extract from paper.md
    paper_file = paper_dir / "paper.md"
    if not paper_file.exists():
        paper_file = paper_dir / "chapter.md"
    if paper_file.exists():
        try:
            text = paper_file.read_text(encoding="utf-8")
            # Match: ![caption](figures/filename.png) or **Figure N.** Caption text
            for m in re.finditer(r'!\[([^\]]*)\]\([^)]*?/([^/)]+)\)', text):
                caption_text, fname = m.group(1), m.group(2)
                if caption_text:
                    captions[fname] = caption_text
            for m in re.finditer(
                r'\*\*Figure\s+\d+[\.\:]?\*\*[:\s]*(.+?)(?:\n|$)', text
            ):
                # Use nearby image reference
                pass  # Complex linking — LLM handles this better
        except OSError:
            pass

    return captions


# ── Report Output ────────────────────────────────────────────────────────

def print_evaluation_report(reports: List[FigureEvaluation]):
    """Print a formatted evaluation report to stdout."""
    if not reports:
        print("No figures to evaluate.")
        return

    grades = {"A": 0, "B": 0, "C": 0, "D": 0, "F": 0, "PENDING": 0}
    for r in reports:
        grades[r.overall_grade] = grades.get(r.overall_grade, 0) + 1

    print(f"Figure Evaluation Report -- {len(reports)} figure(s)")
    print(f"Grades: {' | '.join(f'{g}={n}' for g, n in grades.items() if n > 0)}")
    print("=" * 70)

    for r in reports:
        print(f"\n  [{r.overall_grade}] {r.figure_name}")
        print(f"      {r.summary}")

        # Technical details
        fails = [c for c in r.technical if c.severity in ("FAIL", "WARNING")]
        for c in fails:
            icon = "[FAIL]" if c.severity == "FAIL" else "[WARN]"
            print(f"      {icon} {c.check}: {c.message}")

        # Structural
        if r.structural and r.structural.element_count > 0:
            elements = []
            if r.structural.has_axis_labels:
                elements.append("axes")
            if r.structural.has_units:
                elements.append("units")
            if r.structural.has_legend:
                elements.append("legend")
            if r.structural.has_title:
                elements.append("title")
            print(f"      Elements: {', '.join(elements) or 'none detected'}")

        # LLM critique
        if r.llm_critique and r.llm_critique.overall_score > 0:
            lc = r.llm_critique
            print(f"      LLM ({lc.model}): clarity={lc.clarity_score} "
                  f"complete={lc.completeness_score} quality={lc.scientific_quality_score} "
                  f"access={lc.accessibility_score}")
            if lc.issues:
                for issue in lc.issues[:3]:
                    print(f"        → {issue}")
            if lc.context_narrative:
                print(f"      Context: {lc.context_narrative[:120]}...")

    print()


def save_evaluation_report(reports: List[FigureEvaluation], output_path):
    """Save evaluation report to JSON."""
    output_path = Path(output_path)
    data = {
        "evaluation_date": __import__("datetime").datetime.now().isoformat(),
        "figure_count": len(reports),
        "grade_summary": {},
        "figures": [r.to_dict() for r in reports],
    }
    for r in reports:
        data["grade_summary"][r.overall_grade] = \
            data["grade_summary"].get(r.overall_grade, 0) + 1

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, default=str)
