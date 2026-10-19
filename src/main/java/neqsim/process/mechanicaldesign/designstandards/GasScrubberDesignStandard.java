package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.data.ProcessDesignParameters;

/**
 * GasScrubberDesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class GasScrubberDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(GasScrubberDesignStandard.class);

  double gasLoadFactor = 0.11;
  double designFactorVolumeFlow = 1.1;
  double lengthGasInletToMeshpad = 550.0; // unit: mm
  double lengthGasInetToHHLL = 550.0; // unit: mm
  double lengthMeshPadToDemistingCyclone = 550.0; // unit: mm

  /**
   * Constructor for GasScrubberDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public GasScrubberDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    java.util.Map<String, ProcessDesignParameters.Value> values = loadProcessRequirements("Gas scrubber", name);
    gasLoadFactor = ProcessDesignParameters.require(values, "GasLoadFactor").maximum;
    designFactorVolumeFlow = ProcessDesignParameters.require(values, "FlowDesignFactor").maximum;
    lengthGasInletToMeshpad = ProcessDesignParameters.require(values, "LengthGasInletToMeshpad").minimum;
    lengthGasInetToHHLL = ProcessDesignParameters.require(values, "LengthGasInletToHHLL").minimum;
    lengthMeshPadToDemistingCyclone = ProcessDesignParameters.require(values,
        "LengthMeshPadToDemistingCyclone").minimum;
  }

  /**
   * Getter for the field <code>gasLoadFactor</code>.
   *
   * @return a double
   */
  public double getGasLoadFactor() {
    return gasLoadFactor;
  }

  /**
   * getVolumetricDesignFactor.
   *
   * @return a double
   */
  public double getVolumetricDesignFactor() {
    return designFactorVolumeFlow;
  }
}
