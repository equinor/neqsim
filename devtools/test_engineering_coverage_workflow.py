"""Keep inventory validation compatible with protected default branches."""
from pathlib import Path

import yaml


WORKFLOW = Path(__file__).resolve().parents[1] / (
    ".github/workflows/ai-engineering-capability-coverage.yml"
)
INVENTORY = "src/main/resources/neqsim/mcp/engineering-coverage.json"


def test_inventory_writes_require_an_internal_non_dependabot_pull_request():
    workflow = yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))
    writers = [
        step
        for step in workflow["jobs"]["coverage"]["steps"]
        if "git push" in step.get("run", "")
    ]
    assert writers, "Retain automatic inventory updates on eligible PR branches"
    for writer in writers:
        condition = writer["if"]
        assert "||" not in condition, "No alternative event may enable a push"
        guards = {part.strip() for part in condition.split("&&")}
        assert "github.event_name == 'pull_request'" in guards
        assert "github.actor != 'dependabot[bot]'" in guards
        assert (
            "github.event.pull_request.head.repo.full_name == github.repository"
            in guards
        )
        assert (
            "github.event.pull_request.head.ref != github.event.repository.default_branch"
            in guards
        )


def test_validated_inventory_is_retained_before_any_optional_push():
    workflow = yaml.safe_load(WORKFLOW.read_text(encoding="utf-8"))
    steps = workflow["jobs"]["coverage"]["steps"]
    generator = next(i for i, step in enumerate(steps)
                     if "build_engineering_coverage.py" in step.get("run", ""))
    tests = next(i for i, step in enumerate(steps)
                 if "pytest" in step.get("run", ""))
    uploads = [(i, step) for i, step in enumerate(steps)
               if step.get("uses", "").startswith("actions/upload-artifact@")]
    assert uploads, "Protected branches need a retrievable generated inventory"
    upload_index, upload = uploads[0]
    assert generator < tests < upload_index
    assert upload["with"]["path"] == INVENTORY
    assert upload["with"]["if-no-files-found"] == "error"
    assert "if" not in upload, "Retain validated output for every supported event"
    assert not any(step.get("continue-on-error", False) for step in steps)
    assert all(upload_index < i for i, step in enumerate(steps)
               if "git push" in step.get("run", ""))
