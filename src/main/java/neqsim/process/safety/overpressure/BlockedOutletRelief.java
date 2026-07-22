package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the relief load for a blocked-outlet contingency per TR3001 section 4.7.2 (SR-26460) and API STD 521
 * section 4.4.2.
 *
 * <p>
 * TR3001 SR-26460 states that the safe and simple approach is to design the pressure relief device for the full inflow
 * to the protected item. This calculator therefore sets the required relief rate equal to the maximum credible inflow,
 * evaluated at the relieving conditions to obtain the relieving-fluid properties needed for downstream device sizing.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BlockedOutletRelief {
  private String name = "Blocked outlet";
  private double inflowRateKgPerS = Double.NaN;
  private double reliefPressureBara = Double.NaN;
  private double reliefTemperatureK = Double.NaN;
  private SystemInterface fluid = null;

  /**
   * Sets the scenario name.
   *
   * @param name the scenario name
   * @return this calculator for chaining
   */
  public BlockedOutletRelief setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the maximum credible inflow rate to the protected item.
   *
   * @param inflowRateKgPerS the inflow rate in kg/s; must be positive
   * @return this calculator for chaining
   */
  public BlockedOutletRelief setInflowRateKgPerS(double inflowRateKgPerS) {
    this.inflowRateKgPerS = inflowRateKgPerS;
    return this;
  }

  /**
   * Sets the maximum credible inflow rate to the protected item in kg/hr.
   *
   * @param inflowRateKgPerHr the inflow rate in kg/hr; must be positive
   * @return this calculator for chaining
   */
  public BlockedOutletRelief setInflowRateKgPerHr(double inflowRateKgPerHr) {
    this.inflowRateKgPerS = inflowRateKgPerHr / 3600.0;
    return this;
  }

  /**
   * Sets the relieving pressure at which fluid properties are evaluated.
   *
   * @param reliefPressureBara the relieving pressure in bara; must be positive
   * @return this calculator for chaining
   */
  public BlockedOutletRelief setReliefPressureBara(double reliefPressureBara) {
    this.reliefPressureBara = reliefPressureBara;
    return this;
  }

  /**
   * Sets the relieving temperature at which fluid properties are evaluated.
   *
   * @param reliefTemperatureC the relieving temperature in degrees Celsius
   * @return this calculator for chaining
   */
  public BlockedOutletRelief setReliefTemperatureC(double reliefTemperatureC) {
    this.reliefTemperatureK = reliefTemperatureC + 273.15;
    return this;
  }

  /**
   * Sets the inflow fluid used to evaluate the relieving-phase properties.
   *
   * @param fluid the NeqSim fluid; not null
   * @return this calculator for chaining
   */
  public BlockedOutletRelief setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Calculates the blocked-outlet relief scenario.
   *
   * @return the {@link ReliefScenario} with the relief rate equal to the full inflow
   */
  public ReliefScenario calculate() {
    List<String> warnings = new ArrayList<String>();
    if (Double.isNaN(inflowRateKgPerS) || inflowRateKgPerS <= 0.0) {
      warnings.add("Inflow rate not set or non-positive; relief rate is zero");
      inflowRateKgPerS = 0.0;
    }

    ReliefScenario.Builder builder = new ReliefScenario.Builder(name, ReliefCause.BLOCKED_OUTLET)
        .reliefRateKgPerS(inflowRateKgPerS).addAssumption("Relief rate set equal to full inflow per TR3001 SR-26460");

    if (fluid != null) {
      double pres = !Double.isNaN(reliefPressureBara) ? reliefPressureBara : fluid.getPressure("bara");
      double temp = !Double.isNaN(reliefTemperatureK) ? reliefTemperatureK : fluid.getTemperature();
      ReliefFluidState state = ReliefFluidState.evaluate(fluid, pres, temp, warnings);
      builder.phase(state.phase).reliefTemperatureK(state.temperatureK).molarMassKgPerMol(state.molarMassKgPerMol)
          .compressibility(state.compressibility).specificHeatRatio(state.specificHeatRatio);
    } else {
      warnings.add("No fluid supplied; relieving-phase properties default to vapour");
      if (!Double.isNaN(reliefTemperatureK)) {
        builder.reliefTemperatureK(reliefTemperatureK);
      }
    }

    builder.assumptions(warnings);
    return builder.build();
  }
}
