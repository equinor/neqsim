"""Typed configuration for the PVT forward-proxy generator.

All pressures are absolute in bara and all temperatures in degC unless the field name says
otherwise (``temperature_K``). Rates are per feed of ``feed.rate_kmol_h``; the same feed rate is
used for the reference and the proxy, so their rates are directly comparable.

A configuration is a plain YAML/JSON mapping. ``load_config`` validates it strictly: unknown keys
are rejected so a mistyped setting cannot silently fall back to a default.
"""
from __future__ import annotations

import copy
import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

R_KJ_KMOL_K = 8.314462618
STOCK_TANK = "stock_tank"


class ConfigError(ValueError):
    """Raised for an invalid or inconsistent configuration."""


def _take(data: Dict[str, Any], allowed: List[str], where: str) -> Dict[str, Any]:
    """Return ``data`` after rejecting keys outside ``allowed``."""
    if data is None:
        return {}
    if not isinstance(data, dict):
        raise ConfigError("%s must be a mapping" % where)
    unknown = sorted(set(data) - set(allowed))
    if unknown:
        raise ConfigError("unknown key(s) in %s: %s (allowed: %s)" % (where, ", ".join(unknown), ", ".join(allowed)))
    return data


@dataclass
class StandardConditions:
    """Measurement conditions for the oil and gas products.

    Oil is the stock-tank liquid volume at these conditions. Gas is every gas stream that leaves
    the process (including the stock-tank flash gas), converted with the ideal-gas molar volume
    at these conditions. Water is not part of the fluid ensemble.
    """

    temperature_K: float = 288.15
    pressure_bara: float = 1.01325

    @property
    def temperature_C(self) -> float:
        """Standard temperature in degC."""
        return self.temperature_K - 273.15

    @property
    def sm3_per_kmol(self) -> float:
        """Ideal-gas standard volume per kmol at these conditions."""
        return R_KJ_KMOL_K * self.temperature_K / (self.pressure_bara * 100.0)

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "StandardConditions":
        d = _take(d, ["temperature_K", "pressure_bara"], "standard_conditions")
        out = cls(**{k: float(v) for k, v in d.items()})
        if out.temperature_K <= 0 or out.pressure_bara <= 0:
            raise ConfigError("standard conditions must be positive")
        return out


@dataclass
class FeedConditions:
    """Feed (wellstream) conditions shared by every fluid in one calibration."""

    pressure_bara: float = 150.0
    temperature_C: float = 80.0
    rate_kmol_h: float = 100.0

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "FeedConditions":
        d = _take(d, ["pressure_bara", "temperature_C", "rate_kmol_h"], "feed")
        out = cls(**{k: float(v) for k, v in d.items()})
        if out.pressure_bara <= 0 or out.rate_kmol_h <= 0:
            raise ConfigError("feed pressure and rate must be positive")
        return out


@dataclass
class Variable:
    """One proxy operating variable: initial value, bounds and whether it is fixed."""

    value: float
    lower: Optional[float] = None
    upper: Optional[float] = None
    fixed: bool = False

    @classmethod
    def from_any(cls, d: Any, where: str) -> "Variable":
        """Accept a number (fixed value) or a mapping with value/lower/upper/fixed."""
        if isinstance(d, (int, float)):
            return cls(value=float(d), fixed=True)
        d = _take(d, ["value", "lower", "upper", "fixed"], where)
        if "value" not in d:
            raise ConfigError("%s needs 'value'" % where)
        var = cls(value=float(d["value"]),
                  lower=None if d.get("lower") is None else float(d["lower"]),
                  upper=None if d.get("upper") is None else float(d["upper"]),
                  fixed=bool(d.get("fixed", False)))
        if not var.fixed:
            if var.lower is None or var.upper is None:
                raise ConfigError("%s is free and needs 'lower' and 'upper' bounds" % where)
            if not var.lower < var.upper:
                raise ConfigError("%s: lower must be below upper" % where)
            if not var.lower <= var.value <= var.upper:
                raise ConfigError("%s: value %.6g outside [%.6g, %.6g]" % (where, var.value, var.lower, var.upper))
        return var


@dataclass
class StageSpec:
    """A proxy separator stage (equilibrium flash at P and T)."""

    name: str
    pressure: Variable
    temperature: Variable

    @classmethod
    def from_dict(cls, d: Dict[str, Any], where: str) -> "StageSpec":
        d = _take(d, ["name", "pressure_bara", "temperature_C"], where)
        for key in ("name", "pressure_bara", "temperature_C"):
            if key not in d:
                raise ConfigError("%s needs '%s'" % (where, key))
        return cls(name=str(d["name"]),
                   pressure=Variable.from_any(d["pressure_bara"], where + ".pressure_bara"),
                   temperature=Variable.from_any(d["temperature_C"], where + ".temperature_C"))


@dataclass
class ScrubberSpec:
    """Gas scrubber of the forward proxy.

    Gas from ``gas_sources`` is brought to the scrubber P and T; the scrubber gas is gas product
    and the scrubber liquid goes strictly forward to ``liquid_destination`` (a later stage or the
    stock tank). A destination at or before a source stage would be a recycle and is rejected.
    """

    name: str
    pressure: Variable
    temperature: Variable
    gas_sources: List[str]
    liquid_destination: str = STOCK_TANK

    @classmethod
    def from_dict(cls, d: Dict[str, Any], stage_names: List[str]) -> "ScrubberSpec":
        d = _take(d, ["name", "pressure_bara", "temperature_C", "gas_sources", "liquid_destination"], "proxy.scrubber")
        for key in ("pressure_bara", "temperature_C"):
            if key not in d:
                raise ConfigError("proxy.scrubber needs '%s'" % key)
        sources = [str(s) for s in d.get("gas_sources", stage_names)]
        dest = str(d.get("liquid_destination", STOCK_TANK))
        scr = cls(name=str(d.get("name", "scrubber")),
                  pressure=Variable.from_any(d["pressure_bara"], "proxy.scrubber.pressure_bara"),
                  temperature=Variable.from_any(d["temperature_C"], "proxy.scrubber.temperature_C"),
                  gas_sources=sources, liquid_destination=dest)
        scr.check_forward(stage_names)
        return scr

    def check_forward(self, stage_names: List[str]) -> None:
        """Reject unknown names and any routing that would feed liquid backwards."""
        order = {n: i for i, n in enumerate(stage_names)}
        order[STOCK_TANK] = len(stage_names)
        if not self.gas_sources:
            raise ConfigError("proxy.scrubber.gas_sources is empty")
        for s in self.gas_sources:
            if s not in order or s == STOCK_TANK:
                raise ConfigError("proxy.scrubber gas source %r is not a proxy stage" % s)
        if self.liquid_destination not in order:
            raise ConfigError("proxy.scrubber liquid_destination %r is not a stage or %r"
                              % (self.liquid_destination, STOCK_TANK))
        if order[self.liquid_destination] <= max(order[s] for s in self.gas_sources):
            raise ConfigError("proxy.scrubber liquid_destination %r is not downstream of every gas source; "
                              "that would be a recycle, which the forward proxy forbids" % self.liquid_destination)


@dataclass
class ProxyTopology:
    """Strictly forward proxy: N separator stages, optional scrubber, stock tank at standard conditions."""

    name: str
    stages: List[StageSpec]
    scrubber: Optional[ScrubberSpec] = None
    min_pressure_step_bar: float = 0.5

    @property
    def stage_names(self) -> List[str]:
        """Stage names in flow order (stock tank excluded)."""
        return [s.name for s in self.stages]

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ProxyTopology":
        d = _take(d, ["name", "stages", "scrubber", "min_pressure_step_bar"], "proxy")
        stages = [StageSpec.from_dict(s, "proxy.stages[%d]" % i) for i, s in enumerate(d.get("stages") or [])]
        if not stages:
            raise ConfigError("proxy.stages needs at least one stage")
        names = [s.name for s in stages]
        if len(set(names)) != len(names) or STOCK_TANK in names:
            raise ConfigError("proxy stage names must be unique and must not be %r" % STOCK_TANK)
        scr = ScrubberSpec.from_dict(d["scrubber"], names) if d.get("scrubber") else None
        if scr is not None and scr.name in names:
            raise ConfigError("proxy scrubber name must differ from the stage names")
        return cls(name=str(d.get("name", "forward_pvt_proxy")), stages=stages, scrubber=scr,
                   min_pressure_step_bar=float(d.get("min_pressure_step_bar", 0.5)))


@dataclass
class RecompressionSpec:
    """One gas-handling step of the reference process: (compressor), cooler, scrubber.

    ``gas_sources`` lists separator stages and/or earlier steps whose gas enters the step. The gas is
    brought to ``scrubber_pressure_bara`` (alias ``discharge_pressure_bara``) and
    ``cooler_temperature_C`` and scrubbed. A compressor is inserted only when that pressure is above
    every source pressure; otherwise the cooler takes the pressure drop (suction cooler and scrubber).
    The scrubber liquid returns to ``liquid_return_stage``; returning it to the stage the gas came from
    creates the liquid recycle the proxy has to approximate.

    Optional ``gas_return`` (``{"to_stage": name, "rate_Sm3_h": q}``) splits a fixed standard gas rate
    off the scrubber gas and returns it to the inlet of ``to_stage`` (gas lift returning through the
    wells, or a gas recycle). The returned gas is not a product.
    """

    name: str
    gas_sources: List[str]
    discharge_pressure_bara: float
    cooler_temperature_C: float
    liquid_return_stage: str
    gas_return_stage: Optional[str] = None
    gas_return_Sm3_h: float = 0.0

    @property
    def scrubber_pressure_bara(self) -> float:
        """Scrubber pressure (same value as ``discharge_pressure_bara``)."""
        return self.discharge_pressure_bara

    @classmethod
    def from_dict(cls, d: Dict[str, Any], where: str) -> "RecompressionSpec":
        d = _take(d, ["name", "gas_sources", "discharge_pressure_bara", "scrubber_pressure_bara",
                      "cooler_temperature_C", "liquid_return_stage", "gas_return"], where)
        if "scrubber_pressure_bara" in d and "discharge_pressure_bara" in d:
            raise ConfigError("%s: give scrubber_pressure_bara or discharge_pressure_bara, not both" % where)
        p = d.get("scrubber_pressure_bara", d.get("discharge_pressure_bara"))
        for key, val in (("name", d.get("name")), ("gas_sources", d.get("gas_sources")),
                         ("scrubber_pressure_bara", p), ("cooler_temperature_C", d.get("cooler_temperature_C")),
                         ("liquid_return_stage", d.get("liquid_return_stage"))):
            if val is None:
                raise ConfigError("%s needs '%s'" % (where, key))
        ret_stage, ret_rate = None, 0.0
        if d.get("gas_return"):
            gr = _take(d["gas_return"], ["to_stage", "rate_Sm3_h"], where + ".gas_return")
            if "to_stage" not in gr or "rate_Sm3_h" not in gr:
                raise ConfigError("%s.gas_return needs to_stage and rate_Sm3_h" % where)
            ret_stage, ret_rate = str(gr["to_stage"]), float(gr["rate_Sm3_h"])
            if ret_rate <= 0:
                raise ConfigError("%s.gas_return.rate_Sm3_h must be positive" % where)
        return cls(name=str(d["name"]), gas_sources=[str(s) for s in d["gas_sources"]],
                   discharge_pressure_bara=float(p),
                   cooler_temperature_C=float(d["cooler_temperature_C"]),
                   liquid_return_stage=str(d["liquid_return_stage"]),
                   gas_return_stage=ret_stage, gas_return_Sm3_h=ret_rate)


@dataclass
class ReferenceSpec:
    """The detailed (ground-truth) process.

    ``type``:
      * ``recompression`` - separator train plus recompression/cooling/scrubbers with liquid
        returned upstream (recycles closed with ``ProcessSystem.makeRecycles``).
      * ``forward`` - a forward train with fixed P/T (used to prove parameter recovery).
      * ``python`` - ``callable: "module:function"``; the function receives
        ``(fluid, feed, standard_conditions, jclass)`` and returns a dict with ``oil_Sm3_h`` and
        ``gas_Sm3_h`` (optionally ``oil_kg_h``/``gas_kg_h``). Use it to plug an existing NeqSim model.
    """

    type: str
    id: str
    stages: List[Dict[str, Any]] = field(default_factory=list)
    recompression: List[RecompressionSpec] = field(default_factory=list)
    scrubber: Optional[Dict[str, Any]] = None
    callable: Optional[str] = None
    max_mass_balance_error: float = 1.0e-4
    recycle_tolerance: float = 1.0e-6

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ReferenceSpec":
        d = _take(d, ["type", "id", "stages", "recompression", "scrubber", "callable", "max_mass_balance_error",
                      "recycle_tolerance"], "reference")
        typ = str(d.get("type", "recompression"))
        if typ not in ("recompression", "forward", "python"):
            raise ConfigError("reference.type must be recompression, forward or python")
        ref = cls(type=typ, id=str(d.get("id", typ + "_reference")), stages=list(d.get("stages") or []),
                  recompression=[RecompressionSpec.from_dict(r, "reference.recompression[%d]" % i)
                                 for i, r in enumerate(d.get("recompression") or [])],
                  scrubber=d.get("scrubber"), callable=d.get("callable"),
                  max_mass_balance_error=float(d.get("max_mass_balance_error", 1.0e-4)),
                  recycle_tolerance=float(d.get("recycle_tolerance", 1.0e-6)))
        if ref.recycle_tolerance <= 0:
            raise ConfigError("reference.recycle_tolerance must be positive")
        if typ == "python":
            if not ref.callable or ":" not in ref.callable:
                raise ConfigError("reference.type python needs callable: 'module:function'")
            return ref
        if not ref.stages:
            raise ConfigError("reference.stages is required for type %s" % typ)
        for i, s in enumerate(ref.stages):
            _take(s, ["name", "pressure_bara", "temperature_C"], "reference.stages[%d]" % i)
            if not all(k in s for k in ("name", "pressure_bara", "temperature_C")):
                raise ConfigError("reference.stages[%d] needs name, pressure_bara, temperature_C" % i)
        names = [str(s["name"]) for s in ref.stages]
        pressures = [float(s["pressure_bara"]) for s in ref.stages]
        if any(a <= b for a, b in zip(pressures, pressures[1:])):
            raise ConfigError("reference stage pressures must decrease along the train")
        known = set(names)
        for r in ref.recompression:
            for src in r.gas_sources:
                if src not in known:
                    raise ConfigError("recompression %s: unknown or later gas source %r" % (r.name, src))
            if r.liquid_return_stage not in names:
                raise ConfigError("recompression %s: liquid_return_stage %r is not a stage" % (r.name, r.liquid_return_stage))
            if r.gas_return_stage is not None and r.gas_return_stage not in names:
                raise ConfigError("recompression %s: gas_return.to_stage %r is not a stage" % (r.name, r.gas_return_stage))
            known.add(r.name)
        if typ == "forward":
            if ref.recompression:
                raise ConfigError("reference.type forward cannot have recompression (that would be a recycle)")
            ref.as_proxy_topology()
        return ref

    def as_proxy_topology(self) -> ProxyTopology:
        """Interpret a ``forward`` reference as a fixed proxy topology."""
        scr = None
        if self.scrubber:
            scr = dict(self.scrubber)
            scr["pressure_bara"] = float(scr["pressure_bara"])
            scr["temperature_C"] = float(scr["temperature_C"])
        return ProxyTopology.from_dict({
            "name": self.id, "min_pressure_step_bar": 0.0,
            "stages": [{"name": s["name"], "pressure_bara": float(s["pressure_bara"]),
                        "temperature_C": float(s["temperature_C"])} for s in self.stages],
            "scrubber": scr})


@dataclass
class ObjectiveConfig:
    """Weighted normalized least squares on standard oil and gas rates (GOR optional, default 0).

    ``eps_*_fraction`` set the absolute normalization floor as a fraction of the median reference
    value, so a fluid with almost no oil cannot produce an unbounded relative oil error.
    ``dry_gas_gor_threshold`` and ``dry_gas_weight`` down-weight super-dry fluids, and ``loss``
    (``squared``, ``soft_l1`` or ``cap`` with ``residual_scale``) limits the pull of any one fluid.
    """

    oil_weight: float = 2.0
    gas_weight: float = 1.0
    gor_weight: float = 0.0
    eps_oil_fraction: float = 0.05
    eps_gas_fraction: float = 0.0
    eps_gor_fraction: float = 0.0
    loss: str = "soft_l1"
    residual_scale: float = 0.2
    dry_gas_gor_threshold: float = 15000.0
    dry_gas_weight: float = 0.25
    failed_penalty: float = 10.0
    dominance_share: float = 0.3
    prior_weight: float = 0.0

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ObjectiveConfig":
        d = _take(d, list(cls.__dataclass_fields__), "objective")
        out = cls(**{k: (str(v) if k == "loss" else float(v)) for k, v in d.items()})
        if out.loss not in ("squared", "soft_l1", "cap"):
            raise ConfigError("objective.loss must be squared, soft_l1 or cap")
        weights = (out.oil_weight, out.gas_weight, out.gor_weight)
        if min(weights) < 0 or sum(weights) <= 0:
            raise ConfigError("objective weights must be non-negative with a positive sum")
        if out.residual_scale <= 0:
            raise ConfigError("objective.residual_scale must be positive")
        if out.prior_weight < 0:
            raise ConfigError("objective.prior_weight must be non-negative")
        return out


_OPT_INT = ("seed", "global_maxiter", "popsize", "local_maxiter", "n_starts", "workers", "checkpoint_every",
            "cache_decimals")
OPTIMIZER_METHODS = ("de+lsq", "de+powell", "de", "lsq", "powell", "nelder-mead", "multistart-lsq",
                     "multistart-powell")


@dataclass
class OptimizerConfig:
    """Optimizer settings.

    ``method``: ``de+lsq`` (default: differential evolution, then bounded least-squares refinement),
    ``de+powell``, ``de``, ``lsq``, ``powell``, ``nelder-mead``, ``multistart-lsq`` or
    ``multistart-powell``.
    """

    method: str = "de+lsq"
    seed: int = 1
    global_maxiter: int = 15
    popsize: int = 6
    local_maxiter: int = 400
    n_starts: int = 3
    workers: int = 1
    xtol: float = 1.0e-3
    ftol: float = 1.0e-6
    fd_step: float = 1.0e-3
    checkpoint_every: int = 25
    cache_decimals: int = 6

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "OptimizerConfig":
        d = _take(d, list(cls.__dataclass_fields__), "optimizer")
        kw = {}
        for k, v in d.items():
            kw[k] = str(v) if k == "method" else (int(v) if k in _OPT_INT else float(v))
        out = cls(**kw)
        if out.method not in OPTIMIZER_METHODS:
            raise ConfigError("optimizer.method must be one of %s" % ", ".join(OPTIMIZER_METHODS))
        if out.workers < 1:
            raise ConfigError("optimizer.workers must be at least 1")
        if not 1.0e-6 <= out.fd_step <= 0.05:
            raise ConfigError("optimizer.fd_step must be in [1e-6, 0.05] (unit box)")
        return out


@dataclass
class AcceptanceConfig:
    """Acceptance criteria; a fitted proxy that misses any of them is marked ``review_required``."""

    oil_mape_pct: float = 3.0
    gas_mape_pct: float = 3.0
    oil_p90_abs_pct: float = 6.0
    max_failed: int = 0
    allow_dominance: bool = False

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "AcceptanceConfig":
        d = _take(d, list(cls.__dataclass_fields__), "acceptance")
        kw = {}
        for k, v in d.items():
            kw[k] = int(v) if k == "max_failed" else (bool(v) if k == "allow_dominance" else float(v))
        return cls(**kw)


@dataclass
class ProjectConfig:
    """Complete calibration configuration."""

    name: str
    standard_conditions: StandardConditions
    feed: FeedConditions
    fluids: Dict[str, Any]
    reference: ReferenceSpec
    proxy: ProxyTopology
    objective: ObjectiveConfig
    optimizer: OptimizerConfig
    exports: List[str]
    raw: Dict[str, Any]
    base_dir: Path
    acceptance: AcceptanceConfig = field(default_factory=AcceptanceConfig)

    def config_hash(self) -> str:
        """Stable hash of the settings that define a calibration (checkpoint/caching key)."""
        text = json.dumps(self.raw, sort_keys=True, default=str)
        return hashlib.sha256(text.encode("utf-8")).hexdigest()[:16]

    def resolve(self, path: str) -> Path:
        """Resolve a path relative to the configuration file."""
        p = Path(path)
        return p if p.is_absolute() else (self.base_dir / p)


def parse_config(data: Dict[str, Any], base_dir: Optional[Path] = None) -> ProjectConfig:
    """Validate a configuration mapping and return a :class:`ProjectConfig`."""
    raw = copy.deepcopy(data)
    data = _take(data, ["name", "standard_conditions", "feed", "fluids", "reference", "proxy", "objective",
                        "optimizer", "acceptance", "exports"], "configuration")
    for key in ("fluids", "reference", "proxy"):
        if key not in data:
            raise ConfigError("configuration needs '%s'" % key)
    from .export import EXPORTERS
    exports = [str(e) for e in (data.get("exports") or ["neqsim", "e300", "pvtsim", "blackoil"])]
    unknown = [e for e in exports if e not in EXPORTERS]
    if unknown:
        raise ConfigError("unknown export target(s): %s (known: %s)" % (", ".join(unknown), ", ".join(sorted(EXPORTERS))))
    return ProjectConfig(
        name=str(data.get("name", "pvt_forward_proxy")),
        standard_conditions=StandardConditions.from_dict(data.get("standard_conditions") or {}),
        feed=FeedConditions.from_dict(data.get("feed") or {}),
        fluids=dict(data["fluids"]),
        reference=ReferenceSpec.from_dict(data["reference"]),
        proxy=ProxyTopology.from_dict(data["proxy"]),
        objective=ObjectiveConfig.from_dict(data.get("objective") or {}),
        optimizer=OptimizerConfig.from_dict(data.get("optimizer") or {}),
        exports=exports, raw=raw, base_dir=Path(base_dir or ".").resolve(),
        acceptance=AcceptanceConfig.from_dict(data.get("acceptance") or {}))


def load_config(path: str) -> ProjectConfig:
    """Load a YAML or JSON configuration file."""
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if p.suffix.lower() == ".json":
        data = json.loads(text)
    else:
        import yaml
        data = yaml.safe_load(text)
    return parse_config(data, base_dir=p.parent)
