package neqsim.process.equipment.energy;

import neqsim.process.equipment.stream.EnergyType;

/**
 * Energy converter with optional rated output and piecewise-linear load-efficiency curve.
 *
 * <p>
 * Without a curve, the inherited constant-efficiency behavior is unchanged. With a curve, efficiency is evaluated from
 * useful output divided by rated useful output, and forward conversion is solved by bounded bisection to preserve
 * energy conservation.
 * </p>
 */
public class LoadMappedEnergyConverter extends EnergyConverter {
  private static final long serialVersionUID = 1000L;
  private double ratedOutputPower = Double.POSITIVE_INFINITY;
  private LoadEfficiencyCurve loadEfficiencyCurve;

  protected LoadMappedEnergyConverter(String name, EnergyType inputType, EnergyType outputType) {
    super(name, inputType, outputType);
  }

  /** Sets rated useful output in W. */
  public void setRatedOutputPower(double ratedOutputPower) {
    if (!Double.isFinite(ratedOutputPower) || ratedOutputPower <= 0.0) {
      throw new IllegalArgumentException("Rated output power must be positive and finite");
    }
    this.ratedOutputPower = ratedOutputPower;
  }

  /** @return rated useful output in W, or positive infinity when unspecified */
  public double getRatedOutputPower() {
    return ratedOutputPower;
  }

  /** Attaches a load-efficiency curve. Rated output must also be configured before use. */
  public void setLoadEfficiencyCurve(LoadEfficiencyCurve curve) {
    if (curve == null) {
      throw new IllegalArgumentException("Load-efficiency curve is required");
    }
    loadEfficiencyCurve = curve;
  }

  /** Removes the curve and restores nominal constant efficiency. */
  public void clearLoadEfficiencyCurve() {
    loadEfficiencyCurve = null;
  }

  public LoadEfficiencyCurve getLoadEfficiencyCurve() {
    return loadEfficiencyCurve;
  }

  public boolean hasLoadEfficiencyCurve() {
    return loadEfficiencyCurve != null;
  }

  /** Gets operating efficiency at one requested useful output. */
  public double getEfficiencyAtOutputPower(double outputPower) {
    if (!Double.isFinite(outputPower) || outputPower < 0.0) {
      throw new IllegalArgumentException("Output power must be non-negative and finite");
    }
    if (loadEfficiencyCurve == null) {
      return getEfficiency();
    }
    requireRatedOutput();
    if (outputPower > ratedOutputPower + Math.max(1.0e-6, ratedOutputPower * 1.0e-10)) {
      throw new IllegalArgumentException("Requested output exceeds rated converter output");
    }
    return loadEfficiencyCurve.getEfficiency(outputPower / ratedOutputPower);
  }

  @Override
  protected double calculateRequiredInputForOutput(double output) {
    if (loadEfficiencyCurve == null) {
      return super.calculateRequiredInputForOutput(output);
    }
    if (output <= 0.0) {
      return 0.0;
    }
    double efficiency = getEfficiencyAtOutputPower(output);
    return output / efficiency + getIdleLoss();
  }

  @Override
  protected double calculateTargetOutput(double input) {
    if (loadEfficiencyCurve == null) {
      return super.calculateTargetOutput(input);
    }
    if (isTripped() || input <= getIdleLoss()) {
      return 0.0;
    }
    requireRatedOutput();
    if (calculateRequiredInputForOutput(ratedOutputPower) <= input) {
      return ratedOutputPower;
    }
    double lower = 0.0;
    double upper = ratedOutputPower;
    for (int iteration = 0; iteration < 60; iteration++) {
      double trial = 0.5 * (lower + upper);
      if (calculateRequiredInputForOutput(trial) <= input) {
        lower = trial;
      } else {
        upper = trial;
      }
    }
    return lower;
  }

  private void requireRatedOutput() {
    if (!Double.isFinite(ratedOutputPower) || ratedOutputPower <= 0.0) {
      throw new IllegalStateException("Configure rated output power before using a load-efficiency curve");
    }
  }
}
