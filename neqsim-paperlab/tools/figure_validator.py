"""
Figure Validator — Check figures meet journal submission requirements.

Validates DPI, dimensions, format, file size, and color mode using Pillow.

Usage::

    from tools.figure_validator import validate_figures

    issues = validate_figures("papers/my_paper/figures/", journal_profile)
    for issue in issues:
        print(f"[{issue['severity']}] {issue['file']}: {issue['message']}")
"""

from pathlib import Path
from typing import Dict, List, Optional

try:
    from PIL import Image
    _HAS_PIL = True
except ImportError:
    _HAS_PIL = False


# Default requirements (Elsevier baseline)
_DEFAULTS = {
    "min_dpi": 300,
    "max_file_size_mb": 20,
    "allowed_formats": {"png", "tif", "tiff", "eps", "pdf", "jpg", "jpeg"},
    "min_width_px": 500,
    "max_width_px": 10000,
    "preferred_formats": {"tif", "pdf", "eps"},
}


def validate_single_figure(fig_path, requirements=None):
    """Validate a single figure file.

    Parameters
    ----------
    fig_path : str or Path
        Path to the figure file.
    requirements : dict, optional
        Override default requirements.

    Returns
    -------
    list of dict
        Each dict has keys: file, severity (INFO/WARNING/FAIL), message.
    """
    fig_path = Path(fig_path)
    reqs = dict(_DEFAULTS)
    if requirements:
        reqs.update(requirements)

    issues = []
    name = fig_path.name

    if not fig_path.exists():
        issues.append({"file": name, "severity": "FAIL",
                       "message": "File not found"})
        return issues

    # Check file extension
    ext = fig_path.suffix.lower().lstrip(".")
    if ext not in reqs["allowed_formats"]:
        issues.append({"file": name, "severity": "FAIL",
                       "message": f"Format .{ext} not accepted. Use: {reqs['allowed_formats']}"})

    # Check file size
    size_mb = fig_path.stat().st_size / (1024 * 1024)
    if size_mb > reqs["max_file_size_mb"]:
        issues.append({"file": name, "severity": "WARNING",
                       "message": f"File size {size_mb:.1f} MB exceeds {reqs['max_file_size_mb']} MB"})

    # Pillow checks (raster images only)
    if _HAS_PIL and ext in {"png", "jpg", "jpeg", "tif", "tiff", "bmp"}:
        try:
            with Image.open(fig_path) as img:
                width, height = img.size

                # DPI check (allow 0.5 tolerance for floating-point rounding)
                dpi = img.info.get("dpi", (72, 72))
                x_dpi = dpi[0] if isinstance(dpi, (tuple, list)) else dpi
                if x_dpi < reqs["min_dpi"] - 0.5:
                    issues.append({"file": name, "severity": "FAIL",
                                   "message": f"DPI is {x_dpi:.0f}, minimum is {reqs['min_dpi']}"})
                else:
                    issues.append({"file": name, "severity": "INFO",
                                   "message": f"DPI: {x_dpi:.0f} (OK)"})

                # Dimension checks
                if width < reqs["min_width_px"]:
                    issues.append({"file": name, "severity": "WARNING",
                                   "message": f"Width {width}px may be too small for print"})
                if width > reqs["max_width_px"]:
                    issues.append({"file": name, "severity": "WARNING",
                                   "message": f"Width {width}px is unusually large"})

                # Color mode
                if img.mode == "RGBA":
                    issues.append({"file": name, "severity": "WARNING",
                                   "message": "Image has alpha channel — some journals reject this"})

                # Check if grayscale image could use smaller format
                if img.mode in ("L", "1") and ext in ("png", "jpg"):
                    issues.append({"file": name, "severity": "INFO",
                                   "message": "Grayscale image — consider TIFF for line art"})

        except Exception as e:
            issues.append({"file": name, "severity": "WARNING",
                           "message": f"Could not read image metadata: {e}"})

    # Suggest preferred format
    if ext not in reqs.get("preferred_formats", set()):
        pref = ", ".join(sorted(reqs.get("preferred_formats", {"tif", "pdf"})))
        issues.append({"file": name, "severity": "INFO",
                       "message": f"Consider converting to {pref} for best print quality"})

    if not issues:
        issues.append({"file": name, "severity": "INFO", "message": "All checks passed"})

    return issues


def validate_figures(figures_dir, journal_profile=None):
    """Validate all figures in a directory against journal requirements.

    Parameters
    ----------
    figures_dir : str or Path
        Directory containing figure files.
    journal_profile : dict, optional
        Journal profile dict (from YAML). Extracts figure_dpi_min and
        figure_formats to build requirements.

    Returns
    -------
    list of dict
        All issues found across all figures.
    """
    figures_dir = Path(figures_dir)
    if not figures_dir.exists():
        return [{"file": str(figures_dir), "severity": "FAIL",
                 "message": "Figures directory not found"}]

    # Build requirements from journal profile
    reqs = dict(_DEFAULTS)
    if journal_profile:
        if "figure_dpi_min" in journal_profile:
            reqs["min_dpi"] = journal_profile["figure_dpi_min"]
        if "figure_formats" in journal_profile:
            reqs["allowed_formats"] = set(journal_profile["figure_formats"])
            reqs["preferred_formats"] = set(journal_profile["figure_formats"][:2])

    # Find all figure files
    extensions = {"*.png", "*.jpg", "*.jpeg", "*.tif", "*.tiff",
                  "*.eps", "*.pdf", "*.bmp", "*.svg"}
    fig_files = []
    for ext_pattern in extensions:
        fig_files.extend(figures_dir.glob(ext_pattern))
    fig_files.sort()

    if not fig_files:
        return [{"file": str(figures_dir), "severity": "WARNING",
                 "message": "No figure files found"}]

    all_issues = []
    for fig in fig_files:
        all_issues.extend(validate_single_figure(fig, reqs))

    return all_issues


def print_validation_report(issues):
    """Print a formatted validation report."""
    fails = [i for i in issues if i["severity"] == "FAIL"]
    warns = [i for i in issues if i["severity"] == "WARNING"]
    infos = [i for i in issues if i["severity"] == "INFO"]

    print(f"Figure Validation: {len(fails)} FAIL, {len(warns)} WARNING, {len(infos)} INFO")
    print("-" * 60)

    for issue in issues:
        icon = {"FAIL": "[FAIL]", "WARNING": "[WARN]", "INFO": "[ OK ]"}[issue["severity"]]
        print(f"  {icon} {issue['file']}: {issue['message']}")

    if fails:
        print(f"\n{len(fails)} issue(s) must be fixed before submission.")
    elif warns:
        print(f"\n{len(warns)} warning(s) — review before submission.")
    else:
        print("\nAll figures pass validation.")
