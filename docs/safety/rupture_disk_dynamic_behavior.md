# Rupture Disk Dynamic Behavior

This document explains the rupture disk implementation in NeqSim, demonstrating the key difference between rupture disks and pressure safety valves (PSVs).

## What is a Rupture Disk?

A **rupture disk** (also called a bursting disc) is a non-reclosing pressure relief device that:
- Bursts at a specific set pressure
- Opens rapidly and remains fully open
- **Cannot reseat** - it's a one-time use device
- Must be physically replaced after activation

This is fundamentally different from a **safety valve** which:
- Opens at set pressure
- **Closes again** when pressure drops to blowdown/reseat pressure
- Can cycle multiple times
- Uses hysteresis to prevent chattering

## Applications

Rupture disks are typically used for:
1. **Primary relief** for rapid pressure rise scenarios (runaway reactions)
2. **Backup protection** in series with safety valves
3. **Corrosive/fouling services** where PSVs would fail
4. **Emergency relief** where instant full opening is required
5. **Low maintenance** applications

## Implementation

### RuptureDisk Class

```java
RuptureDisk disk = new RuptureDisk("RD-001", inletStream);
disk.setBurstPressure(55.0);  // bara - disk ruptures at this pressure
disk.setFullOpenPressure(57.75);  // bara - fully open (typically 5% above burst)
disk.setOutletPressure(1.0, "bara");
disk.setCv(150.0);
disk.setCalculateSteadyState(false);
```

### Key Parameters

| Parameter | Description | Typical Value |
|-----------|-------------|---------------|
| Burst Pressure | Pressure at which disk ruptures | Set by design |
| Full Open Pressure | Pressure for 100% opening | 105-110% of burst |
| Cv | Flow coefficient | Sized for relief scenario |

### Automatic Behavior in runTransient()

The rupture disk automatically:
1. **Monitors inlet pressure** each time step
2. **Ruptures** when pressure ≥ burst pressure
3. **Remains fully open** regardless of subsequent pressure changes
4. **Tracks state** with `hasRuptured()` flag

## Comparison: Rupture Disk vs Safety Valve

### Safety Valve (PSV) with Hysteresis
```
Pressure rises → Opens at 55 bara → Relieves pressure
Pressure drops → Stays open until 51.15 bara (blowdown)
Pressure below blowdown → Closes → Can reopen if needed
```

### Rupture Disk
```
Pressure rises → Bursts at 55 bara → Relieves pressure
Pressure drops → STAYS 100% OPEN
Pressure at any level → STAYS 100% OPEN (one-time device)
```

## Example: Blocked Outlet Scenario

```java
// Setup separator with gas splitter
Separator separator = new Separator("HP Separator", feedStream);
Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 2);

// Normal operation path
ThrottlingValve pcv = new ThrottlingValve("PCV-001", gasSplitter.getSplitStream(0));
pcv.setPercentValveOpening(50.0);

// Emergency relief path
RuptureDisk disk = new RuptureDisk("RD-001", gasSplitter.getSplitStream(1));
disk.setBurstPressure(55.0);
disk.setFullOpenPressure(57.75);

// Dynamic simulation
UUID id = UUID.randomUUID();
for (int i = 0; i < numSteps; i++) {
    double time = i * dt;
    
    // Simulate PCV blockage at t=50s
    if (time >= 50.0 && time < 51.0) {
        pcv.setPercentValveOpening(1.0);
    }
    
    // Simulate PCV recovery at t=200s
    if (time >= 200.0 && time < 201.0) {
        pcv.setPercentValveOpening(50.0);
    }
    
    // Run transient - disk bursts automatically
    separator.runTransient(dt, id);
    gasSplitter.runTransient(dt, id);
    pcv.runTransient(dt, id);
    disk.runTransient(dt, id);  // Automatic rupture control
}
```

## Test Results

From `RuptureDiskDynamicTest`:

### Behavior Sequence
```
Time:   0-120s: Normal operation, disk closed, pressure below 55 bara
Time:   ~130s: Disk ruptures at 55 bara
Time: 140-200s: Pressure controlled at ~53 bara, disk 100% open
Time: 200-300s: PCV reopens, pressure drops to 30 bara
              → Disk STILL 100% open!
```

### Key Observations

| Metric | Value |
|--------|-------|
| Burst pressure | 55.0 bara |
| Max pressure | 55.35 bara |
| Max relief flow | 5950 kg/hr |
| Final pressure | 30.5 bara |
| **Final disk opening** | **100%** |

**Critical Behavior**: Disk remained fully open even though pressure dropped **24.5 bara below** the burst pressure!

## Disk Reset (Simulation Only)

For simulation purposes, you can reset a ruptured disk:

```java
disk.reset();  // Simulates disk replacement
// Disk is now unruptured and closed
// In reality, you would physically replace the disk
```

## Best Practices

1. **Sizing**: Size rupture disks for full relief capacity - they open instantly
2. **Series Protection**: Often used upstream of PSVs to protect them from corrosion
3. **Burst Tolerance**: Account for manufacturing tolerance (typically ±5%)
4. **Rapid Opening**: Full open pressure is typically 5% above burst (vs 10% for PSV)
5. **One-Time Use**: Plan for system shutdown and disk replacement after rupture
6. **Testing**: Use `reset()` method in simulations to test multiple scenarios

## When to Use Rupture Disk vs PSV

### Use Rupture Disk When:
- ✅ Pressure rise is extremely rapid
- ✅ Medium is highly corrosive or fouling
- ✅ Instant full area opening is required
- ✅ Low maintenance is critical
- ✅ Operating as backup to PSV

### Use Safety Valve When:
- ✅ Pressure relief is cyclic
- ✅ Need reseating capability
- ✅ Clean, non-fouling service
- ✅ Controlled gradual opening preferred
- ✅ Want to avoid system shutdown

## See Also

- Rupture disk class: `RuptureDisk.java`
- Test implementation: `RuptureDiskDynamicTest.java`
- PSV comparison: `psv_dynamic_sizing_example.md`
- ASME Section VIII - Pressure Relief Devices
- API 520 Part 1 - Sizing and Selection
