package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * HydrateEquilibriumLine class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HydrateEquilibriumLine extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] hydratePoints = null;
  double minPressure = 1.0, maxPressure = 200.0;
  int numberOfPoints = 10;

  /**
   * <p>
   * Constructor for HydrateEquilibriumLine.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param minPres a double
   * @param maxPres a double
   */
  public HydrateEquilibriumLine(SystemInterface system, double minPres, double maxPres) {
    super(system);
    minPressure = minPres;
    maxPressure = maxPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    SystemInterface system = this.system.clone();
    hydratePoints = new double[2][numberOfPoints];
    system.setHydrateCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    system.setPressure(minPressure);
    double dp = (maxPressure - minPressure) / (numberOfPoints - 1.0);

    // Use previous temperature as initial guess for faster convergence
    double previousTemp = system.getTemperature();

    for (int i = 0; i < numberOfPoints; i++) {
      system.setPressure(minPressure + dp * i);

      // Set temperature to previous result as starting guess (hydrate T increases with P)
      if (i > 0 && previousTemp > 0) {
        system.setTemperature(previousTemp);
      }

      try {
        ops.hydrateFormationTemperature();
        previousTemp = system.getTemperature();
      } catch (Exception ex) {
        // logger.error(ex.getMessage(),e);
      }
      hydratePoints[0][i] = system.getTemperature();
      hydratePoints[1][i] = system.getPressure();
      // system.display();
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[][] getPoints(int i) {
    return hydratePoints;
  }
}
