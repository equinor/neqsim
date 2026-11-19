"""Three-stage oil stabilization and recompression, steady NeqSim flowsheet."""
import json
import sys
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "devtools"))
from neqsim_dev_setup import neqsim_init

ns = neqsim_init(project_root=REPO, verbose=False)
J = ns.JClass
import jpype as jp

P = jp.JPackage("neqsim").process
E = P.equipment
ROOT = REPO / "target" / "stabilization-commissioning"
ROOT.mkdir(parents=True, exist_ok=True)
Z = {"nitrogen": .4, "CO2": 1.2, "methane": 32, "ethane": 5, "propane": 4,
     "i-butane": 1, "n-butane": 2.3, "n-pentane": 2.7, "n-hexane": 2.5,
     "n-heptane": 2.5, "n-octane": 3, "n-decane": 4, "nC14": 6, "nC20": 8,
     "water": 25.3}

def build(rate=100_000, heater_c=78., gas_splits=(0.,0.,0.), surge_targets=None,
          integrated_sas=False):
    process = P.processmodel.ProcessSystem()
    units, streams, sinks, work = {}, {}, {}, {}
    def add(unit):
        process.add(unit)
        units[str(unit.getName())] = unit
        unit.run()
        return unit
    def valve(tag, inlet, pressure):
        u = E.valve.ThrottlingValve(tag, inlet)
        u.setOutletPressure(pressure)
        return add(u).getOutletStream()
    def thermal(cls, tag, inlet, temp):
        u = cls(tag, inlet)
        u.setOutTemperature(temp, "C")
        add(u)
        work[tag] = u.getDuty() / 1000
        return u.getOutletStream()
    def track(name, obj):
        streams[name] = obj
        return obj
    fluid = J("neqsim.thermo.system.SystemSrkEos")(343.15, 55.)
    for component, parts in Z.items():
        fluid.addComponent(component, float(parts))
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    feed = E.stream.Stream("F-001", fluid)
    feed.setFlowRate(rate, "kg/hr")
    add(feed)
    current = track("feed", feed)
    if integrated_sas:
        for tag, stroke in (("20-XV-101A", 2.0), ("20-XV-101B", 3.0)):
            isolation = E.valve.HIPPSValve(tag, current)
            isolation.setOutletPressure(55.0, "bara")
            isolation.setClosureTime(stroke)
            isolation.setPhysicalStepIdempotencyEnabled(True)
            current = add(isolation).getOutletStream()
        inlet_esd = E.valve.ESDValve("10-ESDV-100", current)
        inlet_esd.setOutletPressure(55.0, "bara")
        inlet_esd.setStrokeTime(4.0)
        inlet_esd.setPhysicalStepIdempotencyEnabled(True)
        current = add(inlet_esd).getOutletStream()
    overhead = []
    return_paths = []
    for i, pressure in enumerate([26., 8., 1.6]):
        n = 101 + i
        if i == 2:
            current = thermal(E.heatexchanger.Heater, "E-101", current, heater_c)
        current = valve(["CHV-100", "LV-101", "LV-102"][i], current, pressure)
        mixing = E.mixer.Mixer("M-"+str(n))
        mixing.addStream(current)
        add(mixing)
        sep = add(E.separator.ThreePhaseSeparator("V-"+str(n), mixing.getOutletStream()))
        overhead.append(track("gas"+str(n), sep.getGasOutStream()))
        if integrated_sas and n == 101:
            for suffix in "ABC":
                transmitter = P.measurementdevice.PressureTransmitter(
                    "20-PT-001" + suffix, sep.getGasOutStream())
                transmitter.setUnit("bara")
                process.add(transmitter)
        sinks["water"+str(n)] = track("water"+str(n), sep.getWaterOutStream())
        current = track("oil"+str(n), sep.getOilOutStream())
    # Stabilized crude to export via flooding suction and product cooler.
    pump = E.pump.Pump("P-101", current)
    pump.setOutletPressure(12.)
    pump.setIsentropicEfficiency(.75)
    add(pump)
    work["P-101"] = pump.getPower("kW")
    sinks["oil_export"] = track("oil_export", thermal(E.heatexchanger.Cooler, "E-102", pump.getOutletStream(), 38.))
    last = None
    for i, (ps, pd) in enumerate(zip([1.45, 7., 24.], [7., 24., 60.])):
        n = 201+i
        fresh = valve("PV-"+str(103-i), overhead[2-i], ps)
        mix = E.mixer.Mixer("M-"+str(n))
        mix.addStream(fresh)
        if last is not None:
            mix.addStream(last)
        mix = add(mix)
        cold = thermal(E.heatexchanger.Cooler, "E-"+str(221+i), mix.getOutletStream(), 35.)
        suction = add(E.separator.ThreePhaseSeparator("V-"+str(n), cold))
        sinks["suction_water"+str(n)] = track("suction_water"+str(n),suction.getWaterOutStream())
        liquid = track("suction_liq"+str(n), suction.getOilOutStream())
        if i == 0:
            lift = E.pump.Pump("P-301", liquid)
            lift.setOutletPressure(2.)
            lift.setIsentropicEfficiency(.65)
            liquid = add(lift).getOutletStream()
        dest = [103,103,102][i]
        return_paths.append(("RS-"+str(n), liquid, "M-"+str(dest)))
        dry = track("suction"+str(n), suction.getGasOutStream())
        k = E.compressor.Compressor("K-"+str(n), dry)
        k.setOutletPressure(pd)
        k.setIsentropicEfficiency(.78)
        add(k)
        work["K-"+str(n)] = k.getPower("kW")
        hot = track("discharge"+str(n), k.getOutletStream())
        cooled = thermal(E.heatexchanger.Cooler, "E-"+str(n), hot, 35.)
        knock = add(E.separator.ThreePhaseSeparator("V-"+str(211+i), cooled))
        sinks["cond_water"+str(n)] = track("cond_water"+str(n),knock.getWaterOutStream())
        liquid = track("cond"+str(n), knock.getOilOutStream())
        dest = [103,102,101][i]
        return_paths.append(("RD-"+str(n), liquid, "M-"+str(dest)))
        dry_gas = knock.getGasOutStream()
        split = E.splitter.Splitter("SPL-"+str(n), dry_gas, 2)
        split.setSplitFactors([1.-gas_splits[i],gas_splits[i]])
        add(split)
        last = track("net"+str(n), split.getSplitStream(0))
        track("recycle_gas"+str(n), split.getSplitStream(1))
        if gas_splits[i] > 0.:
            returning = valve("ASV-"+str(n),split.getSplitStream(1),ps)
            return_paths.append(("RG-"+str(n),returning,"M-"+str(n)))
    sinks["gas_export"] = last
    for name, source, dest in return_paths:
        tear = source.clone(name+" outlet")
        units[dest].addStream(tear)
        r = E.util.Recycle(name)
        r.addStream(source)
        r.setOutletStream(tear)
        r.setTolerance(1.e-8)
        r.setFlowTolerance(1.e-8)
        r.setTemperatureTolerance(1.e-8)
        process.add(r)
        units[name] = r
    # Force every unit on each tear sweep. This preserves composition updates
    # even when a small flow change fits a library recalculation tolerance.
    r_units = [units[name] for name,_,_ in return_paths]
    for sweep in range(180):
        for unit in units.values():
            unit.run()
        error = np.zeros(3)
        if surge_targets is not None:
            for i, target in enumerate(surge_targets):
                n = str(201+i)
                splitter = units["SPL-"+n]
                error[i] = 1.-streams["suction"+n].getFlowRate("m3/hr")/target
                fraction = np.clip(splitter.getSplitFactor(1)+.22*error[i],0.,.94)
                if abs(error[i]) > .0008:
                    splitter.setSplitFactors([1.-fraction,fraction])
        if surge_targets is not None and sweep % 12 == 0:
            print(sweep, error, [units["SPL-"+str(n)].getSplitFactor(1) for n in range(201,204)],flush=True)
        if sweep >= 3 and all(r.solved() for r in r_units) and max(abs(error)) <= .0008:
            break
    else:
        raise RuntimeError("Recycle convergence failed after 180 full sweeps")
    print("Converged full sweeps", sweep+1, flush=True)
    for tag, unit in units.items():
        if tag.startswith(("K-","P-")):
            work[tag] = unit.getPower("kW")
        if tag.startswith("E-"):
            work[tag] = unit.getDuty()/1000
    return process, units, streams, sinks, work

def stream_data(s):
    f = s.getFluid()
    if s.getFlowRate("kg/hr") < 1.e-10:
        return dict(kg_h=0., p_bara=s.getPressure("bara"), t_C=s.getTemperature("C"),
                    density=None, actual_m3_h=0., enthalpy_kW=0.)
    return dict(kg_h=s.getFlowRate("kg/hr"), p_bara=s.getPressure("bara"),
                t_C=s.getTemperature("C"), density=f.getDensity("kg/m3"),
                actual_m3_h=s.getFlowRate("m3/hr"), enthalpy_kW=f.getEnthalpy()/1000)

def evaluate(rate=100_000, heater_c=78., gas_splits=(0.,0.,0.), surge_targets=None):
    process, units, streams, sinks, duties = build(rate, heater_c, gas_splits, surge_targets)
    feed = streams["feed"].getFluid()
    def molar(s):
        f = s.getFluid()
        return np.asarray(f.getMolarComposition()) * f.getTotalNumberOfMoles()
    balances = molar(streams["feed"]) - sum(map(molar, sinks.values()))
    mass_error = rate - sum(s.getFlowRate("kg/hr") for s in sinks.values())
    energy_error = feed.getEnthalpy()/1000 + sum(duties.values()) - sum(s.getFluid().getEnthalpy()/1000 for s in sinks.values())
    oil = streams["oil_export"].getFluid().clone()
    # Retain the oil outlet composition, including dissolved water.
    ops = J("neqsim.thermodynamicoperations.ThermodynamicOperations")
    oil.setTemperature(37.8, "C")
    oil.setPressure(1.2)
    oil.setMultiPhaseCheck(False)
    ops(oil).bubblePointPressureFlash(False)
    bpp = oil.getPressure("bara")
    assert np.isfinite(bpp) and bpp > 0
    recycles = {tag: {"kg_h":u.getOutletStream().getFlowRate("kg/hr"),
                     "solved":bool(u.solved()),"relative_composition_error":u.getErrorComposition(),
                     "temperature_error":u.getErrorTemperature(),"flow_error":u.getErrorFlow()}
               for tag,u in units.items() if tag.startswith(("RS-","RD-","RG-"))}
    print("Recycles", recycles, "mass", mass_error, "energy", energy_error, flush=True)
    result = dict(recycles=recycles, gas_split_fractions=[units["SPL-"+str(i)].getSplitFactor(1) for i in range(201,204)], streams={n:stream_data(s) for n,s in streams.items()},
                  sinks=list(sinks), duties_kW=duties, oil_bubble_bara_37_8C=bpp,
                  feed_components_relative_moles=Z, mass_error_kg_h=mass_error,
                  max_component_residual_mol_s=max(abs(balances)), energy_error_kW=energy_error)
    return result

if __name__ == "__main__":
    out = evaluate()
    print(json.dumps(out, indent=2))
    (ROOT/"steady.json").write_text(json.dumps(out, indent=2))
    targets = [.76*1.12*out["streams"]["suction"+str(n)]["actual_m3_h"] for n in range(201,204)]
    other = evaluate(55_000,78.,(.5,.4,.3),targets)
    (ROOT/"steady_turndown.json").write_text(json.dumps(other, indent=2))
    cooler = evaluate(100_000, 65.)
    (ROOT/"steady_cooler_heater.json").write_text(json.dumps(cooler, indent=2))
