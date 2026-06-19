"""Fluid helpers for NeqSim Studio.

These helpers turn a plain ``{component: mole_fraction}`` dictionary into a
NeqSim ``SystemInterface`` so newcomers never have to remember the Java EOS
class names or the mandatory ``setMixingRule`` call.

All functions take a ``cls`` callable as their first argument — a function that
resolves a fully-qualified Java class name to a JPype class (typically the
``ProcessBuilderContext.cls`` bound by :func:`neqsim_studio.connect`).  Keeping
the JVM accessor as an explicit argument means this module imports cleanly
without a running JVM, which keeps the parser/gallery/wizard logic unit
testable in plain Python.
"""

from __future__ import annotations

from typing import Callable, Dict, Mapping

# Friendly model name -> fully qualified NeqSim system class.
EOS_MODELS: Dict[str, str] = {
    "srk": "neqsim.thermo.system.SystemSrkEos",
    "pr": "neqsim.thermo.system.SystemPrEos",
    "peng-robinson": "neqsim.thermo.system.SystemPrEos",
    "cpa": "neqsim.thermo.system.SystemSrkCPAstatoil",
    "srk-cpa": "neqsim.thermo.system.SystemSrkCPAstatoil",
    "gerg": "neqsim.thermo.system.SystemGERG2008Eos",
    "gerg2008": "neqsim.thermo.system.SystemGERG2008Eos",
    "umr": "neqsim.thermo.system.SystemUMRPRUMCEos",
}

# A few ready-made compositions (mole fractions) for the most common teaching
# cases.  Values are normalised automatically when the fluid is built.
PRESET_FLUIDS: Dict[str, Dict[str, float]] = {
    "natural_gas": {
        "nitrogen": 0.01,
        "CO2": 0.02,
        "methane": 0.85,
        "ethane": 0.07,
        "propane": 0.03,
        "i-butane": 0.005,
        "n-butane": 0.005,
    },
    "lean_gas": {"nitrogen": 0.02, "methane": 0.94, "ethane": 0.03, "propane": 0.01},
    "rich_gas": {
        "CO2": 0.02,
        "methane": 0.75,
        "ethane": 0.10,
        "propane": 0.07,
        "i-butane": 0.02,
        "n-butane": 0.02,
        "n-pentane": 0.02,
    },
    "wet_gas": {
        "methane": 0.80,
        "ethane": 0.07,
        "propane": 0.04,
        "n-butane": 0.02,
        "water": 0.07,
    },
    "co2_stream": {"CO2": 0.97, "methane": 0.01, "nitrogen": 0.02},
}


def make_fluid(
    cls: Callable[[str], object],
    components: Mapping[str, float],
    temperature_c: float = 15.0,
    pressure_bara: float = 1.01325,
    model: str = "srk",
    mixing_rule: str = "classic",
):
    """Build a NeqSim fluid from a component dictionary.

    Parameters
    ----------
    cls:
        Callable that resolves a fully qualified Java class name to a class
        object (e.g. ``jpype.JClass`` or ``ProcessBuilderContext.cls``).
    components:
        Mapping of NeqSim component name to mole fraction (need not sum to 1;
        the EOS normalises internally).
    temperature_c:
        Initial temperature in degrees Celsius.
    pressure_bara:
        Initial pressure in bara.
    model:
        Friendly EOS name, see :data:`EOS_MODELS`.
    mixing_rule:
        Mixing rule passed to ``setMixingRule`` (``"classic"`` for most gas
        work, a CPA numeric rule for polar/aqueous systems).

    Returns
    -------
    object
        A configured NeqSim ``SystemInterface`` (Java object).

    Raises
    ------
    ValueError
        If ``components`` is empty or ``model`` is unknown.
    """
    if not components:
        raise ValueError("components must contain at least one component")
    model_key = model.strip().lower()
    if model_key not in EOS_MODELS:
        raise ValueError(
            "Unknown EOS model '%s'. Choose from: %s"
            % (model, ", ".join(sorted(EOS_MODELS)))
        )

    system_cls = cls(EOS_MODELS[model_key])
    fluid = system_cls(temperature_c + 273.15, pressure_bara)
    for name, fraction in components.items():
        fluid.addComponent(str(name), float(fraction))
    fluid.setMixingRule(mixing_rule)
    fluid.init(0)
    return fluid


def make_preset(
    cls: Callable[[str], object],
    name: str,
    temperature_c: float = 15.0,
    pressure_bara: float = 1.01325,
    model: str = "srk",
    mixing_rule: str = "classic",
):
    """Build one of the :data:`PRESET_FLUIDS` by name.

    Parameters
    ----------
    cls:
        Class resolver callable (see :func:`make_fluid`).
    name:
        Preset key, e.g. ``"natural_gas"`` or ``"rich_gas"``.
    temperature_c:
        Initial temperature in degrees Celsius.
    pressure_bara:
        Initial pressure in bara.
    model:
        Friendly EOS name.
    mixing_rule:
        Mixing rule name.

    Returns
    -------
    object
        A configured NeqSim ``SystemInterface``.

    Raises
    ------
    ValueError
        If ``name`` is not a known preset.
    """
    key = name.strip().lower()
    if key not in PRESET_FLUIDS:
        raise ValueError(
            "Unknown preset '%s'. Choose from: %s"
            % (name, ", ".join(sorted(PRESET_FLUIDS)))
        )
    return make_fluid(
        cls,
        PRESET_FLUIDS[key],
        temperature_c=temperature_c,
        pressure_bara=pressure_bara,
        model=model,
        mixing_rule=mixing_rule,
    )


def list_presets() -> Dict[str, Dict[str, float]]:
    """Return a copy of the available preset fluid compositions.

    Returns
    -------
    dict
        Mapping of preset name to its component dictionary.
    """
    return {name: dict(comp) for name, comp in PRESET_FLUIDS.items()}
