"""Dependency-free BM25 scorer shared by ``agent_search`` and ``skill_search``.

The scikit-learn TF-IDF path in those tools is optional and is absent in the
shared NeqSim interpreter and in CI, so this is the retriever that actually runs.
Plain Jaccard treated "neqsim", "model" and "process" as informative as
"unisim"; BM25's inverse document frequency fixes that, and a light suffix
stem lets "compression" match "compressor" and "hydrates" match "hydrate".
Scores are normalised to 0..1 per query so callers can add curated boosts.
"""
from __future__ import annotations

import math
import re
from typing import Dict, List, Sequence

_TOKEN_RE = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")
_SUFFIXES = ("ations", "ation", "ising", "izing", "ings", "ness", "ies", "ing", "ers", "ion",
             "ed", "es", "er", "ly", "s")
# Words that carry no routing signal in this catalogue.
STOPWORDS = frozenset("""
a an and are as at be by for from in into is it its of on or that the this to with
neqsim use when using used task tasks agent agents skill skills model models results
""".split())


def stem(token: str) -> str:
    if len(token) <= 4 or "-" in token or token.isdigit():
        return token
    for suffix in _SUFFIXES:
        if token.endswith(suffix) and len(token) - len(suffix) >= 4:
            return token[: -len(suffix)]
    return token


def tokenize(text: str) -> List[str]:
    out: List[str] = []
    for tok in _TOKEN_RE.findall(text.lower()):
        if tok in STOPWORDS:
            continue
        out.append(stem(tok))
        # Hyphenated ids also contribute their parts so "cfd-coupling" matches "cfd".
        if "-" in tok:
            out.extend(stem(p) for p in tok.split("-") if p and p not in STOPWORDS)
    return out


class BM25:
    """Okapi BM25 over pre-tokenised documents, plus a coverage term.

    ``b`` is low because the corpus is catalogue descriptions of very different
    length: a comprehensive skill must not lose to a narrow one merely for
    covering more ground. The coverage term rewards a document that matches
    more *distinct* query terms, which is what routing cares about: three
    shared words each seen once beat one shared word seen three times.
    """

    def __init__(self, docs: Sequence[str], k1: float = 1.5, b: float = 0.15,
                 coverage_weight: float = 0.5) -> None:
        self.k1 = k1
        self.b = b
        self.coverage_weight = coverage_weight
        self.tokens = [tokenize(d) for d in docs]
        self.lengths = [len(t) for t in self.tokens]
        self.avgdl = (sum(self.lengths) / len(self.lengths)) if self.lengths else 1.0
        df: Dict[str, int] = {}
        for toks in self.tokens:
            for t in set(toks):
                df[t] = df.get(t, 0) + 1
        n = len(self.tokens)
        self.idf = {t: math.log(1.0 + (n - f + 0.5) / (f + 0.5)) for t, f in df.items()}
        self.tf: List[Dict[str, int]] = []
        for toks in self.tokens:
            counts: Dict[str, int] = {}
            for t in toks:
                counts[t] = counts.get(t, 0) + 1
            self.tf.append(counts)

    def scores(self, query: str) -> List[float]:
        q = tokenize(query)
        if not q or not self.tokens:
            return [0.0] * len(self.tokens)
        q_distinct = set(q)
        q_idf_total = sum(self.idf.get(t, 0.0) for t in q_distinct) or 1.0
        out: List[float] = []
        for tf, dl in zip(self.tf, self.lengths):
            score = 0.0
            covered = 0.0
            norm = self.k1 * (1.0 - self.b + self.b * dl / self.avgdl)
            for t in q:
                f = tf.get(t)
                if not f:
                    continue
                score += self.idf.get(t, 0.0) * f * (self.k1 + 1.0) / (f + norm)
            for t in q_distinct:
                if t in tf:
                    covered += self.idf.get(t, 0.0)
            # IDF-weighted share of the query vocabulary this document covers.
            score *= 1.0 + self.coverage_weight * covered / q_idf_total
            out.append(score)
        peak = max(out) if out else 0.0
        return [s / peak for s in out] if peak > 0 else out
