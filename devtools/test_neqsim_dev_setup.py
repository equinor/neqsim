"""Tests for the workspace JVM bootstrap."""

import neqsim_dev_setup


def test_workspace_uses_maven_dependencies_when_thin_jar_exists(tmp_path, monkeypatch):
    """A packaged thin JAR must not replace the runtime dependency classpath."""
    (tmp_path / "pom.xml").touch()
    target = tmp_path / "target"
    target.mkdir()
    (target / "neqsim-1.0.jar").touch()
    classes = target / "classes"
    classes.mkdir()

    started = {}
    monkeypatch.setattr(neqsim_dev_setup, "_ensure_jvm_available", lambda verbose: None)
    monkeypatch.setattr(neqsim_dev_setup, "_has_compiled_classes", lambda path: True)
    monkeypatch.setattr(neqsim_dev_setup, "_build_dependency_classpath", lambda root: ["dep.jar"])
    monkeypatch.setattr(neqsim_dev_setup, "_jvm_args", lambda: [])
    monkeypatch.setattr(neqsim_dev_setup.jpype, "isJVMStarted", lambda: False)
    monkeypatch.setattr(
        neqsim_dev_setup.jpype,
        "startJVM",
        lambda *args, **kwargs: started.update(kwargs),
    )

    neqsim_dev_setup.neqsim_init(tmp_path, extra_classpath=["extra.jar"], verbose=False)

    assert started["classpath"] == [
        str(classes),
        str(tmp_path / "src" / "main" / "resources"),
        "dep.jar",
        "extra.jar",
    ]
