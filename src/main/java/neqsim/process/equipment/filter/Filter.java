package neqsim.process.equipment.filter;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Filter class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class Filter extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double deltaP = 0.01;
  private double Cv = 0.0;

  /**
   * <p>
   * Constructor for Filter.
   * </p>
   *
   * @param name name of filter
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Filter(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inStream.getThermoSystem().clone();
    if (Math.abs(getDeltaP()) > 1e-10) {
      system.setPressure(inStream.getPressure() - getDeltaP());
      ThermodynamicOperations testOps = new ThermodynamicOperations(system);
      testOps.TPflash();
    }
    system.initProperties();
    outStream.setThermoSystem(system);
    Cv = Math.sqrt(deltaP) / inStream.getFlowRate("kg/hr");
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * Getter for the field <code>deltaP</code>.
   * </p>
   *
   * @return a double
   */
  public double getDeltaP() {
    return deltaP;
  }

  /**
   * <p>
   * Setter for the field <code>deltaP</code>.
   * </p>
   *
   * @param deltaP a double
   */
  public void setDeltaP(double deltaP) {
    this.deltaP = deltaP;
    this.outStream.setPressure(this.inStream.getPressure() - deltaP);
  }

  /**
   * <p>
   * Setter for the field <code>deltaP</code>.
   * </p>
   *
   * @param deltaP a double
   * @param unit a {@link java.lang.String} object
   */
  public void setDeltaP(double deltaP, String unit) {
    this.deltaP = deltaP;
    this.outStream.setPressure(this.inStream.getPressure(unit) - deltaP, unit);
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refTEGabsorberloc) {
    double deltaP = inStream.getPressure("bara") - outStream.getPressure("bara");
    Cv = Math.sqrt(deltaP) / inStream.getFlowRate("kg/hr");
  }

  /**
   * <p>
   * getCvFactor.
   * </p>
   *
   * @return a double
   */
  public double getCvFactor() {
    return Cv;
  }

  /**
   * <p>
   * setCvFactor.
   * </p>
   *
   * @param pressureCoef a double
   */
  public void setCvFactor(double pressureCoef) {
    this.Cv = pressureCoef;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new neqsim.process.util.monitor.FilterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(neqsim.process.util.report.ReportConfig cfg) {
    if (cfg != null && cfg
        .getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.FilterResponse res =
        new neqsim.process.util.monitor.FilterResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(res);
  }
}
