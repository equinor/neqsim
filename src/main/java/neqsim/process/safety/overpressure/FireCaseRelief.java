package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.util.fire.ReliefValveSizing;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the vapour relief load from an external pool-fire exposure per TR3001 section 4.7.8 (SR-26504) and API STD
 * 521 section 4.4.13 using the API 521 Table 4 wetted-surface heat-input correlation.
 *
 * <p>
 * The fire heat absorption is obtained from
 * {@link ReliefValveSizing#calculateAPI521FireHeatInput(double, boolean, boolean)} as Q = C1 &times; F &times;
 * A<sub>ws</sub><sup>0.82</sup>, where C1 is 21000 BTU/hr&middot;ft<sup>2</sup> with adequate drainage and 34500
 * BTU/hr&middot;ft<sup>2</sup> without. The relief rate is the vapour generation rate Q / h<sub>vap</sub>, where
 * h<sub>vap</sub> is the latent heat of vaporization at the relieving pressure.
 * </p>
 *
 * <p>
 * The wetted area may be supplied directly or computed from the vessel diameter and wetted (liquid) height for the
 * shell. API 521 limits the credited wetted area to the portion below an elevation of about 7.6 m (25 ft) above the
 * fire source; that elevation cut-off is the responsibility of the caller when supplying geometry.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class FireCaseRelief {
  private String name = "Pool fire";
  private double wettedAreaM2 = Double.NaN;
  private double vesselDiameterM = Double.NaN;
  private double wettedHeightM = Double.NaN;
  private boolean hasDrainage = true;
  private boolean hasFireFighting = true;
  private double latentHeatJPerKg = Double.NaN;
  private double reliefPressureBara = Double.NaN;
  private double reliefTemperatureK = Double.NaN;
  private SystemInterface fluid = null;

  /**
   * Sets the scenario name.
   *
   * @param name the scenario name
   * @return this calculator for chaining
   */
  public FireCaseRelief setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the fire-exposed wetted surface area directly.
   *
   * @param wettedAreaM2 the wetted area in m^2; must be positive
   * @return this calculator for chaining
   */
  public FireCaseRelief setWettedAreaM2(double wettedAreaM2) {
    this.wettedAreaM2 = wettedAreaM2;
    return this;
  }

  /**
   * Sets the vessel shell diameter used to derive the wetted area from geometry.
   *
   * @param vesselDiameterM the vessel inside diameter in m; must be positive
   * @return this calculator for chaining
   */
  public FireCaseRelief setVesselDiameterM(double vesselDiameterM) {
    this.vesselDiameterM = vesselDiameterM;
    return this;
  }

  /**
   * Sets the liquid-wetted height of the shell used to derive the wetted area from geometry.
   *
   * @param wettedHeightM the wetted shell height in m; must be positive
   * @return this calculator for chaining
   */
  public FireCaseRelief setWettedHeightM(double wettedHeightM) {
    this.wettedHeightM = wettedHeightM;
    return this;
  }

  /**
   * Sets whether adequate drainage and firefighting exist (API 521 21000 vs 34500 BTU/hr&middot;ft^2 basis).
   *
   * @param hasDrainage true if adequate drainage exists
   * @return this calculator for chaining
   */
  public FireCaseRelief setHasDrainage(boolean hasDrainage) {
    this.hasDrainage = hasDrainage;
    return this;
  }

  /**
   * Sets whether firefighting facilities are credited.
   *
   * @param hasFireFighting true if firefighting equipment is available
   * @return this calculator for chaining
   */
  public FireCaseRelief setHasFireFighting(boolean hasFireFighting) {
    this.hasFireFighting = hasFireFighting;
    return this;
  }

  /**
   * Sets the latent heat of vaporization at the relieving pressure used to convert heat input to vapour rate.
   *
   * @param latentHeatJPerKg the latent heat in J/kg; must be positive
   * @return this calculator for chaining
   */
  public FireCaseRelief setLatentHeatJPerKg(double latentHeatJPerKg) {
    this.latentHeatJPerKg = latentHeatJPerKg;
    return this;
  }

  /**
   * Sets the relieving pressure at which the vapour properties are evaluated.
   *
   * @param reliefPressureBara the relieving pressure in bara; must be positive
   * @return this calculator for chaining
   */
  public FireCaseRelief setReliefPressureBara(double reliefPressureBara) {
    this.reliefPressureBara = reliefPressureBara;
    return this;
  }

  /**
   * Sets the relieving temperature in degrees Celsius.
   *
   * @param reliefTemperatureC the relieving temperature in degrees Celsius
   * @return this calculator for chaining
   */
  public FireCaseRelief setReliefTemperatureC(double reliefTemperatureC) {
    this.reliefTemperatureK = reliefTemperatureC + 273.15;
    return this;
  }

  /**
   * Sets the fluid used to evaluate the relieving vapour properties.
   *
   * @param fluid the NeqSim fluid; not null
   * @return this calculator for chaining
   */
  public FireCaseRelief setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Calculates the fire-case vapour relief scenario.
   *
   * @return the {@link ReliefScenario} with the relief rate equal to the fire vapour-generation rate
   */
  public ReliefScenario calculate() {
    List<String> warnings = new ArrayList<String>();
    double area = wettedAreaM2;
    if (Double.isNaN(area) || area <= 0.0) {
      if (!Double.isNaN(vesselDiameterM) && vesselDiameterM > 0.0 && !Double.isNaN(wettedHeightM)
          && wettedHeightM > 0.0) {
        area = Math.PI * vesselDiameterM * wettedHeightM;
        warnings.add("Wetted area derived from shell geometry (pi*D*h) = " + String.format("%.2f", area) + " m2");
      } else {
        warnings.add("Wetted area not set; fire relief rate is zero");
        area = 0.0;
      }
    }

    double heatInputW = area > 0.0 ? ReliefValveSizing.calculateAPI521FireHeatInput(area, hasDrainage, hasFireFighting)
        : 0.0;

    double latent = latentHeatJPerKg;
    if (Double.isNaN(latent) || latent <= 0.0) {
      latent = 350000.0;
      warnings.add("Latent heat not set; using default 350 kJ/kg for light hydrocarbon");
    }

    double reliefRate = latent > 0.0 ? heatInputW / latent : 0.0;

    ReliefScenario.Builder builder = new ReliefScenario.Builder(name, ReliefCause.FIRE).phase(ReliefPhase.VAPOUR)
        .reliefRateKgPerS(reliefRate).latentHeatJPerKg(latent).addAssumption("Fire heat input per API 521 Table 4 ("
            + (hasDrainage ? "21000" : "34500") + " BTU/hr/ft^2 basis); relief rate = Q/latent heat");

    if (fluid != null) {
      double pres = !Double.isNaN(reliefPressureBara) ? reliefPressureBara : fluid.getPressure("bara");
      double temp = !Double.isNaN(reliefTemperatureK) ? reliefTemperatureK : fluid.getTemperature();
      ReliefFluidState propState = ReliefFluidState.evaluate(fluid, pres, temp, warnings);
      builder.reliefTemperatureK(propState.temperatureK).molarMassKgPerMol(propState.molarMassKgPerMol)
          .compressibility(propState.compressibility).specificHeatRatio(propState.specificHeatRatio)
          .densityKgPerM3(propState.densityKgPerM3);
    } else {
      warnings.add("No fluid supplied; relieving-vapour properties default to light-hydrocarbon values");
      if (!Double.isNaN(reliefTemperatureK)) {
        builder.reliefTemperatureK(reliefTemperatureK);
      }
    }

    builder.assumptions(warnings);
    return builder.build();
  }
}
