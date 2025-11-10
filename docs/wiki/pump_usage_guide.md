# Pump Usage Guide - Quick Reference

## Basic Pump Setup

### Simple Pump (Specified Pressure)
```java
SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
fluid.addComponent("water", 1.0);
fluid.setTotalFlowRate(100.0, "m3/hr");

Stream feed = new Stream("Feed", fluid);
feed.setTemperature(20.0, "C");
feed.setPressure(1.0, "bara");

Pump pump = new Pump("Pump1", feed);
pump.setOutletPressure(10.0, "bara");
pump.setIsentropicEfficiency(0.75); // 75% efficiency
pump.run();

double power = pump.getPower("kW");
double outletTemp = pump.getOutletStream().getTemperature("C");
```

---

## Using Pump Curves

### Setting Up Pump Curves
```java
// Define pump performance at different speeds
double[] speed = new double[] {1000.0, 1500.0, 2000.0};

// Flow rates in m³/hr for each speed
double[][] flow = new double[][] {
    {10.0, 20.0, 30.0, 40.0, 50.0, 60.0},
    {15.0, 30.0, 45.0, 60.0, 75.0, 90.0},
    {20.0, 40.0, 60.0, 80.0, 100.0, 120.0}
};

// Head in meters for each speed and flow
double[][] head = new double[][] {
    {120.0, 118.0, 115.0, 110.0, 103.0, 94.0},
    {270.0, 265.5, 258.8, 247.5, 231.8, 211.5},
    {480.0, 472.0, 460.0, 440.0, 412.0, 376.0}
};

// Efficiency in % for each speed and flow
double[][] efficiency = new double[][] {
    {65.0, 72.0, 78.0, 82.0, 80.0, 74.0},
    {66.0, 73.0, 79.0, 83.0, 81.0, 75.0},
    {67.0, 74.0, 80.0, 84.0, 82.0, 76.0}
};

pump.getPumpChart().setCurves(new double[]{}, speed, flow, head, efficiency);
pump.getPumpChart().setHeadUnit("meter"); // or "kJ/kg"
pump.setSpeed(1500.0); // Set operating speed in rpm
```

### Head Units

**Meters (most common):**
```java
pump.getPumpChart().setHeadUnit("meter");
// Head represents height of fluid column
// ΔP = ρ × g × H
```

**Specific Energy (kJ/kg):**
```java
pump.getPumpChart().setHeadUnit("kJ/kg");
// Head represents specific energy
// ΔP = E × ρ
```

---

## NPSH Monitoring

### Enable Cavitation Detection
```java
pump.setCheckNPSH(true);
pump.setNPSHMargin(1.3); // Recommended: 1.1-1.5

pump.run();

// Check for cavitation risk
if (pump.isCavitating()) {
    double npsha = pump.getNPSHAvailable();
    double npshr = pump.getNPSHRequired();
    System.out.println("Warning: NPSHa = " + npsha + " m, NPSHr = " + npshr + " m");
    // Take corrective action: increase suction pressure or decrease temperature
}
```

### Manual NPSH Check
```java
double npsha = pump.getNPSHAvailable();
double npshr = pump.getNPSHRequired();

if (npsha < 1.3 * npshr) {
    // Insufficient NPSH - risk of cavitation
    // Solutions:
    // 1. Increase suction pressure
    // 2. Decrease fluid temperature
    // 3. Reduce pump speed
    // 4. Select different pump
}
```

---

## Operating Status Monitoring

### Check Pump Operating Region
```java
double flow = feed.getFlowRate("m3/hr");
double speed = pump.getSpeed();

String status = pump.getPumpChart().getOperatingStatus(flow, speed);

switch (status) {
    case "OPTIMAL":
        // Operating near best efficiency point
        break;
    case "NORMAL":
        // Operating within acceptable range
        break;
    case "LOW_EFFICIENCY":
        // Operating far from BEP - inefficient
        // Consider adjusting speed or selecting different pump
        break;
    case "SURGE":
        // Flow too low - risk of instability and damage
        // Increase flow or reduce speed immediately
        break;
    case "STONEWALL":
        // Flow too high - maximum capacity reached
        // Reduce flow or increase speed
        break;
}
```

### Find Best Efficiency Point
```java
double bepFlow = pump.getPumpChart().getBestEfficiencyFlowRate();
double bepHead = pump.getPumpChart().getHead(bepFlow, speed);
double bepEfficiency = pump.getPumpChart().getEfficiency(bepFlow, speed);

System.out.println("Best efficiency: " + bepEfficiency + "% at " + bepFlow + " m³/hr");
```

---

## Pump Selection and Sizing

### Calculate Specific Speed
```java
double ns = pump.getPumpChart().getSpecificSpeed();

if (ns < 1000) {
    System.out.println("Radial flow (centrifugal) pump");
} else if (ns < 4000) {
    System.out.println("Mixed flow pump");
} else {
    System.out.println("Axial flow pump");
}
```

### Variable Speed Operation
```java
// Affinity laws: Q ∝ N, H ∝ N², P ∝ N³

double baseSpeed = 1500.0;
double baseFlow = 50.0; // m³/hr
double baseHead = pump.getPumpChart().getHead(baseFlow, baseSpeed);

// To increase head by 44% (factor of 1.44 = 1.2²):
double newSpeed = baseSpeed * 1.2;
double newFlow = baseFlow * 1.2;
double newHead = baseHead * 1.44;

pump.setSpeed(newSpeed);
// Efficiency stays approximately constant at same reduced flow
```

---

## Common Patterns

### Pump with Minimum Flow Protection
```java
pump.setMinimumFlow(0.05); // kg/sec

// When flow drops below minimum, pump idles with no pressure rise
// In practice, add minimum flow recirculation loop
```

### Multi-stage Pump System
```java
Stream stage1Out = new Stream("Stage 1 Out");
Pump stage1 = new Pump("Stage 1", feed);
stage1.setOutletPressure(5.0, "bara");
stage1.setOutStream(stage1Out);

Pump stage2 = new Pump("Stage 2", stage1Out);
stage2.setOutletPressure(10.0, "bara");

// Total head = stage1 head + stage2 head
```

### Pump with Different Chart Type
```java
// Default: Simple fan law interpolation
pump.setPumpChartType("fan law");

// Alternative: Map lookup with extrapolation
pump.setPumpChartType("interpolate and extrapolate");
```

---

## Troubleshooting

### Low Outlet Pressure
1. Check pump curve covers operating flow rate
2. Verify speed setting matches curve
3. Check for cavitation (low NPSH)
4. Verify head unit setting ("meter" vs "kJ/kg")

### High Power Consumption
1. Operating far from BEP - reduce or increase flow
2. Check efficiency curve - may need different pump
3. Verify outlet pressure requirement is reasonable

### Cavitation Warnings
1. Increase suction pressure
2. Reduce fluid temperature
3. Reduce pump speed
4. Check for air entrainment
5. Verify NPSH_r curve is accurate

### Surge/Instability
1. Increase minimum flow setpoint
2. Add recirculation line from discharge to suction
3. Reduce speed if possible
4. Check for blockage downstream

---

## Performance Calculations

### Hydraulic Power
```java
double rho = feed.getThermoSystem().getDensity("kg/m3");
double Q = feed.getFlowRate("m3/s");
double H = pump.getPumpChart().getHead(feed.getFlowRate("m3/hr"), pump.getSpeed());
double g = 9.81; // m/s²

double hydraulicPower = rho * g * Q * H; // Watts
```

### Shaft Power (with losses)
```java
double efficiency = pump.getIsentropicEfficiency() / 100.0; // Convert % to decimal
double shaftPower = hydraulicPower / efficiency;
```

### Energy Cost Estimate
```java
double powerKW = pump.getPower("kW");
double hoursPerYear = 8760;
double costPerKWh = 0.10; // $/kWh

double annualEnergyCost = powerKW * hoursPerYear * costPerKWh;
System.out.println("Annual energy cost: $" + annualEnergyCost);
```

---

## Best Practices

1. **Always set pump curves when available** - more accurate than fixed efficiency
2. **Enable NPSH checking** for all liquid pumps
3. **Monitor operating status** to avoid damage and inefficiency
4. **Operate near BEP** (±20% flow) when possible
5. **Use correct head units** - "meter" for liquid pumps
6. **Set realistic efficiency** - typical centrifugal pumps: 70-85%
7. **Consider minimum flow** - typically 10-20% of BEP flow
8. **Document curve source** - manufacturer data sheets
9. **Validate with measurements** - adjust curves if needed
10. **Check affinity laws** - verify speed changes follow theory

---

## Example: Complete Pump System

```java
// Create fluid system
SystemInterface water = new SystemSrkEos(298.15, 1.5);
water.addComponent("water", 1.0);
water.setTemperature(25.0, "C");
water.setPressure(1.5, "bara");
water.setTotalFlowRate(75.0, "m3/hr");

Stream feed = new Stream("Pump Feed", water);
feed.run();

// Create pump with curve
Pump pump = new Pump("Booster Pump", feed);

double[] speed = new double[] {1450.0};
double[][] flow = new double[][] {{30, 50, 70, 90, 110, 130}};
double[][] head = new double[][] {{45, 44, 42, 38, 32, 24}};
double[][] eff = new double[][] {{68, 76, 82, 84, 80, 70}};

pump.getPumpChart().setCurves(new double[]{}, speed, flow, head, eff);
pump.getPumpChart().setHeadUnit("meter");
pump.setSpeed(1450.0);
pump.setCheckNPSH(true);
pump.setNPSHMargin(1.3);

// Run simulation
pump.run();

// Check results
System.out.println("Outlet pressure: " + pump.getOutletPressure() + " bara");
System.out.println("Power: " + pump.getPower("kW") + " kW");
System.out.println("Outlet temp: " + pump.getOutletStream().getTemperature("C") + " °C");
System.out.println("NPSHa: " + pump.getNPSHAvailable() + " m");
System.out.println("Status: " + pump.getPumpChart().getOperatingStatus(75.0, 1450.0));

if (pump.isCavitating()) {
    System.out.println("WARNING: Cavitation risk!");
}
```
