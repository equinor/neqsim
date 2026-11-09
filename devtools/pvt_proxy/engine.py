"""NeqSim adapter: fluids, the detailed reference process and the forward proxy train.

Everything is built from existing NeqSim process equipment (``Stream``, ``Heater``,
``Separator``, ``Mixer``, ``Compressor``) inside a ``ProcessSystem``. Reference loops (scrubber
liquid returned upstream) are closed with ``ProcessSystem.makeRecycles()``. The proxy is a strictly
forward ``ProcessSystem`` that is built once per fluid and re-run with new set points, so a
calibration does not rebuild flowsheets for every candidate.

Product definitions (identical for reference and proxy):

* oil: stock-tank liquid after the final flash at standard conditions; volume = mass / density at
  standard conditions (EOS density, NeqSim default volume correction).
* gas: every gas stream leaving the process, including the stock-tank flash gas, converted with
  the ideal-gas molar volume at standard conditions (:attr:`StandardConditions.sm3_per_kmol`).
* GOR = gas Sm3 / oil Sm3. Rates are for the configured feed molar rate.
"""
from __future__ import annotations

import importlib
import math
import os
import sys
import threading
from typing import Any, Dict, List, Optional, Sequence, Tuple

from .config import STOCK_TANK, ConfigError, FeedConditions, ProxyTopology, ReferenceSpec, StandardConditions
from .fluids import ComponentDef, FluidTemplate

Z_FLOOR = 1.0e-12
_LOCK = threading.Lock()


class SimulationError(RuntimeError):
    """A NeqSim calculation failed for one fluid."""


def jclass():
    """Return ``jpype.JClass`` with the JVM started on the workspace (or packaged) NeqSim classes."""
    import jpype
    with _LOCK:
        if not jpype.isJVMStarted():
            here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            if here not in sys.path:
                sys.path.insert(0, here)
            import neqsim_dev_setup
            neqsim_dev_setup.neqsim_init(verbose=False)
    return jpype.JClass


def neqsim_version() -> str:
    """Best-effort identity of the NeqSim classes in use."""
    try:
        import jpype
        jclass()
        src = jpype.JClass("neqsim.thermo.system.SystemSrkEos").class_.getProtectionDomain().getCodeSource()
        return str(src.getLocation().getPath())
    except Exception:  # noqa: BLE001
        return "unknown"


def _measure(feed, oil, gases, std: StandardConditions) -> Dict[str, Any]:
    """Reduce product streams to standard oil and gas rates."""
    feed_kg = float(feed.getFlowRate("kg/hr"))
    oil_kg = float(oil.getFlowRate("kg/hr"))
    oil_sm3 = 0.0
    oil_rho = None
    if oil_kg > 1.0e-9:
        fl = oil.getFluid()
        fl.initProperties()
        oil_rho = float(fl.getDensity("kg/m3"))
        oil_sm3 = oil_kg / oil_rho
    gas_kmol = sum(float(g.getFlowRate("kmol/hr")) for g in gases)
    gas_kg = sum(float(g.getFlowRate("kg/hr")) for g in gases)
    gas_sm3 = gas_kmol * std.sm3_per_kmol
    values = [feed_kg, oil_kg, oil_sm3, gas_kmol, gas_kg]
    if not all(math.isfinite(v) for v in values):
        raise SimulationError("non-finite product rate (flash failure)")
    mb = (oil_kg + gas_kg - feed_kg) / feed_kg
    return {"status": "ok", "reason": "", "oil_Sm3_h": oil_sm3, "gas_Sm3_h": gas_sm3,
            "gor_Sm3_Sm3": (gas_sm3 / oil_sm3) if oil_sm3 > 0 else math.inf,
            "oil_kg_h": oil_kg, "gas_kg_h": gas_kg, "oil_density_kg_m3": oil_rho,
            "mass_balance_rel_err": mb}


def failed(reason: str) -> Dict[str, Any]:
    """Result record for a failed simulation (kept, never dropped)."""
    return {"status": "failed", "reason": str(reason)[:300], "oil_Sm3_h": math.nan, "gas_Sm3_h": math.nan,
            "gor_Sm3_Sm3": math.nan, "oil_kg_h": math.nan, "gas_kg_h": math.nan, "oil_density_kg_m3": None,
            "mass_balance_rel_err": math.nan}


class Engine:
    """Creates NeqSim fluids and process models for one standard-condition and feed basis."""

    def __init__(self, std: StandardConditions, feed: FeedConditions):
        self.std = std
        self.feed = feed
        self.J = jclass()
        self._base: Dict[int, Any] = {}

    # -- fluids ---------------------------------------------------------------------------------
    def resolve_template(self, template: FluidTemplate) -> Optional[List[float]]:
        """Load an E300 or factory template; fill its component list. Returns the E300 file composition."""
        if template.factory:
            fl = self._factory_fluid(template)
            n = int(fl.getNumberOfComponents())
            template.components = [ComponentDef(str(fl.getComponent(i).getName())) for i in range(n)]
            self._base[id(template)] = fl
            return None
        if not template.e300:
            self._base_fluid(template)
            return None
        reader = self.J("neqsim.thermo.util.readwrite.EclipseFluidReadWrite")
        fl = reader.read(str(template.e300))
        n = int(fl.getNumberOfComponents())
        template.components = [ComponentDef(str(fl.getComponent(i).getName())) for i in range(n)]
        self._base[id(template)] = fl
        return [float(fl.getComponent(i).getz()) for i in range(n)]

    def _factory_fluid(self, template: FluidTemplate):
        mod_name, fn_name = template.factory.split(":", 1)
        fl = getattr(importlib.import_module(mod_name), fn_name)()
        if fl is None or int(fl.getNumberOfComponents()) < 1:
            raise ConfigError("fluid factory %s returned no fluid" % template.factory)
        return fl

    def _base_fluid(self, template: FluidTemplate):
        key = id(template)
        if key in self._base:
            return self._base[key]
        if template.e300 or template.factory:
            self.resolve_template(template)
            return self._base[key]
        cls = self.J("neqsim.thermo.system." + ("SystemSrkEos" if template.eos == "srk" else "SystemPrEos"))
        fl = cls(273.15 + self.feed.temperature_C, self.feed.pressure_bara)
        for c in template.components:
            if c.is_pseudo:
                fl.addTBPfraction(c.name, 1.0, float(c.mw_kg_mol), float(c.density_g_cm3))
            else:
                fl.addComponent(c.name, 1.0)
        fl.createDatabase(True)
        fl.setMixingRule(int(template.mixing_rule))
        if int(fl.getNumberOfComponents()) != len(template.components):
            raise ConfigError("NeqSim created %d components for a %d-component template"
                              % (fl.getNumberOfComponents(), len(template.components)))
        self._base[key] = fl
        return fl

    def new_fluid(self, template: FluidTemplate, z: Sequence[float], temperature_C: float, pressure_bara: float):
        """Clone the template fluid and set composition, temperature and pressure."""
        import jpype
        base = self._base_fluid(template)
        if len(z) != int(base.getNumberOfComponents()):
            raise ConfigError("composition has %d entries, template has %d components"
                              % (len(z), base.getNumberOfComponents()))
        fl = base.clone()
        fl.setMolarComposition(jpype.JArray(jpype.JDouble)([max(float(v), Z_FLOOR) for v in z]))
        fl.setTemperature(273.15 + temperature_C)
        fl.setPressure(pressure_bara)
        return fl

    def molar_masses(self, template: FluidTemplate) -> List[float]:
        """Component molar masses in kg/mol."""
        base = self._base_fluid(template)
        return [float(base.getComponent(i).getMolarMass()) for i in range(int(base.getNumberOfComponents()))]

    def cvd(self, template: FluidTemplate, z: Sequence[float], temperature_C: float,
            pressures: Sequence[float]) -> List[Dict[str, Any]]:
        """Constant-volume depletion: produced-gas and reservoir-liquid composition per step.

        The cell volume is fixed at the first pressure. At each lower pressure the fluid is
        flashed and the excess volume is removed as equilibrium gas.
        """
        ops_cls = self.J("neqsim.thermodynamicoperations.ThermodynamicOperations")
        names = template.names
        moles = [float(v) for v in z]
        v_cell = None
        cum_gas = 0.0
        steps = []
        for p in pressures:
            rec = {"pressure_bara": p, "cumulative_gas_produced_frac": cum_gas}
            try:
                n_tot = sum(moles)
                fl = self.new_fluid(template, [m / n_tot for m in moles], temperature_C, p)
                ops_cls(fl).TPflash()
                fl.initProperties()
                v_per_mol = float(fl.getVolume("m3")) / float(fl.getTotalNumberOfMoles())
                if v_cell is None:
                    v_cell = n_tot * v_per_mol
                gas_z = liq_z = None
                if fl.hasPhaseType("gas"):
                    ph = fl.getPhase("gas")
                    gas_z = [float(ph.getComponent(i).getx()) for i in range(len(names))]
                    beta = float(ph.getNumberOfMolesInPhase()) / float(fl.getTotalNumberOfMoles())
                    vg = float(ph.getVolume("m3")) / float(ph.getNumberOfMolesInPhase())
                    remove = min(max((n_tot * v_per_mol - v_cell) / vg, 0.0), n_tot * beta)
                    moles = [m - remove * y for m, y in zip(moles, gas_z)]
                    cum_gas += remove
                    rec["cumulative_gas_produced_frac"] = cum_gas
                for ptype in ("oil", "liquid"):
                    if fl.hasPhaseType(ptype):
                        ph = fl.getPhase(ptype)
                        liq_z = [float(ph.getComponent(i).getx()) for i in range(len(names))]
                        break
                rec["gas_z"], rec["liquid_z"] = gas_z, liq_z
            except Exception as exc:  # noqa: BLE001
                rec["error"] = "CVD flash failed at %.2f bara: %s" % (p, exc)
            steps.append(rec)
        return steps

    def feed_stream(self, ps, template: FluidTemplate, z: Sequence[float]):
        """Add the feed stream at the configured feed conditions and rate."""
        fl = self.new_fluid(template, z, self.feed.temperature_C, self.feed.pressure_bara)
        feed = self.J("neqsim.process.equipment.stream.Stream")("feed", fl)
        feed.setFlowRate(self.feed.rate_kmol_h, "kmol/hr")
        ps.add(feed)
        return feed

    # -- flowsheet helpers ----------------------------------------------------------------------
    def _stage(self, ps, name: str, inlets: List[Any], p: float, t: float, allow_mixer_growth: bool = False):
        """Mixer (if needed) + Heater (P, T) + Separator. Returns (heater, separator, mixer)."""
        J = self.J
        mixer = None
        if len(inlets) > 1 or allow_mixer_growth:
            mixer = J("neqsim.process.equipment.mixer.Mixer")("mix_" + name)
            for s in inlets:
                mixer.addStream(s)
            ps.add(mixer)
            src = mixer.getOutletStream()
        else:
            src = inlets[0]
        heater = J("neqsim.process.equipment.heatexchanger.Heater")("cond_" + name, src)
        heater.setOutPressure(float(p))
        heater.setOutTemperature(273.15 + float(t))
        ps.add(heater)
        sep = J("neqsim.process.equipment.separator.Separator")(name, heater.getOutletStream())
        ps.add(sep)
        return heater, sep, mixer

    # -- proxy ----------------------------------------------------------------------------------
    def forward_train(self, template: FluidTemplate, z: Sequence[float], topology: ProxyTopology) -> "ForwardTrain":
        """Build the strictly forward proxy for one fluid."""
        return ForwardTrain(self, template, z, topology)

    # -- reference ------------------------------------------------------------------------------
    def run_reference(self, spec: ReferenceSpec, template: FluidTemplate, z: Sequence[float]) -> Dict[str, Any]:
        """Run one fluid through the detailed reference process."""
        try:
            if spec.type == "forward":
                topo = spec.as_proxy_topology()
                train = self.forward_train(template, z, topo)
                res = train.run(fixed_parameters(topo))
            elif spec.type == "recompression":
                res = self._run_recompression(spec, template, z)
            else:
                res = self._run_python(spec, template, z)
        except Exception as exc:  # noqa: BLE001
            return failed("reference simulation failed: %s" % exc)
        if res["status"] == "ok" and abs(res["mass_balance_rel_err"]) > spec.max_mass_balance_error:
            return dict(failed("reference mass balance error %.2e exceeds %.1e"
                               % (res["mass_balance_rel_err"], spec.max_mass_balance_error)))
        return res

    def _run_recompression(self, spec: ReferenceSpec, template: FluidTemplate, z: Sequence[float]) -> Dict[str, Any]:
        J = self.J
        ps = J("neqsim.process.processmodel.ProcessSystem")()
        feed = self.feed_stream(ps, template, z)
        returns_to = {r.liquid_return_stage for r in spec.recompression} | \
            {r.gas_return_stage for r in spec.recompression if r.gas_return_stage}
        stage_p = {str(s["name"]): float(s["pressure_bara"]) for s in spec.stages}
        gas_out: Dict[str, Any] = {}
        mixers: Dict[str, Any] = {}
        liquid = feed
        for s in spec.stages:
            name = str(s["name"])
            _, sep, mixer = self._stage(ps, name, [liquid], s["pressure_bara"], s["temperature_C"],
                                        allow_mixer_growth=name in returns_to)
            mixers[name] = mixer
            gas_out[name] = sep.getGasOutStream()
            liquid = sep.getLiquidOutStream()
        _, st_sep, _ = self._stage(ps, STOCK_TANK, [liquid], self.std.pressure_bara, self.std.temperature_C)
        consumed = set()
        source_p = dict(stage_p)
        for r in spec.recompression:
            inlets = [gas_out[s] for s in r.gas_sources]
            consumed.update(r.gas_sources)
            if len(inlets) > 1:
                mx = J("neqsim.process.equipment.mixer.Mixer")("mix_" + r.name)
                for s in inlets:
                    mx.addStream(s)
                ps.add(mx)
                src = mx.getOutletStream()
            else:
                src = inlets[0]
            p_scr = r.scrubber_pressure_bara
            if p_scr > max(source_p[s] for s in r.gas_sources):
                comp = J("neqsim.process.equipment.compressor.Compressor")("comp_" + r.name, src)
                comp.setOutletPressure(p_scr)
                ps.add(comp)
                src = comp.getOutletStream()
            cooler = J("neqsim.process.equipment.heatexchanger.Heater")("cool_" + r.name, src)
            cooler.setOutPressure(p_scr)
            cooler.setOutTemperature(273.15 + r.cooler_temperature_C)
            ps.add(cooler)
            scr = J("neqsim.process.equipment.separator.Separator")(r.name, cooler.getOutletStream())
            ps.add(scr)
            mixers[r.liquid_return_stage].addStream(scr.getLiquidOutStream())
            gas = scr.getGasOutStream()
            if r.gas_return_stage:
                import jpype
                split = J("neqsim.process.equipment.splitter.Splitter")("gas_return_" + r.name, gas, 2)
                split.setFlowRates(jpype.JArray(jpype.JDouble)([float(r.gas_return_Sm3_h) * 24.0, -1.0]), "Sm3/day")
                ps.add(split)
                mixers[r.gas_return_stage].addStream(split.getSplitStream(0))
                gas = split.getSplitStream(1)
            gas_out[r.name] = gas
            source_p[r.name] = p_scr
        gases = [g for n, g in gas_out.items() if n not in consumed] + [st_sep.getGasOutStream()]
        recycles = ps.makeRecycles(float(spec.recycle_tolerance)) if spec.recompression else []
        ps.run()
        if recycles is not None and not all(bool(r.solved()) for r in recycles):
            raise SimulationError("reference recycle did not converge")
        res = _measure(feed, st_sep.getLiquidOutStream(), gases, self.std)
        res["n_recycles"] = len(recycles) if recycles is not None else 0
        return res

    def _run_python(self, spec: ReferenceSpec, template: FluidTemplate, z: Sequence[float]) -> Dict[str, Any]:
        mod_name, fn_name = spec.callable.split(":", 1)
        fn = getattr(importlib.import_module(mod_name), fn_name)
        fl = self.new_fluid(template, z, self.feed.temperature_C, self.feed.pressure_bara)
        out = dict(fn(fl, self.feed, self.std, self.J))
        if "oil_Sm3_h" not in out or "gas_Sm3_h" not in out:
            raise SimulationError("reference callable must return oil_Sm3_h and gas_Sm3_h")
        oil, gas = float(out["oil_Sm3_h"]), float(out["gas_Sm3_h"])
        if not (math.isfinite(oil) and math.isfinite(gas)):
            raise SimulationError("reference callable returned non-finite rates")
        res = {"status": "ok", "reason": "", "oil_Sm3_h": oil, "gas_Sm3_h": gas,
               "gor_Sm3_Sm3": gas / oil if oil > 0 else math.inf, "oil_kg_h": out.get("oil_kg_h", math.nan),
               "gas_kg_h": out.get("gas_kg_h", math.nan), "oil_density_kg_m3": out.get("oil_density_kg_m3"),
               "mass_balance_rel_err": float(out.get("mass_balance_rel_err", 0.0))}
        return res


def fixed_parameters(topology: ProxyTopology) -> Dict[str, Tuple[float, float]]:
    """Parameter map from the initial values of a topology."""
    out = {s.name: (s.pressure.value, s.temperature.value) for s in topology.stages}
    if topology.scrubber is not None:
        out[topology.scrubber.name] = (topology.scrubber.pressure.value, topology.scrubber.temperature.value)
    return out


class ForwardTrain:
    """Strictly forward proxy flowsheet for one fluid, re-run with new stage set points.

    Liquid cascades stage to stage and ends in a stock-tank flash at standard conditions. The
    optional scrubber collects gas from its source stages and sends its liquid forward to a later
    stage or to the stock tank. There is no recycle: every unit only sees upstream streams.
    """

    def __init__(self, engine: Engine, template: FluidTemplate, z: Sequence[float], topology: ProxyTopology):
        J = engine.J
        self.engine = engine
        self.topology = topology
        self.ps = J("neqsim.process.processmodel.ProcessSystem")()
        self.feed = engine.feed_stream(self.ps, template, z)
        names = topology.stage_names + [STOCK_TANK]
        scr = topology.scrubber
        dest = names.index(scr.liquid_destination) if scr is not None else -1
        self.heaters: Dict[str, Any] = {}
        seps: Dict[str, Any] = {}
        liquid = self.feed
        std = engine.std
        for pos, name in enumerate(names):
            inlets = [liquid]
            if scr is not None and pos == dest:
                gas_in = [seps[s].getGasOutStream() for s in scr.gas_sources]
                h, s_sep, _ = engine._stage(self.ps, scr.name, gas_in, scr.pressure.value, scr.temperature.value)
                self.heaters[scr.name] = h
                seps[scr.name] = s_sep
                inlets.append(s_sep.getLiquidOutStream())
            if name == STOCK_TANK:
                p, t = std.pressure_bara, std.temperature_C
            else:
                st = topology.stages[pos]
                p, t = st.pressure.value, st.temperature.value
            h, sep, _ = engine._stage(self.ps, name, inlets, p, t)
            self.heaters[name] = h
            seps[name] = sep
            liquid = sep.getLiquidOutStream()
        sources = set(scr.gas_sources) if scr is not None else set()
        self.gases = [seps[n].getGasOutStream() for n in names if n not in sources]
        if scr is not None:
            self.gases.append(seps[scr.name].getGasOutStream())
        self.oil = seps[STOCK_TANK].getLiquidOutStream()
        self.has_recycle = False

    def run(self, params: Dict[str, Tuple[float, float]]) -> Dict[str, Any]:
        """Set stage (P bara, T degC) and run; returns the product record (failures captured)."""
        try:
            for name, (p, t) in params.items():
                h = self.heaters[name]
                h.setOutPressure(float(p))
                h.setOutTemperature(273.15 + float(t))
            self.ps.run()
            return _measure(self.feed, self.oil, self.gases, self.engine.std)
        except Exception as exc:  # noqa: BLE001
            return failed("proxy simulation failed: %s" % exc)
