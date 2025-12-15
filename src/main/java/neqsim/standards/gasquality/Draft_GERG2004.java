package neqsim.standards.gasquality;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2004Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Draft_GERG2004 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Draft_GERG2004 extends neqsim.standards.Standard {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Draft_GERG2004.class);

  double specPressure = 70.0;
  double initTemperature = 273.15;

  /**
   * <p>
   * Constructor for Draft_GERG2004.
   * </p>
   *
   * @param thermoSystemMet a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Draft_GERG2004(SystemInterface thermoSystemMet) {
    super("Draft_GERG2004", "reference properties of natural gas");

    if (thermoSystemMet.getModelName().equals("GERG2004-EOS")) {
      this.thermoSystem = thermoSystemMet;
    } else {
      // System.out.println("setting model GERG2004 EOS...");
      this.thermoSystem =
          new SystemGERG2004Eos(thermoSystemMet.getTemperature(), thermoSystemMet.getPressure());
      for (int i = 0; i < thermoSystemMet.getPhase(0).getNumberOfComponents(); i++) {
        this.thermoSystem.addComponent(thermoSystemMet.getPhase(0).getComponent(i).getName(),
            thermoSystemMet.getPhase(0).getComponent(i).getNumberOfmoles());
      }
    }

    this.thermoSystem.setMixingRule(1);
    thermoSystem.init(0);
    thermoSystem.init(1);

    this.thermoOps = new ThermodynamicOperations(this.thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    try {
      this.thermoOps.TPflash();
      thermoSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    if (returnParameter.equals("dewPointTemperature")) {
      return 0.0;
    }
    if (returnParameter.equals("pressure")) {
      return this.thermoSystem.getPressure();
    } else {
      return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if (returnParameter.equals("dewPointTemperature")) {
      return "";
    }
    if (returnParameter.equals("pressureUnit")) {
      return "";
    } else {
      return "";
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] createTable(String name) {
    if (thermoSystem == null) {
      String[][] table = new String[0][6];
      return table;
    }
    // thermoSystem.setNumberOfPhases(1);
    thermoSystem.createTable(name);

    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    int rows = thermoSystem.getPhases()[0].getNumberOfComponents() + 30;
    String[][] table = new String[rows][6];

    // String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
    table[0][0] = ""; // getPhases()[0].getType(); //"";

    for (int i = 0; i < thermoSystem.getPhases()[0].getNumberOfComponents() + 30; i++) {
      for (int j = 0; j < 6; j++) {
        table[i][j] = "";
      }
    }
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      table[0][i + 1] = thermoSystem.getPhase(i).getType().toString();
    }

    StringBuffer buf = new StringBuffer();
    FieldPosition test = new FieldPosition(0);
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponent(j).getName();
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getComponent(j).getx(),
                buf, test).toString();
        table[j + 1][4] = "[-]";
      }

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "Compressibility Factor";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
          nf.format(thermoSystem.getPhase(i).getZ());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 4][0] =
          "Density";
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 4][i
          + 1] =
              nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getPhysicalProperties()
                  .getDensity(), buf, test).toString();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 4][4] =
          "[kg/m^3]";

      // Double.longValue(system.getPhase(phaseIndex[i]).getBeta());

      buf = new StringBuffer();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 5][0] =
          "PhaseFraction";
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 5][i
          + 1] =
              nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getBeta(), buf, test)
                  .toString();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 5][4] =
          "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 6][0] =
          "MolarMass";
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 6][i
          + 1] =
              nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getMolarMass() * 1000,
                  buf, test).toString();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 6][4] =
          "[kg/kmol]";

      buf = new StringBuffer();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 7][0] =
          "Cp";
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 7][i
          + 1] =
              nf.format((thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getCp()
                  / (thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfMolesInPhase()
                      * thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getMolarMass()
                      * 1000)),
                  buf, test).toString();
      table[thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getNumberOfComponents() + 7][4] =
          "[kJ/kg*K]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Pressure";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] =
          Double.toString(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getPressure());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[bar]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Temperature";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] =
          Double.toString(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getTemperature());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[K]";
      Double.toString(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getTemperature());
    }

    resultTable = table;
    return table;
  }
}
