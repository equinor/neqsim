"""Validate a built Agent Plugins 1.0 marketplace against VS Code's acceptance rules.

Run from the marketplace root (or pass it as the first argument). Exits 1 when a
skill would be silently skipped (dir != name, non-kebab name), an agent lacks
name/description frontmatter, a plugin manifest is malformed, or the marketplace
points at a plugin that is not there.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import agent_frontmatter as af  # noqa: E402

KEBAB = re.compile(r"[a-z0-9]+(-[a-z0-9]+)*")
PLUGIN_NAME = re.compile(r"[a-z0-9.-]+")


def main(root: Path) -> int:
    problems = []
    marketplace = json.loads((root / "marketplace.json").read_text(encoding="utf-8"))
    for entry in marketplace["plugins"]:
        plugin_dir = root / entry["source"]
        manifest_path = plugin_dir / "plugin.json"
        if not manifest_path.exists():
            problems.append("{}: plugin.json missing".format(entry["source"]))
            continue
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if not str(manifest.get("$schema", "")).endswith("plugin.schema.json"):
            problems.append("{}: $schema is not the Agent Plugins schema".format(entry["name"]))
        if not PLUGIN_NAME.fullmatch(manifest.get("name", "")):
            problems.append("{}: plugin name not lowercase/digits/hyphen/period".format(entry["name"]))
        if manifest.get("version") != entry.get("version"):
            problems.append("{}: marketplace version {} != plugin.json {}".format(
                entry["name"], entry.get("version"), manifest.get("version")))
        for skill_dir in sorted((plugin_dir / "skills").iterdir()):
            fm = af.parse_frontmatter((skill_dir / "SKILL.md").read_text(encoding="utf-8"))
            if fm.get("name") != skill_dir.name:
                problems.append("{}: skill dir '{}' != name '{}'".format(
                    entry["name"], skill_dir.name, fm.get("name")))
            if not KEBAB.fullmatch(skill_dir.name):
                problems.append("{}: skill '{}' is not kebab-case".format(entry["name"], skill_dir.name))
        for agent in sorted((plugin_dir / "com.github.copilot" / "agents").glob("*.agent.md")):
            fm = af.parse_frontmatter(agent.read_text(encoding="utf-8"))
            if not fm.get("name") or not fm.get("description"):
                problems.append("{}: agent {} lacks name/description".format(entry["name"], agent.name))
        mcp_path = plugin_dir / "mcp.json"
        if mcp_path.exists():
            for srv_name, srv in json.loads(mcp_path.read_text(encoding="utf-8")).get(
                    "mcpServers", {}).items():
                cmd = srv.get("command", "")
                # Spec: one executable token, bare or ./-relative; placeholders not expanded here.
                if srv.get("type") == "stdio" and (" " in cmd or "${" in cmd
                                                    or ("/" in cmd and not cmd.startswith("./"))):
                    problems.append("{}: mcp server '{}' command must be one bare or ./-relative "
                                    "token, got '{}'".format(entry["name"], srv_name, cmd))
                for arg in srv.get("args", []):
                    if "${PLUGIN_ROOT}/" in arg:
                        rel = arg.split("${PLUGIN_ROOT}/", 1)[1]
                        if not (plugin_dir / rel).exists():
                            problems.append("{}: mcp server '{}' arg references missing file {}"
                                            .format(entry["name"], srv_name, rel))
    for p in problems:
        print("ERROR " + p)
    print("{}: {} plugin(s), {} problem(s)".format(root, len(marketplace["plugins"]), len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()))
