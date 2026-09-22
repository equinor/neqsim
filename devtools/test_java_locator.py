import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import java_locator as jl  # noqa: E402


def _make_java_home(root, name, version=None, with_javac=True):
    """Create a directory that looks like a real Java home to the locator."""
    home = root / name
    (home / "bin").mkdir(parents=True)
    exe = ".exe" if sys.platform.startswith("win") else ""
    (home / "bin" / ("java" + exe)).write_text("")
    if with_javac:
        (home / "bin" / ("javac" + exe)).write_text("")
    if sys.platform.startswith("win"):
        lib = home / "bin" / "server" / "jvm.dll"
    elif sys.platform == "darwin":
        lib = home / "lib" / "server" / "libjvm.dylib"
    else:
        lib = home / "lib" / "server" / "libjvm.so"
    lib.parent.mkdir(parents=True)
    lib.write_text("")
    if version:
        (home / "release").write_text('JAVA_VERSION="{v}"\n'.format(v=version))
    return home


@pytest.mark.parametrize("text,expected", [
    ('openjdk version "21.0.12.1" 2026-08-18 LTS', 21),
    ('java version "1.8.0_392"', 8),
    ('"25.0.1"', 25),
    ("", None),
    ("not a version", None),
])
def test_parse_java_major(text, expected):
    assert jl.parse_java_major(text) == expected


def test_inspect_rejects_directory_without_jvm_library(tmp_path):
    home = tmp_path / "fake"
    (home / "bin").mkdir(parents=True)
    exe = ".exe" if sys.platform.startswith("win") else ""
    (home / "bin" / ("java" + exe)).write_text("")
    assert jl._inspect(str(home), "test") is None


def test_inspect_reads_version_from_release_file(tmp_path):
    home = _make_java_home(tmp_path, "jdk-21", version="21.0.12.1")
    install = jl._inspect(str(home), "test", allow_binary_probe=False)
    assert install.major == 21
    assert install.is_jdk is True


def test_find_installs_prefers_jdk_then_newest(tmp_path, monkeypatch):
    old_jdk = _make_java_home(tmp_path, "jdk-11", version="11.0.22")
    new_jdk = _make_java_home(tmp_path, "jdk-21", version="21.0.12.1")
    new_jre = _make_java_home(tmp_path, "jre-25", version="25.0.1", with_javac=False)
    monkeypatch.delenv("JAVA_HOME", raising=False)
    monkeypatch.setattr(jl, "_java_home_from_path", lambda: None)
    monkeypatch.setattr(jl, "_registry_java_homes", lambda: [])
    monkeypatch.setattr(
        jl, "_glob_roots", lambda: [(str(tmp_path / "*"), "test")])

    installs = jl.find_java_installs()

    assert [i.home for i in installs] == [
        str(new_jdk), str(old_jdk), str(new_jre)]


def test_find_installs_skips_versions_below_minimum(tmp_path, monkeypatch):
    _make_java_home(tmp_path, "jdk-7", version="1.7.0_80")
    monkeypatch.delenv("JAVA_HOME", raising=False)
    monkeypatch.setattr(jl, "_java_home_from_path", lambda: None)
    monkeypatch.setattr(jl, "_registry_java_homes", lambda: [])
    monkeypatch.setattr(
        jl, "_glob_roots", lambda: [(str(tmp_path / "*"), "test")])

    assert jl.find_java_installs(min_major=8) == []


def test_ensure_java_home_keeps_a_valid_existing_value(tmp_path, monkeypatch):
    home = _make_java_home(tmp_path, "jdk-21", version="21.0.12.1")
    monkeypatch.setenv("JAVA_HOME", str(home))
    monkeypatch.setattr(jl, "find_java_installs", lambda **kw: pytest.fail(
        "should not search when JAVA_HOME already works"))

    assert jl.ensure_java_home() == str(home)


def test_ensure_java_home_sets_discovered_home_in_process(tmp_path, monkeypatch):
    home = _make_java_home(tmp_path, "jdk-21", version="21.0.12.1")
    monkeypatch.delenv("JAVA_HOME", raising=False)
    monkeypatch.setenv("PATH", "")
    monkeypatch.setattr(jl, "_java_home_from_path", lambda: None)
    monkeypatch.setattr(jl, "_registry_java_homes", lambda: [])
    monkeypatch.setattr(
        jl, "_glob_roots", lambda: [(str(tmp_path / "*"), "test")])

    resolved = jl.ensure_java_home()

    assert resolved == str(home)
    assert os.environ["JAVA_HOME"] == str(home)
    assert os.path.join(str(home), "bin") in os.environ["PATH"]


def test_ensure_java_home_returns_none_when_nothing_found(tmp_path, monkeypatch):
    monkeypatch.delenv("JAVA_HOME", raising=False)
    monkeypatch.setattr(jl, "_java_home_from_path", lambda: None)
    monkeypatch.setattr(jl, "_registry_java_homes", lambda: [])
    monkeypatch.setattr(
        jl, "_glob_roots", lambda: [(str(tmp_path / "empty" / "*"), "test")])

    assert jl.ensure_java_home() is None
