"""doc_retriever.py - automatic engineering-document retrieval for task folders.

This is the chat-equivalent, zero-argument retrieval path used by the task
workflow, the Standard-first gate of living tasks, and ``neqsim fetch-docs``.
It works out the installation from the task text, searches the configured
backend (currently STID via ``stidapi``) for the equipment that matters to the
task, ranks the referenced documents (P&IDs and data sheets first), and
downloads them into ``step1_scope_and_research/references/stid/``.

It never raises for backend problems. Every call writes
``references/stid/retrieval_status.json`` with a status of ``ok``,
``no_backend``, ``no_installation``, ``auth_error``, ``no_matches`` or
``error`` so an agent can report the concrete blocker instead of guessing.

The backend configuration is ``devtools/doc_retrieval_config.yaml`` (gitignored),
``~/.neqsim/doc_retrieval_config.yaml``, or the path in
``NEQSIM_DOC_RETRIEVAL_CONFIG``. Set ``NEQSIM_DISABLE_DOC_RETRIEVAL=1`` to turn
automatic retrieval off (tests, offline runs).

Console output is redacted to counts: document numbers, titles and tags are
written only to the task-local manifest.

Usage::

    neqsim fetch-docs <task_dir>                   # infer installation, default keywords
    neqsim fetch-docs <task_dir> --inst MYINST     # explicit installation code
    neqsim fetch-docs <task_dir> --keywords compressor "export gas" --max-docs 80
    neqsim fetch-docs <task_dir> --tags 20VA001 --no-download
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone

DEVTOOLS = os.path.dirname(os.path.abspath(__file__))
STATUS_FILE = "retrieval_status.json"
MANIFEST_FILE = "stid_retrieval_manifest.json"

DEFAULT_KEYWORDS = [
    "compressor", "separator", "scrubber", "cooler", "heat exchanger", "export",
    "inlet", "pump", "flare", "anti-surge",
]

TITLE_WEIGHTS = [
    ("performance", 4), ("curve", 4), ("heat and material", 4), ("heat & material", 4),
    ("mass balance", 4), ("process flow", 4), ("flow diagram", 4), ("pfd", 4),
    ("data sheet", 3), ("datasheet", 3), ("piping and instrument", 3), ("p&id", 3),
    ("cause and effect", 2), ("general arrangement", 2), ("design basis", 3),
    ("operating", 1), ("capacity", 2),
]


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _say(message, quiet):
    if not quiet:
        print(message)


def config_path():
    """Return the first existing retrieval-config path, or ``None``."""
    candidates = []
    env = os.environ.get("NEQSIM_DOC_RETRIEVAL_CONFIG")
    if env:
        candidates.append(env)
    candidates.append(os.path.join(DEVTOOLS, "doc_retrieval_config.yaml"))
    project_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    if project_root:
        candidates.append(os.path.join(project_root, "devtools", "doc_retrieval_config.yaml"))
    candidates.append(os.path.join(os.path.expanduser("~"), ".neqsim",
                                   "doc_retrieval_config.yaml"))
    for path in candidates:
        if path and os.path.isfile(path):
            return path
    return None


def load_config(path=None):
    """Load the retrieval config as a dict ({} when missing or unreadable)."""
    path = path or config_path()
    if not path:
        return {}
    try:
        import yaml
        with open(path, "r", encoding="utf-8") as handle:
            data = yaml.safe_load(handle) or {}
        return data if isinstance(data, dict) else {}
    except Exception:  # noqa: BLE001
        return {}


def resolve_installation(cfg, name_or_code=None, text=None):
    """Resolve an installation code from a code, a display name, or free text.

    ``installation_codes`` in the config maps code -> display name. Matching is
    offline: exact code, exact display name, then the longest display name
    (case-insensitive) or code (upper-case only) found as a whole word in
    ``text``. There is deliberately no fallback to ``default_inst_code`` for
    inference, so a task never silently pulls another installation's documents.

    Returns the code or ``None``.
    """
    codes = cfg.get("installation_codes") or {}
    if name_or_code:
        wanted = str(name_or_code).strip()
        for code, name in codes.items():
            if wanted.upper() == str(code).upper():
                return code
            if wanted.lower() == str(name).strip().lower():
                return code
        return wanted
    if text:
        lowered = " " + re.sub(r"[_\-]+", " ", text.lower()) + " "
        spaced = " " + re.sub(r"[_\-]+", " ", text) + " "
        candidates = []
        for code, name in codes.items():
            label = str(name).strip().lower()
            if len(label) >= 3 and re.search(
                    r"(?<![a-z0-9])" + re.escape(label) + r"(?![a-z0-9])", lowered):
                candidates.append((len(label), code))
            code_label = str(code).strip()
            if len(code_label) >= 3 and re.search(
                    r"(?<![A-Za-z0-9])" + re.escape(code_label.upper()) + r"(?![A-Za-z0-9])", spaced):
                candidates.append((len(code_label), code))
        if candidates:
            candidates.sort(reverse=True)
            return candidates[0][1]
    return None


def task_text(task_dir):
    """Collect the task title/scope text used for installation inference."""
    parts = [os.path.basename(os.path.abspath(task_dir))]
    for rel in ("step1_scope_and_research/task_spec.md", "README.md", "study_config.yaml",
                "continuous/goal.yaml"):
        path = os.path.join(task_dir, rel)
        if os.path.isfile(path):
            try:
                with open(path, "r", encoding="utf-8", errors="ignore") as handle:
                    parts.append(handle.read(4000))
            except OSError:
                pass
    return "\n".join(parts)


def _doc_type_match(ref, doc_types):
    if not doc_types:
        return True
    wanted = set(str(t).upper() for t in doc_types)
    if str(ref.get("docType", "")).upper() in wanted:
        return True
    tokens = set(re.split(r"[-_./ ]+", str(ref.get("docNo", "")).upper()))
    if tokens & wanted:
        return True
    if "DS" in wanted and ref.get("isDatasheet"):
        return True
    if "XB" in wanted and ref.get("isPid"):
        return True
    return False


def score_document(ref):
    """Relevance score for a STID document reference (higher is better)."""
    score = 0
    if ref.get("isPid"):
        score += 6
    if ref.get("isDatasheet"):
        score += 5
    title = str(ref.get("docTitle", "")).lower()
    for needle, weight in TITLE_WEIGHTS:
        if needle in title:
            score += weight
    score += min(len(ref.get("tags_referencing", [])), 5)
    if str(ref.get("revStatus", "")).upper() in ("VOID", "CANCELLED", "SUPERSEDED"):
        score -= 10
    return score


def discover(inst_code, keywords=None, tags=None, take_per_keyword=25, tag_search=None):
    """Search tags (by number and description) and collect document references.

    Returns ``(docs, stats)`` where ``docs`` maps docNo -> reference dict.
    """
    if tag_search is None:
        from stidapi import Tag
        tag_search = Tag.search
    docs = {}
    stats = {"tag_queries": 0, "tags_found": 0, "doc_references": 0}

    def collect(found, label):
        for tag in found or []:
            stats["tags_found"] += 1
            tag_no = getattr(tag, "no", None) or getattr(tag, "tag_no", None) or label
            refs = tag.get_doc_references() or []
            for ref in refs:
                stats["doc_references"] += 1
                doc_no = ref.get("docNo", "")
                if not doc_no:
                    continue
                entry = docs.get(doc_no)
                if entry is None:
                    entry = dict(ref)
                    entry["tags_referencing"] = []
                    entry["matched_queries"] = []
                    docs[doc_no] = entry
                if tag_no not in entry["tags_referencing"]:
                    entry["tags_referencing"].append(tag_no)
                if label not in entry["matched_queries"]:
                    entry["matched_queries"].append(label)

    for tag_no in tags or []:
        stats["tag_queries"] += 1
        collect(tag_search(inst_code, tag_no=tag_no, take=5), "tag:" + str(tag_no))
    for keyword in keywords or []:
        stats["tag_queries"] += 1
        collect(tag_search(inst_code, description=keyword, take=take_per_keyword),
                "keyword:" + str(keyword))
    return docs, stats


def select(docs, max_docs=60, doc_types=None, include_pid=False):
    """Rank documents and keep the best ``max_docs`` with at least one PDF.

    ``doc_types`` filters on docType, doc-number segments, or the P&ID/data
    sheet flags. If the filter would remove everything it is ignored, because
    type codes differ between STID installations. ``include_pid`` keeps P&IDs
    regardless of the filter (used for config defaults, since topology is
    always needed).
    """
    def has_pdf(ref):
        return any(str((f or {}).get("fileName", "") if isinstance(f, dict)
                       else getattr(f, "file_name", "")).lower().endswith(".pdf")
                   for f in ref.get("files", []) or [])

    pool = [ref for ref in docs.values() if has_pdf(ref)]
    filtered = [ref for ref in pool
                if _doc_type_match(ref, doc_types) or (include_pid and ref.get("isPid"))]
    filter_applied = bool(doc_types) and bool(filtered)
    chosen = filtered if filter_applied else pool
    chosen.sort(key=lambda ref: (-score_document(ref), str(ref.get("docNo", ""))))
    return chosen[:max_docs], filter_applied


def _write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, default=str)


def _status(out_dir, payload):
    payload.setdefault("schema_version", "1.0")
    payload.setdefault("checked_at", _now())
    if payload.get("status") not in ("no_backend", "disabled") or os.path.isdir(out_dir):
        _write_json(os.path.join(out_dir, STATUS_FILE), payload)
    return payload


def retrieve_for_task(task_dir, inst_code=None, keywords=None, tags=None, doc_types=None,
                      max_docs=60, download=True, quiet=False, tag_search=None,
                      downloader=None):
    """Retrieve documents for a task folder. Never raises for backend errors.

    Returns the status dict that is also written to
    ``references/stid/retrieval_status.json``.
    """
    task_dir = os.path.abspath(str(task_dir))
    out_dir = os.path.join(task_dir, "step1_scope_and_research", "references", "stid")
    base = {"task": os.path.basename(task_dir), "backend": None, "installation": None,
            "documents_selected": 0, "documents_downloaded": 0, "documents_cached": 0,
            "documents_failed": 0}

    if os.environ.get("NEQSIM_DISABLE_DOC_RETRIEVAL", "").strip() in ("1", "true", "yes"):
        return _status(out_dir, dict(base, status="disabled",
                                     message="NEQSIM_DISABLE_DOC_RETRIEVAL is set"))
    cfg = load_config()
    backend = str(cfg.get("backend", "")).strip().lower()
    base["backend"] = backend or None
    if backend != "stidapi":
        return _status(out_dir, dict(base, status="no_backend", message=(
            "No document backend configured (doc_retrieval_config.yaml with backend: stidapi)."
            " Place documents in references/ manually.")))
    if tag_search is None:
        try:
            import stidapi  # noqa: F401
        except ImportError:
            return _status(out_dir, dict(base, status="no_backend",
                                         message="stidapi is not installed (pip install 'stidapi>=1.4.4')"))

    inst = resolve_installation(cfg, inst_code, None if inst_code else task_text(task_dir))
    base["installation"] = inst
    if not inst:
        return _status(out_dir, dict(base, status="no_installation", message=(
            "Could not infer the installation from the task text; pass --inst CODE")))

    keywords = list(keywords) if keywords else list(cfg.get("default_keywords") or DEFAULT_KEYWORDS)
    include_pid = doc_types is None
    doc_types = doc_types if doc_types is not None else cfg.get("default_doc_types")
    max_docs = int(max_docs or cfg.get("max_docs") or 60)
    _say("Document retrieval: backend=stidapi, queries={}".format(len(keywords) + len(tags or [])), quiet)

    try:
        docs, stats = discover(inst, keywords=keywords, tags=tags, tag_search=tag_search)
    except Exception as exc:  # noqa: BLE001
        text = str(exc)
        kind = "auth_error" if re.search(r"(?i)401|403|auth|token|login|credential", text) else "error"
        return _status(out_dir, dict(base, status=kind, message=text[:300]))
    base.update(stats)
    if not docs:
        return _status(out_dir, dict(base, status="no_matches", message=(
            "Backend reachable but no tag/document matches; check the installation code"
            " or pass --keywords/--tags"), keywords=keywords))

    chosen, filter_applied = select(docs, max_docs=max_docs, doc_types=doc_types,
                                    include_pid=include_pid)
    base.update(documents_discovered=len(docs), documents_selected=len(chosen),
                doc_type_filter_applied=filter_applied)
    downloaded, failed = [], []
    if download and chosen:
        if downloader is None:
            if DEVTOOLS not in sys.path:
                sys.path.insert(0, DEVTOOLS)
            from stid_download import download_doc_files as downloader
        selection = {}
        for ref in chosen:
            selection[ref["docNo"]] = {"docNo": ref["docNo"], "docTitle": ref.get("docTitle", ""),
                                       "docType": ref.get("docType", ""),
                                       "files": ref.get("files", [])}
        import contextlib
        import io
        sink = io.StringIO()
        with contextlib.redirect_stdout(sink):
            downloaded, failed = downloader(inst, selection, out_dir)

    manifest = {
        "schema_version": "1.0", "source": "stidapi", "inst_code": inst, "retrieved_at": _now(),
        "keywords": keywords, "tags_searched": tags or [], "doc_types": doc_types,
        "documents_selected": [{
            "docNo": ref.get("docNo"), "docTitle": ref.get("docTitle"),
            "docType": ref.get("docType"), "revNo": ref.get("revNo"),
            "revDate": ref.get("revDate"), "isPid": ref.get("isPid"),
            "isDatasheet": ref.get("isDatasheet"), "score": score_document(ref),
            "tags_referencing": ref.get("tags_referencing", [])[:20],
            "matched_queries": ref.get("matched_queries", []),
        } for ref in chosen],
        "documents_retrieved": downloaded, "documents_failed": failed,
    }
    _write_json(os.path.join(out_dir, MANIFEST_FILE), manifest)
    ok_count = sum(1 for d in downloaded if d.get("status") == "downloaded")
    cached = sum(1 for d in downloaded if d.get("status") == "cached")
    status = dict(base, status="ok" if (downloaded or not download) else "error",
                  documents_downloaded=ok_count, documents_cached=cached,
                  documents_failed=len(failed),
                  pid_count=sum(1 for r in chosen if r.get("isPid")),
                  datasheet_count=sum(1 for r in chosen if r.get("isDatasheet")),
                  manifest=os.path.join("step1_scope_and_research", "references", "stid", MANIFEST_FILE),
                  message="" if downloaded or not download else "all downloads failed")
    _say("Document retrieval: {} selected, {} downloaded, {} cached, {} failed".format(
        len(chosen), ok_count, cached, len(failed)), quiet)
    return _status(out_dir, status)


def retrieve_documents(tags=None, doc_types=None, output_dir=None, task_dir=None,
                       inst_code=None, keywords=None, max_docs=60):
    """Backward-compatible helper returning the list of downloaded file paths.

    ``output_dir`` may be a task's ``references`` folder; the task folder is
    derived from it when ``task_dir`` is not given. Returns ``[]`` when no
    backend is configured or nothing could be retrieved.
    """
    if task_dir is None:
        if output_dir is None:
            task_dir = os.getcwd()
        else:
            path = os.path.abspath(output_dir)
            while path and os.path.basename(path) != "step1_scope_and_research":
                parent = os.path.dirname(path)
                if parent == path:
                    path = None
                    break
                path = parent
            task_dir = os.path.dirname(path) if path else os.getcwd()
    status = retrieve_for_task(task_dir, inst_code=inst_code, keywords=keywords, tags=tags,
                               doc_types=doc_types, max_docs=max_docs, quiet=True)
    if status.get("status") != "ok":
        return []
    out_dir = os.path.join(os.path.abspath(task_dir), "step1_scope_and_research", "references",
                           "stid")
    manifest_path = os.path.join(out_dir, MANIFEST_FILE)
    try:
        with open(manifest_path, "r", encoding="utf-8") as handle:
            manifest = json.load(handle)
    except (OSError, ValueError):
        return []
    return [os.path.join(out_dir, d["file"]) for d in manifest.get("documents_retrieved", [])
            if d.get("file")]


def main(argv=None):
    """CLI entry point for ``neqsim fetch-docs``."""
    parser = argparse.ArgumentParser(prog="neqsim fetch-docs",
                                     description="Retrieve engineering documents into a task folder")
    parser.add_argument("task_dir", nargs="?", default=os.environ.get("NEQSIM_TASK_DIR", "."))
    parser.add_argument("--inst", default=None, help="Installation code or name (default: inferred)")
    parser.add_argument("--keywords", nargs="+", default=None, help="Tag-description search terms")
    parser.add_argument("--tags", nargs="+", default=None, help="Tag numbers to search")
    parser.add_argument("--doc-types", nargs="+", default=None, help="Optional doc-type filter")
    parser.add_argument("--max-docs", type=int, default=60)
    parser.add_argument("--no-download", action="store_true", help="Discover and rank only")
    parser.add_argument("--json", action="store_true", help="Print the status JSON")
    args = parser.parse_args(argv)
    task_dir = os.path.abspath(args.task_dir)
    if not os.path.isdir(task_dir):
        print("Task folder not found: {}".format(task_dir))
        return 2
    status = retrieve_for_task(task_dir, inst_code=args.inst, keywords=args.keywords,
                               tags=args.tags, doc_types=args.doc_types, max_docs=args.max_docs,
                               download=not args.no_download, quiet=args.json)
    if args.json:
        print(json.dumps({k: v for k, v in status.items() if k != "message"} , indent=2))
    elif status.get("status") != "ok":
        print("Document retrieval {}: {}".format(status.get("status"), status.get("message", "")))
    return 0 if status.get("status") in ("ok", "disabled") else 1


if __name__ == "__main__":
    sys.exit(main())
