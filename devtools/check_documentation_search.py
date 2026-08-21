#!/usr/bin/env python3
"""Validate NeqSim documentation sources and generated site-search coverage."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple


ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SEARCH_TEMPLATE = DOCS / "search-index.json"
SEARCH_SCRIPT = DOCS / "assets" / "js" / "search.js"
NON_CONTENT_HTML_DIRS = {"_includes", "_layouts"}
NOTEBOOK_LINK_PATTERN = re.compile(
    r"""(?:\[[^\]]*\]\(\s*|href\s*=\s*["'])
    (?P<target>[^)\s"']+\.ipynb(?:[?#][^)\s"']*)?)""",
    re.IGNORECASE | re.VERBOSE,
)


def markdown_files() -> List[Path]:
    """Return every Markdown documentation source that Jekyll should index."""
    return sorted(DOCS.rglob("*.md"))


def content_html_files() -> List[Path]:
    """Return standalone HTML documentation, excluding Jekyll implementation files."""
    return sorted(
        path
        for path in DOCS.rglob("*.html")
        if not any(part in NON_CONTENT_HTML_DIRS for part in path.relative_to(DOCS).parts)
    )


def parse_front_matter(path: Path) -> Tuple[Dict[str, str], str]:
    """Parse the leading flat YAML fields needed by search without external packages."""
    text = path.read_text(encoding="utf-8-sig")
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        raise ValueError("missing leading YAML front matter")

    try:
        end = next(index for index in range(1, len(lines)) if lines[index].strip() == "---")
    except StopIteration as exc:
        raise ValueError("front matter is not closed") from exc

    front_lines = lines[1:end]
    fields: Dict[str, str] = {}
    index = 0
    while index < len(front_lines):
        match = re.match(r"^([A-Za-z_][A-Za-z0-9_-]*):\s*(.*)$", front_lines[index])
        if not match:
            index += 1
            continue
        key, value = match.group(1), match.group(2).strip()
        if value in {">", ">-", ">+", "|", "|-", "|+"}:
            block: List[str] = []
            index += 1
            while index < len(front_lines) and (
                not front_lines[index].strip() or front_lines[index][0].isspace()
            ):
                block.append(front_lines[index].strip())
                index += 1
            value = " ".join(part for part in block if part)
            fields[key] = value
            continue
        if key in {"title", "description", "keywords"} and ": " in value and not value.startswith(
            ("\"", "'")
        ):
            raise ValueError(f"front-matter {key} contains an unquoted colon")
        fields[key] = value.strip("\"'")
        index += 1

    return fields, "\n".join(lines[end + 1 :]).strip()


def markdown_prose(text: str) -> str:
    """Return Markdown text without fenced or inline code."""

    prose: List[str] = []
    fence_character = ""
    fence_length = 0
    for line in text.splitlines():
        fence = re.match(r"^\s*(\x60{3,}|~{3,})", line)
        if fence_character:
            if (
                fence
                and fence.group(1)[0] == fence_character
                and len(fence.group(1)) >= fence_length
            ):
                fence_character = ""
                fence_length = 0
            continue
        if fence:
            fence_character = fence.group(1)[0]
            fence_length = len(fence.group(1))
            continue
        prose.append(re.sub(r"\x60+[^\x60]*\x60+", "", line))
    return "\n".join(prose)


def relative_notebook_link_errors(
    path: Path, text: str, *, strip_markdown_code: bool = True
) -> List[str]:
    """Reject relative notebook links that GitHub Pages cannot publish."""

    errors: List[str] = []
    link_source = markdown_prose(text) if strip_markdown_code else text
    for match in NOTEBOOK_LINK_PATTERN.finditer(link_source):
        target = match.group("target").strip("<>")
        if re.match(r"^[A-Za-z][A-Za-z0-9+.-]*:", target) or target.startswith("//"):
            continue
        errors.append(
            f"{path.relative_to(ROOT)}: relative notebook link {target} is excluded "
            "from GitHub Pages; use an absolute GitHub or Colab URL"
        )
    return errors


def relative_sources(paths: Iterable[Path]) -> List[str]:
    """Return source paths in the same form emitted by Jekyll's ``page.path``."""
    return [path.relative_to(DOCS).as_posix() for path in paths]


def source_audit() -> List[str]:
    """Check source metadata and the contracts that build and consume the index."""
    errors: List[str] = []
    markdown = markdown_files()
    html = content_html_files()

    for path in markdown:
        try:
            fields, body = parse_front_matter(path)
        except ValueError as exc:
            errors.append(f"{path.relative_to(ROOT)}: {exc}")
            continue
        for field in ("title", "description"):
            if not fields.get(field, "").strip():
                errors.append(f"{path.relative_to(ROOT)}: front matter needs a non-empty {field}")
        if not body:
            errors.append(f"{path.relative_to(ROOT)}: documentation body is empty")
        errors.extend(relative_notebook_link_errors(path, body))

    template = SEARCH_TEMPLATE.read_text(encoding="utf-8")
    required_template_contracts = {
        "regular and collection pages": "site.pages | concat: site.documents",
        "rendered Markdown": "assign rendered_content = doc.content | markdownify",
        "complete rendered content": (
            "assign content_text = rendered_content | strip_html | normalize_whitespace"
        ),
        "rendered heading extraction": (
            "assign h_parts = rendered_content | split: '</h'"
        ),
        "inline-markup-safe headings": "remove_first: opening_tag",
        "source-path traceability": '"path": {{ source_path | jsonify }}',
        "title field": '"title": {{ title | jsonify }}',
        "description field": '"description": {{ description | jsonify }}',
        "heading field": '"headings": {{ headings | jsonify }}',
        "content field": '"content": {{ content_text | jsonify }}',
    }
    for contract, marker in required_template_contracts.items():
        if marker not in template:
            errors.append(f"docs/search-index.json: missing {contract} contract")
    if re.search(r"content_text[^\n]*\|\s*truncate", template):
        errors.append("docs/search-index.json: page content must not be truncated")
    if "assign content_text = doc.content | strip_html" in template:
        errors.append("docs/search-index.json: raw Markdown must be rendered before prose extraction")
    if "assign h_parts = doc.content" in template:
        errors.append("docs/search-index.json: headings must be extracted from rendered Markdown")

    for path in html:
        relative = path.relative_to(DOCS).as_posix()
        if relative not in template:
            errors.append(
                f"docs/search-index.json: standalone HTML page {relative} needs a discoverable entry"
            )
        errors.extend(
            relative_notebook_link_errors(
                path,
                path.read_text(encoding="utf-8-sig"),
                strip_markdown_code=False,
            )
        )

    script = SEARCH_SCRIPT.read_text(encoding="utf-8")
    required_script_contracts = {
        "path field": "this.field('path'",
        "complete corpus iteration": "data.forEach(function (doc) {",
        "normalized indexed content": "content: normalizeQuery(doc.content || '')",
        "technical notation normalization": "function normalizeTechnicalNotation(value)",
        "punctuation-safe queries": "function normalizeQuery(query)",
    }
    for contract, marker in required_script_contracts.items():
        if marker not in script:
            errors.append(f"docs/assets/js/search.js: missing {contract} contract")

    if not errors:
        print(
            "Documentation search source audit passed: "
            f"{len(markdown)} Markdown pages and {len(html)} standalone HTML page(s)."
        )
    return errors


def generated_site_audit(site: Path) -> List[str]:
    """Compare a built Jekyll search corpus with every documentation source."""
    errors: List[str] = []
    index_path = site / "search-index.json"
    if not index_path.is_file():
        return [f"{index_path}: generated search index is missing"]

    try:
        entries = json.loads(index_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"{index_path}: invalid JSON: {exc}"]
    if not isinstance(entries, list):
        return [f"{index_path}: expected a JSON array"]

    markdown = markdown_files()
    html = content_html_files()
    expected = set(relative_sources(markdown + html))
    heading_sources = {
        path.relative_to(DOCS).as_posix()
        for path in markdown
        if re.search(
            r"^\\s{0,3}#{1,6}\\s+\\S",
            markdown_prose(parse_front_matter(path)[1]),
            flags=re.MULTILINE,
        )
    }
    actual: Dict[str, dict] = {}
    urls: Dict[str, int] = {}
    for position, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"search entry {position}: expected an object")
            continue
        source = str(entry.get("path", "")).lstrip("/")
        url = str(entry.get("url", ""))
        if source:
            if source in actual:
                errors.append(f"duplicate search source: {source}")
            actual[source] = entry
        if url:
            urls[url] = urls.get(url, 0) + 1
        for field in ("url", "title", "description", "path", "content"):
            if not str(entry.get(field, "")).strip():
                errors.append(f"search entry {position} ({source or url}): empty {field}")
        if source in heading_sources and not str(entry.get("headings", "")).strip():
            errors.append(
                f"search entry {position} ({source}): empty headings for heading-bearing source"
            )

    missing = sorted(expected - set(actual))
    for source in missing:
        errors.append(f"generated search index is missing documentation source: {source}")
    for url, count in sorted(urls.items()):
        if count > 1:
            errors.append(f"duplicate search URL ({count} entries): {url}")

    exactly_cut_off = [
        source
        for source, entry in actual.items()
        if len(str(entry.get("content", ""))) == 10000
    ]
    for source in exactly_cut_off:
        errors.append(f"search content still appears truncated at 10,000 characters: {source}")

    if not errors:
        total_chars = sum(len(str(entry.get("content", ""))) for entry in entries)
        print(
            "Generated search-index audit passed: "
            f"{len(entries)} unique entries, {len(expected)} documentation sources, "
            f"{total_chars:,} searchable content characters."
        )
    return errors


def report(errors: Sequence[str]) -> int:
    """Print findings and return a process exit status."""
    if not errors:
        return 0
    print("Documentation search audit failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--site",
        type=Path,
        help="also validate a generated Jekyll destination such as _site",
    )
    args = parser.parse_args()

    errors = source_audit()
    if args.site:
        errors.extend(generated_site_audit(args.site.resolve()))
    return report(errors)


if __name__ == "__main__":
    raise SystemExit(main())
