"""Result contracts and the plugin registry for adapters, stages and notifiers.

Plugins are found in two ways:

* in-process, with :func:`register` (tests, notebooks, task-local code);
* installed packages that declare Python entry points in the groups
  ``neqsim_continuous.adapters``, ``neqsim_continuous.stages`` and
  ``neqsim_continuous.notifiers``. Community and enterprise skill packages use this,
  so a third party without enterprise access simply does not see those plugins.

A missing plugin is not an error: :func:`resolve` returns ``None`` and the caller
records the source or stage as ``not_installed``.
"""

from dataclasses import asdict, dataclass, field
from typing import Any, Callable, Dict, List, Optional

KINDS = ("adapters", "stages", "notifiers")
SOURCE_STATUSES = ("ok", "partial", "stale", "failed", "not_installed")
STAGE_STATUSES = ("ok", "warn", "fail", "skipped", "not_installed")

_REGISTRY = {kind: {} for kind in KINDS}  # type: Dict[str, Dict[str, Callable[..., Any]]]


@dataclass
class SourceResult:
    """Outcome of one incremental source pull. Never carries credentials."""

    status: str
    rows: int = 0
    watermark: Optional[str] = None
    gaps: List[str] = field(default_factory=list)
    interaction_required: bool = False
    message: str = ""
    outputs: List[str] = field(default_factory=list)
    records: List[Dict[str, Any]] = field(default_factory=list)

    def __post_init__(self):
        if self.status not in SOURCE_STATUSES:
            raise ValueError("Unknown source status '{}'".format(self.status))

    def to_dict(self):
        data = asdict(self)
        data.pop("records", None)
        return data


@dataclass
class StageResult:
    """Outcome of one cycle stage."""

    name: str
    status: str
    outputs: List[str] = field(default_factory=list)
    kpis: Dict[str, float] = field(default_factory=dict)
    triggers: List[str] = field(default_factory=list)
    message: str = ""

    def __post_init__(self):
        if self.status not in STAGE_STATUSES:
            raise ValueError("Unknown stage status '{}'".format(self.status))

    def to_dict(self):
        return asdict(self)


def _check_kind(kind):
    if kind not in KINDS:
        raise ValueError("Unknown plugin kind '{}'. Valid: {}".format(kind, ", ".join(KINDS)))


def register(kind, name, factory):
    """Register a plugin factory in-process; replaces an earlier one of the same name."""
    _check_kind(kind)
    _REGISTRY[kind][name] = factory


def _entry_points(group):
    try:
        from importlib.metadata import entry_points
    except ImportError:  # Python < 3.8 fallback is not supported by devtools anyway
        return []
    found = entry_points()
    if hasattr(found, "select"):
        return list(found.select(group=group))
    return list(found.get(group, []))


def resolve(kind, name):
    """Return the plugin factory for ``name``, or None when it is not installed.

    ``name`` is a registered name, an entry-point name in group ``neqsim_continuous.<kind>``,
    or a dotted path ``package.module:Attribute`` to any importable factory (so a plugin
    package works without entry-point metadata).
    """
    _check_kind(kind)
    if name in _REGISTRY[kind]:
        return _REGISTRY[kind][name]
    if ":" in str(name):
        module_name, _, attribute = str(name).partition(":")
        try:
            import importlib
            return getattr(importlib.import_module(module_name), attribute)
        except (ImportError, AttributeError):
            return None
    for point in _entry_points("neqsim_continuous." + kind):
        if point.name == name:
            try:
                return point.load()
            except Exception:
                return None
    return None


def available(kind):
    """List plugin names that can be resolved for ``kind``."""
    _check_kind(kind)
    names = set(_REGISTRY[kind])
    names.update(point.name for point in _entry_points("neqsim_continuous." + kind))
    return sorted(names)
