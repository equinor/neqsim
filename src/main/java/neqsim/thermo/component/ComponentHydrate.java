package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * ComponentHydrate class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentHydrate extends Component {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentHydrate.class);

  // double[][] emptyHydrateVapourPressureConstant = {{17.6025820786,
  // -6056.0650578668},{17.332, -6017.6}}; //fitted
  // Sloan (1990)
  double[][] emptyHydrateVapourPressureConstant = {{17.44, -6003.9}, {17.332, -6017.6}};
  // double[][] emptyHydrateVapourPressureConstant = {{ 17.5061457754,
  // -6030.6886435166},{17.332, -6017.6}}; //fitted (1990)
  int hydrateStructure = 0;
  double[][] coordNumb = new double[2][2]; // [structure][cavitytype]
  double[][] cavRadius = new double[2][2]; // [structure][cavitytype]
  double[][] cavNumb = new double[2][2]; // [structure][cavitytype]
  double[][] cavprwat = new double[2][2]; // [structure][cavitytype]
  // double[] dGfHydrate = {-236539.2, -235614.0};
  // double[] dHfHydrate = {-292714.5, -292016.0};
  double[] dGfHydrate = {-235557, -235614};
  double[] dHfHydrate = {-291786, -292016};
  double[] reffug = new double[neqsim.thermo.ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS];
  private double sphericalCoreRadiusHydrate = 0.0;
  private double lennardJonesEnergyParameterHydrate = 0.0;
  private double lennardJonesMolecularDiameterHydrate = 0.0;
  PhaseInterface refPhase = null;

  /**
   * <p>
   * Constructor for ComponentHydrate.
   * </p>
   *
   * @param name Name of component.
   * @param moles Total number of moles of component.
   * @param molesInPhase Number of moles in phase.
   * @param compIndex Index number of component in phase object component array.
   */
  public ComponentHydrate(String name, double moles, double molesInPhase, int compIndex) {
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

    reffug[0] = 10.0;
    reffug[1] = 1.0;

    if (!name.equals("default")) {
      java.sql.ResultSet dataSet = null;
      try (neqsim.util.database.NeqSimDataBase database =
          new neqsim.util.database.NeqSimDataBase()) {
        try {
          if (NeqSimDataBase.createTemporaryTables()) {
            dataSet = database.getResultSet(("SELECT * FROM comptemp WHERE name='" + name + "'"));
          } else {
            dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          }
          dataSet.next();
          dataSet.getString("FORMULA");
        } catch (Exception ex) {
          dataSet.close();
          logger.info("no parameters in tempcomp -- trying comp.. " + name);
          dataSet = database.getResultSet(("SELECT * FROM comp WHERE name='" + name + "'"));
          dataSet.next();
        }
        lennardJonesMolecularDiameterHydrate =
            Double.parseDouble(dataSet.getString("LJdiameterHYDRATE")); // BF
        lennardJonesEnergyParameterHydrate = Double.parseDouble(dataSet.getString("LJepsHYDRATE"));
        sphericalCoreRadiusHydrate =
            Double.parseDouble(dataSet.getString("SphericalCoreRadiusHYDRATE"));
      } catch (Exception ex) {
        logger.error("error in comp");
      } finally {
        try {
          if (dataSet != null) {
            dataSet.close();
          }
        } catch (Exception ex) {
          logger.error("error closing database.....", ex);
        }
      }
    }
  }

  /**
   * <p>
   * readHydrateParameters.
   * </p>
   */
  public void readHydrateParameters() {}

  /** {@inheritDoc} */
  @Override
  public double fugcoef(PhaseInterface phase) {
    return fugcoef(phase, phase.getNumberOfComponents(), phase.getTemperature(),
        phase.getPressure());
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
      fugacityCoefficient = -1e50;
      double val = 1.0;
      double tempy = 1.0;
      double fugold = 0.0;

      do {
        val = 0;
        tempy = 0.0;
        fugold = fugacityCoefficient;
        if (hydrateStructure >= 0) {
          for (int cavType = 0; cavType < 2; cavType++) {
            tempy = 0.0;
            for (int j = 0; j < phase.getNumberOfComponents(); j++) {
              // System.out.println(phase.getComponent(j));
              tempy += ((ComponentHydrate) phase.getComponent(j)).calcYKI(hydrateStructure, cavType,
                  phase);
              // System.out.println("tempny " +tempy);
              // System.out.println("temp ny " + this); //phase.getComponent(j));
            }
            val += getCavprwat()[hydrateStructure][cavType] * Math.log(1.0 - tempy);
          }
        }
        // System.out.println("val " +(val));
        // System.out.println("fugacityCoefficient bef " + fugacityCoefficient);
        double solvol = 1.0 / 906.0 * getMolarMass();
        fugacityCoefficient = Math.exp(val)
            * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
            * Math.exp(solvol / (R * temp)
                * ((pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp))) * 1e5)
            / pres;
        // System.out.println("struct " + hydrateStruct + " fug " + tempfugcoef + " val
        // "+ val);

        // fugacityCoefficient =
        // Math.exp(val) *
        // getEmptyHydrateStructureVapourPressure(hydrateStructure,temp) *
        // Math.exp(solvol/(R*temp)*((pres-getEmptyHydrateStructureVapourPressure(hydrateStructure,temp)))*1e5)/pres;
        // fugacityCoefficient = getAntoineVaporPressure(temp)/pres;
        // fugacityCoefficient = Math.exp(Math.log(fugacityCoefficient) + val*boltzmannConstant/R);
        // System.out.println("fugacityCoefficient " + fugacityCoefficient);
      } while (Math.abs((fugacityCoefficient - fugold) / fugold) > 1e-6);
    } else {
      fugacityCoefficient = 1e5;
    }

    // System.out.println("fug " + fugacityCoefficient);
    return fugacityCoefficient;
  }

  /**
   * <p>
   * Setter for the field <code>hydrateStructure</code>.
   * </p>
   *
   * @param structure a int
   */
  public void setHydrateStructure(int structure) {
    this.hydrateStructure = structure;
  }

  /**
   * <p>
   * Getter for the field <code>hydrateStructure</code>.
   * </p>
   *
   * @return a int
   */
  public int getHydrateStructure() {
    return this.hydrateStructure;
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
    if (type == -1) {
      return getSolidVaporPressure(temperature);
    } else {
      return Math
          .exp(getEmptyHydrateVapourPressureConstant(type, 0)
              + getEmptyHydrateVapourPressureConstant(type, 1) / temperature)
          * ThermodynamicConstantsInterface.referencePressure;
    }
  }

  /**
   * <p>
   * Setter for the field <code>emptyHydrateVapourPressureConstant</code>.
   * </p>
   *
   * @param hydrateStructure a int
   * @param parameterNumber a int
   * @param value a double
   */
  public void setEmptyHydrateVapourPressureConstant(int hydrateStructure, int parameterNumber,
      double value) {
    emptyHydrateVapourPressureConstant[hydrateStructure][parameterNumber] = value;
  }

  /**
   * <p>
   * Getter for the field <code>emptyHydrateVapourPressureConstant</code>.
   * </p>
   *
   * @param hydrateStructure a int
   * @param parameterNumber a int
   * @return a double
   */
  public double getEmptyHydrateVapourPressureConstant(int hydrateStructure, int parameterNumber) {
    return emptyHydrateVapourPressureConstant[hydrateStructure][parameterNumber];
  }

  /**
   * <p>
   * calcChemPotEmpty.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComps a int
   * @param temp a double
   * @param pres a double
   * @param hydrateStruct a int
   * @return a double
   */
  public double calcChemPotEmpty(PhaseInterface phase, int numberOfComps, double temp, double pres,
      int hydrateStruct) {
    double dGf = 0.0;
    double dHf = 0.0;
    double Cpa = getCpA();
    double Cpb = getCpB();
    double Cpc = getCpC();
    double Cpd = getCpD();
    double deltaMolarVolume = 0.0;
    if (hydrateStruct == 1) {
      dGf = getDGfHydrate()[1];
      dHf = getDHfHydrate()[1];
      deltaMolarVolume = 5.0e-6;
    } else {
      dGf = getDGfHydrate()[0];
      dHf = getDHfHydrate()[0];
      deltaMolarVolume = 4.6e-6;
    }
    double T0 = 298.15;

    dHf += Cpa * (temp - T0) + Cpb * Math.log(temp / T0) - Cpc * (1.0 / temp - 1.0 / T0)
        - 1.0 / 2.0 * Cpd * (1.0 / (temp * temp) - 1.0 / (T0 * T0));

    return (dGf / R / T0 + 1.0 * dHf * (1.0 / R / temp - 1.0 / R / T0))
        + deltaMolarVolume / R / ((temp + T0) / 2.0) * (pres * 1e5 - 1e5);
  }

  /**
   * <p>
   * calcChemPotIdealWater.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @param numberOfComps a int
   * @param temp a double
   * @param pres a double
   * @param hydrateStruct a int
   * @return a double
   */
  public double calcChemPotIdealWater(PhaseInterface phase, int numberOfComps, double temp,
      double pres, int hydrateStruct) {
    double dGf = -228700.0;
    double dHf = -242000.0;
    double Cpa = getCpA();
    double Cpb = getCpB();
    double Cpc = getCpC();
    double Cpd = getCpD();
    // Cpa = 0.7354;
    // Cpb = 0.01418;
    // Cpc = -1.727e-5;
    double T0 = 298.15;
    dHf += Cpa * (temp - T0) + Cpb * Math.log(temp / T0) - Cpc * (1.0 / temp - 1.0 / T0)
        - 1.0 / 2.0 * Cpd * (1.0 / (temp * temp) - 1.0 / (T0 * T0));
    return (dGf / R / T0 + 1.0 * dHf * (1.0 / R / temp - 1.0 / R / T0));
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
    if (componentName.equals("water")) {
      return 0.0;
    }
    // if(componentName.equals("methane")){
    double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber];
    double temp = 1.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      if (!phase.getComponent(i).getComponentName().equals("water")) {
        if (phase.getComponent(i).isHydrateFormer()) {
          temp += ((ComponentHydrate) phase.getComponent(i)).calcCKI(stucture, cavityType, phase)
              * reffug[i];
        }
      }
    }
    return yki / temp;
    // }
    // else return 0.0;
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
        * potIntegral(stucture, cavityType, phase);
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
    // System.out.println("ref fug setting " + val);
    reffug[compNumbm] = val;
  }

  /**
   * <p>
   * potIntegral.
   * </p>
   *
   * @param stucture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double potIntegral(int stucture, int cavityType, PhaseInterface phase) {
    double val = 0.0;
    double endval = cavRadius[stucture][cavityType] - getSphericalCoreRadiusHydrate();
    double x = 0.0;
    double step = endval / 100.0;
    x = step;
    for (int i = 1; i < 100; i++) {
      // System.out.println("x" +x);
      // System.out.println("pot " + getPot(x,stucture,cavityType,phase));
      val += step * ((getPot(x, stucture, cavityType, phase)
          + 4 * getPot((x + 0.5 * step), stucture, cavityType, phase)
          + getPot(x + step, stucture, cavityType, phase)) / 6.0);
      x = i * step;
    }
    return val / 100000.0;
  }

  /**
   * <p>
   * getPot.
   * </p>
   *
   * @param radius a double
   * @param struccture a int
   * @param cavityType a int
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   * @return a double
   */
  public double getPot(double radius, int struccture, int cavityType, PhaseInterface phase) {
    double pot =
        2.0 * coordNumb[struccture][cavityType] * this.getLennardJonesEnergyParameterHydrate()
            * ((Math.pow(this.getLennardJonesMolecularDiameterHydrate(), 12.0)
                / (Math.pow(cavRadius[struccture][cavityType], 11.0) * radius)
                * (delt(10.0, radius, struccture, cavityType, this)
                    + this.getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType]
                        * delt(11.0, radius, struccture, cavityType, this)))
                - (Math.pow(this.getLennardJonesMolecularDiameterHydrate(), 6.0)
                    / (Math.pow(cavRadius[struccture][cavityType], 5.0) * radius)
                    * (delt(4.0, radius, struccture, cavityType, this)
                        + this.getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType]
                            * delt(5.0, radius, struccture, cavityType, this))));
    // System.out.println("lenjones "
    // +this.getLennardJonesMolecularDiameterHydrate() );
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
   * @param n a double
   * @param radius a double
   * @param struccture a int
   * @param cavityType a int
   * @param comp a {@link neqsim.thermo.component.ComponentInterface} object
   * @return a double
   */
  public double delt(double n, double radius, int struccture, int cavityType,
      ComponentInterface comp) {
    double delt = 1.0 / n
        * (Math
            .pow(1.0 - radius / cavRadius[struccture][cavityType]
                - ((ComponentHydrate) comp).getSphericalCoreRadiusHydrate()
                    / cavRadius[struccture][cavityType],
                -n)
            - Math.pow(1.0 + radius / cavRadius[struccture][cavityType]
                - ((ComponentHydrate) comp).getSphericalCoreRadiusHydrate()
                    / cavRadius[struccture][cavityType],
                -n));

    // System.out.println("delt " + delt);
    return delt;
  }

  /**
   * <p>
   * Getter for the field <code>dGfHydrate</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getDGfHydrate() {
    return dGfHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>dGfHydrate</code>.
   * </p>
   *
   * @param dGfHydrate an array of type double
   */
  public void setDGfHydrate(double[] dGfHydrate) {
    this.dGfHydrate = dGfHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>dGfHydrate</code>.
   * </p>
   *
   * @param dGfHydrate a double
   * @param i a int
   */
  public void setDGfHydrate(double dGfHydrate, int i) {
    this.dGfHydrate[i] = dGfHydrate;
  }

  /**
   * <p>
   * Getter for the field <code>dHfHydrate</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getDHfHydrate() {
    return dHfHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>dHfHydrate</code>.
   * </p>
   *
   * @param dHfHydrate a double
   * @param i a int
   */
  public void setDHfHydrate(double dHfHydrate, int i) {
    this.dHfHydrate[i] = dHfHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>dHfHydrate</code>.
   * </p>
   *
   * @param dHfHydrate an array of type double
   */
  public void setDHfHydrate(double[] dHfHydrate) {
    this.dHfHydrate = dHfHydrate;
  }

  /**
   * <p>
   * getMolarVolumeHydrate.
   * </p>
   *
   * @param structure a int
   * @param temperature a double
   * @return a double
   */
  public double getMolarVolumeHydrate(int structure, double temperature) {
    // taken from chem.eng.sci Avlonitis 1994
    double TO = 273.15;
    if (structure == 0) {
      double v0 = 22.35;
      double k1 = 3.1075e-4;
      double k2 = 5.9537e-7;
      double k3 = 1.3707e-10;
      return v0 * (1.0 + k1 * (temperature - TO) + k2 * Math.pow(temperature - TO, 2.0)
          + k3 * Math.pow(temperature - TO, 3.0)) / 1.0e6;
    } else if (structure == 1) {
      double v0 = 22.57;
      double k1 = 1.9335e-4;
      double k2 = 2.1768e-7;
      double k3 = -1.4786e-10;
      return v0 * (1.0 + k1 * (temperature - TO) + k2 * Math.pow(temperature - TO, 2.0)
          + k3 * Math.pow(temperature - TO, 3.0)) / 1.0e6;
    } else if (structure == -1) {
      double v0 = 19.6522;
      double k1 = 1.6070e-4;
      double k2 = 3.4619e-7;
      double k3 = -1.4786e-10;
      return v0 * (1.0 + k1 * (temperature - TO) + k2 * Math.pow(temperature - TO, 2.0)
          + k3 * Math.pow(temperature - TO, 3.0)) / 1.0e6;
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * Getter for the field <code>sphericalCoreRadiusHydrate</code>.
   * </p>
   *
   * @return a double
   */
  public double getSphericalCoreRadiusHydrate() {
    return sphericalCoreRadiusHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>sphericalCoreRadiusHydrate</code>.
   * </p>
   *
   * @param sphericalCoreRadiusHydrate a double
   */
  public void setSphericalCoreRadiusHydrate(double sphericalCoreRadiusHydrate) {
    this.sphericalCoreRadiusHydrate = sphericalCoreRadiusHydrate;
  }

  /**
   * <p>
   * Getter for the field <code>lennardJonesEnergyParameterHydrate</code>.
   * </p>
   *
   * @return a double
   */
  public double getLennardJonesEnergyParameterHydrate() {
    return lennardJonesEnergyParameterHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>lennardJonesEnergyParameterHydrate</code>.
   * </p>
   *
   * @param lennardJonesEnergyParameterHydrate a double
   */
  public void setLennardJonesEnergyParameterHydrate(double lennardJonesEnergyParameterHydrate) {
    this.lennardJonesEnergyParameterHydrate = lennardJonesEnergyParameterHydrate;
  }

  /**
   * <p>
   * Getter for the field <code>lennardJonesMolecularDiameterHydrate</code>.
   * </p>
   *
   * @return a double
   */
  public double getLennardJonesMolecularDiameterHydrate() {
    return lennardJonesMolecularDiameterHydrate;
  }

  /**
   * <p>
   * Setter for the field <code>lennardJonesMolecularDiameterHydrate</code>.
   * </p>
   *
   * @param lennardJonesMolecularDiameterHydrate a double
   */
  public void setLennardJonesMolecularDiameterHydrate(double lennardJonesMolecularDiameterHydrate) {
    this.lennardJonesMolecularDiameterHydrate = lennardJonesMolecularDiameterHydrate;
  }

  /**
   * <p>
   * setSolidRefFluidPhase.
   * </p>
   *
   * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
   */
  public void setSolidRefFluidPhase(PhaseInterface phase) {
    try {
      refPhase = phase.getClass().getDeclaredConstructor().newInstance();
      refPhase.setTemperature(273.0);
      refPhase.setPressure(1.0);
      refPhase.addComponent("water", 10.0, 10.0, 0);
      refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 0, PhaseType.byValue(1), 1.0);
    } catch (Exception ex) {
      logger.error("error occured", ex);
    }
  }
  // public double dfugdt(PhaseInterface phase, int numberOfComps, double temp,
  // double pres){
  // if(componentName.equals("water")){
  // double solvol =
  // 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
  // dfugdt =
  // Math.log((getEmptyHydrateStructureVapourPressuredT(hydrateStructure,temp))/pres);
  // } else dfugdt=0;
  // return dfugdt;
  // }

  // public double getEmptyHydrateStructureVapourPressure2(int type, double
  // temperature){
  // double par1_struc1=4.6477;
  // double par2_struc1=-5242.979;
  // double par3_struc1=2.7789;
  // double par4_struc1=-8.7156e-3;
  // if(type==0){
  // return
  // Math.exp(par1_struc1*Math.log(temperature)+par2_struc1/temperature+par3_struc1+par4_struc1*temperature)/1.0e5;
  // }
  // if(type==1){
  // return
  // Math.exp(par1_struc2+par2_struc2/temperature)*ThermodynamicConstantsInterface.referencePressure;
  // } else return 0.0;
  // }

  // public double getEmptyHydrateStructureVapourPressure(int type, double
  // temperature){
  // if(type==0){
  // return
  // Math.exp(par1_struc1+par2_struc1/temperature)*ThermodynamicConstantsInterface.referencePressure;
  // }
  // if(type==1){
  // return
  // Math.exp(par1_struc2+par2_struc2/temperature)*ThermodynamicConstantsInterface.referencePressure;
  // } else return 0.0;
  // }

  // public double getEmptyHydrateStructureVapourPressuredT(int type, double
  // temperature){
  // if(type==0){
  // return
  // -par2_struc1/(temperature*temperature)*Math.exp(par1_struc1+par2_struc1/temperature);
  // }
  // if(type==1){
  // return
  // -par2_struc2/(temperature*temperature)*Math.exp(par1_struc2+par2_struc2/temperature);
  // } else return 0.0;
  // }

  /**
   * <p>
   * Getter for the field <code>cavprwat</code>.
   * </p>
   *
   * @param structure a int
   * @param cavityType a int
   * @return the cavprwat
   */
  public double getCavprwat(int structure, int cavityType) {
    return getCavprwat()[structure][cavityType];
  }

  /**
   * <p>
   * Getter for the field <code>cavprwat</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[][] getCavprwat() {
    return cavprwat;
  }
}
