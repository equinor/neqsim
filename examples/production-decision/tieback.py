"""Two producing wells, a distributed flowline/riser, platform choke and constrained host.

Run from a compiled NeqSim checkout with JPype1 and NEQSIM_CLASSPATH. Each
invocation starts from a fresh steady state. Output is a JSON run receipt.
"""

import argparse
import hashlib
import json
import math
import os
import tempfile
from pathlib import Path

import jpype

from pvt_evidence import check as check_density


def stream(n, name, eos, pressure, rate):
    if isinstance(eos, Path):
        model = n.thermo.util.readwrite.EclipseFluidReadWrite.read(str(eos))
        model.setTemperature(308.15, "K")
        model.setPressure(pressure, "bara")
    else:
        model = getattr(n.thermo.system, "System" + eos + "Eos")(308.15, pressure)
        for species, amount in zip(("methane", "ethane", "propane", "n-pentane", "n-heptane",
                                    "n-decane", "water"), (.42, .04, .03, .07, .07, .05, .32)):
            model.addComponent(species, amount)
        model.setMixingRule("classic")
    model.setMultiPhaseCheck(True)
    feed = n.process.equipment.stream.Stream(name, model)
    feed.setFlowRate(rate, "kg/sec")
    feed.run()
    return feed


def make_pipe(eq, feed, refinement):
    pipe = eq.pipeline.TwoFluidPipe("5.4 km flowline and riser", feed)
    faces = [-260.0]
    lengths = []
    for length, end, count in zip((800, 900, 850, 1100, 1350, 400),
                                  (-278, -252, -289, -270, -280, 25), (8, 9, 9, 11, 13, 10)):
        start = faces[-1]
        count *= refinement
        lengths.extend([length / count] * count)
        faces.extend(start + (end - start) * j / count for j in range(1, count + 1))
    pipe.setLength(sum(lengths))
    pipe.setDiameter(.2032)
    pipe.setRoughness(4.5e-5)
    pipe.setSectionLengths(lengths)
    pipe.setCellFaceElevationProfile(faces)
    pipe.setOutletPressure(56.40099362, "bara")
    pipe.setEnableJouleThomson(False)
    pipe.setEnableWaterOilSlip(True)
    pipe.setAllowOutletPhaseBackflow(False)
    pipe.setEnableCoupledPressureMomentum(True)
    pipe.setEnableInterfacialPressure(True)
    pipe.setTimeIntegrationMethod(eq.pipeline.twophasepipe.numerics.TimeIntegrator.Method.EULER)
    pipe.setEnableAdaptiveTimestepping(True)
    pipe.setMaterialWaveRusanovEnabled(True)
    pipe.setComponentTransportEnabled(True)
    pipe.run()
    if not pipe.isSteadyStateConverged():
        raise ValueError(str(pipe.getSteadyStateConvergenceReport()))
    return pipe


def host_snapshot(n, gas, rate, recycle_fraction=.15):
    """Steady compression with cooled, pressure-reduced discharge recycle at a sampled instant."""
    eq = n.process.equipment
    feed = eq.stream.Stream("Host fresh gas", gas.getFluid().clone())
    feed.setFlowRate(rate, "kg/sec")
    feed.run()
    tear = eq.stream.Stream("Recycle return", feed.getFluid().clone())
    tear.setFlowRate(rate * recycle_fraction / (1 - recycle_fraction), "kg/sec")
    mixer = eq.mixer.Mixer("Compressor suction")
    mixer.addStream(feed)
    mixer.addStream(tear)
    machine = eq.compressor.Compressor("Export compressor", mixer.getOutletStream())
    machine.setOutletPressure(100.0, "bara")
    machine.setIsentropicEfficiency(.78)
    split = eq.splitter.Splitter("Discharge split", machine.getOutletStream(), 2)
    split.setSplitFactors([1 - recycle_fraction, recycle_fraction])
    valve = eq.valve.ThrottlingValve("Recycle valve", split.getSplitStream(1))
    valve.setOutletPressure(feed.getPressure("bara"), "bara")
    cooler = eq.heatexchanger.Cooler("Recycle cooler", valve.getOutletStream())
    cooler.setOutTemperature(feed.getTemperature("K"), "K")
    recycle = eq.util.Recycle("Recycle convergence")
    recycle.addStream(cooler.getOutletStream())
    recycle.setOutletStream(tear)
    recycle.setTolerance(1e-7)
    proc = n.process.processmodel.ProcessSystem()
    for unit in (feed, tear, mixer, machine, split, valve, cooler, recycle):
        proc.add(unit)
    proc.run()
    export = split.getSplitStream(0).getFlowRate("kg/sec")
    if not recycle.solved() or abs(rate - export) > 1e-5 * rate:
        raise ValueError("Host recycle or fresh-gas/export balance failed")
    flow = mixer.getOutletStream().getFlowRate("m3/hr")
    energy_error = (split.getSplitStream(0).getFluid().getEnthalpy() - feed.getFluid().getEnthalpy()
                    - machine.getPower("W") - cooler.getDuty("W"))
    return {"host_energy_residual_W": energy_error, "power_kW": machine.getPower("kW"), "gas_export_kg_s": export,
            "suction_actual_m3_h": flow, "screening_surge_margin": flow / 325.0 - 1}


def chemistry(n):
    brine = n.thermo.system.SystemPitzer(298.15, 1.01325)
    for name, moles in (("water", 55.508), ("Na+", 1.), ("Ba++", .001),
                        ("Cl-", 1.), ("SO4--", .001)):
        brine.addComponent(name, moles)
    brine.setMixingRule("classic")
    brine.init(0)
    return list(brine.getMissingPhreeqcPitzerCatalogRows())


def run(n, opt):
    eq = n.process.equipment
    fluid_basis = Path(opt.fluid_deck) if opt.fluid_deck else opt.eos
    feed = stream(n, "Gathering node", fluid_basis, 70., 20.)
    composition = [(str(feed.getFluid().getComponent(i).getComponentName()),
                    float(feed.getFluid().getComponent(i).getz())) for i in range(feed.getFluid().getNumberOfComponents())]
    fluid_digest = hashlib.sha256(json.dumps([(k, round(v, 12)) for k, v in sorted(composition)],
                                             separators=(",", ":")).encode()).hexdigest()
    density_evidence = check_density(n, feed.getFluid(), opt.density_evidence, fluid_digest) if opt.density_evidence else None
    pipe = make_pipe(eq, feed, opt.mesh)
    # Fixed calibration from the 60-cell SRK design point (69.52643382711764 bara).
    wells = []
    for tag, rate, reservoir, pi in (("A", 14., 120., 8.740185945616182e-5),
                                    ("B", 6., 117., 4.046554785341249e-5)):
        inlet = stream(n, "Reservoir " + tag, fluid_basis, reservoir, rate)
        well = eq.reservoir.WellFlow("Well " + tag)
        well.setInletStream(inlet)
        if opt.basis == "matched":
            pi = inlet.getFlowRate("MSm3/day") / (reservoir ** 2 - pipe.getInletFacePressure("bara") ** 2)
        well.setWellProductionIndex(pi)
        well.solveFlowFromOutletPressure(True)
        wells.append((well, reservoir))
    outlet = pipe.getOutletStream()
    q0 = 19.99999999999999  # kg/s at the reference choke rating
    rho0 = 171.53793465259855  # kg/m3 at the same rating
    p0 = 54.40099362
    if opt.basis == "matched":
        q0 = outlet.getFlowRate("kg/sec")
        rho0 = outlet.getFluid().getDensity("kg/m3")
        drop = 2.
    else:
        # Balance the shared physical restrictions before handing over to
        # dynamics. Neither PI nor the installed choke coefficient is fitted here.
        for _ in range(30):
            inlet_pressure = pipe.getInletFacePressure("bara")
            rate = 0.
            for well, reservoir in wells:
                if not 0 < inlet_pressure < reservoir:
                    raise ValueError("Steady well pressure outside the IPR range")
                well.setOutletPressure(inlet_pressure, "bara")
                well.run()
                rate += well.getOutletStream().getFlowRate("kg/sec")
            outlet = pipe.getOutletStream()
            drop = 2. * (outlet.getFlowRate("kg/sec") / q0) ** 2 * rho0 / outlet.getFluid().getDensity("kg/m3")
            face = pipe.getOutletPressure("bara")
            if abs(face - p0 - drop) < 1.e-5 and abs(rate - feed.getFlowRate("kg/sec")) < 1.e-5:
                break
            feed.setFlowRate(rate, "kg/sec")
            feed.run()
            pipe.setOutletPressure(p0 + drop, "bara")
            pipe.run()
            if not pipe.isSteadyStateConverged():
                raise ValueError("Steady pipe failed during well/choke initialization")
        else:
            raise ValueError("Coupled steady well rates and choke pressure failed to converge")
    if opt.energy_audit:
        pipe.prepareConservativeEnergyTransientFromSteadyState()
    valve = eq.valve.ThrottlingValve("Platform inlet choke", outlet)
    valve.setKv(1.)
    valve.setOutletPressure(p0, "bara")
    valve.run()
    vessel = eq.separator.ThreePhaseSeparator("Inlet separator", valve.getOutletStream())
    vessel.setInternalDiameter(2.2)
    vessel.setSeparatorLength(7.)
    vessel.setLiquidLevel(.5)
    vessel.setCalculateSteadyState(False)
    vessel.run()
    capacities = {"Gas": 6.46460031955775, "Oil": 18.494369334222533, "Water": 5.041030346220999}
    targets = {"Gas": 54.400993619999994, "Oil": 1.1, "Water": .3005126953125}
    controls = []
    for variable, product, transmitter, units, gain, integral in (
        ("Gas", vessel.getGasOutStream(), n.process.measurementdevice.PressureTransmitter("PT", vessel.getGasOutStream()), "bara", 20., 20.),
        ("Oil", vessel.getOilOutStream(), n.process.measurementdevice.OilLevelTransmitter("LT", vessel), "m", 1000., 60.),
        ("Water", vessel.getWaterOutStream(), n.process.measurementdevice.WaterLevelTransmitter("LT interface", vessel), "m", 1000., 60.)):
        transmitter.setUnit(units)
        controller = n.process.controllerdevice.ControllerDeviceBaseClass(variable + " PI")
        controller.setTransmitter(transmitter)
        controller.setUnit(units)
        if opt.basis == "matched":
            capacities[variable] = 1.5 * product.getFlowRate("kg/sec")
            targets[variable] = transmitter.getMeasuredValue(units)
        controller.setControllerSetPoint(targets[variable])
        controller.setControllerParameters(gain, integral, 0.)
        controller.setOutputLimits(5., 100.)
        fraction = product.getFlowRate("kg/sec") / capacities[variable]
        if not .05 <= fraction <= 1.:
            raise ValueError("Steady withdrawal exceeds the installed separator outlet range")
        product.setFlowRate(capacities[variable], "kg/sec")
        setter = getattr(vessel, "set" + variable + "OutletFlowFraction")
        setter(2 / 3 if opt.basis == "matched" else fraction)
        controls.append((controller, setter, variable))
    if opt.energy_audit:
        vessel.initializeTransientCalculation()
    max_network_energy = 0.
    max_network_component = 0.
    phases = eq.pipeline.TwoFluidMassBalanceReport.Phase
    cumulative = 0.
    rows = []
    max_phase = max_component = 0.
    uuid = jpype.JClass("java.util.UUID")
    for index in range(round(opt.duration / opt.dt)):
        t = index * opt.dt
        run_id = uuid(0, index + 1)
        pressure = pipe.getInletFacePressure("bara")
        well_rates = []
        for i, (well, reservoir) in enumerate(wells):
            if not 0 < pressure < reservoir:
                raise ValueError("IPR outside forward-flow range")
            well.setOutletPressure(pressure, "bara")
            well.run(run_id)
            availability = 0. if i == 1 and 4 <= t < 12 else 1.
            well_rates.append(availability * well.getOutletStream().getFlowRate("kg/sec"))
        # Both reservoirs carry the same composition and prescribed inlet temperature.
        feed.setFlowRate(sum(well_rates), "kg/sec")
        feed.run()
        opening = 58.
        if opt.policy == "ramp" and t >= 12:
            opening = min(58., 54. + (t - 12) / 4.)
        sep_p = vessel.getPressure("bara")
        density = outlet.getFluid().getDensity("kg/m3")
        target_drop = 2. * (outlet.getFlowRate("kg/sec") / q0 * 58. / opening) ** 2 * rho0 / density
        drop += -math.expm1(-opt.dt) * (target_drop - drop)
        pipe.setOutletPressure(sep_p + drop, "bara")
        pipe.runTransient(opt.dt, run_id)
        if pipe.isTransientOutletBackflowClamped():
            cell = pipe.getSectionSnapshots()[-1]
            velocities = (cell.getGasVelocity(), cell.getOilVelocity(), cell.getWaterVelocity())
            raise ValueError(f"Outlet backflow clamped at {t + opt.dt} s; "
                             f"separator={sep_p:.6f} bara, choke={opening:.3f}%, "
                             f"imposed riser boundary={sep_p + drop:.6f} bara; "
                             f"end-step gas/oil/water cell velocities={velocities} m/s")
        fluid = outlet.getFluid()
        n.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash()
        fluid.initProperties()
        valve.setPercentValveOpening(opening)
        valve.setOutletPressure(sep_p, "bara")
        valve.run(run_id)
        if abs(valve.getOutletStream().getFlowRate("kg/sec") - outlet.getFlowRate("kg/sec")) > 1e-8:
            raise ValueError("Choke handoff mass mismatch")
        for controller, setter, variable in controls:
            if variable == "Gas" and t == 20.:
                controller.setControllerSetPoint(p0 + .25)
            previous = getattr(vessel, "get" + variable + "OutletFlowFraction")()
            controller.runTransient(100 * previous, opt.dt, run_id)
            setter(controller.getResponse() / 100.)
        gas_rate = vessel.getGasOutletFlowFraction() * vessel.getGasOutStream().getFlowRate("kg/sec")
        oil_rate = vessel.getOilOutletFlowFraction() * vessel.getOilOutStream().getFlowRate("kg/sec")
        sample = (index + 1) % round(1 / opt.dt) == 0
        host = host_snapshot(n, vessel.getGasOutStream(), gas_rate) if sample else {}
        if opt.energy_audit:
            # Boundary includes both wells at the pipe inlet and ends at the three
            # separator outlets. The host recycle has a separate steady energy audit.
            inventory = vessel.getThermoSystem()
            inventory.init(2)
            initial_u = inventory.getInternalEnergy()
            names = list(pipe.getLastComponentConservationReport().getComponentNames())
            initial_moles = [inventory.getComponent(name).getNumberOfmoles() for name in names]
            outlet_h = 0.
            outlet_kg = [0.] * len(names)
            for variable, product in (("Gas", vessel.getGasOutStream()), ("Oil", vessel.getOilOutStream()),
                                      ("Water", vessel.getWaterOutStream())):
                fraction = getattr(vessel, "get" + variable + "OutletFlowFraction")()
                state = product.getFluid()
                state.init(2)
                outlet_h += fraction * state.getEnthalpy()
                for j, name in enumerate(names):
                    species = state.getComponent(name)
                    outlet_kg[j] += opt.dt * fraction * species.getNumberOfmoles() * species.getMolarMass()
        vessel.runTransient(opt.dt, run_id)
        balance = pipe.getLastMassBalanceReport()
        phase_error = max(abs(balance.getResidualKg(x)) for x in (phases.GAS, phases.OIL, phases.WATER))
        component = pipe.getLastComponentConservationReport()
        component_error = max(abs(x) for x in component.getInventoryResidualKg())
        if opt.energy_audit:
            energy = pipe.getLastConservativeEnergyReport()
            inventory = vessel.getThermoSystem()
            inventory.init(2)
            energy_residual = (energy.getStoredEnergyChangeJ() + inventory.getInternalEnergy() - initial_u
                               - energy.getInletAdvectedEnergyJ() + opt.dt * outlet_h
                               - energy.getGravitationalWorkJ() - energy.getWallHeatToFluidJ())
            initial, final = component.getInitialInventoryKg(), component.getFinalInventoryKg()
            admitted = component.getInletBoundaryMassKg()
            for j, name in enumerate(names):
                species = inventory.getComponent(name)
                vessel_delta = (species.getNumberOfmoles() - initial_moles[j]) * species.getMolarMass()
                residual = final[j] - initial[j] + vessel_delta - admitted[j] + outlet_kg[j]
                max_network_component = max(max_network_component, abs(residual))
            max_network_energy = max(max_network_energy, abs(energy_residual))
        max_phase = max(max_phase, phase_error)
        max_component = max(max_component, component_error)
        cumulative += sum(well_rates) * opt.dt
        if max_phase > 1e-7 or max_component > 1e-7 or not component.isConverged():
            raise ValueError(f"Inventory ledger failed at {t + opt.dt}")
        rows.append({"end_s": t + opt.dt, "well_a_kg_s": well_rates[0], "well_b_kg_s": well_rates[1],
                     "opening_pct": opening, "riser_boundary_bara": sep_p + drop,
                     "separator_bara": vessel.getPressure("bara"), "level_m": vessel.getOilLevel(),
                     "interface_m": vessel.getWaterLevel(), "oil_out_kg_s": oil_rate,
                     "riser_gas_kg_s": balance.getOutletMassKg(phases.GAS) / opt.dt,
                     "riser_oil_kg_s": balance.getOutletMassKg(phases.OIL) / opt.dt,
                     "riser_water_kg_s": balance.getOutletMassKg(phases.WATER) / opt.dt, **host})
    config = {key: value for key, value in vars(opt).items() if key not in ("output", "fluid_deck", "density_evidence")}
    config["design_basis"] = opt.basis
    if opt.fluid_deck:
        config["eos"] = str(feed.getFluid().getModelName())
        config["fluid_deck_sha256"] = hashlib.sha256(fluid_basis.read_bytes()).hexdigest()
    return {"configuration": config, "feed_composition_sha256": fluid_digest, "density_evidence": density_evidence, "well_production_kg": cumulative, "max_phase_residual_kg": max_phase,
            "max_component_residual_kg": max_component, "facility_energy_residual_J": None,
            "max_pipe_to_separator_energy_residual_J": max_network_energy if opt.energy_audit else None,
            "max_pipe_to_separator_component_residual_kg": max_network_component if opt.energy_audit else None,
            "missing_barite_interactions": chemistry(n), "time_series": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eos", choices=["Srk", "Pr"], default="Srk")
    parser.add_argument("--density-evidence", help="Sample-matched measured phase-density manifest")
    parser.add_argument("--energy-audit", action="store_true", help="Use conservative pipe energy recovery")
    parser.add_argument("--fluid-deck", help="Frozen E300 feed deck including water and overall composition")
    parser.add_argument("--basis", choices=["installed", "matched"], default="installed",
                        help="Freeze installed ratings, or recalibrate to compare matched initial operating points")
    parser.add_argument("--policy", choices=["fixed", "ramp"], default="fixed")
    parser.add_argument("--mesh", type=int, choices=[1, 2, 4], default=1)
    parser.add_argument("--dt", type=float, choices=[.125, .25], default=.25)
    parser.add_argument("--duration", type=int, default=40)
    parser.add_argument("--output", required=True)
    parser.add_argument("--revision", required=True)
    args = parser.parse_args()
    if args.duration <= 20:
        parser.error("Duration must include the separator backpressure step at 20 s")
    if Path(args.output).exists():
        parser.error("Output already exists; use a new receipt path")
    jpype.startJVM("-Xmx600m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    try:
        result = run(jpype.JPackage("neqsim"), args)
    except Exception as exc:
        result = {"status": "rejected", "reason": str(exc),
                  "configuration": {k: v for k, v in vars(args).items() if k != "output"}}
    else:
        result["status"] = "completed"
    # A failed solve never publishes a successful-looking partial receipt. A
    # previous study result cannot be silently overwritten by a concurrent run.
    target = Path(args.output)
    payload = json.dumps(result, allow_nan=False) + "\n"
    with tempfile.NamedTemporaryFile(dir=target.parent, mode="w", encoding="utf-8", delete=False) as tmp:
        temporary = Path(tmp.name)
        try:
            tmp.write(payload)
            tmp.flush()
            os.link(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)
    if result["status"] == "rejected":
        raise SystemExit(result["reason"])


if __name__ == "__main__":
    main()
