"""Fluid ensemble: one shared component template and many compositions.

Every fluid in a calibration uses the same :class:`FluidTemplate` (EOS, component list, plus
fraction properties). Only the mole-fraction vector changes between fluids, so the component
mapping is stable between all reference and proxy simulations.

Generation methods (``fluids.methods`` in the configuration):

* ``explicit``  - named base compositions (method A, characterized fluids).
* ``mixing``    - molar mixtures of two end members, e.g. oil zone and gas zone (method B).
* ``depletion`` - constant-volume depletion of a base fluid; produced gas per step, optionally
  recombined with the remaining reservoir liquid (method C, needs the NeqSim engine).
* ``variation`` - Latin-hypercube perturbation of a base composition (method D).
"""
from __future__ import annotations

import hashlib
import json
import math
from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional, Sequence

from .config import ConfigError, _take

HEAVY_MW_KG_MOL = 0.095  # components at or above this molar mass count as C7+ ("heavy")


@dataclass
class ComponentDef:
    """One component: a NeqSim library name or a TBP pseudo component with MW and density."""

    name: str
    mw_kg_mol: Optional[float] = None
    density_g_cm3: Optional[float] = None

    @property
    def is_pseudo(self) -> bool:
        """True for a TBP pseudo component."""
        return self.mw_kg_mol is not None


@dataclass
class FluidTemplate:
    """Shared EOS and component definition for every fluid in the ensemble.

    Exactly one of ``components`` (library names and TBP pseudo components), ``e300`` (Eclipse 300
    fluid file) or ``factory`` (``"module:function"`` returning a characterized NeqSim fluid, e.g. a
    tuned project EOS with custom component properties and kij) defines the template. Component
    names of an ``e300`` or ``factory`` template are read from the fluid.
    """

    eos: str = "srk"
    components: List[ComponentDef] = field(default_factory=list)
    e300: Optional[str] = None
    mixing_rule: int = 2
    factory: Optional[str] = None

    @property
    def names(self) -> List[str]:
        """Component names in order (for E300/factory templates filled in by the engine)."""
        return [c.name for c in self.components]

    def fingerprint(self) -> Dict[str, Any]:
        """JSON-ready identity of the template."""
        return {"eos": self.eos, "e300": self.e300, "factory": self.factory, "mixing_rule": self.mixing_rule,
                "components": [asdict(c) for c in self.components]}

    @classmethod
    def from_dict(cls, d: Dict[str, Any], resolve=None) -> "FluidTemplate":
        d = _take(d, ["eos", "components", "e300", "mixing_rule", "factory"], "fluids.template")
        eos = str(d.get("eos", "srk")).lower()
        if eos not in ("srk", "pr"):
            raise ConfigError("fluids.template.eos must be srk or pr")
        comps = []
        for i, c in enumerate(d.get("components") or []):
            if isinstance(c, str):
                comps.append(ComponentDef(c))
            else:
                c = _take(c, ["name", "mw_kg_mol", "density_g_cm3"], "fluids.template.components[%d]" % i)
                if "mw_kg_mol" in c and "density_g_cm3" not in c:
                    raise ConfigError("pseudo component %s needs density_g_cm3" % c.get("name"))
                comps.append(ComponentDef(str(c["name"]), c.get("mw_kg_mol") and float(c["mw_kg_mol"]),
                                          c.get("density_g_cm3") and float(c["density_g_cm3"])))
        e300 = d.get("e300")
        factory = d.get("factory")
        if sum(bool(x) for x in (comps, e300, factory)) != 1:
            raise ConfigError("fluids.template needs exactly one of components, e300 or factory")
        if factory and ":" not in str(factory):
            raise ConfigError("fluids.template.factory must be 'module:function'")
        if e300 and resolve is not None:
            e300 = str(resolve(e300))
        names = [c.name for c in comps]
        if len(set(names)) != len(names):
            raise ConfigError("fluids.template component names must be unique")
        if any(n.lower() == "water" for n in names):
            raise ConfigError("water is outside the scope of the PVT proxy; give hydrocarbon (+N2/CO2/H2S) fluids")
        return cls(eos=eos, components=comps, e300=e300, mixing_rule=int(d.get("mixing_rule", 2)),
                   factory=str(factory) if factory else None)


@dataclass
class FluidCase:
    """One fluid of the ensemble."""

    id: str
    z: List[float]
    method: str
    provenance: Dict[str, Any] = field(default_factory=dict)
    weight: float = 1.0

    def as_dict(self, names: Sequence[str]) -> Dict[str, Any]:
        """JSON-ready record with a named composition."""
        return {"id": self.id, "method": self.method, "weight": self.weight, "provenance": self.provenance,
                "composition": {n: z for n, z in zip(names, self.z)}}


def normalize(z: Sequence[float], where: str, tol: Optional[float] = None) -> List[float]:
    """Validate a mole-fraction vector and normalize it to sum 1.

    ``tol`` rejects vectors whose sum differs from 1 by more than ``tol`` (explicit input);
    ``None`` accepts any positive sum (generated compositions).
    """
    z = [float(v) for v in z]
    if any((not math.isfinite(v)) or v < 0 for v in z):
        raise ConfigError("%s: mole fractions must be finite and non-negative" % where)
    total = sum(z)
    if total <= 0:
        raise ConfigError("%s: mole fractions sum to zero" % where)
    if tol is not None and abs(total - 1.0) > tol:
        raise ConfigError("%s: mole fractions sum to %.6f, expected 1 within %.1e" % (where, total, tol))
    return [v / total for v in z]


def map_composition(comp: Any, names: Sequence[str], where: str) -> List[float]:
    """Map a named composition (dict) or a list in template order onto the template (missing names = 0)."""
    if isinstance(comp, (list, tuple)):
        if len(comp) != len(names):
            raise ConfigError("%s: %d mole fractions for a %d-component template" % (where, len(comp), len(names)))
        return normalize(comp, where, tol=1e-3)
    unknown = sorted(set(comp) - set(names))
    if unknown:
        raise ConfigError("%s: component(s) not in the template: %s" % (where, ", ".join(unknown)))
    return normalize([float(comp.get(n, 0.0)) for n in names], where, tol=1e-3)


def latin_hypercube(n: int, dims: int, seed: int) -> List[List[float]]:
    """Latin-hypercube samples in [0, 1)^dims with a fixed seed (reproducible)."""
    import numpy as np
    rng = np.random.default_rng(seed)
    out = np.empty((n, dims))
    for d in range(dims):
        out[:, d] = (rng.permutation(n) + rng.random(n)) / n
    return out.tolist()


def mix(z_a: Sequence[float], z_b: Sequence[float], frac_b: float) -> List[float]:
    """Molar mixture: (1 - frac_b) mol of A plus frac_b mol of B."""
    if not 0.0 <= frac_b <= 1.0:
        raise ConfigError("mixing fraction %.4g outside [0, 1]" % frac_b)
    return normalize([(1.0 - frac_b) * a + frac_b * b for a, b in zip(z_a, z_b)], "mixture")


def vary(z: Sequence[float], names: Sequence[str], n: int, rel_range: Any, seed: int) -> List[List[float]]:
    """Perturb each non-zero component by a factor 1 + r*u, u in [-1, 1] (Latin hypercube)."""
    if isinstance(rel_range, dict):
        default = float(rel_range.get("default", 0.2))
        ranges = [float(rel_range.get(nm, default)) for nm in names]
    else:
        ranges = [float(rel_range)] * len(names)
    if any(r < 0 or r >= 1 for r in ranges):
        raise ConfigError("variation relative_range must be in [0, 1)")
    samples = latin_hypercube(n, len(names), seed)
    out = []
    for row in samples:
        out.append(normalize([zi * (1.0 + r * (2.0 * u - 1.0)) for zi, r, u in zip(z, ranges, row)], "variation"))
    return out


def generate_ensemble(spec: Dict[str, Any], template: FluidTemplate, engine=None,
                      e300_base: Optional[List[float]] = None) -> Dict[str, Any]:
    """Build the fluid ensemble from ``fluids`` configuration.

    Returns ``{"id", "template", "cases", "rejected"}``. Rejected candidates are kept with a
    reason (never dropped silently).
    """
    spec = _take(spec, ["id", "template", "base_fluids", "methods", "weights"], "fluids")
    names = template.names
    bases: Dict[str, List[float]] = {}
    if e300_base is not None:
        bases["e300"] = normalize(e300_base, "e300 base composition")
    for bname, comp in (spec.get("base_fluids") or {}).items():
        bases[str(bname)] = map_composition(comp, names, "fluids.base_fluids.%s" % bname)
    cases: List[FluidCase] = []
    rejected: List[Dict[str, Any]] = []

    def base(name: str, where: str) -> List[float]:
        if name not in bases:
            raise ConfigError("%s: unknown base fluid %r (known: %s)" % (where, name, ", ".join(sorted(bases))))
        return bases[name]

    for mi, m in enumerate(spec.get("methods") or [{"type": "explicit"}]):
        where = "fluids.methods[%d]" % mi
        typ = str(m.get("type", ""))
        if typ == "explicit":
            _take(m, ["type", "fluids"], where)
            for bname in m.get("fluids") or sorted(bases):
                cases.append(FluidCase("%s" % bname, list(base(bname, where)), "explicit", {"base": bname}))
        elif typ == "mixing":
            _take(m, ["type", "endmembers", "gas_fractions", "n", "label"], where)
            ends = m.get("endmembers") or []
            if len(ends) != 2:
                raise ConfigError("%s: mixing needs two endmembers [oil, gas]" % where)
            fracs = m.get("gas_fractions")
            if fracs is None:
                n = int(m.get("n", 5))
                fracs = [(i + 1) / (n + 1) for i in range(n)]
            za, zb = base(ends[0], where), base(ends[1], where)
            label = m.get("label", "mix")
            for f in fracs:
                cases.append(FluidCase("%s_%s_%s_%.3f" % (label, ends[0], ends[1], float(f)), mix(za, zb, float(f)),
                                       "mixing", {"endmembers": list(ends), "fraction_of_second": float(f)}))
        elif typ == "variation":
            _take(m, ["type", "base", "n", "relative_range", "seed"], where)
            b = str(m.get("base"))
            seed = int(m.get("seed", 1))
            for i, z in enumerate(vary(base(b, where), names, int(m.get("n", 10)), m.get("relative_range", 0.2), seed)):
                cases.append(FluidCase("var_%s_%03d" % (b, i), z, "variation",
                                       {"base": b, "seed": seed, "index": i, "relative_range": m.get("relative_range", 0.2)}))
        elif typ == "depletion":
            _take(m, ["type", "base", "reservoir_temperature_C", "pressures_bara", "liquid_recombination"], where)
            if engine is None:
                raise ConfigError("%s: depletion needs the NeqSim engine" % where)
            b = str(m.get("base"))
            temp = float(m["reservoir_temperature_C"])
            pressures = [float(p) for p in m["pressures_bara"]]
            if any(a <= c for a, c in zip(pressures, pressures[1:])):
                raise ConfigError("%s: depletion pressures must decrease" % where)
            steps = engine.cvd(template, base(b, where), temp, pressures)
            for st in steps:
                if st.get("error"):
                    rejected.append({"id": "cvd_%s_%.1f" % (b, st["pressure_bara"]), "reason": st["error"]})
                    continue
                if not st.get("gas_z"):
                    rejected.append({"id": "cvd_%s_%.1f" % (b, st["pressure_bara"]),
                                     "reason": "single liquid phase at this pressure; no produced gas"})
                    continue
                prov = {"base": b, "reservoir_temperature_C": temp, "pressure_bara": st["pressure_bara"],
                        "cumulative_gas_produced_frac": st["cumulative_gas_produced_frac"]}
                if st.get("gas_z"):
                    cases.append(FluidCase("cvd_%s_gas_%.1f" % (b, st["pressure_bara"]), normalize(st["gas_z"], "cvd gas"),
                                           "depletion", dict(prov, stream="produced_gas")))
                for frac in m.get("liquid_recombination") or []:
                    if st.get("gas_z") and st.get("liquid_z"):
                        cases.append(FluidCase("cvd_%s_rec_%.1f_%.3f" % (b, st["pressure_bara"], float(frac)),
                                               mix(st["gas_z"], st["liquid_z"], float(frac)), "depletion",
                                               dict(prov, stream="gas_plus_reservoir_liquid", liquid_fraction=float(frac))))
        else:
            raise ConfigError("%s: unknown method type %r (explicit, mixing, variation, depletion)" % (where, typ))

    seen = set()
    for c in cases:
        if c.id in seen:
            raise ConfigError("duplicate fluid id %r; give distinct base names or labels" % c.id)
        seen.add(c.id)
    for fid, w in (spec.get("weights") or {}).items():
        match = [c for c in cases if c.id == fid]
        if not match:
            raise ConfigError("fluids.weights: unknown fluid id %r" % fid)
        if float(w) < 0:
            raise ConfigError("fluids.weights must be non-negative")
        match[0].weight = float(w)
    ens_id = spec.get("id") or "ensemble_" + hashlib.sha256(json.dumps(
        [template.fingerprint(), [c.z for c in cases]], sort_keys=True).encode()).hexdigest()[:10]
    return {"id": str(ens_id), "template": template, "cases": cases, "rejected": rejected}


def descriptors(z: Sequence[float], names: Sequence[str], molar_masses: Sequence[float]) -> Dict[str, float]:
    """Composition descriptors used for the calibration range and extrapolation checks."""
    mw = sum(zi * m for zi, m in zip(z, molar_masses))
    heavy = sum(zi for zi, m in zip(z, molar_masses) if m >= HEAVY_MW_KG_MOL)
    c1 = sum(zi for zi, n in zip(z, names) if n.lower() in ("methane", "c1"))
    return {"feed_molar_mass_kg_kmol": mw * 1000.0, "c1_mole_fraction": c1, "c7plus_mole_fraction": heavy}


def classify(gor_sm3_sm3: Optional[float]) -> str:
    """Fluid class from the reference GOR (Sm3/Sm3)."""
    if gor_sm3_sm3 is None or math.isnan(gor_sm3_sm3):
        return "unclassified"
    if gor_sm3_sm3 < 200.0:
        return "black_oil"
    if gor_sm3_sm3 < 600.0:
        return "volatile_oil"
    if gor_sm3_sm3 < 15000.0:
        return "gas_condensate"
    return "dry_gas"


def save_ensemble(path, ensemble: Dict[str, Any]) -> None:
    """Write the ensemble with provenance to JSON."""
    t = ensemble["template"]
    data = {"id": ensemble["id"], "template": t.fingerprint(),
            "cases": [c.as_dict(t.names) for c in ensemble["cases"]], "rejected": ensemble["rejected"]}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def load_ensemble(path) -> Dict[str, Any]:
    """Read an ensemble written by :func:`save_ensemble`."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    t = data["template"]
    template = FluidTemplate(eos=t["eos"], e300=t.get("e300"), mixing_rule=int(t.get("mixing_rule", 2)),
                             factory=t.get("factory"), components=[ComponentDef(**c) for c in t["components"]])
    cases = [FluidCase(c["id"], [float(c["composition"][n]) for n in template.names], c["method"],
                       c.get("provenance", {}), float(c.get("weight", 1.0))) for c in data["cases"]]
    return {"id": data["id"], "template": template, "cases": cases, "rejected": data.get("rejected", [])}
