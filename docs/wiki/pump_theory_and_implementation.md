# Pump Theory and Implementation in NeqSim

## Theoretical Background

### 1. Euler Pump Equation
The fundamental pump equation (Euler equation) relates the head developed by a pump to the change in angular momentum:

```
H = (u₂·C₂ᵤ - u₁·C₁ᵤ) / g
```

Where:
- H = pump head [m]
- u = blade velocity [m/s]
- Cᵤ = tangential component of absolute velocity [m/s]
- g = gravitational acceleration [m/s²]

For centrifugal pumps, this simplifies to a relationship between head, flow rate, and rotational speed.

### 2. Affinity Laws (Similarity Laws)
The affinity laws relate pump performance at different speeds and impeller sizes:

**Speed variation (same impeller):**
- Flow: Q₂/Q₁ = N₂/N₁
- Head: H₂/H₁ = (N₂/N₁)²
- Power: P₂/P₁ = (N₂/N₁)³

**Diameter variation (same speed):**
- Flow: Q₂/Q₁ = (D₂/D₁)³
- Head: H₂/H₁ = (D₂/D₁)²
- Power: P₂/P₁ = (D₂/D₁)⁵

### 3. Pump Curves
Pump performance is characterized by curves showing:
- **Head vs. Flow (H-Q curve)**: Shows head developed at various flow rates
- **Efficiency vs. Flow (η-Q curve)**: Shows efficiency variation with flow
- **Power vs. Flow (P-Q curve)**: Shows power consumption with flow

Typical curve characteristics:
- Head decreases with increasing flow
- Efficiency has a peak (best efficiency point - BEP)
- Operating away from BEP reduces efficiency and can cause mechanical issues

### 4. Hydraulic Power and Efficiency

**Hydraulic power:**
```
P_hydraulic = ρ·g·Q·H
```

Where:
- ρ = fluid density [kg/m³]
- g = gravitational acceleration [m/s²]
- Q = volumetric flow rate [m³/s]
- H = head [m]

**Alternative form using pressure:**
```
P_hydraulic = Q·ΔP
```

**Shaft power:**
```
P_shaft = P_hydraulic / η
```

Where η is the pump efficiency (decimal, not percentage).

**Enthalpy rise:**
```
ΔH = P_shaft / ṁ = (Q·ΔP) / (ρ·Q·η) = ΔP / (ρ·η)
```

### 5. Net Positive Suction Head (NPSH)

**NPSH Available (NPSHₐ):**
```
NPSHₐ = (P_suction - P_vapor) / (ρ·g) + v²/(2g)
```

**NPSH Required (NPSHᵣ):**
- Provided by pump manufacturer
- Function of flow rate and pump design
- Must satisfy: NPSHₐ > NPSHᵣ (typically NPSHₐ ≥ 1.3·NPSHᵣ)

Insufficient NPSH leads to cavitation, causing:
- Performance degradation
- Noise and vibration
- Mechanical damage

### 6. Operating Limits

**Surge condition:**
- Occurs at low flow rates
- Characterized by flow reversal and instability
- Identified by positive slope in H-Q curve (dH/dQ > 0)

**Stonewall condition:**
- Occurs at high flow rates
- Maximum achievable flow limited by sonic velocity
- Characterized by steep drop in efficiency

---

## Current Implementation Issues

### Issue 1: Inconsistent Head Unit Handling

**Location:** `Pump.java`, lines 148-160

**Problem:**
```java
if (getPumpChart().getHeadUnit().equals("meter")) {
  deltaP = pumpHead * 1000.0 * ThermodynamicConstantsInterface.gravity / 1.0E5;
} else {
  double rho = inStream.getThermoSystem().getDensity("kg/m3");
  deltaP = pumpHead * rho * 1000.0 / 1.0E5;
}
```

**Issues:**
1. When headUnit = "meter", the conversion formula is incorrect:
   - Should be: ΔP [Pa] = H [m] × ρ [kg/m³] × g [m/s²]
   - Current: ΔP [bar] = H [m] × 1000 × g / 1.0E5
   - The factor of 1000 suggests confusion between units
   - Missing density term!

2. When headUnit = "kJ/kg" (specific energy):
   - Formula deltaP = pumpHead × ρ × 1000 / 1.0E5 is dimensionally incorrect
   - kJ/kg is already specific energy (J/kg)
   - Should be: ΔP [Pa] = specific_energy [J/kg] × ρ [kg/m³]

**Correct formulas:**
```java
// For head in meters
ΔP [Pa] = H [m] × ρ [kg/m³] × g [m/s²]

// For specific energy in kJ/kg
ΔP [Pa] = E [kJ/kg] × 1000 × ρ [kg/m³]

// Or equivalently using pressure-enthalpy relationship
ΔH [J/kg] = ΔP [Pa] / (ρ [kg/m³] × η)
```

### Issue 2: Efficiency Calculation Error

**Location:** `Pump.java`, lines 155-158

**Problem:**
```java
double dH = thermoSystem.getFlowRate("kg/sec") / thermoSystem.getDensity("kg/m3")
    * (thermoSystem.getPressure("Pa") - inStream.getThermoSystem().getPressure("Pa"))
    / (isentropicEfficiency / 100.0);
```

**Issues:**
1. Division by `(isentropicEfficiency / 100.0)` assumes efficiency is stored as percentage
2. But from pump chart: `getEfficiency()` already returns percentage
3. This causes efficiency to be divided by 100 twice effectively
4. Results in much higher power consumption than actual

**Analysis of units:**
```
dH = (kg/s) / (kg/m³) × Pa / (η/100)
   = m³/s × Pa / (η/100)
   = W / (η/100)
   = W × 100/η  <- Wrong! Should be W/η
```

**Correct approach:**
```java
// If efficiency is in percentage (0-100)
double eta_decimal = isentropicEfficiency / 100.0;
double dH = volumetricFlow * deltaP / eta_decimal;

// Or if efficiency is already decimal (0-1)
double dH = volumetricFlow * deltaP / isentropicEfficiency;
```

### Issue 3: Missing Density Correction

**Problem:** Pump curves are typically measured with water at standard conditions. When pumping different fluids, corrections are needed.

**Required correction:**
```
H_actual = H_chart × (ρ_chart / ρ_actual)  // For head in meters
```

This is not implemented in the current code.

### Issue 4: Incomplete Affinity Law Implementation

**Location:** `PumpChart.java`, lines 100-102, 119-121

**Current implementation:**
```java
redflow[i][j] = flow[i][j] / speed[i];
redEfficiency[i][j] = efficiency[i][j];
redhead[i][j] = head[i][j] / speed[i] / speed[i];
```

**Issues:**
1. Reduced variables are correctly calculated
2. However, efficiency correction for speed changes is missing
3. Fan law correction fitting is started but not fully utilized
4. No validation that operating point is within valid range

**Theory:** Efficiency typically varies slightly with speed:
```
η₂ ≈ η₁ - C × |N₂/N₁ - 1|
```

### Issue 5: Missing NPSH Calculations

**Problem:** No NPSH checks are implemented, which means:
- Cannot predict cavitation
- Cannot determine minimum suction pressure
- No warning when operating conditions may damage pump

**Required additions:**
- Calculate NPSHₐ from suction conditions
- Read/interpolate NPSHᵣ from pump data
- Warn when NPSHₐ < 1.3 × NPSHᵣ

### Issue 6: Limited Surge and Stonewall Detection

**Location:** `PumpChart.java`, lines 219-235

**Current implementation:**
```java
public boolean checkSurge1(double flow, double head) {
  double derivative = reducedHeadFitterFunc.polynomialDerivative().value(flow / referenceSpeed);
  return derivative > 0.0;
}
```

**Issues:**
1. Surge check uses derivative but doesn't properly identify surge line
2. No protection mechanism (e.g., minimum flow recirculation)
3. Stonewall check only compares to maxFlow, doesn't check efficiency degradation
4. No warnings logged during operation

### Issue 7: Power Calculation Inconsistency

**Location:** `Pump.java`, line 161

**Problem:**
When using pump charts, the code doesn't use the fitted curves to calculate power. Instead, it recalculates from pressure rise, which may not match the manufacturer's power curve.

**Better approach:**
Add power curve to pump chart and use it directly, or calculate from:
```
P_shaft = ρ × g × Q × H / η
```

---

## Recommended Improvements

### 1. Fix Head and Pressure Calculations

Implement proper unit handling:
```java
double deltaP_Pa; // Pressure rise in Pa
double densityInlet = inStream.getThermoSystem().getDensity("kg/m3");

if (getPumpChart().getHeadUnit().equals("meter")) {
  // Head in meters: ΔP = ρ·g·H
  deltaP_Pa = pumpHead * densityInlet * ThermodynamicConstantsInterface.gravity;
} else if (getPumpChart().getHeadUnit().equals("kJ/kg")) {
  // Specific energy: ΔP = E·ρ
  deltaP_Pa = pumpHead * 1000.0 * densityInlet; // Convert kJ to J
} else {
  throw new RuntimeException("Unsupported head unit: " + getPumpChart().getHeadUnit());
}

// Convert to bara for system
double deltaP_bar = deltaP_Pa / 1.0e5;
thermoSystem.setPressure(inStream.getPressure() + deltaP_bar);
```

### 2. Fix Efficiency Handling

Standardize efficiency as percentage in storage, decimal in calculations:
```java
// Get efficiency as percentage from chart
double efficiencyPercent = getPumpChart().getEfficiency(flow_m3hr, speed);
double efficiencyDecimal = efficiencyPercent / 100.0;

// Calculate hydraulic power
double volumetricFlow = thermoSystem.getFlowRate("kg/sec") / densityInlet; // m³/s
double hydraulicPower = volumetricFlow * deltaP_Pa; // W

// Calculate shaft power (with losses)
double shaftPower = hydraulicPower / efficiencyDecimal; // W

// Calculate enthalpy rise
double dH = shaftPower; // Already in W = J/s
```

### 3. Add NPSH Calculations

```java
public double getNPSHAvailable() {
  double P_suction = inStream.getPressure("Pa");
  double P_vapor = inStream.getThermoSystem().getPhase(0).getPressure("Pa"); // Vapor pressure
  double rho = inStream.getThermoSystem().getDensity("kg/m3");
  double velocity = inStream.getVelocity(); // m/s
  
  return (P_suction - P_vapor) / (rho * ThermodynamicConstantsInterface.gravity)
         + velocity * velocity / (2.0 * ThermodynamicConstantsInterface.gravity);
}

public boolean checkCavitation() {
  double NPSHa = getNPSHAvailable();
  double NPSHr = getNPSHRequired(flow, speed); // From pump data
  return NPSHa < 1.3 * NPSHr;
}
```

### 4. Improve Curve Fitting and Extrapolation

- Add polynomial order selection based on data quality
- Implement rational function fitting for better extrapolation
- Add confidence intervals for extrapolated values
- Warn when operating outside measured range

### 5. Add Operating Envelope Validation

```java
public PumpOperatingStatus getOperatingStatus(double flow, double speed) {
  if (checkSurge2(flow, speed)) {
    return PumpOperatingStatus.SURGE;
  }
  if (checkStoneWall(flow, speed)) {
    return PumpOperatingStatus.STONEWALL;
  }
  double efficiency = getEfficiency(flow, speed);
  if (efficiency < 0.5 * maxEfficiency) {
    return PumpOperatingStatus.LOW_EFFICIENCY;
  }
  return PumpOperatingStatus.NORMAL;
}
```

### 6. Add Pump Specific Speed Calculation

```java
public double getSpecificSpeed() {
  // Ns = N·√Q / H^(3/4)
  // Used to classify pump type and check design consistency
  double Q_BEP = getBestEfficiencyFlowRate();
  double H_BEP = getHead(Q_BEP, referenceSpeed);
  return referenceSpeed * Math.sqrt(Q_BEP) / Math.pow(H_BEP, 0.75);
}
```

---

## Testing Requirements

### Unit Tests Needed

1. **Affinity Law Tests**: Verify Q, H, P scale correctly with speed
2. **Head Conversion Tests**: Test meter ↔ kJ/kg conversions with different fluids
3. **Efficiency Tests**: Verify power calculations match theory
4. **NPSH Tests**: Test cavitation detection with various fluids
5. **Curve Fitting Tests**: Verify polynomial fits match input data
6. **Extrapolation Tests**: Test behavior outside measured range
7. **Surge Detection Tests**: Verify surge line identification
8. **Multi-fluid Tests**: Test with water, hydrocarbons, and mixtures

### Integration Tests Needed

1. Test pump in process system with recycles
2. Test with compressor (similar equations)
3. Test with varying inlet conditions
4. Test transient behavior (startup, shutdown)

---

## References

1. Centrifugal Pumps, I.J. Karassik et al., McGraw-Hill
2. Pump Handbook, Igor J. Karassik, McGraw-Hill
3. API 610 - Centrifugal Pumps for Petroleum, Petrochemical and Natural Gas Industries
4. ISO 9906 - Rotodynamic pumps - Hydraulic performance acceptance tests
5. Affinity Laws: https://en.wikipedia.org/wiki/Affinity_laws
