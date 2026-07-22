---
title: "Relief Valve Sizing - API 520/521"
description: "PSV sizing for gas, liquid, and two-phase relief per API 520 and API 521, including fire heat input calculations."
---

# Relief Valve Sizing - API 520 / 521

The `ReliefValveSizing` class calculates required orifice area and mass flow capacity for pressure safety valves (PSVs) using API 520 / 521 methods. It supports gas, liquid, and two-phase service.

**Class**: `neqsim.process.util.fire.ReliefValveSizing`

---

## Capabilities

| Scenario | Method | Standard |
|----------|--------|----------|
| Gas / vapour relief | `calculateRequiredArea()` | API 520 Section 5 |
| Liquid relief | `calculateLiquidReliefArea()` | API 520 Section 5.8 |
| Two-phase relief | `calculateTwoPhaseReliefArea()` | API 520 Appendix D (Leung omega) |
| Fire heat input | `calculateAPI521FireHeatInput()` | API 521 Table 4 |
| Cv rating | `calculateCv()` | ISA/IEC |
| Mass flow capacity | `calculateMassFlowCapacity()` | API 520 |
| Blowdown pressure | `calculateBlowdownPressure()` | API 520/526 |

---

## Gas Relief Sizing

The standard gas PSV sizing determines the required orifice area for critical (choked) or subcritical flow.

### Java Example

```java
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluid = new SystemSrkEos(273.15 + 60, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

ReliefValveSizing psv = new ReliefValveSizing(fluid);
psv.setReliefPressure(110.0);              // bara
psv.setBackPressure(1.013);                // bara
psv.setReliefTemperature(273.15 + 60.0);   // K
psv.setReliefMassRate(5000.0);             // kg/hr

double area = psv.calculateRequiredArea();
double capacity = psv.calculateMassFlowCapacity();
System.out.println("Required area: " + area + " m2");
System.out.println("Mass flow capacity: " + capacity + " kg/hr");
```

---

## Liquid Relief Sizing

For liquid-filled equipment (pumps, blocked-in liquid lines), API 520 Section 5.8 applies.

### Java Example

```java
ReliefValveSizing psv = new ReliefValveSizing(liquidFluid);
psv.setReliefPressure(25.0);
psv.setBackPressure(1.013);
psv.setReliefTemperature(273.15 + 40.0);
psv.setReliefMassRate(50000.0);   // kg/hr

ReliefValveSizing.LiquidPSVSizingResult result = psv.calculateLiquidReliefArea();
System.out.println("Required area (liquid): " + result.getRequiredArea() + " m2");
System.out.println("Kd (liquid): " + result.getKd());
System.out.println("Kw (back-pressure): " + result.getKw());
System.out.println("Kc (combination): " + result.getKc());
System.out.println("Kv (viscosity): " + result.getKv());
```

### Key Correction Factors

| Factor | Symbol | Description | Default |
|--------|--------|-------------|---------|
| Discharge coefficient | $K_d$ | API 520 liquid discharge | 0.65 |
| Back-pressure | $K_w$ | Balanced bellows correction | 1.0 |
| Combination | $K_c$ | Rupture disk + PSV | 1.0 |
| Viscosity | $K_v$ | Re-based viscosity correction | 1.0 |

---

## Two-Phase Relief Sizing

Uses the Leung omega method (API 520 Appendix D) for flashing and non-flashing two-phase mixtures.

### Java Example

```java
ReliefValveSizing psv = new ReliefValveSizing(twoPhaseFluid);
psv.setReliefPressure(80.0);
psv.setBackPressure(5.0);
psv.setReliefTemperature(273.15 + 100.0);
psv.setReliefMassRate(30000.0);
psv.setInletVapourMassFraction(0.3);  // quality at inlet

double area = psv.calculateTwoPhaseReliefArea();
System.out.println("Two-phase required area: " + area + " m2");
```

### Method

The Leung omega parameter characterises the compressibility of the two-phase mixture:

$$
\omega = \frac{x \cdot v_g + (1-x) \cdot v_l}{v_{mix}} \cdot \frac{c_p \cdot T \cdot P}{h_{fg}^2}
$$

The critical pressure ratio and mass flux are then calculated from $\omega$ to obtain the required area.

---

## API 521 Fire Heat Input

Calculates the total heat absorption rate for fire-exposed equipment per API 521 Table 4. Used to determine thermal relief requirements.

### Java Example

```java
double wettedArea = 80.0;  // m2
boolean drainage = true;    // adequate drainage and firefighting

double Q = ReliefValveSizing.calculateAPI521FireHeatInput(wettedArea, drainage);
System.out.println("Fire heat input: " + Q + " W");
System.out.println("Fire heat input: " + (Q / 1000.0) + " kW");
```

### Correlations

For adequate drainage and firefighting facilities:

$$
Q = 43200 \cdot F \cdot A_{wetted}^{0.82} \quad \text{(W)}
$$

where $F$ is the environmental factor (default 1.0) and $A_{wetted}$ is in m$^2$.

For inadequate drainage:

$$
Q = 70900 \cdot F \cdot A_{wetted}^{0.82} \quad \text{(W)}
$$

---

## Python Example

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ReliefValveSizing = jneqsim.process.util.fire.ReliefValveSizing

fluid = SystemSrkEos(273.15 + 60.0, 100.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

psv = ReliefValveSizing(fluid)
psv.setReliefPressure(110.0)
psv.setBackPressure(1.013)
psv.setReliefTemperature(273.15 + 60.0)
psv.setReliefMassRate(5000.0)

area = psv.calculateRequiredArea()
print(f"Required orifice area: {area:.6f} m2")

# Fire heat input
Q = ReliefValveSizing.calculateAPI521FireHeatInput(80.0, True)
print(f"Fire heat input: {Q/1000:.0f} kW")
```

---

## Related Documentation

- [PSV Dynamic Sizing](psv_dynamic_sizing_example.md) - Dynamic relief valve simulation
- [Fire and Blowdown Capabilities](fire_blowdown_capabilities.md) - Blowdown and fire scenario simulation
- [Safety Engineering Overview](index.md) - Full safety tools overview
