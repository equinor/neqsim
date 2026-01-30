# Monte Carlo Risk Simulation

Monte Carlo simulation provides probabilistic production forecasts by randomly sampling equipment failure events over a time horizon.

---

## Overview

Traditional deterministic analysis uses single-point estimates:
- "Production will be 100,000 tonnes"

Monte Carlo provides probability distributions:
- "P50 production is 95,000 tonnes"
- "P90 production (90% confidence) is 88,000 tonnes"
- "Expected availability is 96.5%"

---

## Mathematical Foundation

### Stochastic Failure Model

Equipment failures are modeled as a Poisson process with exponential inter-arrival times.

**Time to next failure:**

$$T_f \sim \text{Exponential}(\lambda)$$

$$f(t) = \lambda e^{-\lambda t}, \quad t \geq 0$$

Where $\lambda$ is the failure rate (failures per hour).

**Mean time to failure:**

$$E[T_f] = \frac{1}{\lambda} = \text{MTTF}$$

**Sampling algorithm:**

$$T_f = -\frac{1}{\lambda} \ln(U), \quad U \sim \text{Uniform}(0,1)$$

### Repair Time Model

Repair times are modeled as exponential (or can be extended to log-normal):

$$T_r \sim \text{Exponential}(\mu)$$

Where $\mu = 1/\text{MTTR}$.

### Production Calculation

For each time step $t$:

$$P(t) = P_{\text{design}} \times \prod_{i \in \text{operating}} C_{f,i}(t)$$

Where:
- $P_{\text{design}}$ = design production rate
- $C_{f,i}(t)$ = capacity factor of equipment $i$ at time $t$

### Cumulative Production

$$P_{\text{total}} = \int_0^T P(t) \, dt \approx \sum_{t=0}^{T} P(t) \Delta t$$

---

## Simulation Algorithm

```
Algorithm: Monte Carlo Production Simulation
─────────────────────────────────────────────
Input: Equipment list with (λ, MTTR, capacity_factor)
       Simulation horizon T (days)
       Number of iterations N

For each iteration i = 1 to N:
    Initialize: all equipment OPERATING
    cumulative_production = 0
    
    For each hour t = 0 to T × 24:
        For each equipment e:
            If e is OPERATING:
                Sample U ~ Uniform(0,1)
                If U < λ_e × Δt:
                    e.state = FAILED
                    e.repair_remaining = sample_repair_time(MTTR_e)
            
            If e is FAILED:
                e.repair_remaining -= Δt
                If e.repair_remaining <= 0:
                    e.state = OPERATING
        
        # Calculate production this hour
        capacity = 1.0
        For each equipment e:
            If e is FAILED:
                capacity *= e.capacity_factor_when_failed
        
        production_this_hour = design_rate × capacity
        cumulative_production += production_this_hour
    
    Store result[i] = cumulative_production

Calculate statistics:
    P50 = median(results)
    P10 = percentile(results, 10)
    P90 = percentile(results, 90)
    Expected = mean(results)
    Availability = mean(uptimes) / total_time
```

---

## Using OperationalRiskSimulator

### Basic Usage

```java
// Create simulator
OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);

// Configure streams for production measurement
simulator.setFeedStreamName("Well Feed");
simulator.setProductStreamName("Export Gas");

// Add equipment reliability data
// Parameters: name, failure rate (per year), MTTR (hours)
simulator.addEquipmentReliability("HP Compressor", 0.5, 24);
simulator.addEquipmentReliability("LP Compressor", 0.5, 24);
simulator.addEquipmentReliability("Export Pump", 0.2, 8);
simulator.addEquipmentReliability("Separator", 0.1, 4);

// Set random seed for reproducibility
simulator.setRandomSeed(42);

// Run simulation: 10,000 iterations, 365 days
OperationalRiskResult result = simulator.runSimulation(10000, 365);
```

### Interpreting Results

```java
// Production statistics
System.out.println("Expected production: " + result.getExpectedProduction() + " kg");
System.out.println("P10 production: " + result.getP10Production() + " kg");
System.out.println("P50 production: " + result.getP50Production() + " kg");
System.out.println("P90 production: " + result.getP90Production() + " kg");

// Availability
System.out.println("Expected availability: " + result.getAvailability() + "%");

// Downtime events
System.out.println("Expected downtime events: " + result.getExpectedDowntimeEvents());
System.out.println("Expected total downtime: " + result.getExpectedDowntimeHours() + " hours");

// Confidence interval
System.out.println("95% CI: [" + result.getLowerConfidenceLimit() + 
                   ", " + result.getUpperConfidenceLimit() + "]");
```

---

## Percentile Interpretation

| Percentile | Meaning | Use Case |
|------------|---------|----------|
| **P10** | 10% chance of exceeding | Optimistic scenario |
| **P50** | 50% chance of exceeding | Most likely scenario |
| **P90** | 90% chance of exceeding | Conservative planning |
| **Mean** | Expected (average) value | Financial budgeting |

### Visual Example

```
Production Distribution (10,000 iterations)
───────────────────────────────────────────

        ▲ Frequency
        │
        │        ████
        │       ██████
        │      ████████
        │     ██████████
        │    ████████████
        │   ██████████████
        │  ████████████████
        │ ██████████████████
        ├──┬──┬──┬──┬──┬──┬──┬──► Production
           P10  P50 Mean P90

P10 = 88,000 tonnes (optimistic)
P50 = 95,000 tonnes (median)
Mean = 94,500 tonnes (expected)
P90 = 99,000 tonnes (conservative)
```

---

## Advanced Configuration

### Equipment-Specific Failure Modes

```java
// Create custom failure mode
EquipmentFailureMode degradedMode = EquipmentFailureMode.builder()
    .name("Partial fouling")
    .type(FailureType.DEGRADED)
    .capacityFactor(0.7)  // 70% capacity when degraded
    .build();

// Add with custom failure mode
simulator.addEquipmentReliability("Heat Exchanger", 1.0, 48, degradedMode);
```

### Correlated Failures

For common-cause failures (e.g., power outage affecting multiple equipment):

```java
// Define correlation group
simulator.addCorrelatedFailureGroup(
    "Power System",
    Arrays.asList("Compressor A", "Compressor B", "Pump A"),
    0.05,  // 5% of failures are correlated
    4.0    // 4 hour common repair time
);
```

### Seasonal Variation

```java
// Vary production rate by season (e.g., gas demand)
Map<Integer, Double> seasonalFactors = new HashMap<>();
seasonalFactors.put(1, 1.2);   // January: 120%
seasonalFactors.put(7, 0.8);   // July: 80%
// ... other months

simulator.setSeasonalProductionFactors(seasonalFactors);
```

---

## Convergence Analysis

### Determining Iteration Count

The number of iterations $N$ affects result accuracy:

**Standard error of mean:**

$$SE = \frac{\sigma}{\sqrt{N}}$$

**Required iterations for precision $\epsilon$:**

$$N = \left(\frac{z \cdot \sigma}{\epsilon}\right)^2$$

Where $z = 1.96$ for 95% confidence.

### Convergence Check

```java
// Run with increasing iterations
int[] iterations = {100, 500, 1000, 5000, 10000};

for (int n : iterations) {
    OperationalRiskResult result = simulator.runSimulation(n, 365);
    System.out.printf("N=%d: P50=%.0f, StdErr=%.1f%n", 
        n, result.getP50Production(), result.getStandardError());
}
```

Typical output:
```
N=100: P50=94500, StdErr=2500
N=500: P50=94800, StdErr=1100
N=1000: P50=95100, StdErr=780
N=5000: P50=94950, StdErr=350
N=10000: P50=95000, StdErr=245
```

**Rule of thumb:** Use N ≥ 10,000 for financial decisions.

---

## System Availability Calculation

### Series System

For equipment in series (all must operate):

$$A_{\text{series}} = \prod_{i=1}^{n} A_i$$

### Parallel System (Redundancy)

For k-out-of-n redundancy:

$$A_{\text{parallel}} = \sum_{i=k}^{n} \binom{n}{i} A^i (1-A)^{n-i}$$

For 1-out-of-2 (simple redundancy):

$$A_{\text{1oo2}} = 1 - (1-A_1)(1-A_2)$$

### Example: Compressor System

```java
// Two parallel compressors, each 99% available
double A_single = 0.99;
double A_parallel = 1 - Math.pow(1 - A_single, 2);  // 99.99%

// Three compressors, need 2 operating
int n = 3, k = 2;
double A_2oo3 = 0;
for (int i = k; i <= n; i++) {
    A_2oo3 += binomial(n, i) * Math.pow(A_single, i) * Math.pow(1-A_single, n-i);
}
// A_2oo3 ≈ 99.97%
```

---

## Output Formats

### Summary Statistics

```java
String summary = result.getSummary();
```

Output:
```
═══════════════════════════════════════════════════════════
            MONTE CARLO SIMULATION RESULTS
═══════════════════════════════════════════════════════════
Iterations: 10,000
Horizon: 365 days
Random Seed: 42

PRODUCTION STATISTICS:
─────────────────────────────────────────────────────────
  Design Production:      100,000,000 kg
  Expected Production:     95,200,000 kg (95.2%)
  P10 Production:          98,500,000 kg
  P50 Production:          95,400,000 kg
  P90 Production:          91,200,000 kg
  Standard Deviation:       2,450,000 kg
  95% Confidence Interval: [94,900,000 - 95,500,000]

AVAILABILITY:
─────────────────────────────────────────────────────────
  Expected Availability:   96.2%
  Expected Downtime:       333 hours/year
  Expected Events:         3.2 failures/year

EQUIPMENT CONTRIBUTION TO DOWNTIME:
─────────────────────────────────────────────────────────
  HP Compressor:    145 hours (43.5%)
  LP Compressor:    142 hours (42.6%)
  Export Pump:       32 hours (9.6%)
  Separator:         14 hours (4.2%)
═══════════════════════════════════════════════════════════
```

### JSON Export

```java
String json = result.toJson();
```

---

## Integration with Risk Matrix

Monte Carlo results can populate risk matrix probability categories:

```java
// Get failure frequency from simulation
double compressorFailures = result.getEquipmentFailureCount("HP Compressor") / years;
ProbabilityCategory prob = ProbabilityCategory.fromFrequency(compressorFailures);

// Get consequence from production impact
double productionLoss = result.getProductionLossFromEquipment("HP Compressor");
ConsequenceCategory cons = ConsequenceCategory.fromProductionLoss(productionLoss);

// Add to risk matrix
riskMatrix.addRiskItem("HP Compressor", prob, cons, estimatedCost);
```

---

## Best Practices

1. **Use sufficient iterations** (≥10,000 for decisions)
2. **Set random seed** for reproducibility
3. **Validate with historical data** when available
4. **Include all significant failures** (not just trips)
5. **Consider correlations** between equipment
6. **Report confidence intervals**, not just point estimates
7. **Sensitivity analysis** on uncertain parameters

---

## See Also

- [Equipment Failure Modeling](equipment-failure.md)
- [Risk Matrix](risk-matrix.md)
- [Mathematical Reference](mathematical-reference.md)
- [API Reference](api-reference.md#operationalrisksimulator)
