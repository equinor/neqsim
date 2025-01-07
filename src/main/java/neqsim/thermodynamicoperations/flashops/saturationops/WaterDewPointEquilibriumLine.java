package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * WaterDewPointEquilibriumLine class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class WaterDewPointEquilibriumLine extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double[][] hydratePoints = null;
  double minPressure = 1.0;
  double maxPressure = 200.0;

  int numberOfPoints = 10;

  /**
   * <p>
   * Constructor for WaterDewPointEquilibriumLine.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param minPres a double
   * @param maxPres a double
   */
  public WaterDewPointEquilibriumLine(SystemInterface system, double minPres, double maxPres) {
    super(system);
    minPressure = minPres;
    maxPressure = maxPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    SystemInterface system = this.system.clone();
    hydratePoints = new double[2][numberOfPoints];
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    system.setPressure(minPressure);
    double dp = (maxPressure - minPressure) / (numberOfPoints - 1.0);
    for (int i = 0; i < numberOfPoints; i++) {
      system.setPressure(minPressure + dp * i);
      try {
        ops.waterDewPointTemperatureMultiphaseFlash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
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
