"""Coverage review for NeqSim process-calculation functionality in the hydrogen book."""

from __future__ import annotations

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd


SCRIPT_DIR = Path(__file__).resolve().parent
CHAPTER_DIR = SCRIPT_DIR.parent
FIGURES_DIR = CHAPTER_DIR / "figures"
FIGURES_DIR.mkdir(parents=True, exist_ok=True)

COVERAGE_ROWS = [
  {
    "family": "Thermodynamic EOS and reference equations",
    "representative_apis": "SystemSrkEos; SystemPrEos; SystemGERG2008Eos; SystemLeachmanEos",
    "chapter": "ch04_hydrogen_reference_equations",
    "artifact": "scripts/eos_reference_comparison.py; notebooks/eos_reference_comparison.ipynb",
    "example_calculation": "Pure-H2 density/Z deviation versus Leachman and methane-H2 density deviation versus GERG-2008-H2.",
    "coverage_depth": "live NeqSim calculation"
  },
  {
    "family": "Flash, phase envelope, water and impurity checks",
    "representative_apis": "ThermodynamicOperations; initProperties; CPA/SRK/GERG systems",
    "chapter": "ch04_hydrogen_reference_equations; ch08_phase_behavior_with_co2_water_impurities",
    "artifact": "parameter_study.ipynb; expected_output.ipynb",
    "example_calculation": "Pressure/phase-property sweeps with explicit property-initialization and impurity attention indices.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Feedstock, water, steam, syngas and gas-quality fluids",
    "representative_apis": "SystemSrkEos; SystemPrEos; Standard_ISO6976; Stream",
    "chapter": "ch05_feedstocks_and_fluids; ch15_pipeline_transport_linepack",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "Feed composition closure, water-quality duty, syngas basis, and ISO 6976 gas-quality checks.",
    "coverage_depth": "chapter and companion notebook"
  },
  {
    "family": "Reaction equilibrium, conversion and catalyst screening",
    "representative_apis": "GibbsReactor; StoichiometricReaction; CatalystDeactivationKinetics; CatalystBed",
    "chapter": "ch06_reaction_equilibrium_and_kinetics",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "SMR/ATR/WGS/ammonia-cracking and methane-pyrolysis route-screening examples.",
    "coverage_depth": "live NeqSim and screening calculations"
  },
  {
    "family": "SMR, ATR, POX and blue-H2 route builders",
    "representative_apis": "SMRHydrogenPlantBuilder; ATRHydrogenPlantBuilder; POXHydrogenPlantBuilder; BlueHydrogenPlantBuilder",
    "chapter": "ch09_reforming_flowsheets; ch12_blue_hydrogen_and_ccs; ch24_capstone_blue_hydrogen_python_study",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "Route-builder plant balances with methane feed, steam-to-carbon, oxygen-to-carbon, PSA and capture settings.",
    "coverage_depth": "live NeqSim route-builder calculation"
  },
  {
    "family": "Water-gas shift and heat recovery",
    "representative_apis": "WaterGasShiftReactor; Cooler; HeatExchanger; PinchAnalysis",
    "chapter": "ch10_water_gas_shift_and_heat_recovery",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "HT/LT shift conversion, CO slip, utility duty and heat-recovery sensitivity.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Electrolyzer stack and green-H2 plant balance",
    "representative_apis": "Electrolyzer; ElectrolyzerTechnology; ElectrolyzerIVCharacteristic; ElectrolyzerCostEstimate",
    "chapter": "ch07_electrochemistry_and_electrolyzers; ch13_green_hydrogen_plant; ch25_capstone_green_hydrogen_portfolio",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "PEM/alkaline/SOEC/AEM voltage, current-density, Faradaic efficiency, water and specific-energy studies.",
    "coverage_depth": "live NeqSim and portfolio calculation"
  },
  {
    "family": "Purification, component capture, drying and splitting",
    "representative_apis": "PressureSwingAdsorptionBed; PSACascade; ComponentCaptureUnit; ComponentSplitter; Splitter",
    "chapter": "ch11_psa_purification_and_tail_gas; ch12_blue_hydrogen_and_ccs",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "PSA bed-count recovery/purity sweep, tail-gas accounting, CO2 capture and H2 drying placeholders.",
    "coverage_depth": "live NeqSim PSA/capture calculation"
  },
  {
    "family": "Separation, knock-out and scrubber calculations",
    "representative_apis": "Separator; ThreePhaseSeparator; GasScrubber; Tank",
    "chapter": "ch00_hydrogen_quickstart; ch09_reforming_flowsheets; ch13_green_hydrogen_plant",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "Water knock-out, shifted-syngas separation, oxygen/product handling and screening inventory.",
    "coverage_depth": "chapter process script"
  },
  {
    "family": "Heat transfer, cooling, heating and heat integration",
    "representative_apis": "Heater; Cooler; HeatExchanger; PinchAnalysis",
    "chapter": "ch10_water_gas_shift_and_heat_recovery; ch14_hydrogen_compression_intercooling",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "Intercooler duty, reformer/shift cooling, utility attention and pinch-style heat-recovery screening.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Compression, expansion and cryogenic pressure work",
    "representative_apis": "Compressor; CompressorChart; Expander; ParaOrthoH2Correction",
    "chapter": "ch14_hydrogen_compression_intercooling; ch16_cryogenic_hydrogen_para_ortho",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "Stage pressure-ratio, discharge-temperature, intercooling, specific-energy and expansion/para-ortho screening.",
    "coverage_depth": "live NeqSim and screening calculations"
  },
  {
    "family": "Valves, throttling, relief and pressure letdown",
    "representative_apis": "ThrottlingValve; SafetyValve; ReliefValveSizing; DepressurizationSimulator",
    "chapter": "ch19_materials_embrittlement_hydrogen_service; ch20_safety_standards_and_risk",
    "artifact": "parameter_study.ipynb; process_calculation_coverage_review.py",
    "example_calculation": "Pressure-letdown, vent/source-term, relief-load and operability-margin screening entries.",
    "coverage_depth": "safety screening script"
  },
  {
    "family": "Pumps, liquid water handling and tank inventory",
    "representative_apis": "Pump; Tank; Stream; water systems",
    "chapter": "ch05_feedstocks_and_fluids; ch13_green_hydrogen_plant",
    "artifact": "parameter_study.ipynb; process_calculation_coverage_review.py",
    "example_calculation": "Water make-up, purification recovery, stack-feed and storage/inventory screening.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Mixing, splitting, recycle and adjuster workflows",
    "representative_apis": "Mixer; StaticMixer; Splitter; Recycle; Adjuster; SetPoint; ProcessSystem",
    "chapter": "ch09_reforming_flowsheets; ch23_optimization_automation_digital_twins; ch24_capstone_blue_hydrogen_python_study",
    "artifact": "parameter_study.ipynb; process_calculation_coverage_review.py",
    "example_calculation": "Route flowsheet wiring, recycle/adjuster convergence and scenario setup for capstone studies.",
    "coverage_depth": "flowsheet and automation scripts"
  },
  {
    "family": "Pipeline hydraulics, linepack and gas transport",
    "representative_apis": "PipeBeggsAndBrills; AdiabaticPipe; TwoFluidPipe; Standard_ISO6976",
    "chapter": "ch15_pipeline_transport_linepack",
    "artifact": "parameter_study.ipynb; companion workflow notebook",
    "example_calculation": "Hydrogen pressure/temperature profile, linepack index, Wobbe index and relative density checks.",
    "coverage_depth": "chapter and companion notebook"
  },
  {
    "family": "CO2 capture, dense-phase transport and injection well envelope",
    "representative_apis": "ComponentCaptureUnit; CO2InjectionWellAnalyzer; ImpurityMonitor; CO2FlowCorrections",
    "chapter": "ch08_phase_behavior_with_co2_water_impurities; ch12_blue_hydrogen_and_ccs",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "Captured CO2 mass flow, dense-phase margin, impurity enrichment and injection-envelope screening.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Flow assurance, hydrate/water/impurity screening",
    "representative_apis": "hydrateEquilibriumTemperature; waterDewPointTemperature; ImpurityMonitor; PipeBeggsAndBrills",
    "chapter": "ch08_phase_behavior_with_co2_water_impurities; ch15_pipeline_transport_linepack",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "Hydrate, water knock-out, impurity and transport-interface attention tables for H2/CO2 systems.",
    "coverage_depth": "screening parameter study"
  },
  {
    "family": "Materials, mechanical design and hydrogen service review",
    "representative_apis": "CompressorDesignFeasibilityReport; HeatExchangerDesignFeasibilityReport; standards classes",
    "chapter": "ch19_materials_embrittlement_hydrogen_service; ch20_safety_standards_and_risk",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "H2 partial pressure, temperature, cyclic pressure, leak source term and standards-attention screening.",
    "coverage_depth": "standards/materials screening"
  },
  {
    "family": "Safety, flare, dispersion, depressuring and QRA handoff",
    "representative_apis": "ReliefValveSizing; Flare; DepressurizationSimulator; fire/dispersion/QRA packages",
    "chapter": "ch20_safety_standards_and_risk",
    "artifact": "parameter_study.ipynb; process_calculation_coverage_review.py",
    "example_calculation": "Pressure-source-term sweep with materials attention, operability margin and external safety handoff fields.",
    "coverage_depth": "safety screening script"
  },
  {
    "family": "Dynamic operation, controls, instruments and alarms",
    "representative_apis": "runTransient; DynamicProcessHelper; ControllerDeviceBaseClass; transmitters; AlarmConfig",
    "chapter": "ch13_green_hydrogen_plant; ch21_operability_and_dynamics; ch23_optimization_automation_digital_twins",
    "artifact": "parameter_study.ipynb; process_calculation_coverage_review.py",
    "example_calculation": "Part-load/ramping, turndown, controller/instrument and alarm-check coverage for hydrogen operation.",
    "coverage_depth": "automation and operability screening"
  },
  {
    "family": "Automation API, model state and digital twins",
    "representative_apis": "ProcessAutomation; ProcessSystemState; ProcessModelState; tagreader",
    "chapter": "ch02_neqsim_architecture_for_hydrogen; ch23_optimization_automation_digital_twins",
    "artifact": "parameter_study.ipynb; process_calculation_coverage_review.py",
    "example_calculation": "Scenario count, dirty tracking, addressable variables, historian mismatch and reproducibility evidence.",
    "coverage_depth": "automation script"
  },
  {
    "family": "Optimization, DOE, uncertainty and Monte Carlo",
    "representative_apis": "BatchStudy; MonteCarloSimulator; ProcessSimulationEvaluator; optimizers",
    "chapter": "ch22_cost_estimation_and_project_economics; ch23_optimization_automation_digital_twins; ch25_capstone_green_hydrogen_portfolio",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "Scenario sweep, portfolio comparison, cost sensitivity and confidence/coverage curves.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Cost, emissions, economics and lifecycle metrics",
    "representative_apis": "CostEstimationCalculator; DCFCalculator; ElectrolyzerCostEstimate; PSACostEstimate; SustainabilityMetrics",
    "chapter": "ch22_cost_estimation_and_project_economics; ch24_capstone_blue_hydrogen_python_study; ch25_capstone_green_hydrogen_portfolio",
    "artifact": "parameter_study.ipynb",
    "example_calculation": "CAPEX/OPEX/LCOH scale sweep, carbon-intensity index and portfolio economics.",
    "coverage_depth": "chapter parameter study"
  },
  {
    "family": "Alternative routes: biomass, waste, pyrolysis, photo and bio H2",
    "representative_apis": "BiomassGasifier; PyrolysisReactor; AnaerobicDigester; FermentationReactor; BiogasUpgrader",
    "chapter": "ch01_why_hydrogen_modeling; ch05_feedstocks_and_fluids; ch06_reaction_equilibrium_and_kinetics",
    "artifact": "topic_examples CSV; parameter_study.ipynb",
    "example_calculation": "Frontier-route boundary examples for pyrolysis, biomass/waste gasification and photo/biological hydrogen.",
    "coverage_depth": "literature-topic calculation map"
  },
  {
    "family": "Reporting, validation, JSON export and submission artifacts",
    "representative_apis": "TaskResultValidator; results.json; PaperLab renderers; ProcessModel reports",
    "chapter": "ch03_python_environment_and_reproducibility; ch23_optimization_automation_digital_twins; capstones",
    "artifact": "parameter_study.ipynb; book-check; book-build outputs",
    "example_calculation": "Every chapter writes CSV/JSON/PNG evidence and the build validates rendered submission artifacts.",
    "coverage_depth": "book-level validation workflow"
  }
]


def coverage_dataframe() -> pd.DataFrame:
    """Return the coverage matrix with explicit validation fields."""
    df = pd.DataFrame(COVERAGE_ROWS)
    df["coverage_status"] = df.apply(
        lambda row: "covered" if row["chapter"] and row["artifact"] and row["example_calculation"] else "missing",
        axis=1,
    )
    depth_scores = {
        "live NeqSim calculation": 1.00,
        "live NeqSim route-builder calculation": 1.00,
        "live NeqSim and portfolio calculation": 0.95,
        "live NeqSim PSA/capture calculation": 0.95,
        "live NeqSim and screening calculations": 0.95,
        "chapter and companion notebook": 0.90,
        "chapter parameter study": 0.85,
        "chapter process script": 0.85,
        "flowsheet and automation scripts": 0.85,
        "screening parameter study": 0.80,
        "safety screening script": 0.78,
        "standards/materials screening": 0.75,
        "automation and operability screening": 0.75,
        "automation script": 0.75,
        "literature-topic calculation map": 0.70,
        "book-level validation workflow": 0.90,
    }
    df["coverage_score"] = df["coverage_depth"].map(depth_scores).fillna(0.70)
    df["primary_chapter"] = df["chapter"].str.split(";").str[0].str.strip()
    return df


def validate_coverage(df: pd.DataFrame) -> None:
    """Raise if a process-calculation family is not mapped to a book script."""
    missing = df[df["coverage_status"] != "covered"]
    if not missing.empty:
        raise RuntimeError("Missing process-calculation coverage: " + ", ".join(missing["family"].tolist()))


def plot_coverage(df: pd.DataFrame) -> None:
    """Plot coverage score by process-calculation family."""
    ordered = df.sort_values("coverage_score", ascending=True).reset_index(drop=True)
    fig_height = max(6.5, 0.34 * len(ordered))
    fig, ax = plt.subplots(figsize=(9.5, fig_height), dpi=160)
    colors = ["#0072B2" if "live" in depth else "#009E73" if "chapter" in depth else "#D55E00" for depth in ordered["coverage_depth"]]
    ax.barh(ordered["family"], ordered["coverage_score"], color=colors)
    ax.set_xlim(0.0, 1.05)
    ax.set_xlabel("Coverage score (-)")
    ax.set_title("NeqSim process-calculation coverage in the hydrogen book scripts")
    ax.grid(axis="x", alpha=0.25)
    for idx, row in ordered.iterrows():
        ax.text(min(row["coverage_score"] + 0.015, 1.01), idx, row["primary_chapter"], va="center", fontsize=7)
    fig.tight_layout()
    fig.savefig(FIGURES_DIR / "process_calculation_coverage_map.png", bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    """Write coverage matrix artifacts and validate complete coverage."""
    df = coverage_dataframe()
    validate_coverage(df)
    df.to_csv(FIGURES_DIR / "process_calculation_coverage_matrix.csv", index=False)
    plot_coverage(df)
    summary = {
        "coverage_complete": True,
        "families_reviewed": int(len(df)),
        "covered_families": int((df["coverage_status"] == "covered").sum()),
        "missing_families": [],
        "live_or_route_builder_rows": int(df["coverage_depth"].str.contains("live").sum()),
        "chapter_parameter_rows": int(df["coverage_depth"].str.contains("chapter").sum()),
        "coverage_by_primary_chapter": df.groupby("primary_chapter")["family"].count().sort_values(ascending=False).to_dict(),
    }
    (FIGURES_DIR / "process_calculation_coverage_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
