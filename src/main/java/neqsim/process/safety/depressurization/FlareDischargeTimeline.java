package neqsim.process.safety.depressurization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput.DischargeWindow;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput.ReliefStudyEntry;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.overpressure.ReliefPhase;
import neqsim.process.safety.overpressure.ReliefScenario;
import neqsim.thermo.system.SystemInterface;

/** Combines accepted discharge intervals from one initiating event at their actual boundaries. */
final class FlareDischargeTimeline {
  private final TreeMap<Double, Map<String, Double>> changes = new TreeMap<Double, Map<String, Double>>();
  private final Map<String, SourceBasis> bases = new LinkedHashMap<String, SourceBasis>();

  /** Ranges per kg of released inventory. Unknown nonnegative loads have no finite upper bound. */
  private static final class SourceBasis {
    private final String pureComponent;
    private final double criticalTemperature;
    private final double heatHigh;
    private final double molesHigh;

    private SourceBasis(double heatHigh, double molesHigh, String pureComponent, double criticalTemperature) {
      this.pureComponent = pureComponent;
      this.criticalTemperature = criticalTemperature;
      this.heatHigh = heatHigh;
      this.molesHigh = molesHigh;
    }

    private static SourceBasis fixed(double heat, double mw, String pureComponent, Double criticalTemperature) {
      double moles = Double.isFinite(mw) && mw > 0.0 ? 1.0 / mw : Double.NaN;
      return new SourceBasis(heat, moles, pureComponent,
          criticalTemperature == null ? Double.NaN : criticalTemperature);
    }

    /** Each nonreacting vapour composition is a mass-weighted mixture of the source constituents. */
    private static SourceBasis fractionating(SystemInterface fluid) {
      double heatHigh = 0.0;
      double molesHigh = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        if (fluid.getComponent(i).getz() <= 0.0) {
          continue;
        }
        SystemInterface component = fluid.clone();
        double[] composition = new double[fluid.getNumberOfComponents()];
        composition[i] = 1.0;
        component.setMolarComposition(composition);
        double heat = CoupledReliefBlowdownFlareInput.gasHeatingValue(component);
        double moles = 1.0 / fluid.getComponent(i).getMolarMass();
        heatHigh = Math.max(heatHigh, heat);
        molesHigh = Math.max(molesHigh, moles);
      }
      return new SourceBasis(heatHigh, molesHigh, null, Double.NaN);
    }
  }

  void addRelief(ReliefStudyEntry entry, ReliefScenario scenario) {
    String source = "PSV:" + entry.getStudy().getItem().getName();
    boolean gas = scenario.getPhase() == ReliefPhase.VAPOUR;
    Double heat = entry.getLowerHeatingValueJPerKg();
    register(source,
        SourceBasis.fixed(gas && heat != null ? heat : Double.NaN, gas ? scenario.getMolarMassKgPerMol() : Double.NaN,
            gas ? entry.getPureComponent() : null, entry.getCriticalTemperatureK()));
    DischargeWindow window = entry.getWindow();
    setRate(window == null ? 0.0 : window.getStartSeconds(), source, scenario.getReliefRateKgPerS());
    if (window != null) {
      setRate(window.getEndSeconds(), source, 0.0);
    }
  }

  void addBlowdown(BlowdownSource blowdown, DischargeWindow window, Map<?, ?> result) {
    String source = "BDV:" + blowdown.getSourceId();
    SystemInterface initialFluid = blowdown.getFluid().clone();
    initialFluid.init(0);
    boolean pure = initialFluid.getNumberOfComponents() == 1;
    boolean fixedComposition = pure || Boolean.FALSE.equals(result.get("vapourWithdrawalUsed"));
    register(source,
        fixedComposition ? SourceBasis.fixed(CoupledReliefBlowdownFlareInput.gasHeatingValue(initialFluid),
            initialFluid.getMolarMass(), pure ? initialFluid.getComponent(0).getComponentName() : null,
            pure ? initialFluid.getComponent(0).getTC() : null) : SourceBasis.fractionating(initialFluid));
    Map<?, ?> profile = (Map<?, ?>) result.get("profile");
    List<?> times = (List<?>) profile.get("timeS");
    List<?> masses = (List<?>) profile.get("massKg");
    double start = window == null ? 0.0 : window.getStartSeconds();
    double stop = window != null && window.getEndSeconds() != null ? window.getEndSeconds() : Double.POSITIVE_INFINITY;
    if (times.size() != masses.size() || times.isEmpty()) {
      throw new IllegalStateException("Incomplete accepted inventory history for " + source);
    }
    setRate(start, source, 0.0);
    for (int i = 1; i < times.size(); i++) {
      double t0 = number(times, i - 1);
      double t1 = number(times, i);
      double rate = (number(masses, i - 1) - number(masses, i)) / (t1 - t0);
      if (start + t0 >= stop) {
        break;
      }
      if (!(t1 > t0)) {
        throw new IllegalStateException("Non-increasing time for " + source);
      }
      setRate(start + t0, source, rate);
    }
    setRate(Math.min(stop, start + number(times, times.size() - 1)), source, 0.0);
  }

  private static double number(List<?> values, int index) {
    return ((Number) values.get(index)).doubleValue();
  }

  private void register(String source, SourceBasis basis) {
    if (bases.put(source, basis) != null) {
      throw new IllegalArgumentException("Duplicate source for one initiating event: " + source);
    }
  }

  private void setRate(double time, String source, double massRate) {
    if (!Double.isFinite(time) || !Double.isFinite(massRate) || massRate < 0.0) {
      throw new IllegalStateException("Invalid discharge time or mass rate for " + source);
    }
    changes.computeIfAbsent(time, ignored -> new LinkedHashMap<String, Double>()).put(source, massRate);
  }

  FlareDisposalCaseResult evaluate(String event, DynamicBlowdownFlareStudyDataSource config) {
    Map<String, Double> current = new LinkedHashMap<String, Double>();
    Map<String, Double> governing = Collections.emptyMap();
    double peak = -1.0;
    double peakTime = 0.0;
    double peakSteady = 0.0;
    double tipMassLow = 0.0;
    double heatLow = 0.0;
    double heatHigh = 0.0;
    double molarLow = 0.0;
    double molarHigh = 0.0;
    for (Map.Entry<Double, Map<String, Double>> step : changes.entrySet()) {
      current.putAll(step.getValue());
      double mass = 0.0;
      double steady = 0.0;
      String soleComponent = null;
      boolean dry = true;
      double maxHeat = 0.0;
      double maxMoles = 0.0;
      for (Map.Entry<String, Double> load : current.entrySet()) {
        double rate = load.getValue();
        if (rate > 0.0) {
          String id = load.getKey();
          mass += rate;
          SourceBasis basis = bases.get(id);
          // A mixed or condensable load can lose mass, moles and heat duty in a knockout drum.
          dry &= basis.pureComponent != null && config.getHeaderTemperatureK() > basis.criticalTemperature
              && (soleComponent == null || soleComponent.equals(basis.pureComponent));
          soleComponent = basis.pureComponent;
          maxHeat += rate * basis.heatHigh;
          maxMoles += rate * basis.molesHigh;
          if (id.startsWith("PSV:")) {
            steady += rate;
          }
        }
      }
      if (mass > peak) {
        peak = mass;
        peakTime = step.getKey();
        governing = new LinkedHashMap<String, Double>(current);
        governing.values().removeIf(value -> value == 0.0);
      }
      peakSteady = Math.max(peakSteady, steady);
      if (dry) {
        tipMassLow = Math.max(tipMassLow, mass);
        heatLow = Math.max(heatLow, maxHeat);
        molarLow = Math.max(molarLow, maxMoles);
      }
      heatHigh = Math.max(heatHigh, maxHeat);
      molarHigh = Math.max(molarHigh, maxMoles);
    }
    return new FlareDisposalCaseResult(event, peak, peakTime, peakSteady, governing, tipMassLow, heatLow, heatHigh,
        molarLow, molarHigh, config);
  }
}
