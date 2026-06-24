package neqsim.pvtsimulation.reservoirproperties;

/**
 * CompositionEstimation class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompositionEstimation {
  double reservoirTemperature;
  double reservoirPressure;

  /**
   * Constructor for CompositionEstimation.
   *
   * @param reservoirTemperature a double
   * @param reservoirPressure a double
   */
  public CompositionEstimation(double reservoirTemperature, double reservoirPressure) {
    this.reservoirTemperature = reservoirTemperature;
    this.reservoirPressure = reservoirPressure;
  }

  /**
   * estimateH2Sconcentration. correlation from Haaland et. al. 1999
   *
   * @return a double
   */
  public double estimateH2Sconcentration() {
    return 5.0e7 * Math.exp(-6543.0 / reservoirTemperature);
  }

  /**
   * estimateH2Sconcentration. reservoir temperature in Kelvin CO2concentration in molfraction
   *
   * @param CO2concentration a double
   * @return a double
   */
  public double estimateH2Sconcentration(double CO2concentration) {
    return Math.exp(11.7 - 4438.3 / reservoirTemperature + 0.7 * Math.log(CO2concentration * 100.0));
  }
}
