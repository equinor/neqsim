package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * MolarMassAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class MolarMassAnalyser extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for MolarMassAnalyser.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public MolarMassAnalyser(StreamInterface stream) {
    this("molar mass analyser", stream);
  }

  /**
   * <p>
   * Constructor for MolarMassAnalyser.
   * </p>
   *
   * @param name Name of MolarMassAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public MolarMassAnalyser(String name, StreamInterface stream) {
    super(name, "gr/mol", stream);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    System.out.println("measured Molar mass " + getMeasuredValue());
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    if (!unit.equalsIgnoreCase("gr/mol")) {
      throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this,
          "getMeasuredValue", "unit", "currently only supports \"gr/mol\""));
    }
    return stream.getThermoSystem().getMolarMass() * 1000.0;
  }
}
