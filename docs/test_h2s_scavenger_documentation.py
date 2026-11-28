"""Contracts for the source-verified H2S scavenger guide."""

import json
import re
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "docs" / "process" / "H2S_scavenger_guide.md"
SOURCE = (
    ROOT
    / "src"
    / "main"
    / "java"
    / "neqsim"
    / "process"
    / "equipment"
    / "absorber"
    / "H2SScavenger.java"
)
JAVA_TEST = (
    ROOT
    / "src"
    / "test"
    / "java"
    / "neqsim"
    / "documentation"
    / "H2SScavengerDocumentationTest.java"
)
MARKER = "<!-- h2s-scavenger-example: triazine-quick-start -->"
FRONT_MATTER = re.compile(r"\A---\n(?P<fields>.*?)\n---\n", re.DOTALL)
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
JSON_KEYS = {
    "name",
    "scavengerType",
    "injectionRate",
    "injectionRateUnit",
    "concentration",
    "inletH2Sppm",
    "outletH2Sppm",
    "removalEfficiencyPercent",
    "h2sRemovedKgPerHr",
    "scavengerConsumptionKgPerHr",
    "scavengerExcessPercent",
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


def test_metadata_compact_math_and_links() -> None:
    text = _guide()
    front_matter = FRONT_MATTER.match(text)
    assert front_matter is not None, "missing YAML front matter"
    fields = front_matter.group("fields")
    for key in ("title", "description", "keywords"):
        assert re.search(rf"^{key}:\s*.+$", fields, re.MULTILINE), key

    body = FRONT_MATTER.sub("", text, count=1)
    visible = _visible_text(body)
    assert re.search(r"^#\s+", visible, re.MULTILINE) is None
    assert re.search(r"(?m)^\s*\$\$\s*$", text) is None, (
        "display math delimiters must stay on the expression line"
    )

    for target in MARKDOWN_LINK.findall(visible):
        candidates = _target_candidates(target)
        if candidates:
            assert any(candidate.is_file() for candidate in candidates), (
                f"unresolved relative target {target}"
            )


def test_published_correlation_matches_source() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    guide = _guide()

    source_contract = (
        "double excessRatio = activeScavengerRate / stoichRequirement;",
        "excessCorrection = 1.0 - Math.exp(-k * (excessRatio - 0.5));",
        "excessCorrection = excessRatio * 0.9;",
        "double contactCorrection = 1.0 - Math.exp(-contactTime / 15.0);",
        "double mixCorrection = 0.5 + 0.5 * mixingEfficiency;",
        "if (tempC < 10)",
        "else if (tempC > 80)",
        "Math.max(0.0, Math.min(0.999, efficiency))",
    )
    for statement in source_contract:
        assert statement in source, statement

    guide_contract = (
        r"D=\frac{\dot m_s}{S\,\dot m_{H_2S}}",
        r"0.9D,&D<1",
        r"1-\exp[-k(D-0.5)],&D\geq1",
        r"f_{contact}=1-\exp(-t/15\,\mathrm{s})",
        r"f_{mix}=0.5+0.5\,\epsilon_{mix}",
        r"0.7+0.03T_C,&T_C<10",
        r"1-0.005(T_C-80),&T_C>80",
        r"\operatorname{clamp}",
    )
    for statement in guide_contract:
        assert statement in guide, statement

    for stale in ("f_{excess}", "(t/30)^{0.3}", "Optimum at 40°C"):
        assert stale not in guide, stale


def test_api_signature_and_json_schema_match_source() -> None:
    source = SOURCE.read_text(encoding="utf-8")
    guide = _guide()

    assert "public double getActualScavengerConsumption(String unit)" in source
    assert "`getActualScavengerConsumption(unit)`" in guide
    assert "`getActualScavengerConsumption()`" not in guide

    response = re.search(r"```json\n(.*?)\n```", guide, re.DOTALL)
    assert response is not None, "missing JSON response example"
    payload = json.loads(response.group(1))
    assert set(payload) == JSON_KEYS
    for key in JSON_KEYS:
        assert re.search(rf"public (?:String|double) {re.escape(key)};", source), key

    for stale in ("equipmentName", "injectionRate_l_hr", "h2sRemoved_kg_hr"):
        assert stale not in guide, stale


def test_literal_example_has_one_owner_and_one_marker() -> None:
    guide = _guide()
    java_test = JAVA_TEST.read_text(encoding="utf-8")

    assert guide.count(MARKER) == 1
    assert guide.count(
        "[`H2SScavengerDocumentationTest`]"
        "(../../src/test/java/neqsim/documentation/"
        "H2SScavengerDocumentationTest.java)"
    ) == 1
    assert guide.count("./mvnw -Dtest=H2SScavengerDocumentationTest test") == 1
    assert "private static final String MARKER =" in java_test
    assert "H2SScavengerQuickStart.java" in java_test
    assert re.search(
        r'"-source",\s*"8",\s*"-target",\s*"8"', java_test
    )
    assert "loader.setDefaultAssertionStatus(true);" in java_test
