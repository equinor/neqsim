"""Build and execute Jupyter notebooks from ``# %%`` cell-marked Python files.

Authoring a task notebook as a plain ``.py`` file with ``# %%`` cell markers keeps the
source diffable and lintable, then this tool converts it to ``.ipynb`` and executes it so the
delivered notebook carries real outputs.

Cell markers:

* ``# %%`` starts a new code cell; an optional trailing title is kept as a comment.
* ``# %% [markdown]`` starts a markdown cell; subsequent ``#`` comment lines become the
  markdown source.

Usage::

    python devtools/py_to_notebook.py path/to/nb_01.py [--no-run] [--timeout 3600]
    python devtools/py_to_notebook.py path/to/folder --glob "nb_*.py"

The executed notebook is written next to the source with the same stem and an ``.ipynb``
suffix. A non-zero exit code is returned if any cell raises.
"""

import argparse
import pathlib
import sys

import nbformat
from nbclient import NotebookClient


def split_cells(text):
    """Splits ``# %%`` cell-marked source into (cell_type, source) pairs.

    :param text: full Python source
    :return: list of (cell_type, source) tuples
    """
    lines = text.splitlines()
    cells = []
    current_type = "code"
    current = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("# %%"):
            if current:
                cells.append((current_type, "\n".join(current).strip("\n")))
            current = []
            current_type = "markdown" if "[markdown]" in stripped else "code"
        else:
            current.append(line)
    if current:
        cells.append((current_type, "\n".join(current).strip("\n")))
    return [(kind, src) for kind, src in cells if src.strip()]


def to_markdown(source):
    """Converts a comment-only cell body into markdown source.

    :param source: cell body consisting of ``#`` comment lines
    :return: markdown text
    """
    out = []
    for line in source.splitlines():
        stripped = line.strip()
        if stripped.startswith("# "):
            out.append(stripped[2:])
        elif stripped == "#":
            out.append("")
        else:
            out.append(stripped)
    return "\n".join(out).strip("\n")


def build(py_path):
    """Builds a notebook object from a cell-marked Python file.

    :param py_path: path to the ``.py`` source
    :return: nbformat notebook node
    """
    text = pathlib.Path(py_path).read_text(encoding="utf-8")
    nb = nbformat.v4.new_notebook()
    nb.metadata["kernelspec"] = {
        "display_name": "Python 3", "language": "python", "name": "python3"}
    nb.metadata["language_info"] = {"name": "python", "version": sys.version.split()[0]}
    for kind, source in split_cells(text):
        if kind == "markdown":
            nb.cells.append(nbformat.v4.new_markdown_cell(to_markdown(source)))
        else:
            nb.cells.append(nbformat.v4.new_code_cell(source))
    return nb


def run(py_path, execute=True, timeout=3600):
    """Builds and optionally executes one notebook.

    :param py_path: path to the ``.py`` source
    :param execute: whether to execute the notebook
    :param timeout: per-cell timeout in seconds
    :return: path to the written ``.ipynb``
    """
    py_path = pathlib.Path(py_path).resolve()
    nb = build(py_path)
    out_path = py_path.with_suffix(".ipynb")
    if execute:
        client = NotebookClient(
            nb, timeout=timeout, kernel_name="python3",
            resources={"metadata": {"path": str(py_path.parent)}},
            allow_errors=False)
        client.execute()
    nbformat.write(nb, str(out_path))
    return out_path


def main(argv=None):
    """Command-line entry point.

    :param argv: optional argument list
    :return: process exit code
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", help="cell-marked .py file or a folder")
    parser.add_argument("--glob", default="nb_*.py", help="glob used when target is a folder")
    parser.add_argument("--no-run", action="store_true", help="convert without executing")
    parser.add_argument("--timeout", type=int, default=3600, help="per-cell timeout in seconds")
    args = parser.parse_args(argv)

    target = pathlib.Path(args.target)
    sources = sorted(target.glob(args.glob)) if target.is_dir() else [target]
    if not sources:
        print("no sources matched")
        return 1

    failed = 0
    for source in sources:
        try:
            out = run(source, execute=not args.no_run, timeout=args.timeout)
            print("OK   {} -> {}".format(source.name, out.name))
        except Exception as exc:  # noqa: BLE001
            failed += 1
            print("FAIL {}: {}".format(source.name, exc))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
