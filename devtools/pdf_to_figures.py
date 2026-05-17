#!/usr/bin/env python3
"""
pdf_to_figures.py — Convert PDF pages to PNG images for the task-solving workflow.

Renders each page of a PDF as a high-resolution PNG image. Useful for:
- Reviewing engineering drawings, P&IDs, compressor sketches
- Embedding reference document pages in notebooks and reports
- Making PDF content viewable by AI tools (view_image)

Usage (CLI):
    # Convert all pages of a single PDF
    python devtools/pdf_to_figures.py path/to/document.pdf

    # Convert to a specific output directory
    python devtools/pdf_to_figures.py path/to/document.pdf --outdir figures/

    # Convert specific pages only (1-indexed)
    python devtools/pdf_to_figures.py path/to/document.pdf --pages 1 3 5

    # Convert all PDFs in a folder
    python devtools/pdf_to_figures.py path/to/references/

    # Custom DPI (default: 200)
    python devtools/pdf_to_figures.py path/to/document.pdf --dpi 300

Usage (Python / Notebook):
    from devtools.pdf_to_figures import pdf_to_pngs, pdf_folder_to_pngs

    # Single PDF → list of PNG paths
    pngs = pdf_to_pngs("references/compressor_sketch.pdf", outdir="figures/")

    # All PDFs in a folder
    all_pngs = pdf_folder_to_pngs("step1_scope_and_research/references/")

Requires: pymupdf  (pip install pymupdf)
"""

import argparse
import os
import re
import sys
from pathlib import Path
from typing import List, Optional


def _slugify(name: str) -> str:
    """Convert a filename to a safe slug for output filenames."""
    name = Path(name).stem
    name = re.sub(r"[^\w\s-]", "", name)
    name = re.sub(r"[\s]+", "_", name)
    return name.strip("_").lower()


def pdf_to_pngs(
    pdf_path: str,
    outdir: Optional[str] = None,
    dpi: int = 200,
    pages: Optional[List[int]] = None,
) -> List[str]:
    """
    Convert PDF pages to PNG images.

    Parameters
    ----------
    pdf_path : str
        Path to the PDF file.
    outdir : str, optional
        Output directory for PNGs. Defaults to same directory as the PDF.
    dpi : int
        Resolution in dots per inch (default: 200, good for drawings).
    pages : list of int, optional
        1-indexed page numbers to convert. None = all pages.

    Returns
    -------
    list of str
        Paths to the generated PNG files.
    """
    try:
        import fitz  # pymupdf
    except ImportError:
        print("ERROR: pymupdf not installed. Run: pip install pymupdf")
        sys.exit(1)

    pdf_path = Path(pdf_path).resolve()
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    if outdir is None:
        outdir = pdf_path.parent
    else:
        outdir = Path(outdir).resolve()
    outdir.mkdir(parents=True, exist_ok=True)

    slug = _slugify(pdf_path.name)
    doc = fitz.open(str(pdf_path))
    total_pages = len(doc)

    # Validate page numbers
    if pages is not None:
        for p in pages:
            if p < 1 or p > total_pages:
                raise ValueError(
                    f"Page {p} out of range (PDF has {total_pages} pages)"
                )
        page_indices = [p - 1 for p in pages]  # convert to 0-indexed
    else:
        page_indices = list(range(total_pages))

    zoom = dpi / 72.0  # 72 DPI is PDF default
    matrix = fitz.Matrix(zoom, zoom)

    output_paths = []
    for idx in page_indices:
        page = doc[idx]
        pix = page.get_pixmap(matrix=matrix)

        if total_pages == 1:
            out_name = f"{slug}.png"
        else:
            out_name = f"{slug}_page{idx + 1:02d}.png"

        out_path = outdir / out_name
        pix.save(str(out_path))
        output_paths.append(str(out_path))
        print(f"  [{idx + 1}/{total_pages}] {out_path.name} ({pix.width}x{pix.height})")

    doc.close()
    return output_paths


def pdf_folder_to_pngs(
    folder: str,
    outdir: Optional[str] = None,
    dpi: int = 200,
    pages: Optional[List[int]] = None,
) -> List[str]:
    """
    Convert all PDFs in a folder to PNG images.

    Parameters
    ----------
    folder : str
        Path to folder containing PDF files.
    outdir : str, optional
        Output directory for all PNGs. Defaults to each PDF's own directory.
    dpi : int
        Resolution in dots per inch.
    pages : list of int, optional
        If given, only these pages are extracted from EACH PDF.

    Returns
    -------
    list of str
        Paths to all generated PNG files.
    """
    folder = Path(folder).resolve()
    if not folder.is_dir():
        raise NotADirectoryError(f"Not a directory: {folder}")

    pdfs = sorted(folder.glob("*.pdf"))
    if not pdfs:
        print(f"No PDF files found in {folder}")
        return []

    all_paths = []
    for pdf in pdfs:
        print(f"\n--- {pdf.name} ---")
        paths = pdf_to_pngs(str(pdf), outdir=outdir, dpi=dpi, pages=pages)
        all_paths.extend(paths)

    print(f"\nTotal: {len(all_paths)} images from {len(pdfs)} PDFs")
    return all_paths


def main():
    parser = argparse.ArgumentParser(
        description="Convert PDF pages to PNG images for the NeqSim task workflow"
    )
    parser.add_argument(
        "input",
        help="Path to a PDF file or a folder of PDFs",
    )
    parser.add_argument(
        "--outdir", "-o",
        default=None,
        help="Output directory for PNGs (default: same as input)",
    )
    parser.add_argument(
        "--dpi", "-d",
        type=int,
        default=200,
        help="Resolution in DPI (default: 200)",
    )
    parser.add_argument(
        "--pages", "-p",
        type=int,
        nargs="+",
        default=None,
        help="Page numbers to extract (1-indexed, default: all)",
    )

    args = parser.parse_args()
    input_path = Path(args.input)

    if input_path.is_dir():
        pdf_folder_to_pngs(
            str(input_path), outdir=args.outdir, dpi=args.dpi, pages=args.pages
        )
    elif input_path.is_file() and input_path.suffix.lower() == ".pdf":
        print(f"Converting: {input_path.name}")
        pdf_to_pngs(
            str(input_path), outdir=args.outdir, dpi=args.dpi, pages=args.pages
        )
    else:
        print(f"ERROR: {input_path} is not a PDF file or directory")
        sys.exit(1)


if __name__ == "__main__":
    main()
