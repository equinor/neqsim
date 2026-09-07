"""Why RD-1987 can supply a molar mass but cannot supply a specific gravity.

The two uses look symmetric but are not:

    addTBPfraction_Tb_Kw   SG comes from the Watson *definition*, exactly.
                           RD-1987 is then evaluated FORWARD: M = f(Tb, SG).

    addTBPfraction_Mw_Tb   SG is the unknown. RD-1987 would have to be
                           INVERTED: solve f(Tb, SG) = M for SG.

Evaluating a correlation forward is always well posed. Inverting it is only
well posed if it is monotonic. This script shows where RD-1987 turns over, how
many roots the inverse has, and how badly conditioned it is in the band where
petroleum fractions actually sit.

Usage:
    python devtools/analyze_rd1987_inversion.py
"""

from __future__ import annotations

import math

# Real components from COMP.csv, used to show where the turning point falls
# relative to fractions people actually characterize.
REFERENCE_COMPONENTS = [
    ("n-heptane", 371.6, 0.690, 100.2),
    ("nC10", 447.3, 0.734, 142.3),
    ("nC14", 526.7, 0.764, 198.4),
    ("toluene", 383.8, 0.871, 92.1),
    ("m-Xylene", 412.3, 0.868, 106.2),
]


def molar_mass_rd1987(tb_k: float, sg: float) -> float:
    """RD-1987, forward direction. Returns g/mol."""
    return (
        42.965
        * math.exp(2.097e-4 * tb_k - 7.78712 * sg + 2.08476e-3 * tb_k * sg)
        * tb_k**1.26007
        * sg**4.98308
    )


def molar_mass_rd1980(tb_k: float, sg: float) -> float:
    """RD-1980, forward direction. Returns g/mol."""
    return 4.5673e-5 * (tb_k * 1.8) ** 2.1962 * sg**-1.0164


def turning_point_sg(tb_k: float) -> float:
    """Specific gravity at which dM/dSG = 0 for RD-1987.

    d(ln M)/d(ln SG) = 4.98308 + SG*(-7.78712 + 2.08476e-3*Tb), which vanishes at
    SG = 4.98308 / (7.78712 - 2.08476e-3*Tb).
    """
    return 4.98308 / (7.78712 - 2.08476e-3 * tb_k)


def log_sensitivity_rd1987(tb_k: float, sg: float) -> float:
    """d(ln M)/d(ln SG) for RD-1987."""
    return 4.98308 + sg * (-7.78712 + 2.08476e-3 * tb_k)


def count_roots(tb_k: float, target_molar_mass: float, lo: float = 0.60, hi: float = 1.00) -> int:
    """Number of specific gravities in [lo, hi] that reproduce the target molar mass."""
    steps = 4000
    roots = 0
    previous = molar_mass_rd1987(tb_k, lo) - target_molar_mass
    for i in range(1, steps + 1):
        sg = lo + (hi - lo) * i / steps
        current = molar_mass_rd1987(tb_k, sg) - target_molar_mass
        if previous == 0.0 or previous * current < 0.0:
            roots += 1
        previous = current
    return roots


def main() -> None:
    print("=" * 76)
    print("1. Where does RD-1987 turn over in specific gravity?")
    print("=" * 76)
    print(f"{'Tb / K':>8}{'SG at dM/dSG = 0':>20}{'sits inside 0.60-1.00?':>26}")
    for tb in (350.0, 400.0, 450.0, 500.0, 550.0, 600.0):
        sg_star = turning_point_sg(tb)
        inside = "yes" if 0.60 <= sg_star <= 1.00 else "no"
        print(f"{tb:>8.0f}{sg_star:>20.3f}{inside:>26}")
    print(
        "\nThe turning point lands at SG 0.71 to 0.77 - the middle of the petroleum\n"
        "range, and exactly where paraffins live."
    )

    print("\n" + "=" * 76)
    print("2. How many specific gravities reproduce a given (Tb, M)?")
    print("=" * 76)
    print(f"{'component':<14}{'Tb / K':>8}{'M g/mol':>10}{'RD-1987 roots':>16}{'RD-1980 roots':>16}")
    for name, tb, _sg, molar_mass in REFERENCE_COMPONENTS:
        n_1987 = count_roots(tb, molar_mass)
        # RD-1980 is a pure power law in SG, so it has exactly one root wherever the
        # target is inside the range spanned over the interval.
        lo_value = molar_mass_rd1980(tb, 0.60)
        hi_value = molar_mass_rd1980(tb, 1.00)
        n_1980 = 1 if min(lo_value, hi_value) <= molar_mass <= max(lo_value, hi_value) else 0
        print(f"{name:<14}{tb:>8.1f}{molar_mass:>10.1f}{n_1987:>16}{n_1980:>16}")
    print(
        "\nTwo roots means the inverse is ambiguous: one solution on the paraffinic\n"
        "side of the turning point and one on the aromatic side, both reproducing the\n"
        "same molar mass. Nothing in the inputs says which one the caller meant."
    )

    print("\n" + "=" * 76)
    print("3. How well conditioned is the inverse where fractions actually sit?")
    print("=" * 76)
    print("Error amplification: a 1 % error in M becomes this many % error in SG.")
    print(f"{'component':<14}{'SG':>7}{'dlnM/dlnSG':>13}{'amplification':>16}")
    for name, tb, sg, _molar_mass in REFERENCE_COMPONENTS:
        gain = log_sensitivity_rd1987(tb, sg)
        amplification = float("inf") if abs(gain) < 1e-12 else abs(1.0 / gain)
        shown = "infinite" if amplification > 1e6 else "{:.1f}x".format(amplification)
        print(f"{name:<14}{sg:>7.3f}{gain:>13.3f}{shown:>16}")

    print("\nSame quantity for RD-1980, whose exponent is constant:")
    print(f"{'':<14}{'':>7}{'dlnM/dlnSG':>13}{'amplification':>16}")
    print(f"{'any':<14}{'any':>7}{-1.0164:>13.3f}{'1.0x':>16}")

    print(
        "\nAcross these paraffins the amplification runs from 7x to 21x, worst at nC10\n"
        "where the turning point almost coincides with the component. A 2 % uncertainty\n"
        "in nC10's molar mass - better than most cuts are known - would move the\n"
        "inferred specific gravity by about 40 %. That is not a usable inverse, which\n"
        "is why addTBPfraction_Mw_Tb refuses RD-1987 and defaults to RD-1980 instead.\n"
        "Note the aromatics are fine: they sit well away from the turning point. The\n"
        "inverse fails precisely for the fractions most people characterize.\n"
    )

    print("=" * 76)
    print("4. The forward direction, which is what addTBPfraction_Tb_Kw uses")
    print("=" * 76)
    print(f"{'component':<14}{'Tb / K':>8}{'Kw':>8}{'SG (exact)':>12}{'M RD-1987':>12}{'M actual':>10}{'dev':>8}")
    for name, tb, sg, molar_mass in REFERENCE_COMPONENTS:
        watson_k = (1.8 * tb) ** (1.0 / 3.0) / sg
        sg_from_kw = (1.8 * tb) ** (1.0 / 3.0) / watson_k
        predicted = molar_mass_rd1987(tb, sg_from_kw)
        dev = 100.0 * (predicted - molar_mass) / molar_mass
        print(
            f"{name:<14}{tb:>8.1f}{watson_k:>8.3f}{sg_from_kw:>12.4f}"
            f"{predicted:>12.1f}{molar_mass:>10.1f}{dev:>7.1f}%"
        )
    print(
        "\nNo inversion anywhere: the Watson definition gives SG exactly, then RD-1987\n"
        "is evaluated once, forward. The turning point is irrelevant because we never\n"
        "have to ask which SG produced a given M."
    )


if __name__ == "__main__":
    main()
