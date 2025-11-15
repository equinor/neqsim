package neqsim.process.equipment.mixer;

import java.awt.Container;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.MixerResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Mixer class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Mixer extends ProcessEquipmentBaseClass implements MixerInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Mixer.class);

  protected ArrayList<StreamInterface> streams = new ArrayList<StreamInterface>(0);
  private int numberOfInputStreams = 0;
  protected StreamInterface mixedStream;
  private boolean isSetOutTemperature = false;
  private double outTemperature = Double.NaN;
  double lowestPressure = Double.NEGATIVE_INFINITY;

  /**
   * <p>
   * Constructor for Mixer.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Mixer(String name) {
    super(name);
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return mixedStream.getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public void replaceStream(int i, StreamInterface newStream) {
    streams.set(i, newStream);
  }

  /** {@inheritDoc} */
  @Override
  public void removeInputStream(int i) {
    streams.remove(i);
    numberOfInputStreams--;
  }

  /** {@inheritDoc} */
  @Override
  public void addStream(StreamInterface newStream) {
    streams.add(newStream);

    try {
      if (getNumberOfInputStreams() == 0) {
        mixedStream = streams.get(0).clone(this.getName() + " mixed stream");
        // mixedStream.getThermoSystem().setNumberOfPhases(2);
        // mixedStream.getThermoSystem().init(0);
        // mixedStream.getThermoSystem().init(3);
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    numberOfInputStreams++;
  }

  /**
   * <p>
   * getStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getStream(int i) {
    return streams.get(i);
  }

  /**
   * <p>
   * mixStream.
   * </p>
   */
  public void mixStream() {
    int index = 0;
    String compName = new String();
    lowestPressure = mixedStream.getThermoSystem().getPhase(0).getPressure();
    boolean hasAddedNewComponent = false;
    for (int k = 1; k < streams.size(); k++) {
      if (streams.get(k).getThermoSystem().getPhase(0).getPressure() < lowestPressure) {
        lowestPressure = streams.get(k).getThermoSystem().getPhase(0).getPressure();
      }
    }
    for (int k = 0; k < streams.size(); k++) {
      // streams.get(k).getThermoSystem().getPhase(0).setPressure(lowestPressure);
    }

    // Process ALL streams starting from k=1 (k=0 is already cloned into mixedStream)
    // but ensure first stream's components are also explicitly added if needed
    for (int k = 1; k < streams.size(); k++) {
      // Skip streams with negligible flow to avoid mixing in zero/negative moles
      if (streams.get(k).getFlowRate("kg/hr") <= getMinimumFlow()) {
        continue;
      }

      for (int i = 0; i < streams.get(k).getThermoSystem().getPhase(0)
          .getNumberOfComponents(); i++) {
        boolean gotComponent = false;
        String componentName =
            streams.get(k).getThermoSystem().getPhase(0).getComponent(i).getName();

        double moles =
            streams.get(k).getThermoSystem().getPhase(0).getComponent(i).getNumberOfmoles();

        for (int p = 0; p < mixedStream.getThermoSystem().getPhase(0)
            .getNumberOfComponents(); p++) {
          if (mixedStream.getThermoSystem().getPhase(0).getComponent(p).getName()
              .equals(componentName)) {
            gotComponent = true;
            index =
                streams.get(0).getThermoSystem().getPhase(0).getComponent(p).getComponentNumber();
            compName =
                streams.get(0).getThermoSystem().getPhase(0).getComponent(p).getComponentName();
          }
        }

        if (gotComponent) {
          mixedStream.getThermoSystem().addComponent(index, moles);
        } else {
          hasAddedNewComponent = true;
          mixedStream.getThermoSystem().addComponent(compName, moles);
        }
      }
    }
    if (hasAddedNewComponent) {
      mixedStream.getThermoSystem().setMixingRule(mixedStream.getThermoSystem().getMixingRule());
    }
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
      if (streams.get(k).getFlowRate("kg/hr") > getMinimumFlow()) {
        streams.get(k).getThermoSystem().init(3);
        enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
      }
    }
    return enthalpy;
  }

  /** {@inheritDoc} */
  @Override
  public StreamInterface getOutletStream() {
    return mixedStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    double enthalpy = 0.0;
    // ((Stream) streams.get(0)).getThermoSystem().display();

    // Check if all streams have zero/negligible flow
    boolean hasFlow = false;
    for (int k = 0; k < streams.size(); k++) {
      if (streams.get(k).getFlowRate("kg/hr") > getMinimumFlow()) {
        hasFlow = true;
        break;
      }
    }

    if (!hasFlow) {
      // All streams have zero flow - set mixer inactive and use first stream as template
      SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
      // Set all component moles to zero to reflect no flow
      for (int i = 0; i < thermoSystem2.getPhase(0).getNumberOfComponents(); i++) {
        thermoSystem2.getPhase(0).getComponent(i).setNumberOfmoles(0.0);
      }
      mixedStream.setThermoSystem(thermoSystem2);
      isActive(false);
      setCalculationIdentifier(id);
      return;
    }

    SystemInterface thermoSystem2 = streams.get(0).getThermoSystem().clone();
    isActive(true);
    // System.out.println("total number of moles " +
    // thermoSystem2.getTotalNumberOfMoles());
    mixedStream.setThermoSystem(thermoSystem2);
    // thermoSystem2.display();
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem2);
    if (streams.size() >= 2) {
      mixStream();
      if (mixedStream.getFlowRate("kg/hr") > getMinimumFlow()) {
        mixedStream.setPressure(lowestPressure);
        enthalpy = calcMixStreamEnthalpy();
        // System.out.println("temp guess " + guessTemperature());
        if (isSetOutTemperature) {
          mixedStream.setTemperature(outTemperature, "K");
        } else {
          mixedStream.getThermoSystem().setTemperature(guessTemperature());
        }
        // System.out.println("filan temp " + mixedStream.getTemperature());
        if (mixedStream.getFluid().getClass().getName()
            .equals("neqsim.thermo.system.SystemSoreideWhitson")) {
          ((SystemSoreideWhitson) mixedStream.getFluid()).setSalinity(getMixedSalinity(),
              "mole/sec");
        }

        mixedStream.run();

        if (isSetOutTemperature) {
          if (!Double.isNaN(getOutTemperature())) {
            mixedStream.getThermoSystem().setTemperature(getOutTemperature());
          }
          testOps.TPflash();
          mixedStream.getThermoSystem().init(2);
        } else {
          try {
            testOps.PHflash(enthalpy, 0);
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            if (!Double.isNaN(guessTemperature())) {
              mixedStream.getThermoSystem().setTemperature(guessTemperature());
              testOps.TPflash();
            }
          }
        }
      } else {
        isActive(false);
      }
    } else

    {
      if (mixedStream.getFlowRate("kg/hr") > getMinimumFlow()) {
        // testOps.TPflash();
        mixedStream.getThermoSystem().init(2);
      } else {
        mixedStream.getThermoSystem().initProperties();
        isActive(false);
      }
    }

    // System.out.println("enthalpy: " +
    // mixedStream.getThermoSystem().getEnthalpy())
    // System.out.println("enthalpy: " + en
    // System.out.println("temperature: " +

    // System.out.println("beta " + mixedStream.getThermoSystem(
    // outStream.setThermoSystem(mixedStream.getThermoSystem());
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    JDialog dialog = new JDialog(new JFrame(), "Results from TPflash");
    Container dialogContentPane = dialog.getContentPane();
    dialogContentPane.setLayout(new FlowLayout());

    SystemInterface thermoSystem = mixedStream.getThermoSystem();
    thermoSystem.initPhysicalProperties();
    String[][] table = new String[50][5];
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
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = name;
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "-";
    }

    String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    JTable Jtab = new JTable(table, names);
    JScrollPane scrollpane = new JScrollPane(Jtab);
    dialogContentPane.add(scrollpane);
    dialog.pack();
    dialog.setVisible(true);
  }

  /** {@inheritDoc} */
  @Override
  public void setPressure(double pres) {
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().setPressure(pres);
    }
    mixedStream.getThermoSystem().setPressure(pres);
  }

  /** {@inheritDoc} */
  @Override
  public void setTemperature(double temp) {
    for (int k = 0; k < streams.size(); k++) {
      streams.get(k).getThermoSystem().setTemperature(temp);
    }
    mixedStream.getThermoSystem().setTemperature(temp);
  }

  /**
   * <p>
   * Getter for the field <code>outTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getOutTemperature() {
    return outTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>outTemperature</code>.
   * </p>
   *
   * @param outTemperature a double
   */
  public void setOutTemperature(double outTemperature) {
    isSetOutTemperature(true);
    this.outTemperature = outTemperature;
  }

  /**
   * <p>
   * isSetOutTemperature.
   * </p>
   *
   * @return a boolean
   */
  public boolean isSetOutTemperature() {
    return isSetOutTemperature;
  }

  /**
   * <p>
   * isSetOutTemperature.
   * </p>
   *
   * @param isSetOutTemperature a boolean
   */
  public void isSetOutTemperature(boolean isSetOutTemperature) {
    this.isSetOutTemperature = isSetOutTemperature;
  }

  /**
   * <p>
   * Getter for the field <code>numberOfInputStreams</code>.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfInputStreams() {
    return numberOfInputStreams;
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    getOutletStream().run();
    double entrop = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      getStream(i).getFluid().init(3);
      entrop += getStream(i).getFluid().getEntropy(unit);
    }
    getOutletStream().getThermoSystem().init(3);
    return getOutletStream().getThermoSystem().getEntropy(unit) - entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    // Only count streams that have significant flow (were actually mixed)
    // to match the logic in mixStream() which skips negligible flows
    for (int i = 0; i < numberOfInputStreams; i++) {
      double streamFlow = getStream(i).getFluid().getFlowRate(unit);
      if (streamFlow > getMinimumFlow()) {
        inletFlow += streamFlow;
      }
    }
    return getOutletStream().getThermoSystem().getFlowRate(unit) - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(isSetOutTemperature, mixedStream, numberOfInputStreams,
        outTemperature, streams);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Mixer other = (Mixer) obj;
    return isSetOutTemperature == other.isSetOutTemperature
        && Objects.equals(mixedStream, other.mixedStream)
        && numberOfInputStreams == other.numberOfInputStreams
        && Double.doubleToLongBits(outTemperature) == Double.doubleToLongBits(other.outTemperature)
        && Objects.equals(streams, other.streams);
  }

  /**
   * Calculates the flow-weighted average salinity of the mixed stream. Assumes each input stream
   * provides getSalinity() and getFlowRate("kg/hr").
   *
   * @return mixed salinity (same unit as getSalinity() returns)
   */
  public double getMixedSalinity() {
    double totalSalinity = 0.0;

    for (StreamInterface stream : streams) {
      // Assumes getSalinity() exists in StreamInterface
      double salinity = 0.0;
      try {
        salinity = ((SystemSoreideWhitson) stream.getFluid()).getSalinity();
      } catch (Exception e) {
        logger.warn("Error mixing salinity for stream: " + stream.getName(), e);
      }
      totalSalinity += salinity;
    }
    return totalSalinity;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().create().toJson(new MixerResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    MixerResponse res = new MixerResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }
}
