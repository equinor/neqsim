package neqsim.process.equipment.absorber;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.UUID;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SimpleTEGAbsorber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleTEGAbsorber extends SimpleAbsorber {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SimpleTEGAbsorber.class);

  protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
  protected double pressure = 0;
  protected int numberOfInputStreams = 0;
  protected StreamInterface mixedStream;
  protected StreamInterface gasInStream;
  protected StreamInterface solventInStream;
  private StreamInterface gasOutStream;
  private StreamInterface solventOutStream;
  protected StreamInterface outStream;
  private double kwater = 1e-4;
  int solventStreamNumber = 0;
  private boolean isSetWaterInDryGas = false;
  private double waterInDryGas = 30e-6;

  /**
   * <p>
   * Constructor for SimpleTEGAbsorber.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public SimpleTEGAbsorber(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addStream(StreamInterface newStream) {
    streams.add(newStream);
    if (numberOfInputStreams == 0) {
      mixedStream = streams.get(0).clone(this.getName() + " mixed stream");
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);
      mixedStream.getThermoSystem().init(3);
    }

    numberOfInputStreams++;
  }

  /**
   * <p>
   * addGasInStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addGasInStream(StreamInterface newStream) {
    // TODO: fail if gasInStream is not null?
    gasInStream = newStream;
    gasOutStream = newStream.clone();
    addStream(newStream);
  }

  /**
   * <p>
   * addSolventInStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addSolventInStream(StreamInterface newStream) {
    // TODO: fail if solventInStream is not null?
    solventInStream = newStream;
    solventOutStream = newStream.clone();
    addStream(newStream);
    solventStreamNumber = streams.size() - 1;
  }

  /**
   * <p>
   * replaceSolventInStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void replaceSolventInStream(StreamInterface newStream) {
    // TODO: fails if solventStreamNumber is 0, i.e. no solventinstream set?
    solventInStream = newStream;
    streams.set(solventStreamNumber, solventInStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pressure) {
    this.pressure = pressure;
  }

  /**
   * <p>
   * mixStream.
   * </p>
   */
  public void mixStream() {
    String compName = new String();

    for (int k = 1; k < streams.size(); k++) {
      for (int i = 0; i < streams.get(k).getThermoSystem().getPhases()[0]
          .getNumberOfComponents(); i++) {
        boolean gotComponent = false;
        String componentName =
            streams.get(k).getThermoSystem().getPhases()[0].getComponent(i).getName();
        // System.out.println("adding: " + componentName);

        double moles =
            streams.get(k).getThermoSystem().getPhases()[0].getComponent(i).getNumberOfmoles();
        // System.out.println("moles: " + moles + " " +
        // mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents());
        for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0]
            .getNumberOfComponents(); p++) {
          if (mixedStream.getThermoSystem().getPhases()[0].getComponent(p).getName()
              .equals(componentName)) {
            gotComponent = true;
            compName =
                streams.get(0).getThermoSystem().getPhases()[0].getComponent(p).getComponentName();
          }
        }

        if (gotComponent) {
          // System.out.println("adding moles starting....");
          mixedStream.getThermoSystem().addComponent(compName, moles);
          // mixedStream.getThermoSystem().init_x_y();
          // System.out.println("adding moles finished");
        } else {
          // System.out.println("ikke gaa hit");
          mixedStream.getThermoSystem().addComponent(compName, moles);
        }
      }
    }
    mixedStream.getThermoSystem().init_x_y();
    mixedStream.getThermoSystem().initBeta();
    mixedStream.getThermoSystem().init(2);
  }

  /**
   * <p>
   * guessTemperature.
   * </p>
   *
   * @return a double
   */
  public double guessTemperature() {
    double gtemp = 0;
    for (int k = 0; k < streams.size(); k++) {
      gtemp += streams.get(k).getThermoSystem().getTemperature()
          * streams.get(k).getThermoSystem().getNumberOfMoles()
          / mixedStream.getThermoSystem().getNumberOfMoles();
    }
    return gtemp;
  }

  /**
   * <p>
   * calcMixStreamEnthalpy.
   * </p>
   *
   * @return a double
   */
  public double calcMixStreamEnthalpy() {
    double enthalpy = 0;
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().init(3);
      enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      // System.out.println("total enthalpy k : " + (
      // ((StreamInterface) streams.get(k)).getThermoSystem()).getEnthalpy());
    }
    // System.out.println("total enthalpy of streams: " + enthalpy);
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutStream() {
    return mixedStream;
  }

  /**
   * <p>
   * getInStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getInStream() {
    return gasInStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>gasInStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGasInStream() {
    return gasInStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getLiquidOutStream() {
    return solventOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getSolventInStream() {
    return solventInStream;
  }

  /**
   * <p>
   * calcEa.
   * </p>
   *
   * @return a double
   */
  public double calcEa() {
    double A = mixedStream.getThermoSystem().getPhase(1).getNumberOfMolesInPhase()
        / mixedStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase() / kwater;
    absorptionEfficiency = (Math.pow(A, getNumberOfTheoreticalStages() + 1) - A)
        / (Math.pow(A, getNumberOfTheoreticalStages() + 1) - 1.0);
    return absorptionEfficiency;
  }

  /**
   * <p>
   * calcY0.
   * </p>
   *
   * @return a double
   */
  public double calcY0() {
    // double fugacityWaterLiquid =
    // mixedStream.getThermoSystem().getPhase(1).getFugacity("water");
    // double xrel =
    // mixedStream.getFluid().getPhase(0).getComponent("water").getx()/solventInStream.getFluid().getPhase(0).getComponent("water").getx();
    // double y0 =
    // xrel*fugacityWaterLiquid/(mixedStream.getFluid().getPhase(0).getComponent("water").getFugacityCoefficient()*mixedStream.getFluid().getPressure());
    double fugCoefRef =
        mixedStream.getThermoSystem().getPhase(1).getComponent("water").getFugacityCoefficient();
    double y0 = solventInStream.getFluid().getPhase(0).getComponent("water").getx() * fugCoefRef
        / (mixedStream.getThermoSystem().getPhase(0).getComponent("water")
            .getFugacityCoefficient());
    return y0;
  }

  /**
   * <p>
   * calcNumberOfTheoreticalStages.
   * </p>
   *
   * @return a double
   */
  public double calcNumberOfTheoreticalStages() {
    setNumberOfTheoreticalStages(getStageEfficiency() * getNumberOfStages());
    return getNumberOfTheoreticalStages();
  }

  /**
   * <p>
   * calcNTU.
   * </p>
   *
   * @param y0 a double
   * @param y1 a double
   * @param yb a double
   * @param ymix a double
   * @return a double
   */
  public double calcNTU(double y0, double y1, double yb, double ymix) {
    return Math.log((yb - ymix) / (y1 - y0));
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    try {
      double y1 = 0.0;
      // double yN = gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx();
      mixedStream.setThermoSystem((streams.get(0).getThermoSystem().clone()));
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);
      mixStream();
      // System.out.println("feed total number of water " +
      // mixedStream.getFluid().getPhase(0).getComponent("water").getNumberOfmoles());
      double enthalpy = calcMixStreamEnthalpy();
      // System.out.println("temp guess " + guessTemperature());
      mixedStream.getThermoSystem().setTemperature(guessTemperature());
      ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
      testOps.TPflash();
      testOps.PHflash(enthalpy, 0);

      kwater = mixedStream.getThermoSystem().getPhase(0).getComponent("water").getx()
          / mixedStream.getThermoSystem().getPhase(1).getComponent("water").getx();

      calcNumberOfTheoreticalStages();
      // System.out.println("number of theoretical stages " +
      // getNumberOfTheoreticalStages());
      double absorptionEffiency = calcEa();

      double y0 = calcY0();
      if (isSetWaterInDryGas) {
        y1 = waterInDryGas;
        setNumberOfTheoreticalStages(2.0);
      } else {
        y1 = gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx()
            - absorptionEffiency
                * (gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx() - y0);
      }

      double yMean = mixedStream.getThermoSystem().getPhase(0).getComponent("water").getx();
      double molesWaterToMove =
          (yMean - y1) * mixedStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase();
      // System.out.println("Lean TEG to absorber "
      // +solventInStream.getFlowRate("kg/hr"));

      // System.out.println("mole water to move " + molesWaterToMove);
      // System.out.println("total moles water in gas " +
      // mixedStream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfMolesInPhase());
      // System.out.println("total moles water " +
      // mixedStream.getThermoSystem().getPhase(0).getComponent("water").getNumberOfmoles());
      StreamInterface newMixedStream = mixedStream.clone();
      newMixedStream.getThermoSystem().addComponent("water", -molesWaterToMove, 0);
      newMixedStream.getThermoSystem().addComponent("water", molesWaterToMove, 1);
      newMixedStream.getThermoSystem().initBeta();
      newMixedStream.getThermoSystem().init_x_y();
      newMixedStream.getThermoSystem().init(2);
      mixedStream = newMixedStream;
      mixedStream.setCalculationIdentifier(id);

      // stream.getThermoSystem().display();

      SystemInterface tempSystem = mixedStream.getThermoSystem().clone();
      SystemInterface gasTemp = tempSystem.phaseToSystem(tempSystem.getPhases()[0]);
      gasTemp.init(2);
      gasOutStream.setThermoSystem(gasTemp);
      // System.out.println("gas total number of water " +
      // gasOutStream.getFluid().getPhase(0).getComponent("water").getNumberOfmoles());

      tempSystem = mixedStream.getThermoSystem().clone();
      SystemInterface liqTemp = tempSystem.phaseToSystem(tempSystem.getPhases()[1]);
      liqTemp.init(2);
      solventOutStream.setThermoSystem(liqTemp);
      // System.out.println("solvent total number of water " +
      // solventOutStream.getFluid().getPhase(0).getComponent("water").getNumberOfmoles());

      setNTU(calcNTU(y0, y1, gasInStream.getThermoSystem().getPhase(0).getComponent("water").getx(),
          yMean));
      // System.out.println("NTU " + getNTU());

      // double Ks = 0.055;
      getSolventOutStream().getThermoSystem().initPhysicalProperties();
      getSolventOutStream().setCalculationIdentifier(id);
      getGasOutStream().getThermoSystem().initPhysicalProperties();
      getGasOutStream().setCalculationIdentifier(id);

      // double vtemp = Ks * Math.sqrt((getSolventOutStream().getThermoSystem().getPhase(0)
      // .getPhysicalProperties().getDensity() -
      // getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties()
      // .getDensity()) /
      // getSolventOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());

      // double d = Math.sqrt(4.0 * getGasOutStream().getMolarRate() *
      // getGasOutStream().getThermoSystem().getPhase(0).getMolarMass() /
      // getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties()
      // .getDensity()/ 3.14 / vtemp);
      // System.out.println("diameter " + d);
      setCalculationIdentifier(id);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("rich TEG from absorber " +
    // getSolventOutStream().getFlowRate("kg/hr"));
  }

  /** {@inheritDoc} */
  @Override
  public double getGasLoadFactor() {
    double intArea = 3.14 * getInternalDiameter() * getInternalDiameter() / 4.0;
    double vs = getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea;
    return vs / Math.sqrt(
        (getSolventOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()
            - getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity())
            / getSolventOutStream().getThermoSystem().getPhase(0).getPhysicalProperties()
                .getDensity());
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    SystemInterface thermoSystem = mixedStream.getThermoSystem();
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    thermoSystem.initPhysicalProperties();
    String[][] table = new String[50][5];
    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = "";
    table[0][1] = "";
    table[0][2] = "";
    table[0][3] = "";
    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);

    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(thermoSystem.getPhases()[i].getComponent(j).getx(), buf, test).toString();
        table[j + 1][4] = "[-]";
      }
      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][0] = "Density";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][i + 1] =
          nf.format(thermoSystem.getPhases()[i].getPhysicalProperties().getDensity(), buf, test)
              .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 2][4] = "[kg/m^3]";

      // Double.longValue(thermoSystem.getPhases()[i].getBeta());

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "PhaseFraction";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
          nf.format(thermoSystem.getPhases()[i].getBeta(), buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] = "MolarMass";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] =
          nf.format(thermoSystem.getPhases()[i].getMolarMass() * 1000, buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] = "[kg/kmol]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Cp";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] =
          nf.format((thermoSystem.getPhases()[i].getCp()
              / (thermoSystem.getPhases()[i].getNumberOfMolesInPhase()
                  * thermoSystem.getPhases()[i].getMolarMass() * 1000)),
              buf, test).toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/kg*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Viscosity";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] =
          nf.format((thermoSystem.getPhases()[i].getPhysicalProperties().getViscosity()), buf, test)
              .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[kg/m*sec]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Conductivity";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] = nf
          .format(thermoSystem.getPhases()[i].getPhysicalProperties().getConductivity(), buf, test)
          .toString();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[W/m*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] =
          Double.toString(thermoSystem.getPhases()[i].getPressure());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] =
          Double.toString(thermoSystem.getPhases()[i].getTemperature());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
      Double.toString(thermoSystem.getPhases()[i].getTemperature());

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Stream";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = getName();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
    }

    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /**
   * <p>
   * Setter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @param gasOutStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public void setGasOutStream(StreamInterface gasOutStream) {
    this.gasOutStream = gasOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>solventOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getSolventOutStream() {
    return solventOutStream;
  }

  /**
   * <p>
   * Setter for the field <code>solventOutStream</code>.
   * </p>
   *
   * @param solventOutStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setSolventOutStream(StreamInterface solventOutStream) {
    this.solventOutStream = solventOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public void runConditionAnalysis(ProcessEquipmentInterface refTEGabsorberloc) {
    double yin = getGasInStream().getFluid().getPhase("gas").getComponent("water").getx();
    double yout = getGasOutStream().getFluid().getPhase("gas").getComponent("water").getx();
    double y0 = calcY0();
    double A = mixedStream.getThermoSystem().getPhase(1).getNumberOfMolesInPhase()
        / mixedStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase() / kwater;
    double N = Math.log(((A - 1.0) / A) * ((yin - y0) / (yout - y0)) + (1.0 / A)) / Math.log(A);
    setNumberOfTheoreticalStages(N);
  }

  /**
   * <p>
   * Setter for the field <code>waterInDryGas</code>.
   * </p>
   *
   * @param waterInDryGasInput water in dry gas
   */
  public void setWaterInDryGas(double waterInDryGasInput) {
    waterInDryGas = waterInDryGasInput;
    isSetWaterInDryGas = true;
  }

  /**
   * <p>
   * isSetWaterInDryGas.
   * </p>
   *
   * @param isSetwaterInDryGas a boolean
   */
  public void isSetWaterInDryGas(boolean isSetwaterInDryGas) {
    this.isSetWaterInDryGas = isSetwaterInDryGas;
  }

  /**
   * Calculates the Fs factor (gas capacity factor) for structured packing in the contactor.
   *
   * <p>
   * The Fs factor is defined as:
   * </p>
   *
   * <pre>
   * Fs = Vs * sqrt(rho_gas)
   * </pre>
   *
   * <p>
   * where Vs is the superficial gas velocity (m/s) and rho_gas is the gas density (kg/m3). The Fs
   * factor is proportional to the aerodynamic lift exerted by the gas on the liquid flowing down
   * the packing. Typical maximum design value for structured packing is 3.0 m/s*sqrt(kg/m3).
   * </p>
   *
   * @return Fs factor in m/s*sqrt(kg/m3), or 0 if streams are not initialized
   */
  @Override
  public double getFsFactor() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    if (intArea <= 0.0) {
      return 0.0;
    }
    double vs = getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea;
    double rhoGas =
        getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    return vs * Math.sqrt(rhoGas);
  }

  /**
   * Calculates the maximum allowable Fs factor for the contactor packing.
   *
   * <p>
   * For structured packing in glycol contactors, the maximum Fs factor is typically limited to 3.0
   * m/s*sqrt(kg/m3) to ensure sufficient hydraulic packing capacity and robustness for protection
   * of downstream equipment.
   * </p>
   *
   * @return maximum allowable Fs factor in m/s*sqrt(kg/m3)
   */
  public double getMaxAllowableFsFactor() {
    return 3.0;
  }

  /**
   * Checks whether the current Fs factor is within the design limit.
   *
   * @return true if Fs factor is within the maximum allowable limit
   */
  public boolean isFsFactorWithinDesignLimit() {
    return getFsFactor() <= getMaxAllowableFsFactor();
  }

  /**
   * Calculates the Fs factor utilization as a fraction of the maximum.
   *
   * @return utilization ratio (0.0-1.0+). Values above 1.0 indicate the design limit is exceeded.
   */
  public double getFsFactorUtilization() {
    double maxFs = getMaxAllowableFsFactor();
    if (maxFs <= 0.0) {
      return 0.0;
    }
    return getFsFactor() / maxFs;
  }

  /**
   * Calculates the minimum vessel internal diameter to meet the Fs factor limit at the current gas
   * flow rate.
   *
   * <p>
   * From Fs = Vs * sqrt(rho_gas) and Vs = Q / A, the minimum diameter is:
   * </p>
   *
   * <pre>
   * D_min = sqrt(4 * Q * sqrt(rho_gas) / (pi * Fs_max))
   * </pre>
   *
   * @return minimum internal diameter in metres, or 0 if streams are not initialized
   */
  public double getMinimumDiameterForFsLimit() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double gasFlowM3s = getGasOutStream().getThermoSystem().getFlowRate("m3/sec");
    double rhoGas =
        getGasOutStream().getThermoSystem().getPhase(0).getPhysicalProperties().getDensity();
    double maxFs = getMaxAllowableFsFactor();
    if (maxFs <= 0.0) {
      return 0.0;
    }
    return Math.sqrt(4.0 * gasFlowM3s * Math.sqrt(rhoGas) / (Math.PI * maxFs));
  }

  /**
   * Calculates the lean TEG equilibrium water dew point temperature at the contactor pressure.
   *
   * <p>
   * Returns the water dew point that the lean TEG can achieve in equilibrium. This is used to
   * verify that the lean TEG quality provides sufficient margin below the treated gas dew point
   * specification.
   * </p>
   *
   * @return equilibrium water dew point in Kelvin, or 0 if solvent stream is not available
   */
  public double getLeanTEGEquilibriumWaterDewPoint() {
    if (solventInStream == null || solventInStream.getThermoSystem() == null) {
      return 0.0;
    }
    try {
      SystemInterface tempSystem = solventInStream.getThermoSystem().clone();
      tempSystem.setTemperature(273.15 + 20.0);
      ThermodynamicOperations ops = new ThermodynamicOperations(tempSystem);
      ops.waterDewPointTemperatureFlash();
      return tempSystem.getTemperature();
    } catch (Exception ex) {
      logger.error("Failed to calculate lean TEG equilibrium water dew point", ex);
      return 0.0;
    }
  }

  /**
   * Checks if the lean TEG equilibrium water dew point is at least the specified margin below the
   * target dew point.
   *
   * <p>
   * Industry practice requires that the equilibrium water dew point of the lean TEG be at least 10
   * degC below the treated gas dew point specification.
   * </p>
   *
   * @param targetDewPointC treated gas water dew point specification in degrees Celsius
   * @param marginC required margin in degrees Celsius (typically 10)
   * @return true if the lean TEG equilibrium dew point has sufficient margin
   */
  public boolean hasAdequateTEGQualityMargin(double targetDewPointC, double marginC) {
    double eqDewPointK = getLeanTEGEquilibriumWaterDewPoint();
    if (eqDewPointK <= 0.0) {
      return false;
    }
    double eqDewPointC = eqDewPointK - 273.15;
    return eqDewPointC <= (targetDewPointC - marginC);
  }

  /**
   * Validates the TEG contactor design by checking Fs factor, gas load factor, and lean TEG
   * quality. Returns a summary string with all design checks.
   *
   * @return design validation summary string
   */
  public String validateContactorDesign() {
    StringBuilder sb = new StringBuilder();
    sb.append("TEG Contactor Design Validation\n");
    sb.append("================================\n");

    double fs = getFsFactor();
    double maxFs = getMaxAllowableFsFactor();
    sb.append(String.format("Fs factor: %.3f m/s*sqrt(kg/m3) (max: %.1f)\n", fs, maxFs));
    sb.append(String.format("Fs utilization: %.1f%%\n", getFsFactorUtilization() * 100.0));
    sb.append(
        String.format("Fs within limit: %s\n", isFsFactorWithinDesignLimit() ? "OK" : "EXCEEDED"));

    if (!isFsFactorWithinDesignLimit()) {
      sb.append(String.format("Minimum diameter needed: %.3f m\n", getMinimumDiameterForFsLimit()));
    }

    double gasLoadFactor = 0.0;
    if (getGasOutStream() != null && getGasOutStream().getThermoSystem() != null
        && getSolventOutStream() != null && getSolventOutStream().getThermoSystem() != null) {
      gasLoadFactor = getGasLoadFactor();
    }
    sb.append(String.format("Gas load factor (Ks): %.4f m/s\n", gasLoadFactor));

    sb.append(String.format("NTU: %.2f\n", getNTU()));
    sb.append(
        String.format("Number of theoretical stages: %.2f\n", getNumberOfTheoreticalStages()));

    return sb.toString();
  }
}
