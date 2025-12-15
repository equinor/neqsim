# Multiphase 1D Transient Pipeline Model - Implementation Recommendations

## Executive Summary

This document outlines recommendations for implementing a full multiphase 1D transient pipeline model in NeqSim, similar to commercial tools like OLGA and LedaFlow. The model would handle:

- **Liquid accumulation** (holdup dynamics)
- **Slug flow** (terrain-induced and hydrodynamic slugs)
- **Three-phase flow** (gas, oil, water)
- **Transient behavior** (startup, shutdown, pigging, rate changes)

## Current State in NeqSim

### Existing Capabilities

| Component | Status | Notes |
|-----------|--------|-------|
| `PipeBeggsAndBrills` | ✅ | Steady-state multiphase, quasi-transient advection |
| `WaterHammerPipe` | ✅ | Fast transients (MOC), single-phase |
| Thermodynamics | ✅ | Full EOS, flash calculations, physical properties |
| Flow regime maps | ✅ | Beggs & Brill flow pattern detection |
| Holdup correlations | ✅ | Beggs & Brill liquid holdup |

### Gaps for Full Transient Multiphase

| Feature | Current | Required |
|---------|---------|----------|
| Mass conservation | Quasi-steady | Full PDE |
| Momentum | Steady friction | Full transient + interfacial |
| Energy | Optional heat loss | Full enthalpy transport |
| Liquid accumulation | Not tracked | Dynamic holdup evolution |
| Slug tracking | Not modeled | Slug initiation, growth, dissipation |
| Phase slip | Correlations | Mechanistic drift-flux or two-fluid |

---

## Recommended Model Architecture

### Two-Fluid Model (Recommended for Accuracy)

The two-fluid model solves separate conservation equations for each phase:

#### Conservation Equations

**Gas Mass:**
$$\frac{\partial}{\partial t}(\alpha_g \rho_g A) + \frac{\partial}{\partial x}(\alpha_g \rho_g v_g A) = \Gamma_g$$

**Liquid Mass:**
$$\frac{\partial}{\partial t}(\alpha_L \rho_L A) + \frac{\partial}{\partial x}(\alpha_L \rho_L v_L A) = \Gamma_L$$

**Gas Momentum:**
$$\frac{\partial}{\partial t}(\alpha_g \rho_g v_g A) + \frac{\partial}{\partial x}(\alpha_g \rho_g v_g^2 A) = -\alpha_g A \frac{\partial P}{\partial x} - \tau_{wg} S_g - \tau_i S_i - \alpha_g \rho_g g A \sin\theta$$

**Liquid Momentum:**
$$\frac{\partial}{\partial t}(\alpha_L \rho_L v_L A) + \frac{\partial}{\partial x}(\alpha_L \rho_L v_L^2 A) = -\alpha_L A \frac{\partial P}{\partial x} - \tau_{wL} S_L + \tau_i S_i - \alpha_L \rho_L g A \sin\theta$$

**Mixture Energy:**
$$\frac{\partial}{\partial t}(E_{mix} A) + \frac{\partial}{\partial x}((E_{mix} + P) v_{mix} A) = Q_{wall} + W_{friction}$$

Where:
- $\alpha$ = volume fraction
- $\rho$ = density
- $v$ = velocity
- $\tau_w$ = wall shear stress
- $\tau_i$ = interfacial shear stress
- $S$ = wetted perimeter
- $\Gamma$ = mass transfer rate (flashing/condensation)
- $\theta$ = pipe inclination

### Alternative: Drift-Flux Model (Simpler, Faster)

For less demanding applications, a drift-flux model combines phases:

**Mixture Mass:**
$$\frac{\partial}{\partial t}(\rho_m A) + \frac{\partial}{\partial x}(\rho_m v_m A) = 0$$

**Mixture Momentum:**
$$\frac{\partial}{\partial t}(\rho_m v_m A) + \frac{\partial}{\partial x}(\rho_m v_m^2 A + \Delta P_{slip}) = -A \frac{\partial P}{\partial x} - \tau_w S - \rho_m g A \sin\theta$$

**Drift-Flux Relation:**
$$v_g = C_0 v_m + v_{drift}$$

Where $C_0$ is the distribution parameter and $v_{drift}$ is the drift velocity (from correlations).

---

## Proposed Class Structure

```
src/main/java/neqsim/process/equipment/pipeline/
├── MultiphasePipe.java              # Main transient solver
├── MultiphasePipeSection.java       # Single pipe section state
├── transient/
│   ├── ConservationEquations.java   # PDE discretization
│   ├── FluxCalculator.java          # Numerical flux (AUSM, HLL, etc.)
│   ├── SourceTerms.java             # Friction, gravity, mass transfer
│   ├── SlugTracker.java             # Slug initiation and tracking
│   └── HoldupEvolution.java         # Liquid accumulation dynamics
├── closure/
│   ├── FlowRegimeMap.java           # Mechanistic flow regime
│   ├── InterfacialFriction.java     # τ_i correlations
│   ├── WallFriction.java            # τ_wg, τ_wL correlations
│   ├── DriftFluxParameters.java     # C_0, v_drift correlations
│   └── EntrainmentDeposition.java   # Droplet exchange
└── geometry/
    ├── PipeProfile.java             # Elevation, diameter profile
    └── PipeNetwork.java             # Junctions, branches
```

---

## Key Implementation Components

### 1. State Vector and Grid

```java
public class MultiphasePipeSection {
    // Primary variables (conservative)
    private double pressure;           // Pa
    private double gasHoldup;          // α_g (0-1)
    private double oilHoldup;          // α_o (0-1)
    private double waterHoldup;        // α_w (0-1)
    private double gasVelocity;        // m/s
    private double liquidVelocity;     // m/s (or separate oil/water)
    private double temperature;        // K
    
    // Derived quantities
    private double gasDensity;
    private double liquidDensity;
    private double mixtureVelocity;
    private double liquidLevel;        // For stratified flow
    private FlowRegime flowRegime;
    
    // Slug tracking
    private boolean isInSlug;
    private double slugFrontPosition;
    private double slugTailPosition;
    private double slugHoldup;
    
    // Geometry
    private double diameter;
    private double area;
    private double inclination;        // radians
    private double position;           // m from inlet
}
```

### 2. Flow Regime Detection (Mechanistic)

```java
public enum FlowRegime {
    STRATIFIED_SMOOTH,
    STRATIFIED_WAVY,
    ANNULAR,
    SLUG,
    DISPERSED_BUBBLE,
    BUBBLE,
    CHURN,
    MIST
}

public class MechanisticFlowRegime {
    
    /**
     * Determine flow regime using Taitel-Dukler or similar mechanistic model.
     */
    public FlowRegime determine(double vsg, double vsl, double diameter,
                                 double inclination, PhaseProperties gas,
                                 PhaseProperties liquid) {
        
        // Calculate dimensionless groups
        double froude = calcFroudeNumber(vsg, vsl, diameter);
        double lockhart = calcLockhartMartinelli(gas, liquid, vsg, vsl);
        
        // Stratified stability (Kelvin-Helmholtz)
        double criticalGasVelocity = calcKelvinHelmholtzLimit(
            liquid.getDensity(), gas.getDensity(), 
            liquidLevel, diameter, inclination);
        
        if (vsg < criticalGasVelocity && inclination < Math.toRadians(10)) {
            // Check wavy vs smooth
            if (isWavyTransition(vsg, liquid)) {
                return FlowRegime.STRATIFIED_WAVY;
            }
            return FlowRegime.STRATIFIED_SMOOTH;
        }
        
        // Slug formation criterion
        if (isSlugCondition(vsg, vsl, liquidLevel, diameter)) {
            return FlowRegime.SLUG;
        }
        
        // Annular transition
        if (vsg > calcAnnularTransition(diameter, gas, liquid)) {
            return FlowRegime.ANNULAR;
        }
        
        // ... other transitions
        return FlowRegime.INTERMITTENT;
    }
    
    /**
     * Kelvin-Helmholtz instability criterion for stratified flow.
     */
    private double calcKelvinHelmholtzLimit(double rhoL, double rhoG,
                                             double hL, double D, double theta) {
        double g = 9.81;
        double aG = calcGasArea(hL, D);
        double aL = calcLiquidArea(hL, D);
        double dAL_dhL = calcDerivativeArea(hL, D);
        
        // Taitel-Dukler criterion
        double term1 = (rhoL - rhoG) * g * Math.cos(theta) * aG;
        double term2 = rhoG * aL * dAL_dhL;
        
        return Math.sqrt(term1 / term2);
    }
}
```

### 3. Slug Flow Model

```java
public class SlugTracker {
    
    private List<Slug> activeSlugs = new ArrayList<>();
    private double minSlugLength;       // Minimum stable slug length
    private double slugInitiationVoid;  // α_g threshold for slug formation
    
    public class Slug {
        double frontPosition;    // m
        double tailPosition;     // m
        double velocity;         // m/s
        double holdup;           // liquid fraction in slug body
        double bubbleHoldup;     // gas fraction in slug bubble
        double frequency;        // slugs per unit time
        boolean isTerrainInduced;
    }
    
    /**
     * Check for slug initiation at each grid point.
     */
    public void checkSlugInitiation(MultiphasePipeSection[] sections, double dt) {
        for (int i = 1; i < sections.length - 1; i++) {
            MultiphasePipeSection sec = sections[i];
            
            // Terrain-induced slug: liquid accumulation at low point
            if (isLowPoint(sections, i) && sec.getLiquidHoldup() > slugInitiationVoid) {
                if (!sec.isInSlug() && sec.getGasVelocity() > getMinGasVelocityForSlug(sec)) {
                    initiateSlug(sections, i);
                }
            }
            
            // Hydrodynamic slug: wave growth in stratified-wavy
            if (sec.getFlowRegime() == FlowRegime.STRATIFIED_WAVY) {
                if (isWaveBlockage(sec)) {
                    initiateSlug(sections, i);
                }
            }
        }
    }
    
    /**
     * Propagate existing slugs.
     */
    public void propagateSlugs(MultiphasePipeSection[] sections, double dt) {
        Iterator<Slug> iter = activeSlugs.iterator();
        while (iter.hasNext()) {
            Slug slug = iter.next();
            
            // Slug front velocity (Bendiksen correlation)
            double vFront = calcSlugFrontVelocity(slug, sections);
            slug.frontPosition += vFront * dt;
            
            // Slug tail velocity
            double vTail = calcSlugTailVelocity(slug, sections);
            slug.tailPosition += vTail * dt;
            
            // Slug length
            double length = slug.frontPosition - slug.tailPosition;
            
            // Slug dissipation
            if (length < minSlugLength || slug.frontPosition > getPipeLength()) {
                iter.remove();
                dissipateSlug(slug, sections);
            }
            
            // Update holdup in slug region
            updateSlugHoldup(slug, sections);
        }
    }
    
    /**
     * Bendiksen (1984) slug front velocity.
     */
    private double calcSlugFrontVelocity(Slug slug, MultiphasePipeSection[] sections) {
        MultiphasePipeSection sec = getSectionAt(slug.frontPosition, sections);
        double vm = sec.getMixtureVelocity();
        double C = 1.2;  // Distribution parameter
        double vd = 0.35 * Math.sqrt(9.81 * sec.getDiameter());  // Drift velocity
        return C * vm + vd;
    }
}
```

### 4. Liquid Accumulation Model

```java
public class LiquidAccumulation {
    
    /**
     * Track liquid inventory and low-point accumulation.
     */
    public void updateAccumulation(MultiphasePipeSection[] sections, 
                                    double dt, double inletLiquidRate) {
        
        // Identify low points in terrain profile
        List<Integer> lowPoints = findLowPoints(sections);
        
        for (int lowIdx : lowPoints) {
            MultiphasePipeSection low = sections[lowIdx];
            
            // Liquid drainage into low point
            double drainageIn = calcDrainageRate(sections, lowIdx, -1);  // From upstream
            drainageIn += calcDrainageRate(sections, lowIdx, +1);        // From downstream
            
            // Liquid carryover out of low point
            double carryover = calcCarryoverRate(low);
            
            // Net accumulation
            double netRate = drainageIn - carryover;
            
            // Update holdup
            double dHoldup = netRate * dt / (low.getArea() * getSegmentLength());
            low.setLiquidHoldup(low.getLiquidHoldup() + dHoldup);
            
            // Check for slug initiation if holdup exceeds critical
            if (low.getLiquidHoldup() > getCriticalHoldup(low)) {
                slugTracker.initiateSlug(sections, lowIdx);
            }
        }
    }
    
    /**
     * Calculate liquid drainage rate into low point.
     */
    private double calcDrainageRate(MultiphasePipeSection[] sections, 
                                     int lowIdx, int direction) {
        int neighborIdx = lowIdx + direction;
        if (neighborIdx < 0 || neighborIdx >= sections.length) return 0;
        
        MultiphasePipeSection neighbor = sections[neighborIdx];
        MultiphasePipeSection low = sections[lowIdx];
        
        // Height difference drives drainage
        double dz = neighbor.getElevation() - low.getElevation();
        if (dz <= 0) return 0;  // No drainage if neighbor is lower
        
        // Stratified film drainage (Wallis falling film)
        double holdup = neighbor.getLiquidHoldup();
        double filmThickness = calcFilmThickness(holdup, neighbor.getDiameter());
        double drainageVelocity = calcFilmDrainageVelocity(filmThickness, 
            neighbor.getInclination(), neighbor.getLiquidViscosity());
        
        return holdup * neighbor.getArea() * drainageVelocity;
    }
    
    /**
     * Calculate liquid carryover (entrainment by gas).
     */
    private double calcCarryoverRate(MultiphasePipeSection section) {
        // Critical gas velocity for liquid removal (Turner correlation)
        double vgCrit = calcCriticalGasVelocity(section);
        double vg = section.getGasVelocity();
        
        if (vg < vgCrit) return 0;
        
        // Carryover rate increases with excess gas velocity
        double excessVelocity = vg - vgCrit;
        double entrainmentFraction = calcEntrainmentFraction(excessVelocity, section);
        
        return entrainmentFraction * section.getLiquidHoldup() * 
               section.getArea() * section.getLiquidVelocity();
    }
}
```

### 5. Numerical Scheme

```java
public class MultiphaseFluxCalculator {
    
    public enum FluxScheme {
        AUSM_PLUS,      // Advection Upstream Splitting Method
        HLL,            // Harten-Lax-van Leer
        ROE,            // Roe approximate Riemann solver
        UPWIND          // First-order upwind
    }
    
    /**
     * Calculate numerical flux at cell interface using AUSM+ scheme.
     * Good for multiphase flows with large density ratios.
     */
    public double[] calcFluxAUSMPlus(double[] UL, double[] UR, 
                                      PhaseProperties propsL, PhaseProperties propsR) {
        
        // Primitive variables
        double rhoL = UL[0], rhoR = UR[0];
        double vL = UL[1] / rhoL, vR = UR[1] / rhoR;
        double pL = propsL.getPressure(), pR = propsR.getPressure();
        double cL = propsL.getSoundSpeed(), cR = propsR.getSoundSpeed();
        
        // Interface speed of sound
        double cHalf = 0.5 * (cL + cR);
        
        // Mach numbers
        double ML = vL / cHalf;
        double MR = vR / cHalf;
        
        // Split Mach numbers (AUSM+ splitting functions)
        double Mplus = calcMachPlus(ML);
        double Mminus = calcMachMinus(MR);
        double Mhalf = Mplus + Mminus;
        
        // Split pressures
        double Pplus = calcPressurePlus(ML) * pL;
        double Pminus = calcPressureMinus(MR) * pR;
        double Phalf = Pplus + Pminus;
        
        // Convective flux
        double[] flux = new double[3];
        if (Mhalf >= 0) {
            flux[0] = cHalf * Mhalf * rhoL;
            flux[1] = cHalf * Mhalf * rhoL * vL + Phalf;
            flux[2] = cHalf * Mhalf * rhoL * propsL.getEnthalpy();
        } else {
            flux[0] = cHalf * Mhalf * rhoR;
            flux[1] = cHalf * Mhalf * rhoR * vR + Phalf;
            flux[2] = cHalf * Mhalf * rhoR * propsR.getEnthalpy();
        }
        
        return flux;
    }
    
    /**
     * MUSCL reconstruction for second-order accuracy.
     */
    public double[] reconstructMUSCL(double[] U, int i, double[] dx) {
        // Slope limiter (minmod, van Leer, etc.)
        double slope = slopeLimiter(U[i-1], U[i], U[i+1], dx[i-1], dx[i]);
        
        double[] UL = new double[U.length];
        double[] UR = new double[U.length];
        
        UL[i] = U[i] - 0.5 * slope * dx[i];
        UR[i] = U[i] + 0.5 * slope * dx[i];
        
        return new double[][] {UL, UR};
    }
}
```

### 6. Time Integration

```java
public class MultiphaseTimeIntegrator {
    
    /**
     * Explicit Runge-Kutta time stepping with CFL control.
     */
    public void stepRK4(MultiphasePipeSection[] sections, double dt) {
        int n = sections.length;
        double[][] U = getConservativeVariables(sections);
        
        // RK4 stages
        double[][] k1 = calcRHS(sections, U);
        double[][] U1 = addArrays(U, scaleArray(k1, 0.5 * dt));
        
        double[][] k2 = calcRHS(sections, U1);
        double[][] U2 = addArrays(U, scaleArray(k2, 0.5 * dt));
        
        double[][] k3 = calcRHS(sections, U2);
        double[][] U3 = addArrays(U, scaleArray(k3, dt));
        
        double[][] k4 = calcRHS(sections, U3);
        
        // Combine stages
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < U[i].length; j++) {
                U[i][j] += dt / 6.0 * (k1[i][j] + 2*k2[i][j] + 2*k3[i][j] + k4[i][j]);
            }
        }
        
        setConservativeVariables(sections, U);
    }
    
    /**
     * Calculate stable time step based on CFL condition.
     */
    public double calcTimeStep(MultiphasePipeSection[] sections, double cflNumber) {
        double dtMin = Double.MAX_VALUE;
        
        for (MultiphasePipeSection sec : sections) {
            // Wave speeds
            double cGas = sec.getGasSoundSpeed();
            double cLiq = sec.getLiquidSoundSpeed();
            double vg = Math.abs(sec.getGasVelocity());
            double vl = Math.abs(sec.getLiquidVelocity());
            
            // Maximum characteristic speed
            double maxSpeed = Math.max(vg + cGas, vl + cLiq);
            
            // CFL condition
            double dt = cflNumber * sec.getSegmentLength() / maxSpeed;
            dtMin = Math.min(dtMin, dt);
        }
        
        return dtMin;
    }
}
```

### 7. Thermodynamic Coupling

```java
public class ThermodynamicCoupling {
    
    /**
     * Update phase properties using NeqSim flash calculations.
     */
    public void updatePhaseProperties(MultiphasePipeSection section) {
        // Clone and update thermodynamic system
        SystemInterface system = section.getThermoSystem().clone();
        system.setPressure(section.getPressure() / 1e5);  // Convert to bar
        system.setTemperature(section.getTemperature());
        
        // Flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        ops.TPflash();
        system.initPhysicalProperties();
        
        // Extract phase properties
        if (system.hasPhaseType("gas")) {
            PhaseInterface gas = system.getPhase("gas");
            section.setGasDensity(gas.getDensity("kg/m3"));
            section.setGasViscosity(gas.getViscosity("kg/msec"));
            section.setGasSoundSpeed(gas.getSoundSpeed());
            section.setGasEnthalpy(gas.getEnthalpy("J/mol"));
            section.setGasMoleFraction(system.getMoleFraction(gas.getPhaseIndex()));
        }
        
        if (system.hasPhaseType("oil")) {
            PhaseInterface oil = system.getPhase("oil");
            section.setOilDensity(oil.getDensity("kg/m3"));
            section.setOilViscosity(oil.getViscosity("kg/msec"));
            // ... other properties
        }
        
        if (system.hasPhaseType("aqueous")) {
            PhaseInterface water = system.getPhase("aqueous");
            section.setWaterDensity(water.getDensity("kg/m3"));
            // ... other properties
        }
        
        // Mass transfer rates (flashing/condensation)
        section.setMassTransferRate(calcMassTransferRate(section, system));
    }
    
    /**
     * Calculate mass transfer between phases (simplified).
     */
    private double calcMassTransferRate(MultiphasePipeSection section, 
                                         SystemInterface system) {
        // Departure from equilibrium
        double pBubble = system.getBubblePointPressure();
        double pActual = section.getPressure() / 1e5;
        
        if (pActual < pBubble) {
            // Flashing - liquid to gas
            double dP = pBubble - pActual;
            return section.getMassTransferCoefficient() * dP;
        } else {
            // Condensation - gas to liquid
            double dP = pActual - pBubble;
            return -section.getMassTransferCoefficient() * dP;
        }
    }
}
```

---

## Main Solver Class

```java
public class MultiphasePipe extends Pipeline {
    
    private MultiphasePipeSection[] sections;
    private SlugTracker slugTracker;
    private LiquidAccumulation liquidAccumulation;
    private MultiphaseFluxCalculator fluxCalculator;
    private MultiphaseTimeIntegrator timeIntegrator;
    private ThermodynamicCoupling thermoCoupling;
    
    private double cflNumber = 0.5;
    private int numberOfSections = 100;
    private double totalLength;
    private double[] elevationProfile;
    private double[] diameterProfile;
    
    public MultiphasePipe(String name, StreamInterface inStream) {
        super(name, inStream);
        slugTracker = new SlugTracker();
        liquidAccumulation = new LiquidAccumulation();
        fluxCalculator = new MultiphaseFluxCalculator();
        timeIntegrator = new MultiphaseTimeIntegrator();
        thermoCoupling = new ThermodynamicCoupling();
    }
    
    @Override
    public void run(UUID id) {
        // Initialize grid
        initializeSections();
        
        // Steady-state initialization
        runSteadyState();
        
        setCalculationIdentifier(id);
    }
    
    @Override
    public void runTransient(double dt, UUID id) {
        // Adaptive time stepping
        double dtStable = timeIntegrator.calcTimeStep(sections, cflNumber);
        double dtActual = Math.min(dt, dtStable);
        
        int subSteps = (int) Math.ceil(dt / dtActual);
        dtActual = dt / subSteps;
        
        for (int step = 0; step < subSteps; step++) {
            // 1. Update thermodynamic properties
            for (MultiphasePipeSection sec : sections) {
                thermoCoupling.updatePhaseProperties(sec);
            }
            
            // 2. Detect flow regimes
            for (MultiphasePipeSection sec : sections) {
                sec.setFlowRegime(flowRegimeMap.determine(sec));
            }
            
            // 3. Calculate fluxes and advance solution
            timeIntegrator.stepRK4(sections, dtActual);
            
            // 4. Track liquid accumulation
            liquidAccumulation.updateAccumulation(sections, dtActual, 
                getInletLiquidRate());
            
            // 5. Track and propagate slugs
            slugTracker.checkSlugInitiation(sections, dtActual);
            slugTracker.propagateSlugs(sections, dtActual);
            
            // 6. Apply boundary conditions
            applyBoundaryConditions();
        }
        
        // Update outlet stream
        updateOutletStream();
        
        setCalculationIdentifier(id);
    }
    
    /**
     * Get liquid inventory in the pipeline.
     */
    public double getLiquidInventory(String unit) {
        double volume = 0;
        for (MultiphasePipeSection sec : sections) {
            volume += sec.getLiquidHoldup() * sec.getArea() * sec.getSegmentLength();
        }
        
        switch (unit.toLowerCase()) {
            case "m3": return volume;
            case "bbl": return volume * 6.28981;
            case "l": return volume * 1000;
            default: return volume;
        }
    }
    
    /**
     * Get slug statistics.
     */
    public SlugStatistics getSlugStatistics() {
        return slugTracker.getStatistics();
    }
    
    /**
     * Get holdup profile.
     */
    public double[] getHoldupProfile() {
        double[] holdup = new double[sections.length];
        for (int i = 0; i < sections.length; i++) {
            holdup[i] = sections[i].getLiquidHoldup();
        }
        return holdup;
    }
}
```

---

## Phased Implementation Plan

### Phase 1: Drift-Flux Model (3-4 months)

**Scope:**
- Single momentum equation (mixture)
- Drift-flux slip relation
- Basic slug unit model (not tracking individual slugs)
- Liquid accumulation at low points
- Explicit time stepping

**Deliverables:**
- `DriftFluxPipe` class
- Mechanistic flow regime map
- Low-point accumulation logic
- Test cases: ramp-up, ramp-down, pigging

### Phase 2: Two-Fluid Model (4-6 months)

**Scope:**
- Separate gas and liquid momentum
- Interfacial friction correlations
- Full slug tracking (unit cell model)
- AUSM+ numerical scheme
- Three-phase (gas, oil, water)

**Deliverables:**
- `TwoFluidPipe` class
- `SlugTracker` with individual slug dynamics
- Terrain-induced and hydrodynamic slugs
- Validation against OLGA/LedaFlow

### Phase 3: Advanced Features (3-4 months)

**Scope:**
- Pipe networks (junctions, branches)
- Pig tracking
- Wax deposition coupling
- Hydrate risk monitoring
- Corrosion rate estimation

**Deliverables:**
- `PipeNetwork` class
- `PigTracker` class
- Integration with NeqSim wax/hydrate modules

---

## Closure Relations Required

### Wall Friction

| Flow Regime | Correlation |
|-------------|-------------|
| Single-phase | Haaland/Colebrook |
| Stratified | Taitel-Dukler (separate phases) |
| Slug | Slug body + film friction |
| Annular | Core + film model |

### Interfacial Friction

| Model | Application |
|-------|-------------|
| Taitel-Dukler | Stratified flow |
| Andritsos-Hanratty | Wavy stratified |
| Wallis | Annular film |
| Oliemans | Slug bubble zone |

### Holdup Correlations

| Model | Type |
|-------|------|
| Taitel-Dukler | Mechanistic stratified |
| Gregory | Slug holdup |
| Beggs-Brill | Empirical (backup) |
| Bendiksen | Slug bubble holdup |

### Drift-Flux Parameters

| Correlation | C_0 | v_drift |
|-------------|-----|---------|
| Zuber-Findlay (vertical) | 1.2 | 1.53(gσΔρ/ρ_L²)^0.25 |
| Bendiksen (horizontal) | 1.05 | 0.35√(gD) |
| Ferschneider | Variable | Inclination-dependent |

---

## Validation Strategy

### Unit Tests

1. **Shock tube** - Verify numerical scheme captures discontinuities
2. **Steady-state** - Match existing Beggs & Brill
3. **Single slug** - Verify slug propagation velocity
4. **Low-point filling** - Verify accumulation rate

### Integration Tests

1. **Ramp-up scenario** - Increasing rate, observe slug formation
2. **Ramp-down scenario** - Decreasing rate, observe liquid fallback
3. **Pigging** - Pig transit time and liquid delivery
4. **Blowdown** - Depressurization with liquid holdup

### Validation Cases (vs. Commercial Tools)

1. **Tordis flowline** (Statoil benchmark)
2. **Sleipner riser** (slug characteristics)
3. **Prudhoe Bay** (cold restart)
4. **Academic data** (Tulsa, SINTEF)

---

## Performance Considerations

### Computational Cost

| Component | Cost | Optimization |
|-----------|------|--------------|
| Flash calculations | High | Tabulation, caching |
| Flow regime map | Medium | Skip if regime unchanged |
| Flux calculation | Low | Vectorization |
| Slug tracking | Low | Skip if no slugs |

### Recommended Approach

1. **Coarse grid for long-term** (100-200 sections for 10 km)
2. **Flash tabulation** - Pre-compute properties on P-T grid
3. **Adaptive time stepping** - Large Δt when stable
4. **Parallel sections** - OpenMP/SIMD for flux calculations

---

## References

1. Bendiksen, K.H. et al. (1991). "The Dynamic Two-Fluid Model OLGA: Theory and Application". SPE Production Engineering.
2. Taitel, Y. & Dukler, A.E. (1976). "A Model for Predicting Flow Regime Transitions in Horizontal and Near Horizontal Gas-Liquid Flow". AIChE Journal.
3. Issa, R.I. & Kempf, M.H.W. (2003). "Simulation of Slug Flow in Horizontal and Nearly Horizontal Pipes with the Two-Fluid Model". Int. J. Multiphase Flow.
4. Kjeldby, T.K. et al. (2013). "Lagrangian Slug Flow Modeling and Sensitivity on Hydrodynamic Slug Initiation Methods in a Severe Slugging Case". Int. J. Multiphase Flow.
5. Bonizzi, M. & Issa, R.I. (2003). "A Model for Simulating Gas Bubble Entrainment in Two-Phase Horizontal Slug Flow". Int. J. Multiphase Flow.

---

## Conclusion

Implementing a full multiphase transient model is a significant undertaking but highly valuable for:

- **Liquid management** (pigging optimization, slugcatcher sizing)
- **Operability studies** (startup, shutdown, rate changes)
- **Flow assurance** (hydrate risk during transients)
- **Design optimization** (pipeline sizing, riser configurations)

The recommended approach is:

1. **Start with drift-flux** for faster development and validation
2. **Graduate to two-fluid** for slug tracking accuracy
3. **Leverage NeqSim thermodynamics** throughout
4. **Validate against commercial tools** and field data

The existing NeqSim infrastructure (thermodynamics, physical properties, process equipment) provides an excellent foundation for this extension.
