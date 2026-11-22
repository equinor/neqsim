"""Reuse the unchanged Run65 ABBA runner in this run's separate evidence directory."""
import importlib.util
from pathlib import Path


def main():
    source = Path(__file__).resolve().parent.parent / 'initialization65/run_native.py'
    spec = importlib.util.spec_from_file_location('radial_abba_runner', source)
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    runner.__file__ = __file__
    runner.main()


if __name__ == '__main__':
    main()
