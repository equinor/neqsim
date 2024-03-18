package neqsim.processSimulation.processEquipment.absorber;

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
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * WaterStripperColumn class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class WaterStripperColumn extends SimpleAbsorber {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(WaterStripperColumn.class);

  protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
  protected double pressure = 0;
  protected int numberOfInputStreams = 0;
  protected StreamInterface mixedStream;
  protected StreamInterface gasInStream;
  protected StreamInterface solventInStream;
  private StreamInterface gasOutStream;
  private StreamInterface solventOutStream;
  protected StreamInterface outStream;
  private double waterDewPointTemperature = 263.15;
  private double dewPressure = 70.0;

  private double kwater = 1e-4;

  int solventStreamNumber = 0;

  /**
   * <p>
   * Constructor for WaterStripperColumn.
   * </p>
   */
  @Deprecated
  public WaterStripperColumn() {}

  /**
   * <p>
   * Constructor for WaterStripperColumn.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public WaterStripperColumn(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public void addStream(StreamInterface newStream) {
    streams.add(newStream);
    if (numberOfInputStreams == 0) {
      mixedStream = streams.get(0).clone();
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
   * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void addGasInStream(StreamInterface newStream) {
    gasInStream = newStream;
    gasOutStream = newStream.clone();
    addStream(newStream);
  }

  /**
   * <p>
   * addSolventInStream.
   * </p>
   *
   * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void addSolventInStream(StreamInterface newStream) {
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
   * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void replaceSolventInStream(StreamInterface newStream) {
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
            streams.get(k).getThermoSystem().getPhases()[0].getComponents()[i].getName();
        // System.out.println("adding: " + componentName);

        double moles =
            streams.get(k).getThermoSystem().getPhases()[0].getComponents()[i].getNumberOfmoles();
        // System.out.println("moles: " + moles + " " +
        // mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents());
        for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0]
            .getNumberOfComponents(); p++) {
          if (mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName()
              .equals(componentName)) {
            gotComponent = true;

            compName = streams.get(0).getThermoSystem().getPhases()[0].getComponents()[p]
                .getComponentName();
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
   * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public StreamInterface getInStream() {
    return gasInStream;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getGasOutStream() {
    return gasOutStream;
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
   * calcX0.
   * </p>
   *
   * @return a double
   */
  public double calcX0() {
    return 0.0;
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
      double x2 = getSolventInStream().getFluid().getPhase(0).getComponent("water").getz();
      double x0 = 0.0;
      double absorptionEffiency = 0.0;
      mixedStream.setThermoSystem((streams.get(0).getThermoSystem().clone()));
      mixedStream.getThermoSystem().setNumberOfPhases(2);
      mixedStream.getThermoSystem().init(0);
      mixStream();
      double enthalpy = calcMixStreamEnthalpy();
      // System.out.println("temp guess " + guessTemperature());
      mixedStream.getThermoSystem().setTemperature(guessTemperature());
      ThermodynamicOperations testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
      testOps.TPflash();
      testOps.PHflash(enthalpy, 0);

      if (mixedStream.getThermoSystem().getNumberOfPhases() == 1) {
        if (mixedStream.getThermoSystem().getPhase(0).getType() == PhaseType.AQUEOUS) {
          SystemInterface tempSystem = mixedStream.getThermoSystem().clone();
          gasOutStream.setEmptyThermoSystem(tempSystem);
          gasOutStream.run(id);
          solventOutStream.setThermoSystem(tempSystem);
          solventOutStream.run(id);
        }
        if (mixedStream.getThermoSystem().getPhase(0).getType() == PhaseType.GAS) {
          SystemInterface tempSystem = mixedStream.getThermoSystem().clone();
          solventOutStream.setEmptyThermoSystem(tempSystem);
          solventOutStream.run(id);
          gasOutStream.setThermoSystem(tempSystem);
          gasOutStream.run(id);
        }
      } else {
        kwater = mixedStream.getThermoSystem().getPhase(0).getComponent("water").getx()
            / mixedStream.getThermoSystem().getPhase(1).getComponent("water").getx();

        double Ntheoretical = calcNumberOfTheoreticalStages();
        // System.out.println("number of theoretical stages " +
        // getNumberOfTheoreticalStages());
        absorptionEffiency = calcEa();

        x0 = calcX0();
        double revA = 1.0 / absorptionEffiency;

        double x1 = x2 - (Math.pow(revA, Ntheoretical + 1) - revA)
            / (Math.pow(revA, Ntheoretical + 1) - 1.0) * (x2 - x0);

        double xMean = mixedStream.getThermoSystem().getPhase(1).getComponent("water").getx();
        double molesWaterToMove =
            (xMean - x1) * mixedStream.getThermoSystem().getPhase(1).getNumberOfMolesInPhase();
        // System.out.println("mole water to move " + molesWaterToMove);

        StreamInterface stream = mixedStream.clone();
        stream.setName("test");
        stream.getThermoSystem().addComponent("water", molesWaterToMove, 0);
        stream.getThermoSystem().addComponent("water", -molesWaterToMove, 1);
        stream.getThermoSystem().initBeta();
        stream.getThermoSystem().init_x_y();
        stream.getThermoSystem().init(2);
        mixedStream = stream;
        // stream.getThermoSystem().display();

        SystemInterface tempSystem = mixedStream.getThermoSystem().clone();
        SystemInterface gasTemp = tempSystem.phaseToSystem(tempSystem.getPhases()[0]);
        gasTemp.init(2);
        gasOutStream.setThermoSystem(gasTemp);
        gasOutStream.setCalculationIdentifier(id);

        tempSystem = mixedStream.getThermoSystem().clone();
        SystemInterface liqTemp = tempSystem.phaseToSystem(tempSystem.getPhases()[1]);
        liqTemp.init(2);
        solventOutStream.setThermoSystem(liqTemp);
        solventOutStream.run(id);

        mixedStream.setCalculationIdentifier(id);

        // System.out.println("Gas from water stripper " +
        // gasOutStream.getFlowRate("kg/hr") + " kg/hr");

        // System.out.println("TEG from water stripper " +
        // solventOutStream.getFlowRate("kg/hr") + " kg/hr");
      }
      setCalculationIdentifier(id);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
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
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponents()[j].getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(thermoSystem.getPhases()[i].getComponents()[j].getx(), buf, test).toString();
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
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
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
   * Getter for the field <code>waterDewPointTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getWaterDewPointTemperature() {
    return waterDewPointTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>waterDewPointTemperature</code>.
   * </p>
   *
   * @param waterDewPointTemperature a double
   * @param dewPressure a double
   */
  public void setWaterDewPointTemperature(double waterDewPointTemperature, double dewPressure) {
    this.waterDewPointTemperature = waterDewPointTemperature;
    this.dewPressure = dewPressure;
  }

  /**
   * <p>
   * Setter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @param gasOutStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void setGasOutStream(StreamInterface gasOutStream) {
    this.gasOutStream = gasOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>solventOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public StreamInterface getSolventOutStream() {
    return solventOutStream;
  }

  /**
   * <p>
   * Setter for the field <code>solventOutStream</code>.
   * </p>
   *
   * @param solventOutStream a
   *        {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  public void setSolventOutStream(StreamInterface solventOutStream) {
    this.solventOutStream = solventOutStream;
  }
}
