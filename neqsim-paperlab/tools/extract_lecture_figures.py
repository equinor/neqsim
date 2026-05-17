"""
extract_lecture_figures.py
==========================

Walks the TPG4230 lecture folder, extracts every embedded image from PDF and
PPTX decks, filters out logos / icons / decorative shapes, deduplicates by
content hash, classifies each unique figure to a book chapter (using a
folder-name to chapter-slug map), and writes a JSON manifest plus the
filtered images to::

    <book_dir>/figures/lectures/auto/<chapter_slug>/<figure_id>.<ext>

The manifest entry holds title hints (lecture deck name, slide / page index,
caption text near the image when available).

Designed to be re-runnable: clean the output folder first, then call.
"""
from __future__ import annotations

import hashlib
import io
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

import fitz  # pymupdf
from PIL import Image


# ---------------------------------------------------------------------------
# Lecture folder ↔ chapter mapping.
# ---------------------------------------------------------------------------
# Keys are the lecture folder names. Values are the chapter slugs in
# books/tpg4230_field_development_and_operations_2026/chapters/.
LECTURE_TO_CHAPTERS = {
    "09-01-2026 Intro": ["ch01_introduction", "ch02_oil_gas_value_chain"],
    "13-01-2026 - Field development": [
        "ch01_introduction", "ch11_field_development_building_blocks"],
    "16-01-2026 - Flow Performance in Production Systems": [
        "ch04_flow_performance_production_systems"],
    "20-01.2026 - Facilities in the value chain": [
        "ch05_facilities_value_chain", "ch17_cost_estimation_scheduling"],
    "23-01-2026 - Introduction to Oil and Gas Processing": [
        "ch06_intro_oil_gas_processing"],
    "30-01-2026 - Field development case": [
        "ch11_field_development_building_blocks",
        "ch25_case_studies_aasta_ultima_thule"],
    "03-02-2026 - Oil and Water Processing and Separator Design": [
        "ch07_oil_water_separator_design"],
    "10-02-2026 - Flow Assurance and Gas Processing": [
        "ch08_flow_assurance_gas_processing"],
    "13-02-2026 - Dry Gas Production Systems": [
        "ch09_dry_gas_production_systems"],
    "17-02-2026 - Field development": [
        "ch11_field_development_building_blocks", "ch18_economic_analysis_npv"],
    "20-02-2026 - Regulation of the Norwegian Continental Shelf": [
        "ch21_regulation_ncs"],
    "27-02-2026 - Oil and Gas Quality, Oil Refining, Oil and Gas Prices": [
        "ch22_oil_gas_quality_refining"],
    "03-03-2026 - Computational tools for field development": [
        "ch24_computational_tools_neqsim", "ch10_acid_gas_removal_dehydration"],
    "06-03-2006 - Aasta Hansteen and Cost Estimation": [
        "ch25_case_studies_aasta_ultima_thule", "ch17_cost_estimation_scheduling"],
    "10-03-2026 - Offshore Structures": ["ch12_offshore_structures"],
    "13-03-2026 - Production scheduling": [
        "ch19_production_scheduling_snohvit"],
    "17-03-2026 - Reservoir Technology in Field Development": [
        "ch15_reservoir_technology"],
    "20-03-2026 - CO2 in field developemnt and operations": [
        "ch23_co2_field_development"],
    "24-03-2026 - Field development Case study": [
        "ch25_case_studies_aasta_ultima_thule",
        "ch10_acid_gas_removal_dehydration"],
    "14-04-2026 - Production Optimisation": ["ch20_production_optimisation"],
    "17-04-2026 - Review of subject in field development": [
        "ch26_review_exam_preparation"],
}

# Files inside the topics/ helper folder.
TOPICS_TO_CHAPTERS = {
    "Approaches to calculate production profiles_Stanko.pdf": [
        "ch15_reservoir_technology", "ch20_production_optimisation"],
}

# Filtering thresholds.
MIN_W = 360        # px — drop small icons / glyphs
MIN_H = 260        # px
MIN_BYTES = 25_000  # smaller is almost certainly a logo / sticker
MAX_BYTES = 6_000_000   # safety cap
MAX_FIGS_PER_LECTURE = 8   # keep inline injection digestible

# Aspect ratios outside this band are likely banners / dividers.
MIN_AR = 0.55
MAX_AR = 2.6

# Pixel-statistics thresholds — flag near-uniform images (mostly white logos,
# solid backgrounds, single-colour shapes).
MIN_STDDEV = 28.0     # grayscale std-dev; logos / flat shapes sit well below
MAX_WHITE_FRACTION = 0.85   # >85 % near-white pixels → likely logo on white


def _slugify(text: str) -> str:
    text = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return text[:60] or "fig"


def _image_metrics(data: bytes):
    """Return (width, height, format, stddev, white_fraction) or None."""
    try:
        with Image.open(io.BytesIO(data)) as im:
            w, h = im.width, im.height
            fmt = (im.format or "").lower()
            # Downsample for speed; convert to grayscale for stats.
            small = im.convert("L")
            if max(small.size) > 400:
                small.thumbnail((400, 400))
            pixels = list(small.getdata())
            n = len(pixels)
            if n == 0:
                return None
            mean = sum(pixels) / n
            var = sum((p - mean) ** 2 for p in pixels) / n
            stddev = var ** 0.5
            white = sum(1 for p in pixels if p > 240) / n
            return w, h, fmt, stddev, white
    except Exception:
        return None


def _accept(width: int, height: int, nbytes: int,
            stddev: float, white_fraction: float) -> bool:
    if nbytes < MIN_BYTES or nbytes > MAX_BYTES:
        return False
    if width < MIN_W or height < MIN_H:
        return False
    ar = width / max(1, height)
    if ar < MIN_AR or ar > MAX_AR:
        return False
    if stddev < MIN_STDDEV:
        return False     # near-uniform: logo / flat colour
    if white_fraction > MAX_WHITE_FRACTION:
        return False     # mostly white background = logo / icon
    return True


def _summarise_text(text: str, max_chars: int = 600) -> tuple[str, str]:
    """Split slide text into (title, body). Title = first non-empty line
    (a heading or first bullet); body = the rest, lightly cleaned."""
    if not text:
        return "", ""
    # Normalise whitespace.
    lines = [ln.strip() for ln in text.splitlines()]
    lines = [ln for ln in lines if ln]
    if not lines:
        return "", ""
    title = lines[0]
    # Drop trivial page numbers / footers in the title.
    if re.fullmatch(r"\d{1,3}", title) and len(lines) > 1:
        title = lines[1]
        body_lines = lines[2:]
    else:
        body_lines = lines[1:]
    # Strip pure numeric lines (slide numbers, page footers) from body.
    body_lines = [ln for ln in body_lines if not re.fullmatch(r"\d{1,3}", ln)]
    body = " \u2022 ".join(body_lines)
    body = re.sub(r"\s+", " ", body)
    if len(body) > max_chars:
        body = body[: max_chars].rsplit(" ", 1)[0] + "\u2026"
    # Truncate excessively long titles too.
    if len(title) > 140:
        title = title[:140].rsplit(" ", 1)[0] + "\u2026"
    return title, body


def extract_pdf(pdf_path: Path):
    """Yield (image_bytes, meta) for each accepted image in a PDF."""
    try:
        doc = fitz.open(pdf_path)
    except Exception as exc:
        print(f"  ! cannot open {pdf_path.name}: {exc}")
        return
    try:
        # Pre-pass: count how many pages each image xref appears on.
        # An xref reused on ≥3 pages is almost certainly a header/footer logo.
        xref_pages: dict[int, int] = {}
        for page_idx in range(len(doc)):
            page = doc.load_page(page_idx)
            for img in page.get_images(full=True):
                xref_pages[img[0]] = xref_pages.get(img[0], 0) + 1
        logo_xrefs = {x for x, n in xref_pages.items() if n >= 3}

        for page_idx in range(len(doc)):
            page = doc.load_page(page_idx)
            try:
                page_text = page.get_text("text") or ""
            except Exception:
                page_text = ""
            slide_title, slide_body = _summarise_text(page_text)
            for img in page.get_images(full=True):
                xref = img[0]
                if xref in logo_xrefs:
                    continue
                try:
                    pix = fitz.Pixmap(doc, xref)
                except Exception:
                    continue
                # Convert CMYK / non-RGB to RGB for PIL compatibility.
                if pix.n - pix.alpha >= 4:
                    try:
                        pix = fitz.Pixmap(fitz.csRGB, pix)
                    except Exception:
                        continue
                try:
                    data = pix.tobytes("png")
                except Exception:
                    continue
                metrics = _image_metrics(data)
                if not metrics:
                    continue
                w, h, _, stddev, white = metrics
                if not _accept(w, h, len(data), stddev, white):
                    continue
                yield data, {
                    "source": pdf_path.name,
                    "kind": "pdf",
                    "page": page_idx + 1,
                    "width": w,
                    "height": h,
                    "ext": "png",
                    "slide_title": slide_title,
                    "slide_body": slide_body,
                }
    finally:
        doc.close()


def extract_pptx(pptx_path: Path):
    """Yield (image_bytes, meta) for each accepted image in a PPTX."""
    try:
        zf = zipfile.ZipFile(pptx_path)
    except Exception as exc:
        print(f"  ! cannot open {pptx_path.name}: {exc}")
        return
    try:
        # Build media -> [slide_text...] index by walking slide rels.
        slide_text_re = re.compile(r"<a:t[^>]*>([^<]*)</a:t>", re.IGNORECASE)
        media_to_slides: dict[str, list[tuple[int, str]]] = {}
        slide_names = sorted(
            n for n in zf.namelist()
            if re.fullmatch(r"ppt/slides/slide\d+\.xml", n)
        )
        for slide_name in slide_names:
            try:
                slide_xml = zf.read(slide_name).decode("utf-8", "ignore")
            except Exception:
                continue
            # Slide index from filename (1-based).
            m = re.search(r"slide(\d+)\.xml$", slide_name)
            slide_idx = int(m.group(1)) if m else 0
            # Collect <a:t> text runs in document order \u2192 first is usually title.
            texts = slide_text_re.findall(slide_xml)
            slide_text = "\n".join(t.strip() for t in texts if t.strip())
            # Find rels file for this slide.
            rels_name = (
                slide_name.replace("ppt/slides/", "ppt/slides/_rels/")
                + ".rels"
            )
            if rels_name not in zf.namelist():
                continue
            try:
                rels_xml = zf.read(rels_name).decode("utf-8", "ignore")
            except Exception:
                continue
            # Find Target=\"../media/imageN.ext\" relationships.
            for tgt in re.findall(r'Target="([^"]+)"', rels_xml):
                if "media/" not in tgt:
                    continue
                # Normalise to ppt/media/<basename>.
                media_name = "ppt/media/" + Path(tgt).name
                media_to_slides.setdefault(media_name, []).append(
                    (slide_idx, slide_text)
                )

        media = [n for n in zf.namelist() if n.startswith("ppt/media/")]
        # Logos repeat across slides; if the same media is referenced on ≥3
        # slides it's almost certainly a header/footer/cover logo.
        logo_media = {
            m for m, refs in media_to_slides.items() if len(refs) >= 3
        }
        for name in media:
            if name in logo_media:
                continue
            ext = Path(name).suffix.lower().lstrip(".")
            if ext not in ("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff",
                           "webp"):
                continue
            try:
                data = zf.read(name)
            except Exception:
                continue
            if ext in ("gif", "bmp", "tif", "tiff", "webp"):
                # Re-encode to PNG for portability.
                try:
                    with Image.open(io.BytesIO(data)) as im:
                        buf = io.BytesIO()
                        im.convert("RGB").save(buf, "PNG")
                        data = buf.getvalue()
                        ext = "png"
                except Exception:
                    continue
            metrics = _image_metrics(data)
            if not metrics:
                continue
            w, h, _, stddev, white = metrics
            if not _accept(w, h, len(data), stddev, white):
                continue
            # Pick the first slide that uses this media; fall back to none.
            slide_refs = media_to_slides.get(name, [])
            if slide_refs:
                slide_idx, slide_text = slide_refs[0]
            else:
                slide_idx, slide_text = 0, ""
            slide_title, slide_body = _summarise_text(slide_text)
            yield data, {
                "source": pptx_path.name,
                "kind": "pptx",
                "media": name,
                "page": slide_idx or None,
                "width": w,
                "height": h,
                "ext": "jpg" if ext in ("jpg", "jpeg") else ext,
                "slide_title": slide_title,
                "slide_body": slide_body,
            }
    finally:
        zf.close()


def main(lectures_dir: Path, book_dir: Path) -> None:
    out_root = book_dir / "figures" / "lectures" / "auto"
    if out_root.exists():
        shutil.rmtree(out_root)
    out_root.mkdir(parents=True, exist_ok=True)

    seen_global: dict[str, dict] = {}   # hash -> manifest entry
    per_chapter: dict[str, list[dict]] = {}
    # Track which source decks each hash appeared in. After the full sweep we
    # delete entries whose hash appeared in 2+ decks (cross-deck logos).
    hash_sources: dict[str, set] = {}

    def assign(meta: dict, chapter_slugs: list[str], data: bytes):
        digest = hashlib.sha256(data).hexdigest()[:16]
        hash_sources.setdefault(digest, set()).add(meta.get("source", ""))
        if digest in seen_global:
            # Already kept; broaden chapter assignment if new chapter matches.
            entry = seen_global[digest]
            for slug in chapter_slugs:
                if slug not in entry["chapters"]:
                    entry["chapters"].append(slug)
                    per_chapter.setdefault(slug, []).append(entry)
            return
        # Decide primary chapter (first in list); copy file there.
        primary = chapter_slugs[0]
        chap_dir = out_root / primary
        chap_dir.mkdir(parents=True, exist_ok=True)
        slug = _slugify(Path(meta["source"]).stem)
        idx = sum(1 for _ in chap_dir.iterdir())
        fname = f"{slug}_{idx:02d}.{meta['ext']}"
        (chap_dir / fname).write_bytes(data)
        entry = {
            "id": digest,
            "file": f"figures/lectures/auto/{primary}/{fname}",
            "chapters": list(chapter_slugs),
            **meta,
        }
        seen_global[digest] = entry
        for slug_ in chapter_slugs:
            per_chapter.setdefault(slug_, []).append(entry)

    # Walk per-lecture folders.
    lect_count = 0
    fig_count = 0
    for sub in sorted(lectures_dir.iterdir()):
        if not sub.is_dir():
            continue
        if sub.name == "topics":
            for f in sorted(sub.iterdir()):
                chapters = TOPICS_TO_CHAPTERS.get(f.name)
                if not chapters:
                    continue
                lect_count += 1
                kept_here = 0
                stream = (extract_pdf(f) if f.suffix.lower() == ".pdf"
                          else extract_pptx(f))
                for data, meta in stream:
                    if kept_here >= MAX_FIGS_PER_LECTURE:
                        break
                    assign(meta, chapters, data)
                    kept_here += 1
                    fig_count += 1
                print(f"  topics/{f.name}: kept {kept_here}")
            continue

        chapters = LECTURE_TO_CHAPTERS.get(sub.name)
        if not chapters:
            print(f"  ? no chapter map for {sub.name!r}; skipping")
            continue

        # Prefer PPTX if both PDF and PPTX exist — usually has higher-res images.
        files = sorted(sub.iterdir())
        # Group by (stem) — keep one source per stem (.pptx wins).
        by_stem: dict[str, Path] = {}
        for f in files:
            if f.suffix.lower() not in (".pdf", ".pptx"):
                continue
            stem = f.stem
            cur = by_stem.get(stem)
            if cur is None or f.suffix.lower() == ".pptx":
                by_stem[stem] = f
        for stem, f in by_stem.items():
            lect_count += 1
            kept_here = 0
            stream = (extract_pdf(f) if f.suffix.lower() == ".pdf"
                      else extract_pptx(f))
            for data, meta in stream:
                if kept_here >= MAX_FIGS_PER_LECTURE:
                    break
                assign(meta, chapters, data)
                kept_here += 1
                fig_count += 1
            print(f"  {sub.name}/{f.name}: kept {kept_here}")

    # Cross-deck logo filter: drop figures whose hash showed up in \u22652 decks.
    cross_logos = {h for h, srcs in hash_sources.items() if len(srcs) >= 2}
    removed = 0
    for digest in list(seen_global.keys()):
        if digest in cross_logos:
            entry = seen_global.pop(digest)
            # Delete the file from disk.
            try:
                (book_dir / entry["file"]).unlink(missing_ok=True)
            except Exception:
                pass
            # Remove from per_chapter listings.
            for slug, lst in per_chapter.items():
                per_chapter[slug] = [e for e in lst if e is not entry]
            removed += 1
    if removed:
        print(f"  cross-deck logo filter removed {removed} duplicates")

    # Write manifest.
    manifest = {
        "lectures_processed": lect_count,
        "figures_extracted": len(seen_global),
        "by_chapter": {
            slug: [e["file"] for e in entries]
            for slug, entries in per_chapter.items()
        },
        "entries": list(seen_global.values()),
    }
    out_manifest = out_root / "manifest.json"
    out_manifest.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    print()
    print(f"Lectures processed: {lect_count}")
    print(f"Unique figures kept: {len(seen_global)}")
    print(f"Manifest: {out_manifest}")


if __name__ == "__main__":
    lectures = Path(
        r"C:\Users\ESOL\OneDrive - Equinor\NTNU-LT-112664\TPG4230\2026\lectures"
    )
    book = Path(__file__).resolve().parents[1] / "books" / (
        "tpg4230_field_development_and_operations_2026")
    main(lectures, book)
