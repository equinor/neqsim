---
title: "Field development planning"
description: "Current NeqSim decline-curve and well-intervention planning APIs, units, limits, and an executable Java example."
---

# Field development planning

NeqSim's `neqsim.process.util.fielddevelopment` package provides utilities for
production-profile calculations, intervention scheduling, process-aware sensitivity
studies, and host tie-in screening. These utilities support engineering studies; they
do not replace reservoir history matching, detailed well modelling, facility design,
operations planning, cost estimation, or an independent safety review.

## Choose the current API

| Planning task | Current API | Engineering boundary |
| --- | --- | --- |
| Arps decline and plateau forecast | `ProductionProfile` | A static decline calculation is unconstrained. A process-constrained forecast requires a `ProcessSystem` and a feed stream. |
| Well potential and intervention schedule | `WellScheduler` | The optimizer is a deterministic screening scheduler. It is not a rig, vessel, weather, logistics, or HSE simulator. |
| Process uncertainty | `SensitivityAnalysis` | The constructor takes a `ProcessSystem`; this is process-aware uncertainty analysis, not a standalone financial Monte Carlo model. |
| Host capacity and bottlenecks | `TieInCapacityPlanner` and `ProductionOptimizer` | There is no `FacilityCapacity` class. Capacity cases must use explicit process models, equipment limits, and engineering units. |

For the broader workflow, see the
[field-development overview](../fielddevelopment/README), the
[field-development API guide](../fielddevelopment/API_GUIDE), and the
[host tie-in capacity guide](../fielddevelopment/HOST_TIE_IN_CAPACITY).

## Units and interpretation

- `DeclineParameters(10000.0, 0.15, EXPONENTIAL, "Sm3/day")` means an
  initial rate of 10,000 Sm3/day and a nominal decline rate of 0.15 per year.
  The default decline time unit is `year`.
- `calculateRate(parameters, time)` expects `time` in the same unit as the
  decline rate.
- `calculateCumulativeProduction(parameters, time)` analytically integrates the
  numerical rate against that time coordinate. It does not convert a daily rate
  and a yearly time coordinate into volume. For `Sm3/day` with time in years,
  multiply the returned rate-years value by 365.25 day/year to obtain Sm3.
- `Intervention.Builder.expectedGain(0.12)` means a fractional 12% potential
  increase, not an absolute rate increase.
- `cost(250000.0, "USD")` records a screening cost and currency label. The
  scheduler does not calculate NPV or certify an economic decision.
- Schedule availability is a fraction from 0 to 1. A concurrency limit constrains
  simultaneous interventions only; real campaigns need resource, weather,
  integrity, barrier, and safety reviews.

## Executable field-development planning example

The following complete Java 8 program calculates an exponential decline case,
converts cumulative production to Sm3, schedules one synthetic intervention, and
checks the result. Run it with assertions enabled (`java -ea`).

```java
import java.time.LocalDate;
import neqsim.process.util.fielddevelopment.ProductionProfile;
import neqsim.process.util.fielddevelopment.ProductionProfile.DeclineParameters;
import neqsim.process.util.fielddevelopment.ProductionProfile.DeclineType;
import neqsim.process.util.fielddevelopment.WellScheduler;
import neqsim.process.util.fielddevelopment.WellScheduler.Intervention;
import neqsim.process.util.fielddevelopment.WellScheduler.InterventionType;
import neqsim.process.util.fielddevelopment.WellScheduler.ScheduleResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FieldDevelopmentPlanningExample {
  private static final Logger logger =
      LogManager.getLogger(FieldDevelopmentPlanningExample.class);

  private FieldDevelopmentPlanningExample() {}

  public static void main(String[] args) {
    double initialRateSm3PerDay = 10000.0;
    double nominalDeclinePerYear = 0.15;
    DeclineParameters decline =
        new DeclineParameters(
            initialRateSm3PerDay,
            nominalDeclinePerYear,
            DeclineType.EXPONENTIAL,
            "Sm3/day");

    double rateAfterTwoYearsSm3PerDay = ProductionProfile.calculateRate(decline, 2.0);
    double cumulativeRateYears =
        ProductionProfile.calculateCumulativeProduction(decline, 5.0);
    double cumulativeFiveYearsSm3 = cumulativeRateYears * 365.25;

    double expectedRateSm3PerDay =
        initialRateSm3PerDay * Math.exp(-nominalDeclinePerYear * 2.0);
    assert Math.abs(rateAfterTwoYearsSm3PerDay - expectedRateSm3PerDay) < 1.0e-8;
    assert cumulativeFiveYearsSm3 > rateAfterTwoYearsSm3PerDay * 365.25 * 5.0;
    assert cumulativeFiveYearsSm3 < initialRateSm3PerDay * 365.25 * 5.0;

    WellScheduler scheduler = new WellScheduler();
    scheduler.addWell("Well-A1", initialRateSm3PerDay, "Sm3/day");

    Intervention stimulation =
        Intervention.builder("Well-A1")
            .type(InterventionType.COILED_TUBING)
            .startDate(LocalDate.of(2027, 4, 1))
            .durationDays(5)
            .expectedGain(0.12)
            .cost(250000.0, "USD")
            .description("Synthetic stimulation screening case")
            .priority(1)
            .build();
    scheduler.scheduleIntervention(stimulation);

    ScheduleResult schedule =
        scheduler.optimizeSchedule(
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1);

    assert schedule.getOptimizedSchedule().size() == 1;
    assert schedule.getOverallAvailability() > 0.0;
    assert schedule.getOverallAvailability() < 1.0;
    assert schedule.getTotalDeferredProduction() > 0.0;
    assert schedule.getTotalProductionGain() > 0.0;

    logger.info(
        "Rate after two years={} Sm3/day, cumulative five-year production={} Sm3, "
            + "availability={}, net production impact={} Sm3",
        rateAfterTwoYearsSm3PerDay,
        cumulativeFiveYearsSm3,
        schedule.getOverallAvailability(),
        schedule.getNetProductionImpact());
  }
}
```

## Extending the screening case

For a facility-constrained production forecast, build and validate the process model
first, then construct `ProductionProfile(processSystem)` and call `forecast` with
the feed stream, explicit plateau rate, plateau duration in years, economic limit,
forecast horizon in years, and time step in days. The result reports the requested
and achieved plateau, cumulative production, economic life, and any process
bottleneck found by the optimizer.

For intervention planning, add each well once with an explicit potential and rate
unit. Build interventions with dates, durations, fractional gains, costs, and
priorities before calling `optimizeSchedule`. Treat the result as a screening case:
verify well deliverability, equipment capacities, shutdown interactions, resource
constraints, cost basis, and safety-critical activities independently.

## Validation checklist

1. Match the decline-rate time basis to the time passed to the calculator.
2. Convert cumulative values when rate and decline time bases differ.
3. Use one rate unit consistently across all wells in a schedule.
4. Confirm every intervention gain is a fraction and every cost has a currency.
5. Validate facility constraints with the actual `ProcessSystem`, not a nameplate
   capacity surrogate.
6. Reconcile the forecast and schedule against reservoir, operations, integrity,
   economics, and safety assumptions before decisions are made.
