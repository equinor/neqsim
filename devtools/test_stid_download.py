"""Offline integration tests for devtools/stid_download.py.

Drives the REAL stidapi (>=1.4.4) Tag/Doc/Revision/File classes through a
fake HTTP layer (``stidapi.utils.get_api_client``) so the test exercises the
actual object model stid_download.py depends on -- including the
``File.download_file()`` resolver added in stidapi 1.4.4 -- without any
network access or STID credentials. Skips cleanly when stidapi is not
installed (it is an internal Equinor package, not available in public CI).
"""
import io
import json
import os
import sys
import unittest
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(__file__))

import stid_download  # noqa: E402

try:
    import stidapi.utils as stid_utils  # noqa: E402
    from stidapi.doc import File  # noqa: E402

    _STIDAPI_AVAILABLE = True
except ImportError:
    _STIDAPI_AVAILABLE = False


class FakeStidClient:
    """Fake stidapi API client backing get_json()/get_file() calls.

    Routes requests by URL substring the same way the real endpoints are
    shaped, so the real Tag/Doc/Revision/File classes can run unmodified.
    """

    def __init__(self, tag_search=None, tag_docrefs=None, doc_get=None,
                 file_bytes=None, fail_file_ids=()):
        self.raise_for_status = False
        self.tag_search = tag_search or {}
        self.tag_docrefs = tag_docrefs or {}
        self.doc_get = doc_get or {}
        self.file_bytes = file_bytes or (b"%PDF-1.4 fake" + b"0" * 2000)
        self.fail_file_ids = {str(x) for x in fail_file_ids}
        self.get_file_urls = []

    def get_json(self, url):
        if "/tag/document-refs" in url:
            for tag_no, refs in self.tag_docrefs.items():
                if "tagNo=" + tag_no in url:
                    return refs
            return []
        if "/tags?" in url:
            for tag_no, tags in self.tag_search.items():
                if "tagNo=" + tag_no in url:
                    return tags
            return []
        if "/document?docNo=" in url:
            doc_no = url.split("docNo=")[-1]
            return self.doc_get.get(doc_no)
        raise AssertionError("Unexpected fake STID URL: {}".format(url))

    def get_file(self, url, file_name):
        self.get_file_urls.append(url)
        file_id = url.rstrip("/").split("/")[-1]
        if file_id in self.fail_file_ids:
            raise RuntimeError("simulated download failure for file {}".format(file_id))
        with open(file_name, "wb") as fh:
            fh.write(self.file_bytes)
        return file_name


@unittest.skipUnless(_STIDAPI_AVAILABLE, "stidapi is not installed (internal Equinor package)")
class StidDownloadIntegrationTest(unittest.TestCase):
    """Exercises stid_download.py against the real stidapi object model."""

    def setUp(self):
        self._orig_get_api_client = stid_utils.get_api_client
        self._orig_get_api_url = stid_utils.get_api_url
        stid_utils.get_api_url = lambda: "https://fake-stid/api/"

    def tearDown(self):
        stid_utils.get_api_client = self._orig_get_api_client
        stid_utils.get_api_url = self._orig_get_api_url

    def _install(self, client):
        stid_utils.get_api_client = lambda: client

    # -- get_docs_for_tags ------------------------------------------------

    def test_get_docs_for_tags_basic(self):
        client = FakeStidClient(
            tag_search={"30PT0001": [{"tagNo": "30PT0001", "instCode": "MYINST"}]},
            tag_docrefs={"30PT0001": [
                {"docNo": "DOC-001", "docTitle": "P&ID Sheet 1", "docType": "PID",
                 "files": [{"id": 101, "fileName": "DOC-001.pdf", "blobId": "b101"}]},
                {"docNo": "DOC-002", "docTitle": "Datasheet", "docType": "DS",
                 "files": [{"id": 102, "fileName": "DOC-002.pdf", "blobId": "b102"}]},
            ]},
        )
        self._install(client)
        docs = stid_download.get_docs_for_tags("MYINST", ["30PT0001"])
        self.assertEqual(set(docs), {"DOC-001", "DOC-002"})
        self.assertEqual(docs["DOC-001"]["tags_referencing"], ["30PT0001"])

    def test_get_docs_for_tags_missing_tag_is_skipped(self):
        client = FakeStidClient(tag_search={}, tag_docrefs={})
        self._install(client)
        buf = io.StringIO()
        with redirect_stdout(buf):
            docs = stid_download.get_docs_for_tags("MYINST", ["NOPE0001"])
        self.assertEqual(docs, {})
        self.assertIn("Tag not found", buf.getvalue())

    def test_get_docs_for_tags_dedups_across_tags(self):
        shared_doc = {"docNo": "DOC-009", "docTitle": "Shared PID", "docType": "PID",
                       "files": [{"id": 900, "fileName": "DOC-009.pdf", "blobId": "b900"}]}
        client = FakeStidClient(
            tag_search={
                "TAG-A": [{"tagNo": "TAG-A", "instCode": "MYINST"}],
                "TAG-B": [{"tagNo": "TAG-B", "instCode": "MYINST"}],
            },
            tag_docrefs={"TAG-A": [shared_doc], "TAG-B": [shared_doc]},
        )
        self._install(client)
        docs = stid_download.get_docs_for_tags("MYINST", ["TAG-A", "TAG-B"])
        self.assertEqual(len(docs), 1)
        self.assertEqual(docs["DOC-009"]["tags_referencing"], ["TAG-A", "TAG-B"])

    # -- download_doc_files -------------------------------------------------

    def test_download_doc_files_from_raw_dicts_uses_real_resolver(self):
        client = FakeStidClient()
        self._install(client)
        all_docs = {
            "DOC-001": {"docTitle": "P&ID", "files": [
                {"id": 101, "fileName": "DOC-001.pdf", "blobId": "b101"},
            ]},
        }
        with self._tmp_dir() as out_dir:
            buf = io.StringIO()
            with redirect_stdout(buf):
                downloaded, failed = stid_download.download_doc_files("MYINST", all_docs, out_dir)
            self.assertEqual(failed, [])
            self.assertEqual(len(downloaded), 1)
            self.assertTrue(os.path.exists(os.path.join(out_dir, "DOC-001.pdf")))
            # Locks in the stidapi>=1.4.4 URL resolver: {inst}/file/{id}, not
            # the old dual-pattern guess (.../file/{id} then .../file/blob/{blobId}).
            self.assertEqual(len(client.get_file_urls), 1)
            self.assertTrue(client.get_file_urls[0].endswith("MYINST/file/101"))

    def test_download_doc_files_skips_non_pdf(self):
        client = FakeStidClient()
        self._install(client)
        all_docs = {
            "DOC-005": {"docTitle": "Excel export", "files": [
                {"id": 500, "fileName": "DOC-005.xlsx", "blobId": "b500"},
            ]},
        }
        with self._tmp_dir() as out_dir:
            downloaded, failed = stid_download.download_doc_files("MYINST", all_docs, out_dir)
            self.assertEqual(downloaded, [])
            self.assertEqual(failed, [])
            self.assertEqual(client.get_file_urls, [])

    def test_download_doc_files_records_failure(self):
        client = FakeStidClient(fail_file_ids=["101"])
        self._install(client)
        all_docs = {
            "DOC-001": {"docTitle": "P&ID", "files": [
                {"id": 101, "fileName": "DOC-001.pdf", "blobId": "b101"},
            ]},
        }
        with self._tmp_dir() as out_dir:
            buf = io.StringIO()
            with redirect_stdout(buf):
                downloaded, failed = stid_download.download_doc_files("MYINST", all_docs, out_dir)
            self.assertEqual(downloaded, [])
            self.assertEqual(len(failed), 1)
            self.assertIn("simulated download failure", failed[0]["error"])
            self.assertFalse(os.path.exists(os.path.join(out_dir, "DOC-001.pdf")))

    def test_download_doc_files_skips_already_cached(self):
        client = FakeStidClient()
        self._install(client)
        all_docs = {
            "DOC-001": {"docTitle": "P&ID", "files": [
                {"id": 101, "fileName": "DOC-001.pdf", "blobId": "b101"},
            ]},
        }
        with self._tmp_dir() as out_dir:
            cached_path = os.path.join(out_dir, "DOC-001.pdf")
            with open(cached_path, "wb") as fh:
                fh.write(b"0" * 2000)
            downloaded, failed = stid_download.download_doc_files("MYINST", all_docs, out_dir)
            self.assertEqual(failed, [])
            self.assertEqual(downloaded[0]["status"], "cached")
            self.assertEqual(client.get_file_urls, [])

    def test_download_doc_files_accepts_file_objects_directly(self):
        """The --docs path (Doc.get_files()) hands File objects, not dicts."""
        client = FakeStidClient()
        self._install(client)
        file_obj = File({"id": 202, "fileName": "DOC-002.pdf", "blobId": "b202"})
        all_docs = {"DOC-002": {"docTitle": "Vendor datasheet", "files": [file_obj]}}
        with self._tmp_dir() as out_dir:
            downloaded, failed = stid_download.download_doc_files("MYINST", all_docs, out_dir)
            self.assertEqual(failed, [])
            self.assertEqual(len(downloaded), 1)
            self.assertTrue(client.get_file_urls[0].endswith("MYINST/file/202"))

    # -- main() end-to-end ---------------------------------------------------

    def test_main_end_to_end_tags(self):
        client = FakeStidClient(
            tag_search={"30PT0001": [{"tagNo": "30PT0001", "instCode": "MYINST"}]},
            tag_docrefs={"30PT0001": [
                {"docNo": "DOC-001", "docTitle": "P&ID Sheet 1", "docType": "PID",
                 "files": [{"id": 101, "fileName": "DOC-001.pdf", "blobId": "b101"}]},
            ]},
        )
        self._install(client)
        with self._tmp_task_dir() as task_dir:
            argv = ["stid_download.py", "--task-dir", task_dir, "--inst", "MYINST",
                    "--tags", "30PT0001"]
            self._run_main(argv)
            refs_dir = os.path.join(task_dir, "step1_scope_and_research", "references")
            self.assertTrue(os.path.exists(os.path.join(refs_dir, "DOC-001.pdf")))
            with open(os.path.join(refs_dir, "stid_retrieval_manifest.json")) as fh:
                manifest = json.load(fh)
            self.assertEqual(len(manifest["documents_retrieved"]), 1)
            self.assertEqual(manifest["documents_failed"], [])

    def test_main_end_to_end_docs_resolves_via_doc_api(self):
        client = FakeStidClient(
            doc_get={"DOC-003": {
                "docNo": "DOC-003", "docTitle": "Vendor Datasheet", "instCode": "MYINST",
                "currentRevision": {
                    "isCurrent": True,
                    "files": [{"id": 103, "fileName": "DOC-003.pdf", "blobId": "b103"}],
                },
            }},
        )
        self._install(client)
        with self._tmp_task_dir() as task_dir:
            argv = ["stid_download.py", "--task-dir", task_dir, "--inst", "MYINST",
                    "--docs", "DOC-003"]
            self._run_main(argv)
            refs_dir = os.path.join(task_dir, "step1_scope_and_research", "references")
            self.assertTrue(os.path.exists(os.path.join(refs_dir, "DOC-003.pdf")))
            self.assertTrue(client.get_file_urls[0].endswith("MYINST/file/103"))

    def test_main_exits_when_nothing_requested(self):
        with self._tmp_task_dir() as task_dir:
            argv = ["stid_download.py", "--task-dir", task_dir, "--inst", "MYINST"]
            with self.assertRaises(SystemExit) as ctx:
                self._run_main(argv, capture=True)
            self.assertEqual(ctx.exception.code, 1)

    def test_main_exits_when_task_dir_missing(self):
        argv = ["stid_download.py", "--task-dir", "/does/not/exist/anywhere",
                "--inst", "MYINST", "--tags", "30PT0001"]
        with self.assertRaises(SystemExit) as ctx:
            self._run_main(argv, capture=True)
        self.assertEqual(ctx.exception.code, 1)

    # -- helpers --------------------------------------------------------------

    def _run_main(self, argv, capture=False):
        old_argv = sys.argv
        sys.argv = argv
        try:
            if capture:
                with redirect_stdout(io.StringIO()):
                    stid_download.main()
            else:
                stid_download.main()
        finally:
            sys.argv = old_argv

    def _tmp_dir(self):
        import tempfile
        return _TempDirCtx(tempfile.mkdtemp(prefix="stid_dl_test_"))

    def _tmp_task_dir(self):
        import tempfile
        d = tempfile.mkdtemp(prefix="stid_dl_task_")
        os.makedirs(os.path.join(d, "step1_scope_and_research"), exist_ok=True)
        return _TempDirCtx(d)


class _TempDirCtx:
    """Minimal auto-cleanup context manager wrapping a temp directory path."""

    def __init__(self, path):
        self.path = path

    def __enter__(self):
        return self.path

    def __exit__(self, exc_type, exc, tb):
        import shutil
        shutil.rmtree(self.path, ignore_errors=True)
        return False


if __name__ == "__main__":
    unittest.main()
