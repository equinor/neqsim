"""Unit tests for the Windows path-length guard."""

import subprocess
import tempfile
import unittest
from pathlib import Path

from devtools import check_path_lengths as guard


class FindLongPathsTest(unittest.TestCase):
    def test_paths_within_budget_pass(self) -> None:
        self.assertEqual(guard.find_long_paths(["a/b.java", "x" * 200], max_length=200), [])

    def test_offenders_are_reported_longest_first(self) -> None:
        offenders = guard.find_long_paths(["s/" + "a" * 199, "t/" + "b" * 220, "ok.md"], max_length=200)

        self.assertEqual([length for length, _ in offenders], [222, 201])
        self.assertTrue(offenders[0][1].startswith("t/"))

    def test_report_names_every_offender_and_remediation(self) -> None:
        report = guard.format_report([(230, "docs/too_long.md")], 200)

        self.assertIn("docs/too_long.md", report)
        self.assertIn("230", report)
        self.assertIn("core.longpaths", report)


class TrackedPathsTest(unittest.TestCase):
    def test_reads_tracked_and_staged_files_from_git(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            subprocess.check_call(["git", "init", "-q", str(root)])
            (root / "tracked.txt").write_text("x", encoding="utf-8")
            (root / "sub").mkdir()
            (root / "sub" / "staged.txt").write_text("y", encoding="utf-8")
            (root / "untracked.txt").write_text("z", encoding="utf-8")
            subprocess.check_call(["git", "-C", str(root), "add", "tracked.txt", "sub/staged.txt"])

            paths = guard.tracked_paths(root)

        self.assertEqual(sorted(paths), ["sub/staged.txt", "tracked.txt"])

    def test_repository_is_within_budget(self) -> None:
        root = Path(__file__).resolve().parent.parent
        self.assertEqual(guard.find_long_paths(guard.tracked_paths(root)), [])


if __name__ == "__main__":
    unittest.main()
