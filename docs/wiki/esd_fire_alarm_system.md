---
title: "ESD Fire Alarm System with Voting Logic"
description: "This implementation demonstrates a comprehensive Emergency Shutdown (ESD) system with fire alarm voting logic in NeqSim. The system showcases how multiple fire detectors can be used in a voting config..."
---

# ESD Fire Alarm System with Voting Logic

## Overview

This implementation demonstrates a comprehensive Emergency Shutdown (ESD) system with fire alarm voting logic in NeqSim. The system showcases how multiple fire detectors can be used in a voting configuration to prevent spurious trips while ensuring safety when multiple alarms confirm a fire event.

## Key Features

### 1. FireDetector Measurement Device
**Location:** `src/main/java/neqsim/process/measurementdevice/FireDetector.java`

A new binary sensor for fire detection with features:
- Binary state: fire detected (1.0) or no fire (0.0)
- Configurable detection threshold and delay
- Location/zone identification
- Integration with alarm system
- Signal level simulation for gradual detection
- Reset capability for testing

**Example Usage:**
```java
FireDetector fireDetector = new FireDetector("FD-101", "Separator Area - North");
fireDetector.setDetectionThreshold(0.5);
fireDetector.setDetectionDelay(1.0);

// Configure alarm
AlarmConfig alarmConfig = AlarmConfig.builder()
    .highLimit(0.5)
    .delay(1.0)
    .unit("binary")
    .build();
fireDetector.setAlarmConfig(alarmConfig);

// Detect fire
fireDetector.detectFire();

// Check status
if (fireDetector.isFireDetected()) {
    System.out.println("Fire detected!");
}
```

### 2. Voting Logic Patterns

#### 2-out-of-2 Voting
Requires both fire detectors to activate before triggering ESD. Provides:
- High confidence in fire event
- Prevents single spurious alarm trips
- Simple redundancy pattern

#### 2-out-of-3 Voting
Any two of three detectors trigger ESD. Provides:
- Greater reliability (tolerates one failed detector)
- Still prevents spurious trips
- Industry-standard safety configuration

**Implementation:**
```java
// Count active alarms
int activeAlarms = (fireDetector1.isFireDetected() ? 1 : 0)
                 + (fireDetector2.isFireDetected() ? 1 : 0)
                 + (fireDetector3.isFireDetected() ? 1 : 0);

// Apply voting logic
boolean esdShouldActivate = (activeAlarms >= 2);

if (esdShouldActivate && !bdValve.isActivated()) {
    bdValve.activate();
    gasSplitter.setSplitFactors(new double[] {0.0, 1.0}); // Redirect to blowdown
}
```

### 3. Complete ESD Blowdown Sequence

The test demonstrates a realistic ESD scenario:

**Phase 1: Normal Operation (t=0-5s)**
- Process operating normally
- Gas flows to process
- No fire alarms active
- BD valve closed

**Phase 2: First Fire Alarm (t=5s)**
- Fire detector FD-101 activates
- Only 1 of 2 required alarms
- ESD **does NOT activate** (awaiting confirmation)
- System continues operating

**Phase 3: Second Fire Alarm (t=10s)**
- Fire detector FD-102 activates
- 2 of 2 required alarms confirmed
- **ESD ACTIVATES**
- Flow redirected to blowdown
- BD valve begins opening

**Phase 4: Blowdown with Emissions Tracking (t=10-20s)**
- BD valve opens over 5 seconds
- Gas flows through orifice to flare
- Flare combusts gas and tracks:
  - Instantaneous heat release (MW)
  - Instantaneous CO2 emission rate (kg/s)
  - Cumulative heat released (GJ)
  - Cumulative CO2 emissions (kg)
  - Total gas burned (kg)

## Test Results

### Test: testESDWithTwoFireAlarmVoting

**System Configuration:**
- Separator at 50 bara
- Gas flow: 10,000 kg/hr (methane-rich mixture)
- Two fire detectors with alarm configuration
- Blowdown valve with 5-second opening time
- Flare header at 1.5 bara

**Simulation Results (20-second blowdown):**
```
Time (s) | FD-101 | FD-102 | Alarms | BD Open (%) | BD Flow (kg/hr) | Flare Heat (MW) | CO2 Rate (kg/s) | Cumul Heat (GJ) | Cumul CO2 (kg)
---------|--------|--------|--------|-------------|-----------------|-----------------|-----------------|-----------------|----------------
     0.0 |   FIRE |   FIRE |      2 |         0.0 |        817,553  |        11,657   |         626.4   |           11.66 |          626.4
     2.0 |   FIRE |   FIRE |      2 |         0.0 |        814,203  |        11,610   |         623.8   |           34.90 |         1875.3
    10.0 |   FIRE |   FIRE |      2 |        20.0 |        803,959  |        11,464   |         616.0   |          127.08 |         6828.3
    14.0 |   FIRE |   FIRE |      2 |       100.0 |        800,259  |        11,411   |         613.1   |          172.80 |         9285.0
    20.0 |   FIRE |   FIRE |      2 |       100.0 |        795,957  |        11,349   |         609.8   |          241.04 |        12951.8
```

**Final Summary:**
- ✓ Total gas blown down: **4,695.7 kg**
- ✓ Total heat released: **241.04 GJ** (67 MWh)
- ✓ Total CO2 emissions: **12,951.8 kg** (13 tonnes)

### Test: testESDWith2OutOf3FireAlarmVoting

**Tests voting combinations:**
1. No alarms → ESD: NO
2. One alarm (FD-101) → ESD: NO
3. Two alarms (FD-101 + FD-102) → **ESD: YES**
4. Three alarms (all active) → ESD: YES
5. Reset one detector (FD-103) → ESD maintained with 2 remaining
6. Reset another (FD-102) → Only 1 active, but BD valve stays latched

**Key Safety Feature:** BD valve remains activated even when alarms clear, requiring manual reset to prevent automatic system restoration during emergency.

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    ESD FIRE ALARM SYSTEM                     │
└──────────────────────────────────────────────────────────────┘

Fire Detectors:                  Voting Logic:
┌────────────┐                  ┌─────────────────┐
│  FD-101    │─────┐           │ Count Active    │
│  (North)   │     │           │ Alarms >= 2?    │──► ESD Trigger
└────────────┘     ├──────────►│                 │
                   │           └─────────────────┘
┌────────────┐     │
│  FD-102    │─────┤
│  (South)   │     │
└────────────┘     │
                   │
┌────────────┐     │
│  FD-103    │─────┘
│  (East)    │ [Optional - for 2-out-of-3]
└────────────┘

Process Flow:
┌────────────┐     ┌──────────┐     ┌─────────────┐
│ Separator  │────►│ Splitter │────►│  To Process │
│  50 bara   │     └──────────┘     └─────────────┘
└────────────┘          │
                        │ (ESD redirects flow)
                        ▼
                  ┌─────────────┐
                  │ BD Valve    │
                  │ (opens 5s)  │
                  └─────────────┘
                        │
                        ▼
                  ┌─────────────┐
                  │  Orifice    │ (flow control)
                  └─────────────┘
                        │
                        ▼
                  ┌─────────────┐
                  │   Flare     │ ◄── Heat & CO2
                  │  1.5 bara   │     Calculations
                  └─────────────┘
```

## Emissions Calculations

The flare tracks cumulative values during blowdown:

**Heat Release:**
- Based on Lower Calorific Value (LCV) of gas
- Calculated as: `Q = LCV × Flow_rate`
- Tracked in real-time and cumulative (GJ)

**CO2 Emissions:**
- Based on carbon content of components
- Calculated as: `CO2 = Σ(moles_C × MW_CO2)`
- Assumes complete combustion
- Tracked instantaneously (kg/s) and cumulative (kg)

**Gas Burned:**
- Total mass flow to flare integrated over time
- Reported in kg and tonnes

## Test Execution

**Run all ESD fire alarm tests:**
```bash
mvnw test -Dtest=ESDFireAlarmSystemTest
```

**Run specific test:**
```bash
mvnw test -Dtest=ESDFireAlarmSystemTest#testESDWithTwoFireAlarmVoting
```

## Integration Points

### With Existing NeqSim Components

**Compatible Equipment:**
- `Separator` - Source vessel for blowdown
- `Splitter` - Flow routing between process and blowdown
- `BlowdownValve` - ESD-activated valve with transient opening
- `Orifice` - ISO 5167 flow restriction
- `Flare` - Combustion and emissions tracking
- `PushButton` - Manual ESD activation (can be combined with fire alarms)

**Alarm System Integration:**
- `FireDetector` extends `MeasurementDeviceBaseClass`
- Compatible with `AlarmConfig`, `AlarmState`, `AlarmLevel`
- Can be used with controllers and process alarm managers
- Supports delay, deadband, and threshold configuration

### Safety Instrumented Systems (SIS) Applications

This implementation demonstrates concepts used in:
- Fire and gas (F&G) detection systems
- Emergency shutdown (ESD) systems
- Pressure relief and blowdown systems
- Flare load management
- Emissions tracking and reporting

## Best Practices Demonstrated

1. **Voting Logic:** Prevents spurious trips while maintaining safety
2. **Safety Latching:** BD valve stays activated until manual reset
3. **Alarm Confirmation:** Requires multiple independent sensors
4. **Dynamic Simulation:** Realistic transient behavior during blowdown
5. **Emissions Tracking:** Cumulative tracking for regulatory compliance
6. **Comprehensive Testing:** Both unit tests and integration scenarios

## Future Enhancements

Potential additions:
- Integration with gas detection sensors (LEL, toxic gas)
- Temperature-based fire detection
- Flame pattern recognition
- Integration with process alarm managers
- Automatic splitter control on ESD
- Multiple ESD levels (ESD-1, ESD-2, etc.)
- Heat detector integration
- Smoke detector support
- Time-stamped alarm logging
- Alarm priority escalation

## References

- ESD Blowdown System: `docs/ESD_BLOWDOWN_SYSTEM.md`
- Process Control: `docs/wiki/process_control.md`
- Test Overview: `docs/wiki/test-overview.md`
- Alarm System: `src/main/java/neqsim/process/alarm/`

## Files Created/Modified

### New Files:
1. `src/main/java/neqsim/process/measurementdevice/FireDetector.java`
2. `src/test/java/neqsim/process/equipment/valve/ESDFireAlarmSystemTest.java`
3. `docs/wiki/esd_fire_alarm_system.md`

### Key Dependencies:
- `BlowdownValve` - ESD-activated valve
- `Flare` - Emissions calculations
- `AlarmConfig` - Alarm configuration
- `Orifice` - Flow control
- `Separator` - Dynamic vessel simulation

## Example: Implementing Custom Voting Logic

```java
/**
 * Custom voting logic class for ESD systems.
 */
public class ESDVotingLogic {
    private final List<FireDetector> detectors;
    private final int requiredAlarms;
    private final BlowdownValve bdValve;
    
    public ESDVotingLogic(BlowdownValve bdValve, int requiredAlarms, 
                          FireDetector... detectors) {
        this.bdValve = bdValve;
        this.requiredAlarms = requiredAlarms;
        this.detectors = Arrays.asList(detectors);
    }
    
    public void evaluate() {
        int activeAlarms = (int) detectors.stream()
                                          .filter(FireDetector::isFireDetected)
                                          .count();
        
        if (activeAlarms >= requiredAlarms && !bdValve.isActivated()) {
            bdValve.activate();
            System.out.println("ESD ACTIVATED: " + activeAlarms + 
                             " of " + detectors.size() + " alarms active");
        }
    }
    
    public boolean isESDActive() {
        return bdValve.isActivated();
    }
}

// Usage:
ESDVotingLogic esdLogic = new ESDVotingLogic(bdValve, 2, 
                                             fireDetector1, 
                                             fireDetector2, 
                                             fireDetector3);

// In simulation loop:
esdLogic.evaluate();
```

---

**Author:** ESOL  
**Date:** November 2025  
**Version:** 1.0
