#!/usr/bin/env python3
"""Build a relocatable Copilot plugin from NeqSim's canonical agents and skills.

Uses only the Python standard library. Generated files belong under target/;
the repository definitions remain the single source of truth.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import quote
import zipfile

try:
    from devtools.verify_skills_agents import FRONT_MATTER_RE, parse_front_matter
except ModuleNotFoundError:
    from verify_skills_agents import FRONT_MATTER_RE, parse_front_matter


ROOT = Path(__file__).resolve().parent.parent
SCHEMA = "https://agent-plugins.org/schemas/1.0.0/"
DEFAULT_IMAGE = "ghcr.io/equinor/neqsim-mcp-server:latest"


def identifier(value):
    """Normalize an identifier, rejecting empty or overlong names."""
    name = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    if not name or len(name) > 64:
        raise ValueError("Invalid plugin component name: " + value)
    return name


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def source_revision(root):
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, check=True
    )
    revision = result.stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{40,64}", revision):
        raise ValueError("Cannot determine source revision")
    return revision


def discover(root):
    """Map every canonical component and supporting skill file into the bundle."""
    mapping = {}
    names = {}
    for source in sorted((root / ".github/skills").glob("*/SKILL.md")):
        name = identifier(source.parent.name)
        if name in names.values():
            raise ValueError("Skill names collide after normalization: " + name)
        names[source.parent.name] = name
        for resource in sorted(source.parent.rglob("*")):
            if resource.is_symlink():
                raise ValueError("Skill resources must not be symlinks: " + str(resource))
            if resource.is_file():
                mapping[resource.relative_to(root)] = (
                    Path("skills") / name / resource.relative_to(source.parent)
                )
    for source in sorted((root / ".github/agents").glob("*.agent.md")):
        name = "neqsim-" + identifier(source.name.removesuffix(".agent.md"))
        mapping[source.relative_to(root)] = Path("com.github.copilot/agents") / (name + ".agent.md")
    if not names or not any(path.suffix == ".md" and "agents" in path.parts for path in mapping):
        raise ValueError("The source checkout must contain both agents and skills")
    destinations = list(mapping.values())
    if len(destinations) != len(set(destinations)):
        raise ValueError("Plugin component destination collision")
    return mapping, names


def rewrite_markdown(text, source, destination, mapping, names, root, revision):
    """Relocate real Markdown links and recognizable component references."""
    def link(match):
        target = match.group(2)
        if re.match(r"[a-zA-Z][a-zA-Z0-9+.-]*:", target) or target.startswith("#"):
            return match.group(0)
        path, separator, anchor = target.partition("#")
        candidate = (root / source.parent / path).resolve()
        if not candidate.is_file():
            candidate = (root / path).resolve()
        if not candidate.is_file() or not candidate.is_relative_to(root):
            return match.group(0)
        relative = candidate.relative_to(root)
        if relative in mapping:
            relocated = os.path.relpath(mapping[relative], destination.parent).replace(os.sep, "/")
        else:
            relocated = "https://github.com/equinor/neqsim/blob/" + revision + "/" + quote(relative.as_posix())
        return match.group(1) + relocated + (separator + anchor if separator else "") + ")"

    text = re.sub(r"(\[[^\]]*\]\()([^\s)]+)\)", link, text)
    for old, new in sorted(names.items(), key=lambda item: -len(item[0])):
        text = text.replace(".github/skills/" + old + "/", "skills/" + new + "/")
        text = text.replace("`" + old + "`", "`" + new + "`")
    for old, new in mapping.items():
        if old.name.endswith(".agent.md"):
            old_id = old.name.removesuffix(".agent.md")
            new_id = new.name.removesuffix(".agent.md")
            text = re.sub(r"@" + re.escape(old_id) + r"(?![\w.-])", "@" + new_id, text)
            text = text.replace(old.as_posix(), new.as_posix())
    def loaded(match):
        line = match.group(0)
        for old, new in names.items():
            line = re.sub(r"(?<![\w-])" + re.escape(old) + r"(?![\w-])", new, line)
        return line
    return re.sub(r"^Loaded skills:.*$", loaded, text, flags=re.MULTILINE)


def component_text(text, source, destination, mapping, names, root, revision):
    match = FRONT_MATTER_RE.match(text)
    fields = parse_front_matter(text)
    if not match or not fields.get("name") or not fields.get("description"):
        raise ValueError("Missing component name/description: " + str(source))
    description = fields["description"].replace("->", "→").replace("<", "less than ").replace(">", "greater than ")
    short = description if len(description) <= 1024 else description[:1020].rsplit(" ", 1)[0] + "…"
    is_skill = source.name == "SKILL.md"
    name = destination.parent.name if is_skill else destination.name.removesuffix(".agent.md")
    front = "---\nname: " + json.dumps(name) + "\ndescription: " + json.dumps(short, ensure_ascii=False) + "\n"
    if is_skill:
        # Preserve repository-only metadata without introducing non-standard skill fields.
        front += "metadata:\n  source: " + json.dumps(source.as_posix()) + "\n"
        front += "  original-frontmatter: " + json.dumps(match.group(1), ensure_ascii=False) + "\n"
    elif fields.get("argument-hint"):
        front += "argument-hint: " + json.dumps(fields["argument-hint"], ensure_ascii=False) + "\n"
    front += "---\n\n"
    readme = os.path.relpath("README.md", destination.parent).replace(os.sep, "/")
    note = (
        "Plugin context: read [bundle setup](" + readme + ") for runtime and workspace requirements. "
        "Paths starting with `skills/` or `com.github.copilot/` refer to this plugin's root. "
        "Source paths such as `devtools/`, `src/`, `task_solve/`, and `neqsim-paperlab/` "
        "refer to the user's NeqSim source checkout, not the plugin installation.\n\n"
    )
    if short != description:
        note += "Full skill description: " + description + "\n\n"
    body = rewrite_markdown(text[match.end():], source, destination, mapping, names, root, revision)
    return front + note + body


def mcp_config(jar, image):
    if jar:
        server = {
            "type": "stdio", "command": "java",
            "args": ["-Dquarkus.profile=stdio", "-jar", "${PLUGIN_ROOT}/server/neqsim-mcp-server.jar"],
        }
    else:
        if not image or image.startswith("-") or any(char.isspace() for char in image):
            raise ValueError("A single Docker image reference is required")
        server = {
            "type": "stdio", "command": "docker",
            "args": ["run", "--rm", "-i", "-e", "QUARKUS_PROFILE=stdio", image],
        }
    return {"$schema": SCHEMA + "mcp.schema.json", "mcpServers": {"neqsim": server}}


def build(root, output, jar=None, image=DEFAULT_IMAGE):
    root, output = root.resolve(), output.resolve()
    # Never replace existing files or write generated artifacts into source directories.
    if output.exists():
        raise ValueError("Output already exists; choose a new directory: " + str(output))
    if output == root or root.is_relative_to(output):
        raise ValueError("Output must not contain the source checkout")
    if output.is_relative_to(root) and not output.is_relative_to(root / "target"):
        raise ValueError("Inside the checkout, use an output directory under target/")
    mapping, names = discover(root)
    revision = source_revision(root)
    config = mcp_config(jar, image)
    if jar:
        jar = jar.resolve()
        if not zipfile.is_zipfile(jar):
            raise ValueError("MCP server jar is missing or is not a jar archive")
        with zipfile.ZipFile(jar) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
            if "Main-Class:" not in manifest or "neqsim/mcp/server/NeqSimTools.class" not in archive.namelist():
                raise ValueError("Expected the packaged NeqSim MCP server uber-jar")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="neqsim-plugin-", dir=output.parent) as temporary:
        staging = Path(temporary) / "neqsim"
        staging.mkdir()
        manifest = {
            "$schema": SCHEMA + "plugin.schema.json", "name": "neqsim",
            "version": "1.0.0", "description": "NeqSim engineering agents, skills, and MCP calculation tools.",
            "license": "Apache-2.0", "repository": "https://github.com/equinor/neqsim",
            "homepage": "https://equinor.github.io/neqsim/integration/copilot_plugin.html",
        }
        write_json(staging / "plugin.json", manifest)
        write_json(staging / "mcp.json", config)
        records = []
        for source, destination in mapping.items():
            raw = (root / source).read_bytes()
            target = staging / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.name == "SKILL.md" or source.name.endswith(".agent.md"):
                target.write_text(component_text(raw.decode("utf-8"), source, destination,
                                                mapping, names, root, revision), encoding="utf-8")
            elif source.suffix == ".md":
                target.write_text(rewrite_markdown(raw.decode("utf-8"), source, destination,
                                                  mapping, names, root, revision), encoding="utf-8")
            else:
                target.write_bytes(raw)
            records.append({"source": source.as_posix(), "path": destination.as_posix(),
                            "sourceSha256": hashlib.sha256(raw).hexdigest(),
                            "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
        shutil.copyfile(root / "LICENSE", staging / "LICENSE")
        shutil.copyfile(root / "docs/integration/copilot_plugin.md", staging / "README.md")
        if jar:
            (staging / "server").mkdir()
            shutil.copyfile(jar, staging / "server/neqsim-mcp-server.jar")
        write_json(staging / "bundle-inventory.json", {
            "sourceRevision": revision, "skillCount": len(names),
            "agentCount": sum(path.name.endswith(".agent.md") for path in mapping),
            "skillNames": names, "files": records,
            "mcp": {"transport": "stdio", "image": None if jar else image,
                    "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest() if jar else None},
        })
        staging.rename(output)
    return output


def archive_bundle(output, archive):
    """Write a deterministic archive with a single neqsim/ top-level folder."""
    if archive.exists():
        raise ValueError("Archive already exists: " + str(archive))
    if archive.resolve().is_relative_to(output.resolve()):
        raise ValueError("Archive must be outside the plugin directory")
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(output.rglob("*")):
            if path.is_file():
                info = zipfile.ZipInfo("neqsim/" + path.relative_to(output).as_posix(), (2020, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                bundle.writestr(info, path.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "target/copilot-plugin/neqsim")
    transport = parser.add_mutually_exclusive_group()
    transport.add_argument("--mcp-jar", type=Path, help="Bundle an existing MCP uber-jar; requires Java 21+ at use time")
    transport.add_argument("--image", default=DEFAULT_IMAGE, help="Docker image tag or immutable digest")
    parser.add_argument("--archive", type=Path, help="Also create a ZIP for distribution")
    args = parser.parse_args()
    try:
        output = build(ROOT, args.output, args.mcp_jar, args.image)
        if args.archive:
            archive_bundle(output, args.archive)
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, str(error) + "\n")
    inventory = json.loads((output / "bundle-inventory.json").read_text(encoding="utf-8"))
    print(f"Built {inventory['agentCount']} agents and {inventory['skillCount']} skills at {output}")
    print(json.dumps({"chat.plugins.enabled": True, "chat.pluginLocations": {str(output): True}}, indent=2))


if __name__ == "__main__":
    main()
