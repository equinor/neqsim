"""
Reproducibility Manifest — Generates a machine-readable JSON manifest for a paper,
capturing software versions, data hashes, benchmark configs, and figure provenance.

Usage::

    from tools.reproducibility_manifest import generate_manifest, print_manifest_report

    manifest = generate_manifest("papers/my_paper/")
    print_manifest_report(manifest)
"""

import hashlib
import json
import os
import platform
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional


def _file_hash(path, algorithm="sha256"):
    """Compute cryptographic hash of a file.

    Args:
        path: File path.
        algorithm: Hash algorithm (default: sha256).

    Returns:
        Hex digest string.
    """
    h = hashlib.new(algorithm)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _get_python_packages():
    """Get installed Python package versions relevant to PaperLab.

    Returns:
        Dict mapping package name to version string.
    """
    packages = {}
    try:
        import importlib.metadata as metadata
        target_packages = [
            "numpy", "scipy", "pandas", "matplotlib", "scienceplots",
            "jpype1", "bibtexparser", "textstat", "proselint",
            "language-tool-python", "scikit-learn", "Pillow",
            "python-docx", "Jinja2", "lxml", "requests",
        ]
        for pkg in target_packages:
            try:
                packages[pkg] = metadata.version(pkg)
            except metadata.PackageNotFoundError:
                pass
    except ImportError:
        pass
    return packages


def _get_neqsim_version():
    """Try to detect the NeqSim JAR version.

    Returns:
        Version string or None.
    """
    try:
        from neqsim import jneqsim
        return str(jneqsim.util.NamedBaseClass.VERSION) if hasattr(jneqsim.util, 'NamedBaseClass') else "unknown"
    except Exception:
        pass

    # Try reading from pom.xml
    pom_candidates = [
        Path(__file__).resolve().parents[2] / "pom.xml",
    ]
    for pom in pom_candidates:
        if pom.exists():
            import re
            text = pom.read_text(encoding="utf-8", errors="replace")
            match = re.search(r'<version>(\d+\.\d+\.\d+[^<]*)</version>', text)
            if match:
                return match.group(1)
    return None


def _hash_directory(directory, extensions=None):
    """Compute hashes for all files in a directory matching given extensions.

    Args:
        directory: Directory path.
        extensions: Set of file extensions to include (e.g., {".json", ".csv"}).

    Returns:
        List of dicts with file path, size, and hash.
    """
    directory = Path(directory)
    if not directory.exists():
        return []

    files = []
    for f in sorted(directory.rglob("*")):
        if f.is_file():
            if extensions and f.suffix.lower() not in extensions:
                continue
            files.append({
                "path": str(f.relative_to(directory)),
                "size_bytes": f.stat().st_size,
                "sha256": _file_hash(f),
                "modified": datetime.fromtimestamp(f.stat().st_mtime).isoformat(),
            })
    return files


def generate_manifest(paper_dir, include_raw_data=True):
    """Generate a reproducibility manifest for a paper project.

    Captures:
    - Environment: OS, Python version, package versions, NeqSim version
    - Data: Hashes of benchmark configs, results, and raw data
    - Figures: Hashes and metadata for all generated figures
    - Manuscript: Hash of paper.md and refs.bib
    - Plan: Copy of plan.json benchmark configuration

    Args:
        paper_dir: Path to the paper directory.
        include_raw_data: Whether to hash raw data files.

    Returns:
        Manifest dict (also saved to paper_dir/reproducibility_manifest.json).
    """
    paper_dir = Path(paper_dir)
    if not paper_dir.exists():
        return {"error": f"Paper directory not found: {paper_dir}"}

    manifest = {
        "schema_version": "1.0",
        "generated": datetime.now().isoformat(),
        "paper_dir": str(paper_dir.resolve()),
    }

    # ── Environment ───────────────────────────────────────────────────
    manifest["environment"] = {
        "os": platform.platform(),
        "python": platform.python_version(),
        "python_implementation": platform.python_implementation(),
        "architecture": platform.machine(),
        "packages": _get_python_packages(),
        "neqsim_version": _get_neqsim_version(),
    }

    # ── Java environment ──────────────────────────────────────────────
    java_info = {}
    try:
        import jpype
        if jpype.isJVMStarted():
            java_info["jvm_path"] = str(jpype.getDefaultJVMPath())
            java_info["jvm_version"] = str(jpype.java.lang.System.getProperty("java.version"))
    except Exception:
        pass
    manifest["environment"]["java"] = java_info

    # ── Key files ─────────────────────────────────────────────────────
    key_files = {}
    for fname in ["paper.md", "refs.bib", "plan.json", "benchmark_config.json",
                   "results.json", "approved_claims.json", "claims_manifest.json"]:
        fpath = paper_dir / fname
        if fpath.exists():
            key_files[fname] = {
                "sha256": _file_hash(fpath),
                "size_bytes": fpath.stat().st_size,
                "modified": datetime.fromtimestamp(fpath.stat().st_mtime).isoformat(),
            }
    manifest["key_files"] = key_files

    # ── Benchmark configuration ───────────────────────────────────────
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        manifest["benchmark_config"] = {
            "title": plan.get("title"),
            "paper_type": plan.get("paper_type"),
            "target_journal": plan.get("target_journal"),
            "research_questions": plan.get("research_questions", []),
        }

    bench_config = paper_dir / "benchmark_config.json"
    if bench_config.exists():
        with open(bench_config) as f:
            manifest["benchmark_config_detail"] = json.load(f)

    # ── Results data ──────────────────────────────────────────────────
    results_dir = paper_dir / "results"
    manifest["results_files"] = _hash_directory(
        results_dir, extensions={".json", ".csv", ".jsonl"})

    if include_raw_data:
        raw_dir = results_dir / "raw"
        manifest["raw_data_files"] = _hash_directory(
            raw_dir, extensions={".json", ".csv", ".jsonl", ".txt"})

    # ── Figures ───────────────────────────────────────────────────────
    fig_dir = paper_dir / "figures"
    manifest["figures"] = _hash_directory(
        fig_dir, extensions={".png", ".pdf", ".svg", ".eps"})

    # ── Source code references ────────────────────────────────────────
    # List any .py or .java files in the paper directory
    source_files = []
    for ext in [".py", ".java", ".ipynb"]:
        for f in sorted(paper_dir.rglob(f"*{ext}")):
            if ".ipynb_checkpoints" in str(f):
                continue
            source_files.append({
                "path": str(f.relative_to(paper_dir)),
                "sha256": _file_hash(f),
                "size_bytes": f.stat().st_size,
            })
    manifest["source_files"] = source_files

    # ── Save manifest ─────────────────────────────────────────────────
    manifest_path = paper_dir / "reproducibility_manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, default=str)

    return manifest


def verify_manifest(paper_dir):
    """Verify that current files match a previously generated manifest.

    Args:
        paper_dir: Path to the paper directory.

    Returns:
        Verification report dict.
    """
    paper_dir = Path(paper_dir)
    manifest_path = paper_dir / "reproducibility_manifest.json"
    if not manifest_path.exists():
        return {"error": "No reproducibility_manifest.json found. Run generate first."}

    with open(manifest_path) as f:
        manifest = json.load(f)

    mismatches = []
    missing = []

    # Check key files
    for fname, info in manifest.get("key_files", {}).items():
        fpath = paper_dir / fname
        if not fpath.exists():
            missing.append(fname)
            continue
        current_hash = _file_hash(fpath)
        if current_hash != info["sha256"]:
            mismatches.append({
                "file": fname,
                "expected": info["sha256"][:16] + "...",
                "actual": current_hash[:16] + "...",
            })

    return {
        "manifest_date": manifest.get("generated"),
        "files_checked": len(manifest.get("key_files", {})),
        "mismatches": mismatches,
        "missing": missing,
        "all_match": len(mismatches) == 0 and len(missing) == 0,
    }


def print_manifest_report(manifest):
    """Print a formatted reproducibility manifest report.

    Args:
        manifest: Manifest dict from generate_manifest() or verify_manifest().
    """
    if "error" in manifest:
        print(f"Error: {manifest['error']}")
        return

    # Verification report
    if "all_match" in manifest:
        print("=" * 60)
        print("MANIFEST VERIFICATION")
        print("=" * 60)
        print(f"  Manifest date: {manifest.get('manifest_date', 'unknown')}")
        print(f"  Files checked: {manifest['files_checked']}")
        if manifest["all_match"]:
            print("  Status: ALL FILES MATCH")
        else:
            if manifest["mismatches"]:
                print(f"  MISMATCHES ({len(manifest['mismatches'])}):")
                for m in manifest["mismatches"]:
                    print(f"    [!!] {m['file']}: expected {m['expected']}, got {m['actual']}")
            if manifest["missing"]:
                print(f"  MISSING ({len(manifest['missing'])}):")
                for m in manifest["missing"]:
                    print(f"    [XX] {m}")
        print()
        return

    # Generation report
    print("=" * 60)
    print("REPRODUCIBILITY MANIFEST")
    print("=" * 60)
    env = manifest.get("environment", {})
    print(f"  Python:  {env.get('python', '?')} ({env.get('python_implementation', '?')})")
    print(f"  OS:      {env.get('os', '?')}")
    print(f"  NeqSim:  {env.get('neqsim_version', 'not detected')}")
    java = env.get("java", {})
    if java.get("jvm_version"):
        print(f"  Java:    {java['jvm_version']}")

    pkgs = env.get("packages", {})
    if pkgs:
        print(f"\n  Packages ({len(pkgs)}):")
        for name, ver in sorted(pkgs.items()):
            print(f"    {name:25s} {ver}")

    key_files = manifest.get("key_files", {})
    if key_files:
        print(f"\n  Key Files ({len(key_files)}):")
        for fname, info in key_files.items():
            print(f"    {fname:30s} sha256={info['sha256'][:16]}...")

    figs = manifest.get("figures", [])
    if figs:
        print(f"\n  Figures ({len(figs)}):")
        for fig in figs:
            print(f"    {fig['path']:30s} sha256={fig['sha256'][:16]}...")

    src = manifest.get("source_files", [])
    if src:
        print(f"\n  Source Files ({len(src)}):")
        for s in src:
            print(f"    {s['path']:30s} sha256={s['sha256'][:16]}...")

    print(f"\n  Saved to: reproducibility_manifest.json")
    print()
