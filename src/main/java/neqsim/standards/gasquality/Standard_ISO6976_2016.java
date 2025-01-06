package neqsim.standards.gasquality;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * Standard_ISO6976_2016 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class Standard_ISO6976_2016 extends Standard_ISO6976 {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Standard_ISO6976_2016.class);

  // metering conditions

  // ThermodynamicConstantsInterface.R
  double R = 8.3144621;

  double Zmix0 = 1.0;
  double Zmix15 = 1.0;
  double Zmix20 = 1.0;
  double Zmix60F = 1.0;

  double Zair0 = 0.999419;
  double Zair15 = 0.999595;
  double Zair60F = 999601;
  double Zair20 = 0.999645;

  double[] Z0;
  double[] Z15;
  double[] Z20;
  double[] Z60F;

  double[] bsqrt0;
  double[] bsqrt15;
  double[] bsqrt20;
  double[] bsqrt60F;

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

  /**
   * <p>
   * Constructor for Standard_ISO6976_2016.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public Standard_ISO6976_2016(SystemInterface thermoSystem) {
    super("Standard_ISO6976_2016",
        "Calculation of calorific values, density, relative density and Wobbe index from composition based on ISO6976 version 2016",
        thermoSystem);
    M = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    carbonNumber = new int[thermoSystem.getPhase(0).getNumberOfComponents()];

    Z0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Z15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Z60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    Z20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

    bsqrt0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    bsqrt15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    bsqrt60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
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
          dataSet =
              database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='"
                  + this.thermoSystem.getPhase(0).getComponentName(i) + "'"));
          dataSet.next();
          M[i] = Double.parseDouble(dataSet.getString("MolarMass"));
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
                ("SELECT * FROM iso6976constants2016 WHERE ComponentName='" + compName + "'"));
            M[i] = this.thermoSystem.getPhase(0).getComponent(i).getMolarMass();
            dataSet.next();
          } catch (Exception er) {
            logger.error(er.getMessage());
          }
          componentsNotDefinedByStandard
              .add("this.thermoSystem.getPhase(0).getComponent(i).getComponentName()");
          logger.info("added component not specified by ISO6976constants2016 "
              + this.thermoSystem.getPhase(0).getComponent(i).getComponentName());
        }

        carbonNumber[i] = Integer.parseInt(dataSet.getString("numberOfCarbon"));

        Z0[i] = Double.parseDouble(dataSet.getString("Z0"));
        Z15[i] = Double.parseDouble(dataSet.getString("Z15"));
        Z60F[i] = Double.parseDouble(dataSet.getString("Z60F"));
        Z20[i] = Double.parseDouble(dataSet.getString("Z20"));

        bsqrt0[i] = Double.parseDouble(dataSet.getString("srtb0"));
        bsqrt15[i] = Double.parseDouble(dataSet.getString("srtb15"));
        bsqrt60F[i] = Double.parseDouble(dataSet.getString("srtb60F"));
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

        dataSet.close();
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * Constructor for Standard_ISO6976_2016.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   * @param volumetricReferenceTemperaturedegC a double (valid are 0, 15, 15.55 and 20)
   * @param energyReferenceTemperaturedegC a double (valid are 0, 15, 15.55 and 20)
   * @param calculationType a {@link java.lang.String} object
   */
  public Standard_ISO6976_2016(SystemInterface thermoSystem,
      double volumetricReferenceTemperaturedegC, double energyReferenceTemperaturedegC,
      String calculationType) {
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
    Zmix60F = 1.0;
    Zmix20 = 1.0;
    double Zmixtemp0 = 0.0;
    double Zmixtemp15 = 0.0;
    double Zmixtemp60F = 0.0;
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
      Zmixtemp60F += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt60F[i];
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
    Zmix60F -= Math.pow(Zmixtemp60F, 2.0);
    Zmix20 -= Math.pow(Zmixtemp20, 2.0);
    molRefm3 =
        volRefP * 1.0e5 * 1.0 / (R * (getVolRefT() + 273.15) * getValue("CompressionFactor"));
    // System.out.println("molRefm3 " + molRefm3);
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
      returnValue = Zmix60F;
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
    if (getReferenceState().equals("ideal")) {
      relativeDens = relDensIdeal;
    } else if (getVolRefT() == 0) {
      relativeDens = relDensIdeal * Zair0 / Zmix0;
    } else if (getVolRefT() == 15) {
      relativeDens = relDensIdeal * Zair15 / Zmix15;
    } else if (getVolRefT() == 15.55) {
      relativeDens = relDensIdeal * Zair60F / Zmix60F;
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
}
