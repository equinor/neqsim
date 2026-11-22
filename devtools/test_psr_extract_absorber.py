"""Test: absorber-family units (SimpleTEGAbsorber, WaterStripperColumn) expose their ports to the topology extractor.

Needs the NeqSim JVM (installed ``jneqsim`` package); skipped when it is not available.
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

jneqsim = pytest.importorskip("jneqsim")


def _fluid(neqsim):
    f = neqsim.thermo.system.SystemSrkCPAstatoil(298.15, 60.0)
    f.addComponent("methane", 0.95)
    f.addComponent("ethane", 0.05)
    f.addComponent("water", 0.0)
    f.addComponent("TEG", 0.0)
    f.setMixingRule(10)
    return f


def test_tegabsorber_ports_are_discovered():
    import psr_extract as pe

    neqsim = jneqsim.neqsim
    ps = neqsim.process.processmodel.ProcessSystem()
    gas = neqsim.process.equipment.stream.Stream("gas", _fluid(neqsim))
    gas.setFlowRate(1.0e5, "kg/hr")
    gas.setTemperature(25.0, "C")
    gas.setPressure(60.0, "bara")
    ps.add(gas)
    lean_fluid = _fluid(neqsim)
    lean_fluid.setMolarComposition([0.0, 0.0, 0.0005, 0.9995])
    lean = neqsim.process.equipment.stream.Stream("lean", lean_fluid)
    lean.setFlowRate(1000.0, "kg/hr")
    lean.setTemperature(35.0, "C")
    lean.setPressure(60.0, "bara")
    ps.add(lean)
    absorber = neqsim.process.equipment.absorber.SimpleTEGAbsorber("contactor")
    absorber.addGasInStream(gas)
    absorber.addSolventInStream(lean)
    absorber.setNumberOfStages(3)
    absorber.setStageEfficiency(0.7)
    ps.add(absorber)
    units, streams, _areas = pe.discover_topology(ps)
    unit = [u for u in units if u["tag"] == "contactor"][0]
    assert len(unit["inlet_streams"]) == 2
    assert len(unit["outlet_streams"]) == 2
