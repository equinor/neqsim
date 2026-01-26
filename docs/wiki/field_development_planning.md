# Field Development Planning Module

The Field Development Planning module provides a comprehensive set of tools for modeling, scheduling, and optimizing oil and gas field development projects. This module integrates with NeqSim's existing process simulation capabilities to enable full-lifecycle field development planning.

## Overview

The module consists of four main classes:

| Class | Purpose |
|-------|---------|
| `ProductionProfile` | Decline curve modeling and production forecasting |
| `WellScheduler` | Well intervention and workover scheduling |
| `FacilityCapacity` | Facility bottleneck analysis and debottleneck planning |
| `SensitivityAnalysis` | Monte Carlo simulation for uncertainty quantification |

## Package Location

```
neqsim.process.util.fielddevelopment
```

## Quick Start

### Production Profile - Decline Curves

Model production decline using industry-standard decline curve analysis:

```java
import neqsim.process.util.fielddevelopment.ProductionProfile;
import neqsim.process.util.fielddevelopment.ProductionProfile.*;

// Create a production profile for a well
ProductionProfile profile = new ProductionProfile("Well-A1");

// Set exponential decline parameters
// Initial rate: 1000 bbl/day, Decline rate: 10%/year
DeclineParameters params = new DeclineParameters(
    DeclineType.EXPONENTIAL, 
    1000.0,  // Initial rate (bbl/day)
    0.10,    // Decline rate (per year)
    0.0,     // b-factor (not used for exponential)
    0.0      // Plateau rate
);
profile.setDeclineParameters(params);

// Calculate rate at 2 years
double rate = profile.calculateRate(2.0);
System.out.println("Rate at 2 years: " + rate + " bbl/day");

// Calculate cumulative production over 5 years
double cumulative = profile.calculateCumulativeProduction(5.0);
System.out.println("Cumulative: " + cumulative + " bbl");

// Set economic limit and find field life
profile.setEconomicLimit(50.0); // bbl/day
double fieldLife = profile.calculateTimeToEconomicLimit();
System.out.println("Field life: " + fieldLife + " years");

// Generate monthly forecast
LocalDate startDate = LocalDate.of(2024, 1, 1);
ProductionForecast forecast = profile.generateForecast(startDate, 10, 12);
for (ProductionPoint point : forecast.getProductionPoints()) {
    System.out.println(point.getDate() + ": " + point.getRate() + " bbl/day");
}
```

### Decline Curve Types

The module supports three industry-standard decline curve models:

#### Exponential Decline
```
q(t) = q₀ × e^(-Dt)
```
Best for: Wells with constant percentage decline, tight reservoirs

#### Hyperbolic Decline
```
q(t) = q₀ / (1 + bDt)^(1/b)
```
Where b is the hyperbolic exponent (0 < b < 1). Best for: Wells with declining percentage decline rate.

#### Harmonic Decline
```
q(t) = q₀ / (1 + Dt)
```
Special case of hyperbolic with b = 1. Best for: Wells with slowly declining rate.

### Plateau Production

Model plateau production before decline onset:

```java
// 2-year plateau at 800 bbl/day before decline begins
DeclineParameters params = new DeclineParameters(
    DeclineType.EXPONENTIAL, 
    1000.0,  // Initial rate
    0.10,    // Decline rate
    0.0,     // b-factor
    800.0    // Plateau rate
);
profile.setDeclineParameters(params);
profile.setPlateauDuration(2.0); // years

// During plateau (t < 2 years), rate = 800 bbl/day
// After plateau, exponential decline from 800 bbl/day
```

## Well Scheduler - Intervention Planning

Schedule and track well interventions, workovers, and availability:

```java
import neqsim.process.util.fielddevelopment.WellScheduler;
import neqsim.process.util.fielddevelopment.WellScheduler.*;

// Create scheduler
WellScheduler scheduler = new WellScheduler("Platform-A");

// Add wells to the schedule
scheduler.addWell("Well-A1", LocalDate.of(2024, 1, 15), 500.0);
scheduler.addWell("Well-A2", LocalDate.of(2024, 3, 1), 450.0);
scheduler.addWell("Well-A3", LocalDate.of(2024, 5, 15), 400.0);

// Update well status
scheduler.updateWellStatus("Well-A1", WellStatus.PRODUCING);

// Schedule interventions
Intervention workover = scheduler.scheduleIntervention(
    "Well-A1",
    InterventionType.WORKOVER_RIG,
    LocalDate.of(2024, 9, 1),
    21, // Duration in days
    "ESP replacement"
);
workover.setDailyCost(150_000.0);
workover.setEstimatedNpv(5_000_000.0);

Intervention stimulation = scheduler.scheduleIntervention(
    "Well-A2",
    InterventionType.COILED_TUBING,
    LocalDate.of(2024, 7, 15),
    5,
    "Acid stimulation"
);

// Check for scheduling conflicts
boolean hasConflict = scheduler.hasSchedulingConflict(
    "Well-A1", 
    LocalDate.of(2024, 9, 10), 
    7
);

// Calculate well availability
double availability = scheduler.calculateAvailability(
    "Well-A1",
    LocalDate.of(2024, 1, 1),
    LocalDate.of(2024, 12, 31)
);

// Get prioritized interventions (by NPV)
List<Intervention> prioritized = scheduler.getPrioritizedInterventions();

// Generate schedule and export Gantt chart
ScheduleResult result = scheduler.generateSchedule(
    LocalDate.of(2024, 1, 1),
    LocalDate.of(2025, 12, 31)
);
String ganttData = result.toGanttFormat();
```

### Intervention Types

| Type | Description | Typical Duration |
|------|-------------|------------------|
| `COILED_TUBING` | Stimulation, cleanout, scale removal | 3-7 days |
| `WIRELINE` | Logging, perforating, mechanical work | 1-5 days |
| `SLICKLINE` | Basic mechanical operations | 1-3 days |
| `WORKOVER_RIG` | Major repairs, ESP/pump replacement | 14-30 days |
| `DRILLING_RIG` | Sidetrack, deepening | 30-90 days |
| `SUBSEA_INTERVENTION` | ROV/vessel-based work | 7-21 days |

### Well Status Tracking

| Status | Description |
|--------|-------------|
| `PENDING` | Well added but not yet on production |
| `DRILLING` | Actively being drilled |
| `COMPLETING` | Completion operations in progress |
| `PRODUCING` | On production |
| `SHUT_IN` | Temporarily shut in |
| `WORKOVER` | Undergoing workover operations |
| `SUSPENDED` | Long-term suspension |
| `ABANDONED` | Permanently abandoned |

## Facility Capacity - Bottleneck Analysis

Analyze facility capacity constraints and evaluate debottleneck options:

```java
import neqsim.process.util.fielddevelopment.FacilityCapacity;
import neqsim.process.util.fielddevelopment.FacilityCapacity.*;

// Create process system (using existing NeqSim capabilities)
ProcessSystem process = new ProcessSystem();
// ... add equipment (separators, compressors, etc.)
process.run();

// Create facility capacity analyzer
FacilityCapacity capacity = new FacilityCapacity("Platform-A", process);

// Identify primary bottleneck
String bottleneck = capacity.identifyBottleneck();
System.out.println("Primary bottleneck: " + bottleneck);

// Set equipment capacities
capacity.setMaxCapacity("Export Compressor", 150000.0, "kg/hr");
capacity.setMaxCapacity("Inlet Separator", 180000.0, "kg/hr");
capacity.setCurrentThroughput("Export Compressor", 120000.0, "kg/hr");

// Get capacity headroom
double headroom = capacity.getCapacityHeadroom("Export Compressor");

// Find all equipment above 90% utilization
List<String> criticalEquipment = capacity.getCriticalEquipment(0.90);

// Define and evaluate debottleneck options
DebottleneckOption option1 = new DebottleneckOption("Add Parallel Compressor");
option1.setCapexCost(10_000_000.0);
option1.setAdditionalCapacity(50000.0);
option1.setProductPrice(0.30);
option1.setOperatingCostPerUnit(0.05);
option1.setDiscountRate(0.10);
option1.setProjectLifeYears(15);

DebottleneckOption option2 = new DebottleneckOption("Upgrade Separator Internals");
option2.setCapexCost(3_000_000.0);
option2.setAdditionalCapacity(20000.0);
// ... set other parameters

capacity.addDebottleneckOption(option1);
capacity.addDebottleneckOption(option2);

// Rank options by NPV
List<DebottleneckOption> rankedOptions = capacity.rankDebottleneckOptions();

// Generate capacity assessment report
CapacityAssessment assessment = capacity.assess();
String report = assessment.generateReport();

// Define capacity periods for planning
capacity.addCapacityPeriod(new CapacityPeriod("2024", 100000.0));
capacity.addCapacityPeriod(new CapacityPeriod("2025", 120000.0));
capacity.addCapacityPeriod(new CapacityPeriod("2026", 140000.0));

// Calculate growth rate
double growthRate = capacity.calculateCapacityGrowthRate();
```

### Integration with ProductionOptimizer

The `FacilityCapacity` class leverages the existing `ProductionOptimizer` infrastructure for bottleneck analysis:

```java
import neqsim.process.util.optimizer.ProductionOptimizer;

// FacilityCapacity wraps ProductionOptimizer
FacilityCapacity capacity = new FacilityCapacity("Platform", process);

// Access underlying optimizer for advanced scenarios
ProductionOptimizer optimizer = capacity.getOptimizer();

// Run scenario comparison
ScenarioRequest baseCase = new ScenarioRequest(process);
ScenarioRequest debottleneck = new ScenarioRequest(modifiedProcess);

ScenarioComparisonResult comparison = optimizer.compareScenarios(baseCase, debottleneck);
```

## Sensitivity Analysis - Monte Carlo Simulation

Perform uncertainty analysis using Monte Carlo simulation:

```java
import neqsim.process.util.fielddevelopment.SensitivityAnalysis;
import neqsim.process.util.fielddevelopment.SensitivityAnalysis.*;

// Create sensitivity analysis
SensitivityAnalysis sensitivity = new SensitivityAnalysis("Project Economics");

// Add uncertain parameters with probability distributions
sensitivity.addParameter(new UncertainParameter(
    "OilPrice",
    DistributionType.NORMAL,
    75.0,   // Mean
    15.0    // Standard deviation
));

sensitivity.addParameter(new UncertainParameter(
    "Reserves",
    DistributionType.TRIANGULAR,
    50_000_000.0,   // Minimum
    100_000_000.0,  // Most likely
    150_000_000.0   // Maximum
));

sensitivity.addParameter(new UncertainParameter(
    "Capex",
    DistributionType.UNIFORM,
    500_000_000.0,  // Minimum
    800_000_000.0   // Maximum
));

sensitivity.addParameter(new UncertainParameter(
    "RecoveryFactor",
    DistributionType.LOGNORMAL,
    Math.log(0.35), // Mu (log of mean)
    0.15            // Sigma
));

// Set correlated parameters
sensitivity.setCorrelation("Reserves", "RecoveryFactor", 0.5);

// Configure and run Monte Carlo
sensitivity.setNumberOfTrials(10000);
sensitivity.setSeed(42L); // For reproducibility
sensitivity.setConvergenceThreshold(0.01);

MonteCarloResult result = sensitivity.runMonteCarlo();

// Get probability statistics
double p10 = result.getP10();  // 10th percentile (pessimistic)
double p50 = result.getP50();  // 50th percentile (median)
double p90 = result.getP90();  // 90th percentile (optimistic)

double mean = result.getMean();
double stdDev = result.getStandardDeviation();

System.out.println("P10: " + p10 + " P50: " + p50 + " P90: " + p90);

// Check convergence
if (result.isConverged()) {
    System.out.println("Simulation converged after " + result.getTrialCount() + " trials");
}

// Generate tornado diagram (sensitivity ranking)
List<TornadoEntry> tornado = result.generateTornadoDiagram();
for (TornadoEntry entry : tornado) {
    System.out.println(entry.getParameterName() + ": impact = " + entry.getImpact());
}

// Get histogram data
int[] histogram = result.generateHistogram(20);

// Calculate sensitivity indices
double oilPriceSensitivity = result.getSensitivityIndex("OilPrice");

// Export results
String csvData = result.exportToCsv();
```

### Distribution Types

| Type | Parameters | Use Case |
|------|------------|----------|
| `NORMAL` | mean, std | Symmetric uncertainty around expected value |
| `LOGNORMAL` | mu, sigma | Positive-only values with right skew |
| `TRIANGULAR` | min, mode, max | Expert judgment with defined range |
| `UNIFORM` | min, max | Equal probability across range |

### Sampling Formulas

**Normal Distribution:**
```
X = μ + σ × Z
where Z ~ N(0,1)
```

**Lognormal Distribution:**
```
X = e^(μ + σZ)
where Z ~ N(0,1)
```

**Triangular Distribution:**
```
if U < (mode-min)/(max-min):
    X = min + √(U(max-min)(mode-min))
else:
    X = max - √((1-U)(max-min)(max-mode))
where U ~ U(0,1)
```

**Uniform Distribution:**
```
X = min + U(max - min)
where U ~ U(0,1)
```

## Integration Example

Combine all modules for comprehensive field development planning:

```java
import neqsim.process.util.fielddevelopment.*;
import neqsim.process.processmodel.ProcessSystem;
import java.time.LocalDate;

public class FieldDevelopmentExample {
    
    public static void main(String[] args) {
        // 1. Create process system
        ProcessSystem process = createProcessSystem();
        process.run();
        
        // 2. Analyze facility capacity
        FacilityCapacity capacity = new FacilityCapacity("Production Platform", process);
        String bottleneck = capacity.identifyBottleneck();
        System.out.println("Current bottleneck: " + bottleneck);
        
        // 3. Create production profiles for wells
        ProductionProfile[] wellProfiles = new ProductionProfile[5];
        for (int i = 0; i < 5; i++) {
            wellProfiles[i] = new ProductionProfile("Well-" + (i + 1));
            wellProfiles[i].setDeclineParameters(new DeclineParameters(
                DeclineType.EXPONENTIAL, 
                500.0 - i * 50,  // Varying initial rates
                0.12,
                0.0,
                0.0
            ));
        }
        
        // 4. Schedule interventions
        WellScheduler scheduler = new WellScheduler("Platform Scheduler");
        LocalDate today = LocalDate.now();
        
        for (int i = 0; i < 5; i++) {
            scheduler.addWell("Well-" + (i + 1), today.minusYears(2 - i), 
                wellProfiles[i].calculateRate(0));
            scheduler.updateWellStatus("Well-" + (i + 1), WellStatus.PRODUCING);
        }
        
        // Schedule workovers based on decline rate
        scheduler.scheduleIntervention("Well-1", InterventionType.WORKOVER_RIG,
            today.plusMonths(6), 21, "ESP replacement");
        scheduler.scheduleIntervention("Well-2", InterventionType.COILED_TUBING,
            today.plusMonths(9), 5, "Acid stimulation");
        
        // 5. Run sensitivity analysis on key parameters
        SensitivityAnalysis sensitivity = new SensitivityAnalysis("Field Economics");
        
        sensitivity.addParameter(new UncertainParameter("OilPrice", 
            DistributionType.NORMAL, 75.0, 15.0));
        sensitivity.addParameter(new UncertainParameter("TotalReserves", 
            DistributionType.TRIANGULAR, 80e6, 100e6, 130e6));
        sensitivity.addParameter(new UncertainParameter("Opex", 
            DistributionType.UNIFORM, 15.0, 25.0));
        
        sensitivity.setNumberOfTrials(5000);
        MonteCarloResult mcResult = sensitivity.runMonteCarlo();
        
        // 6. Generate reports
        System.out.println("\n=== Field Development Summary ===\n");
        
        // Production forecast
        ProductionForecast totalForecast = combinedForecast(wellProfiles, today, 10);
        System.out.println("10-Year Production Forecast:");
        System.out.println("  Year 1: " + totalForecast.getCumulativeProduction(1) + " bbl");
        System.out.println("  Year 5: " + totalForecast.getCumulativeProduction(5) + " bbl");
        System.out.println("  Year 10: " + totalForecast.getCumulativeProduction(10) + " bbl");
        
        // Well schedule
        ScheduleResult schedule = scheduler.generateSchedule(today, today.plusYears(5));
        System.out.println("\nWell Schedule:");
        System.out.println("  Active wells: " + schedule.getWellCount());
        System.out.println("  Planned interventions: " + schedule.getTotalInterventions());
        
        // Facility capacity
        System.out.println("\nFacility Capacity:");
        System.out.println("  Current utilization: " + 
            (capacity.getOverallUtilization() * 100) + "%");
        System.out.println("  Bottleneck equipment: " + bottleneck);
        
        // Uncertainty analysis
        System.out.println("\nEconomic Uncertainty (NPV):");
        System.out.println("  P10: $" + String.format("%.1f", mcResult.getP10() / 1e6) + "M");
        System.out.println("  P50: $" + String.format("%.1f", mcResult.getP50() / 1e6) + "M");
        System.out.println("  P90: $" + String.format("%.1f", mcResult.getP90() / 1e6) + "M");
        
        // Tornado diagram
        System.out.println("\nKey Sensitivities:");
        for (TornadoEntry entry : mcResult.generateTornadoDiagram()) {
            System.out.println("  " + entry.getParameterName() + 
                ": " + String.format("%.1f", entry.getImpact() * 100) + "% impact");
        }
    }
}
```

## Best Practices

### Production Profiles

1. **Validate decline parameters** against historical production data
2. **Use plateau periods** for new wells with initial flush production
3. **Set realistic economic limits** based on operating costs
4. **Consider type curves** for analogous field comparison

### Well Scheduling

1. **Prioritize interventions by NPV** to maximize value
2. **Check for resource conflicts** (rigs, vessels, crews)
3. **Build in contingency time** for complex interventions
4. **Track well status** to maintain production accounting

### Facility Capacity

1. **Start with current bottleneck identification**
2. **Evaluate multiple debottleneck options** before deciding
3. **Consider staging** of capacity expansion
4. **Update capacity data** as field matures

### Sensitivity Analysis

1. **Use appropriate distributions** for each uncertainty type
2. **Run enough trials** for convergence (typically 5,000-10,000)
3. **Set correlations** between dependent parameters
4. **Focus on tornado top items** for risk mitigation

## Related Documentation

- [Bottleneck Analysis Guide](bottleneck_analysis.md)
- [ProductionOptimizer Reference](../javadoc/ProductionOptimizer.html)
- [Monte Carlo Statistics](../thermo/statistics/MonteCarloSimulation.html)

## API Reference

See the Javadoc documentation for complete API details:

- [`ProductionProfile`](../javadoc/neqsim/process/util/fielddevelopment/ProductionProfile.html)
- [`WellScheduler`](../javadoc/neqsim/process/util/fielddevelopment/WellScheduler.html)
- [`FacilityCapacity`](../javadoc/neqsim/process/util/fielddevelopment/FacilityCapacity.html)
- [`SensitivityAnalysis`](../javadoc/neqsim/process/util/fielddevelopment/SensitivityAnalysis.html)

