"""Emit a 'How to cite NeqSim' block from CITATION.cff.

Reads the repository's CITATION.cff (or a configured path) and produces a
markdown citation block for the book backmatter or per-chapter footer.

Usage::

    block = render_citation_block(repo_root / "CITATION.cff")

If the CFF file is missing, returns a minimal NeqSim citation pointing at
the GitHub repository.
"""
from __future__ import annotations

from pathlib import Path

try:
    import yaml  # type: ignore
except Exception:
    yaml = None


_FALLBACK = """\
## How to cite NeqSim

If you use results produced with NeqSim in a publication, please cite the
software as follows:

> Solbraa, E. *et al.* **NeqSim: A library for thermodynamic and process
> simulation.** Equinor / NTNU, 2026. <https://github.com/equinor/neqsim>

```bibtex
@software{neqsim,
  author = {Solbraa, Even and contributors},
  title  = {{NeqSim: A library for thermodynamic and process simulation}},
  year   = {2026},
  url    = {https://github.com/equinor/neqsim}
}
```
"""


def _format_authors(authors) -> str:
    parts = []
    for a in authors or []:
        if isinstance(a, dict):
            given = a.get("given-names", "").strip()
            family = a.get("family-names", "").strip()
            if family and given:
                parts.append(f"{family}, {given}")
            elif family:
                parts.append(family)
            elif "name" in a:
                parts.append(a["name"])
    if not parts:
        return "Solbraa, E. and contributors"
    if len(parts) > 4:
        return parts[0] + " *et al.*"
    return "; ".join(parts)


def render_citation_block(cff_path: Path | str | None = None) -> str:
    if not cff_path:
        return _FALLBACK
    p = Path(cff_path)
    if not p.exists() or yaml is None:
        return _FALLBACK
    try:
        data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
    except Exception:
        return _FALLBACK

    title = data.get("title", "NeqSim")
    version = data.get("version", "")
    year = data.get("date-released", "")
    if hasattr(year, "year"):
        year = year.year
    elif isinstance(year, str):
        year = year[:4]
    url = data.get("repository-code") or data.get("url") or "https://github.com/equinor/neqsim"
    doi = data.get("doi")
    authors = _format_authors(data.get("authors"))

    cite_line = f"> {authors}. **{title}**"
    if version:
        cite_line += f" (v{version})"
    if year:
        cite_line += f". {year}"
    cite_line += f". <{url}>"
    if doi:
        cite_line += f". https://doi.org/{doi}"
    cite_line += "."

    bibtex_key = "neqsim" + (str(version).replace(".", "") if version else "")
    bibtex = [
        f"@software{{{bibtex_key},",
        f"  author  = {{{authors.replace('*et al.*', 'and others')}}},",
        f"  title   = {{{{{title}}}}},",
    ]
    if version:
        bibtex.append(f"  version = {{{version}}},")
    if year:
        bibtex.append(f"  year    = {{{year}}},")
    bibtex.append(f"  url     = {{{url}}},")
    if doi:
        bibtex.append(f"  doi     = {{{doi}}},")
    bibtex.append("}")

    return (
        "## How to cite NeqSim\n\n"
        "If you use results produced with NeqSim in a publication, please cite\n"
        "the software as follows:\n\n"
        + cite_line
        + "\n\n```bibtex\n"
        + "\n".join(bibtex)
        + "\n```\n"
    )


def write_citation_appendix(book_dir, cff_path: Path | str | None = None) -> Path:
    book_dir = Path(book_dir)
    bm = book_dir / "backmatter"
    bm.mkdir(exist_ok=True)
    out = bm / "how_to_cite_neqsim.md"
    out.write_text(
        "<!-- @auto-generated: citation_block -->\n" + render_citation_block(cff_path),
        encoding="utf-8",
    )
    print(f"[citation_block] wrote {out}")
    return out
