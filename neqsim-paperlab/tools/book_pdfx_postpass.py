"""PDF/X-1a or PDF/A post-processing via Ghostscript.

Activated when the publisher profile declares::

    print_specs:
      pdf_standard: PDF/X-1a:2003   # or PDF/A-2b
      color_profile: CMYK            # or sRGB

Ghostscript must be on PATH (`gswin64c.exe` on Windows, `gs` elsewhere).
If Ghostscript is missing the original PDF is left untouched and a
warning is printed — never an error.
"""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path


def _gs_executable() -> str | None:
    if sys.platform == "win32":
        for name in ("gswin64c", "gswin32c", "gs"):
            p = shutil.which(name)
            if p:
                return p
        return None
    return shutil.which("gs")


def apply_pdfx_postpass(pdf_path, profile: dict) -> Path:
    """Run a PDF/X or PDF/A conversion if requested by the publisher profile."""
    pdf_path = Path(pdf_path)
    specs = (profile or {}).get("print_specs") or {}
    standard = (specs.get("pdf_standard") or "").upper()
    if not standard:
        return pdf_path
    gs = _gs_executable()
    if not gs:
        print("[pdfx] Ghostscript not found on PATH — skipping PDF/X conversion")
        return pdf_path

    color = (specs.get("color_profile") or "CMYK").upper()
    out = pdf_path.with_name(pdf_path.stem + ".pdfx" + pdf_path.suffix)

    if "PDF/X" in standard:
        device_args = ["-dPDFX", "-dRenderIntent=3"]
        compat = "1.4"
    elif "PDF/A" in standard:
        # PDF/A-2b
        device_args = ["-dPDFA=2", "-dPDFACompatibilityPolicy=1"]
        compat = "1.7"
    else:
        print(f"[pdfx] unrecognized standard '{standard}' — leaving PDF unchanged")
        return pdf_path

    color_strategy = "CMYK" if color == "CMYK" else "sRGB"
    cmd = [
        gs, "-dBATCH", "-dNOPAUSE", "-dQUIET", "-dSAFER",
        "-sDEVICE=pdfwrite",
        f"-dCompatibilityLevel={compat}",
        f"-sColorConversionStrategy={color_strategy}",
        "-dProcessColorModel=/DeviceCMYK" if color_strategy == "CMYK" else "-dProcessColorModel=/DeviceRGB",
        *device_args,
        f"-sOutputFile={out}",
        str(pdf_path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"[pdfx] Ghostscript failed (exit {result.returncode}): {result.stderr.strip()[:400]}")
        return pdf_path

    # Replace original with the standards-compliant version
    backup = pdf_path.with_suffix(".pre-pdfx.pdf")
    pdf_path.replace(backup)
    out.replace(pdf_path)
    print(f"[pdfx] {standard} ({color_strategy}) applied → {pdf_path.name}  (original kept as {backup.name})")
    return pdf_path
