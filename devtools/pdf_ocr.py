#!/usr/bin/env python3
"""
pdf_ocr.py — OCR text extraction from PDFs (scanned documents, P&IDs, vendor
datasheets) for the task-solving workflow.

Complements ``devtools/pdf_to_figures.py``:
- ``pdf_to_figures.py``  — rasterises pages to PNGs for ``view_image`` (visual)
- ``pdf_ocr.py``         — extracts machine-readable text (textual)

Workflow:
1. Try fast text extraction with pymupdf
2. If yield is low (likely a scanned PDF), run OCR via OCRmyPDF/Tesseract
3. Optionally extract engineering tags via regex (P&ID mode)

Usage (CLI):
    # Extract text (auto-OCR if needed) → JSON {page: text}
    python devtools/pdf_ocr.py document.pdf --json out.json

    # P&ID preset: 400 DPI, sparse mode, rotation, tag extraction
    python devtools/pdf_ocr.py drawing.pdf --pid

    # Force OCR even if text layer exists
    python devtools/pdf_ocr.py document.pdf --force

    # Add a searchable text layer to a scanned PDF (in-place via OCRmyPDF)
    python devtools/pdf_ocr.py scanned.pdf --add-text-layer --output searchable.pdf

Usage (Python / Notebook):
    from devtools.pdf_ocr import extract_text, extract_tags, ocr_pdf

    pages = extract_text("references/scanned_datasheet.pdf")  # {1: "...", 2: "..."}
    tags  = extract_tags("references/PID-001.pdf")            # ["V-100", "PT-2301", ...]
    ocr_pdf("scanned.pdf", out_path="scanned_ocr.pdf")        # add searchable layer

Dependencies (optional extra):
    pip install ocrmypdf pytesseract pdf2image pymupdf
    # Plus the Tesseract binary:
    #   Windows: choco install tesseract  (or download from UB Mannheim build)
    #   macOS:   brew install tesseract
    #   Linux:   apt install tesseract-ocr poppler-utils
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Dict, List, Optional


# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------

DEFAULT_DPI = 300
PID_DPI = 400
MIN_CHARS_PER_PAGE = 50  # below this, assume the page is scanned

# Engineering tag patterns commonly seen on P&IDs
DEFAULT_TAG_PATTERN = re.compile(
    r"\b[A-Z]{1,4}-\d{3,5}[A-Z]?\b"  # e.g. V-100, PT-2301A, FIC-12345
)


# ---------------------------------------------------------------------------
# Dependency helpers
# ---------------------------------------------------------------------------


def _have_tesseract() -> bool:
    return shutil.which("tesseract") is not None


def _have_ocrmypdf() -> bool:
    return shutil.which("ocrmypdf") is not None


def _require_tesseract() -> None:
    if not _have_tesseract():
        raise RuntimeError(
            "Tesseract binary not found on PATH. Install it:\n"
            "  Windows: choco install tesseract  (or use UB Mannheim build)\n"
            "  macOS:   brew install tesseract\n"
            "  Linux:   apt install tesseract-ocr poppler-utils"
        )


# ---------------------------------------------------------------------------
# Fast text extraction (pymupdf) — try this first
# ---------------------------------------------------------------------------


def _extract_text_pymupdf(pdf_path: Path) -> Dict[int, str]:
    """Extract embedded text from a PDF using pymupdf. Returns {page_num: text}."""
    try:
        import fitz  # pymupdf
    except ImportError as exc:
        raise RuntimeError(
            "pymupdf not installed. Run: pip install pymupdf"
        ) from exc

    out: Dict[int, str] = {}
    doc = fitz.open(str(pdf_path))
    try:
        for i, page in enumerate(doc, start=1):
            out[i] = page.get_text("text") or ""
    finally:
        doc.close()
    return out


def _is_likely_scanned(pages: Dict[int, str], threshold: int = MIN_CHARS_PER_PAGE) -> bool:
    """Heuristic: if average chars/page is below threshold, treat as scanned."""
    if not pages:
        return True
    avg = sum(len(t.strip()) for t in pages.values()) / len(pages)
    return avg < threshold


# ---------------------------------------------------------------------------
# OCRmyPDF wrapper — adds a searchable text layer to a scanned PDF
# ---------------------------------------------------------------------------


def ocr_pdf(
    pdf_path: str,
    out_path: Optional[str] = None,
    language: str = "eng",
    dpi: int = DEFAULT_DPI,
    rotate: bool = True,
    deskew: bool = True,
    force: bool = False,
) -> str:
    """
    Run OCRmyPDF on a PDF to add a searchable text layer.

    Parameters
    ----------
    pdf_path : str
        Input PDF path.
    out_path : str, optional
        Output PDF path. Defaults to ``<input>_ocr.pdf`` next to the input.
    language : str
        Tesseract language code (e.g. "eng", "eng+nor"). Multi-lang with "+".
    dpi : int
        Image DPI used when rasterising pages without text.
    rotate : bool
        Auto-detect and correct page rotation (Tesseract OSD).
    deskew : bool
        Deskew tilted scans.
    force : bool
        Re-OCR pages that already have a text layer.

    Returns
    -------
    str
        Path to the OCR'd PDF.
    """
    if not _have_ocrmypdf():
        raise RuntimeError(
            "ocrmypdf not installed. Run: pip install ocrmypdf"
        )
    _require_tesseract()

    src = Path(pdf_path).resolve()
    if not src.exists():
        raise FileNotFoundError(src)

    if out_path is None:
        dst = src.with_name(src.stem + "_ocr.pdf")
    else:
        dst = Path(out_path).resolve()
        dst.parent.mkdir(parents=True, exist_ok=True)

    cmd = [
        "ocrmypdf",
        "--language", language,
        "--image-dpi", str(dpi),
        "--output-type", "pdf",
    ]
    if rotate:
        cmd.append("--rotate-pages")
    if deskew:
        cmd.append("--deskew")
    if force:
        cmd.append("--force-ocr")
    else:
        cmd.append("--skip-text")  # don't re-OCR pages that already have text
    cmd += [str(src), str(dst)]

    print(f"[pdf_ocr] running: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)
    return str(dst)


# ---------------------------------------------------------------------------
# Direct page-level OCR (pytesseract + pdf2image) — needed for P&ID tag mode
# ---------------------------------------------------------------------------


def _ocr_pages_pytesseract(
    pdf_path: Path,
    dpi: int = DEFAULT_DPI,
    language: str = "eng",
    psm: int = 6,
    pages: Optional[List[int]] = None,
) -> Dict[int, str]:
    """OCR each page individually using pytesseract. Returns {page_num: text}."""
    _require_tesseract()
    try:
        from pdf2image import convert_from_path
        import pytesseract
    except ImportError as exc:
        raise RuntimeError(
            "pytesseract / pdf2image not installed. "
            "Run: pip install pytesseract pdf2image"
        ) from exc

    kwargs = {"dpi": dpi}
    if pages is not None:
        # pdf2image is 1-indexed
        kwargs["first_page"] = min(pages)
        kwargs["last_page"] = max(pages)

    images = convert_from_path(str(pdf_path), **kwargs)
    config = f"--psm {psm}"

    out: Dict[int, str] = {}
    base = kwargs.get("first_page", 1)
    for offset, img in enumerate(images):
        page_num = base + offset
        if pages is not None and page_num not in pages:
            continue
        text = pytesseract.image_to_string(img, lang=language, config=config)
        out[page_num] = text
    return out


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def extract_text(
    pdf_path: str,
    ocr_if_needed: bool = True,
    force_ocr: bool = False,
    dpi: int = DEFAULT_DPI,
    language: str = "eng",
    psm: int = 6,
) -> Dict[int, str]:
    """
    Extract text from a PDF, falling back to OCR for scanned pages.

    Parameters
    ----------
    pdf_path : str
        Path to the PDF.
    ocr_if_needed : bool
        If True and the PDF appears to be scanned, run OCR.
    force_ocr : bool
        Skip the text-layer check and run OCR unconditionally.
    dpi : int
        OCR rasterisation DPI.
    language : str
        Tesseract language code(s).
    psm : int
        Tesseract page segmentation mode (6 = block of text, 11 = sparse).

    Returns
    -------
    dict
        ``{page_number: extracted_text}`` (1-indexed).
    """
    src = Path(pdf_path).resolve()
    if not src.exists():
        raise FileNotFoundError(src)

    if not force_ocr:
        try:
            pages = _extract_text_pymupdf(src)
            if not _is_likely_scanned(pages):
                return pages
            if not ocr_if_needed:
                return pages
            print(
                f"[pdf_ocr] {src.name}: low text yield "
                f"(avg {sum(len(t) for t in pages.values()) / max(len(pages), 1):.0f} "
                f"chars/page) — running OCR"
            )
        except RuntimeError:
            if not ocr_if_needed:
                raise

    return _ocr_pages_pytesseract(src, dpi=dpi, language=language, psm=psm)


def extract_tags(
    pdf_path: str,
    pattern: Optional[str] = None,
    pid_mode: bool = False,
    language: str = "eng",
) -> List[str]:
    """
    Extract engineering tags (e.g. V-100, PT-2301) from a PDF.

    Parameters
    ----------
    pdf_path : str
        Path to the PDF.
    pattern : str, optional
        Custom regex. Defaults to ``[A-Z]{1,4}-\\d{3,5}[A-Z]?``.
    pid_mode : bool
        Use P&ID-tuned OCR settings (high DPI, sparse text PSM).
    language : str
        Tesseract language code.

    Returns
    -------
    list of str
        Unique tags, sorted.
    """
    rx = re.compile(pattern) if pattern else DEFAULT_TAG_PATTERN
    pages = extract_text(
        pdf_path,
        ocr_if_needed=True,
        force_ocr=pid_mode,
        dpi=PID_DPI if pid_mode else DEFAULT_DPI,
        psm=11 if pid_mode else 6,
        language=language,
    )
    tags = set()
    for text in pages.values():
        tags.update(rx.findall(text))
    return sorted(tags)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def _main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser(
        description="Extract text and engineering tags from PDFs (with OCR fallback).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("pdf", help="Path to the PDF")
    p.add_argument("--json", help="Write {page: text} to this JSON file")
    p.add_argument("--tags", action="store_true", help="Print extracted engineering tags")
    p.add_argument("--pid", action="store_true",
                   help="P&ID preset: 400 DPI, sparse-text PSM, force OCR, extract tags")
    p.add_argument("--force", action="store_true", help="Force OCR even if text layer exists")
    p.add_argument("--dpi", type=int, default=DEFAULT_DPI, help="OCR DPI (default: 300)")
    p.add_argument("--language", default="eng", help="Tesseract language(s), e.g. eng+nor")
    p.add_argument("--psm", type=int, default=6, help="Tesseract page seg. mode (default: 6)")
    p.add_argument("--add-text-layer", action="store_true",
                   help="Add a searchable text layer via OCRmyPDF (writes a new PDF)")
    p.add_argument("--output", help="Output PDF path for --add-text-layer")
    args = p.parse_args(argv)

    pdf_path = args.pdf

    if args.add_text_layer:
        out = ocr_pdf(
            pdf_path,
            out_path=args.output,
            language=args.language,
            dpi=args.dpi,
            force=args.force,
        )
        print(f"[pdf_ocr] wrote: {out}")
        return 0

    pid = args.pid
    pages = extract_text(
        pdf_path,
        force_ocr=args.force or pid,
        dpi=PID_DPI if pid else args.dpi,
        language=args.language,
        psm=11 if pid else args.psm,
    )

    if args.json:
        Path(args.json).write_text(
            json.dumps(pages, indent=2, ensure_ascii=False), encoding="utf-8"
        )
        print(f"[pdf_ocr] wrote: {args.json}")
    else:
        for n, text in pages.items():
            print(f"\n===== Page {n} =====\n{text}")

    if args.tags or pid:
        tags = set()
        for text in pages.values():
            tags.update(DEFAULT_TAG_PATTERN.findall(text))
        print("\n===== Engineering tags =====")
        for t in sorted(tags):
            print(t)

    return 0


if __name__ == "__main__":
    sys.exit(_main())
