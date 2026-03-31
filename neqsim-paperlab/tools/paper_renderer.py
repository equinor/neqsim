"""
Paper Renderer — Convert Markdown manuscript to LaTeX and Word.

Reads a journal profile YAML and transforms paper.md into
submission-ready formats.

Also provides figure and table format validation to catch issues early.

Usage:
    python paper_renderer.py render papers/tpflash_2026/ --journal fluid_phase_equilibria
    python paper_renderer.py check papers/tpflash_2026/ --journal fluid_phase_equilibria
    python paper_renderer.py validate-figures papers/tpflash_2026/ --journal fluid_phase_equilibria
"""

import json
import re
import sys
import argparse
from pathlib import Path
from typing import Dict, List, Optional

try:
    import yaml
except ImportError:
    yaml = None
    print("Warning: PyYAML not installed. Install with: pip install pyyaml")


def load_journal_profile(journal_name, journals_dir="journals"):
    """Load a journal profile YAML file."""
    if yaml is None:
        raise ImportError("PyYAML is required. Install with: pip install pyyaml")

    profile_path = Path(journals_dir) / f"{journal_name}.yaml"
    if not profile_path.exists():
        available = [p.stem for p in Path(journals_dir).glob("*.yaml")]
        raise FileNotFoundError(
            f"Journal profile not found: {profile_path}\n"
            f"Available: {available}"
        )

    with open(profile_path) as f:
        return yaml.safe_load(f)


def parse_manuscript(paper_md_path):
    """Parse a Markdown manuscript into sections."""
    text = Path(paper_md_path).read_text(encoding="utf-8")

    sections = []
    current_section = None
    current_content = []

    for line in text.split("\n"):
        # Detect section headers (## or ###)
        header_match = re.match(r'^(#{1,3})\s+(.+)$', line)
        if header_match:
            # Save previous section
            if current_section is not None:
                sections.append({
                    "level": len(current_section["hashes"]),
                    "title": current_section["title"],
                    "content": "\n".join(current_content).strip(),
                })
            current_section = {
                "hashes": header_match.group(1),
                "title": header_match.group(2).strip(),
            }
            current_content = []
        else:
            current_content.append(line)

    # Don't forget last section
    if current_section is not None:
        sections.append({
            "level": len(current_section["hashes"]),
            "title": current_section["title"],
            "content": "\n".join(current_content).strip(),
        })

    return sections


def _figure_counter():
    """Global figure counter for LaTeX figure numbering."""
    if not hasattr(_figure_counter, 'n'):
        _figure_counter.n = 0
    _figure_counter.n += 1
    return _figure_counter.n


def _reset_figure_counter():
    """Reset figure counter for a new render pass."""
    _figure_counter.n = 0


def markdown_to_latex(md_text):
    """Convert Markdown content to LaTeX.

    Handles: bold, italic, inline math, display math, code blocks,
    lists, tables, figures, horizontal rules, and HTML comments.
    """
    tex = md_text

    # Remove HTML comments
    tex = re.sub(r'<!--.*?-->', '', tex, flags=re.DOTALL)

    # Remove horizontal rules (---)
    tex = re.sub(r'^\s*---\s*$', '', tex, flags=re.MULTILINE)

    # Display math: $$ ... $$ -> \begin{equation} ... \end{equation}
    tex = re.sub(
        r'\$\$(.*?)\$\$',
        r'\\begin{equation}\n\1\n\\end{equation}',
        tex,
        flags=re.DOTALL,
    )

    # Inline math: $ ... $ -> unchanged (LaTeX handles it)

    # Bold: **text** -> \textbf{text}
    tex = re.sub(r'\*\*(.+?)\*\*', r'\\textbf{\1}', tex)

    # Italic: *text* -> \textit{text}
    tex = re.sub(r'\*(.+?)\*', r'\\textit{\1}', tex)

    # Inline code: `text` -> \texttt{text}
    tex = re.sub(r'`([^`]+)`', r'\\texttt{\1}', tex)

    # Convert markdown images to LaTeX figures
    # Pattern: ![caption](path)
    def _replace_figure(m):
        caption = m.group(1)
        path = m.group(2)
        # Strip extension for includegraphics
        path_no_ext = re.sub(r'\.(png|pdf|eps|jpg|jpeg|tif|tiff)$', '', path)
        return (
            "\\begin{figure}[htbp]\n"
            "\\centering\n"
            f"\\includegraphics[width=0.9\\textwidth]{{{path_no_ext}}}\n"
            f"\\caption{{{caption}}}\n"
            "\\end{figure}"
        )
    tex = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', _replace_figure, tex)

    # Convert markdown tables to LaTeX tabular
    lines = tex.split("\n")
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        # Check if this is a table header row (contains |)
        if re.match(r'^\s*\|', line) and i + 1 < len(lines) and re.match(r'^\s*\|[\s:|-]+\|', lines[i + 1]):
            # Collect all table rows
            table_rows = []
            while i < len(lines) and re.match(r'^\s*\|', lines[i]):
                table_rows.append(lines[i])
                i += 1

            # Parse header
            header = table_rows[0]
            # Skip separator row (row 1)
            data_rows = table_rows[2:] if len(table_rows) > 2 else []

            # Count columns from header
            cells = [c.strip() for c in header.split('|')]
            cells = [c for c in cells if c]  # Remove empty from leading/trailing |
            n_cols = len(cells)

            # Parse alignment from separator row
            sep_row = table_rows[1] if len(table_rows) > 1 else ''
            sep_cells = [c.strip() for c in sep_row.split('|')]
            sep_cells = [c for c in sep_cells if c]
            aligns = []
            for sc in sep_cells:
                sc = sc.strip('-').strip()
                if sc.startswith(':') and sc.endswith(':'):
                    aligns.append('c')
                elif sc.endswith(':'):
                    aligns.append('r')
                else:
                    aligns.append('l')
            while len(aligns) < n_cols:
                aligns.append('l')

            col_spec = ''.join(aligns[:n_cols])

            # Search backward for a table caption (line starting with **Table)
            caption_line = None
            for j in range(len(new_lines) - 1, max(len(new_lines) - 4, -1), -1):
                if j >= 0 and re.match(r'^\\textbf\{Table', new_lines[j]):
                    caption_line = j
                    break

            caption_text = None
            if caption_line is not None:
                caption_text = new_lines.pop(caption_line).strip()

            # Build LaTeX table
            new_lines.append("\\begin{table}[htbp]")
            new_lines.append("\\centering")
            if caption_text:
                new_lines.append(f"\\caption{{{caption_text}}}")
            new_lines.append(f"\\begin{{tabular}}{{{col_spec}}}")
            new_lines.append("\\toprule")

            # Header row
            header_cells = [c.strip() for c in header.split('|')]
            header_cells = [c for c in header_cells if c]
            new_lines.append(' & '.join(header_cells) + ' \\\\')
            new_lines.append("\\midrule")

            # Data rows
            for dr in data_rows:
                data_cells = [c.strip() for c in dr.split('|')]
                data_cells = [c for c in data_cells if c]
                new_lines.append(' & '.join(data_cells) + ' \\\\')

            new_lines.append("\\bottomrule")
            new_lines.append("\\end{tabular}")
            new_lines.append("\\end{table}")
            continue
        else:
            new_lines.append(line)
            i += 1

    tex = "\n".join(new_lines)

    # Bullet lists
    lines = tex.split("\n")
    in_list = False
    new_lines = []
    for line in lines:
        if re.match(r'^\s*[-*]\s', line) and not re.match(r'^\s*---', line):
            if not in_list:
                new_lines.append("\\begin{itemize}")
                in_list = True
            item_text = re.sub(r'^\s*[-*]\s+', '', line)
            new_lines.append(f"  \\item {item_text}")
        else:
            if in_list:
                new_lines.append("\\end{itemize}")
                in_list = False
            new_lines.append(line)
    if in_list:
        new_lines.append("\\end{itemize}")
    tex = "\n".join(new_lines)

    # Numbered lists
    lines = tex.split("\n")
    in_enum = False
    new_lines = []
    for line in lines:
        if re.match(r'^\s*\d+\.\s', line):
            if not in_enum:
                new_lines.append("\\begin{enumerate}")
                in_enum = True
            item_text = re.sub(r'^\s*\d+\.\s+', '', line)
            new_lines.append(f"  \\item {item_text}")
        else:
            if in_enum:
                new_lines.append("\\end{enumerate}")
                in_enum = False
            new_lines.append(line)
    if in_enum:
        new_lines.append("\\end{enumerate}")
    tex = "\n".join(new_lines)

    # Post-process: merge consecutive enumerate/itemize environments
    # (happens when items are separated by blank lines in markdown)
    tex = re.sub(
        r'\\end\{enumerate\}\s*\\begin\{enumerate\}',
        '',
        tex,
    )
    tex = re.sub(
        r'\\end\{itemize\}\s*\\begin\{itemize\}',
        '',
        tex,
    )

    return tex


def render_latex(paper_dir, journal_profile, output_dir=None):
    """Render a Markdown manuscript to LaTeX.

    Args:
        paper_dir: Path to paper directory containing paper.md
        journal_profile: Journal profile dict
        output_dir: Output directory (default: paper_dir/submission/)

    Returns:
        Path to generated .tex file
    """
    paper_dir = Path(paper_dir)
    if output_dir is None:
        output_dir = paper_dir / "submission"
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    sections = parse_manuscript(paper_dir / "paper.md")

    # Build LaTeX document
    latex_class = journal_profile.get("latex_class", "elsarticle")
    latex_options = journal_profile.get("latex_options", "review,3p")

    lines = []
    lines.append(f"\\documentclass[{latex_options}]{{{latex_class}}}")
    lines.append("")
    lines.append("% Packages")
    lines.append("\\usepackage{amsmath,amssymb}")
    lines.append("\\usepackage{graphicx}")
    lines.append("\\usepackage{booktabs}")
    lines.append("\\usepackage{hyperref}")

    # Citation package: natbib for author-year, default for numbered
    citation_style = journal_profile.get("citation_style", "numbered")
    if citation_style == "authoryear":
        lines.append("\\usepackage[authoryear]{natbib}")

    if journal_profile.get("line_numbering_required"):
        lines.append("\\usepackage{lineno}")

    if journal_profile.get("double_spacing_required"):
        lines.append("\\usepackage{setspace}")

    lines.append("")
    lines.append(f"\\journal{{{journal_profile['journal_name']}}}")
    lines.append("")
    lines.append("\\begin{document}")

    if journal_profile.get("line_numbering_required"):
        lines.append("\\linenumbers")

    if journal_profile.get("double_spacing_required"):
        lines.append("\\doublespacing")

    lines.append("")

    # Front matter
    lines.append("\\begin{frontmatter}")
    lines.append("")

    # Find title (first H1 section)
    title = "TITLE PLACEHOLDER"
    for sec in sections:
        if sec["level"] == 1:
            title = sec["title"]
            break
    lines.append(f"\\title{{{title}}}")
    lines.append("")

    # Author placeholder
    lines.append("\\author[inst1]{First Author\\corref{cor1}}")
    lines.append("\\cortext[cor1]{Corresponding author}")
    lines.append("\\ead{email@institution.no}")
    lines.append("")
    lines.append("\\affiliation[inst1]{organization={Department, Institution},")
    lines.append("                     city={City},")
    lines.append("                     country={Country}}")
    lines.append("")

    # Abstract
    for sec in sections:
        if "abstract" in sec["title"].lower():
            lines.append("\\begin{abstract}")
            lines.append(markdown_to_latex(sec["content"]))
            lines.append("\\end{abstract}")
            lines.append("")
            break

    # Keywords
    for sec in sections:
        if "keyword" in sec["title"].lower():
            # Clean content: remove HR markers and blank lines
            kw_content = re.sub(r'^\s*---\s*$', '', sec["content"], flags=re.MULTILINE).strip()
            keywords = kw_content.replace(",", " \\sep ")
            lines.append("\\begin{keyword}")
            lines.append(keywords)
            lines.append("\\end{keyword}")
            lines.append("")
            break

    # Highlights
    if journal_profile.get("highlights_required"):
        for sec in sections:
            if "highlight" in sec["title"].lower():
                lines.append("\\begin{highlights}")
                for item_line in sec["content"].split("\n"):
                    item = re.sub(r'^\s*[-*]\s+', '', item_line).strip()
                    if item:
                        lines.append(f"\\item {item}")
                lines.append("\\end{highlights}")
                lines.append("")
                break

    lines.append("\\end{frontmatter}")
    lines.append("")

    # Body sections
    section_cmds = {1: "\\section", 2: "\\section", 3: "\\subsection"}
    skip_sections = {"abstract", "keywords", "keyword", "highlights"}

    for sec in sections:
        title_lower = sec["title"].lower()
        # Skip front matter sections already handled
        if title_lower in skip_sections:
            continue
        # Skip the H1 title (already in \title{})
        if sec["level"] == 1:
            continue
        # Skip references section (handled by \bibliography)
        if "references" in title_lower:
            continue
        cmd = section_cmds.get(sec["level"], "\\subsection")
        lines.append(f"{cmd}{{{sec['title']}}}")
        lines.append("")
        lines.append(markdown_to_latex(sec["content"]))
        lines.append("")

    # Bibliography
    ref_style = journal_profile.get("reference_style", "elsarticle-num")
    lines.append(f"\\bibliographystyle{{{ref_style}}}")
    lines.append("\\bibliography{refs}")
    lines.append("")
    lines.append("\\end{document}")

    full_tex = "\n".join(lines)

    # Post-process citations based on journal citation style
    citation_style = journal_profile.get("citation_style", "numbered")
    if citation_style == "authoryear":
        # Convert \cite{key} to \citep{key} for natbib author-year
        # Also handle \\cite (double-backslash from markdown escaping)
        full_tex = re.sub(r'\\\\cite\{', r'\\citep{', full_tex)
        full_tex = re.sub(r'(?<!\\)\\cite\{', r'\\citep{', full_tex)

    # Write output
    tex_file = output_dir / "paper.tex"
    tex_file.write_text(full_tex, encoding="utf-8")

    # Copy refs.bib if it exists
    refs_src = paper_dir / "refs.bib"
    if refs_src.exists():
        refs_dst = output_dir / "refs.bib"
        refs_dst.write_text(refs_src.read_text(encoding="utf-8"), encoding="utf-8")

    return tex_file


def validate_figures(paper_dir, journal_profile):
    """Validate all figures in a paper directory against journal requirements.

    Checks:
      - File format is in the journal's allowed list
      - DPI meets minimum requirement (for raster images)
      - Image is readable (not corrupted)
      - Image dimensions are reasonable (not tiny thumbnails)
      - Figure files are referenced in the manuscript text
      - Figure numbering is sequential (fig1, fig2, ... with no gaps)

    Returns:
        List of check results with PASS/FAIL/WARN status
    """
    paper_dir = Path(paper_dir)
    fig_dir = paper_dir / "figures"
    checks = []

    if not fig_dir.exists():
        checks.append({
            "check": "Figures directory exists",
            "status": "WARN",
            "detail": "No figures/ directory found",
        })
        return checks

    allowed_formats = [fmt.lower().lstrip('.')
                       for fmt in journal_profile.get("figure_formats", ["png", "pdf", "eps", "tif"])]
    min_dpi = journal_profile.get("figure_dpi_min", 300)

    # Collect figure files (skip subdirectories like validation/)
    fig_files = []
    for f in sorted(fig_dir.iterdir()):
        if f.is_file() and f.suffix.lower().lstrip('.') in (
            'png', 'jpg', 'jpeg', 'tif', 'tiff', 'eps', 'pdf', 'svg'
        ):
            fig_files.append(f)

    if not fig_files:
        checks.append({
            "check": "Figures present",
            "status": "WARN",
            "detail": "No figure files found in figures/",
        })
        return checks

    checks.append({
        "check": "Figures present",
        "status": "PASS",
        "detail": f"{len(fig_files)} figure files found",
    })

    # Try to import PIL for image validation
    try:
        from PIL import Image
        has_pil = True
    except ImportError:
        has_pil = False

    format_issues = []
    dpi_issues = []
    corrupt_files = []
    small_files = []
    fig_numbers = []

    for fig_path in fig_files:
        ext = fig_path.suffix.lower().lstrip('.')

        # Check format
        if ext not in allowed_formats:
            format_issues.append(f"{fig_path.name} ({ext})")

        # Extract figure number for sequencing check
        num_match = re.search(r'(\d+)', fig_path.stem)
        if num_match:
            fig_numbers.append(int(num_match.group(1)))

        # Image-specific checks (raster formats only)
        if ext in ('png', 'jpg', 'jpeg', 'tif', 'tiff') and has_pil:
            try:
                with Image.open(fig_path) as img:
                    width_px, height_px = img.size

                    # Check DPI
                    dpi_info = img.info.get('dpi', (None, None))
                    if dpi_info and dpi_info[0] is not None:
                        dpi_x = float(dpi_info[0])
                        if round(dpi_x) < min_dpi:
                            dpi_issues.append(
                                f"{fig_path.name}: {dpi_x:.0f} DPI (min: {min_dpi})"
                            )
                    else:
                        # No DPI metadata — estimate from pixel dimensions
                        # Assume figure will be printed at ~6 inches wide
                        estimated_dpi = width_px / 6.0
                        if estimated_dpi < min_dpi:
                            dpi_issues.append(
                                f"{fig_path.name}: ~{estimated_dpi:.0f} DPI estimated "
                                f"(no metadata, {width_px}x{height_px} px, min: {min_dpi})"
                            )

                    # Check for suspiciously small images
                    if width_px < 200 or height_px < 150:
                        small_files.append(
                            f"{fig_path.name}: {width_px}x{height_px} px (too small for publication)"
                        )

            except Exception as e:
                corrupt_files.append(f"{fig_path.name}: {e}")

        elif ext in ('png', 'jpg', 'jpeg', 'tif', 'tiff') and not has_pil:
            # No PIL — check file size as a rough proxy
            file_size_kb = fig_path.stat().st_size / 1024
            if file_size_kb < 5:
                small_files.append(
                    f"{fig_path.name}: {file_size_kb:.1f} KB (suspiciously small)"
                )

    # Report format issues
    if format_issues:
        checks.append({
            "check": "Figure formats",
            "status": "FAIL",
            "detail": f"Disallowed formats (allowed: {', '.join(allowed_formats)}): "
                      f"{', '.join(format_issues)}",
        })
    else:
        checks.append({
            "check": "Figure formats",
            "status": "PASS",
            "detail": f"All figures use allowed formats ({', '.join(allowed_formats)})",
        })

    # Report DPI issues
    if dpi_issues:
        checks.append({
            "check": "Figure DPI",
            "status": "FAIL",
            "detail": f"Below minimum {min_dpi} DPI: {'; '.join(dpi_issues)}",
        })
    elif has_pil:
        checks.append({
            "check": "Figure DPI",
            "status": "PASS",
            "detail": f"All raster figures meet {min_dpi} DPI minimum",
        })
    else:
        checks.append({
            "check": "Figure DPI",
            "status": "WARN",
            "detail": "Install Pillow (pip install Pillow) for DPI validation",
        })

    # Report corrupt files
    if corrupt_files:
        checks.append({
            "check": "Figure readability",
            "status": "FAIL",
            "detail": f"Unreadable files: {'; '.join(corrupt_files)}",
        })

    # Report small files
    if small_files:
        checks.append({
            "check": "Figure dimensions",
            "status": "WARN",
            "detail": f"Possibly too small: {'; '.join(small_files)}",
        })

    # Check sequential numbering
    if fig_numbers:
        fig_numbers_sorted = sorted(set(fig_numbers))
        expected = list(range(fig_numbers_sorted[0],
                              fig_numbers_sorted[0] + len(fig_numbers_sorted)))
        if fig_numbers_sorted != expected:
            missing = set(expected) - set(fig_numbers_sorted)
            checks.append({
                "check": "Figure numbering",
                "status": "WARN",
                "detail": f"Non-sequential: found {fig_numbers_sorted}, "
                          f"missing: {sorted(missing) if missing else 'none (duplicates?)'}",
            })
        else:
            checks.append({
                "check": "Figure numbering",
                "status": "PASS",
                "detail": f"Sequential: {fig_numbers_sorted[0]}–{fig_numbers_sorted[-1]}",
            })

    # Check that all figure files are referenced in the manuscript
    paper_file = paper_dir / "paper.md"
    if paper_file.exists():
        text = paper_file.read_text(encoding="utf-8")
        unreferenced = []
        for fig_path in fig_files:
            # Check if the filename (with or without extension) appears in text
            stem = fig_path.stem
            name = fig_path.name
            if name not in text and stem not in text:
                unreferenced.append(fig_path.name)

        if unreferenced:
            checks.append({
                "check": "Figures referenced in text",
                "status": "WARN",
                "detail": f"Not referenced in paper.md: {', '.join(unreferenced)}",
            })
        else:
            checks.append({
                "check": "Figures referenced in text",
                "status": "PASS",
                "detail": "All figure files are referenced in paper.md",
            })

    return checks


def validate_tables(paper_dir, journal_profile):
    """Validate all tables in the manuscript against journal requirements.

    Checks:
      - Tables have captions (\"**Table N:**\" pattern)
      - Tables use consistent column counts across all rows
      - Tables are referenced in-text (\"Table N\")
      - No vertical rules (Markdown tables should not use ||)
      - Tables are numbered sequentially
      - Highlight character limits (if applicable)

    Returns:
        List of check results with PASS/FAIL/WARN status
    """
    paper_dir = Path(paper_dir)
    paper_file = paper_dir / "paper.md"
    checks = []

    if not paper_file.exists():
        checks.append({
            "check": "Manuscript exists (table check)",
            "status": "FAIL",
            "detail": "paper.md not found",
        })
        return checks

    text = paper_file.read_text(encoding="utf-8")
    lines = text.split("\n")

    # Find all markdown tables (sequences of lines starting with |)
    tables = []
    current_table = []
    current_table_start = -1

    for i, line in enumerate(lines):
        if re.match(r'^\s*\|', line):
            if not current_table:
                current_table_start = i + 1  # 1-indexed
            current_table.append(line)
        else:
            if current_table:
                tables.append({
                    "lines": current_table,
                    "start_line": current_table_start,
                    "end_line": i,
                })
                current_table = []
                current_table_start = -1

    if current_table:
        tables.append({
            "lines": current_table,
            "start_line": current_table_start,
            "end_line": len(lines),
        })

    if not tables:
        checks.append({
            "check": "Tables present",
            "status": "WARN",
            "detail": "No markdown tables found in paper.md",
        })
        return checks

    checks.append({
        "check": "Tables present",
        "status": "PASS",
        "detail": f"{len(tables)} tables found",
    })

    # Check each table for column consistency
    col_issues = []
    vertical_rule_issues = []

    for idx, table in enumerate(tables, 1):
        col_counts = []
        for row_line in table["lines"]:
            # Skip separator rows
            if re.match(r'^\s*\|[\s:|-]+\|?\s*$', row_line):
                continue
            # Mask LaTeX math ($ ... $) to avoid counting | inside math as column delimiters
            masked = re.sub(r'\$[^$]*\$', 'MATH', row_line)
            # Count columns: split by | and remove empties from edges
            cells = masked.split('|')
            # Remove leading/trailing empty from | at edges
            if cells and cells[0].strip() == '':
                cells = cells[1:]
            if cells and cells[-1].strip() == '':
                cells = cells[:-1]
            col_counts.append(len(cells))

            # Check for double pipes (vertical rules)
            if '||' in row_line:
                vertical_rule_issues.append(
                    f"Table at line {table['start_line']}: double pipe '||' found"
                )

        # Check column consistency
        if col_counts and len(set(col_counts)) > 1:
            col_issues.append(
                f"Table at line {table['start_line']}: inconsistent columns "
                f"{col_counts} (rows have different column counts)"
            )

    if col_issues:
        checks.append({
            "check": "Table column consistency",
            "status": "FAIL",
            "detail": "; ".join(col_issues),
        })
    else:
        checks.append({
            "check": "Table column consistency",
            "status": "PASS",
            "detail": "All tables have consistent column counts",
        })

    if vertical_rule_issues:
        checks.append({
            "check": "Table style (no vertical rules)",
            "status": "WARN",
            "detail": "; ".join(vertical_rule_issues),
        })

    # Check that tables have captions
    table_captions = {}
    for m in re.finditer(r'\*\*Table\s+(\d+)[.:]\*\*', text):
        table_captions[int(m.group(1))] = True
    # Also match "**Table N:**" pattern used in the Gibbs paper
    for m in re.finditer(r'\*\*Table\s+(\d+)\*\*:', text):
        table_captions[int(m.group(1))] = True
    # Also match "**Table N.**"
    for m in re.finditer(r'\*\*Table\s+(\d+)\.\*\*', text):
        table_captions[int(m.group(1))] = True

    if len(table_captions) < len(tables):
        checks.append({
            "check": "Table captions",
            "status": "WARN",
            "detail": f"Found {len(table_captions)} captions for {len(tables)} tables "
                      f"(some tables may lack **Table N:** captions)",
        })
    else:
        checks.append({
            "check": "Table captions",
            "status": "PASS",
            "detail": f"All {len(table_captions)} tables have captions",
        })

    # Check sequential table numbering
    if table_captions:
        nums = sorted(table_captions.keys())
        expected = list(range(nums[0], nums[0] + len(nums)))
        if nums != expected:
            missing = set(expected) - set(nums)
            checks.append({
                "check": "Table numbering",
                "status": "WARN",
                "detail": f"Non-sequential: found {nums}, "
                          f"missing: {sorted(missing) if missing else 'duplicates?'}",
            })
        else:
            checks.append({
                "check": "Table numbering",
                "status": "PASS",
                "detail": f"Sequential: {nums[0]}–{nums[-1]}",
            })

    # Check that tables are referenced in text ("Table N" outside of captions)
    table_refs_in_text = set()
    for m in re.finditer(r'Table\s+(\d+)', text):
        table_refs_in_text.add(int(m.group(1)))

    defined_but_unreferenced = set(table_captions.keys()) - table_refs_in_text
    if defined_but_unreferenced:
        checks.append({
            "check": "Tables referenced in text",
            "status": "WARN",
            "detail": f"Tables with captions but no in-text reference: "
                      f"{sorted(defined_but_unreferenced)}",
        })

    # Check highlights character limits
    highlights_max_chars = journal_profile.get("highlights_max_chars_each", 85)
    highlights_max = journal_profile.get("highlights_max", 5)
    highlights_min = journal_profile.get("highlights_min", 3)

    for sec_text in text.split("## "):
        if sec_text.lower().startswith("highlights"):
            highlight_items = []
            for line in sec_text.split("\n"):
                item = re.sub(r'^\s*[-*]\s+', '', line).strip()
                if item and not item.lower().startswith("highlights"):
                    highlight_items.append(item)

            if highlight_items:
                too_long = []
                for item in highlight_items:
                    if len(item) > highlights_max_chars:
                        too_long.append(
                            f"'{item[:40]}...' ({len(item)} chars, max: {highlights_max_chars})"
                        )
                if too_long:
                    checks.append({
                        "check": "Highlights character limit",
                        "status": "FAIL",
                        "detail": "; ".join(too_long),
                    })
                else:
                    checks.append({
                        "check": "Highlights character limit",
                        "status": "PASS",
                        "detail": f"All {len(highlight_items)} highlights within "
                                  f"{highlights_max_chars} char limit",
                    })

                if len(highlight_items) < highlights_min:
                    checks.append({
                        "check": "Highlights count",
                        "status": "FAIL",
                        "detail": f"{len(highlight_items)} highlights "
                                  f"(min: {highlights_min})",
                    })
                elif len(highlight_items) > highlights_max:
                    checks.append({
                        "check": "Highlights count",
                        "status": "FAIL",
                        "detail": f"{len(highlight_items)} highlights "
                                  f"(max: {highlights_max})",
                    })
            break

    return checks


def check_compliance(paper_dir, journal_profile):
    """Check manuscript compliance with journal requirements.

    Returns:
        List of check results with PASS/FAIL status
    """
    paper_dir = Path(paper_dir)
    paper_file = paper_dir / "paper.md"

    if not paper_file.exists():
        return [{"check": "Manuscript exists", "status": "FAIL", "detail": "paper.md not found"}]

    text = paper_file.read_text(encoding="utf-8")
    sections = parse_manuscript(paper_file)
    checks = []

    # Abstract length
    abstract_max = journal_profile.get("abstract_words_max", 200)
    for sec in sections:
        if "abstract" in sec["title"].lower():
            word_count = len(sec["content"].split())
            checks.append({
                "check": "Abstract length",
                "status": "PASS" if word_count <= abstract_max else "FAIL",
                "detail": f"{word_count}/{abstract_max} words",
            })
            break
    else:
        checks.append({
            "check": "Abstract present",
            "status": "FAIL",
            "detail": "No abstract section found",
        })

    # Keywords
    kw_max = journal_profile.get("keywords_max", 6)
    for sec in sections:
        if "keyword" in sec["title"].lower():
            keywords = [k.strip() for k in sec["content"].split(",") if k.strip()]
            checks.append({
                "check": "Keywords count",
                "status": "PASS" if len(keywords) <= kw_max else "FAIL",
                "detail": f"{len(keywords)}/{kw_max} keywords",
            })
            break

    # Highlights
    if journal_profile.get("highlights_required"):
        has_highlights = any("highlight" in s["title"].lower() for s in sections)
        checks.append({
            "check": "Highlights present",
            "status": "PASS" if has_highlights else "FAIL",
        })

    # Required sections
    required_words = ["introduction", "method", "result", "conclusion"]
    section_titles_lower = [s["title"].lower() for s in sections]
    for req in required_words:
        found = any(req in t for t in section_titles_lower)
        checks.append({
            "check": f"Section '{req}*' present",
            "status": "PASS" if found else "FAIL",
        })

    # References file
    refs_file = paper_dir / "refs.bib"
    checks.append({
        "check": "refs.bib exists",
        "status": "PASS" if refs_file.exists() else "FAIL",
    })

    # Data availability statement
    if journal_profile.get("data_availability_required"):
        has_da = "data availability" in text.lower()
        checks.append({
            "check": "Data availability statement",
            "status": "PASS" if has_da else "FAIL",
        })

    # Figures directory
    fig_dir = paper_dir / "figures"
    checks.append({
        "check": "Figures directory exists",
        "status": "PASS" if fig_dir.exists() else "WARN",
    })

    # Approved claims file
    claims_file = paper_dir / "approved_claims.json"
    checks.append({
        "check": "approved_claims.json exists",
        "status": "PASS" if claims_file.exists() else "WARN",
        "detail": "Needed for claim tracing",
    })

    # Cross-reference validation: detect orphan Fig./Table/Eq. references
    # Find all in-text references to Fig. N, Table N, Eq. N
    fig_refs = set(re.findall(r'Fig\.\s*(\d+)', text))
    table_refs = set(re.findall(r'Table\s+(\d+)', text))
    eq_refs = set(re.findall(r'Eq\.\s*\((\d+)\)', text))

    # Find defined figures (files in figures/ dir or **Figure N** captions)
    fig_dir = paper_dir / "figures"
    defined_figs = set()
    if fig_dir.exists():
        for fp in fig_dir.glob("fig*.*"):
            m = re.search(r'(\d+)', fp.stem)
            if m:
                defined_figs.add(m.group(1))
    # Also count "**Fig. N.**" or "**Figure N.**" caption patterns
    for m in re.finditer(r'\*\*(?:Fig(?:ure)?\.?\s*)(\d+)', text):
        defined_figs.add(m.group(1))

    # Find defined tables ("**Table N.**" captions in markdown)
    defined_tables = set()
    for m in re.finditer(r'\*\*Table\s+(\d+)\.?\*\*', text):
        defined_tables.add(m.group(1))

    # Find defined equations ($$...$$ blocks counted sequentially)
    display_eqs = re.findall(r'\$\$.*?\$\$', text, flags=re.DOTALL)
    defined_eqs = set(str(i) for i in range(1, len(display_eqs) + 1))

    orphan_figs = fig_refs - defined_figs
    orphan_tables = table_refs - defined_tables
    orphan_eqs = eq_refs - defined_eqs

    orphans = []
    if orphan_figs:
        orphans.append(f"Fig. {', '.join(sorted(orphan_figs))}")
    if orphan_tables:
        orphans.append(f"Table {', '.join(sorted(orphan_tables))}")
    if orphan_eqs:
        orphans.append(f"Eq. {', '.join(sorted(orphan_eqs))}")

    if orphans:
        checks.append({
            "check": "Cross-references resolved",
            "status": "FAIL",
            "detail": f"Orphan references (no definition found): {'; '.join(orphans)}",
        })
    else:
        n_refs = len(fig_refs) + len(table_refs) + len(eq_refs)
        checks.append({
            "check": "Cross-references resolved",
            "status": "PASS",
            "detail": f"All {n_refs} cross-references resolved",
        })

    # Citation key validation: ensure all \cite{key} keys exist in refs.bib
    cite_keys = set()
    for m in re.finditer(r'\\\\?cite\{([^}]+)\}', text):
        for key in m.group(1).split(','):
            cite_keys.add(key.strip())

    if cite_keys:
        refs_file = paper_dir / "refs.bib"
        bib_keys = set()
        if refs_file.exists():
            bib_text = refs_file.read_text(encoding="utf-8")
            bib_keys = set(re.findall(r'@\w+\{(\w+),', bib_text))

        missing_keys = cite_keys - bib_keys
        if missing_keys:
            checks.append({
                "check": "Citation keys resolved",
                "status": "FAIL",
                "detail": f"Keys not in refs.bib: {', '.join(sorted(missing_keys))}",
            })
        else:
            checks.append({
                "check": "Citation keys resolved",
                "status": "PASS",
                "detail": f"All {len(cite_keys)} citation keys found in refs.bib",
            })

    # Figure format and quality validation
    fig_checks = validate_figures(paper_dir, journal_profile)
    checks.extend(fig_checks)

    # Table format and quality validation
    tbl_checks = validate_tables(paper_dir, journal_profile)
    checks.extend(tbl_checks)

    return checks


def print_compliance(checks):
    """Pretty-print compliance check results."""
    print("=" * 50)
    print("JOURNAL COMPLIANCE CHECK")
    print("=" * 50)

    all_pass = True
    for check in checks:
        status = check["status"]
        icon = {"PASS": "[OK]", "FAIL": "[!!]", "WARN": "[??]"}[status]
        detail = f" — {check['detail']}" if "detail" in check else ""
        print(f"  {icon} {check['check']}{detail}")
        if status == "FAIL":
            all_pass = False

    print()
    verdict = "ALL CHECKS PASSED" if all_pass else "SOME CHECKS FAILED"
    print(f"  Verdict: {verdict}")
    print("=" * 50)


def main():
    parser = argparse.ArgumentParser(description="Paper Renderer")
    subparsers = parser.add_subparsers(dest="command")

    # Render command
    rend = subparsers.add_parser("render", help="Render manuscript to LaTeX")
    rend.add_argument("paper_dir", help="Paper directory")
    rend.add_argument("--journal", required=True, help="Journal profile name")
    rend.add_argument("--journals-dir", default="journals", help="Journals directory")

    # Check command
    chk = subparsers.add_parser("check", help="Check journal compliance")
    chk.add_argument("paper_dir", help="Paper directory")
    chk.add_argument("--journal", required=True, help="Journal profile name")
    chk.add_argument("--journals-dir", default="journals", help="Journals directory")

    args = parser.parse_args()

    if args.command == "render":
        profile = load_journal_profile(args.journal, args.journals_dir)
        tex_file = render_latex(args.paper_dir, profile)
        print(f"Rendered LaTeX: {tex_file}")

    elif args.command == "check":
        profile = load_journal_profile(args.journal, args.journals_dir)
        checks = check_compliance(args.paper_dir, profile)
        print_compliance(checks)

    else:
        parser.print_help()


if __name__ == "__main__":
    main()
