"""Test: a stream carrying only numerical noise (below 1 ug/h) is reported as zero flow, not as a composition that fails the sum check.

Needs the NeqSim JVM (installed ``jneqsim`` package); skipped when it is not available.
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

jneqsim = pytest.importorskip("jneqsim")


def _stream(neqsim, rate_kg_h):
    fluid = neqsim.thermo.system.SystemSrkEos(298.15, 50.0)
    fluid.addComponent("methane", 0.9)
    fluid.addComponent("ethane", 0.1)
    fluid.setMixingRule("classic")
    stream = neqsim.process.equipment.stream.Stream("s", fluid)
    stream.setFlowRate(rate_kg_h, "kg/hr")
    stream.setTemperature(25.0, "C")
    stream.setPressure(50.0, "bara")
    stream.run()
    return stream


def test_noise_flow_is_zero_flow_and_real_flow_is_not():
    import psr_extract as pe

    neqsim = jneqsim.neqsim
    noise = pe.stream_results(_stream(neqsim, 1.0e-12), [], std_flash=False)
    real = pe.stream_results(_stream(neqsim, 1000.0), [], std_flash=False)
    assert noise["zero_flow"] is True
    assert real["zero_flow"] is False
