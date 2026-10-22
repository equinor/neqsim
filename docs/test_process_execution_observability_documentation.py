"""Source-anchored regression contracts for process execution observability guides."""

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUN_STATUS_GUIDE = ROOT / "docs" / "process" / "processmodel" / "run_status.md"
HOOKS_GUIDE = ROOT / "docs" / "process" / "simulation-hooks-and-events.md"
PROCESS_SYSTEM = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "processmodel"
    / "ProcessSystem.java"
)
PROCESS_MODEL = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "processmodel"
    / "ProcessModel.java"
)
RUN_STATUS = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "processmodel"
    / "RunStatus.java"
)
UNIT_RUN_STATUS = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "processmodel"
    / "UnitRunStatus.java"
)
EVENT_BUS = (
    ROOT / "src" / "main" / "java" / "neqsim" / "process" / "util" / "event"
    / "ProcessEventBus.java"
)
RUN_STATUS_TEST = (
    ROOT / "src" / "test" / "java" / "neqsim" / "process" / "processmodel"
    / "RunStatusTest.java"
)
HOOKS_TEST = (
    ROOT / "src" / "test" / "java" / "neqsim" / "process" / "processmodel"
    / "SimulationHooksTest.java"
)
MODEL_HOOKS_TEST = (
    ROOT / "src" / "test" / "java" / "neqsim" / "process" / "processmodel"
    / "ProcessModelHooksTest.java"
)


def read(path: Path) -> str:
    """Read one repository text file."""

    return path.read_text(encoding="utf-8")


def java_blocks(markdown: str) -> list[str]:
    """Return Java fenced-code bodies."""

    fence = chr(96) * 3
    return re.findall(rf"{fence}java\n(.*?)\n{fence}", markdown, flags=re.DOTALL)


class ProcessExecutionObservabilityDocumentationContractTest(unittest.TestCase):
    def test_guides_use_front_matter_titles_and_parameterized_logging(self) -> None:
        """Keep Java reporting examples on the repository logging contract."""

        run_status = read(RUN_STATUS_GUIDE)
        hooks = read(HOOKS_GUIDE)
        combined = run_status + hooks
        fence = chr(96) * 3
        prose = re.sub(rf"{fence}.*?{fence}", "", combined, flags=re.DOTALL)

        assert run_status.startswith("---\n")
        assert hooks.startswith("---\n")
        assert not re.search(r"^# ", prose, flags=re.MULTILINE)
        assert "System.out" not in combined
        assert "System.err" not in combined
        assert combined.count("logger.info(") == 12
        assert 'logger.error("Simulation error: {}", event.getDescription());' in hooks
        assert "LogManager.getLogger(YourClass.class)" in run_status
        assert "LogManager.getLogger(YourClass.class)" in hooks

    def test_run_status_examples_are_self_contained_and_links_resolve(self) -> None:
        """Keep imports complete and internal destinations explicit and valid."""

        guide = read(RUN_STATUS_GUIDE)
        for block in java_blocks(guide):
            if "RunStatus status" in block:
                assert "import neqsim.process.processmodel.RunStatus;" in block

        links = re.findall(r"\[[^]]+\]\(([^)]+)\)", guide)
        assert len(links) == 3
        for destination in links:
            path_text = destination.split("#", maxsplit=1)[0]
            assert path_text.endswith(".md")
            assert (RUN_STATUS_GUIDE.parent / path_text).resolve().is_file()

    def test_documented_observability_apis_exist_in_current_source(self) -> None:
        """Keep the documented entry points aligned with Java source."""

        process_system = read(PROCESS_SYSTEM)
        process_model = read(PROCESS_MODEL)
        run_status = read(RUN_STATUS)
        unit_run_status = read(UNIT_RUN_STATUS)
        event_bus = read(EVENT_BUS)

        for signature in (
            "public RunStatus getRunStatus()",
            "public String getRunStatusJson()",
            "setProgressListener(SimulationProgressListener",
            "public void setPublishEvents(boolean",
            "public void setAutoValidate(boolean",
        ):
            assert signature in process_system

        for signature in (
            "public RunStatus getRunStatus()",
            "public String getRunStatusJson()",
            "setProgressListener(ModelProgressListener",
            "public void setPublishEvents(boolean",
            "public void setAutoValidate(boolean",
        ):
            assert signature in process_model

        for signature in (
            "public boolean isSuccess()",
            "public String getFailedUnitName()",
            "public String getFailedUnitError()",
            "public List<UnitRunStatus> getUnits()",
        ):
            assert signature in run_status

        assert "public String getAreaName()" in unit_run_status
        assert "public void subscribe(ProcessEventListener listener)" in event_bus

    def test_examples_have_executable_java_regression_anchors(self) -> None:
        """Keep both guide concepts connected to focused Java tests."""

        run_status_test = read(RUN_STATUS_TEST)
        hooks_test = read(HOOKS_TEST)
        model_hooks_test = read(MODEL_HOOKS_TEST)

        for method in (
            "testRunStatusSuccess",
            "testRunStatusJsonSchemaVersioned",
            "testRunStatusRecordsFailedUnit",
            "testProcessModelAggregatesRunStatus",
        ):
            assert method in run_status_test

        for method in (
            "testBeforeHooksFiredInRunWithProgress",
            "testEventBusReceivesEventsWhenEnabled",
            "testAutoValidateRunsOnValidProcess",
        ):
            assert method in hooks_test

        for method in (
            "testModelLifecycleHooksFired",
            "testEventBusReceivesEventsWhenEnabled",
            "testAutoValidateRunsOnValidModel",
        ):
            assert method in model_hooks_test


if __name__ == "__main__":
    unittest.main()
