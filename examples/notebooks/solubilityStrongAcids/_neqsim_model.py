"""Shared NeqSim setup for the Taleb-Ponche-Mirabel (1996) acid vapour-pressure
validation scripts.

This module boots the JVM against the locally compiled NeqSim classes and exposes
the Java model class ``neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure``
together with a few convenience wrappers used by the figure scripts.

Run ``./mvnw compile`` (or any Maven test) once before using these scripts so that
``target/classes`` contains the compiled model class.
"""
from pathlib import Path
import sys

# examples/notebooks/solubilityStrongAcids/<this file>  ->  repo root is parents[3]
PROJECT_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_init  # noqa: E402

# Unit conversions (NeqSim returns pressures in pascal).
PA_TO_TORR = 1.0 / 133.322368421
PA_TO_MBAR = 1.0 / 100.0

_NS = None
_MODEL = None


def get_model():
    """Start the JVM (once) and return the Java model class object."""
    global _NS, _MODEL
    if _MODEL is None:
        _NS = neqsim_init(project_root=PROJECT_ROOT,
                          recompile=False, verbose=False)
        _MODEL = _NS.JClass(
            "neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure"
        )
    return _MODEL


def mole_fractions(w_water, w_nitric, w_sulfuric):
    """Convert component weight percentages to mole fractions [x1, x2, x3]."""
    model = get_model()
    x = model.moleFractionsFromMassFractions(
        float(w_water), float(w_nitric), float(w_sulfuric)
    )
    return float(x[0]), float(x[1]), float(x[2])


def p_water_torr(w_water, w_nitric, w_sulfuric, temperature):
    """Water partial pressure in torr for the given mass percentages and T [K]."""
    model = get_model()
    x1, x2, x3 = mole_fractions(w_water, w_nitric, w_sulfuric)
    return model.partialPressureWater(x1, x2, x3, float(temperature)) * PA_TO_TORR


def p_nitric_torr(w_water, w_nitric, w_sulfuric, temperature):
    """Nitric acid partial pressure in torr for the given mass percentages and T."""
    model = get_model()
    x1, x2, x3 = mole_fractions(w_water, w_nitric, w_sulfuric)
    return model.partialPressureNitricAcid(x1, x2, x3, float(temperature)) * PA_TO_TORR


def water_activity_from_mole_fraction(x_sulfuric, temperature):
    """Water activity a1 = gamma1 * x1 in the binary water-sulfuric acid system."""
    model = get_model()
    x1 = 1.0 - x_sulfuric
    gamma = model.activityCoefficientWater(
        x1, 0.0, x_sulfuric, float(temperature))
    return gamma * x1


def ternary_panel_compositions(hno3_wt, sulfuric_wt):
    """Return (w_water, w_nitric, w_sulfuric) for a Figure 6/7 panel.

    The panels of Figures 6 and 7 hold the HNO3 weight percent of the *total*
    ternary mixture fixed (the panel label) and sweep the H2SO4 weight percent
    (the x-axis); water makes up the balance. Each curve therefore terminates at
    ``sulfuric_wt = 100 - hno3_wt`` where the water content reaches zero, which is
    why the high-HNO3 panels span a progressively shorter x-range in the paper.

    ``hno3_wt`` is the fixed HNO3 weight percent of the mixture and ``sulfuric_wt``
    is the H2SO4 weight percent (x-axis value).
    """
    w_nitric = hno3_wt
    w_sulfuric = sulfuric_wt
    w_water = 100.0 - w_nitric - w_sulfuric
    return w_water, w_nitric, w_sulfuric
