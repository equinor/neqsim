"""
Trending Topics Scanner — Discover paper opportunities from web research.

Instead of scanning the static NeqSim codebase (which produces the same
results every run), this module queries academic search APIs for recent
trending papers in NeqSim's capability domains and identifies where
NeqSim could contribute novel scientific work.

It produces a *daily pick* — one paper opportunity per day — by rotating
through discovered trends using a date-based seed.  A local history file
prevents the same topic from being suggested twice within a configurable
window (default: 90 days).

Usage::

    from tools.trending_topics import daily_suggestion, scan_trending

    # One suggestion per day
    pick = daily_suggestion()
    print(pick["title"])

    # Full trending scan (all domains)
    trends = scan_trending(top_n=20)
    for t in trends:
        print(f"  [{t['domain']}] {t['title']}  (trend_score: {t['trend_score']})")
"""

import hashlib
import json
import os
import random
import time
from collections import Counter
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    import requests

    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False

# ── Semantic Scholar API ──────────────────────────────────────────────
_S2_SEARCH_URL = "https://api.semanticscholar.org/graph/v1/paper/search"
_S2_FIELDS = "title,authors,year,citationCount,url,abstract,publicationDate"

# ── NeqSim capability domains and search queries ─────────────────────
# Each domain maps to search queries that find trending work in areas where
# NeqSim has or could have strong computational capabilities.
NEQSIM_DOMAINS = {
    "Thermodynamic EOS": {
        "queries": [
            "cubic equation of state prediction recent advances",
            "SRK Peng-Robinson EOS mixing rules new",
            "CPA equation of state association modeling",
            "GERG-2008 natural gas thermodynamic properties",
        ],
        "neqsim_classes": [
            "SystemSrkEos", "SystemPrEos", "SystemSrkCPAstatoil",
            "SystemUMRPRUMCEos",
        ],
        "journals": ["fluid_phase_equilibria", "iecr", "jced"],
        "keywords": ["equation of state", "thermodynamic model", "phase equilibrium",
                      "mixing rule", "binary interaction parameter"],
    },
    "CO2 Capture and Storage": {
        "queries": [
            "CO2 capture process simulation thermodynamic",
            "carbon capture solvent modeling equation of state",
            "CO2 pipeline transport dense phase properties",
            "CO2 injection well integrity phase behavior",
        ],
        "neqsim_classes": [
            "CO2InjectionWellAnalyzer", "TransientWellbore",
            "ImpurityMonitor", "ProcessSystem",
        ],
        "journals": ["computers_chem_eng", "iecr", "energy_fuels"],
        "keywords": ["CO2 capture", "CCS", "carbon storage", "amine",
                      "dense phase CO2", "impurity"],
    },
    "Hydrogen Systems": {
        "queries": [
            "hydrogen blending natural gas pipeline thermodynamic",
            "hydrogen phase behavior equation of state",
            "green hydrogen electrolysis process simulation",
            "hydrogen storage materials thermodynamic modeling",
        ],
        "neqsim_classes": [
            "SystemSrkEos", "ProcessSystem", "PipeBeggsAndBrills",
        ],
        "journals": ["computers_chem_eng", "energy_fuels", "aiche"],
        "keywords": ["hydrogen", "H2 blending", "power-to-gas",
                      "hydrogen pipeline", "electrolysis"],
    },
    "Multiphase Flow": {
        "queries": [
            "multiphase pipe flow modeling slug prediction",
            "subsea pipeline flow assurance simulation",
            "two-phase pressure drop correlation new",
            "wet gas metering multiphase flow",
        ],
        "neqsim_classes": [
            "PipeBeggsAndBrills", "TwoFluidPipe", "AdiabaticPipe",
        ],
        "journals": ["computers_chem_eng", "spe"],
        "keywords": ["multiphase flow", "pipeline", "slug flow",
                      "pressure drop", "flow assurance"],
    },
    "Hydrate Management": {
        "queries": [
            "gas hydrate formation prediction thermodynamic",
            "hydrate inhibitor MEG dosing modeling",
            "hydrate risk flow assurance subsea",
            "hydrate phase equilibria equation of state",
        ],
        "neqsim_classes": [
            "HydrateFormationTemperatureOps", "SystemSrkCPAstatoil",
        ],
        "journals": ["fluid_phase_equilibria", "energy_fuels", "spe"],
        "keywords": ["hydrate", "inhibitor", "MEG", "THI",
                      "hydrate formation", "flow assurance"],
    },
    "Process Simulation": {
        "queries": [
            "process simulation open source chemical engineering",
            "digital twin process plant thermodynamic",
            "modular process simulation framework",
            "dynamic process simulation distillation",
        ],
        "neqsim_classes": [
            "ProcessSystem", "ProcessModel", "DistillationColumn",
            "Separator", "Compressor",
        ],
        "journals": ["computers_chem_eng", "aiche"],
        "keywords": ["process simulation", "digital twin", "flowsheet",
                      "steady state", "dynamic simulation"],
    },
    "PVT and Reservoir Fluids": {
        "queries": [
            "PVT simulation reservoir fluid characterization",
            "plus fraction characterization C7+ petroleum",
            "black oil correlations machine learning improvement",
            "gas condensate phase behavior modeling",
        ],
        "neqsim_classes": [
            "SaturationPressure", "ConstantMassExpansion",
            "ConstantVolumeDepletion",
        ],
        "journals": ["fluid_phase_equilibria", "spe", "energy_fuels"],
        "keywords": ["PVT", "reservoir fluid", "C7+", "characterization",
                      "saturation pressure", "GOR"],
    },
    "Heat Exchanger Design": {
        "queries": [
            "heat exchanger network optimization pinch analysis",
            "shell tube heat exchanger simulation fouling",
            "LNG heat exchanger cryogenic process simulation",
        ],
        "neqsim_classes": [
            "HeatExchanger", "Heater", "Cooler", "PinchAnalysis",
        ],
        "journals": ["computers_chem_eng", "aiche"],
        "keywords": ["heat exchanger", "pinch analysis", "LMTD",
                      "heat integration", "fouling"],
    },
    "Gas Processing": {
        "queries": [
            "natural gas dehydration TEG simulation",
            "NGL recovery turboexpander cryogenic",
            "acid gas removal amine scrubbing simulation",
            "gas sweetening MDEA process model",
        ],
        "neqsim_classes": [
            "DistillationColumn", "Separator", "WaterStripperColumn",
        ],
        "journals": ["computers_chem_eng", "energy_fuels"],
        "keywords": ["gas processing", "dehydration", "TEG", "NGL",
                      "amine", "acid gas", "sweetening"],
    },
    "Electrolyte Systems": {
        "queries": [
            "electrolyte thermodynamic model produced water",
            "scale prediction oilfield brine modeling",
            "CO2 solubility brine equation of state",
        ],
        "neqsim_classes": [
            "SystemElectrolyteCPAstatoil", "SystemFurstElectrolyteEos",
        ],
        "journals": ["fluid_phase_equilibria", "iecr", "jced"],
        "keywords": ["electrolyte", "brine", "scale", "ion",
                      "produced water", "solubility"],
    },
}

# ── History file location ─────────────────────────────────────────────
_DEFAULT_HISTORY_DIR = Path(__file__).resolve().parent.parent / "papers" / "_research_scan"


def _search_semantic_scholar(query, year_from=None, limit=10):
    """Search Semantic Scholar for recent papers.

    Parameters
    ----------
    query : str
        Search query string.
    year_from : int, optional
        Only return papers from this year onward.
    limit : int
        Max results to return.

    Returns
    -------
    list[dict]
        Paper metadata dicts with title, authors, year, citationCount, url, abstract.
    """
    if not _HAS_REQUESTS:
        return []

    params = {
        "query": query[:200],
        "limit": limit,
        "fields": _S2_FIELDS,
    }
    if year_from:
        params["year"] = f"{year_from}-"

    try:
        resp = requests.get(_S2_SEARCH_URL, params=params, timeout=15)
        if resp.status_code == 429:
            time.sleep(3)
            resp = requests.get(_S2_SEARCH_URL, params=params, timeout=15)
        if resp.status_code != 200:
            return []
        data = resp.json()
        return data.get("data", [])
    except (requests.RequestException, json.JSONDecodeError, ValueError):
        return []


def _compute_trend_score(paper, domain_keywords):
    """Score a paper for trendiness and NeqSim relevance (0-100).

    Signals:
    - Recency (recent papers score higher)
    - Citation velocity (citations per year since publication)
    - Keyword overlap with NeqSim domain
    - Abstract mentions computational/simulation/modeling
    """
    score = 0.0

    # Recency bonus
    pub_date = paper.get("publicationDate") or ""
    year = paper.get("year") or 2020
    if pub_date:
        try:
            pub = datetime.strptime(pub_date[:10], "%Y-%m-%d")
            days_ago = (datetime.now() - pub).days
            if days_ago < 180:
                score += 25
            elif days_ago < 365:
                score += 18
            elif days_ago < 730:
                score += 10
        except ValueError:
            pass
    elif year >= datetime.now().year:
        score += 20
    elif year >= datetime.now().year - 1:
        score += 12

    # Citation velocity
    citations = paper.get("citationCount") or 0
    years_since = max(1, datetime.now().year - (year or 2020))
    velocity = citations / years_since
    if velocity > 20:
        score += 20
    elif velocity > 10:
        score += 15
    elif velocity > 3:
        score += 10
    elif velocity > 0:
        score += 5

    # Keyword overlap with NeqSim domain
    title = (paper.get("title") or "").lower()
    abstract = (paper.get("abstract") or "").lower()
    text = title + " " + abstract

    kw_hits = sum(1 for kw in domain_keywords if kw.lower() in text)
    score += min(20, kw_hits * 5)

    # Computational / simulation / modeling mentions
    comp_keywords = ["simulation", "modeling", "computational", "numerical",
                     "software", "open source", "calculation", "prediction",
                     "equation of state", "thermodynamic model"]
    comp_hits = sum(1 for kw in comp_keywords if kw in text)
    score += min(15, comp_hits * 5)

    return min(100, score)


def _generate_opportunity(paper, domain_name, domain_info, trend_score):
    """Convert a trending paper + NeqSim domain into a paper opportunity.

    Returns a dict describing what NeqSim paper could be written in response
    to this trend.
    """
    title_words = (paper.get("title") or "Unknown").strip()

    # Infer paper type from abstract content
    abstract = (paper.get("abstract") or "").lower()
    if any(w in abstract for w in ["benchmark", "comparison", "evaluate", "versus"]):
        paper_type = "comparative"
    elif any(w in abstract for w in ["novel", "new method", "algorithm", "solver"]):
        paper_type = "method"
    elif any(w in abstract for w in ["experimental", "measurement", "data"]):
        paper_type = "characterization"
    else:
        paper_type = "application"

    # Build NeqSim-specific opportunity title
    type_prefix = {
        "method": "A Computational Study Using NeqSim:",
        "comparative": "Benchmarking NeqSim for",
        "characterization": "Validation of NeqSim Models for",
        "application": "NeqSim Application to",
    }
    opp_title = f"{type_prefix.get(paper_type, 'NeqSim Study:')} {domain_name}"

    # What NeqSim improvements this work would drive
    improvements = []
    if paper_type == "comparative":
        improvements.append(f"Benchmark {', '.join(domain_info['neqsim_classes'][:2])} against published data")
    if paper_type == "method":
        improvements.append(f"Improve algorithms in {', '.join(domain_info['neqsim_classes'][:2])}")
    if any(w in abstract for w in ["experimental", "data", "measurement"]):
        improvements.append("Add new experimental data validation to test suite")
    improvements.append(f"Strengthen NeqSim's {domain_name} capabilities")

    # Authors from the inspiring paper
    inspiring_authors = []
    for author in (paper.get("authors") or [])[:3]:
        name = author.get("name", "")
        if name:
            inspiring_authors.append(name)

    return {
        "title": opp_title,
        "domain": domain_name,
        "paper_type": paper_type,
        "trend_score": round(trend_score),
        "inspiring_paper": {
            "title": title_words,
            "authors": inspiring_authors,
            "year": paper.get("year"),
            "citations": paper.get("citationCount", 0),
            "url": paper.get("url", ""),
        },
        "suggested_journals": domain_info["journals"][:3],
        "neqsim_classes": domain_info["neqsim_classes"],
        "neqsim_improvement": improvements,
        "research_angle": _infer_research_angle(paper, domain_name, domain_info),
        "effort": "medium",
        "effort_weeks": 8,
    }


def _infer_research_angle(paper, domain_name, domain_info):
    """Suggest a concrete research angle combining the trend with NeqSim.

    Returns a 1-2 sentence suggestion.
    """
    abstract = (paper.get("abstract") or "").lower()
    title = (paper.get("title") or "").lower()
    text = title + " " + abstract

    angles = []

    # Look for specific computational gaps NeqSim could fill
    if "machine learning" in text or "neural network" in text or "data-driven" in text:
        angles.append(
            f"Compare physics-based NeqSim predictions ({', '.join(domain_info['neqsim_classes'][:2])}) "
            f"against the data-driven approach in this work — hybrid models are a hot topic."
        )
    if "experimental" in text or "measurement" in text:
        angles.append(
            f"Use the new experimental data from this paper to validate and improve "
            f"NeqSim's {domain_name} models."
        )
    if "review" in text or "survey" in text:
        angles.append(
            f"Position NeqSim within the landscape surveyed by this review — "
            f"demonstrate capabilities and identify improvement areas."
        )
    if "open source" in text or "software" in text:
        angles.append(
            f"Benchmark NeqSim against this software/approach for {domain_name} problems."
        )
    if "industrial" in text or "plant" in text or "field" in text:
        angles.append(
            f"Apply NeqSim to the industrial scenario described — "
            f"validate against their reported data."
        )

    # Default angle
    if not angles:
        angles.append(
            f"Build on this work by applying NeqSim's {domain_name} capabilities "
            f"({', '.join(domain_info['neqsim_classes'][:2])}) to extend or validate their findings."
        )

    return angles[0]


def scan_trending(top_n=20, year_lookback=2, rate_limit_delay=1.2):
    """Scan all NeqSim domains for trending paper opportunities.

    Parameters
    ----------
    top_n : int
        Maximum total opportunities to return.
    year_lookback : int
        How many years back to search (default: 2).
    rate_limit_delay : float
        Seconds to wait between API calls (Semantic Scholar rate limit).

    Returns
    -------
    dict
        Report with 'opportunities', 'summary', 'metadata'.
    """
    year_from = datetime.now().year - year_lookback
    all_opportunities = []
    domains_searched = 0
    queries_made = 0
    api_errors = 0

    for domain_name, domain_info in NEQSIM_DOMAINS.items():
        # Pick one query per domain (rotate by date so different queries on different days)
        day_index = date.today().toordinal()
        query_idx = (day_index + hash(domain_name)) % len(domain_info["queries"])
        query = domain_info["queries"][query_idx]

        papers = _search_semantic_scholar(query, year_from=year_from, limit=10)
        queries_made += 1

        if not papers:
            api_errors += 1
            if rate_limit_delay > 0:
                time.sleep(rate_limit_delay)
            continue

        # Score and rank papers
        for paper in papers:
            if not paper.get("title"):
                continue
            trend_score = _compute_trend_score(paper, domain_info["keywords"])
            if trend_score >= 30:  # minimum threshold
                opp = _generate_opportunity(paper, domain_name, domain_info, trend_score)
                all_opportunities.append(opp)

        domains_searched += 1
        if rate_limit_delay > 0:
            time.sleep(rate_limit_delay)

    # Sort by trend score and deduplicate similar titles
    all_opportunities.sort(key=lambda o: o["trend_score"], reverse=True)
    seen_titles = set()
    deduped = []
    for opp in all_opportunities:
        # Simple dedup on inspiring paper title
        key = (opp["inspiring_paper"]["title"] or "")[:50].lower()
        if key not in seen_titles:
            seen_titles.add(key)
            deduped.append(opp)

    deduped = deduped[:top_n]

    # Summary
    domain_counts = Counter(o["domain"] for o in deduped)
    type_counts = Counter(o["paper_type"] for o in deduped)

    return {
        "metadata": {
            "scan_date": datetime.now().isoformat()[:10],
            "year_lookback": year_lookback,
            "domains_searched": domains_searched,
            "queries_made": queries_made,
            "api_errors": api_errors,
            "scanner_type": "trending_topics",
        },
        "summary": {
            "total_opportunities": len(deduped),
            "by_domain": dict(domain_counts.most_common()),
            "by_paper_type": dict(type_counts.most_common()),
            "top_score": deduped[0]["trend_score"] if deduped else 0,
        },
        "opportunities": deduped,
    }


def _load_history(history_dir=None):
    """Load the suggestion history file.

    Returns (list[dict], Path) — history entries and the file path.
    """
    history_dir = Path(history_dir) if history_dir else _DEFAULT_HISTORY_DIR
    history_dir.mkdir(parents=True, exist_ok=True)
    path = history_dir / "suggestion_history.json"
    if path.exists():
        try:
            with open(str(path), encoding="utf-8") as f:
                return json.load(f), path
        except (json.JSONDecodeError, OSError):
            return [], path
    return [], path


def _save_history(entries, path):
    """Save the suggestion history file."""
    with open(str(path), "w", encoding="utf-8") as f:
        json.dump(entries, f, indent=2, default=str)


def _suggestion_hash(opp):
    """Stable hash of an opportunity for dedup."""
    key = opp.get("inspiring_paper", {}).get("title", "") + opp.get("domain", "")
    return hashlib.sha256(key.encode()).hexdigest()[:16]


def daily_suggestion(history_dir=None, window_days=90,
                     scan_kwargs=None, _today=None):
    """Return today's single paper opportunity suggestion.

    Uses a date-seeded rotation through trending results, skipping any
    topic suggested within the last ``window_days``.

    Parameters
    ----------
    history_dir : str or Path, optional
        Where to read/write suggestion_history.json.
    window_days : int
        Don't re-suggest a topic within this many days.
    scan_kwargs : dict, optional
        Extra kwargs for ``scan_trending()``.
    _today : date, optional
        Override today's date (for testing).

    Returns
    -------
    dict or None
        A single opportunity dict, or None if no opportunities found.
    """
    today = _today or date.today()
    history, history_path = _load_history(history_dir)

    # Prune old history entries
    cutoff = (today - timedelta(days=window_days)).isoformat()
    history = [h for h in history if h.get("date", "") >= cutoff]

    recent_hashes = {h.get("hash") for h in history}

    # Get trending topics
    kwargs = {"top_n": 30, "rate_limit_delay": 1.2}
    if scan_kwargs:
        kwargs.update(scan_kwargs)
    report = scan_trending(**kwargs)
    opportunities = report.get("opportunities", [])

    if not opportunities:
        return None

    # Filter out recently suggested
    candidates = [
        opp for opp in opportunities
        if _suggestion_hash(opp) not in recent_hashes
    ]

    if not candidates:
        # All have been suggested recently — pick from full list using date seed
        candidates = opportunities

    # Date-seeded selection for deterministic daily pick
    rng = random.Random(today.toordinal())
    # Weight by trend_score — higher scored topics more likely
    weights = [max(1, opp["trend_score"]) for opp in candidates]
    pick = rng.choices(candidates, weights=weights, k=1)[0]

    # Record in history
    history.append({
        "date": today.isoformat(),
        "hash": _suggestion_hash(pick),
        "title": pick["title"],
        "domain": pick["domain"],
        "inspiring_paper": pick["inspiring_paper"]["title"],
    })
    _save_history(history, history_path)

    # Add daily metadata
    pick["suggestion_date"] = today.isoformat()
    pick["scan_metadata"] = report["metadata"]

    return pick


def format_daily_suggestion(pick):
    """Format a daily suggestion as a readable string for the terminal.

    Parameters
    ----------
    pick : dict
        An opportunity from ``daily_suggestion()``.

    Returns
    -------
    str
        Multi-line formatted string.
    """
    if not pick:
        return "No suggestion available today. Try again tomorrow or check your network."

    lines = []
    lines.append("=" * 72)
    lines.append("  Today's Paper Opportunity")
    lines.append("=" * 72)
    lines.append("")
    lines.append(f"  Title:    {pick['title']}")
    lines.append(f"  Domain:   {pick['domain']}")
    lines.append(f"  Type:     {pick['paper_type']}")
    lines.append(f"  Score:    {pick['trend_score']}/100")
    lines.append(f"  Effort:   {pick['effort']} (~{pick['effort_weeks']} weeks)")
    lines.append(f"  Journals: {', '.join(pick['suggested_journals'])}")
    lines.append("")

    # Inspiring paper
    ip = pick.get("inspiring_paper", {})
    lines.append("  Inspired by:")
    lines.append(f"    \"{ip.get('title', 'N/A')}\"")
    if ip.get("authors"):
        lines.append(f"    Authors: {', '.join(ip['authors'][:3])}")
    if ip.get("year"):
        lines.append(f"    Year: {ip['year']}  Citations: {ip.get('citations', 0)}")
    if ip.get("url"):
        lines.append(f"    URL: {ip['url']}")
    lines.append("")

    # Research angle
    lines.append(f"  Research angle:")
    lines.append(f"    {pick.get('research_angle', 'N/A')}")
    lines.append("")

    # NeqSim classes
    classes = pick.get("neqsim_classes", [])
    if classes:
        lines.append(f"  NeqSim classes: {', '.join(classes[:5])}")

    # Improvements
    improvements = pick.get("neqsim_improvement", [])
    if improvements:
        lines.append("  Code improvements this paper would drive:")
        for imp in improvements[:3]:
            lines.append(f"    - {imp}")

    lines.append("")
    lines.append(f"  Suggestion date: {pick.get('suggestion_date', 'N/A')}")
    lines.append("=" * 72)
    return "\n".join(lines)


def generate_markdown_suggestion(pick):
    """Generate markdown for a daily suggestion.

    Parameters
    ----------
    pick : dict
        An opportunity from ``daily_suggestion()``.

    Returns
    -------
    str
        Markdown-formatted suggestion report.
    """
    if not pick:
        return "# Daily Paper Suggestion\n\nNo suggestions available today.\n"

    lines = []
    lines.append("# Daily Paper Suggestion")
    lines.append("")
    lines.append(f"**Date:** {pick.get('suggestion_date', 'N/A')}  ")
    lines.append(f"**Domain:** {pick['domain']}  ")
    lines.append(f"**Type:** {pick['paper_type']}  ")
    lines.append(f"**Trend score:** {pick['trend_score']}/100  ")
    lines.append("")

    lines.append(f"## {pick['title']}")
    lines.append("")

    # Research angle
    lines.append(f"> {pick.get('research_angle', 'N/A')}")
    lines.append("")

    # Inspiring paper
    ip = pick.get("inspiring_paper", {})
    lines.append("### Inspiring Paper")
    lines.append("")
    title = ip.get("title", "N/A")
    url = ip.get("url", "")
    if url:
        lines.append(f"**[{title}]({url})**")
    else:
        lines.append(f"**{title}**")
    if ip.get("authors"):
        lines.append(f"- Authors: {', '.join(ip['authors'][:3])}")
    if ip.get("year"):
        lines.append(f"- Year: {ip['year']}")
    lines.append(f"- Citations: {ip.get('citations', 0)}")
    lines.append("")

    # NeqSim connection
    lines.append("### NeqSim Connection")
    lines.append("")
    classes = pick.get("neqsim_classes", [])
    if classes:
        lines.append(f"**Relevant classes:** `{'`, `'.join(classes[:5])}`")
    lines.append("")
    lines.append(f"**Suggested journals:** {', '.join(pick.get('suggested_journals', []))}")
    lines.append(f"**Effort:** {pick['effort']} (~{pick['effort_weeks']} weeks)")
    lines.append("")

    improvements = pick.get("neqsim_improvement", [])
    if improvements:
        lines.append("**Code improvements this paper would drive:**")
        for imp in improvements:
            lines.append(f"- {imp}")
        lines.append("")

    lines.append("---")
    lines.append("*Generated by [neqsim-paperlab](../README.md) trending topics scanner.*")
    return "\n".join(lines)
