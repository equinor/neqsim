package neqsim.processSimulation.measurementDevice;

import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * NMVOCAnalyser class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class NMVOCAnalyser extends MeasurementDeviceBaseClass {
  private static final long serialVersionUID = 1000;
  protected StreamInterface stream = null;

  /**
   * <p>
   * Constructor for MolarMassAnalyser.
   * </p>
   */
  public NMVOCAnalyser() {
    name = "NM VOC Analyser";
  }

  /**
   * <p>
   * Constructor for MolarMassAnalyser.
   * </p>
   *
   * @param stream a
   *               {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *               object
   */
  public NMVOCAnalyser(StreamInterface stream) {
    this();
    this.stream = stream;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return getnmVOCFlowRate();
  }

  /**
   * Calculates the mass flow rate of non-methane volatile organic compounds
   * (nmVOCs)
   * 
   * 
   * @return the flow rate of nmVOCs in the flow unit set setUnit method (e.g.
   *         "kg/hr", "tonnes/year")
   */
  public double getnmVOCFlowRate() {
    // Define list of components to include in mass flow calculation
    java.util.List<String> nmVOCcomponents = java.util.Arrays.asList("ethane", "propane", "i-butane", "n-butane",
        "i-pentane",
        "n-pentane",
        "n-hexane", "n-heptane", "benzene", "nC8", "nC9", "nC10", "nC11");

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
