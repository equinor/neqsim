"""Run the native V-101 gas-space PSV/BDV into a finite, rigid adiabatic disposal volume.

Requires JPype and the compiled NeqSim main and test classes on NEQSIM_CLASSPATH.
The synthetic tip takes equilibrium vapor. Equilibrium liquid drains from the common volume.
"""

import argparse
import json
import math
import os
from pathlib import Path

import jpype


VOLUME_M3 = 8.
STATIC_BARA = 1.2
TIP_RATED_KG_S = 10.
TIP_RATED_RISE_BAR = 2.
DRUM_AREA_M2 = 4.
DRAIN_TIME_S = 30.
# At a collapsed flow bracket, the VU flash can resolve pressure only to a fraction of a pascal.
TIP_EOS_TOLERANCE_BAR = 1e-6


def component_moles(fluid):
    """Read named component inventories (mol) of an extensive NeqSim state."""
    return {str(fluid.getComponent(i).getName()): fluid.getComponent(i).getNumberOfmoles()
            for i in range(fluid.getNumberOfComponents())}


def solve_terminal(trial, limit):
    """Bracket the vapor rate that satisfies the gas-tip rating and EOS pressure."""
    low = 0.
    low_error, low_state = trial(low)
    if low_error >= -1e-9:
        return low, low_state
    high = min(limit, TIP_RATED_KG_S * math.sqrt(-low_error / TIP_RATED_RISE_BAR))
    high_error, high_state = trial(high)
    if high_error < -1e-9 and high < limit:
        high = limit
        high_error, high_state = trial(high)
    if abs(high_error) <= 1e-9:
        return high, high_state
    if high_error < 0:
        raise ValueError("Tip exceeds its characterized rate or available opening vapor")
    for iteration in range(100):
        if high - low < 1e-11:
            # Retain the evaluated state with the smaller physical mismatch.
            if min(-low_error, high_error) <= TIP_EOS_TOLERANCE_BAR:
                return (low, low_state) if -low_error < high_error else (high, high_state)
            break
        rate = low + (high - low) * (-low_error) / (high_error - low_error)
        if not low < rate < high or iteration % 5 == 4:
            rate = .5 * (low + high)
        error, state = trial(rate)
        if abs(error) <= 1e-9:
            return rate, state
        if error < 0:
            low, low_error, low_state = rate, error, state
        else:
            high, high_error, high_state = rate, error, state
    raise ValueError(f"Terminal pressure did not converge: bracket {low:g}..{high:g} kg/s, "
                     f"residuals {low_error:.5g}..{high_error:.5g} bar")


class DisposalVolume:
    """One well-mixed equilibrium inventory with vapor withdrawal at the start-of-step composition."""

    def __init__(self, n, seed):
        self.fluid = seed.clone()
        self.flash_type = n.thermodynamicoperations.ThermodynamicOperations
        self.fluid.setPressure(STATIC_BARA, "bara")
        ops = self.flash_type(self.fluid)
        ops.TPflash()
        self.fluid.init(3)
        moles = self.fluid.getTotalNumberOfMoles() * VOLUME_M3 / self.fluid.getVolume("m3")
        self.fluid.setTotalFlowRate(moles, "mol/sec")
        ops.TPflash()
        self.fluid.init(3)
        self.expected_moles = component_moles(self.fluid)
        self.initial_u = self.fluid.getInternalEnergy()
        self.expected_u = self.initial_u
        self.initial_mass = self.fluid.getMass("kg")
        self.admitted = self.discharged = self.drained = 0.

    def advance(self, feeds, dt):
        """Integrate the valve discharge and solve tip flow and final pressure at fixed volume and energy."""
        fluid = self.fluid
        gas = fluid.phaseToSystem("gas")
        gas.init(3)
        gas_molar_mass = gas.getMolarMass()
        gas_h_molar = gas.getEnthalpy() / gas.getTotalNumberOfMoles()
        gas_fractions = {name: gas.getComponent(name).getz() for name in self.expected_moles}
        delta = {name: 0. for name in self.expected_moles}
        delta_u = 0.
        # Drain each equilibrium liquid phase in proportion to its opening inventory.
        fraction = -math.expm1(-dt / DRAIN_TIME_S)
        liquid_drained = 0.
        for i in range(fluid.getNumberOfPhases()):
            phase = fluid.getPhase(i)
            if str(phase.getPhaseTypeName()) == "gas":
                continue
            liquid_drained += fraction * phase.getMass()
            delta_u -= fraction * phase.getEnthalpy()
            for name in delta:
                delta[name] -= fraction * phase.getComponent(name).getNumberOfMolesInPhase()
        admitted = 0.
        relief_liquid_rate = 0.
        for source in feeds:
            rate = source.getFlowRate("kg/sec")
            if not math.isfinite(rate) or rate < 0:
                raise ValueError("Invalid valve discharge")
            if rate == 0:
                continue
            feed = source.getFluid()
            feed.init(3)
            relief_liquid_rate += sum(feed.getPhase(i).getMass() for i in range(feed.getNumberOfPhases())
                                      if str(feed.getPhase(i).getPhaseTypeName()) != "gas")
            amount = dt * rate / feed.getMolarMass()
            for name in delta:
                delta[name] += amount * feed.getComponent(name).getz()
            delta_u += amount * feed.getEnthalpy() / feed.getTotalNumberOfMoles()
            admitted += dt * rate
        # Solve the nonlinear terminal rating against the end pressure from a VU flash.
        # All trial withdrawals use the same opening vapor donor. Only the accepted
        # trial changes the owned header inventory and its cumulative ledgers.
        charged = fluid.clone()
        for name, amount in delta.items():
            if amount:
                charged.addComponent(name, amount)
        charged_u = self.expected_u + delta_u

        def trial(rate):
            amount = dt * rate / gas_molar_mass
            candidate = charged.clone()
            for name, z in gas_fractions.items():
                if z and amount:
                    candidate.addComponent(name, -amount * z)
            ops = self.flash_type(candidate)
            ops.VUflash(VOLUME_M3, charged_u - amount * gas_h_molar, "m3", "J", True)
            candidate.init(3)
            residual = STATIC_BARA + TIP_RATED_RISE_BAR * (rate / TIP_RATED_KG_S)**2 - candidate.getPressure("bara")
            if not math.isfinite(residual):
                raise ValueError("Nonfinite terminal pressure residual")
            return residual, candidate

        limit = min(TIP_RATED_KG_S, gas.getMass("kg") / dt)
        tip_flow, candidate = solve_terminal(trial, limit)
        fluid = self.fluid = candidate
        tip_moles = dt * tip_flow / gas_molar_mass
        self.discharged += dt * tip_flow
        self.drained += liquid_drained
        self.admitted += admitted
        self.expected_u = charged_u - tip_moles * gas_h_molar
        for name, change in delta.items():
            self.expected_moles[name] += change - tip_moles * gas_fractions[name]
        actual = component_moles(fluid)
        energy_residual = fluid.getInternalEnergy() - self.expected_u
        liquid_volume = sum(fluid.getPhase(i).getVolume("m3") for i in range(fluid.getNumberOfPhases())
                            if str(fluid.getPhase(i).getPhaseTypeName()) != "gas")
        return {"drain_kg_s": liquid_drained / dt, "liquid_level_m": liquid_volume / DRUM_AREA_M2,
                "relief_liquid_kg_s": relief_liquid_rate, "header_bara": fluid.getPressure("bara"), "header_K": fluid.getTemperature("K"),
                "header_mass_kg": fluid.getMass("kg"), "tip_kg_s": tip_flow,
                "condensate_kg": sum(fluid.getPhase(i).getMass() for i in range(fluid.getNumberOfPhases())
                                     if str(fluid.getPhase(i).getPhaseTypeName()) != "gas"),
                "volume_residual_m3": fluid.getVolume("m3") - VOLUME_M3,
                "internal_energy_residual_J": energy_residual,
                "relative_energy_residual": abs(energy_residual) / max(1., abs(self.initial_u), abs(self.expected_u)),
                "max_component_residual_mol": max(abs(actual[k] - v) for k, v in self.expected_moles.items()),
                "mass_residual_kg": fluid.getMass("kg") - self.initial_mass - self.admitted + self.discharged + self.drained}


def run(n, case, dt, duration, fixed_boundary, vessel_energy_policy):
    """Advance the two native vessels and apply the previous drum pressure at both valve flanges."""
    study = n.process.safety.scenario.AdvancedV101PsvStudy
    settings = study.defaultSettings()
    process = study.buildSource(settings)
    if case == "wet":
        feed = process.getUnit("20-FEED-001")
        fluid = feed.getFluid()
        fractions = list(fluid.getMolarComposition())
        names = [str(fluid.getComponent(i).getName()) for i in range(len(fractions))]
        fractions[names.index("methane")] -= .20
        fractions[names.index("n-hexane")] += .20
        fluid.setMolarComposition(fractions)
        feed.setFlowRate(20000., "kg/hr")
        feed.run()
        process.run()
    study.configureAfterSteady(process, settings)
    for tag in ("10-V-100", "20-V-101"):
        source = process.getUnit(tag)
        source.setConservativeTransientEnergyIntegrationEnabled(vessel_energy_policy == "conservative")
        if vessel_energy_policy == "conservative":
            source.setConservativeTransientEnergyTolerance(settings.get("relativeEnergyLimit"))
    vessel = process.getUnit("20-V-101")
    pcv = process.getUnit("20-PCV-001")
    psv = process.getUnit("20-PSV-001")
    tee = process.getUnit("20-TEE-PSV-001")
    header = DisposalVolume(n, vessel.getGasOutStream().getFluid())
    bdv = None
    if case != "psv":
        if case == "wet":
            # One liquid takeoff supplies the existing LCV and the additional BDV.
            upstream_vessel = process.getUnit("10-V-100")
            liquid_valve = process.getUnit("10-LCV-001")
            branch = n.process.equipment.splitter.Splitter("10-TEE-LIQ-001", upstream_vessel.getLiquidOutStream(), 2)
            # Initialize both valve inlet property states from the physical liquid takeoff.
            # The closed BDV publishes its own zero demand before the first vessel step.
            branch.setSplitFactors([.5, .5])
            branch.run()
            branch.setTransientSplitMode(branch.TransientSplitMode.DOWNSTREAM_DEMAND_ALWAYS)
            refresh = study.BranchStateRefresh(branch)
            refresh.setName("V100-LIQUID-BRANCH-REFRESH")
            liquid_valve.setInletStream(branch.getSplitStream(0))
            process.getUnitOperations().remove(liquid_valve)
            before_vessel = process.getUnitOperations().indexOf(upstream_vessel)
            process.add(before_vessel, refresh)
            process.add(before_vessel + 1, liquid_valve)
            process.add(before_vessel + 2, branch)
            bdv_feed = branch.getSplitStream(1)
        else:
            branch = tee
            tee.setSplitNumber(3)
            pcv.setInletStream(tee.getSplitStream(0))
            psv.setInletStream(tee.getSplitStream(1))
            bdv_feed = tee.getSplitStream(2)
        bdv = n.process.equipment.valve.ThrottlingValve("10-BDV-001" if case == "wet" else "20-BDV-001", bdv_feed)
        bdv.setOutletPressure(STATIC_BARA, "bara")
        bdv.setCv(8.)
        bdv.setAllowChoked(True)
        bdv.setPercentValveOpening(0.)
        bdv.setCalculateSteadyState(False)
        process.add(process.getUnitOperations().indexOf(branch), bdv)
        if case == "bdv":
            psv.setLiftMotionAvailable(False)
    uuid = jpype.JClass("java.util.UUID")
    rows = []
    for i in range(round(duration / dt)):
        start = i * dt
        if i == round(1. / dt):
            pcv.getController().setActive(False)
            pcv.setPercentValveOpening(0.)
        if bdv is not None and i == round((5. if case == "delayed" else 1.) / dt):
            bdv.setPercentValveOpening(100.)
        backpressure = STATIC_BARA if fixed_boundary else header.fluid.getPressure("bara")
        psv.setOutletPressure(backpressure, "bara")
        if bdv is not None:
            bdv.setOutletPressure(backpressure, "bara")
        process.runTransient(dt, uuid(0, i + 1))
        relief = psv.getOutletStream()
        feeds = [relief] if bdv is None else [relief, bdv.getOutletStream()]
        row = {"end_s": start + dt, "vessel_bara": vessel.getPressure("bara"), "flange_bara": backpressure,
               "lift_fraction": psv.getOpenFraction(), "psv_kg_s": relief.getFlowRate("kg/sec"),
               "bdv_kg_s": 0. if bdv is None else feeds[1].getFlowRate("kg/sec")}
        row["valve_flange_bara"] = relief.getPressure("bara")
        row["bdv_flange_bara"] = None if bdv is None else feeds[1].getPressure("bara")
        row["gas_tee_snapshot_difference_kg_s"] = tee.getMassBalance("kg/sec")
        for tag in ("10-V-100", "20-V-101"):
            source_vessel = process.getUnit(tag)
            row[tag + "_mass_kg"] = source_vessel.getThermoSystem().getMass("kg")
            row[tag + "_K"] = source_vessel.getThermoSystem().getTemperature("K")
        row.update(header.advance(feeds, dt))
        rows.append(row)
    peaks = {}
    for key in ("psv_kg_s", "bdv_kg_s", "tip_kg_s", "vessel_bara", "header_bara", "condensate_kg"):
        peak = max(rows, key=lambda r: r[key])
        peaks[key] = {"value": peak[key], "end_s": peak["end_s"] if peak[key] else None}
    actual = component_moles(header.fluid)
    component_residuals = {name: actual[name] - expected for name, expected in header.expected_moles.items()}
    vessel_energy = {tag: process.getUnit(tag).getLastTransientEnergyBalanceReport()
                     for tag in ("10-V-100", "20-V-101")}
    vessel_ledger = {tag: {"complete": bool(r.isComplete()), "initial_J": r.getInitialEnergyJ(),
                           "net_boundary_energy_J": r.getNetIntegratedEnergyJ(),
                           "expected_J": r.getExpectedEnergyJ(), "published_J": r.getPublishedEnergyJ(),
                           "final_debt_J": r.getCumulativeDebtJ(), "max_abs_debt_J": r.getMaximumAbsoluteCumulativeDebtJ(),
                           "signed_local_vu_defects_J": r.getCumulativeSignedLocalVuDefectJ(),
                           "max_absolute_local_vu_defect_J": r.getMaximumAbsoluteLocalVuDefectJ()}
                     for tag, r in vessel_energy.items()}
    criteria = {
        "source_vessel_energy": all(r.isComplete() and r.getMaximumRelativeCumulativeResidual()
                                    <= settings.get("relativeEnergyLimit") for r in vessel_energy.values()),
        "header_component_closure": max(r["max_component_residual_mol"] for r in rows) <= 1e-8,
        "header_mass_closure": max(abs(r["mass_residual_kg"]) for r in rows) <= 1e-8,
        "header_energy_closure": max(r["relative_energy_residual"] for r in rows) <= settings.get("relativeEnergyLimit"),
        "header_volume_closure": max(abs(r["volume_residual_m3"]) for r in rows) / VOLUME_M3 <= 1e-5,
        "valve_flange_feedback": all(abs(r["valve_flange_bara"] - r["flange_bara"]) <= 1e-8
                                     and (r["bdv_flange_bara"] is None or
                                          abs(r["bdv_flange_bara"] - r["flange_bara"]) <= 1e-8) for r in rows),
        "discharge_observed": (case == "bdv") == (peaks["psv_kg_s"]["value"] == 0.)
                              and (case == "psv") == (peaks["bdv_kg_s"]["value"] == 0.),
    }
    if case == "wet":
        criteria["wet_relief_and_drain"] = (max(r["relief_liquid_kg_s"] for r in rows) > 0.
                                            and peaks["condensate_kg"]["value"] > 0. and header.drained > 0.)
    return {"numerical_checks": criteria, "numerical_status": "PASS" if all(criteria.values()) else "HOLD",
            "engineering_qualification": "HOLD", "vessel_energy_policy": vessel_energy_policy,
            "vessel_energy_ledger": vessel_ledger, "header_component_residuals_mol": component_residuals,
            "peaks": peaks, "case": case, "dt_s": dt, "fixed_boundary": fixed_boundary,
            "header_volume_m3": VOLUME_M3, "tip_static_bara": STATIC_BARA,
            "tip_rated_kg_s": TIP_RATED_KG_S, "tip_rated_rise_bar": TIP_RATED_RISE_BAR,
            "drained_kg": header.drained, "drum_area_m2": DRUM_AREA_M2, "drain_time_s": DRAIN_TIME_S,
            "admitted_kg": header.admitted, "discharged_kg": header.discharged,
            "vessel_relative_energy_residuals": {tag: process.getUnit(tag).getLastTransientEnergyBalanceReport()
                .getMaximumRelativeCumulativeResidual() for tag in ("10-V-100", "20-V-101")},
            "rows": rows}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=["psv", "bdv", "simultaneous", "delayed", "wet"], required=True)
    parser.add_argument("--dt", type=float, choices=[.0125, .025, .05, .1], default=.1)
    parser.add_argument("--duration", type=int, default=20)
    parser.add_argument("--fixed-boundary", action="store_true")
    parser.add_argument("--vessel-energy", choices=["reference", "conservative"], default="conservative",
                        help="Energy target for each source vessel. Reference reproduces the original benchmark.")
    parser.add_argument("--revision", required=True, help="Revision of the compiled NeqSim classpath")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.duration <= (5 if args.case == "delayed" else 1):
        parser.error("Duration must include the initiating event and discharge interval")
    jpype.startJVM("-XX:-UsePerfData", "-Xmx600m", "-XX:ActiveProcessorCount=2", convertStrings=True,
                   classpath=os.environ["NEQSIM_CLASSPATH"].split(os.pathsep))
    result = run(jpype.JPackage("neqsim"), args.case, args.dt, args.duration, args.fixed_boundary, args.vessel_energy)
    result["source_revision"] = args.revision
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(result, output, allow_nan=False)
        output.write("\n")
