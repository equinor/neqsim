package neqsim.process.fielddevelopment.hybrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Reservoir-state proxy that interpolates between several reservoir simulator scenarios.
 *
 * <p>
 * Each scenario is a {@link ReservoirStateProxy} from one simulator run (for example low and high water injection) that
 * also records how a second cumulative volume developed along the run. At the current primary cumulative volume the
 * scenarios are ordered by their value of the second quantity, the two that bracket the current value are selected and
 * every state variable is blended linearly between them:
 * </p>
 *
 * <p>
 * state = (1 - w) state_low(x) + w state_high(x), w = (y - y_low(x)) / (y_high(x) - y_low(x))
 * </p>
 *
 * <p>
 * where x is the primary cumulative volume (the abscissa of the tables) and y the secondary one. This makes the proxy a
 * function of two cumulative volumes, for example state = f(Np, Winj), so a strategy that lies between the pre-computed
 * scenarios can be followed without a new simulator run. Outside the envelope of the scenarios the weight is clamped
 * and {@link #isOutsideValidity()} returns true.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScenarioReservoirState extends AbstractReservoirState {
  private static final long serialVersionUID = 1000L;
  private final CumulativeQuantity secondaryQuantity;
  private final List<ReservoirStateProxy> scenarios = new ArrayList<ReservoirStateProxy>();
  private double validityTolerance = 0.02;

  /**
   * Creates an empty scenario blend.
   *
   * @param name name of the well or drainage unit
   * @param secondaryQuantity cumulative volume that distinguishes the scenarios
   */
  public ScenarioReservoirState(String name, CumulativeQuantity secondaryQuantity) {
    super(name);
    if (secondaryQuantity == null) {
      throw new IllegalArgumentException("Secondary quantity must not be null");
    }
    this.secondaryQuantity = secondaryQuantity;
  }

  /**
   * Adds a scenario table.
   *
   * @param scenario proxy of one simulator run with a column for the secondary quantity
   * @return this blend
   * @throws IllegalArgumentException if the table lacks the secondary quantity or uses another abscissa than the
   * scenarios already added
   */
  public ScenarioReservoirState addScenario(ReservoirStateProxy scenario) {
    if (!scenario.hasQuantityColumn(secondaryQuantity)) {
      throw new IllegalArgumentException("Scenario " + scenario.getName() + " has no column " + secondaryQuantity);
    }
    if (!scenarios.isEmpty() && scenarios.get(0).getAbscissaQuantity() != scenario.getAbscissaQuantity()) {
      throw new IllegalArgumentException("All scenarios must use the same abscissa quantity");
    }
    if (scenario.getAbscissaQuantity() == secondaryQuantity) {
      throw new IllegalArgumentException("Secondary quantity must differ from the abscissa quantity");
    }
    scenarios.add(scenario);
    return this;
  }

  /**
   * Sets the relative distance outside the scenario envelope that is still reported as valid.
   *
   * @param tolerance fraction of the envelope width, 0.02 by default
   */
  public void setValidityTolerance(double tolerance) {
    this.validityTolerance = Math.max(0.0, tolerance);
  }

  /**
   * Gets the cumulative volume that distinguishes the scenarios.
   *
   * @return secondary quantity
   */
  public CumulativeQuantity getSecondaryQuantity() {
    return secondaryQuantity;
  }

  /**
   * Gets the number of scenarios.
   *
   * @return scenario count
   */
  public int getScenarioCount() {
    return scenarios.size();
  }

  /**
   * Finds the two bracketing scenarios and the blend weight at the current cumulative volumes.
   *
   * @return array of lower scenario index, upper scenario index, weight of the upper scenario in [0, 1] and the
   * unclamped weight
   * @throws IllegalStateException if no scenario has been added
   */
  private double[] bracket() {
    if (scenarios.isEmpty()) {
      throw new IllegalStateException("Add at least one scenario to " + getName());
    }
    double x = getCumulative(scenarios.get(0).getAbscissaQuantity());
    double y = getCumulative(secondaryQuantity);
    int lower = -1;
    int upper = -1;
    double yLower = Double.NEGATIVE_INFINITY;
    double yUpper = Double.POSITIVE_INFINITY;
    int lowest = 0;
    int highest = 0;
    double yLowest = Double.POSITIVE_INFINITY;
    double yHighest = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < scenarios.size(); i++) {
      double ys = scenarios.get(i).quantityAt(secondaryQuantity, x);
      if (ys <= y && ys > yLower) {
        lower = i;
        yLower = ys;
      }
      if (ys >= y && ys < yUpper) {
        upper = i;
        yUpper = ys;
      }
      if (ys < yLowest) {
        lowest = i;
        yLowest = ys;
      }
      if (ys > yHighest) {
        highest = i;
        yHighest = ys;
      }
    }
    if (lower < 0) {
      double width = Math.max(yHighest - yLowest, 1.0e-12);
      return new double[] {lowest, lowest, 0.0, (y - yLowest) / width};
    }
    if (upper < 0) {
      double width = Math.max(yHighest - yLowest, 1.0e-12);
      return new double[] {highest, highest, 0.0, 1.0 + (y - yHighest) / width};
    }
    if (lower == upper) {
      double position = (y - yLowest) / Math.max(yHighest - yLowest, 1.0e-12);
      return new double[] {lower, upper, 0.0, position};
    }
    if (yUpper - yLower < 1.0e-12) {
      return new double[] {lower, upper, 0.5, 0.5};
    }
    double weight = (y - yLower) / (yUpper - yLower);
    return new double[] {lower, upper, weight, weight};
  }

  /**
   * Gets the weight of the upper bracketing scenario at the current cumulative volumes.
   *
   * @return blend weight in [0, 1]
   */
  public double getBlendWeight() {
    return bracket()[2];
  }

  /**
   * Gets the names of the two scenarios currently blended.
   *
   * @return lower and upper scenario names
   */
  public String[] getActiveScenarios() {
    double[] b = bracket();
    return new String[] {scenarios.get((int) b[0]).getName(), scenarios.get((int) b[1]).getName()};
  }

  /**
   * Gets the current abscissa value of the scenario tables.
   *
   * @return primary cumulative volume in Sm3
   */
  private double primary() {
    return getCumulative(scenarios.get(0).getAbscissaQuantity());
  }

  /** {@inheritDoc} */
  @Override
  public double getReservoirPressure() {
    double[] b = bracket();
    double x = primary();
    return (1.0 - b[2]) * scenarios.get((int) b[0]).pressureAt(x) + b[2] * scenarios.get((int) b[1]).pressureAt(x);
  }

  /** {@inheritDoc} */
  @Override
  public double getWaterCut() {
    double[] b = bracket();
    double x = primary();
    return (1.0 - b[2]) * scenarios.get((int) b[0]).waterCutAt(x) + b[2] * scenarios.get((int) b[1]).waterCutAt(x);
  }

  /** {@inheritDoc} */
  @Override
  public double getGasOilRatio() {
    double[] b = bracket();
    double x = primary();
    return (1.0 - b[2]) * scenarios.get((int) b[0]).gasOilRatioAt(x)
        + b[2] * scenarios.get((int) b[1]).gasOilRatioAt(x);
  }

  /** {@inheritDoc} */
  @Override
  public double getProductivityIndex() {
    double[] b = bracket();
    double x = primary();
    return (1.0 - b[2]) * scenarios.get((int) b[0]).productivityIndexAt(x)
        + b[2] * scenarios.get((int) b[1]).productivityIndexAt(x);
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasComposition() {
    if (scenarios.isEmpty()) {
      return false;
    }
    for (ReservoirStateProxy scenario : scenarios) {
      if (!scenario.hasComposition()) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String[] getComponentNames() {
    return hasComposition() ? scenarios.get(0).getComponentNames() : null;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getComposition() {
    if (!hasComposition()) {
      return null;
    }
    double[] b = bracket();
    double x = primary();
    double[] low = scenarios.get((int) b[0]).compositionAt(x);
    double[] high = scenarios.get((int) b[1]).compositionAt(x);
    double[] z = new double[low.length];
    for (int i = 0; i < z.length; i++) {
      z[i] = (1.0 - b[2]) * low[i] + b[2] * high[i];
    }
    return z;
  }

  /** {@inheritDoc} */
  @Override
  public double getHydrocarbonMolesPerSm3() {
    if (!hasComposition()) {
      return Double.NaN;
    }
    double[] b = bracket();
    double x = primary();
    return (1.0 - b[2]) * scenarios.get((int) b[0]).hydrocarbonMolesPerSm3At(x)
        + b[2] * scenarios.get((int) b[1]).hydrocarbonMolesPerSm3At(x);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOutsideValidity() {
    double[] b = bracket();
    double x = primary();
    if (b[3] < -validityTolerance || b[3] > 1.0 + validityTolerance) {
      return true;
    }
    return scenarios.get((int) b[0]).isOutsideTable(x) || scenarios.get((int) b[1]).isOutsideTable(x);
  }
}
