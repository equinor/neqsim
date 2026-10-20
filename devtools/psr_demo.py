"""Synthetic demonstration flowsheet for the process-study-report toolkit tests.

Generic, public fixture: wet gas feed, inlet mixer, three-phase separator,
compressor, cooler, scrubber and a liquid recycle through a valve. Nothing in
here comes from a real facility.
"""


def build_demo_model(feed_kg_h=20000.0, compressor_outlet_bara=90.0):
    """Build, run and return a two-area ``ProcessModel`` (SRK, classic mixing rule).

    Args:
        feed_kg_h: feed mass flow in kg/h.
        compressor_outlet_bara: compressor discharge pressure in bara.

    Returns:
        A solved ``neqsim.process.processmodel.ProcessModel`` with the areas
        ``separation`` and ``compression``.
    """
    from jneqsim import neqsim

    fluid = neqsim.thermo.system.SystemSrkEos(273.15 + 45.0, 40.0)
    for name, amount in (("nitrogen", 0.005), ("CO2", 0.02), ("methane", 0.72), ("ethane", 0.08),
                         ("propane", 0.05), ("i-butane", 0.01), ("n-butane", 0.02), ("n-pentane", 0.01),
                         ("n-hexane", 0.01), ("water", 0.05)):
        fluid.addComponent(name, amount)
    fluid.addTBPfraction("C7", 0.015, 0.100, 0.760)
    fluid.addTBPfraction("C9", 0.010, 0.135, 0.790)
    fluid.setMixingRule("classic")
    fluid.setMultiPhaseCheck(True)

    stream = neqsim.process.equipment.stream.Stream
    feed = stream("feed", fluid)
    feed.setFlowRate(feed_kg_h, "kg/hr")
    feed.setTemperature(45.0, "C")
    feed.setPressure(40.0, "bara")

    seed = stream("recycle seed", fluid.clone())
    seed.setFlowRate(1.0e-3, "kg/hr")

    mixer = neqsim.process.equipment.mixer.Mixer("inlet mixer")
    mixer.addStream(feed)
    mixer.addStream(seed)

    sep = neqsim.process.equipment.separator.ThreePhaseSeparator("HP separator", mixer.getOutletStream())

    comp = neqsim.process.equipment.compressor.Compressor("export compressor", sep.getGasOutStream())
    comp.setOutletPressure(compressor_outlet_bara)
    comp.setIsentropicEfficiency(0.75)

    cooler = neqsim.process.equipment.heatexchanger.Cooler("after cooler", comp.getOutletStream())
    cooler.setOutTemperature(273.15 + 35.0)

    scrubber = neqsim.process.equipment.separator.Separator("export scrubber", cooler.getOutletStream())

    valve = neqsim.process.equipment.valve.ThrottlingValve("scrubber liquid valve", scrubber.getLiquidOutStream())
    valve.setOutletPressure(40.0)

    recycle = neqsim.process.equipment.util.Recycle("liquid recycle")
    recycle.addStream(valve.getOutletStream())
    recycle.setOutletStream(seed)

    area1 = neqsim.process.processmodel.ProcessSystem()
    for unit in (feed, seed, mixer, sep):
        area1.add(unit)
    area2 = neqsim.process.processmodel.ProcessSystem()
    for unit in (comp, cooler, scrubber, valve, recycle):
        area2.add(unit)
    model = neqsim.process.processmodel.ProcessModel()
    model.add("separation", area1)
    model.add("compression", area2)
    model.run()
    return model
