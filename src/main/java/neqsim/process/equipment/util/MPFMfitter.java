package neqsim.process.equipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * GORfitter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class MPFMfitter extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MPFMfitter.class);

  double pressure = ThermodynamicConstantsInterface.referencePressure;
  double temperature = 15.0;
  private String referenceConditions = "standard"; // "actual";
  private boolean fitAsGVF = false;

  private double GOR = 120.0;
  private double GVF;
  String unitT = "C";
  String unitP = "bara";

  SystemInterface referenceFluidPackage = null;

  @Deprecated
  /**
   * <p>
   * Constructor for MPFMfitter.
   * </p>
   */
  public MPFMfitter() {
    super("MPFMfitter fitter");
  }

  /**
   * <p>
   * Constructor for MPFMfitter.
   * </p>
   *
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  @Deprecated
  public MPFMfitter(StreamInterface stream) {
    this("MPFMfitter", stream);
  }

  /**
   * <p>
   * Constructor for MPFMfitter.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param stream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public MPFMfitter(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * <p>
   * getGFV.
   * </p>
   *
   * @return a double
   */
  public double getGFV() {
    return GVF;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return pressure;
  }

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   * @param unitP a {@link java.lang.String} object
   */
  public void setPressure(double pressure, String unitP) {
    this.pressure = pressure;
    this.unitP = unitP;
  }

  /**
   * <p>
   * getTemperature.
   * </p>
   *
   * @return a double
   */
  @Override
  public double getTemperature() {
    return temperature;
  }

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param temperature a double
   * @param unitT a {@link java.lang.String} object
   */
  public void setTemperature(double temperature, String unitT) {
    this.temperature = temperature;
    this.unitT = unitT;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface tempFluid = inStream.getThermoSystem().clone();
    double flow = tempFluid.getFlowRate("kg/sec");

    if (GOR < 1e-15) {
      outStream.setThermoSystem(tempFluid);
      return;
    }

    if (flow < 1e-6) {
      outStream.setThermoSystem(tempFluid);
      return;
    }

    if (GOR == 0 && tempFluid.hasPhaseType("gas")) {
      tempFluid.removePhase(0);
      ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
      try {
        thermoOps.TPflash();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
      outStream.setThermoSystem(tempFluid);
      return;
    }
    if (!getReferenceConditions().equals("actual")) {
      tempFluid.setTemperature(15.0, "C");
      tempFluid.setPressure(ThermodynamicConstantsInterface.referencePressure, "bara");
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(tempFluid);
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    if (!tempFluid.hasPhaseType("gas") || !tempFluid.hasPhaseType("oil")) {
      outStream = inStream.clone();
      return;
    }
    tempFluid.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double currGOR = tempFluid.getPhase("gas").getCorrectedVolume()
        / tempFluid.getPhase("oil").getCorrectedVolume();

    if (fitAsGVF) {
      GOR = tempFluid.getPhase("oil").getCorrectedVolume() * getGOR()
          / (tempFluid.getPhase("oil").getCorrectedVolume()
              - tempFluid.getPhase("oil").getCorrectedVolume() * getGOR());
      // GVF*Vo/(Vo-GVF*Vo)
      // currGOR = tempFluid.getPhase("gas").getCorrectedVolume()
      // / (tempFluid.getPhase("oil").getCorrectedVolume() +
      // tempFluid.getPhase("gas").getCorrectedVolume());
    }

    double dev = getGOR() / currGOR;
    // System.out.println("dev "+dev);

    double[] moleChange = new double[tempFluid.getNumberOfComponents()];
    for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
      moleChange[i] =
          (dev - 1.0) * tempFluid.getPhase("gas").getComponent(i).getNumberOfMolesInPhase();
    }
    tempFluid.init(0);
    for (int i = 0; i < tempFluid.getNumberOfComponents(); i++) {
      tempFluid.addComponent(i, moleChange[i]);
    }
    tempFluid.setPressure((inStream.getThermoSystem()).getPressure());
    tempFluid.setTemperature((inStream.getThermoSystem()).getTemperature());
    tempFluid.setTotalFlowRate(flow, "kg/sec");
    try {
      thermoOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    tempFluid.initProperties();
    outStream.setThermoSystem(tempFluid);
    if (!tempFluid.hasPhaseType("gas")) {
      GVF = 0.0;
    } else if (tempFluid.hasPhaseType("gas") && tempFluid.hasPhaseType("oil")) {
      GVF = tempFluid.getPhase("gas").getCorrectedVolume()
          / (tempFluid.getPhase("oil").getCorrectedVolume()
              + tempFluid.getPhase("gas").getCorrectedVolume());
    } else {
      GVF = Double.NaN;
    }

    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * getGOR.
   * </p>
   *
   * @return a double
   */
  public double getGOR() {
    return GOR;
  }

  /**
   * <p>
   * setGOR.
   * </p>
   *
   * @param gOR a double
   */
  public void setGOR(double gOR) {
    fitAsGVF = false;
    this.GOR = gOR;
  }

  /**
   * <p>
   * setGVF.
   * </p>
   *
   * @param gvf a double
   */
  public void setGVF(double gvf) {
    fitAsGVF = true;
    this.GOR = gvf;
  }

  /**
   * <p>
   * Getter for the field <code>referenceConditions</code>.
   * </p>
   *
   * @return the referenceConditions
   */
  public String getReferenceConditions() {
    return referenceConditions;
  }

  /**
   * <p>
   * Setter for the field <code>referenceConditions</code>.
   * </p>
   *
   * @param referenceConditions the referenceConditions to set
   */
  public void setReferenceConditions(String referenceConditions) {
    this.referenceConditions = referenceConditions;
  }

  /**
   * <p>
   * isFitAsGVF.
   * </p>
   *
   * @return the fitAsGVF
   */
  public boolean isFitAsGVF() {
    return fitAsGVF;
  }

  /**
   * <p>
   * Setter for the field <code>fitAsGVF</code>.
   * </p>
   *
   * @param fitAsGVF the fitAsGVF to set
   */
  public void setFitAsGVF(boolean fitAsGVF) {
    this.fitAsGVF = fitAsGVF;
  }

  /**
   * <p>
   * Getter for the field <code>referenceFluidPackage</code>.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getReferenceFluidPackage() {
    return referenceFluidPackage;
  }

  /**
   * <p>
   * Setter for the field <code>referenceFluidPackage</code>.
   * </p>
   *
   * @param referenceFluidPackage a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setReferenceFluidPackage(SystemInterface referenceFluidPackage) {
    this.referenceFluidPackage = referenceFluidPackage;
  }
}
