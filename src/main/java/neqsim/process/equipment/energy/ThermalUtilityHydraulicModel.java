package neqsim.process.equipment.energy;

import java.io.Serializable;

/**
 * Single-header hydraulic screening model for a thermal utility circulation loop.
 *
 * <p>
 * The model uses Darcy-Weisbach pressure loss with a Haaland friction-factor correlation and optional aggregate
 * local-loss coefficient. It is intended for utility-header screening and capacity checks, not detailed network or
 * two-phase steam-distribution design.
 * </p>
 */
public final class ThermalUtilityHydraulicModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  private double length = 100.0;
  private double internalDiameter = 0.2;
  private double roughness = 4.5e-5;
  private double density = 1000.0;
  private double dynamicViscosity = 1.0e-3;
  private double localLossCoefficient = 0.0;
  private double pumpEfficiency = 0.75;
  private double maximumVelocity = Double.POSITIVE_INFINITY;
  private double maximumPressureDrop = Double.POSITIVE_INFINITY;

  public void setGeometry(double length, double internalDiameter, double roughness) {
    requirePositive(length, "Header length");
    requirePositive(internalDiameter, "Internal diameter");
    if (!Double.isFinite(roughness) || roughness < 0.0) {
      throw new IllegalArgumentException("Roughness must be non-negative and finite");
    }
    this.length = length;
    this.internalDiameter = internalDiameter;
    this.roughness = roughness;
  }

  public void setFluidProperties(double density, double dynamicViscosity) {
    requirePositive(density, "Density");
    requirePositive(dynamicViscosity, "Dynamic viscosity");
    this.density = density;
    this.dynamicViscosity = dynamicViscosity;
  }

  public void setLocalLossCoefficient(double localLossCoefficient) {
    if (!Double.isFinite(localLossCoefficient) || localLossCoefficient < 0.0) {
      throw new IllegalArgumentException("Local-loss coefficient must be non-negative and finite");
    }
    this.localLossCoefficient = localLossCoefficient;
  }

  public void setPumpEfficiency(double pumpEfficiency) {
    if (!Double.isFinite(pumpEfficiency) || pumpEfficiency <= 0.0 || pumpEfficiency > 1.0) {
      throw new IllegalArgumentException("Pump efficiency must be in (0, 1]");
    }
    this.pumpEfficiency = pumpEfficiency;
  }

  public void setCapacityLimits(double maximumVelocity, double maximumPressureDrop) {
    if (Double.isNaN(maximumVelocity) || maximumVelocity <= 0.0) {
      throw new IllegalArgumentException("Maximum velocity must be positive");
    }
    if (Double.isNaN(maximumPressureDrop) || maximumPressureDrop <= 0.0) {
      throw new IllegalArgumentException("Maximum pressure drop must be positive");
    }
    this.maximumVelocity = maximumVelocity;
    this.maximumPressureDrop = maximumPressureDrop;
  }

  public double getFlowArea() {
    return Math.PI * internalDiameter * internalDiameter / 4.0;
  }

  public double getVelocity(double massFlowKgPerSecond) {
    requireNonNegative(massFlowKgPerSecond, "Mass flow");
    return massFlowKgPerSecond / density / getFlowArea();
  }

  public double getReynoldsNumber(double massFlowKgPerSecond) {
    return density * getVelocity(massFlowKgPerSecond) * internalDiameter / dynamicViscosity;
  }

  public double getFrictionFactor(double massFlowKgPerSecond) {
    double reynolds = getReynoldsNumber(massFlowKgPerSecond);
    if (reynolds <= 0.0) {
      return 0.0;
    }
    if (reynolds < 2300.0) {
      return 64.0 / reynolds;
    }
    double haaland = -1.8 * Math.log10(Math.pow(roughness / internalDiameter / 3.7, 1.11) + 6.9 / reynolds);
    return 1.0 / (haaland * haaland);
  }

  public double getPressureDrop(double massFlowKgPerSecond) {
    double velocity = getVelocity(massFlowKgPerSecond);
    double dynamicPressure = 0.5 * density * velocity * velocity;
    return (getFrictionFactor(massFlowKgPerSecond) * length / internalDiameter + localLossCoefficient)
        * dynamicPressure;
  }

  public double getHydraulicPower(double massFlowKgPerSecond) {
    return getPressureDrop(massFlowKgPerSecond) * massFlowKgPerSecond / density;
  }

  public double getPumpPower(double massFlowKgPerSecond) {
    return getHydraulicPower(massFlowKgPerSecond) / pumpEfficiency;
  }

  public boolean isWithinCapacity(double massFlowKgPerSecond) {
    return getVelocity(massFlowKgPerSecond) <= maximumVelocity
        && getPressureDrop(massFlowKgPerSecond) <= maximumPressureDrop;
  }

  public double getMaximumMassFlow() {
    if (!Double.isFinite(maximumVelocity) && !Double.isFinite(maximumPressureDrop)) {
      return Double.POSITIVE_INFINITY;
    }
    double upper = Double.isFinite(maximumVelocity) ? maximumVelocity * density * getFlowArea()
        : density * getFlowArea();
    while (Double.isFinite(maximumPressureDrop) && getPressureDrop(upper) < maximumPressureDrop) {
      upper *= 2.0;
      if (!Double.isFinite(upper)) {
        return Double.POSITIVE_INFINITY;
      }
    }
    double lower = 0.0;
    for (int iteration = 0; iteration < 80; iteration++) {
      double trial = 0.5 * (lower + upper);
      if (isWithinCapacity(trial)) {
        lower = trial;
      } else {
        upper = trial;
      }
    }
    return lower;
  }

  public double getLength() {
    return length;
  }

  public double getInternalDiameter() {
    return internalDiameter;
  }

  public double getDensity() {
    return density;
  }

  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  private static void requireNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }
}
