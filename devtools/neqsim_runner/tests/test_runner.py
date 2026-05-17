"""Tests for neqsim_runner package — covers critical fixes and core logic."""
import json
import os
import tempfile
import shutil
from pathlib import Path

import pytest

from neqsim_runner.models import Job, JobStatus, InvalidTransitionError
from neqsim_runner.store import JobStore
from neqsim_runner.progress import TaskProgress
from neqsim_runner.agent_bridge import AgentBridge


# ── Fixtures ──

@pytest.fixture
def tmp_dir(tmp_path):
    return tmp_path


@pytest.fixture
def store(tmp_dir):
    return JobStore(str(tmp_dir / "test.db"))


@pytest.fixture
def progress_dir(tmp_dir):
    task = tmp_dir / "task_solve" / "2026-04-08_test"
    task.mkdir(parents=True)
    return task


# ── Model Tests ──

class TestJob:
    def test_roundtrip_serialization(self):
        job = Job("test.py", args={"p": 60.0}, job_type="notebook")
        d = job.to_dict()
        restored = Job.from_dict(d)
        assert restored.script == "test.py"
        assert restored.args == {"p": 60.0}
        assert restored.job_type == "notebook"
        assert restored.status == JobStatus.PENDING

    def test_default_job_type_is_script(self):
        job = Job("test.py")
        assert job.job_type == "script"

    def test_backward_compat_missing_job_type(self):
        d = Job("test.py").to_dict()
        del d["job_type"]
        restored = Job.from_dict(d)
        assert restored.job_type == "script"


# ── Store Tests ──

class TestJobStore:
    def test_save_and_get(self, store):
        job = Job("myscript.py", args={"temp": 25})
        store.save_job(job)
        loaded = store.get_job(job.job_id)
        assert loaded is not None
        assert loaded.script == "myscript.py"
        assert loaded.args == {"temp": 25}

    def test_pending_jobs(self, store):
        j1 = Job("a.py")
        j2 = Job("b.py")
        j3 = Job("c.py")
        j3.status = JobStatus.SUCCESS
        store.save_job(j1)
        store.save_job(j2)
        store.save_job(j3)
        pending = store.get_pending_jobs()
        ids = {j.job_id for j in pending}
        assert j1.job_id in ids
        assert j2.job_id in ids
        assert j3.job_id not in ids

    def test_job_type_column_migration(self, tmp_dir):
        """Verify that a DB created without job_type column gets migrated."""
        db_path = str(tmp_dir / "old.db")
        import sqlite3
        conn = sqlite3.connect(db_path)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS jobs (
                job_id TEXT PRIMARY KEY,
                script TEXT NOT NULL,
                args TEXT NOT NULL DEFAULT '{}',
                max_retries INTEGER NOT NULL DEFAULT 3,
                timeout_seconds INTEGER NOT NULL DEFAULT 3600,
                checkpoint_interval INTEGER,
                workdir TEXT,
                status TEXT NOT NULL DEFAULT 'pending',
                attempt INTEGER NOT NULL DEFAULT 0,
                created_at TEXT, started_at TEXT, finished_at TEXT,
                error_message TEXT, result_path TEXT, checkpoint_path TEXT,
                pid INTEGER
            );
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS job_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                job_id TEXT NOT NULL, timestamp TEXT NOT NULL,
                event TEXT NOT NULL, message TEXT
            );
        """)
        conn.execute(
            "INSERT INTO jobs (job_id, script, status) VALUES (?, ?, ?)",
            ("old-job-1", "old.py", "pending"),
        )
        conn.commit()
        conn.close()

        s = JobStore(db_path)
        job = s.get_job("old-job-1")
        assert job is not None
        assert job.job_type == "script"


# ── Progress Tests ──

class TestTaskProgress:
    def test_fresh_start(self, progress_dir):
        p = TaskProgress(progress_dir)
        assert not p.is_resuming()
        assert p.current_phase() == "phase0"

    def test_checkpoint_and_resume(self, progress_dir):
        p = TaskProgress(progress_dir)
        p.complete_milestone("step1_spec_written",
                             summary="Spec done",
                             outputs=["step1/task_spec.md"],
                             decisions={"eos": "SRK"})
        p.set_next_action("Create notebook")

        p2 = TaskProgress(progress_dir)
        assert p2.is_resuming()
        assert p2.is_milestone_done("step1_spec_written")
        assert p2.get_decision("eos") == "SRK"
        assert "Create notebook" in p2.next_action()

    def test_resume_summary_text(self, progress_dir):
        p = TaskProgress(progress_dir)
        p.complete_milestone("step1_research_done", summary="Research complete")
        p.set_next_action("Build notebook")
        text = p.resume_summary(format="text")
        assert "TASK RESUME POINT" in text
        assert "step1_research_done" in text
        assert "Build notebook" in text

    def test_resume_summary_json(self, progress_dir):
        p = TaskProgress(progress_dir)
        p.complete_milestone("step1_spec_written",
                             summary="Spec done",
                             outputs=["task_spec.md"],
                             decisions={"scale": "Standard"})
        p.store_context("fluid", {"methane": 0.85})
        p.set_next_action("Create notebook")

        data = p.resume_summary(format="json")
        assert isinstance(data, dict)
        assert data["current_phase"] == "step1"
        assert data["next_action"] == "Create notebook"
        assert data["decisions"]["scale"] == "Standard"
        assert data["context"]["fluid"]["methane"] == 0.85
        assert len(data["completed_milestones"]) == 1
        assert data["completed_milestones"][0]["name"] == "step1_spec_written"
        assert "task_spec.md" in data["completed_milestones"][0]["outputs"]

    def test_no_duplicate_milestones(self, progress_dir):
        p = TaskProgress(progress_dir)
        p.complete_milestone("step1_spec_written", summary="first")
        p.complete_milestone("step1_spec_written", summary="second")
        assert p.completed_milestones().count("step1_spec_written") == 1

    def test_error_logging(self, progress_dir):
        p = TaskProgress(progress_dir)
        p.log_error("Something broke", recoverable=True)
        p2 = TaskProgress(progress_dir)
        errors = p2._data.get("error_log", [])
        assert len(errors) == 1
        assert "Something broke" in errors[0]["message"]

    def test_job_id_tracking(self, progress_dir):
        p = TaskProgress(progress_dir)
        p.add_job_id("job-abc123")
        p.add_job_id("job-abc123")  # duplicate
        p.add_job_id("job-def456")
        assert p.get_job_ids() == ["job-abc123", "job-def456"]

    def test_atomic_write_survives(self, progress_dir):
        """File should be valid JSON even after multiple rapid writes."""
        p = TaskProgress(progress_dir)
        for i in range(20):
            p.complete_milestone(f"m{i}", summary=f"milestone {i}")
        p2 = TaskProgress(progress_dir)
        assert len(p2.completed_milestones()) == 20


# ── Worker Bootstrap Validation Tests ──

class TestWorkerBootstrap:
    def test_script_bootstrap_has_path_validation(self):
        from neqsim_runner.worker import _WORKER_BOOTSTRAP
        assert "os.path.exists(script_path)" in _WORKER_BOOTSTRAP
        assert "_status.json" in _WORKER_BOOTSTRAP

    def test_notebook_executor_has_path_validation(self):
        from neqsim_runner.worker import _NOTEBOOK_EXECUTOR
        assert "os.path.exists(notebook_path)" in _NOTEBOOK_EXECUTOR

    def test_notebook_executor_has_backup(self):
        from neqsim_runner.worker import _NOTEBOOK_EXECUTOR
        assert ".backup" in _NOTEBOOK_EXECUTOR

    def test_notebook_executor_writes_status_on_import_error(self):
        from neqsim_runner.worker import _NOTEBOOK_EXECUTOR
        after_import = _NOTEBOOK_EXECUTOR.split("except ImportError:")[-1].split("try:")[0]
        assert "_status.json" in after_import

    def test_notebook_executor_logs_partial_save_failure(self):
        from neqsim_runner.worker import _NOTEBOOK_EXECUTOR
        assert "Could not save partial notebook" in _NOTEBOOK_EXECUTOR

    def test_runner_bootstraps_require_devtools(self):
        from neqsim_runner.worker import _WORKER_BOOTSTRAP, _NOTEBOOK_EXECUTOR
        assert "NEQSIM_REQUIRE_DEVTOOLS" in _WORKER_BOOTSTRAP
        assert "from neqsim import jneqsim" not in _WORKER_BOOTSTRAP
        assert "pip mode is disabled" in _WORKER_BOOTSTRAP
        assert "PYTHONPATH" in _NOTEBOOK_EXECUTOR
        assert "NEQSIM_PROJECT_ROOT" in _NOTEBOOK_EXECUTOR

    def test_notebook_script_setup_uses_devtools_only(self):
        from neqsim_runner.agent_bridge import _process_code_cell
        processed = _process_code_cell(
            "from neqsim_dev_setup import neqsim_init\nNEQSIM_MODE = 'devtools'\n"
        )
        assert "NEQSIM_PROJECT_ROOT" in processed
        assert "neqsim_dev_setup" in processed
        assert "jneqsim" not in processed
        assert "pip install" not in processed


class TestWorkerProcess:
    def test_log_handles_stored(self):
        from neqsim_runner.worker import WorkerProcess
        job = Job("dummy.py")
        wp = WorkerProcess(job)
        assert hasattr(wp, "_log_stdout")
        assert hasattr(wp, "_log_stderr")
        assert wp._log_stdout is None
        assert wp._log_stderr is None

    def test_close_logs_method_exists(self):
        from neqsim_runner.worker import WorkerProcess
        assert hasattr(WorkerProcess, "_close_logs")


class TestAgentBridgeResults:
    def _store_completed_job(self, bridge, tmp_dir, results, script_name):
        script_path = tmp_dir / script_name
        script_path.write_text("print('ok')\n", encoding="utf-8")
        job = Job(str(script_path), job_type="notebook")
        result_dir = bridge.output_dir / job.job_id / "output"
        result_dir.mkdir(parents=True)
        with open(result_dir / "results.json", "w", encoding="utf-8") as results_file:
            json.dump(results, results_file, indent=2)
        job.status = JobStatus.SUCCESS
        job.result_path = str(result_dir)
        bridge.store.save_job(job)
        bridge._job_ids.append(job.job_id)
        bridge.progress.add_job_id(job.job_id)
        return job.job_id

    def test_merge_results_to_task_preserves_prior_notebook_outputs(self, tmp_dir):
        task_dir = tmp_dir / "task_solve" / "2026-04-28_merge"
        task_dir.mkdir(parents=True)
        bridge = AgentBridge(task_dir=task_dir, project_root=tmp_dir)

        with open(task_dir / "results.json", "w", encoding="utf-8") as results_file:
            json.dump({"key_results": {"base_case": 1}, "figure_discussion": []},
                      results_file)

        first_job = self._store_completed_job(
            bridge,
            tmp_dir,
            {"key_results": {"power_kW": 42.0},
             "figure_discussion": [{"figure": "power.png"}]},
            "first.ipynb",
        )
        second_job = self._store_completed_job(
            bridge,
            tmp_dir,
            {"key_results": {"temperature_C": 15.0},
             "figure_discussion": [{"figure": "temperature.png"}]},
            "second.ipynb",
        )

        bridge.merge_results_to_task([first_job, second_job])

        with open(task_dir / "results.json", encoding="utf-8") as results_file:
            merged = json.load(results_file)
        assert merged["key_results"]["base_case"] == 1
        assert merged["key_results"]["power_kW"] == 42.0
        assert merged["key_results"]["temperature_C"] == 15.0
        assert {entry["figure"] for entry in merged["figure_discussion"]} == {
            "power.png", "temperature.png"}

    def test_copy_results_to_task_can_merge(self, tmp_dir):
        task_dir = tmp_dir / "task_solve" / "2026-04-28_copy_merge"
        task_dir.mkdir(parents=True)
        bridge = AgentBridge(task_dir=task_dir, project_root=tmp_dir)
        with open(task_dir / "results.json", "w", encoding="utf-8") as results_file:
            json.dump({"key_results": {"existing": 1}}, results_file)

        job_id = self._store_completed_job(
            bridge,
            tmp_dir,
            {"key_results": {"new_value": 2}},
            "merge.ipynb",
        )
        bridge.copy_results_to_task(job_id, merge=True)

        with open(task_dir / "results.json", encoding="utf-8") as results_file:
            merged = json.load(results_file)
        assert merged["key_results"] == {"existing": 1, "new_value": 2}


# ── Bootstrap File Loading Tests ──

class TestBootstrapFileLoading:
    """Verify that bootstrap scripts are loaded from real .py files."""

    def test_bootstrap_files_exist(self):
        from neqsim_runner.worker import _BOOTSTRAP_DIR
        assert (_BOOTSTRAP_DIR / "script_executor.py").exists()
        assert (_BOOTSTRAP_DIR / "notebook_executor.py").exists()

    def test_loaded_content_matches_files(self):
        from neqsim_runner.worker import _WORKER_BOOTSTRAP, _NOTEBOOK_EXECUTOR, _BOOTSTRAP_DIR
        with open(_BOOTSTRAP_DIR / "script_executor.py", encoding="utf-8") as f:
            assert f.read() == _WORKER_BOOTSTRAP
        with open(_BOOTSTRAP_DIR / "notebook_executor.py", encoding="utf-8") as f:
            assert f.read() == _NOTEBOOK_EXECUTOR


# ── Job Status Transition Validation Tests ──

class TestJobTransitions:
    def test_valid_pending_to_running(self):
        job = Job("test.py")
        assert job.status == JobStatus.PENDING
        job.set_status(JobStatus.RUNNING)
        assert job.status == JobStatus.RUNNING

    def test_valid_running_to_success(self):
        job = Job("test.py")
        job.set_status(JobStatus.RUNNING)
        job.set_status(JobStatus.SUCCESS)
        assert job.status == JobStatus.SUCCESS

    def test_valid_running_to_failed(self):
        job = Job("test.py")
        job.set_status(JobStatus.RUNNING)
        job.set_status(JobStatus.FAILED)
        assert job.status == JobStatus.FAILED

    def test_valid_failed_to_retrying(self):
        job = Job("test.py")
        job.set_status(JobStatus.RUNNING)
        job.set_status(JobStatus.FAILED)
        job.set_status(JobStatus.RETRYING)
        assert job.status == JobStatus.RETRYING

    def test_valid_retrying_to_pending(self):
        job = Job("test.py")
        job.set_status(JobStatus.RUNNING)
        job.set_status(JobStatus.FAILED)
        job.set_status(JobStatus.RETRYING)
        job.set_status(JobStatus.PENDING)
        assert job.status == JobStatus.PENDING

    def test_invalid_pending_to_success_raises(self):
        job = Job("test.py")
        with pytest.raises(InvalidTransitionError, match="pending.*success"):
            job.set_status(JobStatus.SUCCESS)

    def test_invalid_success_to_running_raises(self):
        job = Job("test.py")
        job.set_status(JobStatus.RUNNING)
        job.set_status(JobStatus.SUCCESS)
        with pytest.raises(InvalidTransitionError):
            job.set_status(JobStatus.RUNNING)

    def test_invalid_cancelled_is_terminal(self):
        job = Job("test.py")
        job.set_status(JobStatus.CANCELLED)
        with pytest.raises(InvalidTransitionError):
            job.set_status(JobStatus.PENDING)

    def test_any_state_can_be_cancelled(self):
        for start in [JobStatus.PENDING, JobStatus.RUNNING, JobStatus.FAILED,
                      JobStatus.TIMED_OUT, JobStatus.RETRYING]:
            job = Job("test.py")
            job.status = start  # direct set for test setup
            job.set_status(JobStatus.CANCELLED)
            assert job.status == JobStatus.CANCELLED

    def test_timed_out_to_retrying(self):
        job = Job("test.py")
        job.set_status(JobStatus.RUNNING)
        job.set_status(JobStatus.TIMED_OUT)
        job.set_status(JobStatus.RETRYING)
        assert job.status == JobStatus.RETRYING


# ── Rate Limiting Tests ──

class TestRateLimiting:
    def test_queue_hard_limit_raises(self, store, tmp_dir):
        """Submitting past the hard limit should raise RuntimeError."""
        from neqsim_runner.agent_bridge import AgentBridge
        task_dir = tmp_dir / "task"
        task_dir.mkdir()
        bridge = AgentBridge(task_dir, db_path=str(tmp_dir / "rl.db"),
                             queue_limit=3, queue_warn=2)
        # Create a dummy script
        script = task_dir / "sim.py"
        script.write_text("print('hello')")

        # Submit up to the limit
        bridge.submit_script(str(script))
        bridge.submit_script(str(script))
        bridge.submit_script(str(script))

        with pytest.raises(RuntimeError, match="hard limit"):
            bridge.submit_script(str(script))

    def test_queue_warn_logs(self, store, tmp_dir, caplog):
        """Submitting past the warn threshold should log a warning."""
        import logging
        from neqsim_runner.agent_bridge import AgentBridge
        task_dir = tmp_dir / "task2"
        task_dir.mkdir()
        bridge = AgentBridge(task_dir, db_path=str(tmp_dir / "rl2.db"),
                             queue_limit=100, queue_warn=2)
        script = task_dir / "sim.py"
        script.write_text("print('hello')")

        bridge.submit_script(str(script))
        bridge.submit_script(str(script))  # at threshold now
        with caplog.at_level(logging.WARNING, logger="neqsim_runner.agent_bridge"):
            bridge.submit_script(str(script))  # should warn
        assert any("warning threshold" in r.message.lower() for r in caplog.records)


# ── Structured Logging Tests ──

class TestStructuredLogging:
    def test_slog_function_produces_structured_output(self, caplog):
        """_slog should produce pipe-delimited structured log messages."""
        import logging
        from neqsim_runner.supervisor import _slog
        with caplog.at_level(logging.DEBUG, logger="neqsim_runner.supervisor"):
            _slog("info", "test_event", x=1, y="two")
        assert any("test_event" in r.message and "x=1" in r.message
                    for r in caplog.records)


# ── Monotonic Clock Tests ──

class TestMonotonicClock:
    def test_supervisor_has_mono_starts(self):
        """Supervisor should track monotonic start times."""
        from neqsim_runner.supervisor import Supervisor
        s = Supervisor(store=None)
        assert hasattr(s, "_mono_starts")
        assert isinstance(s._mono_starts, dict)
