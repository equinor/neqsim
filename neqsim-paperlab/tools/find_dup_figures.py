"""Detect duplicate or near-duplicate lecture figures across a rendered book.

Strategies:
  1. Exact byte-hash (md5) — catches identical files at different paths.
  2. Perceptual hash (average hash, 8x8) — catches visually-same images
     that differ only by compression / cropping margin.
  3. Caption-text similarity — catches figures with the same slide_title
     pulled from different decks.

Usage:
    python -m tools.find_dup_figures <book_dir> [--phash-threshold 5]

Reads the rendered book.html if present (so figure numbers shown match the
output the reader sees) and the manifest.json under figures/lectures/auto/.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


def md5_file(p: Path) -> str:
    return hashlib.md5(p.read_bytes()).hexdigest()


def avg_hash(p: Path, size: int = 8) -> int | None:
    """8x8 average-hash perceptual fingerprint. Returns 64-bit int or None."""
    try:
        from PIL import Image
    except ImportError:
        return None
    try:
        img = Image.open(p).convert("L").resize((size, size), Image.BILINEAR)
    except Exception:
        return None
    px = list(img.getdata())
    avg = sum(px) / len(px)
    bits = 0
    for v in px:
        bits = (bits << 1) | (1 if v >= avg else 0)
    return bits


def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def parse_book_html(book_html: Path):
    """Return list of (label, file_rel) from the rendered HTML."""
    if not book_html.exists():
        return []
    text = book_html.read_text(encoding="utf-8")
    figs = re.findall(r'<figure class="lecture-fig">.*?</figure>', text, re.S)
    out = []
    for f in figs:
        lab = re.search(r"Lecture figure ([\d.]+)", f)
        src = re.search(r'(figures/lectures/auto/[^"]+)', f)
        if lab and src:
            out.append((lab.group(1), src.group(1)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("book_dir", type=Path)
    ap.add_argument("--phash-threshold", type=int, default=5,
                    help="Hamming distance ≤ N → near-duplicate (default 5)")
    args = ap.parse_args()

    book = args.book_dir
    auto_dir = book / "figures" / "lectures" / "auto"
    if not auto_dir.is_dir():
        print(f"[error] {auto_dir} not found", file=sys.stderr)
        sys.exit(1)

    files = sorted(p for p in auto_dir.rglob("*") if p.is_file()
                   and p.suffix.lower() in {".png", ".jpg", ".jpeg"})
    print(f"[info] scanning {len(files)} lecture figures under {auto_dir}")

    book_map = parse_book_html(book / "submission" / "book.html")
    rel_to_label = {}
    for lab, rel in book_map:
        rel_to_label.setdefault(rel.replace("\\", "/"), []).append(lab)

    def label_for(p: Path) -> str:
        rel = "figures/lectures/auto/" + str(p.relative_to(auto_dir)).replace("\\", "/")
        labs = rel_to_label.get(rel, [])
        return f"[{','.join(labs)}] " if labs else ""

    # 1. md5 dupes
    md5_groups: dict[str, list[Path]] = {}
    for p in files:
        md5_groups.setdefault(md5_file(p), []).append(p)
    md5_dups = [g for g in md5_groups.values() if len(g) > 1]

    print(f"\n=== Exact byte-hash duplicates: {len(md5_dups)} groups ===")
    for g in md5_dups:
        print("  DUP:")
        for p in g:
            print(f"    {label_for(p)}{p.relative_to(book)}")

    # 2. perceptual hash
    print("\n=== Perceptual near-duplicates (Pillow average-hash) ===")
    phashes = []
    skipped = 0
    for p in files:
        h = avg_hash(p)
        if h is None:
            skipped += 1
            continue
        phashes.append((p, h))
    if skipped == len(files):
        print("  [skipped — Pillow not installed; pip install pillow]")
    else:
        # Pairwise within tight threshold; group transitively.
        seen_pairs = []
        for i in range(len(phashes)):
            pi, hi = phashes[i]
            for j in range(i + 1, len(phashes)):
                pj, hj = phashes[j]
                d = hamming(hi, hj)
                if d <= args.phash_threshold and md5_file(pi) != md5_file(pj):
                    seen_pairs.append((d, pi, pj))
        seen_pairs.sort(key=lambda t: t[0])
        if not seen_pairs:
            print("  (none)")
        for d, pi, pj in seen_pairs:
            print(f"  d={d}:")
            print(f"    {label_for(pi)}{pi.relative_to(book)}")
            print(f"    {label_for(pj)}{pj.relative_to(book)}")

    # 3. caption-text duplicates from manifest
    manifest = auto_dir / "manifest.json"
    if manifest.is_file():
        try:
            data = json.loads(manifest.read_text(encoding="utf-8"))
        except Exception:
            data = None
        if data:
            entries = data if isinstance(data, list) else data.get("figures", [])
            if isinstance(entries, dict):
                # nested by chapter
                flat = []
                for v in entries.values():
                    if isinstance(v, list):
                        flat.extend(v)
                entries = flat
            cap_groups: dict[str, list[dict]] = {}
            for e in entries:
                if not isinstance(e, dict):
                    continue
                key = ((e.get("slide_title") or "").strip().lower() + " | " +
                       (e.get("slide_body") or "").strip().lower())
                if len(key) < 10:
                    continue
                cap_groups.setdefault(key, []).append(e)
            cap_dups = [g for g in cap_groups.values() if len(g) > 1]
            print(f"\n=== Same slide_title+body across files: {len(cap_dups)} groups ===")
            for g in cap_dups[:20]:
                title = (g[0].get("slide_title") or "")[:80]
                print(f"  '{title}' ({len(g)} copies)")
                for e in g:
                    f = e.get("file", "?")
                    p = book / f
                    print(f"    {label_for(p) if p.exists() else ''}{f}")


if __name__ == "__main__":
    main()
