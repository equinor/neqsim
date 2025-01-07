package neqsim.process.measurementdevice;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * NMVOCAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class NMVOCAnalyser extends StreamMeasurementDeviceBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for NMVOCAnalyser.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public NMVOCAnalyser(StreamInterface stream) {
    this("NM VOC Analyser", stream);
  }

  /**
   * <p>
   * Constructor for NMVOCAnalyser.
   * </p>
   *
   * @param name Name of NMVOCAnalyser
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public NMVOCAnalyser(String name, StreamInterface stream) {
    super(name, "kg/hr", stream);
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue(String unit) {
    return getnmVOCFlowRate(unit);
  }

  /**
   * <p>
   * Calculates the mass flow rate of non-methane volatile organic compounds (nmVOCs).
   * </p>
   *
   * @param unit Unit to get measurement in
   * @return the flow rate of nmVOCs in the flow unit set setUnit method (e.g. "kg/hr",
   *         "tonnes/year")
   */
  public double getnmVOCFlowRate(String unit) {
    // Define list of components to include in mass flow calculation
    java.util.List<String> nmVOCcomponents =
        java.util.Arrays.asList("ethane", "propane", "i-butane", "n-butane", "i-pentane",
            "n-pentane", "n-hexane", "n-heptane", "benzene", "nC8", "nC9", "nC10", "nC11");

    double flow = 0.0;
    for (int i = 0; i < this.stream.getFluid().getNumberOfComponents(); i++) {
      String name = this.stream.getFluid().getComponent(i).getName();
      if (nmVOCcomponents.contains(name)) {
        flow += this.stream.getFluid().getComponent(i).getFlowRate(unit);
      }
    }

    return flow;
  }
}
