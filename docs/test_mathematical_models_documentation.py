"""Contracts for the source-verified mathematical-models guide."""

import re
import unittest
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "thermo" / "mathematical_models.md"
MIXING_RULE_ENUM = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "thermo"
    / "mixingrule"
    / "EosMixingRuleType.java"
)
JAVA_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "documentation"
    / "MathematicalModelsDocumentationTest.java"
)
MARKER = "<!-- mathematical-models-example: cubic-eos-selection -->"
FRONT_MATTER = re.compile(r"\A---\n(?P<fields>.*?)\n---\n", re.DOTALL)
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
EXPECTED_RULES = {
    "NO": 1,
    "CLASSIC": 2,
    "CLASSIC_HV": 3,
    "HV": 4,
    "WS": 5,
    "CPA_MIX": 7,
    "CLASSIC_T_CPA": 9,
    "CLASSIC_TX_CPA": 10,
    "SOREIDE_WHITSON": 11,
}


def _guide() -> str:
    return GUIDE.read_text(encoding="utf-8")


def _visible_text(text: str) -> str:
    """Return prose outside fenced code blocks."""
    visible: list[str] = []
    in_fence = False
    for line in text.splitlines():
        if re.match(r"^\x60{3,}", line):
            in_fence = not in_fence
            continue
        if not in_fence:
            visible.append(line)
    assert not in_fence, "unclosed fenced code block"
    return "\n".join(visible)


def _target_candidates(target: str) -> tuple[Path, ...]:
    parsed = urlsplit(target.strip().strip("<>"))
    if parsed.scheme or parsed.netloc or target.startswith("#"):
        return ()
    relative = unquote(parsed.path)
    if not relative or relative.startswith("/"):
        return ()

    destination = (GUIDE.parent / relative).resolve()
    candidates = [destination]
    if not destination.suffix:
        candidates.extend(
            (
                destination.with_suffix(".md"),
                destination / "index.md",
                destination / "README.md",
            )
        )
    return tuple(candidates)


def test_metadata_math_and_links() -> None:
    text = _guide()
    front_matter = FRONT_MATTER.match(text)
    assert front_matter is not None, "missing YAML front matter"
    fields = front_matter.group("fields")
    for key in ("title", "description", "keywords"):
        assert re.search(rf"^{key}:\s*.+$", fields, re.MULTILINE), key

    body = FRONT_MATTER.sub("", text, count=1)
    visible = _visible_text(body)
    assert re.search(r"^#\s+", visible, re.MULTILINE) is None
    assert "$$\n" not in text, "display math must stay compact"

    for target in MARKDOWN_LINK.findall(visible):
        candidates = _target_candidates(target)
        if candidates:
            assert any(candidate.is_file() for candidate in candidates), (
                f"unresolved relative target {target}"
            )


def test_mixing_rule_guidance_matches_source_enum() -> None:
    enum_source = MIXING_RULE_ENUM.read_text(encoding="utf-8")
    guide = _guide()

    for name, value in EXPECTED_RULES.items():
        assert re.search(rf"\b{name}\s*\(\s*{value}\s*\)", enum_source), (
            f"EosMixingRuleType.{name} no longer maps to {value}"
        )
        assert f"| `{name}` | {value} |" in guide

    assert "fluid.setMixingRule(EosMixingRuleType.CLASSIC);" in guide
    assert "Legacy value 2 is **not** Huron-Vidal" in guide
    assert "value 4 selects `HV`, not Wong-Sandler" in guide

    stale_claims = (
        "2 = Huron",
        "use 1 for classical",
        "setMixingRule(4); // enables Wong",
    )
    for claim in stale_claims:
        assert claim not in guide, claim


def test_literal_example_has_one_owner_and_one_marker() -> None:
    guide = _guide()
    java_test = JAVA_TEST.read_text(encoding="utf-8")

    assert guide.count(MARKER) == 1
    assert guide.count(
        "[`MathematicalModelsDocumentationTest`]"
        "(../../src/test/java/neqsim/documentation/"
        "MathematicalModelsDocumentationTest.java)"
    ) == 1
    assert guide.count(
        "./mvnw -Dtest=MathematicalModelsDocumentationTest test"
    ) == 1
    assert "private static final String MARKER = " in java_test
    assert "MathematicalModelsQuickStart.java" in java_test
    assert re.search(
        r'"-source",\s*"8",\s*"-target",\s*"8"', java_test
    )
    assert "loader.setDefaultAssertionStatus(true);" in java_test


def load_tests(
    loader: unittest.TestLoader,
    tests: unittest.TestSuite,
    pattern: str | None,
) -> unittest.TestSuite:
    """Expose module-level contracts to the repository's unittest gate."""
    del loader, tests, pattern
    return unittest.TestSuite(
        unittest.FunctionTestCase(contract)
        for contract in (
            test_metadata_math_and_links,
            test_mixing_rule_guidance_matches_source_enum,
            test_literal_example_has_one_owner_and_one_marker,
        )
    )
