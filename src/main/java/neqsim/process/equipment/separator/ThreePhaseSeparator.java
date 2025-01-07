package neqsim.process.equipment.separator;

import java.util.UUID;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.SeparatorResponse;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ThreePhaseSeparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThreePhaseSeparator extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  StreamInterface waterOutStream = new Stream("waterOutStream", waterSystem);

  String specifiedStream = "feed";
  double gasInAqueous = 0.00;
  String gasInAqueousSpec = "mole";

  double gasInOil = 0.00;
  String gasInOilSpec = "mole";

  double oilInGas = 0.00;
  String oilInGasSpec = "mole";

  double oilInAqueous = 0.00;
  String oilInAqueousSpec = "mole";

  double aqueousInGas = 0.00;
  String aqueousInGasSpec = "mole";

  double aqueousInOil = 0.00;
  String aqueousInOilSpec = "mole";

  boolean useTempMultiPhaseCheck = false;

  private double lastEnthalpy;
  private double lastFlowRate;
  private double lastPressure;

  /**
   * Constructor for ThreePhaseSeparator.
   *
   * @param name name of separator
   */
  public ThreePhaseSeparator(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for ThreePhaseSeparator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ThreePhaseSeparator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * <p>
   * setEntrainment.
   * </p>
   *
   * @param val a double
   * @param specType a {@link java.lang.String} object
   * @param specifiedStream a {@link java.lang.String} object
   * @param phaseFrom a {@link java.lang.String} object
   * @param phaseTo a {@link java.lang.String} object
   */
  public void setEntrainment(double val, String specType, String specifiedStream, String phaseFrom,
      String phaseTo) {
    this.specifiedStream = specifiedStream;
    if (phaseFrom.equals("gas") && phaseTo.equals("aqueous")) {
      gasInAqueous = val;
      gasInAqueousSpec = specType;
    }
    if (phaseFrom.equals("gas") && phaseTo.equals("oil")) {
      gasInOil = val;
      gasInOilSpec = specType;
    }
    if (phaseFrom.equals("oil") && phaseTo.equals("aqueous")) {
      oilInAqueous = val;
      oilInAqueousSpec = specType;
    }
    if (phaseFrom.equals("oil") && phaseTo.equals("gas")) {
      oilInGas = val;
      oilInGasSpec = specType;
    }
    if (phaseFrom.equals("aqueous") && phaseTo.equals("gas")) {
      aqueousInGas = val;
      aqueousInGasSpec = specType;
    }
    if (phaseFrom.equals("aqueous") && phaseTo.equals("oil")) {
      aqueousInOil = val;
      aqueousInOilSpec = specType;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    super.setInletStream(inletStream);

    thermoSystem = inletStream.getThermoSystem().clone();
    waterSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    waterOutStream = new Stream("waterOutStream", waterSystem);
  }

  /**
   * <p>
   * Getter for the field <code>waterOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getWaterOutStream() {
    return waterOutStream;
  }

  /**
   * <p>
   * getOilOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOilOutStream() {
    return liquidOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    inletStreamMixer.run(id);
    double enthalpy = inletStreamMixer.getOutletStream().getFluid().getEnthalpy();
    double flow = inletStreamMixer.getOutletStream().getFlowRate("kg/hr");
    double pres = inletStreamMixer.getOutletStream().getPressure();
    if (Math.abs((lastEnthalpy - enthalpy) / enthalpy) < 1e-6
        && Math.abs((lastFlowRate - flow) / flow) < 1e-6
        && Math.abs((lastPressure - pres) / pres) < 1e-6) {
      return;
    }
    lastEnthalpy = inletStreamMixer.getOutletStream().getFluid().getEnthalpy();
    lastFlowRate = inletStreamMixer.getOutletStream().getFlowRate("kg/hr");
    lastPressure = inletStreamMixer.getOutletStream().getPressure();
    thermoSystem = inletStreamMixer.getOutletStream().getThermoSystem().clone();

    if (!thermoSystem.doMultiPhaseCheck()) {
      useTempMultiPhaseCheck = true;
      thermoSystem.setMultiPhaseCheck(true);
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();
    if (useTempMultiPhaseCheck) {
      thermoSystem.setMultiPhaseCheck(false);
    }
    // thermoSystem.display();
    thermoSystem.addPhaseFractionToPhase(gasInAqueous, gasInAqueousSpec, specifiedStream, "gas",
        "aqueous");
    thermoSystem.addPhaseFractionToPhase(gasInOil, gasInOilSpec, specifiedStream, "gas", "oil");
    thermoSystem.addPhaseFractionToPhase(oilInAqueous, oilInAqueousSpec, specifiedStream, "oil",
        "aqueous");
    thermoSystem.addPhaseFractionToPhase(oilInGas, oilInGasSpec, specifiedStream, "oil", "gas");
    thermoSystem.addPhaseFractionToPhase(aqueousInGas, aqueousInGasSpec, specifiedStream, "aqueous",
        "gas");
    thermoSystem.addPhaseFractionToPhase(aqueousInOil, aqueousInOilSpec, specifiedStream, "aqueous",
        "oil");
    // thermoSystem.init_x_y();
    // thermoSystem.display();
    // thermoSystem.init(3);
    // thermoSystem.setMultiPhaseCheck(false);

    // //gasSystem = thermoSystem.phaseToSystem(0);
    // //gasOutStream.setThermoSystem(gasSystem);
    if (thermoSystem.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(thermoSystem, "gas");
      gasOutStream.run(id);
    } else {
      gasOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
    }

    // quidSystem = thermoSystem.phaseToSystem(1);
    // liquidOutStream.setThermoSystem(liquidSystem);
    if (thermoSystem.hasPhaseType("oil")) {
      // thermoSystem.display();
      liquidOutStream.setThermoSystemFromPhase(thermoSystem, "oil");
      liquidOutStream.run(id);
      // thermoSystem.display();
    } else {
      liquidOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
    }

    // waterSystem = thermoSystem.phaseToSystem(2);
    // waterOutStream.setThermoSystem(waterSystem);
    if (thermoSystem.hasPhaseType("aqueous")) {
      waterOutStream.setThermoSystemFromPhase(thermoSystem, "aqueous");
      waterOutStream.run(id);
    } else {
      waterOutStream.setThermoSystem(thermoSystem.getEmptySystemClone());
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    thermoSystem.display("from here " + getName());
    // gasOutStream.getThermoSystem().initPhysicalProperties();
    // waterOutStream.getThermoSystem().initPhysicalProperties();
    // try {
    // System.out.println("Gas Volume Flow Out " +
    // gasOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*gasOutStream.getThermoSystem().getPhase(0).getMolarMass()/gasOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0
    // + " m^3/h");
    // } finally {
    // }
    // try {
    // waterOutStream.getThermoSystem().display();
    // waterOutStream.run();
    // System.out.println("Water/MEG Volume Flow Out " +
    // waterOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*waterOutStream.getThermoSystem().getPhase(0).getMolarMass()/waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0
    // + " m^3/h");
    // System.out.println("Density MEG " +
    // waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());
    // } finally {
    // }
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    double entrop = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      entrop += inletStreamMixer.getStream(i).getFluid().getEntropy(unit);
    }
    getWaterOutStream().getThermoSystem().init(3);
    getOilOutStream().getThermoSystem().init(3);
    getGasOutStream().getThermoSystem().init(3);

    return getWaterOutStream().getThermoSystem().getEntropy(unit)
        + getOilOutStream().getThermoSystem().getEntropy(unit)
        + getGasOutStream().getThermoSystem().getEntropy(unit) - entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    double entrop = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      entrop += inletStreamMixer.getStream(i).getFluid().getExergy(surroundingTemperature, unit);
    }
    getWaterOutStream().getThermoSystem().init(3);
    getOilOutStream().getThermoSystem().init(3);
    getGasOutStream().getThermoSystem().init(3);

    return getWaterOutStream().getThermoSystem().getExergy(surroundingTemperature, unit)
        + getOilOutStream().getThermoSystem().getEntropy(unit)
        + getGasOutStream().getThermoSystem().getExergy(surroundingTemperature, unit) - entrop;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new SeparatorResponse(this));
  }
}
