"""Reference runs and calibration of one common proxy parameter set over the fluid ensemble."""
from __future__ import annotations

import hashlib
import json
import math
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from . import objective as obj
from .config import ProjectConfig, ProxyTopology
from .engine import Engine, ForwardTrain, failed
from .optimize import ParameterSpace, invalid_penalty, make_optimizer


def _key(*parts: Any) -> str:
    return hashlib.sha256(json.dumps(parts, sort_keys=True, default=str).encode("utf-8")).hexdigest()


def run_references(cfg: ProjectConfig, engine: Engine, ensemble: Dict[str, Any],
                   cache_path: Optional[Path] = None, log=print) -> Dict[str, Dict[str, Any]]:
    """Run every fluid through the reference process (cached by configuration and composition)."""
    template = ensemble["template"]
    cache: Dict[str, Any] = {}
    if cache_path is not None and Path(cache_path).is_file():
        cache = json.loads(Path(cache_path).read_text(encoding="utf-8"))
    basis = [cfg.raw.get("reference"), cfg.raw.get("feed"), cfg.raw.get("standard_conditions"),
             template.fingerprint()]
    out: Dict[str, Dict[str, Any]] = {}
    for i, case in enumerate(ensemble["cases"]):
        k = _key(basis, case.z)
        if k in cache:
            out[case.id] = cache[k]
            continue
        t0 = time.time()
        rec = engine.run_reference(cfg.reference, template, case.z)
        rec["seconds"] = round(time.time() - t0, 3)
        out[case.id] = rec
        cache[k] = rec
        log("reference %3d/%d %-32s %s" % (i + 1, len(ensemble["cases"]), case.id,
                                             rec["status"] if rec["status"] != "ok" else
                                             "oil %.4g Sm3/h gas %.5g Sm3/h GOR %.4g" % (
                                                 rec["oil_Sm3_h"], rec["gas_Sm3_h"], rec["gor_Sm3_Sm3"])))
    if cache_path is not None:
        Path(cache_path).write_text(json.dumps(cache, indent=1, default=_json_default), encoding="utf-8")
    return out


def _json_default(o):
    if isinstance(o, float) and not math.isfinite(o):
        return None
    return str(o)


class ProxyEvaluator:
    """Runs the forward proxy for every fluid at one parameter set (flowsheets built once)."""

    def __init__(self, engine: Engine, template, cases, topology: ProxyTopology, workers: int = 1):
        self.engine, self.template, self.cases, self.topology = engine, template, list(cases), topology
        self.workers = workers
        self._trains: Dict[str, Any] = {}

    def _train(self, case):
        if case.id not in self._trains:
            try:
                self._trains[case.id] = ForwardTrain(self.engine, self.template, case.z, self.topology)
            except Exception as exc:  # noqa: BLE001
                self._trains[case.id] = exc
        return self._trains[case.id]

    def _one(self, case, params):
        train = self._train(case)
        if isinstance(train, Exception):
            return case.id, failed("proxy build failed: %s" % train)
        return case.id, train.run(params)

    def evaluate(self, params: Dict[str, Tuple[float, float]]) -> Dict[str, Dict[str, Any]]:
        """Product records per fluid id."""
        for c in self.cases:
            self._train(c)
        if self.workers > 1:
            with ThreadPoolExecutor(self.workers) as pool:
                return dict(pool.map(lambda c: self._one(c, params), self.cases))
        return dict(self._one(c, params) for c in self.cases)


@dataclass
class CalibrationResult:
    """Outcome of one calibration."""

    params: Dict[str, Tuple[float, float]]
    free_values: Dict[str, float]
    objective: float
    breakdown: obj.Breakdown
    proxies: Dict[str, Dict[str, Any]]
    weights: Dict[str, float]
    norm: obj.Normalization
    dominance: Dict[str, Any]
    optimizer: Dict[str, Any]
    history: List[Dict[str, float]] = field(default_factory=list)
    n_evaluations: int = 0
    n_rejected: int = 0
    seconds: float = 0.0
    initial_objective: float = math.nan
    data_objective: float = math.nan
    identifiability: Dict[str, Any] = field(default_factory=dict)


class Calibrator:
    """Fits one set of proxy stage pressures and temperatures to all reference fluids."""

    def __init__(self, cfg: ProjectConfig, engine: Optional[Engine], ensemble: Dict[str, Any],
                 refs: Dict[str, Dict[str, Any]], out_dir: Optional[Path] = None, log=print, evaluator=None):
        self.cfg, self.engine, self.ensemble, self.refs, self.log = cfg, engine, ensemble, refs, log
        self.space = ParameterSpace(cfg.proxy, cfg.standard_conditions)
        self.weights = obj.effective_weights(ensemble["cases"], refs, cfg.objective)
        fit_cases = [c for c in ensemble["cases"] if self.weights.get(c.id, 0.0) > 0]
        if not fit_cases:
            raise RuntimeError("no fluid has a successful reference result and positive weight; nothing to fit")
        self.norm = obj.normalization(refs, cfg.objective)
        self.evaluator = evaluator or ProxyEvaluator(engine, ensemble["template"], fit_cases, cfg.proxy,
                                                     cfg.optimizer.workers)
        self.out_dir = Path(out_dir) if out_dir else None
        self._cache: Dict[Tuple[float, ...], float] = {}
        self.history: List[Dict[str, float]] = []
        self.nfev = 0
        self.n_rejected = 0
        self.best = (math.inf, None)
        self.u0 = self.space.initial_unit()

    def prior_components(self, phys) -> List[float]:
        """Prior components ``sqrt(prior_weight) (u - u0)`` toward the initial guess (empty when off)."""
        w = self.cfg.objective.prior_weight
        if w <= 0:
            return []
        return [math.sqrt(w) * (a - b) for a, b in zip(self.space.to_unit(phys), self.u0)]

    def prior(self, phys) -> float:
        """Prior penalty toward the initial guess (0 unless ``objective.prior_weight`` > 0)."""
        return sum(v * v for v in self.prior_components(phys))

    @property
    def n_components(self) -> int:
        """Length of the objective component vector."""
        per = 3 if self.cfg.objective.gor_weight > 0 else 2
        n_fit = sum(1 for w in self.weights.values() if w > 0)
        return n_fit * per + (self.space.n if self.cfg.objective.prior_weight > 0 else 0)

    @property
    def checkpoint_path(self) -> Optional[Path]:
        """Checkpoint file location."""
        return self.out_dir / "checkpoint.json" if self.out_dir else None

    def evaluate_components(self, phys) -> Tuple[List[float], Dict[str, Any]]:
        """Objective components (``J = sum f^2``) and the per-fluid proxy records."""
        params = self.space.decode(phys)
        viol = self.space.pressure_violation(params)
        if viol > 0:
            self.n_rejected += 1
            m = self.n_components
            return [math.sqrt(invalid_penalty(viol) / m)] * m, {}
        proxies = self.evaluator.evaluate(params)
        comps = obj.objective_components(self.refs, proxies, self.weights, self.cfg.objective, self.norm)
        return comps + self.prior_components(phys), proxies

    def evaluate_phys(self, phys, records: bool = False) -> Tuple[float, Dict[str, Any]]:
        """Objective (cached by rounded vector) and, if ``records``, the per-fluid proxy records."""
        key = tuple(round(float(v), self.cfg.optimizer.cache_decimals) for v in phys)
        if key in self._cache and not records:
            return self._cache[key], {}
        comps, proxies = self.evaluate_components(phys)
        f = float(sum(c * c for c in comps))
        self._cache[key] = f
        return f, proxies

    def identifiability(self, u_best, rate_tol: float = 1.0e-3, step: float = 1.0e-3) -> Dict[str, Any]:
        """How well the rates determine each free variable at the optimum.

        Finite-difference Jacobian of the weighted normalized residuals in the unit box. For each
        variable, ``resolution`` is the change (physical units) that alone raises the rms rate
        residual by ``rate_tol`` (0.1 %) when the other variables re-adjust; a variable whose
        resolution exceeds a quarter of its bound range is poorly determined by the data.
        """
        import numpy as np
        n = self.space.n
        if n == 0:
            return {"available": False}
        params0 = self.space.decode(self.space.to_phys(u_best))
        r0 = np.array(obj.residual_vector(self.refs, self.evaluator.evaluate(params0), self.weights,
                                          self.cfg.objective, self.norm))
        cols = []
        for k in range(n):
            pts = []
            for sgn in (+1, -1):
                u = list(u_best)
                u[k] = u[k] + sgn * step
                if not 0.0 <= u[k] <= 1.0:
                    continue
                params = self.space.decode(self.space.to_phys(u))
                if self.space.pressure_violation(params) > 0:
                    continue
                r = np.array(obj.residual_vector(self.refs, self.evaluator.evaluate(params), self.weights,
                                                 self.cfg.objective, self.norm))
                pts.append((sgn * step, r))
            if not pts:
                cols.append(np.zeros_like(r0))
            elif len(pts) == 2:
                cols.append((pts[0][1] - pts[1][1]) / (pts[0][0] - pts[1][0]))
            else:
                cols.append((pts[0][1] - r0) / pts[0][0])
        jac = np.column_stack(cols)
        rows_ok = np.all(np.isfinite(jac), axis=1) & np.isfinite(r0)
        jac = jac[rows_ok]
        m = jac.shape[0]
        sv = np.linalg.svd(jac, compute_uv=False) if m else np.zeros(n)
        cond = float(sv[0] / sv[-1]) if sv.size and sv[-1] > 0 else math.inf
        if m:
            lam = (float(sv[0]) * 1.0e-8) ** 2 if sv.size and sv[0] > 0 else 1.0e-30
            cov = np.linalg.inv(jac.T @ jac + lam * np.eye(n))
        else:
            cov = np.full((n, n), np.inf)
        out = {"available": True, "rate_tolerance": rate_tol, "n_residuals": int(m), "condition_number": cond,
               "variables": []}
        for k, f in enumerate(self.space.free):
            hw_u = rate_tol * math.sqrt(max(m, 1)) * math.sqrt(max(float(cov[k, k]), 0.0))
            col_norm = float(np.linalg.norm(jac[:, k])) if m else 0.0
            alone_u = rate_tol * math.sqrt(max(m, 1)) / col_norm if col_norm > 0 else math.inf
            span = f.upper - f.lower
            out["variables"].append({"key": f.key, "unit": f.unit, "resolution": hw_u * span,
                                     "resolution_fraction_of_range": hw_u,
                                     "resolution_alone": alone_u * span,
                                     "poorly_determined": bool(hw_u > 0.25 or not math.isfinite(hw_u))})
        out["poorly_determined"] = [v["key"] for v in out["variables"] if v["poorly_determined"]]
        return out

    def _track(self, u, f) -> None:
        self.nfev += 1
        if f < self.best[0]:
            self.best = (f, list(u))
            self.history.append({"evaluation": self.nfev, "objective": f})
        if self.checkpoint_path and self.nfev % max(1, self.cfg.optimizer.checkpoint_every) == 0:
            self._checkpoint()

    def _fun(self, u) -> float:
        f, _ = self.evaluate_phys(self.space.to_phys(u))
        self._track(u, f)
        return f

    def _vec(self, u):
        """Objective components at a unit-box point (for least-squares methods)."""
        phys = self.space.to_phys(u)
        comps, _ = self.evaluate_components(phys)
        f = float(sum(c * c for c in comps))
        self._cache[tuple(round(float(v), self.cfg.optimizer.cache_decimals) for v in phys)] = f
        self._track(u, f)
        return comps

    def _checkpoint(self) -> None:
        if self.best[1] is None or self.checkpoint_path is None:
            return
        data = {"config_hash": self.cfg.config_hash(), "best_unit": self.best[1], "best_objective": self.best[0],
                "best_free_values": dict(zip(self.space.keys, self.space.to_phys(self.best[1]))),
                "evaluations": self.nfev, "history": self.history[-500:]}
        self.checkpoint_path.write_text(json.dumps(data, indent=1), encoding="utf-8")

    def run(self, resume: bool = False) -> CalibrationResult:
        """Run the configured optimizer; returns the best common parameter set."""
        t0 = time.time()
        x0 = self.space.initial_unit()
        init_f, _ = self.evaluate_phys(self.space.to_phys(x0))
        cp = self.checkpoint_path
        if resume and cp is not None and cp.is_file():
            data = json.loads(cp.read_text(encoding="utf-8"))
            if data.get("config_hash") == self.cfg.config_hash():
                x0 = data["best_unit"]
                self.log("resuming from checkpoint (objective %.6g after %d evaluations)"
                         % (data["best_objective"], data["evaluations"]))
            else:
                self.log("checkpoint belongs to a different configuration; starting fresh")
        opt_cfg = self.cfg.optimizer
        if self.space.n:
            try:
                import scipy  # noqa: F401
            except ImportError as exc:
                raise RuntimeError("the calibration needs SciPy: install it in the active interpreter "
                                   "(pip install scipy)") from exc
        if self.space.n == 0:
            res_x, opt_info = [], {"method": "none (all variables fixed)", "success": True, "message": "", "starts": []}
        else:
            optimizer = make_optimizer(opt_cfg.method, opt_cfg)
            res = optimizer.minimize(self._fun, x0, opt_cfg.seed, residuals=self._vec)
            res_x = res.x if res.fun <= self.best[0] else self.best[1]
            starts = []
            for s in res.starts:
                entry = {k: v for k, v in s.items() if k not in ("x", "start")}
                entry["free_values"] = dict(zip(self.space.keys, self.space.to_phys(s["x"])))
                starts.append(entry)
            opt_info = {"method": optimizer.name, "success": res.success, "message": res.message,
                        "optimizer_nfev": res.nfev, "starts": starts}
        phys = self.space.to_phys(res_x)
        f, proxies = self.evaluate_phys(phys, records=True)
        params = self.space.decode(phys)
        self.space.validate(params)
        bd = obj.evaluate(self.refs, proxies, self.weights, self.cfg.objective, self.norm)
        self.best = (f, list(res_x)) if res_x else self.best
        self._checkpoint()
        ident = self.identifiability(res_x) if res_x else {"available": False}
        return CalibrationResult(
            params=params, free_values=dict(zip(self.space.keys, phys)), objective=f, breakdown=bd, proxies=proxies,
            weights=self.weights, norm=self.norm, dominance=obj.dominance(bd, self.refs, self.cfg.objective),
            optimizer=opt_info, history=list(self.history), n_evaluations=self.nfev, n_rejected=self.n_rejected,
            seconds=time.time() - t0, initial_objective=init_f, data_objective=bd.total, identifiability=ident)


def evaluate_all(engine: Engine, ensemble: Dict[str, Any], topology: ProxyTopology,
                 params: Dict[str, Tuple[float, float]]) -> Dict[str, Dict[str, Any]]:
    """Run the fitted proxy for every fluid (including fluids without a reference result)."""
    return ProxyEvaluator(engine, ensemble["template"], ensemble["cases"], topology).evaluate(params)
