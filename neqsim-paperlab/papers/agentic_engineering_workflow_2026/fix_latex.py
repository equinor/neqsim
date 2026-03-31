"""Post-process the auto-generated paper.tex to fix common rendering issues."""
import re
from pathlib import Path

tex_file = Path(__file__).resolve().parent / "submission" / "paper.tex"
text = tex_file.read_text(encoding="utf-8")

# 1. Fix double-backslash cite commands: \\cite -> \cite, \\citep -> \citep
text = text.replace("\\\\cite{", "\\cite{")
text = text.replace("\\\\citep{", "\\citep{")

# 2. Remove HTML comments
text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)

# 3. Remove the duplicate title section (first \section that matches the title)
text = text.replace(
    "\\section{Agentic Engineering: A Multi-Agent LLM Framework for Solving "
    "Chemical Engineering Tasks with Open-Source Thermodynamic Software}\n",
    "",
)

# 4. Fix keyword section: remove --- and use \sep
text = re.sub(
    r"\\begin\{keyword\}\n(.*?)\n\n---\n\\end\{keyword\}",
    lambda m: "\\begin{keyword}\n"
    + m.group(1).strip().replace("; ", " \\sep ")
    + "\n\\end{keyword}",
    text,
    flags=re.DOTALL,
)

# 5. Remove manual section numbering (e.g., "1. Introduction" -> "Introduction")
text = re.sub(r"\\section\{(\d+\.)\s+", r"\\section{", text)
text = re.sub(r"\\subsection\{(\d+\.\d+)\s+", r"\\subsection{", text)

# 6. Merge consecutive enumerate environments into one
# Pattern: \end{enumerate}\n\n\begin{enumerate}
text = re.sub(
    r"\\end\{enumerate\}\s*\\begin\{enumerate\}",
    "",
    text,
)

# 7. Similarly merge consecutive itemize environments
text = re.sub(
    r"\\end\{itemize\}\s*\\begin\{itemize\}",
    "",
    text,
)

# 8. Update author information
text = text.replace(
    "\\author[inst1]{First Author\\corref{cor1}}",
    "\\author[inst1]{Even Solbraa\\corref{cor1}}",
)
text = text.replace("\\ead{email@institution.no}", "\\ead{esolbraa@equinor.com}")
text = text.replace(
    "\\affiliation[inst1]{organization={Department, Institution},\n"
    "                     city={City},\n"
    "                     country={Country}}",
    "\\affiliation[inst1]{organization={Equinor ASA and Norwegian University of Science and Technology (NTNU)},\n"
    "                     city={Trondheim},\n"
    "                     country={Norway}}",
)

# 9. Fix em-dash: — -> ---
text = text.replace("—", "---")

# 10. Fix special characters in text
text = text.replace("ä", r"\"{a}")
text = text.replace("ö", r"\"{o}")
text = text.replace("ü", r"\"{u}")
text = text.replace("é", r"\'{e}")
text = text.replace("ø", r"{\o}")

# 11. Clean up excessive blank lines
text = re.sub(r"\n{4,}", "\n\n\n", text)

tex_file.write_text(text, encoding="utf-8")
print(f"Post-processed: {tex_file}")
print(f"Size: {len(text):,} chars")

# Quick verification
cite_count = len(re.findall(r"\\cite[p]?\{", text))
section_count = len(re.findall(r"\\section\{", text))
print(f"\\cite/\\citep commands: {cite_count}")
print(f"\\section commands: {section_count}")
