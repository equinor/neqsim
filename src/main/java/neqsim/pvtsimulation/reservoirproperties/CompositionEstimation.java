package neqsim.pvtsimulation.reservoirproperties;

/**
 * <p>
 * CompositionEstimation class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompositionEstimation {
  double reservoirTemperature;
  double reservoirPressure;

  /**
   * <p>
   * Constructor for CompositionEstimation.
   * </p>
   *
   * @param reservoirTemperature a double
   * @param reservoirPressure a double
   */
  public CompositionEstimation(double reservoirTemperature, double reservoirPressure) {
    this.reservoirTemperature = reservoirTemperature;
    this.reservoirPressure = reservoirPressure;
  }

  /**
   * <p>
   * estimateH2Sconcentration. correlation from Haaland et. al. 1999
   * </p>
   *
   * @return a double
   */
  public double estimateH2Sconcentration() {
    return 5.0e7 * Math.exp(-6543.0 / reservoirTemperature);
  }

  /**
   * <p>
   * estimateH2Sconcentration. reservoir temperature in Kelvin CO2concentration in molfraction
   * </p>
   *
   * @param CO2concentration a double
   * @return a double
   */
  public double estimateH2Sconcentration(double CO2concentration) {
    return Math
        .exp(11.7 - 4438.3 / reservoirTemperature + 0.7 * Math.log(CO2concentration * 100.0));
  }
}
