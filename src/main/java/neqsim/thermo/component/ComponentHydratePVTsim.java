package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * ComponentHydratePVTsim class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentHydratePVTsim extends ComponentHydrate {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentHydratePVTsim.class);

  double[][] Ak = new double[2][2]; // [structure][cavitytype]
  double[][] Bk = new double[2][2]; // [structure][cavitytype]

  /**
   * <p>
   * Constructor for ComponentHydratePVTsim.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentHydratePVTsim(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    java.sql.ResultSet dataSet = null;

    if (!name.equals("default")) {
      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        logger.info("reading hydrate parameters ..............");
        try {
          if (NeqSimDataBase.createTemporaryTables()) {
            dataSet = database.getResultSet(("SELECT * FROM comptemp WHERE name='" + name + "'"));
          } else {
            dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          }
          dataSet.next();
        } catch (Exception ex) {
          dataSet.close();
          logger.info("no parameters in tempcomp -- trying comp.. " + name);
          dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          dataSet.next();
        }
        Ak[0][0] = Double.parseDouble(dataSet.getString("HydrateA1Small"));
        Bk[0][0] = Double.parseDouble(dataSet.getString("HydrateB1Small"));
        Ak[0][1] = Double.parseDouble(dataSet.getString("HydrateA1Large"));
        Bk[0][1] = Double.parseDouble(dataSet.getString("HydrateB1Large"));

        Ak[1][0] = Double.parseDouble(dataSet.getString("HydrateA2Small"));
        Bk[1][0] = Double.parseDouble(dataSet.getString("HydrateB2Small"));
        Ak[1][1] = Double.parseDouble(dataSet.getString("HydrateA2Large"));
        Bk[1][1] = Double.parseDouble(dataSet.getString("HydrateB2Large"));
      } catch (Exception ex) {
        logger.error("error in comp");
      } finally {
        try {
          dataSet.close();
        } catch (Exception ex2) {
          logger.error("error closing comp hydrate database....." + name);
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres) {
    double maxFug = 1.0e100;

    int stableStructure = 0;

    if (hydrateStructure == -1) {
      stableStructure = -1;
      refPhase.setTemperature(temp);
      refPhase.setPressure(pres);
      refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 3, PhaseType.byValue(0), 1.0);

      double refWaterFugacityCoef = Math.log(refPhase.getComponent("water").fugcoef(refPhase));

      double dhf = 6010.0;

      double tmi = 273.15;
      double dcp = 37.29;
      double LNFUG_ICEREF = refWaterFugacityCoef - (dhf / (R * tmi)) * (tmi / temp - 1.0)
          + (dcp / R) * ((tmi / temp) - 1.0 - Math.log(tmi / temp));

      double VM = 0.0;

      double K1 = 1.6070E-4;

      double K2 = 3.4619E-7;

      double K3 = -0.2637E-10;
      VM = 1E-6 * 19.6522 * (1 + K1 * (temp - tmi) + K2 * Math.pow((temp - tmi), 2)
          + K3 * Math.pow((temp - tmi), 3));

      double LNFUG_ICE = LNFUG_ICEREF + (VM * 100000 * (pres - 1.0) / (R * temp));
      fugacityCoefficient = Math.exp(LNFUG_ICE);
    } else {
      for (int structure = 0; structure < 2; structure++) {
        hydrateStructure = structure;

        if (componentName.equals("water")) {
          refPhase.setTemperature(temp);
          refPhase.setPressure(pres);
          refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 3, PhaseType.byValue(0), 1.0);

          double refWaterFugacity = refPhase.getComponent("water").fugcoef(refPhase) * pres;

          double alphaWater = reffug[getComponentNumber()];

          double wateralphaRef = Math.log(refWaterFugacity / alphaWater);
          logger.info("wateralphaRef " + wateralphaRef + " refFUgalpha " + alphaWater + " refFug "
              + refWaterFugacity);

          double val = 0.0;

          double tempy = 1.0;

          for (int cavType = 0; cavType < 2; cavType++) {
            tempy = 0.0;

            for (int j = 0; j < phase.getNumberOfComponents(); j++) {
              double tee = ((ComponentHydrate) phase.getComponent(j)).calcYKI(hydrateStructure,
                  cavType, phase);
              tempy += tee;
            }
            val += getCavprwat()[hydrateStructure][cavType] * Math.log(1.0 - tempy);
          }
          logger.info("val " + val + " structure " + hydrateStructure);
          logger
              .info("emty " + calcDeltaChemPot(phase, numberOfComps, temp, pres, hydrateStructure));
          // tempfugcoef = refWaterFugacity * Math.exp(val + calcDeltaChemPot(phase,
          // numberOfComps, temp, pres, hydrateStruct) + wateralphaRef) / (pres);
          fugacityCoefficient = alphaWater
              * Math.exp(val + calcDeltaChemPot(phase, numberOfComps, temp, pres, hydrateStructure)
                  + wateralphaRef)
              / pres;

          if (fugacityCoefficient < maxFug) {
            maxFug = fugacityCoefficient;

            stableStructure = hydrateStructure;
          }
        } else {
          fugacityCoefficient = 1e50;
        }
      }
      fugacityCoefficient = maxFug;
    }

    hydrateStructure = stableStructure;
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double calcYKI(int stucture, int cavityType, PhaseInterface phase) {
    if (componentName.equals("water")) {
      return 0.0;
    }
    double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber];

    double temp = 1.0;

    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (phase.getComponent(i).isHydrateFormer()) {
        temp += ((ComponentHydrate) phase.getComponent(i)).calcCKI(stucture, cavityType, phase)
            * reffug[i];
      }
      // System.out.println("reffug " + reffug[i]);
      // System.out.println("temp " + temp);
    }
    // System.out.println("YKI " + yki/temp);
    // System.out.println("fug " + yki/temp);
    return yki / temp;
    // }
    // else return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcCKI(int stucture, int cavityType, PhaseInterface phase) {
    // this is equation 8.8
    if (componentName.equals("water")) {
      return 0.0;
    }
    return Ak[stucture][cavityType] / (phase.getTemperature())
        * Math.exp(Bk[stucture][cavityType] / (phase.getTemperature()));
  }

  /**
   * <p>
   * calcDeltaChemPot.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComps a int
   * @param temp a double
   * @param pres a double
   * @param hydrateStruct a int
   * @return a double
   */
  public double calcDeltaChemPot(PhaseInterface phase, int numberOfComps, double temp, double pres,
      int hydrateStruct) {
    double dGf = 0.0;

    double dHf = 0.0;
    double Cp = 0;

    double deltaMolarVolume = 0.0;

    if (hydrateStruct == 0) {
      dGf = 1264.0;
      dHf = -4858.0;
      Cp = -39.16;
      deltaMolarVolume = 4.6e-6;
    } else {
      dGf = 883.0;
      dHf = -5201.0;
      Cp = -39.16;
      deltaMolarVolume = 5.0e-6;
    }
    double T0 = 273.15;

    // dHf -= Cp * (temp - T0);
    return dGf / R / T0 - (-1.0 * dHf * (1.0 / R / temp - 1.0 / R / T0)
        + Cp / R * Math.log(temp / T0) + Cp * T0 / R * (1.0 / temp - 1.0 / T0))
        + deltaMolarVolume / R / (temp) * (pres * 1e5);
  }
}
