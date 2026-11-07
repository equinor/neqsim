"""Guard repeatable, complete static-analysis input across filesystem orders."""
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

import prepare_code_health_input as preparation


class AnalysisInputTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write_classes(self, root, names):
        for name in names:
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"class bytes for " + name.encode())

    def test_creation_order_and_file_metadata_do_not_change_archive(self):
        names = ["neqsim/Z.class", "neqsim/A.class", "neqsim/A$Inner.class", "neqsim/package-info.class"]
        first, second = self.root / "first", self.root / "second"
        self.write_classes(first, names)
        self.write_classes(second, reversed(names))
        for path in second.rglob("*.class"):
            os.utime(path, (1700000000, 1700000000))
            path.chmod(0o600)
        (first / "resource.txt").write_text("not bytecode")
        a = preparation.prepare(first, self.root / "out-a")
        b = preparation.prepare(second, self.root / "out-b")
        self.assertEqual(a, b)
        self.assertEqual(4, a["classes"])
        self.assertEqual((self.root / "out-a" / preparation.ARCHIVE).read_bytes(),
                         (self.root / "out-b" / preparation.ARCHIVE).read_bytes())
        with zipfile.ZipFile(self.root / "out-a" / preparation.ARCHIVE) as archive:
            self.assertEqual(sorted(names), archive.namelist())
            self.assertEqual(b"class bytes for neqsim/A.class", archive.read("neqsim/A.class"))

    def test_deleted_and_changed_classes_refresh_the_archive(self):
        source, output = self.root / "classes", self.root / "output"
        self.write_classes(source, ["A.class", "B.class"])
        previous = preparation.prepare(source, output)
        (source / "B.class").unlink()
        (source / "A.class").write_bytes(b"new class")
        current = preparation.prepare(source, output)
        self.assertEqual(1, current["classes"])
        self.assertNotEqual(previous["class_content_sha256"], current["class_content_sha256"])
        with zipfile.ZipFile(output / preparation.ARCHIVE) as archive:
            self.assertEqual(["A.class"], archive.namelist())
            self.assertEqual(b"new class", archive.read("A.class"))

    def test_empty_input_and_output_within_classes_fail(self):
        source = self.root / "classes"
        with self.assertRaises(ValueError):
            preparation.prepare(source, self.root / "output")
        self.write_classes(source, ["A.class"])
        with self.assertRaises(ValueError):
            preparation.prepare(source, source / "output")

    def test_unmanaged_analysis_archives_are_not_silently_included(self):
        source, output = self.root / "classes", self.root / "output"
        self.write_classes(source, ["A.class"])
        output.mkdir()
        (output / "stale.jar").write_bytes(b"stale")
        with self.assertRaises(ValueError):
            preparation.prepare(source, output)

    def test_class_symlinks_are_rejected(self):
        source = self.root / "classes"
        self.write_classes(source, ["A.class"])
        (source / "B.class").symlink_to(source / "A.class")
        with self.assertRaises(ValueError):
            preparation.prepare(source, self.root / "output")

    def test_package_directory_symlinks_do_not_silently_drop_classes(self):
        source, external = self.root / "classes", self.root / "external"
        self.write_classes(source, ["A.class"])
        self.write_classes(external, ["B.class"])
        (source / "package").symlink_to(external, target_is_directory=True)
        with self.assertRaises(ValueError):
            preparation.prepare(source, self.root / "output")


if __name__ == "__main__":
    unittest.main()
