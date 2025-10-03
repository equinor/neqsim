#!/usr/bin/env python3
"""Reference implementations of differential pressure flow calculations in Python.

The formulas mirror the tilstandomatic implementation and are used to cross-check
Java results in unit tests.
"""

from __future__ import annotations

import argparse
import math


def calc_venturi(dp: float, p: float, rho: float, kappa: float, D: float, d: float, C: float) -> float:
    """Calculate Venturi mass flow in kg/h using the ISO 5167 formulation."""
    beta = d / D
    beta4 = beta ** 4
    tau = p / (p + dp)
    if math.isclose(tau, 1.0):
        return 0.0
    tau2k = tau ** (2.0 / kappa)
    numerator = (
        kappa
        * tau2k
        / (kappa - 1.0)
        * (1.0 - beta4)
        / (1.0 - beta4 * tau2k)
        * (1.0 - tau ** ((kappa - 1.0) / kappa))
        / (1.0 - tau)
    )
    eps = math.sqrt(max(numerator, 0.0))
    root_term = math.sqrt(max(dp * rho * 2.0, 0.0))
    mass_flow = C / math.sqrt(max(1.0 - beta4, 1e-30)) * eps * math.pi / 4.0 * d * d * root_term
    return mass_flow * 3600.0


def calc_simplified(dp: float, rho: float, Cv: float) -> float:
    """Calculate mass flow using a simplified Cv formulation."""
    return Cv * math.sqrt(max(dp * rho, 0.0))


def main() -> None:
    parser = argparse.ArgumentParser(description="Differential pressure flow reference")
    subparsers = parser.add_subparsers(dest="command", required=True)

    venturi_parser = subparsers.add_parser("venturi")
    venturi_parser.add_argument("--dp", type=float, required=True, help="Differential pressure in Pa")
    venturi_parser.add_argument("--p", type=float, required=True, help="Inlet pressure in Pa")
    venturi_parser.add_argument("--rho", type=float, required=True, help="Density in kg/m3")
    venturi_parser.add_argument("--kappa", type=float, required=True, help="Isentropic exponent")
    venturi_parser.add_argument("--D", type=float, required=True, help="Pipe diameter in m")
    venturi_parser.add_argument("--d", type=float, required=True, help="Throat diameter in m")
    venturi_parser.add_argument("--C", type=float, required=True, help="Discharge coefficient")

    simplified_parser = subparsers.add_parser("simplified")
    simplified_parser.add_argument("--dp", type=float, required=True, help="Differential pressure in Pa")
    simplified_parser.add_argument("--rho", type=float, required=True, help="Density in kg/m3")
    simplified_parser.add_argument("--Cv", type=float, required=True, help="Cv coefficient")

    args = parser.parse_args()

    if args.command == "venturi":
        value = calc_venturi(args.dp, args.p, args.rho, args.kappa, args.D, args.d, args.C)
    elif args.command == "simplified":
        value = calc_simplified(args.dp, args.rho, args.Cv)
    else:
        raise ValueError(f"Unsupported command: {args.command}")

    # Print with high precision so Java tests can parse the float accurately.
    print(f"{value:.12f}")


if __name__ == "__main__":
    main()
