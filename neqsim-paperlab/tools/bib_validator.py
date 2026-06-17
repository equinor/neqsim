"""
Bibliography Validator — Validate refs.bib against journal requirements.

Uses bibtexparser to parse and check BibTeX entries for completeness,
consistency, and common issues.

Usage::

    from tools.bib_validator import validate_bibliography

    issues = validate_bibliography("papers/my_paper/refs.bib")
    for issue in issues:
        print(f"[{issue['severity']}] {issue['key']}: {issue['message']}")
"""

from pathlib import Path
from typing import Dict, List, Optional

try:
    import bibtexparser
    _HAS_BIBTEX = True
except ImportError:
    _HAS_BIBTEX = False

try:
    import requests as _requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False


# Required fields by entry type (BibTeX standard)
_REQUIRED_FIELDS = {
    "article": {"author", "title", "journal", "year"},
    "book": {"author", "title", "publisher", "year"},
    "inproceedings": {"author", "title", "booktitle", "year"},
    "incollection": {"author", "title", "booktitle", "publisher", "year"},
    "phdthesis": {"author", "title", "school", "year"},
    "mastersthesis": {"author", "title", "school", "year"},
    "techreport": {"author", "title", "institution", "year"},
    "misc": {"title"},
    "software": {"title", "year"},
    "online": {"title", "url"},
}

# Fields that should not be empty
_NONEMPTY_FIELDS = {"author", "title", "year", "journal", "publisher", "school"}


def validate_bibliography(bib_path, manuscript_path=None):
    """Validate a BibTeX file for completeness and consistency.

    Parameters
    ----------
    bib_path : str or Path
        Path to the .bib file.
    manuscript_path : str or Path, optional
        Path to paper.md — if given, checks that all \\cite{} keys exist
        in the bib file and all bib entries are used.

    Returns
    -------
    list of dict
        Each dict has keys: key, severity (INFO/WARNING/FAIL), message.
    """
    bib_path = Path(bib_path)
    issues = []

    if not bib_path.exists():
        issues.append({"key": "-", "severity": "FAIL",
                       "message": f"Bibliography file not found: {bib_path}"})
        return issues

    if not _HAS_BIBTEX:
        issues.append({"key": "-", "severity": "WARNING",
                       "message": "bibtexparser not installed — skipping deep validation"})
        return _validate_regex_fallback(bib_path, manuscript_path)

    # Parse with bibtexparser
    bib_text = bib_path.read_text(encoding="utf-8")
    try:
        bib_db = bibtexparser.loads(bib_text)
    except Exception as e:
        issues.append({"key": "-", "severity": "FAIL",
                       "message": f"Failed to parse BibTeX: {e}"})
        return issues

    entries = bib_db.entries
    if not entries:
        issues.append({"key": "-", "severity": "WARNING",
                       "message": "Bibliography file contains no entries"})
        return issues

    issues.append({"key": "-", "severity": "INFO",
                   "message": f"Found {len(entries)} bibliography entries"})

    # Check for duplicate keys
    seen_keys = set()
    for entry in entries:
        key = entry.get("ID", "unknown")
        if key in seen_keys:
            issues.append({"key": key, "severity": "FAIL",
                           "message": "Duplicate BibTeX key"})
        seen_keys.add(key)

    # Validate each entry
    for entry in entries:
        key = entry.get("ID", "unknown")
        entry_type = entry.get("ENTRYTYPE", "misc").lower()

        # Check required fields
        required = _REQUIRED_FIELDS.get(entry_type, {"title"})
        for field in required:
            if field not in entry or not entry[field].strip():
                issues.append({"key": key, "severity": "FAIL",
                               "message": f"Missing required field '{field}' for @{entry_type}"})

        # Check for empty fields
        for field in _NONEMPTY_FIELDS:
            if field in entry and not entry[field].strip():
                issues.append({"key": key, "severity": "WARNING",
                               "message": f"Field '{field}' is present but empty"})

        # Check year is a valid number
        year_str = entry.get("year", "").strip()
        if year_str:
            try:
                year = int(year_str)
                if year < 1800 or year > 2100:
                    issues.append({"key": key, "severity": "WARNING",
                                   "message": f"Unusual year: {year}"})
            except ValueError:
                issues.append({"key": key, "severity": "WARNING",
                               "message": f"Year is not a number: '{year_str}'"})

        # Check for common issues
        title = entry.get("title", "")
        if title and title == title.lower():
            issues.append({"key": key, "severity": "INFO",
                           "message": "Title is all lowercase — check capitalization"})

        # Check URL/DOI presence (recommended)
        if "doi" not in entry and "url" not in entry:
            issues.append({"key": key, "severity": "INFO",
                           "message": "No DOI or URL — consider adding for discoverability"})

    # Cross-check with manuscript if provided
    if manuscript_path:
        issues.extend(_cross_check_manuscript(manuscript_path, seen_keys))

    return issues


def verify_dois(bib_path, timeout=10):
    """Verify that DOIs in a BibTeX file resolve correctly.

    Sends HTTP HEAD requests to https://doi.org/{doi} and checks for
    a valid redirect (HTTP 302/301 → 200).

    Parameters
    ----------
    bib_path : str or Path
        Path to the .bib file.
    timeout : int
        Request timeout in seconds per DOI.

    Returns
    -------
    list of dict
        Each dict has keys: key, doi, status (ok/broken/timeout/missing).
    """
    bib_path = Path(bib_path)
    results = []

    if not _HAS_REQUESTS:
        return [{"key": "-", "doi": "-", "status": "skipped",
                 "message": "requests package not installed"}]

    if not bib_path.exists():
        return [{"key": "-", "doi": "-", "status": "error",
                 "message": f"File not found: {bib_path}"}]

    bib_text = bib_path.read_text(encoding="utf-8")

    # Extract DOIs with their entry keys
    import re as _re
    entries = _re.findall(
        r'@\w+\{(\w+),.*?doi\s*=\s*\{([^}]+)\}', bib_text, _re.DOTALL | _re.IGNORECASE)

    if not entries:
        return [{"key": "-", "doi": "-", "status": "info",
                 "message": "No DOIs found in bibliography"}]

    for key, doi in entries:
        doi = doi.strip()
        url = f"https://doi.org/{doi}"
        try:
            resp = _requests.head(url, timeout=timeout, allow_redirects=True)
            if resp.status_code == 200:
                results.append({"key": key, "doi": doi, "status": "ok"})
            elif resp.status_code == 404:
                results.append({"key": key, "doi": doi, "status": "broken",
                                "message": f"DOI not found (404): {doi}"})
            else:
                results.append({"key": key, "doi": doi, "status": "warning",
                                "message": f"Unexpected status {resp.status_code} for {doi}"})
        except _requests.Timeout:
            results.append({"key": key, "doi": doi, "status": "timeout",
                            "message": f"Timeout resolving DOI: {doi}"})
        except _requests.RequestException as e:
            results.append({"key": key, "doi": doi, "status": "error",
                            "message": f"Error resolving DOI {doi}: {e}"})

    return results


def print_doi_report(results):
    """Print a formatted DOI verification report.

    Parameters
    ----------
    results : list of dict
        Results from verify_dois().
    """
    ok = sum(1 for r in results if r["status"] == "ok")
    broken = sum(1 for r in results if r["status"] == "broken")
    other = len(results) - ok - broken

    print("=" * 60)
    print("DOI VERIFICATION REPORT")
    print("=" * 60)
    print(f"  Checked: {len(results)}  OK: {ok}  Broken: {broken}  Other: {other}")
    print()

    for r in results:
        if r["status"] == "ok":
            icon = "[OK]"
        elif r["status"] == "broken":
            icon = "[!!]"
        else:
            icon = "[??]"
        msg = r.get("message", r["doi"])
        print(f"  {icon} [{r['key']}] {msg}")
    print()


def _cross_check_manuscript(manuscript_path, bib_keys):
    """Check that manuscript citations match bib entries."""
    import re
    manuscript_path = Path(manuscript_path)
    issues = []

    if not manuscript_path.exists():
        return issues

    text = manuscript_path.read_text(encoding="utf-8")

    # Find all \cite{key} and \cite{key1, key2} references
    cited_keys = set()
    for match in re.finditer(r'\\cite\{([^}]+)\}', text):
        for key in match.group(1).split(","):
            cited_keys.add(key.strip())

    # Keys cited but not in bib
    missing = cited_keys - bib_keys
    for key in sorted(missing):
        issues.append({"key": key, "severity": "FAIL",
                       "message": f"{key}: cited in manuscript but not in refs.bib"})

    # Keys in bib but not cited
    unused = bib_keys - cited_keys
    for key in sorted(unused):
        issues.append({"key": key, "severity": "WARNING",
                       "message": f"{key}: in refs.bib but not cited in manuscript"})

    if not missing and not unused:
        issues.append({"key": "-", "severity": "INFO",
                       "message": "All citations match bibliography entries"})

    return issues


def _validate_regex_fallback(bib_path, manuscript_path=None):
    """Basic regex validation when bibtexparser is not available."""
    import re
    issues = []
    bib_text = Path(bib_path).read_text(encoding="utf-8")

    # Count entries
    entries = re.findall(r'@(\w+)\{(\w+),', bib_text)
    if not entries:
        issues.append({"key": "-", "severity": "WARNING",
                       "message": "No BibTeX entries found (regex fallback)"})
        return issues

    issues.append({"key": "-", "severity": "INFO",
                   "message": f"Found {len(entries)} entries (regex fallback)"})

    # Check for duplicate keys
    keys = [e[1] for e in entries]
    seen = set()
    for key in keys:
        if key in seen:
            issues.append({"key": key, "severity": "FAIL",
                           "message": "Duplicate BibTeX key"})
        seen.add(key)

    return issues


def print_validation_report(issues):
    """Print a formatted validation report."""
    fails = [i for i in issues if i["severity"] == "FAIL"]
    warns = [i for i in issues if i["severity"] == "WARNING"]
    infos = [i for i in issues if i["severity"] == "INFO"]

    print(f"Bibliography Validation: {len(fails)} FAIL, {len(warns)} WARNING, {len(infos)} INFO")
    print("-" * 60)

    for issue in issues:
        icon = {"FAIL": "[FAIL]", "WARNING": "[WARN]", "INFO": "[ OK ]"}[issue["severity"]]
        print(f"  {icon} [{issue['key']}] {issue['message']}")

    if fails:
        print(f"\n{len(fails)} issue(s) must be fixed before submission.")
    elif warns:
        print(f"\n{len(warns)} warning(s) — review before submission.")
    else:
        print("\nBibliography passes all checks.")
