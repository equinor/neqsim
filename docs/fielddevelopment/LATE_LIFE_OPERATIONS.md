---
title: Late-Life Field Screening
description: Executable production-decline and economics screening with explicit units, assumptions, and engineering limits.
---

Late-life decisions combine reservoir decline, well deliverability, facility limits, operating
cost, integrity, emissions, and abandonment obligations. NeqSim can support parts of that
workflow, but a decline curve and cash-flow calculation are only a screening layer. They do
not determine a safe operating limit or an abandonment date.

## Screening basis

The executable example below uses current
`ProductionProfileGenerator` and `CashFlowEngine` APIs. Its synthetic gas case assumes:

| Input | Value and unit |
|---|---:|
| Initial plateau rate | 8.0 million Sm³/d |
| Ramp / plateau | 1 / 4 years |
| Exponential decline | 12% per year |
| Forecast | 2028–2047 |
| Total CAPEX | 1,384.5 MUSD |
| Fixed OPEX proxy | 4% of CAPEX per year |
| Gas price / tariff | 0.30 / 0.02 USD/Sm³ |
| Discount rate | 8% |
| Fiscal selector | `NO` |

`generateFullProfile(...)` receives a daily rate in this example but returns annual
production volumes. Pass each returned value once to `addAnnualProduction(...)`; multiplying
by days per year again would overstate revenue. NeqSim treats Sm³ numerically here and does
not convert or verify the chosen standard condition. Record the project standard condition
with the input data.

## Executable late-life screen

Compile and run this complete Java 8 program with assertions enabled, for example
`java -ea LateLifeScreeningExample`.

```java
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.CashFlowEngine.CashFlowResult;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator;
import neqsim.process.fielddevelopment.economics.ProductionProfileGenerator.DeclineType;

public final class LateLifeScreeningExample {
  private static final Logger logger =
      LogManager.getLogger(LateLifeScreeningExample.class);
  private static final double DAYS_PER_YEAR = 365.25;

  private LateLifeScreeningExample() {}

  private static Map<Integer, Double> createProfile(double plateauRateSm3PerDay) {
    return new ProductionProfileGenerator()
        .generateFullProfile(
            plateauRateSm3PerDay,
            1,
            4,
            0.12,
            DeclineType.EXPONENTIAL,
            2028,
            20);
  }

  private static CashFlowEngine createCashFlow(
      Map<Integer, Double> annualProfile,
      double totalCapexMusd,
      double gasPriceUsdPerSm3) {
    CashFlowEngine engine = new CashFlowEngine("NO");
    engine.setCapex(0.77 * totalCapexMusd, 2026);
    engine.addCapex(0.23 * totalCapexMusd, 2027);
    engine.setOpexPercentOfCapex(0.04);
    engine.setGasPrice(gasPriceUsdPerSm3);
    engine.setGasTariff(0.02);
    for (Map.Entry<Integer, Double> entry : annualProfile.entrySet()) {
      engine.addAnnualProduction(entry.getKey(), 0.0, entry.getValue(), 0.0);
    }
    return engine;
  }

  public static void main(String[] args) {
    Map<Integer, Double> baseProfile = createProfile(8.0e6);
    double firstRateSm3PerDay = baseProfile.get(2028) / DAYS_PER_YEAR;
    double finalRateSm3PerDay = baseProfile.get(2047) / DAYS_PER_YEAR;

    CashFlowEngine baseEngine = createCashFlow(baseProfile, 1384.5, 0.30);
    CashFlowResult base = baseEngine.calculate(0.08);
    CashFlowResult lowerRate =
        createCashFlow(createProfile(6.0e6), 1384.5, 0.30).calculate(0.08);
    CashFlowResult higherCapex =
        createCashFlow(baseProfile, 1700.0, 0.30).calculate(0.08);
    double breakEvenGasPriceUsdPerSm3 =
        baseEngine.calculateBreakevenGasPrice(0.08);

    assert baseProfile.size() == 20;
    assert Math.abs(firstRateSm3PerDay - 8.0e6) < 1.0e-6;
    assert finalRateSm3PerDay > 0.0;
    assert finalRateSm3PerDay < firstRateSm3PerDay;
    assert Double.isFinite(base.getNpv());
    assert Double.isFinite(base.getIrr());
    assert base.getTotalCapex() > 0.0;
    assert breakEvenGasPriceUsdPerSm3 > 0.0;
    assert breakEvenGasPriceUsdPerSm3 < 2.0;
    assert lowerRate.getNpv() < base.getNpv();
    assert higherCapex.getNpv() < base.getNpv();

    logger.info(
        "First/final rate={}/{} Sm3/d, NPV={} MUSD, break-even={} USD/Sm3",
        firstRateSm3PerDay,
        finalRateSm3PerDay,
        base.getNpv(),
        breakEvenGasPriceUsdPerSm3);
  }
}
```

The assertions protect unit conversion and expected trends; they do not validate the
synthetic commercial assumptions.

## Interpreting a late-life study

Use the calculation as one transparent layer in a larger annual or monthly workflow:

1. Replace the synthetic decline with a qualified reservoir, well, and network forecast.
2. Apply well, separator, compression, water-treatment, export, power, emissions, and
   integrity limits before booking saleable production.
3. Model downtime, maintenance, tariffs, variable and fixed OPEX, taxes, abandonment
   security, and decommissioning timing with traceable project inputs.
4. Test nearby rates, prices, costs, uptime, and abandonment dates. Add probability labels
   only when distributions, correlations, sampling, and percentile conventions are documented.
5. Require reservoir, production-technology, process, integrity, environmental, fiscal,
   commercial, and decommissioning review before a decision.

The example does not simulate water cut, GOR evolution, artificial lift, equipment turndown,
host capacity, process convergence, material degradation, emissions compliance, or
decommissioning execution. A positive NPV is not permission to operate, and a negative NPV
is not by itself an abandonment recommendation.

## Related documentation

- [Field-development documentation](README.md)
- [Integrated field lifecycle simulation](FIELD_LIFECYCLE_SIMULATION.md)
- [Transparent field-development screening notebook](../examples/FieldDevelopmentWorkflow.md)
