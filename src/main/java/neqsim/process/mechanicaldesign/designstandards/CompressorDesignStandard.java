package neqsim.process.mechanicaldesign.designstandards;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.data.ProcessDesignParameters;

/**
 * CompressorDesignStandard class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class CompressorDesignStandard extends DesignStandard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CompressorDesignStandard.class);

  private double compressorFactor = 0.11;

  /**
   * Constructor for CompressorDesignStandard.
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentInn a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public CompressorDesignStandard(String name, MechanicalDesign equipmentInn) {
    super(name, equipmentInn);

    java.util.Map<String, ProcessDesignParameters.Value> values = loadProcessRequirements("Compressor", name);
    compressorFactor = ProcessDesignParameters.require(values, "compressorFactor").maximum;
  }

  /**
   * Getter for the field <code>compressorFactor</code>.
   *
   * @return the compressorFactor
   */
  public double getCompressorFactor() {
    return compressorFactor;
  }

  /**
   * Setter for the field <code>compressorFactor</code>.
   *
   * @param compressorFactor the compressorFactor to set
   */
  public void setCompressorFactor(double compressorFactor) {
    this.compressorFactor = compressorFactor;
  }
}
