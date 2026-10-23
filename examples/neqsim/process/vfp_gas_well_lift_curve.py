"""Generate a VFPPROD lift curve for a synthetic deviated gas well with NeqSim.

Uses the workspace classes through devtools/neqsim_dev_setup.py. Run with the NeqSim interpreter, for example:

    C:/appl/neqsim-venv/Scripts/python.exe examples/neqsim/process/vfp_gas_well_lift_curve.py

The Java API is described in docs/process/optimization/vfp-lift-curve-builder.md and the method guide is the skill
neqsim-vfp-lift-curves. The well, fluid and axes below are synthetic.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init  # noqa: E402

ns = neqsim_init(project_root=ROOT, recompile=False, verbose=False)
import jpype  # noqa: E402

SystemSrkEos = ns.JClass("neqsim.thermo.system.SystemSrkEos")
TubingPerformance = ns.JClass("neqsim.process.equipment.pipeline.TubingPerformance")
RecombinationFluidFactory = ns.JClass("neqsim.process.fielddevelopment.reservoir.RecombinationFluidFactory")
TubingVlpProvider = ns.JClass("neqsim.process.fielddevelopment.reservoir.TubingVlpProvider")
VfpTableBuilder = ns.JClass("neqsim.process.fielddevelopment.reservoir.VfpTableBuilder")
RateMonotoneMode = ns.JClass("neqsim.process.fielddevelopment.reservoir.VfpTableBuilder$RateMonotoneMode")
VfpDeckReader = ns.JClass("neqsim.process.fielddevelopment.reservoir.VfpDeckReader")


def fluid(temperature_k, pressure_bara, components):
    system = SystemSrkEos(temperature_k, pressure_bara)
    for name, amount in components:
        system.addComponent(name, amount)
    return system


template = fluid(358.15, 200.0, [("methane", 0.88), ("ethane", 0.06), ("propane", 0.03), ("n-butane", 0.015),
                                  ("n-hexane", 0.01), ("n-octane", 0.005), ("water", 1.0e-6)])
template.setMixingRule("classic")
template.setMultiPhaseCheck(True)
surface_gas = fluid(288.15, 1.01325, [("methane", 0.90), ("ethane", 0.06), ("propane", 0.03), ("n-butane", 0.01)])
surface_gas.setMixingRule("classic")
condensate = fluid(288.15, 1.01325, [("n-hexane", 0.6), ("n-octane", 0.4)])
condensate.setMixingRule("classic")

well_stream = (RecombinationFluidFactory(template, surface_gas, condensate)
               .setRateBasis("GAS").setWaterRatioType("WGR").setGasRatioType("OGR"))
md = jpype.JArray(jpype.JDouble)([0.0, 1500.0, 3200.0])
tvd = jpype.JArray(jpype.JDouble)([0.0, 1450.0, 2500.0])
inner_diameter = jpype.JArray(jpype.JDouble)([0.15, 0.15])
vlp = (TubingVlpProvider(well_stream, md, tvd, inner_diameter).setTemperatures(95.0, 40.0)
       .setNumberOfSegments(6).setCorrelation(TubingPerformance.CorrelationType.GRAY))

builder = (VfpTableBuilder.gasWell(1).setDatumDepth(2500.0)
           .setRates(VfpTableBuilder.rateAxis(0.2e6, 1.0e6, 5, 1.2))
           .setOutletPressures(VfpTableBuilder.pressureAxis(40.0, 80.0, 4, 0.1, 0.1))
           .setWaterRatios(jpype.JArray(jpype.JDouble)([1.0e-6, 1.0e-4]))
           .setGasRatios(jpype.JArray(jpype.JDouble)([1.0e-5, 1.0e-4]))
           .setRateMonotoneMode(RateMonotoneMode.FULL)
           .addProvenance("Gray tubing survey, synthetic well"))
result = builder.build(vlp)

deck = str(result.toDeckString())
parsed = VfpDeckReader.parse(deck)
print("warnings:", [str(w) for w in result.getWarnings()])
print("repairs:", int(result.getRepairCount()), "tables axes:", [len(a) for a in parsed.getAxes()])
out = pathlib.Path(__file__).with_name("vfp1.inc")
out.write_text(deck, encoding="utf-8")
print("wrote", out)
