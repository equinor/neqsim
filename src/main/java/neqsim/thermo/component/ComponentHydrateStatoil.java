package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentHydrateStatoil class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentHydrateStatoil extends ComponentHydrate {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentHydrateStatoil.class);

  double[][] coordNumb = new double[2][2];
  double[][] cavRadius = new double[2][2];
  double[][] cavNumb = new double[2][2];
  double[][] cavprwat = new double[2][2];

  /**
   * <p>
   * Constructor for ComponentHydrateStatoil.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentHydrateStatoil(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    coordNumb[0][0] = 20.0;
    coordNumb[0][1] = 24.0;
    cavRadius[0][0] = 3.95;
    cavRadius[0][1] = 4.33;
    cavNumb[0][0] = 2.0;
    cavNumb[0][1] = 6.0;
    cavprwat[0][0] = 1.0 / 23.0;
    cavprwat[0][1] = 3.0 / 23.0;

    coordNumb[1][0] = 20.0;
    coordNumb[1][1] = 28.0;
    cavRadius[1][0] = 3.91;
    cavRadius[1][1] = 4.73;
    cavNumb[1][0] = 16.0;
    cavNumb[1][1] = 8.0;
    cavprwat[1][0] = 2.0 / 17.0;
    cavprwat[1][1] = 1.0 / 17.0;
  }

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres) {
    if (componentName.equals("water")) {
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
          val += cavprwat[hydrateStructure][cavType] * Math.log(1.0 - tempy);
        }
        // System.out.println("val " + Math.exp(val));

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

    return fugacityCoefficient;
  }

  /** {@inheritDoc} */
  @Override
  public double calcYKI(int stucture, int cavityType, PhaseInterface phase) {
    if (componentName.equals("water")) {
      return 0.0;
    }
    double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber] * 1.0e5;
    double temp = 1.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (phase.getComponent(i).isHydrateFormer()) {
        temp += ((ComponentHydrate) phase.getComponent(i)).calcCKI(stucture, cavityType, phase)
            * 1.0e5 * reffug[i];
        // System.out.println("yk2 "+
        // ((ComponentHydrateBallard)phase.getComponent(i)).calcCKI(stucture,
        // cavityType, phase)*reffug[i]);
        // System.out.println("CYJI" +yki + " ref fug " +(1.0e5*reffug[i]));
      }
    }

    return yki / temp;
  }

  /** {@inheritDoc} */
  @Override
  public double calcCKI(int stucture, int cavityType, PhaseInterface phase) {
    if (componentName.equals("water")) {
      return 0.0;
    }
    double cki = 4.0 * pi / (phase.getTemperature() * R) * potIntegral(stucture, cavityType, phase)
        * avagadroNumber;
    // System.out.println("cki " + cki/1.0e30*1e5 + " " + componentName);
    return cki / 1.0e30;
  }

  /** {@inheritDoc} */
  @Override
  public double potIntegral(int stucture, int cavityType, PhaseInterface phase) {
    double val = 0.0;
    int numberOfSteps = 200;
    double endval = cavRadius[stucture][cavityType] - getSphericalCoreRadiusHydrate();
    double x = 0.0;
    double step = endval / numberOfSteps;
    x = step;
    for (int i = 1; i < numberOfSteps; i++) {
      val += step * ((getPot(x, stucture, cavityType, phase)
          + 4.0 * getPot((x + 0.5 * step), stucture, cavityType, phase)
          + getPot(x + step, stucture, cavityType, phase)) / 6.0);
      x = i * step;
      // System.out.println("step " + i + " " +
      // (step*getPot(x,stucture,cavityType,phase)));
    }
    // System.out.println("integral " + val);
    if (Double.isNaN(val)) {
      logger.error("val NaN ...");
    }
    if (Double.isInfinite(val)) {
      logger.error("val Infinite ...");
    }
    return val;
  }

  /** {@inheritDoc} */
  @Override
  public double getPot(double radius, int struccture, int cavityType, PhaseInterface phase) {
    double pot = 2.0 * coordNumb[struccture][cavityType]
        * this.getLennardJonesEnergyParameterHydrate()
        * ((Math.pow(this.getLennardJonesMolecularDiameterHydrate(), 12.0)
            / (Math.pow(cavRadius[struccture][cavityType], 11.0) * radius)
            * (delt(10.0, radius, struccture, cavityType) + getSphericalCoreRadiusHydrate()
                / cavRadius[struccture][cavityType] * delt(11.0, radius, struccture, cavityType)))
            - (Math.pow(this.getLennardJonesMolecularDiameterHydrate(), 6.0)
                / (Math.pow(cavRadius[struccture][cavityType], 5.0) * radius)
                * (delt(4.0, radius, struccture, cavityType)
                    + getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType]
                        * delt(5.0, radius, struccture, cavityType))));

    pot = Math.exp(-pot / (phase.getTemperature())) * radius * radius;
    // System.out.println("pot " + pot);
    if (Double.isNaN(pot)) {
      logger.error("pot NaN ...");
    }
    if (Double.isInfinite(pot)) {
      logger.error("pot Infinite ...");
    }
    // System.out.println("pot " +pot);
    return pot;
  }

  /**
   * <p>
   * delt.
   * </p>
   *
   * @param n a double
   * @param radius a double
   * @param struccture a int
   * @param cavityType a int
   * @return a double
   */
  public double delt(double n, double radius, int struccture, int cavityType) {
    double diff1 = (radius + getSphericalCoreRadiusHydrate()) / cavRadius[struccture][cavityType];
    double diff2 = (radius - getSphericalCoreRadiusHydrate()) / cavRadius[struccture][cavityType];
    double delt = 1.0 / n * (Math.pow(1.0 - diff1, -n) - Math.pow(1.0 + diff2, -n));
    // System.out.println("diff1 " + diff1);
    // System.out.println("diff2 " + diff2);
    // System.out.println("delt " + delt);
    if (Double.isNaN(delt)) {
      logger.error("delt NaN ...");
    }
    if (Double.isInfinite(delt)) {
      logger.error("delt Infinite ...");
    }
    return delt;
  }
}
