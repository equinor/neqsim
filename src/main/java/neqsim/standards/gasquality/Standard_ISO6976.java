package neqsim.standards.gasquality;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Standard_ISO6976 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Standard_ISO6976 extends neqsim.standards.Standard
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Standard_ISO6976.class);

  // metering conditions
  ArrayList<String> componentsNotDefinedByStandard = new ArrayList<String>();
  double volRefT = 0;
  double volRefP = ThermodynamicConstantsInterface.referencePressure;
  // ThermodynamicConstantsInterface.R
  double R = 8.314510;
  double molRefm3 = 0.0;
  // combustion conditions
  double energyRefT = 25;
  double energyRefP = ThermodynamicConstantsInterface.referencePressure;
  String referenceType = "volume"; // mass volume molar
  String energyUnit = "KJ/Nm3";
  double energy = 1.0;
  double Zmix0 = 1.0;
  double Zmix15 = 1.0;
  double Zmix20 = 1.0;
  double Zair0 = 0.99941;
  double Zair15 = 0.99958;
  double Zair20 = 0.99963;
  double averageCarbonNumber = 0.0;
  int[] carbonNumber;
  double[] M;
  double[] Z0;
  double[] Z15;
  double[] Z20;
  double[] bsqrt0;
  double[] bsqrt15;
  double[] bsqrt20;
  double[] Hsup0;
  double[] Hsup15;
  double[] Hsup20;
  double[] Hsup25;
  double[] Hsup60F;
  double[] Hinf0;
  double[] Hinf15;
  double[] Hinf20;
  double[] Hinf25;
  double[] Hinf60F;
  double Mmix = 0.0;
  double HsupIdeal0 = 0.0;
  double HsupIdeal15 = 0.0;
  double HsupIdeal20 = 0.0;
  double HsupIdeal25 = 0.0;
  double HsupIdeal60F = 0.0;
  double HinfIdeal0 = 0.0;
  double HinfIdeal15 = 0.0;
  double HinfIdeal20 = 0.0;
  double HinfIdeal25 = 0.0;
  double HinfIdeal60F = 0.0;
  double wobbeIdeal = 0.0;
  double wobbeReal = 0.0;
  double relDensIdeal = 0.0;
  double relDensReal = 0.0;
  double densIdeal = 0.0;
  double densReal = 0.0;

  /**
   * Constructor for Standard_ISO6976.
   *
   * @param thermoSystem SystemInterface to base object on
   */
  public Standard_ISO6976(SystemInterface thermoSystem) {
    this("Standard_ISO6976",
        "Calculation of calorific values, density, relative density and Wobbe index from composition",
        thermoSystem);
  }

  /**
   * Constructor for Standard_ISO6976.
   *
   * @param name Name of standard
   * @param description Description of standard
   * @param thermoSystem SystemInterface to base object on
   */
  public Standard_ISO6976(String name, String description, SystemInterface thermoSystem) {
    super(name, description, thermoSystem);
    M = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    carbonNumber = new int[thermoSystem.getPhase(0).getNumberOfComponents()];

    Z0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Z15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Z20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

    bsqrt0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    bsqrt15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    bsqrt20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

    Hsup0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hsup15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hsup20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hsup25 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hsup60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

    Hinf0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hinf15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hinf20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hinf25 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Hinf60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    try (neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase()) {
      java.sql.ResultSet dataSet = null;

      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        try {
          dataSet = database.getResultSet(("SELECT * FROM ISO6976constants WHERE ComponentName='"
              + this.thermoSystem.getPhase(0).getComponentName(i) + "'"));
          dataSet.next();
          dataSet.getString("ID");
        } catch (Exception ex) {
          try {
            String compName = "inert";
            String compType = this.thermoSystem.getPhase(0).getComponent(i).getComponentType();
            if (compType.equals("HC") || compType.equals("TBP") || compType.equals("plus")) {
              compName = "n-heptane";
            } else if (compType.equals("alcohol") || compType.equals("glycol")) {
              compName = "methanol";
            }

            dataSet.close();
            dataSet = database.getResultSet(
                ("SELECT * FROM iso6976constants WHERE ComponentName='" + compName + "'"));
            M[i] = this.thermoSystem.getPhase(0).getComponent(i).getMolarMass();
            dataSet.next();
          } catch (Exception ex2) {
            logger.error(ex2.getMessage());
          }
          componentsNotDefinedByStandard
              .add("this.thermoSystem.getPhase(0).getComponent(i).getComponentName()");
        }
        carbonNumber[i] = Integer.parseInt(dataSet.getString("numberOfCarbon"));
        M[i] = Double.parseDouble(dataSet.getString("MolarMass"));
        Z0[i] = Double.parseDouble(dataSet.getString("Z0"));
        Z15[i] = Double.parseDouble(dataSet.getString("Z15"));
        Z20[i] = Double.parseDouble(dataSet.getString("Z20"));

        bsqrt0[i] = Double.parseDouble(dataSet.getString("srtb0"));
        bsqrt15[i] = Double.parseDouble(dataSet.getString("srtb15"));
        bsqrt20[i] = Double.parseDouble(dataSet.getString("srtb20"));

        Hsup0[i] = Double.parseDouble(dataSet.getString("Hsupmolar0"));
        Hsup15[i] = Double.parseDouble(dataSet.getString("Hsupmolar15"));
        Hsup20[i] = Double.parseDouble(dataSet.getString("Hsupmolar20"));
        Hsup25[i] = Double.parseDouble(dataSet.getString("Hsupmolar25"));
        Hsup60F[i] = Double.parseDouble(dataSet.getString("Hsupmolar60F"));

        Hinf0[i] = Double.parseDouble(dataSet.getString("Hinfmolar0"));
        Hinf15[i] = Double.parseDouble(dataSet.getString("Hinfmolar15"));
        Hinf20[i] = Double.parseDouble(dataSet.getString("Hinfmolar20"));
        Hinf25[i] = Double.parseDouble(dataSet.getString("Hinfmolar25"));
        Hinf60F[i] = Double.parseDouble(dataSet.getString("Hinfmolar60F"));
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // logger.info("ok adding components in " + getName());
  }

  /**
   * <p>
   * Constructor for Standard_ISO6976.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param volumetricReferenceTemperaturedegC a double (valid are 0, 15, 15.55 and 20)
   * @param energyReferenceTemperaturedegC a double (valid are 0, 15, 15.55 and 20)
   * @param calculationType a {@link java.lang.String} object
   */
  public Standard_ISO6976(SystemInterface thermoSystem, double volumetricReferenceTemperaturedegC,
      double energyReferenceTemperaturedegC, String calculationType) {
    this(thermoSystem);
    this.referenceType = calculationType;
    volRefT = volumetricReferenceTemperaturedegC;
    energyRefT = energyReferenceTemperaturedegC;
  }

  /** {@inheritDoc} */
  @Override
  public void calculate() {
    Zmix0 = 1.0;
    Zmix15 = 1.0;
    Zmix20 = 1.0;
    double Zmixtemp0 = 0.0;
    double Zmixtemp15 = 0.0;
    double Zmixtemp20 = 0.0;
    Mmix = 0.0;
    relDensIdeal = 0.0;
    HsupIdeal0 = 0.0;
    HsupIdeal15 = 0.0;
    HsupIdeal20 = 0.0;
    HsupIdeal25 = 0.0;
    HsupIdeal60F = 0.0;
    HinfIdeal0 = 0.0;
    HinfIdeal15 = 0.0;
    HinfIdeal20 = 0.0;
    HinfIdeal25 = 0.0;
    HinfIdeal60F = 0.0;

    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      Mmix += thermoSystem.getPhase(0).getComponent(i).getz() * M[i];

      Zmixtemp0 += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt0[i];
      Zmixtemp15 += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt15[i];
      Zmixtemp20 += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt20[i];

      HsupIdeal0 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup0[i];
      HsupIdeal15 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup15[i];
      HsupIdeal20 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup20[i];
      HsupIdeal25 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup25[i];
      HsupIdeal60F += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup60F[i];

      HinfIdeal0 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf0[i];
      HinfIdeal15 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf15[i];
      HinfIdeal20 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf20[i];
      HinfIdeal25 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf25[i];
      HinfIdeal60F += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf60F[i];

      relDensIdeal += thermoSystem.getPhase(0).getComponent(i).getz() * M[i] / molarMassAir;
    }
    Zmix0 -= Math.pow(Zmixtemp0, 2.0);
    Zmix15 -= Math.pow(Zmixtemp15, 2.0);
    Zmix20 -= Math.pow(Zmixtemp20, 2.0);
    molRefm3 =
        volRefP * 1.0e5 * 1.0 / (R * (getVolRefT() + 273.15) * getValue("CompressionFactor"));
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter, String returnUnit) {
    checkReferenceCondition();

    if (returnParameter.equals("GCV")) {
      returnParameter = "SuperiorCalorificValue";
    }
    if (returnParameter.equals("LCV")) {
      returnParameter = "InferiorCalorificValue";
    }

    double returnValue = 0.0;

    if (getVolRefT() == 0) {
      returnValue = Zmix0;
    } else if (getVolRefT() == 15) {
      returnValue = Zmix15;
    } else if (getVolRefT() == 15.55) {
      returnValue = Zmix15;
    } else if (getVolRefT() == 20) {
      returnValue = Zmix20;
    } else {
      returnValue = Zmix15;
    }

    if (returnParameter.equals("CompressionFactor")) {
      return returnValue;
    }
    if (returnParameter.equals("MolarMass")) {
      return Mmix;
    }

    double realCorrection = 1.0;
    if (getReferenceState().equals("ideal")) {
      realCorrection = 1.0;
    } else {
      realCorrection = returnValue;
    }

    if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 0) {
      returnValue = HsupIdeal0;
    } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 15) {
      returnValue = HsupIdeal15;
    } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 20) {
      returnValue = HsupIdeal20;
    } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 25) {
      returnValue = HsupIdeal25;
    } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 15.55) {
      returnValue = HsupIdeal60F;
    } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 0) {
      returnValue = HinfIdeal0;
    } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 15) {
      returnValue = HinfIdeal15;
    } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 20) {
      returnValue = HinfIdeal20;
    } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 25) {
      returnValue = HinfIdeal25;
    } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 15.55) {
      returnValue = HinfIdeal60F;
    } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 0) {
      returnValue = HsupIdeal0;
    } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 15) {
      returnValue = HsupIdeal15;
    } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 20) {
      returnValue = HsupIdeal20;
    } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 25) {
      returnValue = HsupIdeal25;
    } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 15.55) {
      returnValue = HsupIdeal60F;
    } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 0) {
      returnValue = HinfIdeal0;
    } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 15) {
      returnValue = HinfIdeal15;
    } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 20) {
      returnValue = HinfIdeal20;
    } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 15.55) {
      returnValue = HinfIdeal60F;
    } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 25) {
      returnValue = HinfIdeal25;
    }
    if (returnUnit.equals("kWh")) {
      returnValue /= 3600.0;
    }

    double relativeDens = 0.0;
    // System.out.println("reference state " + getReferenceState());
    if (getReferenceState().equals("ideal")) {
      relativeDens = relDensIdeal;
    } else if (getVolRefT() == 0) {
      relativeDens = relDensIdeal * Zair0 / Zmix0;
    } else if (getVolRefT() == 15) {
      relativeDens = relDensIdeal * Zair15 / Zmix15;
    } else if (getVolRefT() == 15.55) {
      relativeDens = relDensIdeal * Zair15 / Zmix15;
    } else if (getVolRefT() == 20) {
      relativeDens = relDensIdeal * Zair20 / Zmix20;
    }
    if (returnParameter.equals("RelativeDensity")) {
      return relativeDens;
    }
    if (returnParameter.equals("InferiorWobbeIndex")
        || returnParameter.equals("SuperiorWobbeIndex")) {
      returnValue /= Math.sqrt(relativeDens);
    }
    if (returnParameter.equals("DensityIdeal")) {
      return volRefP * 1e5 / (R * (getVolRefT() + 273.15)) * Mmix / 1.0e3;
    }
    if (returnParameter.equals("DensityReal")) {
      return volRefP * 1e5 / (R * (getVolRefT() + 273.15)) * Mmix / 1.0e3 / realCorrection;
    }

    if (getReferenceType().equals("molar")) {
      return returnValue;
    } else if (getReferenceType().equals("mass")) {
      return returnValue / (Mmix / 1000.0);
    } else {
      return returnValue * volRefP * 1.0e5 / (R * (getVolRefT() + 273.15)) / realCorrection;
    }
  }

  /**
   * <p>
   * checkReferenceCondition.
   * </p>
   */
  public void checkReferenceCondition() {
    Double[] validvalues = {0.0, 15.0, 15.55, 20.0, 25.0};

    if (!java.util.Arrays.stream(validvalues).anyMatch(Double.valueOf(energyRefT)::equals)) {
      energyRefT = 25.0;
      logger.error("energy reference temperature not in valid range...setting it to 25C");
    }
    if (!java.util.Arrays.stream(validvalues).anyMatch(Double.valueOf(volRefT)::equals)) {
      volRefT = 15.0;
      logger.error("volume reference temperature not in valid range...setting it to 15C");
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String returnParameter) {
    return getValue(returnParameter, "");
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit(String returnParameter) {
    if (returnParameter.equals("CompressionFactor")) {
      return "-";
    } else {
      return energyUnit;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isOnSpec() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String[][] createTable(String name) {
    thermoSystem.setNumberOfPhases(1);

    thermoSystem.createTable(name);

    DecimalFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(5);
    nf.applyPattern("#.#####E0");

    int rows = 0;
    if (thermoSystem == null) {
      String[][] table = new String[0][6];
      return table;
    }

    rows = thermoSystem.getPhases()[0].getNumberOfComponents() + 30;
    String[][] table = new String[rows][6];

    // String[] names = { "", "Phase 1", "Phase 2", "Phase 3", "Unit" };
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

    String referenceTypeUnit = "";
    if (getReferenceType().equals("volume")) {
      referenceTypeUnit = "m^3";
    } else if (getReferenceType().equals("mass")) {
      referenceTypeUnit = "kg";
    } else if (getReferenceType().equals("molar")) {
      referenceTypeUnit = "mol";
    }
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
        table[j + 1][0] = thermoSystem.getPhases()[0].getComponentName(j);
        buf = new StringBuffer();
        table[j + 1][i + 1] =
            nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getComponent(j).getx(),
                buf, test).toString();
        table[j + 1][4] = "[-]";
      }

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "Compressibility Factor";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] =
          nf.format(getValue("CompressionFactor"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] =
          "Superior Calorific Value";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] =
          nf.format(getValue("SuperiorCalorificValue"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] =
          "[kJ/" + referenceTypeUnit + "]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] =
          "Inferior Calorific Value";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] =
          nf.format(getValue("InferiorCalorificValue"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] =
          "[kJ/" + referenceTypeUnit + "]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 6][0] = "Superior Wobbe Index";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 6][i + 1] =
          nf.format(getValue("SuperiorWobbeIndex") / 3600.0);
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 6][4] =
          "[kWh/" + referenceTypeUnit + "]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Superior Wobbe Index";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] =
          nf.format(getValue("SuperiorWobbeIndex"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] =
          "[kJ/" + referenceTypeUnit + "]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Inferior Wobbe Index";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] =
          nf.format(getValue("InferiorWobbeIndex"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] =
          "[kJ/" + referenceTypeUnit + "]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 9][0] = "Relative Density";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 9][i + 1] =
          nf.format(getValue("RelativeDensity"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 9][4] = "[-]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Molar Mass";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] =
          nf.format(getValue("MolarMass"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[gr/mol]";

      buf = new StringBuffer();
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Density";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] =
          nf.format(getValue("DensityReal"));
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[kg/m^3]";

      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] =
          "Reference Temperature Combustion";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] =
          Double.toString(getEnergyRefT());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "[C]";

      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 14][0] =
          "Reference Temperature Volume";
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 14][i + 1] =
          Double.toString(getVolRefT());
      table[thermoSystem.getPhases()[0].getNumberOfComponents() + 14][4] = "[C]";
    }

    resultTable = table;
    return table;
  }

  /**
   * <p>
   * Getter for the field <code>energyRefT</code>. // combustion conditions
   * </p>
   *
   * @return the energyRefT
   */
  public double getEnergyRefT() {
    return energyRefT;
  }

  /**
   * <p>
   * Setter for the field <code>energyRefT</code>.
   * </p>
   *
   * @param energyRefT the energyRefT to set
   */
  public void setEnergyRefT(double energyRefT) {
    this.energyRefT = energyRefT;
  }

  /**
   * <p>
   * Getter for the field <code>energyRefP</code>.
   * </p>
   *
   * @return the energyRefP
   */
  public double getEnergyRefP() {
    return energyRefP;
  }

  /**
   * <p>
   * Setter for the field <code>energyRefP</code>.
   * </p>
   *
   * @param energyRefP the energyRefP to set
   */
  public void setEnergyRefP(double energyRefP) {
    this.energyRefP = energyRefP;
  }

  /**
   * <p>
   * Getter for the field <code>volRefT</code>. metering conditions
   * </p>
   *
   * @return the volRefT
   */
  public double getVolRefT() {
    return volRefT;
  }

  /**
   * <p>
   * Setter for the field <code>volRefT</code>.
   * </p>
   *
   * @param volRefT the volRefT to set
   */
  public void setVolRefT(double volRefT) {
    this.volRefT = volRefT;
  }

  /**
   * <p>
   * Getter for the field <code>componentsNotDefinedByStandard</code>.
   * </p>
   *
   * @return the componentsNotDefinedByStandard
   */
  public ArrayList<String> getComponentsNotDefinedByStandard() {
    return componentsNotDefinedByStandard;
  }

  /**
   * <p>
   * getTotalMolesOfInerts.
   * </p>
   *
   * @return a double
   */
  public double getTotalMolesOfInerts() {
    double inerts = 0.0;
    for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
      if (carbonNumber[j] == 0) {
        inerts += thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles();
      }
    }

    return inerts;
  }

  /**
   * <p>
   * removeInertsButNitrogen.
   * </p>
   */
  public void removeInertsButNitrogen() {
    for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
      if (carbonNumber[j] == 0
          && !thermoSystem.getPhase(0).getComponentName(j).equals("nitrogen")) {
        thermoSystem.addComponent("nitrogen",
            thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles());
        thermoSystem.addComponent(thermoSystem.getPhase(0).getComponentName(j),
            -thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles() * 0.99999);
      }
    }
  }

  /**
   * <p>
   * Getter for the field <code>averageCarbonNumber</code>.
   * </p>
   *
   * @return the averageCarbonNumber
   */
  public double getAverageCarbonNumber() {
    double inerts = getTotalMolesOfInerts();
    averageCarbonNumber = 0;
    for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
      averageCarbonNumber +=
          carbonNumber[j] * thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles()
              / (thermoSystem.getTotalNumberOfMoles() - inerts);
    }
    System.out.println("average carbon number " + averageCarbonNumber);
    return averageCarbonNumber;
  }

  /**
   * <p>
   * Getter for the field <code>referenceType</code>.
   * </p>
   *
   * @return the referenceType
   */
  public String getReferenceType() {
    return referenceType;
  }

  /**
   * <p>
   * Setter for the field <code>referenceType</code>.
   * </p>
   *
   * @param referenceType the referenceType to set
   */
  public void setReferenceType(String referenceType) {
    this.referenceType = referenceType;
  }
}
