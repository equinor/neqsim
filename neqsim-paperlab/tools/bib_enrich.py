"""DOI / Crossref enrichment for PaperLab `refs.bib`.

For every entry in a BibTeX file:

* If a `doi` field is present, validate it via the Crossref REST API
  (https://api.crossref.org/works/<doi>) and report mismatches in title,
  year, or author surnames.
* If `doi` is missing, query Crossref by title + first author + year and
  attach the top match (only when score > threshold and surname matches).

The enricher writes the result to ``refs.enriched.bib`` next to the input
file by default, leaving the original untouched. Pass ``--in-place`` to
overwrite. A JSON report (``refs.enrichment_report.json``) summarizes
what changed.

Network failures are non-fatal: entries are kept as-is and the report
records the error.

Usage
-----
    python paperflow.py book-enrich-bib books/<book_dir>
    python paperflow.py book-enrich-bib books/<book_dir> --in-place --min-score 80
"""
from __future__ import annotations

import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = "neqsim-paperlab/1.0 (mailto:postmaster@equinor.com)"
CROSSREF_BASE = "https://api.crossref.org"
DEFAULT_MIN_SCORE = 70.0


# ---------------------------------------------------------------------------
# Minimal BibTeX parser (entry-level only — preserves field text verbatim)
# ---------------------------------------------------------------------------

ENTRY_RE = re.compile(r"@(\w+)\s*\{\s*([^,]+),", re.IGNORECASE)


def _parse_bib(path: Path) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    entries: list[dict] = []
    i = 0
    while True:
        m = ENTRY_RE.search(text, i)
        if not m:
            break
        start = m.start()
        # Walk braces to find the matching closing brace
        depth = 0
        j = m.end() - 1  # at the comma; backtrack to opening brace
        # Find the opening brace right before the entry key
        k = text.rfind("{", start, m.end())
        if k == -1:
            break
        depth = 1
        j = k + 1
        while j < len(text) and depth > 0:
            c = text[j]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
            j += 1
        body = text[k + 1: j - 1]
        entries.append({
            "type": m.group(1).lower(),
            "key": m.group(2).strip(),
            "raw": text[start:j],
            "body": body,
            "span": (start, j),
        })
        i = j
    return entries


FIELD_RE = re.compile(r"(?ms)^\s*(\w+)\s*=\s*[\{\"](.*?)[\}\"]\s*,?\s*$")


def _fields(entry: dict) -> dict[str, str]:
    out: dict[str, str] = {}
    # Skip the first line (key,) — start after the comma
    body = entry["body"]
    after_key = body.split(",", 1)[1] if "," in body else body
    for m in FIELD_RE.finditer(after_key):
        out[m.group(1).lower()] = m.group(2).strip()
    return out


def _set_field(entry: dict, name: str, value: str) -> None:
    """Add or replace a single field in the entry's raw text."""
    fld = name.lower()
    pattern = re.compile(rf"(?ms)^(\s*){fld}\s*=\s*[\{{\"].*?[\}}\"](\s*,?\s*)$")
    new_line = f"  {fld} = {{{value}}},"
    raw = entry["raw"]
    if pattern.search(raw):
        entry["raw"] = pattern.sub(lambda _m: new_line, raw)
    else:
        # Insert before the final closing brace
        entry["raw"] = raw[:-1].rstrip().rstrip(",") + ",\n" + new_line + "\n}"


# ---------------------------------------------------------------------------
# Crossref client
# ---------------------------------------------------------------------------

def _http_json(url: str, timeout: float = 10.0) -> dict | None:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
        print(f"[bib_enrich] HTTP {url}: {exc}")
        return None


def crossref_lookup_doi(doi: str) -> dict | None:
    return _http_json(f"{CROSSREF_BASE}/works/{urllib.parse.quote(doi)}")


def crossref_search(title: str, author: str = "", year: str = "") -> list[dict]:
    params = {"query.title": title, "rows": "3"}
    if author:
        params["query.author"] = author
    q = urllib.parse.urlencode(params)
    data = _http_json(f"{CROSSREF_BASE}/works?{q}")
    if not data:
        return []
    items = data.get("message", {}).get("items", [])
    if year:
        # Promote items matching year by re-sorting
        def _y(it: dict) -> int:
            try:
                return int(it.get("issued", {}).get("date-parts", [[0]])[0][0])
            except Exception:
                return 0
        items.sort(key=lambda it: 0 if _y(it) == int(year) else 1)
    return items


# ---------------------------------------------------------------------------
# Matching utilities
# ---------------------------------------------------------------------------

def _score(a: str, b: str) -> float:
    """Token-Jaccard percentage."""
    sa = {t for t in re.findall(r"\w+", a.lower()) if len(t) > 2}
    sb = {t for t in re.findall(r"\w+", b.lower()) if len(t) > 2}
    if not sa or not sb:
        return 0.0
    return 100.0 * len(sa & sb) / len(sa | sb)


def _first_author_surname(entry_author: str) -> str:
    if not entry_author:
        return ""
    first = entry_author.split(" and ")[0].strip()
    if "," in first:
        return first.split(",")[0].strip()
    return first.split()[-1] if first.split() else ""


def _crossref_title(item: dict) -> str:
    titles = item.get("title") or []
    return titles[0] if titles else ""


def _crossref_authors(item: dict) -> list[str]:
    return [a.get("family", "") for a in item.get("author", []) if a.get("family")]


def _crossref_year(item: dict) -> str:
    parts = item.get("issued", {}).get("date-parts", [[]])
    return str(parts[0][0]) if parts and parts[0] else ""


# ---------------------------------------------------------------------------
# Per-entry enrichment
# ---------------------------------------------------------------------------

def enrich_entry(entry: dict, min_score: float = DEFAULT_MIN_SCORE) -> dict:
    """Return a report dict; mutates `entry["raw"]` when changes are made."""
    fields = _fields(entry)
    key = entry["key"]
    report = {"key": key, "action": "none", "details": ""}

    # Skip entry types where DOI lookup is not meaningful
    if entry["type"] in ("misc", "manual", "techreport", "phdthesis", "mastersthesis"):
        if not fields.get("doi"):
            report["action"] = "skipped"
            report["details"] = f"entry type {entry['type']} — no DOI lookup"
            return report

    title = fields.get("title", "")
    year = fields.get("year", "")
    author = fields.get("author", "")
    surname = _first_author_surname(author)
    doi = fields.get("doi", "")

    if doi:
        item = crossref_lookup_doi(doi)
        if item is None:
            report["action"] = "doi_unreachable"
            return report
        msg = item.get("message", {})
        cr_title = _crossref_title(msg)
        cr_year = _crossref_year(msg)
        cr_authors = _crossref_authors(msg)
        title_score = _score(title, cr_title) if title and cr_title else 100.0
        problems = []
        if title and title_score < min_score:
            problems.append(f"title mismatch ({title_score:.0f}%)")
        if year and cr_year and year != cr_year:
            problems.append(f"year {year} vs Crossref {cr_year}")
        if surname and cr_authors and not any(surname.lower() == s.lower() for s in cr_authors):
            problems.append(f"first-author surname '{surname}' not in {cr_authors[:3]}")
        if problems:
            report["action"] = "doi_warning"
            report["details"] = "; ".join(problems)
        else:
            report["action"] = "doi_validated"
        return report

    # No DOI present — search Crossref
    if not title or not year:
        report["action"] = "missing_fields"
        report["details"] = "no DOI, and missing title or year"
        return report
    candidates = crossref_search(title, surname, year)
    if not candidates:
        report["action"] = "no_match"
        return report
    best = candidates[0]
    cr_title = _crossref_title(best)
    title_score = _score(title, cr_title)
    cr_authors = _crossref_authors(best)
    cr_year = _crossref_year(best)
    surname_ok = surname and any(surname.lower() == s.lower() for s in cr_authors)
    year_ok = (cr_year == year) or not year
    if title_score >= min_score and surname_ok and year_ok and best.get("DOI"):
        _set_field(entry, "doi", best["DOI"])
        url = best.get("URL")
        if url and "url" not in fields:
            _set_field(entry, "url", url)
        report["action"] = "doi_added"
        report["details"] = f"DOI={best['DOI']} (title score {title_score:.0f}%)"
    else:
        report["action"] = "no_confident_match"
        report["details"] = f"best score {title_score:.0f}%, surname_ok={surname_ok}, year_ok={year_ok}"
    return report


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def enrich_bibfile(bib_path, in_place: bool = False, min_score: float = DEFAULT_MIN_SCORE,
                   delay: float = 0.2, limit: int | None = None) -> Path:
    bib_path = Path(bib_path).resolve()
    if not bib_path.exists():
        raise FileNotFoundError(bib_path)

    entries = _parse_bib(bib_path)
    print(f"[bib_enrich] {bib_path.name}: {len(entries)} entries")
    reports: list[dict] = []

    for n, entry in enumerate(entries, 1):
        if limit and n > limit:
            break
        try:
            r = enrich_entry(entry, min_score=min_score)
        except Exception as exc:
            r = {"key": entry["key"], "action": "error", "details": str(exc)}
        reports.append(r)
        print(f"  [{n}/{len(entries)}] {entry['key']:40s} {r['action']}"
              + (f"  — {r['details']}" if r['details'] else ""))
        time.sleep(delay)

    # Reassemble the file
    original = bib_path.read_text(encoding="utf-8")
    new_text = original
    # Replace from the bottom up so spans stay valid
    for entry in sorted(entries, key=lambda e: e["span"][0], reverse=True):
        s, e = entry["span"]
        new_text = new_text[:s] + entry["raw"] + new_text[e:]

    out = bib_path if in_place else bib_path.with_name("refs.enriched.bib")
    out.write_text(new_text, encoding="utf-8")
    report_path = bib_path.with_name("refs.enrichment_report.json")
    summary = {
        "input": str(bib_path),
        "output": str(out),
        "n_entries": len(entries),
        "by_action": {a: sum(1 for r in reports if r["action"] == a)
                      for a in sorted({r["action"] for r in reports})},
        "details": reports,
    }
    report_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(f"[bib_enrich] wrote: {out}")
    print(f"[bib_enrich] report: {report_path}")
    print(f"[bib_enrich] summary: {summary['by_action']}")
    return out
