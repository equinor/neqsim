"""Synthetic gas-processing example for ``neqsim process-report`` (public fixture, no real facility).

Three-stage separation (HP, MP, LP), LP and mid-pressure recompression with intercooling and scrubbers, export
compression and a scrubber-liquid recycle to the LP separator. Two areas in one ``ProcessModel``.

Contract used by the tool: ``build(case)`` returns a ``ProcessModel`` (or ``ProcessSystem``); ``case`` is
``{"id": ..., "label": ..., "parameters": {...}}``. Operating-case overrides given in cases.yaml are applied
afterwards through ``getAutomation()`` and the model is run.
"""
from jneqsim import neqsim


def _fluid(temperature_c):
    fluid = neqsim.thermo.system.SystemSrkEos(273.15 + temperature_c, 60.0)
    for name, amount in (("nitrogen", 0.004), ("CO2", 0.02), ("methane", 0.62), ("ethane", 0.07), ("propane", 0.05),
                         ("i-butane", 0.01), ("n-butane", 0.02), ("n-pentane", 0.012), ("n-hexane", 0.02),
                         ("water", 0.12)):
        fluid.addComponent(name, amount)
    fluid.addTBPfraction("C7", 0.04, 0.100, 0.760)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)
    return fluid


def build(case=None):
    """Build the flowsheet for one operating case and return the (run) ProcessModel."""
    params = (case or {}).get("parameters") or {}
    t_feed = float(params.get("feed_temperature_c", 45.0))
    fluid = _fluid(t_feed)
    equipment = neqsim.process.equipment
    feed = equipment.stream.Stream("feed", fluid)
    feed.setFlowRate(60000.0, "kg/hr")
    feed.setTemperature(t_feed, "C")
    feed.setPressure(60.0, "bara")

    hp_sep = equipment.separator.ThreePhaseSeparator("HP separator", feed)
    hp_valve = equipment.valve.ThrottlingValve("HP oil valve", hp_sep.getOilOutStream())
    hp_valve.setOutletPressure(18.0)
    mp_sep = equipment.separator.ThreePhaseSeparator("MP separator", hp_valve.getOutletStream())
    mp_valve = equipment.valve.ThrottlingValve("MP oil valve", mp_sep.getOilOutStream())
    mp_valve.setOutletPressure(4.0)

    seed = equipment.stream.Stream("scrubber liquid return", fluid.clone())
    seed.setFlowRate(1.0e-3, "kg/hr")
    lp_mixer = equipment.mixer.Mixer("LP inlet mixer")
    lp_mixer.addStream(mp_valve.getOutletStream())
    lp_mixer.addStream(seed)
    lp_sep = equipment.separator.Separator("LP separator", lp_mixer.getOutletStream())

    area_sep = neqsim.process.processmodel.ProcessSystem()
    for unit in (feed, hp_sep, hp_valve, mp_sep, mp_valve, seed, lp_mixer, lp_sep):
        area_sep.add(unit)

    lp_comp = equipment.compressor.Compressor("LP compressor", lp_sep.getGasOutStream())
    lp_comp.setOutletPressure(18.0)
    lp_comp.setIsentropicEfficiency(0.75)
    lp_cooler = equipment.heatexchanger.Cooler("LP cooler", lp_comp.getOutletStream())
    lp_cooler.setOutTemperature(273.15 + 35.0)
    lp_scrubber = equipment.separator.Separator("LP scrubber", lp_cooler.getOutletStream())
    scrub_valve = equipment.valve.ThrottlingValve("scrubber liquid valve", lp_scrubber.getLiquidOutStream())
    scrub_valve.setOutletPressure(4.0)
    recycle = equipment.util.Recycle("scrubber liquid recycle")
    recycle.addStream(scrub_valve.getOutletStream())
    recycle.setOutletStream(seed)

    mp_mixer = equipment.mixer.Mixer("MP gas mixer")
    mp_mixer.addStream(mp_sep.getGasOutStream())
    mp_mixer.addStream(lp_scrubber.getGasOutStream())
    mp_comp = equipment.compressor.Compressor("MP compressor", mp_mixer.getOutletStream())
    mp_comp.setOutletPressure(60.0)
    mp_comp.setIsentropicEfficiency(0.76)
    inter_cooler = equipment.heatexchanger.Cooler("intercooler", mp_comp.getOutletStream())
    inter_cooler.setOutTemperature(273.15 + 38.0)
    inter_scrubber = equipment.separator.Separator("intercooler scrubber", inter_cooler.getOutletStream())
    hp_mixer = equipment.mixer.Mixer("HP gas mixer")
    hp_mixer.addStream(hp_sep.getGasOutStream())
    hp_mixer.addStream(inter_scrubber.getGasOutStream())
    export_comp = equipment.compressor.Compressor("export compressor", hp_mixer.getOutletStream())
    export_comp.setOutletPressure(120.0)
    export_comp.setIsentropicEfficiency(0.77)
    after_cooler = equipment.heatexchanger.Cooler("export cooler", export_comp.getOutletStream())
    after_cooler.setOutTemperature(273.15 + 40.0)

    area_comp = neqsim.process.processmodel.ProcessSystem()
    for unit in (lp_comp, lp_cooler, lp_scrubber, scrub_valve, recycle, mp_mixer, mp_comp, inter_cooler, inter_scrubber,
                 hp_mixer, export_comp, after_cooler):
        area_comp.add(unit)

    plant = neqsim.process.processmodel.ProcessModel()
    plant.add("separation", area_sep)
    plant.add("compression", area_comp)
    plant.run()
    return plant
