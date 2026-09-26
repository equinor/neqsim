"""Continuous task solving for NeqSim task folders (living tasks).

Public, plant-agnostic foundation: stop rules for "solve until the goal is met or the
improvement becomes marginal", an append-only improvement ledger, watermarks, and a
plugin registry through which source adapters, stages and notifiers are supplied by
this package, by community skills, or by private (enterprise) packages.

Everything here is additive: a task folder without a ``continuous/`` directory is
untouched, and no existing command changes behaviour.
"""

from .adapters import FileDropAdapter
from .contracts import (SourceResult, StageResult, available, register, resolve)
from .drift import DriftMonitor
from .ledger import Ledger, LedgerError
from .stop_rules import StopDecision, evaluate, should_reopen
from .watermarks import Watermarks
from .cycle import run_cycle
from .living import make_living, promote, status
from .solve import solve
from .backtest import run_backtest

SCHEMA_VERSION = "1.0"

__all__ = [
    "SCHEMA_VERSION", "SourceResult", "StageResult", "available", "register", "resolve",
    "Ledger", "LedgerError", "StopDecision", "evaluate", "should_reopen", "Watermarks",
    "FileDropAdapter", "DriftMonitor", "run_cycle", "make_living", "promote", "status",
    "solve", "run_backtest",
]
