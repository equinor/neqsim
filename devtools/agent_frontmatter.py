#!/usr/bin/env python
"""Single source of truth for reading agent markdown metadata.

Every NeqSim agent definition (``.github/agents/*.agent.md`` in the core repo,
``agents/<id>/AGENT.md`` in the community and enterprise repos, and the
``*.agent.md`` hooks exported to VS Code) is a markdown file with a YAML
frontmatter block. The skills an agent needs are declared in the frontmatter
key ``required_skills`` (canonical), with two legacy prose forms still
accepted so older files keep working:

* an inline body line ``Loaded skills: a, b, c``;
* a ``## Loaded skills`` / ``## Skills to Load`` heading followed by bullets.

The build script for agent plugins, the agent search index, the agent
installer, the PaperLab exporter and the AGENT_SKILL_MAP generator all read
through this module so a future change to the declaration format is made in
exactly one place.

No PyYAML dependency: the frontmatter dialect used across the repos is flat
scalars plus simple lists, which is parsed here directly.
"""
from __future__ import annotations

import re
from pathlib import Path
from typing import Dict, List, Optional, Union

FRONT_MATTER_RE = re.compile(r"^---\s*\r?\n(.*?)\r?\n---\s*(?:\r?\n|$)", re.DOTALL)
SKILL_LIST_KEYS = ("required_skills", "loaded_skills", "skills")
INLINE_LOADED_RE = re.compile(
    r"^\s*(?:[-*]\s*)?(?:\*\*)?loaded skills(?:\*\*)?\s*[:\-]\s*(.+?)$",
    re.IGNORECASE | re.MULTILINE,
)
BLOCK_HEADER_RE = re.compile(
    r"^\s{0,3}#{1,6}\s+(?:\*\*)?(?:loaded skills|skills to load)(?:\*\*)?\s*$",
    re.IGNORECASE,
)
MARKDOWN_HEADING_RE = re.compile(r"^\s{0,3}#{1,6}\s+\S")
SKILL_TOKEN_RE = re.compile(r"`?@?([a-z0-9][a-z0-9_.-]*[a-z0-9])`?", re.IGNORECASE)
# Agent Skills / Agent Plugins id rule shared by skills and agents.
KEBAB_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


def strip_yaml_scalar(value: str) -> str:
    """Return a YAML scalar without surrounding quotes or whitespace."""
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def split_frontmatter(text: str):
    """Return ``(frontmatter_text, body_text)``; frontmatter is ``None`` if absent."""
    match = FRONT_MATTER_RE.match(text)
    if not match:
        return None, text
    return match.group(1), text[match.end():]


def parse_frontmatter(text: str) -> Dict[str, Union[str, List[str]]]:
    """Parse flat scalars and simple lists from a frontmatter block.

    Scalars become strings; ``- item`` blocks and ``[a, b]`` inline lists become
    lists of strings. Nested mappings are not supported and are skipped.
    """
    front, _ = split_frontmatter(text)
    if front is None:
        return {}
    out: Dict[str, Union[str, List[str]]] = {}
    lines = front.splitlines()
    index = 0
    while index < len(lines):
        raw = lines[index]
        stripped = raw.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            index += 1
            continue
        key, _, value = stripped.partition(":")
        key = key.strip()
        value = value.strip()
        if value in ("|", ">"):
            block: List[str] = []
            index += 1
            while index < len(lines) and (not lines[index].strip()
                                          or lines[index].startswith((" ", "\t"))):
                block.append(lines[index].strip())
                index += 1
            out[key] = " ".join(part for part in block if part)
            continue
        if value == "" or value == "[]":
            items: List[str] = []
            index += 1
            while index < len(lines):
                candidate = lines[index].strip()
                if candidate.startswith("- "):
                    items.append(strip_yaml_scalar(candidate[2:]))
                    index += 1
                    continue
                if not candidate:
                    index += 1
                    continue
                break
            out[key] = items
            continue
        if value.startswith("[") and value.endswith("]"):
            out[key] = [strip_yaml_scalar(v) for v in value[1:-1].split(",") if v.strip()]
        else:
            out[key] = strip_yaml_scalar(value)
        index += 1
    return out


def _clean_skill(token: str) -> str:
    cleaned = token.strip().strip("`").lstrip("@").rstrip(".,;")
    if not cleaned:
        return ""
    return re.split(r"\s+", cleaned, maxsplit=1)[0].strip("`").rstrip(".,;")


def _dedupe(items: List[str]) -> List[str]:
    seen = set()
    out: List[str] = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            out.append(item)
    return out


def _tokens(text: str) -> List[str]:
    """Return one skill per comma-separated item (first token; trailing prose dropped)."""
    out: List[str] = []
    for item in text.split(","):
        match = SKILL_TOKEN_RE.search(item)
        if match:
            out.append(_clean_skill(match.group(1)))
    return out


def _first_token(text: str) -> List[str]:
    """Return the first skill token of a bullet item, ignoring any annotation."""
    match = SKILL_TOKEN_RE.search(text)
    return [_clean_skill(match.group(1))] if match else []


def extract_body_skills(body: str) -> List[str]:
    """Return skills declared in legacy prose forms in the markdown body."""
    skills: List[str] = []
    for match in INLINE_LOADED_RE.finditer(body):
        skills.extend(_tokens(match.group(1)))
    lines = body.splitlines()
    for line_number, line in enumerate(lines):
        if not BLOCK_HEADER_RE.match(line):
            continue
        for next_line in lines[line_number + 1:]:
            if MARKDOWN_HEADING_RE.match(next_line):
                break
            stripped = next_line.strip()
            if not stripped and skills:
                break
            if stripped.startswith(("-", "*")):
                skills.extend(_first_token(stripped[1:]))
    return _dedupe(skills)


def extract_required_skills(text: str) -> List[str]:
    """Return the ordered, de-duplicated skills an agent declares.

    Frontmatter ``required_skills`` (or ``loaded_skills`` / ``skills``) wins;
    legacy prose declarations in the body are appended so a partially migrated
    file still resolves.
    """
    fm = parse_frontmatter(text)
    skills: List[str] = []
    for key in SKILL_LIST_KEYS:
        value = fm.get(key)
        if isinstance(value, list):
            skills.extend(_clean_skill(v) for v in value)
        elif isinstance(value, str) and value:
            skills.extend(_tokens(value))
    _, body = split_frontmatter(text)
    skills.extend(extract_body_skills(body))
    return _dedupe(skills)


def agent_id_for_path(path: Union[str, Path]) -> str:
    """Return the kebab-case id used to install or invoke an agent.

    Core agents are flat ``<id>.agent.md`` files; community and enterprise
    agents live in ``agents/<id>/AGENT.md``. Dots are normalised to hyphens
    because agent-plugin marketplaces reject them.
    """
    path_obj = Path(path)
    name = path_obj.name
    if name.lower() == "agent.md":
        raw = path_obj.parent.name
    elif name.lower().endswith(".agent.md"):
        raw = name[: -len(".agent.md")]
    else:
        raw = path_obj.stem
    return raw.replace(".", "-")


def set_frontmatter_list(text: str, key: str, values: List[str]) -> str:
    """Return ``text`` with frontmatter ``key`` set to a YAML block list.

    Replaces an existing scalar/list entry for ``key`` or inserts one after
    ``description`` (else at the end of the block). Creates a frontmatter block
    if the file has none.
    """
    rendered = [key + ": []"] if not values else [key + ":"] + ["- " + v for v in values]
    front, body = split_frontmatter(text)
    if front is None:
        return "---\n" + "\n".join(rendered) + "\n---\n" + text
    lines = front.splitlines()
    out: List[str] = []
    replaced = False
    index = 0
    while index < len(lines):
        line = lines[index]
        if re.match(r"^" + re.escape(key) + r"\s*:", line):
            index += 1
            while index < len(lines) and (lines[index].strip().startswith("- ")
                                          or not lines[index].strip()):
                index += 1
            out.extend(rendered)
            replaced = True
            continue
        out.append(line)
        index += 1
    if not replaced:
        insert_at = len(out)
        for i, line in enumerate(out):
            if line.startswith("description:"):
                insert_at = i + 1
                # skip a folded/literal description continuation
                while insert_at < len(out) and out[insert_at].startswith((" ", "\t")):
                    insert_at += 1
                break
        out[insert_at:insert_at] = rendered
    newline = "\r\n" if "\r\n" in text[: len(text) - len(body)] else "\n"
    return "---" + newline + newline.join(out) + newline + "---" + newline + body


def read_agent(path: Union[str, Path]) -> Dict[str, object]:
    """Return ``{'id', 'name', 'description', 'required_skills', 'path'}`` for an agent file."""
    path_obj = Path(path)
    text = path_obj.read_text(encoding="utf-8")
    fm = parse_frontmatter(text)
    agent_id = agent_id_for_path(path_obj)
    name = fm.get("name")
    return {
        "id": agent_id,
        "name": name if isinstance(name, str) and name else agent_id,
        "description": fm.get("description") if isinstance(fm.get("description"), str) else "",
        "required_skills": extract_required_skills(text),
        "path": str(path_obj),
    }


def iter_agent_files(agents_dir: Union[str, Path]):
    """Yield agent definition files under ``agents_dir`` in either layout."""
    root = Path(agents_dir)
    if not root.is_dir():
        return
    for md in sorted(root.glob("*.agent.md")):
        yield md
    for md in sorted(root.glob("*/AGENT.md")):
        yield md
