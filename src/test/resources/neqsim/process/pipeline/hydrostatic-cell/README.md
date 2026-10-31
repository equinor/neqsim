# Static subcell geometry qualification

This is a public geometric reference, not a transient pool initialization or a
hydrocarbon evaporation benchmark. The implementation is an opt-in read-only
query; it does not change the existing flow closures or conserved inventories.

## Inputs and provenance

- Pipe arclength 5000 m, internal diameter 1.0148 m.
- Centreline face elevations: `-300 - 55*sin(pi*s)^2 - 12*sin(3*pi*s)^2` m,
  where `s = x/5000`. For `s > 0.75`, retain the documented historical shelf
  at -333.5 m. Every mesh represents this same physical terrain.
- Counterfactual at-rest liquid volume: **92.5481689873561 m3**. This was
  exported from the 600 s twelve-cell rich-gas run on source
  `c57c8f863068ab21b51662e65c6c6178ee0ba4aa`; the profile SHA256 is
  `73ad931d9cd00739d4fc03be099f6623ee6ae52493055ae846d61ef181867a42`.
  It is never seeded into a transient model or fitted to a desired pool.
- Original qualification base `fd3d28142b2139691ce606e4cba5ab8241469486`;
  publication base `dd5e650ed6107234084517cd0147297dae825d65` changes only
  independent safety documentation and its test.
- Horizontal liquid plane inside straight cylindrical cells; normal depth is
  measured perpendicular to the pipe. No capillarity, curved-pipe volume
  Jacobian, separate oil/water layers or moving-interface model.

`generate_reference.py` uses independent adaptive normal-depth quadrature and
Brent volume inversion, with no NeqSim imports. The production query instead uses
angular Gauss quadrature and minority-volume bisection. Dependencies used for the
original reference are NumPy/SciPy; regenerated floating-point values may differ
within the fixed acceptance tolerances.

Run the script with the selected Python interpreter, then run
`./mvnw test -Dtest=HydrostaticCellGeometryTest,GeometryCalculatorTest`.
`HydrostaticCellGeometryTest.publicTerrainReferences` reads every reference row
and checks all reported geometry against the production implementation.
Other methods cover horizontal/trace/reversed-slope/partition/empty/full limits,
serialization, invalid inputs, and unchanged section inventories and ordinary
geometry. The CSV column units are: diameter/length/start_depth/end_depth in m;
angle in radians; interface_area/wall_area/minimum_gas_area in m2; holdup
dimensionless. Cell index is zero based. The rows determine cell volume as
`holdup*pi*diameter^2*length/4`.

## Fixed acceptance and results

The tolerances were declared before implementation: depth absolute 1e-8 m;
area absolute 1e-9 m2 or relative 1e-7; volume absolute 1e-9 m3 or relative 1e-9;
exact empty/full endpoints; explicit invalid-input rejection; no section-state
mutation. All **3060** cells pass. `mesh_results.csv` records the earlier executed
production outputs; the regression also replays regenerated independent inputs.

| Cells | Maximum depth (m) | Interface area (m2) | Minimum gas area (m2) |
| ---: | ---: | ---: | ---: |
| 12 | 1.014800 | 69.598720 | 0 |
| 24 | 1.014800 | 125.077051 | 0 |
| 48 | 0.891400 | 204.506440 | 0.056051 |
| 96 | 0.805077 | 197.929492 | 0.120683 |
| 192 | 0.783790 | 196.533651 | 0.138493 |
| 384 | 0.778488 | 196.332581 | 0.143023 |
| 768 | 0.777155 | 196.353521 | 0.144168 |
| 1536 | 0.776819 | 196.347040 | 0.144457 |

An independent continuous-terrain quadrature gives maximum depth 0.776707703 m,
minimum gas area 0.144552648 m2 and interface area 196.346036943 m2. The finest
interface-area difference is 0.000511%. This is resolution evidence for this
specific static geometry, not a universal mesh recommendation.

Earlier measured optional-helper cost was median 88.09 microseconds/query,
range 84.01-116.57, with 3000 warmups and seven repeats of 10000 varied queries
on Java 17.0.20 under concurrent load. This measures the calculator, not full
section-accessor cost or portable throughput. The timestep path never invokes it.

## Engineering limits and next dependencies

Full rich-gas numerical cycles on the earlier base completed 3000 s on matched
12/24-cell geometry, but the equivalent-level profile difference **12.0204%**
fails the unchanged 10% mesh gate. Peak levels differ 33.8259%. Closed gravity
retention, gas flowing over stable pools, complete force/momentum accounting,
local enriched phase-boundary qualification and physical evaporation time are
still open. The cycle used approximate equilibrium relaxation with tau = 30 s,
not a qualified multicomponent interfacial rate law. Static geometry does not
repair those limitations or supply a physical heat-transfer coefficient.

Ownership and complete executed evidence remain in
[#3298](https://github.com/equinor/neqsim/issues/3298), with numerical/boundary
dependencies in [#4674](https://github.com/equinor/neqsim/issues/4674) and physical
mass/heat-transfer qualification in [#4675](https://github.com/equinor/neqsim/issues/4675).
