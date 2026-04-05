"""
Citation Discovery — Suggest missing references using Semantic Scholar API.

Extracts key phrases from the manuscript abstract/introduction, queries
Semantic Scholar for highly-cited papers, and reports potentially-missing
references.

No API key required (free tier: 100 requests/5 minutes).

Usage::

    from tools.citation_discovery import suggest_citations

    suggestions = suggest_citations("papers/my_paper/")
    for s in suggestions:
        print(f"  {s['title']} ({s['year']}) — cited {s['citation_count']} times")
"""

import re
import json
import time
from pathlib import Path
from typing import Dict, List, Optional

try:
    import requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False


_S2_SEARCH_URL = "https://api.semanticscholar.org/graph/v1/paper/search"
_S2_FIELDS = "title,authors,year,citationCount,externalIds,abstract,url"
_MIN_CITATIONS = 10  # Only suggest well-cited papers


def _extract_search_terms(paper_dir):
    """Extract search queries from the manuscript.

    Uses: title, abstract keywords, and research questions from plan.json.
    """
    paper_dir = Path(paper_dir)
    queries = []

    # From plan.json
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        plan = json.loads(plan_file.read_text(encoding="utf-8"))
        title = plan.get("title", "")
        if title:
            queries.append(title)

        # Research questions as search queries
        for rq in plan.get("research_questions", []):
            q = rq.get("question", "")
            if q and "TODO" not in q:
                queries.append(q)

    # From paper.md abstract
    paper_file = paper_dir / "paper.md"
    if paper_file.exists():
        text = paper_file.read_text(encoding="utf-8")

        # Extract abstract
        abstract_match = re.search(
            r'## Abstract\s*\n(.*?)(?=\n## |\n---)',
            text, re.DOTALL | re.IGNORECASE)
        if abstract_match:
            abstract = abstract_match.group(1).strip()
            # Remove TODO placeholders
            abstract = re.sub(r'TODO.*', '', abstract).strip()
            if len(abstract) > 50:
                queries.append(abstract[:300])

        # Extract keywords
        kw_match = re.search(
            r'## Keywords\s*\n(.*?)(?=\n## |\n---)',
            text, re.DOTALL | re.IGNORECASE)
        if kw_match:
            kw_text = kw_match.group(1).strip()
            if "TODO" not in kw_text and len(kw_text) > 5:
                queries.append(kw_text)

    return queries


def _get_existing_refs(paper_dir):
    """Get set of existing reference titles (lowercase) from refs.bib."""
    bib_file = Path(paper_dir) / "refs.bib"
    existing = set()
    if bib_file.exists():
        bib_text = bib_file.read_text(encoding="utf-8")
        for match in re.finditer(r'title\s*=\s*\{(.+?)\}', bib_text, re.DOTALL):
            title = match.group(1).replace("{", "").replace("}", "").strip().lower()
            existing.add(title)
    return existing


def _search_semantic_scholar(query, limit=10):
    """Search Semantic Scholar API for papers matching a query."""
    if not _HAS_REQUESTS:
        return []

    params = {
        "query": query[:200],  # API limit
        "limit": limit,
        "fields": _S2_FIELDS,
    }

    try:
        resp = requests.get(_S2_SEARCH_URL, params=params, timeout=15)
        if resp.status_code == 429:
            # Rate limited — wait and retry once
            time.sleep(3)
            resp = requests.get(_S2_SEARCH_URL, params=params, timeout=15)

        if resp.status_code != 200:
            return []

        data = resp.json()
        return data.get("data", [])
    except (requests.RequestException, json.JSONDecodeError):
        return []


def suggest_citations(paper_dir, max_suggestions=15, min_citations=None):
    """Suggest potentially-missing citations for a manuscript.

    Parameters
    ----------
    paper_dir : str or Path
        Path to the paper project directory.
    max_suggestions : int
        Maximum number of suggestions to return.
    min_citations : int, optional
        Minimum citation count filter (default: 10).

    Returns
    -------
    dict
        Report with 'suggestions' list and metadata.
    """
    if not _HAS_REQUESTS:
        return {
            "error": "requests package not installed. Run: pip install requests",
            "suggestions": [],
        }

    paper_dir = Path(paper_dir)
    if min_citations is None:
        min_citations = _MIN_CITATIONS

    queries = _extract_search_terms(paper_dir)
    if not queries:
        return {
            "error": "No search terms found. Add a title to plan.json or content to paper.md.",
            "suggestions": [],
        }

    existing_titles = _get_existing_refs(paper_dir)

    # Search for each query
    all_papers = {}
    for query in queries[:4]:  # Limit to 4 queries to stay within rate limits
        papers = _search_semantic_scholar(query, limit=15)
        for paper in papers:
            pid = paper.get("paperId")
            if pid and pid not in all_papers:
                all_papers[pid] = paper
        time.sleep(1.0)  # Rate limiting

    # Filter and rank
    suggestions = []
    for pid, paper in all_papers.items():
        title = (paper.get("title") or "").strip()
        if not title:
            continue

        # Skip papers already in refs.bib
        if title.lower() in existing_titles:
            continue

        citation_count = paper.get("citationCount", 0) or 0
        if citation_count < min_citations:
            continue

        year = paper.get("year")

        # Format authors
        authors = paper.get("authors", [])
        if len(authors) > 2:
            author_str = f"{authors[0].get('name', '?')} et al."
        elif len(authors) == 2:
            author_str = f"{authors[0].get('name', '?')} and {authors[1].get('name', '?')}"
        elif authors:
            author_str = authors[0].get("name", "?")
        else:
            author_str = "Unknown"

        # DOI
        ext_ids = paper.get("externalIds", {}) or {}
        doi = ext_ids.get("DOI", "")

        suggestions.append({
            "title": title,
            "authors": author_str,
            "year": year,
            "citation_count": citation_count,
            "doi": doi,
            "url": paper.get("url", ""),
            "relevance_source": "semantic_scholar",
        })

    # Sort by citation count (most cited first)
    suggestions.sort(key=lambda x: x["citation_count"], reverse=True)
    suggestions = suggestions[:max_suggestions]

    return {
        "paper_dir": str(paper_dir),
        "queries_used": queries[:4],
        "total_candidates": len(all_papers),
        "filtered_by_citations": min_citations,
        "existing_refs": len(existing_titles),
        "suggestions": suggestions,
    }


def format_bibtex_suggestion(suggestion):
    """Format a suggestion as a BibTeX entry for easy copy-paste."""
    # Create a key from first author last name + year
    author = suggestion["authors"].split(",")[0].split(" ")[-1].split(".")[0]
    year = suggestion.get("year", "XXXX") or "XXXX"
    key = f"{author}{year}"

    doi_line = f"  doi = {{{suggestion['doi']}}},\n" if suggestion.get("doi") else ""
    url_line = f"  url = {{{suggestion['url']}}},\n" if suggestion.get("url") else ""

    return (
        f"@article{{{key},\n"
        f"  author = {{{suggestion['authors']}}},\n"
        f"  title = {{{{{suggestion['title']}}}}},\n"
        f"  year = {{{year}}},\n"
        f"{doi_line}{url_line}"
        f"  note = {{Cited {suggestion['citation_count']} times}}\n"
        f"}}\n"
    )


def print_suggestions(report):
    """Print formatted citation suggestions."""
    if "error" in report and not report.get("suggestions"):
        print(f"Error: {report['error']}")
        return

    suggestions = report.get("suggestions", [])
    print("=" * 60)
    print("CITATION DISCOVERY REPORT")
    print("=" * 60)
    print(f"  Queries: {len(report.get('queries_used', []))}")
    print(f"  Candidates found: {report.get('total_candidates', 0)}")
    print(f"  Already in refs.bib: {report.get('existing_refs', 0)}")
    print(f"  Suggestions (≥{report.get('filtered_by_citations', _MIN_CITATIONS)} citations): "
          f"{len(suggestions)}")
    print()

    if not suggestions:
        print("  No new citation suggestions found.")
        return

    for i, s in enumerate(suggestions, 1):
        doi_str = f"  DOI: {s['doi']}" if s.get('doi') else ""
        print(f"  {i:2d}. {s['title']}")
        print(f"      {s['authors']} ({s['year']}) — {s['citation_count']:,} citations{doi_str}")
        print()

    # Print BibTeX block
    print("-" * 60)
    print("BibTeX entries (copy to refs.bib):")
    print("-" * 60)
    for s in suggestions[:5]:
        print(format_bibtex_suggestion(s))
