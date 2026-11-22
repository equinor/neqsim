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

from host_chart import assess_point, load_chart
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


class HostDutyUnavailable(ValueError):
    """Evidence for an online compressor study with zero modeled gas export."""

    def __init__(self, fresh, export, solved):
        self.diagnostic = {"condition": "no_online_export_duty", "fresh_kg_s": fresh,
                           "export_kg_s": export, "recycle_solved": bool(solved)}
        super().__init__(f"No online compressor duty: fresh={fresh:.9g} kg/s, export={export:.9g} kg/s, "
                         f"recycle solved={solved}")


def host_snapshot(n, gas, rate, recycle_fraction=.15, chart=None):
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
    if chart:
        machine.loadCompressorChartFromJsonString(json.dumps(chart))
        machine.setSolveSpeed(True)
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
    if recycle.solved() and not recycle.isActive() and export == 0.0 and 0. <= rate * 3600. < recycle.getMinimumFlow():
        # The native recycle has reached numerical zero flow. A pressure-target
        # compressor snapshot has no online operating point in this condition.
        raise HostDutyUnavailable(rate, export, True)
    if not recycle.solved() or not math.isfinite(export) or abs(rate - export) > 1e-5 * rate:
        raise ValueError(f"Host recycle solved={recycle.solved()}, iterations={recycle.getIterations()}, "
                         f"fresh={rate:.9g} kg/s, export={export:.9g} kg/s, "
                         f"flow/temperature/pressure/composition errors="
                         f"{recycle.getErrorFlow():.6g}/{recycle.getErrorTemperature():.6g}/"
                         f"{recycle.getErrorPressure():.6g}/{recycle.getErrorComposition():.6g}")
    flow = mixer.getOutletStream().getFlowRate("m3/hr")
    energy_error = (split.getSplitStream(0).getFluid().getEnthalpy() - feed.getFluid().getEnthalpy()
                    - machine.getPower("W") - cooler.getDuty("W"))
    duty = assess_point(machine, chart, mixer.getOutletStream(), flow) if chart else {"screening_surge_margin": flow / 325.0 - 1}
    return {"host_energy_residual_W": energy_error, "power_kW": machine.getPower("kW"), "gas_export_kg_s": export,
            "suction_actual_m3_h": flow, **duty}


def chemistry(n):
    brine = n.thermo.system.SystemPitzer(298.15, 1.01325)
    for name, moles in (("water", 55.508), ("Na+", 1.), ("Ba++", .001),
                        ("Cl-", 1.), ("SO4--", .001)):
        brine.addComponent(name, moles)
    brine.setMixingRule("classic")
    brine.init(0)
    return list(brine.getMissingPhreeqcPitzerCatalogRows())


# Stay inside the native first-step boundary identity tolerance (1e-9 relative).
# Absolute residuals in bar and kg/s are appropriate for this installed fixture.
HANDOFF_TOLERANCE = 1.e-10


def compare_choke(n, reported_stream, p_up, p_down, opening, rated_rate, rated_density):
    """Compare the imposed restriction with an independent equilibrium short-opening calculation."""
    effective_area = rated_rate * (opening / 58.) / math.sqrt(2. * rated_density * 2.e5)
    fluid = reported_stream.getFluid().clone()
    fluid.setPressure(p_up, "bara")
    release = n.process.safety.release
    model = release.HomogeneousEquilibriumReleaseModel()
    request = release.ReleaseFlowRequest(fluid, math.sqrt(4. * effective_area / math.pi), 1., p_down * 1.e5)
    result = model.calculate(request)
    return {"model": str(result.getModelId()), "model_version": str(result.getModelVersion()),
            "upstream_bara": p_up, "downstream_bara": p_down, "effective_cd_area_m2": effective_area,
            "stream_kg_s": reported_stream.getFlowRate("kg/sec"), "status": str(result.getStatus()),
            "hem_kg_s": result.getMassFlowRateKgS() if result.isUsable() else None,
            "choked": bool(result.isChoked()) if result.isUsable() else None,
            "diagnostics": [str(item.getCode()) for item in result.getDiagnostics()]}


class OutletBackflowError(ValueError):
    """Carry the rejected interval separately from the final cell and transferred mass."""

    def __init__(self, pipe, phases, start, dt, vessel, opening, pressure, initialization):
        cell = pipe.getSectionSnapshots()[-1]
        balance = pipe.getLastMassBalanceReport()
        inventory = vessel.getThermoSystem()
        phase_order = (phases.GAS, phases.OIL, phases.WATER)
        self.diagnostic = {
            "condition": "forward_only_outlet_clamped", "start_s": start, "end_s": start + dt,
            "separator_bara": vessel.getPressure("bara"), "choke_pct": opening, "imposed_outlet_bara": pressure,
            "accepted_pipe_substeps": balance.getAcceptedSubsteps(),
            "pipe_interval_s": balance.getElapsedTimeSeconds(),
            "pipe_initial_phase_inventory_kg": {p.name().lower(): balance.getInitialMassKg(p) for p in phase_order},
            "pipe_final_phase_inventory_kg": {p.name().lower(): balance.getFinalMassKg(p) for p in phase_order},
            "separator_mass_before_handoff_kg": inventory.getMass("kg"),
            "separator_advanced_for_interval": False,
            "terminal_cell_velocity_m_s": dict(zip(("gas", "oil", "water"),
                (cell.getGasVelocity(), cell.getOilVelocity(), cell.getWaterVelocity()))),
            "interval_outlet_mass_kg": {phase.name().lower(): balance.getOutletMassKg(phase)
                                       for phase in phase_order},
            "steady_handoff": initialization}
        super().__init__(f"Outlet backflow clamped in ({start}, {start + dt}] s at {pressure:.6f} bara")


def run(n, opt, chart_info=None):
    chart = chart_info[0] if chart_info else None
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
    initialization = None
    if opt.basis == "matched":
        q0 = outlet.getFlowRate("kg/sec")
        rho0 = outlet.getFluid().getDensity("kg/m3")
        drop = 2.
    else:
        # Balance the shared physical restrictions before handing over to
        # dynamics. Neither PI nor the installed choke coefficient is fitted here.
        for iteration in range(1, 31):
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
            pressure_error = face - p0 - drop
            rate_error = rate - feed.getFlowRate("kg/sec")
            if max(abs(pressure_error), abs(rate_error)) < HANDOFF_TOLERANCE:
                initialization = {"iterations": iteration, "choke_residual_bar": pressure_error,
                                  "well_residual_kg_s": rate_error, "absolute_tolerance": HANDOFF_TOLERANCE}
                break
            feed.setFlowRate(rate, "kg/sec")
            feed.run()
            pipe.setOutletPressure(p0 + drop, "bara")
            pipe.run()
            if not pipe.isSteadyStateConverged():
                raise ValueError("Steady pipe failed during well/choke initialization")
        else:
            raise ValueError("Coupled steady well rates and choke pressure failed to converge")
    if initialization is not None:
        initialization["choke_comparison"] = compare_choke(n, outlet, pipe.getOutletPressure("bara"), p0, 58., q0, rho0)
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
            rejection = OutletBackflowError(pipe, phases, t, opt.dt, vessel, opening, sep_p + drop, initialization)
            rejection.diagnostic["choke_comparison"] = compare_choke(n, outlet, sep_p + drop, sep_p, opening, q0, rho0)
            raise rejection
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
        host = {}
        if sample:
            try:
                host = host_snapshot(n, vessel.getGasOutStream(), gas_rate, chart=chart)
            except HostDutyUnavailable as exc:
                exc.diagnostic.update(start_s=t, end_s=t + opt.dt)
                raise
            except ValueError as exc:
                raise ValueError(f"Host withdrawal for ({t}, {t + opt.dt}] s: {exc}") from exc
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
                     "manifold_bara": pipe.getInletFacePressure("bara"),
                     "arrival_K": outlet.getTemperature("K"),
                     "separator_bara": vessel.getPressure("bara"), "level_m": vessel.getOilLevel(),
                     "interface_m": vessel.getWaterLevel(), "oil_out_kg_s": oil_rate,
                     "riser_gas_kg_s": balance.getOutletMassKg(phases.GAS) / opt.dt,
                     "riser_oil_kg_s": balance.getOutletMassKg(phases.OIL) / opt.dt,
                     "riser_water_kg_s": balance.getOutletMassKg(phases.WATER) / opt.dt, **host})
    config = configuration(opt, chart_info)
    if opt.fluid_deck:
        config["eos"] = str(feed.getFluid().getModelName())
    return {"configuration": config, "steady_handoff": initialization, "feed_composition_sha256": fluid_digest, "density_evidence": density_evidence, "well_production_kg": cumulative, "max_phase_residual_kg": max_phase,
            "max_component_residual_kg": max_component, "facility_energy_residual_J": None,
            "max_pipe_to_separator_energy_residual_J": max_network_energy if opt.energy_audit else None,
            "max_pipe_to_separator_component_residual_kg": max_network_component if opt.energy_audit else None,
            "missing_barite_interactions": chemistry(n), "time_series": rows}


def configuration(opt, chart_info=None):
    """Identify the same frozen case whether a numerical step succeeds or fails."""
    config = {k: v for k, v in vars(opt).items() if k not in ("output", "fluid_deck", "density_evidence", "compressor_chart")}
    if chart_info:
        config["chart_sha256"] = chart_info[1]
        config["chart_origin"] = chart_info[0]["origin"]
        config["chart_source"] = chart_info[0]["source"]
    config["design_basis"] = opt.basis
    config["handoff_protocol"] = "coupled-steady-v2" if opt.basis == "installed" else "matched-v1"
    if opt.fluid_deck:
        config["fluid_deck_sha256"] = hashlib.sha256(Path(opt.fluid_deck).read_bytes()).hexdigest()
    return config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eos", choices=["Srk", "Pr"], default="Srk")
    parser.add_argument("--density-evidence", help="Sample-matched measured phase-density manifest")
    parser.add_argument("--energy-audit", action="store_true", help="Use conservative pipe energy recovery")
    parser.add_argument("--compressor-chart", help="NeqSim JSON with declared surge endpoints and suction envelope")
    parser.add_argument("--fluid-deck", help="Frozen E300 feed deck including water and overall composition")
    parser.add_argument("--basis", choices=["installed", "matched"], default="installed",
                        help="Freeze installed ratings, or recalibrate to compare matched initial operating points")
    parser.add_argument("--policy", choices=["fixed", "ramp"], default="fixed")
    parser.add_argument("--mesh", type=int, choices=[1, 2, 4], default=1)
    parser.add_argument("--dt", type=float, choices=[.125, .25], default=.25)
    parser.add_argument("--duration", type=int, default=40)
    parser.add_argument("--output", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--event-id", default="synthetic-two-well-restart", help="Preassigned event identifier shared with a holdout manifest")
    args = parser.parse_args()
    if args.duration <= 20:
        parser.error("Duration must include the separator backpressure step at 20 s")
    if Path(args.output).exists():
        parser.error("Output already exists; use a new receipt path")
    jpype.startJVM("-Xmx600m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    chart = load_chart(args.compressor_chart) if args.compressor_chart else None
    config = configuration(args, chart)
    try:
        result = run(jpype.JPackage("neqsim"), args, chart)
    except Exception as exc:
        result = {"status": "rejected", "reason": str(exc),
                  "configuration": config}
        if isinstance(exc, HostDutyUnavailable):
            result["host_diagnostic"] = exc.diagnostic
        if isinstance(exc, OutletBackflowError):
            result["outlet_diagnostic"] = exc.diagnostic
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
