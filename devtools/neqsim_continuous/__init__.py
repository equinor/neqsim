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
from .production import demonstrated_limit, evaluate_constraints
from .user_input import lever_limits
from .living import make_living, promote, resume, status
from .solve import solve
from .backtest import run_backtest, tune_drift
from .loop import run_loop
from .inference import (candidate_from_samples, ensemble_update, identifiability, innovation_gate, mda_std,
                        noise_std_from_series, replenish, sparse_precision, value_of_information)
from .state import STATE_SCHEMA_VERSION, StateSchemaError
from .evidence import EvidenceSchemaError, analyze as analyze_evidence, inventory as evidence_inventory
from .final_report import (FINAL_REPORT_SCHEMA_VERSION, FinalReportError,
                           finalize as finalize_report, status as final_report_status)

SCHEMA_VERSION = "1.0"

__all__ = [
    "SCHEMA_VERSION", "SourceResult", "StageResult", "available", "register", "resolve",
    "Ledger", "LedgerError", "StopDecision", "evaluate", "should_reopen", "Watermarks",
    "FileDropAdapter", "DriftMonitor", "run_cycle", "make_living", "promote", "resume", "status",
    "solve", "run_backtest", "tune_drift", "run_loop", "STATE_SCHEMA_VERSION", "StateSchemaError",
    "ensemble_update", "sparse_precision", "identifiability", "candidate_from_samples",
    "value_of_information", "innovation_gate", "mda_std", "noise_std_from_series", "replenish",
    "demonstrated_limit", "evaluate_constraints", "lever_limits",
    "analyze_evidence", "evidence_inventory", "EvidenceSchemaError",
    "FINAL_REPORT_SCHEMA_VERSION", "FinalReportError", "finalize_report", "final_report_status",
]
