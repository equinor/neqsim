package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;

/**
 * <p>
 * ComponentHydrateGF class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentHydrateGF extends ComponentHydrate {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentHydrateGF.class);

  double[][] Ak = new double[2][2]; // [structure][cavitytype]
  double[][] Bk = new double[2][2]; // [structure][cavitytype]

  /**
   * <p>
   * Constructor for ComponentHydrateGF.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentHydrateGF(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);

    java.sql.ResultSet dataSet = null;
    if (!name.equals("default")) {
      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        // System.out.println("reading GF hydrate parameters ..............");
        try {
          dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          dataSet.next();
          dataSet.getString("ID");
        } catch (Exception ex) {
          logger.info("no parameters in tempcomp -- trying comp.. " + name);
          dataSet.close();
          dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          dataSet.next();
        }
        Ak[0][0] = Double.parseDouble(dataSet.getString("A1_SmallGF"));
        Bk[0][0] = Double.parseDouble(dataSet.getString("B1_SmallGF"));
        Ak[0][1] = Double.parseDouble(dataSet.getString("A1_LargeGF"));
        Bk[0][1] = Double.parseDouble(dataSet.getString("B1_LargeGF"));

        Ak[1][0] = Double.parseDouble(dataSet.getString("A2_SmallGF"));
        Bk[1][0] = Double.parseDouble(dataSet.getString("B2_SmallGF"));
        Ak[1][1] = Double.parseDouble(dataSet.getString("A2_LargeGF"));
        Bk[1][1] = Double.parseDouble(dataSet.getString("B2_LargeGF"));
        dataSet.close();
      } catch (Exception ex) {
        logger.error("error in comp");
      } finally {
        try {
          dataSet.close();
        } catch (Exception ex) {
          logger.error("error closing database.....");
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
      // fugacityCoefficient = 1.02 *
      // getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) *
      // Math.exp(getMolarVolumeHydrate(hydrateStructure, temp) / (R * temp) * (pres -
      // getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5) /
      // pres;

      refPhase.setTemperature(temp);
      refPhase.setPressure(pres);
      refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 3, PhaseType.LIQUID, 1.0);
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
          // this is the empty hydrate fugacity devited by pressure (why??)
          double solvol = getMolarVolumeHydrate(hydrateStructure, temp);

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
          // System.out.println("val " + Math.exp(val));
          /*
           * System.out.println("fugacity " +
           * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol / (R
           * * temp) * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) *
           * 1e5) / pres)); System.out.println("temperature " + temp);
           * System.out.println("pressure " + pres); System.out.println("vapor pressure " +
           * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)));
           * System.out.println("fugacity folas " +
           * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol / (R
           * * temp) * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) *
           * 1e5)));
           */
          // System.out.println("pointing "
          // +(Math.exp(solvol/(R*temp)*((pres-getEmptyHydrateStructureVapourPressure(hydrateStruct,temp))*1e5))));
          fugacityCoefficient = Math.exp(val)
              * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
              * Math.exp(solvol / (R * temp)
                  * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5)
              / pres;
          // System.out.println("fugcoef " + tempfugcoef + "structure " +
          // (hydrateStruct+1));

          // System.out.println("structure " + (hydrateStructure+1));
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

  /**
   * <p>
   * fugcoef2.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComps a int
   * @param temp a double
   * @param pres a double
   * @return a double
   */
  public double fugcoef2(PhaseInterface phase, int numberOfComps, double temp, double pres) {
    // this is empty hydrate latice fugacity coefficient equation 8.9
    if (componentName.equals("water")) {
      // this is the empty hydrate fugacity devited by pressure (why??)
      double solvol = getMolarVolumeHydrate(hydrateStructure, temp);
      if (hydrateStructure == -1) {
        fugacityCoefficient = getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
            * Math.exp(solvol / (R * temp)
                * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5)
            / pres;
      } else {
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
        // System.out.println("val " + Math.exp(val));
        /*
         * System.out.println("fugacity " +
         * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol / (R *
         * temp) * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5) /
         * pres)); System.out.println("temperature " + temp); System.out.println("pressure " +
         * pres); System.out.println("vapor pressure " +
         * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)));
         * System.out.println("fugacity folas " +
         * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol / (R *
         * temp) * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5)));
         */

        // System.out.println("pointing "
        // +(Math.exp(solvol/(R*temp)*((pres-getEmptyHydrateStructureVapourPressure(hydrateStruct,temp))*1e5))));
        fugacityCoefficient =
            Math.exp(val) * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
                * Math.exp(solvol / (R * temp)
                    * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5)
                / pres;
        // System.out.println("fugcoef " + tempfugcoef + "structure " +
        // (hydrateStruct+1));

        // System.out.println("structure " + (hydrateStructure+1));
      }
    } else {
      fugacityCoefficient = 1e50;
    }

    // System.out.println("reading fugacity coefficient calculation");
    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double calcYKI(int stucture, int cavityType, PhaseInterface phase) {
    if (componentName.equals("water")) {
      return 0.0;
    }
    double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber];
    // * 1.0e5;
    double temp = 1.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (phase.getComponent(i).isHydrateFormer()) {
        temp += ((ComponentHydrate) phase.getComponent(i)).calcCKI(stucture, cavityType, phase)
            * reffug[i];
      }

      // System.out.println("yk2 "+
      // ((ComponentHydrateBallard)phase.getComponent(i)).calcCKI(stucture,
      // cavityType, phase)*reffug[i]);
      // System.out.println("CYJI" +yki + " ref fug " +(1.0e5*reffug[i]));
    }

    return yki / temp;
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
}
