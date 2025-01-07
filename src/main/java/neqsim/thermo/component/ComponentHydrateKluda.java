package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentHydrateKluda class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentHydrateKluda extends Component {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentHydrateKluda.class);

  double par1_struc1 = 17.44;
  double par2_struc1 = -6003.9;
  double par1_struc2 = 17.332;
  double par2_struc2 = -6017.6;
  int hydrateStructure = 0;
  double[][][] coordNumb = new double[3][2][2]; // [structure][cavitytype]
  double[][][] cavRadius = new double[3][2][2]; // [structure][cavitytype]
  double[][] cavNumb = new double[2][2]; // [structure][cavitytype]
  double[][] cavprwat = new double[2][2]; // [structure][cavitytype]
  double[] reffug = new double[20];

  /**
   * <p>
   * Constructor for ComponentHydrateKluda.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentHydrateKluda(String name, double moles, double molesInPhase, int compIndex) {
    super(name, moles, molesInPhase, compIndex);
    coordNumb[0][0][0] = 20.0;
    coordNumb[0][0][1] = 24.0;
    cavRadius[0][0][0] = 3.906;
    cavRadius[0][0][1] = 4.326;
    coordNumb[1][0][0] = 20.0;
    coordNumb[1][0][1] = 24.0;
    cavRadius[1][0][0] = 6.593;
    cavRadius[1][0][1] = 7.078;
    coordNumb[2][0][0] = 50.0;
    coordNumb[2][0][1] = 50.0;
    cavRadius[2][0][0] = 8.086;
    cavRadius[2][0][1] = 8.285;
    cavNumb[0][0] = 2.0;
    cavNumb[0][1] = 6.0;
    cavprwat[0][0] = 1.0 / 23.0;
    cavprwat[0][1] = 3.0 / 23.0;

    coordNumb[0][1][0] = 20.0;
    coordNumb[0][1][1] = 28.0;
    cavRadius[0][1][0] = 3.902;
    cavRadius[0][1][1] = 4.683;
    cavNumb[1][0] = 16.0;
    cavNumb[1][1] = 8.0;
    cavprwat[1][0] = 2.0 / 17.0;
    cavprwat[1][1] = 1.0 / 17.0;
  }

  /**
   * <p>
   * Calculate, set and return fugacity coefficient.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object to get fugacity coefficient
   *        of.
   * @param numberOfComps a int
   * @param temp a double
   * @param pres a double
   * @return Fugacity coefficient
   */
  public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres) {
    if (componentName.equals("water")) {
      double val = 1.0;
      double tempy = 1.0;
      double fugold = 0.0;
      do {
        val = 0.0;
        tempy = 0.0;
        fugold = fugacityCoefficient;
        for (int cavType = 0; cavType < 2; cavType++) {
          tempy = 0.0;
          for (int j = 0; j < phase.getNumberOfComponents(); j++) {
            // System.out.println(phase.getComponent(j));
            tempy += ((ComponentHydrateKluda) phase.getComponent(j)).calcYKI(hydrateStructure,
                cavType, phase);
            logger.info("tempny " + tempy);
            // System.out.println("temp ny " + this); //phase.getComponent(j));
          }
          val += cavprwat[hydrateStructure][cavType] * Math.log(1.0 - tempy);
        }
        logger.info("val " + (val));
        logger.info("fugacityCoefficient bef " + fugacityCoefficient);
        double solvol = 1.0 / 906.0 * getMolarMass();
        fugacityCoefficient = Math.exp(val)
            * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
            * Math.exp(solvol / (R * temp)
                * ((pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp))) * 1e5)
            / pres;
        // fugacityCoefficient = getAntoineVaporPressure(temp)/pres;
        // fugacityCoefficient = Math.exp(Math.log(fugacityCoefficient) + val*boltzmannConstant/R);
        logger.info("fugacityCoefficient " + fugacityCoefficient);
      } while (Math.abs((fugacityCoefficient - fugold) / fugold) > 1e-8);
    } else {
      fugacityCoefficient = 1e5;
    }

    // System.out.println("fug " + fugacityCoefficient);
    return fugacityCoefficient;
  }

  /**
   * <p>
   * dfugdt.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComps a int
   * @param temp a double
   * @param pres a double
   * @return a double
   */
  public double dfugdt(PhaseInterface phase, int numberOfComps, double temp, double pres) {
    if (componentName.equals("water")) {
      // double solvol =1.0 / getPureComponentSolidDensity(getMeltingPointTemperature()) *
      // molarMass;
      dfugdt = Math.log((getEmptyHydrateStructureVapourPressuredT(hydrateStructure, temp)) / pres);
    } else {
      dfugdt = 0;
    }
    return dfugdt;
  }

  /**
   * <p>
   * setStructure.
   * </p>
   *
   * @param structure a int
   */
  public void setStructure(int structure) {
    this.hydrateStructure = structure;
  }

  /**
   * <p>
   * getEmptyHydrateStructureVapourPressure.
   * </p>
   *
   * @param type a int
   * @param temperature a double
   * @return a double
   */
  public double getEmptyHydrateStructureVapourPressure(int type, double temperature) {
    double par1_struc1 = 4.6477;
    double par2_struc1 = -5242.979;
    double par3_struc1 = 2.7789;
    double par4_struc1 = -8.7156e-3;
    if (type == 0) {
      return Math.exp(par1_struc1 * Math.log(temperature) + par2_struc1 / temperature + par3_struc1
          + par4_struc1 * temperature) / 1.0e5;
    }
    if (type == 1) {
      return Math.exp(par1_struc2 + par2_struc2 / temperature)
          * ThermodynamicConstantsInterface.referencePressure;
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * getEmptyHydrateStructureVapourPressuredT.
   * </p>
   *
   * @param type a int
   * @param temperature a double
   * @return a double
   */
  public double getEmptyHydrateStructureVapourPressuredT(int type, double temperature) {
    if (type == 0) {
      return -par2_struc1 / (temperature * temperature)
          * Math.exp(par1_struc1 + par2_struc1 / temperature);
    }
    if (type == 1) {
      return -par2_struc2 / (temperature * temperature)
          * Math.exp(par1_struc2 + par2_struc2 / temperature);
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * calcYKI.
   * </p>
   *
   * @param stucture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double calcYKI(int stucture, int cavityType, PhaseInterface phase) {
    if (componentName.equals("methane")) {
      double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber];
      double temp = 1.0;
      for (int i = 0; i < phase.getNumberOfComponents(); i++) {
        if (phase.getComponent(i).isHydrateFormer()) {
          temp +=
              ((ComponentHydrateKluda) phase.getComponent(i)).calcCKI(stucture, cavityType, phase)
                  * reffug[i];
        }
      }
      return yki / temp;
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * calcCKI.
   * </p>
   *
   * @param stucture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double calcCKI(int stucture, int cavityType, PhaseInterface phase) {
    double cki = 4.0 * pi / (boltzmannConstant * phase.getTemperature())
        * (potIntegral(0, stucture, cavityType, phase));
    // +0*potIntegral(1,stucture, cavityType,phase)+0*potIntegral(2,stucture, cavityType,phase));
    // System.out.println("cki " + cki);
    return cki;
  }

  /**
   * <p>
   * setRefFug.
   * </p>
   *
   * @param compNumbm a int
   * @param val a double
   */
  public void setRefFug(int compNumbm, double val) {
    reffug[compNumbm] = val;
  }

  /**
   * <p>
   * potIntegral.
   * </p>
   *
   * @param intnumb a int
   * @param stucture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double potIntegral(int intnumb, int stucture, int cavityType, PhaseInterface phase) {
    double val = 0.0;
    double endval = cavRadius[intnumb][stucture][cavityType] - getSphericalCoreRadius();
    double x = 0.0;
    double step = endval / 100.0;
    x = step;
    for (int i = 1; i < 100; i++) {
      // System.out.println("x" +x);
      // System.out.println("pot " + getPot(x,stucture,cavityType,phase));
      val += step * ((getPot(intnumb, x, stucture, cavityType, phase)
          + 4 * getPot(intnumb, (x + 0.5 * step), stucture, cavityType, phase)
          + getPot(intnumb, x + step, stucture, cavityType, phase)) / 6.0);
      x = i * step;
    }
    return val / 100000.0;
  }

  /**
   * <p>
   * getPot.
   * </p>
   *
   * @param intnumb a int
   * @param radius a double
   * @param struccture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getPot(int intnumb, double radius, int struccture, int cavityType,
      PhaseInterface phase) {
    double lenjonsenergy = Math.sqrt(this.getLennardJonesEnergyParameter()
        * phase.getComponent("water").getLennardJonesEnergyParameter());
    double diam = (this.getLennardJonesMolecularDiameter()
        + phase.getComponent("water").getLennardJonesMolecularDiameter()) / 2.0;
    double corerad =
        (this.getSphericalCoreRadius() + phase.getComponent("water").getSphericalCoreRadius())
            / 2.0;

    double pot = 2.0 * coordNumb[intnumb][struccture][cavityType] * lenjonsenergy
        * ((Math.pow(diam, 12.0)
            / (Math.pow(cavRadius[intnumb][struccture][cavityType], 11.0) * radius)
            * (delt(intnumb, 10.0, radius, struccture, cavityType, phase)
                + corerad / cavRadius[intnumb][struccture][cavityType]
                    * delt(intnumb, 11.0, radius, struccture, cavityType, phase)))
            - (Math.pow(diam, 6.0)
                / (Math.pow(cavRadius[intnumb][struccture][cavityType], 5.0) * radius)
                * (delt(intnumb, 4.0, radius, struccture, cavityType, phase)
                    + corerad / cavRadius[intnumb][struccture][cavityType]
                        * delt(intnumb, 5.0, radius, struccture, cavityType, phase))));

    // intnumb++;
    // pot += 2.0*coordNumb[intnumb][struccture][cavityType]*lenjonsenergy*(
    // (Math.pow(diam,12.0)/(Math.pow(cavRadius[intnumb][struccture][cavityType],11.0)*
    // radius)*(delt(intnumb,10.0,radius,struccture,cavityType,phase) +
    // corerad/cavRadius[intnumb][struccture][cavityType]*delt(intnumb,11.0,radius,struccture,cavityType,phase)))
    // -
    // (Math.pow(diam,6.0)/(Math.pow(cavRadius[intnumb][struccture][cavityType],5.0)*
    // radius)*(delt(intnumb,
    // 4.0,radius,struccture,cavityType,phase) +
    // corerad/cavRadius[intnumb][struccture][cavityType]*delt(intnumb,5.0,radius,struccture,cavityType,phase)))
    // );

    // intnumb++;
    // pot += 2.0*coordNumb[intnumb][struccture][cavityType]*lenjonsenergy*(
    // (Math.pow(diam,12.0)/(Math.pow(cavRadius[intnumb][struccture][cavityType],11.0)*
    // radius)*(delt(intnumb,10.0,radius,struccture,cavityType,phase) +
    // corerad/cavRadius[intnumb][struccture][cavityType]*delt(intnumb,11.0,radius,struccture,cavityType,phase)))
    // -
    // (Math.pow(diam,6.0)/(Math.pow(cavRadius[intnumb][struccture][cavityType],5.0)*
    // radius)*(delt(intnumb,
    // 4.0,radius,struccture,cavityType,phase) +
    // corerad/cavRadius[intnumb][struccture][cavityType]*delt(intnumb,5.0,radius,struccture,cavityType,phase)))
    // );

    // System.out.println("lenjones " +this.getLennardJonesMolecularDiameter() );
    // System.out.println("pot bef " + pot);
    pot = Math.exp(-pot / (phase.getTemperature())) * radius * radius / 1.0e20;
    // System.out.println("pot " + pot);
    return pot;
  }

  /**
   * <p>
   * delt.
   * </p>
   *
   * @param intnumb a int
   * @param n a double
   * @param radius a double
   * @param struccture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double delt(int intnumb, double n, double radius, int struccture, int cavityType,
      PhaseInterface phase) {
    // double lenjonsenergy = Math.sqrt(this.getLennardJonesEnergyParameter() *
    // phase.getComponent("water").getLennardJonesEnergyParameter());
    // double diam = (this.getLennardJonesMolecularDiameter() +
    // phase.getComponent("water").getLennardJonesMolecularDiameter()) / 2.0;
    double corerad =
        (this.getSphericalCoreRadius() + phase.getComponent("water").getSphericalCoreRadius())
            / 2.0;

    double delt = 1.0 / n
        * (Math
            .pow(1.0 - radius / cavRadius[intnumb][struccture][cavityType]
                - corerad / cavRadius[intnumb][struccture][cavityType], -n)
            - Math.pow(1.0 + radius / cavRadius[intnumb][struccture][cavityType]
                - corerad / cavRadius[intnumb][struccture][cavityType], -n));

    // System.out.println("delt " + delt);
    return delt;
  }
}
