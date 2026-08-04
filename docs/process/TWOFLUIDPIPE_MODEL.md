| Carbon Steel | 50.0 | 7850 | 480 |
| FBE Coating | 0.3 | 1400 | 1000 |
| PU Foam | 0.035 | 80 | 1500 |
| Syntactic Foam | 0.15 | 650 | 1100 |
| Aerogel | 0.015 | 150 | 1000 |
| Concrete | 1.4 | 2400 | 880 |

### Usage Example
```java
TwoFluidPipe pipe = new TwoFluidPipe("subsea-export", inletStream);
pipe.setLength(20000.0); // 20 km
pipe.setDiameter(0.254); // 10 inch
pipe.setWallThickness(0.015);
pipe.setSurfaceTemperature(4.0, "C"); // Cold seabed

// Configure with 50mm PU foam + 40mm concrete
pipe.configureSubseaThermalModel(0.050, 0.040,
    RadialThermalLayer.MaterialType.PU_FOAM);

// Set hydrate formation temperature
pipe.setHydrateFormationTemperature(20.0, "C");

// Calculate cooldown time
double cooldownHours = pipe.calculateHydrateCooldownTime();
System.out.printf("Cooldown to hydrate: %.1f hours%n", cooldownHours);

// Run simulation
pipe.run();

// Get thermal summary
System.out.println(pipe.getThermalSummary());
```

### Thermal Calculations
- **Overall U-value**: Based on series thermal resistance through all layers
- **Transient response**: Explicit finite-difference with thermal mass in each layer
- **Cooldown time**: Lumped capacitance approximation for shutdown scenarios
- **Transient advection**: Uses the gas, oil, and water mass flow retained from each conservative AUSM+ integration
  stage, combined with the time integrator's own stage weights. Temperature transport therefore uses the same face
  fluxes that advanced the accepted hydrodynamic state without a second flux sweep. A CLOSED inlet or outlet has
  exactly zero advective sensible-energy transport, while internal convection can continue. Every cell reads from
  one pre-update temperature snapshot, so explicit advection is independent of the cell loop order. The external outlet
  face is outflow-only; reverse-flow upwinding applies only at internal faces.
- **Closed cooldown**: Radial wall/ambient heat exchange is evaluated for every physical cell, including section zero.
  The local conservative phase inventory supplies fluid thermal inertia, so a disconnected inlet stream's stored
  nominal rate cannot change a closed-domain temperature history. Each cell also owns an independent radial-layer
  temperature history, preventing repeated advancement or cross-cell mixing of stateful multilayer wall calculations.
- **Energy ownership**: The post-step fluid/wall temperature model owns ambient heat exchange. The duplicate wall source
  in `TwoFluidConservationEquations` is disabled when this model is configured, avoiding two applications of the same
  heat loss.

For validation, start from `run()`, close both boundaries, disable Joule–Thomson effects for an adiabatic invariant, and
check that a uniform state remains uniform. For cooldown, report absolute pressure, composition and mixing rule,
temperatures, heat-transfer coefficients, wall properties, mesh, time step, and units; verify that every cell approaches
ambient monotonically without undershoot. This implementation does not claim OLGA or LedaFlow equivalence.

## Model Capabilities Summary

| Category | Feature | Method/Correlation |
|----------|---------|--------------------|
| **Conservation Equations** |
| Gas mass | Full continuity equation | Phase-resolved flash transfer |
| Oil mass | Full continuity equation | Equilibrium mass split / donor inventory |
| Water mass | Full continuity equation | Equilibrium mass split / donor inventory |
| Gas momentum | 1D momentum balance | Wall and interfacial shear |
| Liquid momentum | 1D momentum balance | Wall and interfacial shear |
| Mixture energy | Full energy balance | Optional J-T effect |
| **Closure Models** |
| Stratified holdup | Momentum balance | Taitel-Dukler geometry |
| Annular holdup | Film model | Ishii-Mishima entrainment |
| Slug holdup | Empirical correlation | Dukler correlation |
| Interfacial friction | Flow-regime specific | Multiple correlations |
| **Oil-Water Models** |
| Oil-water flow regime | OilWaterFlowRegimeDetector | Trallero/Brauner/Angeli classification |
| Phase inversion | Decarre-Fabre (1997) | Viscosity/density-ratio model |
| Emulsion viscosity | Brinkman correlation | Continuous/dispersed mixture |
| Water wetting | Per-section detection | Corrosion risk indicator |
| Water dropout | Velocity/holdup criterion | Accumulation risk flag |
| **Terrain Effects** |
| Low point accumulation | Froude criterion | Fr < 0.5 triggers accumulation |
| Riser-base liquid fallback | Local gas-carryover screen | Indicates possible local fallback only |
| Flowline-riser stability | Taitel (1986) quasi-steady criterion | Explicit topology-aware system diagnostic |