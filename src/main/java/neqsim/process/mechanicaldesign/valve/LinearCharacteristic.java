package neqsim.process.mechanicaldesign.valve;

/**
 * Represents a valve with a linear flow characteristic. For linear characteristics, the flow is
 * directly proportional to the opening.
 *
 * @author esol
 */
public class LinearCharacteristic implements ValveCharacteristic {

  /** {@inheritDoc} */
  @Override
  public double getActualKv(double Kv, double percentOpening) {
    // For linear characteristics, the flow is directly proportional to the opening
    return Kv * getOpeningFactor(percentOpening);
  }

  /** {@inheritDoc} */
  @Override
  public double getOpeningFactor(double percentOpening) {
    return percentOpening / 100.0;
  }
}
