"""Tests for devtools/doc_retriever.py (no network: backend calls are faked)."""
import json
import os

import pytest

import doc_retriever as dr


class FakeTag(object):
    def __init__(self, no, refs):
        self.no = no
        self._refs = refs

    def get_doc_references(self):
        return self._refs


def _ref(doc_no, title, pid=False, ds=False, pdf=True):
    return {"docNo": doc_no, "docTitle": title, "docType": "55", "isPid": pid,
            "isDatasheet": ds, "revStatus": "OF",
            "files": [{"fileName": doc_no + (".pdf" if pdf else ".dwg")}]}


def fake_search(inst_code, tag_no="", description="", take=50, **_):
    assert inst_code == "PLTA"
    if description == "compressor":
        return [FakeTag("20KA001", [_ref("D-XB-001", "Compression P&ID", pid=True),
                                    _ref("D-DS-002", "Compressor data sheet", ds=True)])]
    if description == "separator":
        return [FakeTag("20VA001", [_ref("D-XB-001", "Compression P&ID", pid=True),
                                    _ref("D-MD-003", "Misc note"),
                                    _ref("D-AA-004", "Drawing", pdf=False)])]
    return []


def fake_downloader(inst, selection, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    done = []
    for doc_no in selection:
        name = doc_no + ".pdf"
        with open(os.path.join(out_dir, name), "wb") as handle:
            handle.write(b"%PDF" + b"0" * 2000)
        done.append({"docNo": doc_no, "file": name, "status": "downloaded"})
    return done, []


@pytest.fixture
def backend(tmp_path, monkeypatch):
    cfg = tmp_path / "cfg.yaml"
    cfg.write_text("backend: stidapi\ninstallation_codes:\n  PLTA: Platform A\n  PLTB: Platform B\n"
                   "default_inst_code: PLTB\ndefault_doc_types: [CE, DS, AA, MD]\n",
                   encoding="utf-8")
    monkeypatch.setenv("NEQSIM_DOC_RETRIEVAL_CONFIG", str(cfg))
    monkeypatch.delenv("NEQSIM_DISABLE_DOC_RETRIEVAL", raising=False)
    task = tmp_path / "2026-01-01_improve_gas_export_at_platform_a"
    (task / "step1_scope_and_research").mkdir(parents=True)
    return task


def test_resolve_installation_from_name_code_and_text():
    cfg = {"installation_codes": {"PLTA": "Platform A", "PLTAB": "Platform AB", "VIS": "Visund"},
           "default_inst_code": "PLTA"}
    assert dr.resolve_installation(cfg, "platform a") == "PLTA"
    assert dr.resolve_installation(cfg, "plta") == "PLTA"
    assert dr.resolve_installation(cfg, text="study for platform ab export") == "PLTAB"
    # lower-case words must not match short codes, and no default fallback
    assert dr.resolve_installation(cfg, text="the vis viva of gas") is None
    assert dr.resolve_installation(cfg, text="VIS compressor study") == "VIS"


def test_retrieve_for_task_ranks_pid_and_datasheets(backend):
    status = dr.retrieve_for_task(str(backend), keywords=["compressor", "separator"], quiet=True,
                                  tag_search=fake_search, downloader=fake_downloader)
    assert status["status"] == "ok"
    assert status["installation"] == "PLTA"
    # DWG-only doc dropped; P&ID kept despite letter-code filter; MD kept via docNo segment
    assert status["documents_selected"] == 3
    assert status["pid_count"] == 1 and status["datasheet_count"] == 1
    out = backend / "step1_scope_and_research" / "references" / "stid"
    manifest = json.loads((out / dr.MANIFEST_FILE).read_text(encoding="utf-8"))
    assert manifest["documents_selected"][0]["docNo"] == "D-XB-001"
    assert manifest["documents_selected"][0]["tags_referencing"] == ["20KA001", "20VA001"]
    assert json.loads((out / dr.STATUS_FILE).read_text(encoding="utf-8"))["status"] == "ok"


def test_no_installation_and_no_matches_are_reported(backend, tmp_path):
    other = tmp_path / "2026-01-01_generic_study"
    (other / "step1_scope_and_research").mkdir(parents=True)
    status = dr.retrieve_for_task(str(other), quiet=True, tag_search=fake_search)
    assert status["status"] == "no_installation"
    status = dr.retrieve_for_task(str(backend), keywords=["nothing"], quiet=True,
                                  tag_search=fake_search, downloader=fake_downloader)
    assert status["status"] == "no_matches"


def test_backend_errors_are_classified_not_raised(backend):
    def failing(*_, **__):
        raise RuntimeError("returned status code 403 forbidden")
    status = dr.retrieve_for_task(str(backend), keywords=["compressor"], quiet=True,
                                  tag_search=failing)
    assert status["status"] == "auth_error"


def test_disabled_and_no_backend(tmp_path, monkeypatch):
    task = tmp_path / "task"
    task.mkdir()
    monkeypatch.setenv("NEQSIM_DISABLE_DOC_RETRIEVAL", "1")
    assert dr.retrieve_for_task(str(task), quiet=True)["status"] == "disabled"
    monkeypatch.delenv("NEQSIM_DISABLE_DOC_RETRIEVAL")
    cfg = tmp_path / "empty.yaml"
    cfg.write_text("backend: none\n", encoding="utf-8")
    monkeypatch.setenv("NEQSIM_DOC_RETRIEVAL_CONFIG", str(cfg))
    assert dr.retrieve_for_task(str(task), quiet=True)["status"] == "no_backend"
    assert not (task / "step1_scope_and_research" / "references" / "stid").exists()
