package neqsim.process.safety.depressurization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.SerializationUtils;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Time-resolved, adiabatic mixing and equilibrium knockout of simultaneous blowdown intervals at the header pressure.
 *
 * @author NeqSim
 * @version 1.0
 */
final class BlowdownFlareLoad {
  /** Material and energy actually withdrawn in each accepted vessel interval. */
  static final class Discharge {
    private final SystemInterface fluid;
    private final List<double[]> componentRates = new ArrayList<>();
    private final List<Double> enthalpyRates = new ArrayList<>();

    /**
     * Retain the thermodynamic basis and original component order.
     *
     * @param fluid source fluid
     */
    Discharge(SystemInterface fluid) {
      this.fluid = fluid;
    }

    /**
     * Append the interval-average withdrawal.
     *
     * @param fractions mole fractions of the withdrawn material
     * @param molarFlow total withdrawn mol/s after inventory caps
     * @param enthalpyRate withdrawn enthalpy in W on the source EOS reference
     */
    void add(double[] fractions, double molarFlow, double enthalpyRate) {
      double[] rates = fractions.clone();
      for (int i = 0; i < rates.length; i++) {
        rates[i] *= molarFlow;
      }
      componentRates.add(rates);
      enthalpyRates.add(enthalpyRate);
    }
  }

  /** Interval series with an independently evaluated peak and time integral. */
  static final class Series {
    final List<Double> values = new ArrayList<>();
    double peak;
    double peakTime;
    double integral;

    /**
     * Record a piecewise-constant interval value.
     *
     * @param value interval average
     * @param time end of accepted interval in seconds
     * @param dt interval length in seconds
     */
    void add(double value, double time, double dt) {
      if (!Double.isFinite(value)) {
        throw new IllegalStateException("Non-finite flare load at " + time + " s");
      }
      values.add(value);
      integral += value * dt;
      if (value > peak) {
        peak = value;
        peakTime = time;
      }
    }
  }

  final Series gasMass = new Series();
  final Series gasMoles = new Series();
  final Series heat = new Series();
  private final Series co2 = new Series();
  private final Series liquidMass = new Series();
  private final Series headerMass = new Series();
  private final Series inletH = new Series();
  private final Series gasH = new Series();
  private final Series liquidH = new Series();
  private final List<Double> times = new ArrayList<>();
  private final List<double[]> feedRates = new ArrayList<>();
  private final List<double[]> vaporRates = new ArrayList<>();
  private final List<double[]> knockoutRates = new ArrayList<>();
  private final String[] componentNames;

  /**
   * Align source profiles produced on the runner's common fixed time step. A finished vessel contributes zero.
   *
   * @param sources source intervals indexed by unique source identifier
   * @param headerPressureBara local header pressure in bara
   * @param dt common accepted time step in seconds
   */
  BlowdownFlareLoad(Map<String, Discharge> sources, double headerPressureBara, double dt) {
    if (!Double.isFinite(headerPressureBara) || headerPressureBara <= 0.0) {
      throw new IllegalArgumentException("A finite positive flare header pressure in bara is required");
    }
    List<Discharge> ordered = new ArrayList<>(new TreeMap<>(sources).values());
    SystemInterface header = SerializationUtils.clone(ordered.get(0).fluid);
    int count = 0;
    for (Discharge source : ordered) {
      if (!header.getModelName().equals(source.fluid.getModelName())
          || header.getMixingRule() != source.fluid.getMixingRule()
          || header.isUsingIdealGasEnthalpyOfFormation() != source.fluid.isUsingIdealGasEnthalpyOfFormation()) {
        throw new IllegalArgumentException("Flare sources require a common EOS, mixing rule and enthalpy reference");
      }
      if (source != ordered.get(0)) {
        header.addFluid(source.fluid);
      }
      if (source.fluid.doMultiPhaseCheck() && !header.doMultiPhaseCheck()) {
        header.setMultiPhaseCheck(true);
      }
      count = Math.max(count, source.componentRates.size());
    }
    componentNames = header.getComponentNames();
    int[][] indexes = new int[ordered.size()][];
    for (int i = 0; i < ordered.size(); i++) {
      SystemInterface source = ordered.get(i).fluid;
      indexes[i] = new int[source.getNumberOfComponents()];
      for (int j = 0; j < indexes[i].length; j++) {
        int index = header.getComponent(source.getComponent(j).getName()).getComponentNumber();
        if (Math.abs(header.getComponent(index).getMolarMass() - source.getComponent(j).getMolarMass()) > 1e-10) {
          throw new IllegalArgumentException(
              "Conflicting flare component molar mass: " + source.getComponent(j).getName());
        }
        indexes[i][j] = index;
      }
    }
    for (int i = 0; i < ordered.size(); i++) {
      verifyInteractionParameters(header, ordered.get(i).fluid, indexes[i]);
    }
    for (int step = -1; step < count; step++) {
      double[] rates = new double[componentNames.length];
      double enthalpy = 0.0;
      if (step >= 0) {
        for (int i = 0; i < ordered.size(); i++) {
          Discharge source = ordered.get(i);
          if (step < source.componentRates.size()) {
            double[] incoming = source.componentRates.get(step);
            for (int j = 0; j < incoming.length; j++) {
              rates[indexes[i][j]] += incoming[j];
            }
            enthalpy += source.enthalpyRates.get(step);
          }
        }
      }
      record(header, headerPressureBara, rates, enthalpy, (step + 1) * dt, step < 0 ? 0.0 : dt);
    }
  }

  /**
   * Reject source binary fits that differ from those in the assembled header EOS.
   *
   * @param header assembled header fluid
   * @param source source fluid
   * @param indexes source-to-header component mapping
   */
  private static void verifyInteractionParameters(SystemInterface header, SystemInterface source, int[] indexes) {
    if (header.getPhase(0) instanceof PhaseEosInterface && source.getPhase(0) instanceof PhaseEosInterface) {
      EosMixingRulesInterface headerRule = ((PhaseEosInterface) header.getPhase(0)).getEosMixingRule();
      EosMixingRulesInterface sourceRule = ((PhaseEosInterface) source.getPhase(0)).getEosMixingRule();
      for (int i = 0; i < indexes.length; i++) {
        for (int j = 0; j < indexes.length; j++) {
          if (Math.abs(headerRule.getBinaryInteractionParameter(indexes[i], indexes[j])
              - sourceRule.getBinaryInteractionParameter(i, j)) > 1e-10) {
            throw new IllegalArgumentException("Flare header and source binary interaction parameters differ for "
                + source.getComponent(i).getName() + " and " + source.getComponent(j).getName());
          }
        }
      }
    }
  }

  /**
   * Flash combined material at its delivered enthalpy, route vapor to the tip and retain condensed liquid.
   *
   * @param header reusable thermodynamic basis
   * @param pressure header pressure in bara
   * @param rates component molar flows in mol/s
   * @param enthalpy summed inlet enthalpy rate in W
   * @param time interval end in s
   * @param dt interval duration in s
   */
  private void record(SystemInterface header, double pressure, double[] rates, double enthalpy, double time,
      double dt) {
    double mass = 0.0;
    for (int i = 0; i < rates.length; i++) {
      mass += rates[i] * header.getComponent(i).getMolarMass();
    }
    double[] vapor = new double[rates.length];
    double[] liquid = new double[rates.length];
    double liquidRate = 0.0;
    double gasRate = 0.0;
    double thermalDuty = 0.0;
    double carbonDioxide = 0.0;
    double vaporH = 0.0;
    double condensedH = 0.0;
    if (mass > 0.0) {
      header.setComponentFlowRates(rates, "mol/sec");
      header.setPressure(pressure);
      new ThermodynamicOperations(header).PHflash(enthalpy);
      header.init(2);
      int gasIndex = header.getNumberOfPhases() == 1 ? (header.getPhase(0).getType() == PhaseType.GAS ? 0 : -1)
          : DepressurizationSimulator.vapourPhaseIndex(header);
      for (int p = 0; p < header.getNumberOfPhases(); p++) {
        PhaseInterface phase = header.getPhase(p);
        PhaseType type = phase.getType();
        if (type != PhaseType.GAS && type != PhaseType.OIL && type != PhaseType.AQUEOUS && type != PhaseType.LIQUID
            && type != PhaseType.LIQUID_ASPHALTENE) {
          throw new IllegalArgumentException("Flare header requires fluid phases; found " + type);
        }
        if (p == gasIndex) {
          SystemInterface gas = header.phaseToSystem(p);
          Flare tip = new Flare("tip", new Stream("knockout vapor", gas));
          tip.run();
          thermalDuty += tip.getHeatDuty();
          carbonDioxide += tip.getCO2Emission();
          gasRate += phase.getNumberOfMolesInPhase() * phase.getMolarMass();
          vaporH += phase.getEnthalpy();
          for (int i = 0; i < rates.length; i++) {
            vapor[i] += phase.getNumberOfMolesInPhase() * phase.getComponent(i).getx();
          }
        } else {
          liquidRate += phase.getNumberOfMolesInPhase() * phase.getMolarMass();
          condensedH += phase.getEnthalpy();
          for (int i = 0; i < rates.length; i++) {
            liquid[i] += phase.getNumberOfMolesInPhase() * phase.getComponent(i).getx();
          }
        }
      }
      for (int i = 0; i < rates.length; i++) {
        if (Math.abs(rates[i] - vapor[i] - liquid[i]) > 1e-8 * Math.max(1.0, rates[i])) {
          throw new IllegalStateException(
              "Flare header component imbalance for " + componentNames[i] + " at " + time + " s");
        }
      }
      if (Math.abs(mass - liquidRate - gasRate) > 1e-8 * Math.max(1.0, mass)
          || Math.abs(enthalpy - vaporH - condensedH) > 1.0
              + 1e-5 * Math.max(Math.abs(enthalpy), Math.abs(vaporH) + Math.abs(condensedH))) {
        throw new IllegalStateException(
            "Flare header PH flash did not conserve discharge mass or enthalpy at " + time + " s");
      }
    }
    times.add(time);
    feedRates.add(rates);
    vaporRates.add(vapor);
    knockoutRates.add(liquid);
    headerMass.add(mass, time, dt);
    gasMass.add(gasRate, time, dt);
    gasMoles.add(Arrays.stream(vapor).sum(), time, dt);
    liquidMass.add(liquidRate, time, dt);
    inletH.add(enthalpy, time, dt);
    gasH.add(vaporH, time, dt);
    liquidH.add(condensedH, time, dt);
    heat.add(thermalDuty, time, dt);
    co2.add(carbonDioxide, time, dt);
  }

  /**
   * Publish independent peaks, interval-integrated totals and the aligned component-resolved profile.
   *
   * @return JSON-compatible flare load
   */
  Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("basis",
        "Adiabatic PH at the configured header pressure; equilibrium vapor to tip, condensed phases to knockout. "
            + "ISO 6976:1995 molar LHV, combustion at 15.55 C; complete conversion of gas-phase carbon to CO2, including inlet CO2.");
    map.put("peakMassFlowKgPerS", gasMass.peak);
    map.put("peakTimeS", gasMass.peakTime);
    map.put("peakHeatDutyW", heat.peak);
    map.put("peakHeatDutyMW", heat.peak * 1e-6);
    map.put("peakHeatDutyTimeS", heat.peakTime);
    map.put("peakMolarFlowMolePerS", gasMoles.peak);
    map.put("peakMolarFlowTimeS", gasMoles.peakTime);
    map.put("peakCO2EmissionKgPerS", co2.peak);
    map.put("peakCO2EmissionTimeS", co2.peakTime);
    map.put("totalMassToHeaderKg", headerMass.integral);
    map.put("totalMassToFlareKg", gasMass.integral);
    map.put("totalLiquidToKnockoutKg", liquidMass.integral);
    map.put("cumulativeHeatReleasedGJ", heat.integral * 1e-9);
    map.put("cumulativeCO2EmissionKg", co2.integral);
    map.put("cumulativeHeaderEnthalpyJ", inletH.integral);
    map.put("cumulativeGasEnthalpyJ", gasH.integral);
    map.put("cumulativeKnockoutEnthalpyJ", liquidH.integral);
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("timeS", times);
    profile.put("componentNames", componentNames);
    profile.put("headerComponentMolesPerS", feedRates);
    profile.put("vaporComponentMolesPerS", vaporRates);
    profile.put("knockoutComponentMolesPerS", knockoutRates);
    profile.put("headerMassFlowKgPerS", headerMass.values);
    profile.put("massFlowKgPerS", gasMass.values);
    profile.put("molarFlowMolePerS", gasMoles.values);
    profile.put("liquidToKnockoutKgPerS", liquidMass.values);
    profile.put("headerEnthalpyW", inletH.values);
    profile.put("gasEnthalpyW", gasH.values);
    profile.put("knockoutEnthalpyW", liquidH.values);
    profile.put("heatDutyW", heat.values);
    profile.put("co2EmissionKgPerS", co2.values);
    map.put("profile", profile);
    return map;
  }
}
