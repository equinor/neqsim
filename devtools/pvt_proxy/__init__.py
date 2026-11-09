"""PVT forward-proxy surface process generator built on NeqSim.

Calibrates ONE common set of separator and scrubber pressures and temperatures for a strictly
forward (no recycle) surface process so that it reproduces the standard oil and gas rates of a
detailed NeqSim reference process over a representative fluid ensemble. The fitted proxy defines
the surface separation used when generating PVT tables (Eclipse 300, PVTsim, FluidMagic, OLGA,
black-oil tables).

Entry points: ``python -m pvt_proxy run CONFIG --out DIR`` or ``neqsim pvt-proxy run ...``.
"""

__version__ = "0.1.0"
