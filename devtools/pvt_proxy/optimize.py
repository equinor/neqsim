"""Parameter space, physical constraints and replaceable optimizers.

The optimizer works in a normalized unit box ``u in [0, 1]^n`` over the free variables; the
:class:`ParameterSpace` maps it to stage pressures/temperatures and back. Any optimizer that
implements :class:`Optimizer.minimize` can be swapped in without touching the process code.

Pressure ordering ``P_1 > P_2 > ... > P_N > P_stock`` (with ``min_pressure_step_bar``) is enforced
by rejection: an invalid candidate is never simulated; it receives a penalty that grows with the
size of the violation, so derivative-free methods are pushed back into the feasible region.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Optional, Sequence, Tuple

from .config import OPTIMIZER_METHODS as METHODS
from .config import ConfigError, ProxyTopology, StandardConditions
from .fluids import latin_hypercube

INVALID_BASE_PENALTY = 1.0e6


@dataclass
class FreeVariable:
    """A free proxy variable."""

    key: str
    unit: str
    lower: float
    upper: float
    initial: float


class ParameterSpace:
    """Maps the optimizer vector to proxy stage set points."""

    def __init__(self, topology: ProxyTopology, std: StandardConditions):
        self.topology = topology
        self.std = std
        self.free: List[FreeVariable] = []
        units = topology.stages + ([topology.scrubber] if topology.scrubber is not None else [])
        for u in units:
            for attr, unit in (("pressure", "bara"), ("temperature", "C")):
                var = getattr(u, attr)
                if not var.fixed:
                    self.free.append(FreeVariable("%s.%s" % (u.name, attr), unit, var.lower, var.upper, var.value))

    @property
    def n(self) -> int:
        """Number of free variables."""
        return len(self.free)

    @property
    def keys(self) -> List[str]:
        """Free-variable keys."""
        return [f.key for f in self.free]

    def to_unit(self, phys: Sequence[float]) -> List[float]:
        """Physical values to the unit box."""
        return [(v - f.lower) / (f.upper - f.lower) for v, f in zip(phys, self.free)]

    def to_phys(self, u: Sequence[float]) -> List[float]:
        """Unit box to physical values (clipped to the bounds)."""
        return [f.lower + min(max(float(x), 0.0), 1.0) * (f.upper - f.lower) for x, f in zip(u, self.free)]

    def initial_unit(self) -> List[float]:
        """Initial guess in the unit box."""
        return self.to_unit([f.initial for f in self.free])

    def decode(self, phys: Sequence[float]) -> Dict[str, Tuple[float, float]]:
        """Physical free-variable vector to ``{unit name: (P bara, T degC)}`` incl. fixed values."""
        values = {f.key: v for f, v in zip(self.free, phys)}
        out = {}
        units = self.topology.stages + ([self.topology.scrubber] if self.topology.scrubber is not None else [])
        for u in units:
            p = values.get(u.name + ".pressure", u.pressure.value)
            t = values.get(u.name + ".temperature", u.temperature.value)
            out[u.name] = (float(p), float(t))
        return out

    def pressure_violation(self, params: Dict[str, Tuple[float, float]]) -> float:
        """Total violation (bar) of the monotonic-pressure rule; 0 for a valid candidate."""
        step = self.topology.min_pressure_step_bar
        seq = [params[s.name][0] for s in self.topology.stages] + [self.std.pressure_bara]
        viol = 0.0
        for a, b in zip(seq, seq[1:]):
            viol += max(0.0, b + step - a)
        if self.topology.scrubber is not None:
            viol += max(0.0, self.std.pressure_bara + step - params[self.topology.scrubber.name][0])
        return viol

    def validate(self, params: Dict[str, Tuple[float, float]]) -> None:
        """Raise :class:`ConfigError` for an invalid pressure sequence."""
        v = self.pressure_violation(params)
        if v > 0:
            seq = ", ".join("%s %.3f" % (s.name, params[s.name][0]) for s in self.topology.stages)
            raise ConfigError("invalid separator pressure sequence (violation %.3f bar): %s > stock tank %.5f "
                              "with minimum step %.3f bar" % (v, seq, self.std.pressure_bara,
                                                              self.topology.min_pressure_step_bar))


@dataclass
class OptimizerResult:
    """Outcome in the unit box."""

    x: List[float]
    fun: float
    nfev: int
    success: bool
    message: str
    starts: List[Dict[str, object]] = field(default_factory=list)


class Optimizer:
    """Optimizer interface (unit-box, bound-constrained, derivative-free)."""

    name = "base"

    def minimize(self, fun: Callable[[Sequence[float]], float], x0: Sequence[float], seed: int,
                 residuals: Optional[Callable[[Sequence[float]], List[float]]] = None) -> OptimizerResult:
        """Minimize ``fun`` over ``[0, 1]^n`` from ``x0``; ``residuals`` (``fun = sum r^2``) is optional."""
        raise NotImplementedError


class DifferentialEvolution(Optimizer):
    """SciPy differential evolution (global, Latin-hypercube initialisation, seeded)."""

    name = "de"

    def __init__(self, maxiter: int = 15, popsize: int = 6):
        self.maxiter, self.popsize = maxiter, popsize

    def minimize(self, fun, x0, seed, residuals=None):
        from scipy.optimize import differential_evolution
        n = len(x0)
        res = differential_evolution(fun, [(0.0, 1.0)] * n, seed=seed, maxiter=self.maxiter, popsize=self.popsize,
                                     init="latinhypercube", polish=False, x0=list(x0), tol=0.0, updating="immediate")
        return OptimizerResult(list(res.x), float(res.fun), int(res.nfev), bool(res.success), str(res.message))


class LocalScipy(Optimizer):
    """SciPy ``minimize`` with Powell or Nelder-Mead and box bounds."""

    def __init__(self, method: str = "Powell", maxiter: int = 400, xtol: float = 1e-3, ftol: float = 1e-6):
        self.method, self.maxiter, self.xtol, self.ftol = method, maxiter, xtol, ftol
        self.name = method.lower()

    def minimize(self, fun, x0, seed, residuals=None):
        from scipy.optimize import minimize, minimize_scalar
        n = len(x0)
        if n == 1:
            res = minimize_scalar(lambda t: fun([t]), bounds=(0.0, 1.0), method="bounded",
                                  options={"xatol": self.xtol * 1e-2, "maxiter": self.maxiter})
            return OptimizerResult([float(res.x)], float(res.fun), int(res.nfev), bool(res.success), str(res.message))
        if self.method == "Powell":
            opts = {"xtol": self.xtol, "ftol": self.ftol, "maxiter": self.maxiter, "maxfev": self.maxiter * max(n, 1) * 10}
        else:
            opts = {"xatol": self.xtol, "fatol": self.ftol, "maxiter": self.maxiter}
        res = minimize(fun, list(x0), method=self.method, bounds=[(0.0, 1.0)] * n, options=opts)
        return OptimizerResult([min(max(v, 0.0), 1.0) for v in res.x], float(res.fun), int(res.nfev),
                               bool(res.success), str(res.message))


class LeastSquares(Optimizer):
    """Bounded trust-region-reflective least squares on the objective components.

    Efficient in the narrow, correlated valleys typical for stage conditions that are only seen
    through stock-tank oil and total gas. Falls back to the scalar function when no residual
    callback is given. The finite-difference step ``diff_step`` (unit box) must stay well above
    the flash convergence noise (about 1e-6 in the normalized residuals); 1e-3 is safe.
    """

    name = "lsq"

    def __init__(self, maxiter: int = 400, xtol: float = 1e-12, ftol: float = 1e-14, diff_step: float = 1e-3):
        self.maxiter, self.xtol, self.ftol, self.diff_step = maxiter, xtol, ftol, diff_step

    def minimize(self, fun, x0, seed, residuals=None):
        import numpy as np
        from scipy.optimize import least_squares
        if residuals is None:
            def residuals(u):
                return [math.sqrt(max(fun(u), 0.0))]
        x0 = np.clip(np.asarray(x0, dtype=float), 0.0, 1.0)
        res = least_squares(lambda u: np.asarray(residuals(list(u)), dtype=float), x0, bounds=(0.0, 1.0),
                            method="trf", diff_step=self.diff_step, xtol=self.xtol, ftol=self.ftol, gtol=1e-15,
                            max_nfev=self.maxiter * max(len(x0), 1))
        return OptimizerResult([float(min(max(v, 0.0), 1.0)) for v in res.x], float(2.0 * res.cost), int(res.nfev),
                               bool(res.success), str(res.message))


class Sequential(Optimizer):
    """Global search followed by local refinement from the global optimum."""

    def __init__(self, first: Optimizer, second: Optimizer):
        self.first, self.second = first, second
        self.name = "%s+%s" % (first.name, second.name)

    def minimize(self, fun, x0, seed, residuals=None):
        a = self.first.minimize(fun, x0, seed, residuals)
        b = self.second.minimize(fun, a.x, seed, residuals)
        best = b if b.fun <= a.fun else a
        return OptimizerResult(best.x, best.fun, a.nfev + b.nfev, b.success, "%s; %s" % (a.message, b.message),
                               starts=[{"stage": self.first.name, "x": a.x, "fun": a.fun},
                                       {"stage": self.second.name, "x": b.x, "fun": b.fun}])


class MultiStart(Optimizer):
    """Local optimizer from the initial guess plus Latin-hypercube starts; keeps every outcome."""

    def __init__(self, local: Optimizer, n_starts: int = 3):
        self.local, self.n_starts = local, max(1, n_starts)
        self.name = "multistart-" + local.name

    def minimize(self, fun, x0, seed, residuals=None):
        starts = [list(x0)] + latin_hypercube(self.n_starts - 1, len(x0), seed)
        runs = [self.local.minimize(fun, s, seed, residuals) for s in starts]
        best = min(runs, key=lambda r: r.fun)
        return OptimizerResult(best.x, best.fun, sum(r.nfev for r in runs), best.success, best.message,
                               starts=[{"start": s, "x": r.x, "fun": r.fun} for s, r in zip(starts, runs)])


def make_optimizer(method: str, opt_cfg) -> Optimizer:
    """Factory for the configured optimizer."""
    powell = LocalScipy("Powell", opt_cfg.local_maxiter, opt_cfg.xtol, opt_cfg.ftol)
    lsq = LeastSquares(opt_cfg.local_maxiter, diff_step=opt_cfg.fd_step)
    de = DifferentialEvolution(opt_cfg.global_maxiter, opt_cfg.popsize)
    table = {"de": de, "powell": powell, "lsq": lsq,
             "nelder-mead": LocalScipy("Nelder-Mead", opt_cfg.local_maxiter, opt_cfg.xtol, opt_cfg.ftol),
             "multistart-powell": MultiStart(powell, opt_cfg.n_starts), "multistart-lsq": MultiStart(lsq, opt_cfg.n_starts),
             "de+powell": Sequential(de, powell), "de+lsq": Sequential(de, lsq)}
    if method not in table:
        raise ConfigError("unknown optimizer method %r (known: %s)" % (method, ", ".join(METHODS)))
    return table[method]

def invalid_penalty(violation: float) -> float:
    """Objective for a rejected (not simulated) candidate."""
    return INVALID_BASE_PENALTY * (1.0 + violation)
