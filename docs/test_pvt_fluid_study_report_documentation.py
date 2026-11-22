"""Execute the PVT report guide's scaffold and parse every documented CLI option."""
import re
import shlex
import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parent.parent
DEVTOOLS = REPO / "devtools"
PAGE = REPO / "docs/pvtsimulation/pvt_fluid_study_report.md"
sys.path.insert(0, str(DEVTOOLS))

FENCE = re.compile(
    r"(?:<!--\s*(?P<marker>[^>]*?)\s*-->\s*\n)?"
    r"```(?P<language>\w*)\n(?P<code>.*?)```", re.DOTALL
)


def command_blocks():
    """Return marked guide commands without requiring a JVM at collection time."""
    return list(FENCE.finditer(PAGE.read_text(encoding="utf-8")))


def command_arguments(code):
    """Extract the actual pvt-study argument vectors from a command block."""
    for line in code.splitlines():
        argv = shlex.split(line)
        if argv:
            assert argv[:2] == ["neqsim", "pvt-study"]
            yield argv[2:]


def test_guide_frontmatter_and_checked_blocks():
    text = PAGE.read_text(encoding="utf-8")
    assert text.startswith("---\ntitle: ")
    assert "\ndescription: " in text.split("---", 2)[1]
    assert not re.search(r"^# ", text.split("---", 2)[2], re.MULTILINE)
    assert "\\(" not in text and "\\[" not in text
    blocks = command_blocks()
    assert blocks
    for block in blocks:
        if block["language"] in ("powershell", "python", "java", "yaml", "json"):
            assert (block["marker"] or "").strip() in ("run", "parse")


def test_every_documented_option_uses_the_actual_parser():
    import neqsim_cli
    import pvt_study

    assert neqsim_cli.COMMANDS["pvt-study"]["module"] == "pvt_study"
    parser = pvt_study.build_parser()
    for block in command_blocks():
        if block["language"] == "powershell":
            commands = list(command_arguments(block["code"]))
            assert commands
            for argv in commands:
                parser.parse_args(argv)


def test_scaffold_command_executes_without_fabricated_results(tmp_path, monkeypatch):
    import pvt_study

    monkeypatch.chdir(tmp_path)
    ran = False
    for block in command_blocks():
        if (block["marker"] or "").strip() == "run":
            for argv in command_arguments(block["code"]):
                assert pvt_study.main(argv) == 0
                ran = True
    assert ran
    inputs = tmp_path / "study_inputs"
    assert inputs.is_dir()
    assert list(inputs.glob("*.yaml"))
    assert not list(inputs.glob("**/*.e300"))
    assert not list(inputs.glob("**/*.ser"))
    assert not list(inputs.glob("**/pvt_study.json"))


def test_relative_links_and_index_entries_resolve():
    text = PAGE.read_text(encoding="utf-8")
    for target in re.findall(r"\]\(([^):#]+\.md)(?:#[^)]*)?\)", text):
        assert (PAGE.parent / target).resolve().is_file(), target
    for index in (REPO / "docs/REFERENCE_MANUAL_INDEX.md", PAGE.parent / "index.md"):
        assert "pvt_fluid_study_report.md" in index.read_text(encoding="utf-8")


@pytest.mark.parametrize("name", ["oil", "condensate"])
def test_documented_examples_are_real_validated_inputs(name):
    import pvt_study

    path = DEVTOOLS / "pvt_examples" / (name + ".yaml")
    assert path.is_file()
    study = pvt_study.load_study(path)
    pvt_study.validate_study(study)
