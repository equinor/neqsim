"""EOS and reference-equation comparison for the hydrogen thermodynamics chapter.

The script compares SRK, Peng-Robinson, GERG-2008, GERG-2008-H2, and Leachman
models with direct NeqSim calls. It writes CSV, JSON, and figure files used by
chapter 04 of the hydrogen book.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd


SCRIPT_DIR = Path(__file__).resolve().parent
CHAPTER_DIR = SCRIPT_DIR.parent
FIGURES_DIR = CHAPTER_DIR / "figures"
FIGURES_DIR.mkdir(parents=True, exist_ok=True)


def find_project_root() -> Path:
    """Find the NeqSim repository root from the script location or environment."""
    env_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    candidates = []
    if env_root:
        candidates.append(Path(env_root).resolve())
    candidates.extend([SCRIPT_DIR] + list(SCRIPT_DIR.parents))
    for candidate in candidates:
        if (candidate / "pom.xml").exists() and (candidate / "devtools" / "neqsim_dev_setup.py").exists():
            return candidate
    raise RuntimeError("Could not find NeqSim project root. Set NEQSIM_PROJECT_ROOT.")


def load_jclass():
    """Load JPype JClass with workspace classes preferred over an installed package."""
    try:
        project_root = find_project_root()
        sys.path.insert(0, str(project_root / "devtools"))
        from neqsim_dev_setup import neqsim_init

        ns = neqsim_init(project_root=project_root, recompile=False, verbose=False)
        return ns.JClass
    except Exception:
        from neqsim import jneqsim  # noqa: F401
        from jpype import JClass

        return JClass


JClass = load_jclass()
SystemSrkEos = JClass("neqsim.thermo.system.SystemSrkEos")
SystemPrEos = JClass("neqsim.thermo.system.SystemPrEos")
SystemGERG2008Eos = JClass("neqsim.thermo.system.SystemGERG2008Eos")
SystemLeachmanEos = JClass("neqsim.thermo.system.SystemLeachmanEos")
ThermodynamicOperations = JClass("neqsim.thermodynamicoperations.ThermodynamicOperations")


def make_system(model: str, temperature_K: float, pressure_bara: float, components: dict[str, float]):
    """Create, flash, and initialize a NeqSim system for one EOS model."""
    if model == "Leachman":
        if set(components.keys()) != {"hydrogen"}:
            raise ValueError("Leachman is only valid for pure hydrogen in this comparison.")
        system = SystemLeachmanEos(temperature_K, pressure_bara)
    elif model == "GERG2008":
        system = SystemGERG2008Eos(temperature_K, pressure_bara)
        for name, amount in components.items():
            if amount > 0.0:
                system.addComponent(name, amount)
    elif model == "GERG2008-H2":
        system = SystemGERG2008Eos(temperature_K, pressure_bara)
        for name, amount in components.items():
            if amount > 0.0:
                system.addComponent(name, amount)
        system.useHydrogenEnhancedModel()
    elif model == "SRK":
        system = SystemSrkEos(temperature_K, pressure_bara)
        for name, amount in components.items():
            if amount > 0.0:
                system.addComponent(name, amount)
        system.setMixingRule("classic")
    elif model == "PR":
        system = SystemPrEos(temperature_K, pressure_bara)
        for name, amount in components.items():
            if amount > 0.0:
                system.addComponent(name, amount)
        system.setMixingRule("classic")
    else:
        raise ValueError(f"Unsupported EOS model: {model}")

    ThermodynamicOperations(system).TPflash()
    system.initProperties()
    return system


def properties(model: str, temperature_K: float, pressure_bara: float, components: dict[str, float]) -> dict[str, float]:
    """Return selected flashed properties from one model/state point."""
    system = make_system(model, temperature_K, pressure_bara, components)
    phase = system.getPhase(0)
    return {
        "temperature_K": temperature_K,
        "pressure_bara": pressure_bara,
        "model": model,
        "density_kg_m3": float(phase.getDensity("kg/m3")),
        "z_factor": float(system.getZ()),
    }


def pure_hydrogen_sweep() -> pd.DataFrame:
    """Compare pure hydrogen EOS models against Leachman at 300 K."""
    rows = []
    pressures_bara = [1.0, 10.0, 30.0, 50.0, 100.0, 200.0, 500.0, 700.0]
    models = ["Leachman", "GERG2008", "GERG2008-H2", "SRK", "PR"]
    for pressure_bara in pressures_bara:
        for model in models:
            rows.append(properties(model, 300.0, pressure_bara, {"hydrogen": 1.0}))
    df = pd.DataFrame(rows)
    references = df[df["model"] == "Leachman"].set_index("pressure_bara")["density_kg_m3"]
    df["reference_density_kg_m3"] = df["pressure_bara"].map(references)
    df["density_deviation_pct"] = 100.0 * (df["density_kg_m3"] / df["reference_density_kg_m3"] - 1.0)
    return df


def mixture_sweep() -> pd.DataFrame:
    """Compare methane-hydrogen mixtures against a GERG-2008-H2/Leachman reference."""
    rows = []
    h2_fractions = [0.0, 0.05, 0.10, 0.20, 0.50, 0.80, 0.95, 1.0]
    temperature_K = 288.15
    pressure_bara = 100.0
    models = ["SRK", "PR", "GERG2008", "GERG2008-H2"]
    for h2_fraction in h2_fractions:
        components = {"methane": 1.0 - h2_fraction, "hydrogen": h2_fraction}
        if h2_fraction == 1.0:
            reference = properties("Leachman", temperature_K, pressure_bara, {"hydrogen": 1.0})
            reference_model = "Leachman"
        else:
            reference = properties("GERG2008-H2", temperature_K, pressure_bara, components)
            reference_model = "GERG2008-H2"
        for model in models:
            value = properties(model, temperature_K, pressure_bara, components)
            value.update(
                {
                    "hydrogen_mole_fraction": h2_fraction,
                    "reference_model": reference_model,
                    "reference_density_kg_m3": reference["density_kg_m3"],
                    "density_deviation_pct": 100.0
                    * (value["density_kg_m3"] / reference["density_kg_m3"] - 1.0),
                }
            )
            rows.append(value)
    return pd.DataFrame(rows)


def style_axes(ax, ylabel: str, xlabel: str) -> None:
    """Apply consistent plotting style."""
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, alpha=0.3)
    ax.legend(frameon=False)


def plot_pure_density(df: pd.DataFrame) -> None:
    """Plot pure hydrogen density deviations relative to Leachman."""
    fig, ax = plt.subplots(figsize=(7.2, 4.4), dpi=160)
    colors = {"SRK": "#0072B2", "PR": "#D55E00", "GERG2008": "#009E73", "GERG2008-H2": "#CC79A7"}
    for model in ["SRK", "PR", "GERG2008", "GERG2008-H2"]:
        subset = df[df["model"] == model]
        ax.plot(subset["pressure_bara"], subset["density_deviation_pct"], marker="o", label=model, color=colors[model])
    ax.axhline(0.0, color="#333333", linewidth=1.0)
    style_axes(ax, "Density deviation from Leachman (%)", "Pressure (bara)")
    ax.set_title("Pure H2 density: cubic and GERG models vs Leachman at 300 K")
    fig.tight_layout()
    fig.savefig(FIGURES_DIR / "eos_pure_h2_density_deviation.png", bbox_inches="tight")
    plt.close(fig)


def plot_pure_zfactor(df: pd.DataFrame) -> None:
    """Plot pure hydrogen compressibility factor by EOS."""
    fig, ax = plt.subplots(figsize=(7.2, 4.4), dpi=160)
    for model in ["Leachman", "GERG2008", "GERG2008-H2", "SRK", "PR"]:
        subset = df[df["model"] == model]
        ax.plot(subset["pressure_bara"], subset["z_factor"], marker="o", label=model)
    style_axes(ax, "Compressibility factor Z (-)", "Pressure (bara)")
    ax.set_title("Pure H2 compressibility-factor spread at 300 K")
    fig.tight_layout()
    fig.savefig(FIGURES_DIR / "eos_pure_h2_zfactor.png", bbox_inches="tight")
    plt.close(fig)


def plot_mixture_density(df: pd.DataFrame) -> None:
    """Plot methane-hydrogen mixture deviations relative to reference basis."""
    fig, ax = plt.subplots(figsize=(7.2, 4.4), dpi=160)
    for model in ["SRK", "PR", "GERG2008"]:
        subset = df[df["model"] == model]
        ax.plot(100.0 * subset["hydrogen_mole_fraction"], subset["density_deviation_pct"], marker="o", label=model)
    ax.axhline(0.0, color="#333333", linewidth=1.0)
    style_axes(ax, "Density deviation from reference (%)", "Hydrogen mole fraction (%)")
    ax.set_title("Methane-H2 density vs GERG-2008-H2/Leachman reference")
    fig.tight_layout()
    fig.savefig(FIGURES_DIR / "eos_methane_hydrogen_density_deviation.png", bbox_inches="tight")
    plt.close(fig)


def main() -> None:
    """Run all EOS comparison sweeps and write the book artifacts."""
    pure_df = pure_hydrogen_sweep()
    mixture_df = mixture_sweep()
    pure_df.to_csv(FIGURES_DIR / "eos_reference_comparison_pure_h2.csv", index=False)
    mixture_df.to_csv(FIGURES_DIR / "eos_reference_comparison_mixture.csv", index=False)
    plot_pure_density(pure_df)
    plot_pure_zfactor(pure_df)
    plot_mixture_density(mixture_df)
    summary = {
        "basis": {
            "pure_hydrogen_reference": "Leachman EOS",
            "mixture_reference": "GERG-2008-H2 for mixtures, Leachman at pure H2",
            "pure_h2_temperature_K": 300.0,
            "mixture_temperature_K": 288.15,
            "mixture_pressure_bara": 100.0,
        },
        "pure_hydrogen_max_abs_density_deviation_pct": pure_df[pure_df["model"] != "Leachman"]
        .groupby("model")["density_deviation_pct"]
        .apply(lambda values: float(values.abs().max()))
        .to_dict(),
        "mixture_max_abs_density_deviation_pct": mixture_df[mixture_df["model"].isin(["SRK", "PR", "GERG2008"])]
        .groupby("model")["density_deviation_pct"]
        .apply(lambda values: float(values.abs().max()))
        .to_dict(),
    }
    (FIGURES_DIR / "eos_reference_comparison_results.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
