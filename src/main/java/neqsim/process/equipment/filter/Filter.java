package neqsim.process.equipment.filter;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Filter class.
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
   * Constructor for Filter.
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
   * Getter for the field <code>deltaP</code>.
   *
   * @return a double
   */
  public double getDeltaP() {
    return deltaP;
  }

  /**
   * Setter for the field <code>deltaP</code>.
   *
   * @param deltaP a double
   */
  public void setDeltaP(double deltaP) {
    this.deltaP = deltaP;
    this.outStream.setPressure(this.inStream.getPressure() - deltaP);
  }

  /**
   * Setter for the field <code>deltaP</code>.
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
   * getCvFactor.
   *
   * @return a double
   */
  public double getCvFactor() {
    return Cv;
  }

  /**
   * setCvFactor.
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
    if (cfg != null && cfg.getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.FilterResponse res = new neqsim.process.util.monitor.FilterResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }
}
