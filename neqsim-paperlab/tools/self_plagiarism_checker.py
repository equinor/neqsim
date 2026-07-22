"""
Self-Plagiarism Checker — Detects excessive textual overlap between the current
manuscript and previously published papers/drafts using TF-IDF cosine similarity.

Usage::

    from tools.self_plagiarism_checker import check_self_plagiarism, print_plagiarism_report

    report = check_self_plagiarism(
        "papers/my_paper/paper.md",
        corpus_dir="papers/",
    )
    print_plagiarism_report(report)
"""

import re
from pathlib import Path
from typing import Dict, List, Optional

try:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity
    _HAS_SKLEARN = True
except ImportError:
    _HAS_SKLEARN = False


def _extract_body_text(path):
    """Read a markdown file and strip YAML front matter, code blocks, and references.

    Args:
        path: Path to the markdown file.

    Returns:
        Plain body text.
    """
    text = Path(path).read_text(encoding="utf-8", errors="replace")
    # Strip YAML front matter
    text = re.sub(r'^---.*?---\s*', '', text, flags=re.DOTALL)
    # Strip code blocks
    text = re.sub(r'```.*?```', '', text, flags=re.DOTALL)
    # Strip inline code
    text = re.sub(r'`[^`]+`', '', text)
    # Strip references section
    text = re.sub(r'## References.*$', '', text, flags=re.DOTALL | re.IGNORECASE)
    # Strip markdown formatting
    text = re.sub(r'[#*_\[\]()>|]', ' ', text)
    # Collapse whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    return text


def _split_paragraphs(text, min_words=20):
    """Split text into paragraph-level chunks for fine-grained comparison.

    Args:
        text: Body text string.
        min_words: Minimum words per chunk.

    Returns:
        List of paragraph strings.
    """
    # Split on double newlines or sentence boundaries for chunks
    paragraphs = re.split(r'\n\s*\n', text)
    chunks = []
    for p in paragraphs:
        p = p.strip()
        if len(p.split()) >= min_words:
            chunks.append(p)
    return chunks


def check_self_plagiarism(manuscript_path, corpus_dir=None, corpus_files=None,
                          threshold=0.35, chunk_threshold=0.50):
    """Check a manuscript for textual overlap against a corpus of previous papers.

    Uses TF-IDF cosine similarity at both document and paragraph level.

    Args:
        manuscript_path: Path to the current paper.md.
        corpus_dir: Directory to scan for other .md papers (recursive).
        corpus_files: Explicit list of .md file paths to compare against.
        threshold: Document-level similarity warning threshold (0-1).
        chunk_threshold: Paragraph-level similarity warning threshold (0-1).

    Returns:
        Report dict with document-level and paragraph-level overlap results.
    """
    if not _HAS_SKLEARN:
        return {
            "error": "scikit-learn required. Install: pip install scikit-learn",
            "available": False,
        }

    manuscript_path = Path(manuscript_path)
    if not manuscript_path.exists():
        return {"error": f"Manuscript not found: {manuscript_path}"}

    manuscript_text = _extract_body_text(manuscript_path)
    if len(manuscript_text.split()) < 50:
        return {"error": "Manuscript too short for meaningful comparison"}

    # Build corpus
    corpus_paths = []
    if corpus_files:
        corpus_paths = [Path(f) for f in corpus_files]
    elif corpus_dir:
        corpus_dir = Path(corpus_dir)
        corpus_paths = [f for f in corpus_dir.rglob("paper.md")
                       if f.resolve() != manuscript_path.resolve()]
    else:
        # Default: look for sibling paper directories
        parent = manuscript_path.parent.parent
        if parent.exists():
            corpus_paths = [f for f in parent.rglob("paper.md")
                           if f.resolve() != manuscript_path.resolve()]

    if not corpus_paths:
        return {
            "available": True,
            "document_overlaps": [],
            "paragraph_overlaps": [],
            "corpus_size": 0,
            "message": "No comparison documents found in corpus",
        }

    corpus_texts = {}
    for p in corpus_paths:
        if p.exists():
            text = _extract_body_text(p)
            if len(text.split()) >= 50:
                corpus_texts[str(p)] = text

    if not corpus_texts:
        return {
            "available": True,
            "document_overlaps": [],
            "paragraph_overlaps": [],
            "corpus_size": 0,
            "message": "No valid comparison documents found",
        }

    # ── Document-level similarity ─────────────────────────────────────
    all_docs = [manuscript_text] + list(corpus_texts.values())
    all_names = [str(manuscript_path)] + list(corpus_texts.keys())

    vectorizer = TfidfVectorizer(
        max_features=5000,
        stop_words="english",
        ngram_range=(1, 3),
        min_df=1,
    )
    tfidf_matrix = vectorizer.fit_transform(all_docs)
    sim_matrix = cosine_similarity(tfidf_matrix[0:1], tfidf_matrix[1:]).flatten()

    doc_overlaps = []
    for i, sim in enumerate(sim_matrix):
        doc_overlaps.append({
            "file": all_names[i + 1],
            "similarity": round(float(sim), 4),
            "above_threshold": float(sim) >= threshold,
        })
    doc_overlaps.sort(key=lambda x: x["similarity"], reverse=True)

    # ── Paragraph-level overlap (for high-similarity documents) ───────
    paragraph_overlaps = []
    ms_paragraphs = _split_paragraphs(manuscript_text)

    for doc_overlap in doc_overlaps:
        if doc_overlap["similarity"] < threshold * 0.5:
            continue  # Skip obviously dissimilar docs

        corpus_text = corpus_texts.get(doc_overlap["file"], "")
        corpus_paragraphs = _split_paragraphs(corpus_text)
        if not corpus_paragraphs or not ms_paragraphs:
            continue

        all_paras = ms_paragraphs + corpus_paragraphs
        para_vectorizer = TfidfVectorizer(
            max_features=3000,
            stop_words="english",
            ngram_range=(1, 2),
        )
        try:
            para_tfidf = para_vectorizer.fit_transform(all_paras)
        except ValueError:
            continue

        ms_tfidf = para_tfidf[:len(ms_paragraphs)]
        corp_tfidf = para_tfidf[len(ms_paragraphs):]
        para_sim = cosine_similarity(ms_tfidf, corp_tfidf)

        for mi in range(len(ms_paragraphs)):
            for ci in range(len(corpus_paragraphs)):
                sim = para_sim[mi][ci]
                if sim >= chunk_threshold:
                    paragraph_overlaps.append({
                        "manuscript_paragraph": mi + 1,
                        "manuscript_preview": ms_paragraphs[mi][:120] + "...",
                        "corpus_file": doc_overlap["file"],
                        "corpus_paragraph": ci + 1,
                        "corpus_preview": corpus_paragraphs[ci][:120] + "...",
                        "similarity": round(float(sim), 4),
                    })

    paragraph_overlaps.sort(key=lambda x: x["similarity"], reverse=True)

    return {
        "available": True,
        "corpus_size": len(corpus_texts),
        "threshold": threshold,
        "chunk_threshold": chunk_threshold,
        "document_overlaps": doc_overlaps,
        "paragraph_overlaps": paragraph_overlaps[:20],  # Cap at 20
        "max_document_similarity": max((d["similarity"] for d in doc_overlaps), default=0.0),
        "flagged_documents": sum(1 for d in doc_overlaps if d["above_threshold"]),
        "flagged_paragraphs": len(paragraph_overlaps),
    }


def print_plagiarism_report(report):
    """Print a formatted self-plagiarism check report.

    Args:
        report: Report dict from check_self_plagiarism().
    """
    if "error" in report:
        print(f"Error: {report['error']}")
        return

    print("=" * 60)
    print("SELF-PLAGIARISM CHECK")
    print("=" * 60)
    print(f"  Corpus: {report['corpus_size']} documents compared")
    print(f"  Doc threshold: {report['threshold']:.0%}    "
          f"Para threshold: {report['chunk_threshold']:.0%}")
    print()

    doc_overlaps = report.get("document_overlaps", [])
    if doc_overlaps:
        print("  DOCUMENT-LEVEL SIMILARITY:")
        for d in doc_overlaps:
            icon = "  [!!]" if d["above_threshold"] else "  [OK]"
            fname = Path(d["file"]).parent.name + "/" + Path(d["file"]).name
            print(f"    {icon} {d['similarity']:.1%}  {fname}")
    else:
        print("  No documents in corpus to compare")
    print()

    para_overlaps = report.get("paragraph_overlaps", [])
    if para_overlaps:
        print(f"  PARAGRAPH-LEVEL MATCHES ({len(para_overlaps)} found):")
        for p in para_overlaps[:10]:
            print(f"    [{p['similarity']:.1%}] Para {p['manuscript_paragraph']} ↔ "
                  f"{Path(p['corpus_file']).parent.name} para {p['corpus_paragraph']}")
            print(f"           MS:   {p['manuscript_preview'][:80]}")
            print(f"           CORP: {p['corpus_preview'][:80]}")
            print()

    flagged = report.get("flagged_documents", 0)
    if flagged > 0:
        print(f"  ⚠ {flagged} document(s) above {report['threshold']:.0%} similarity threshold")
        print("  → Rewrite overlapping sections or add proper self-citations")
    else:
        print("  All documents below similarity threshold — OK")
    print()
