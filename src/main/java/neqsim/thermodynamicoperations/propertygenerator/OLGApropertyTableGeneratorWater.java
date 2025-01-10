package neqsim.thermodynamicoperations.propertygenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import org.apache.commons.math3.analysis.interpolation.BicubicInterpolatingFunction;
import org.apache.commons.math3.analysis.interpolation.BicubicInterpolator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * OLGApropertyTableGeneratorWater class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class OLGApropertyTableGeneratorWater extends neqsim.thermodynamicoperations.BaseOperation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OLGApropertyTableGeneratorWater.class);

  BicubicInterpolator interpolationFunc = new BicubicInterpolator();
  SystemInterface thermoSystem = null;
  SystemInterface gasSystem = null;
  SystemInterface oilSystem = null;
  SystemInterface waterSystem = null;
  ThermodynamicOperations thermoOps = null;
  double[] pressures;
  double[] temperatureLOG;
  double[] temperatures;
  double[] pressureLOG = null;
  double[] bubP;
  double[] bubT;
  double[] dewP;
  double[] bubPLOG;
  double[] dewPLOG;
  Matrix XMatrixgas;
  Matrix XMatrixoil;
  Matrix XMatrixwater;
  double[][] ROG = null;
  double TC;
  double PC;
  double RSWTOB;
  double[][][] props;
  int nProps;
  String[] names;
  Matrix[] xcoef = new Matrix[9];
  String[] units;
  int temperatureSteps;
  int pressureSteps;
  boolean continuousDerivativesExtrapolation = true;
  boolean hasGasValues = false;
  boolean hasOilValues = false;
  boolean hasWaterValues = false;
  boolean[][][] hasValue;
  Matrix aMatrix = new Matrix(4, 4);
  Matrix s = new Matrix(1, 4);
  String fileName = "c:/Appl/OLGAneqsim.tab";

  /**
   * <p>
   * Constructor for OLGApropertyTableGeneratorWater.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public OLGApropertyTableGeneratorWater(SystemInterface system) {
    this.thermoSystem = system;
    thermoOps = new ThermodynamicOperations(thermoSystem);

    XMatrixgas = new Matrix(9, 4);
    XMatrixoil = new Matrix(9, 4);
    XMatrixwater = new Matrix(9, 4);

    gasSystem = new SystemSrkEos(298, 10);
    gasSystem.addComponent("methane", 1);
    // gasSystem.createDatabase(true);
    gasSystem.init(0);
    gasSystem.setNumberOfPhases(1);

    waterSystem = new SystemSrkCPAstatoil(298, 10);
    waterSystem.addComponent("water", 1);
    // waterSystem.createDatabase(true);
    waterSystem.init(0);
    waterSystem.setNumberOfPhases(1);

    oilSystem = new SystemSrkEos(298, 10);
    oilSystem.addComponent("nC10", 1);
    // oilSystem.createDatabase(true);
    oilSystem.init(0);
    oilSystem.setNumberOfPhases(1);
  }

  /**
   * <p>
   * Setter for the field <code>fileName</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void setFileName(String name) {
    fileName = name;
  }

  /**
   * <p>
   * setPressureRange.
   * </p>
   *
   * @param minPressure a double
   * @param maxPressure a double
   * @param numberOfSteps a int
   */
  public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
    pressures = new double[numberOfSteps];
    pressureLOG = new double[numberOfSteps];
    double step = (maxPressure - minPressure) / (numberOfSteps * 1.0 - 1.0);
    for (int i = 0; i < numberOfSteps; i++) {
      pressures[i] = minPressure + i * step;
      pressureLOG[i] = pressures[i] * 1e5;
    }
  }

  /**
   * <p>
   * setTemperatureRange.
   * </p>
   *
   * @param minTemperature a double
   * @param maxTemperature a double
   * @param numberOfSteps a int
   */
  public void setTemperatureRange(double minTemperature, double maxTemperature, int numberOfSteps) {
    temperatures = new double[numberOfSteps];
    temperatureLOG = new double[numberOfSteps];
    double step = (maxTemperature - minTemperature) / (numberOfSteps * 1.0 - 1.0);
    for (int i = 0; i < numberOfSteps; i++) {
      temperatures[i] = minTemperature + i * step;
      temperatureLOG[i] = temperatures[i] - 273.15;
    }
  }

  /**
   * <p>
   * calcPhaseEnvelope.
   * </p>
   */
  public void calcPhaseEnvelope() {
    try {
      thermoOps.calcPTphaseEnvelope();
      TC = thermoSystem.getTC() - 273.15;
      PC = thermoSystem.getPC() * 1e5;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * <p>
   * calcBubP.
   * </p>
   *
   * @param temperatures an array of type double
   * @return an array of type double
   */
  public double[] calcBubP(double[] temperatures) {
    double[] bubP = new double[temperatures.length];
    bubPLOG = new double[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      thermoSystem.setTemperature(temperatures[i]);
      try {
        thermoOps.bubblePointPressureFlash(false);
        bubP[i] = thermoSystem.getPressure();
        bubPLOG[i] = bubP[i] * 1e5;
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        bubP[i] = 0;
      }
    }
    return bubP;
  }

  /**
   * <p>
   * calcDewP.
   * </p>
   *
   * @param temperatures an array of type double
   * @return an array of type double
   */
  public double[] calcDewP(double[] temperatures) {
    double[] dewP = new double[temperatures.length];
    dewPLOG = new double[temperatures.length];
    for (int i = 0; i < temperatures.length; i++) {
      thermoSystem.setTemperature(temperatures[i]);
      try {
        thermoOps.dewPointPressureFlashHC();
        dewP[i] = thermoSystem.getPressure();
        dewPLOG[i] = dewP[i] * 1e5;
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        dewP[i] = 0;
      }
    }
    return dewP;
  }

  /**
   * <p>
   * calcBubT.
   * </p>
   *
   * @param pressures an array of type double
   * @return an array of type double
   */
  public double[] calcBubT(double[] pressures) {
    double[] bubTemps = new double[pressures.length];
    for (int i = 0; i < pressures.length; i++) {
      thermoSystem.setPressure(pressures[i]);
      try {
        thermoOps.bubblePointTemperatureFlash();
        bubT[i] = thermoSystem.getPressure();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
        bubT[i] = 0.0;
      }
    }
    return bubTemps;
  }

  /**
   * <p>
   * initCalc.
   * </p>
   */
  public void initCalc() {
    double stdTemp = 288.15;
    double stdPres = ThermodynamicConstantsInterface.referencePressure;
    // double GOR, GLR;
    double[] molfracs = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    double[] MW = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    double[] dens = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
    String[] components = new String[thermoSystem.getPhase(0).getNumberOfComponents()];

    for (int i = 0; i < molfracs.length; i++) {
      molfracs[i] = thermoSystem.getPhase(0).getComponent(i).getz();
      components[i] = thermoSystem.getPhase(0).getComponent(i).getComponentName();
      MW[i] = thermoSystem.getPhase(0).getComponent(i).getMolarMass() * 1000;
      dens[i] = thermoSystem.getPhase(0).getComponent(i).getNormalLiquidDensity();
    }

    thermoSystem.setTemperature(stdTemp);
    thermoSystem.setPressure(stdPres);

    thermoOps.TPflash();

    // GOR = thermoSystem.getPhase(0).getTotalVolume() /
    // thermoSystem.getPhase(1).getTotalVolume();
    // GLR = thermoSystem.getPhase(0).getTotalVolume() /
    // thermoSystem.getPhase(1).getTotalVolume();
  }

  /**
   * <p>
   * calcRSWTOB.
   * </p>
   */
  public void calcRSWTOB() {
    thermoSystem.init(0);
    thermoSystem.init(1);
    if (thermoSystem.getPhase(0).hasComponent("water")) {
      RSWTOB = thermoSystem.getPhase(0).getComponent("water").getNumberOfmoles()
          * thermoSystem.getPhase(0).getComponent("water").getMolarMass()
          / (thermoSystem.getTotalNumberOfMoles() * thermoSystem.getMolarMass());
    } else {
      RSWTOB = 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    calcRSWTOB();
    logger.info("RSWTOB " + RSWTOB);
    nProps = 29;
    props = new double[nProps][pressures.length][temperatures.length];
    units = new String[nProps];
    names = new String[nProps];

    // int startGasTemperatures = 0;
    boolean acceptedFlash = true;
    for (int j = 0; j < temperatures.length; j++) {
      thermoSystem.setTemperature(temperatures[j]);
      for (int i = 0; i < pressures.length; i++) {
        thermoSystem.setPressure(pressures[i]);
        try {
          logger.info("TPflash... " + thermoSystem.getTemperature() + " pressure "
              + thermoSystem.getPressure());
          if (thermoSystem.getTemperature() > (273.15 + 106.8)) {
            logger.info("here I am");
            thermoOps.TPflash();
          } else {
            thermoOps.TPflash();
          }
          thermoSystem.init(3);
          thermoSystem.initPhysicalProperties();
          acceptedFlash = true;
        } catch (Exception ex) {
          acceptedFlash = false;
          logger.info("fail temperature " + thermoSystem.getTemperature() + " fail pressure "
              + thermoSystem.getPressure());

          thermoSystem.display();
          logger.error(ex.getMessage(), ex);
        }

        /*
         * logger.info("water density " +
         * thermoSystem.getPhase(2).getPhysicalProperties().getDensity()); logger.info("RSW " +
         * thermoSystem.getPhase(0).getComponent("water").getx() *
         * thermoSystem.getPhase(0).getComponent("water").getMolarMass() /
         * thermoSystem.getPhase(0).getMolarMass()); logger.info("surf tens oil-water  " +
         * thermoSystem.getInterphaseProperties().getSurfaceTension(1, 2));
         * logger.info("surf tens gas-water  " +
         * thermoSystem.getInterphaseProperties().getSurfaceTension(0, 2));
         */
        int k = 0;
        if (thermoSystem.hasPhaseType("gas") && acceptedFlash) {
          int phaseNumb = thermoSystem.getPhaseNumberOfPhase("gas");

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getDensity();
          names[k] = "GAS DENSITY";
          units[k] = "KG/M3";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getdrhodP() / 1.0e5;
          names[k] = "DRHOG/DP";
          units[k] = "S2/M2";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getdrhodT();
          names[k] = "DRHOG/DT";
          units[k] = "KG/M3-K";
          k++;

          // double beta = 0.0;
          if (thermoSystem.hasPhaseType("oil")) {
            props[k][i][j] = thermoSystem.getPhase(phaseNumb).getBeta()
                * thermoSystem.getPhase(phaseNumb).getMolarMass()
                / (thermoSystem.getPhase(phaseNumb).getBeta()
                    * thermoSystem.getPhase(phaseNumb).getMolarMass()
                    + thermoSystem.getPhase("oil").getBeta()
                        * thermoSystem.getPhase("oil").getMolarMass());
          } else {
            props[k][i][j] = 1.0;
            // thermoSystem.getPhase(phaseNumb).getBeta() *
            // thermoSystem.getPhase(phaseNumb).getMolarMass() / thermoSystem.getMolarMass();
          }
          names[k] = "GAS MASS FRACTION";
          units[k] = "-";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getViscosity();
          names[k] = "GAS VISCOSITY";
          units[k] = "NS/M2";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getCp()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "GAS HEAT CAPACITY";
          units[k] = "J/KG-K";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getEnthalpy()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "GAS ENTHALPY";
          units[k] = "J/KG";
          k++;

          props[k][i][j] =
              thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getConductivity();
          names[k] = "GAS THERMAL CONDUCTIVITY";
          units[k] = "W/M-K";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getEntropy()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "GAS ENTROPY";
          units[k] = "J/KG/K";
          k++;
          hasGasValues = true;
          // set gas properties
        } else {
          if (continuousDerivativesExtrapolation && hasGasValues) {
            do {
              if (i > 1) {
                props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                    / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
                // } //else if (j < 2) {
                // props[k][i][j] = 0; //props[k][i - 1][j] + (props[k][i - 1][j] -
                // props[k][i -
                // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                // pressures[i
                // - 1]);
                // } else {
                // props[k][i][j] = 0; //props[k][i - 1][j - 1] + (props[k][i][j -
                // 1]
                // -
                // props[k][i][j - 2]) / (temperatures[j - 1] - temperatures[j - 2])
                // *
                // (temperatures[j] - temperatures[j - 1]) + (props[k][i - 1][j] -
                // props[k][i -
                // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                // pressures[i
                // - 1]);
                // double newTemp = pressures[i];
                // double vall = xcoef[k].get(0, 0) + newTemp * (xcoef[k].get(1, 0)
                // + newTemp *
                // (xcoef[k].get(2, 0) + newTemp * xcoef[k].get(3, 0)));
                // props[k][i][j] = vall;
                // if(i>0 && props[k][i-1][j]>1e-10) props[k][i][j] =
                // props[k][i-1][j]*pressures[i]/pressures[i-1];
              }
              if (names[k].equals("GAS MASS FRACTION") && props[k][i][j] < 0) {
                props[k][i][j] = 0;
              }
              k++;
            } while (k < 9); // names[k] = "GAS DENSITY";
            // units[k] = "KG/M3";
            /*
             * } else if (false && !hasGasValues) { startGasTemperatures = j;
             */
          } else {
            gasSystem.setTemperature(temperatures[j]);
            gasSystem.setPressure(pressures[i]);
            gasSystem.init(3);
            gasSystem.initPhysicalProperties();
            // gasSystem.display();
            props[k][i][j] = gasSystem.getPhase(0).getPhysicalProperties().getDensity();
            names[k] = "GAS DENSITY";
            units[k] = "KG/M3";
            k++;
            props[k][i][j] = gasSystem.getPhase(0).getdrhodP() / 1.0e5;
            names[k] = "DRHOG/DP";
            units[k] = "S2/M2";
            k++;
            props[k][i][j] = gasSystem.getPhase(0).getdrhodT();
            names[k] = "DRHOG/DT";
            units[k] = "KG/M3-K";
            k++;

            props[k][i][j] = 0.0;
            // thermoSystem.getPhase(phaseNumb).getBeta() *
            // thermoSystem.getPhase(phaseNumb).getMolarMass() / thermoSystem.getMolarMass();
            names[k] = "GAS MASS FRACTION";
            units[k] = "-";
            k++;

            props[k][i][j] = gasSystem.getPhase(0).getPhysicalProperties().getViscosity();
            names[k] = "GAS VISCOSITY";
            units[k] = "NS/M2";
            k++;

            props[k][i][j] =
                gasSystem.getPhase(0).getCp() / gasSystem.getPhase(0).getNumberOfMolesInPhase()
                    / gasSystem.getPhase(0).getMolarMass();
            names[k] = "GAS HEAT CAPACITY";
            units[k] = "J/KG-K";
            k++;

            props[k][i][j] = gasSystem.getPhase(0).getEnthalpy()
                / gasSystem.getPhase(0).getNumberOfMolesInPhase()
                / gasSystem.getPhase(0).getMolarMass();
            names[k] = "GAS ENTHALPY";
            units[k] = "J/KG";
            k++;

            props[k][i][j] = gasSystem.getPhase(0).getPhysicalProperties().getConductivity();
            names[k] = "GAS THERMAL CONDUCTIVITY";
            units[k] = "W/M-K";
            k++;

            props[k][i][j] =
                gasSystem.getPhase(0).getEntropy() / gasSystem.getPhase(0).getNumberOfMolesInPhase()
                    / gasSystem.getPhase(0).getMolarMass();
            names[k] = "GAS ENTROPY";
            units[k] = "J/KG/K";
            k++;
          }
        }

        if (thermoSystem.hasPhaseType("oil") && acceptedFlash) {
          int phaseNumb = thermoSystem.getPhaseNumberOfPhase("oil");

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getDensity();
          names[k] = "LIQUID DENSITY";
          units[k] = "KG/M3";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getdrhodP() / 1.0e5;
          names[k] = "DRHOL/DP";
          units[k] = "S2/M2";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getdrhodT();
          names[k] = "DRHOL/DT";
          units[k] = "KG/M3-K";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getViscosity();
          names[k] = "LIQUID VISCOSITY";
          units[k] = "NS/M2";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getCp()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "LIQUID HEAT CAPACITY";
          units[k] = "J/KG-K";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getEnthalpy()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "LIQUID ENTHALPY";
          units[k] = "J/KG";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getEntropy()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "LIQUID ENTROPY";
          units[k] = "J/KG/K";
          k++;

          props[k][i][j] =
              thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getConductivity();
          names[k] = "LIQUID THERMAL CONDUCTIVITY";
          units[k] = "W/M-K";
          k++;
          hasOilValues = true;
        } else {
          if (continuousDerivativesExtrapolation && hasOilValues) {
            do {
              if (i > 1) {
                props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                    / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
              }
              /*
               * if (i < 2) { props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] -
               * props[k][i][j - 2]) / (temperatures[j - 1] - temperatures[j - 2]) *
               * (temperatures[j] - temperatures[j - 1]); } else if (j < 2) { props[k][i][j] =
               * props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j]) / (pressures[i - 1]
               * - pressures[i - 2]) * (pressures[i] - pressures[i - 1]); } else { props[k][i][j] =
               * props[k][i - 1][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2]) /
               * (temperatures[j - 1] - temperatures[j - 2]) * (temperatures[j] - temperatures[j -
               * 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) / (pressures[i - 1] - pressures[i -
               * 2]) * (pressures[i] - pressures[i - 1]); } props[k][i][j] = 0.0;
               */
              k++;
            } while (k < 17); // names[k] = "GAS DENSITY";
            // units[k] = "KG/M3";
          } else {
            oilSystem.setPhaseType(0, PhaseType.LIQUID);
            oilSystem.setTemperature(temperatures[j]);
            oilSystem.setPressure(pressures[i]);
            oilSystem.init(3);
            oilSystem.initPhysicalProperties();

            props[k][i][j] = oilSystem.getPhase(0).getPhysicalProperties().getDensity();
            names[k] = "LIQUID DENSITY";
            units[k] = "KG/M3";
            k++;

            props[k][i][j] = oilSystem.getPhase(0).getdrhodP() / 1.0e5;
            names[k] = "DRHOL/DP";
            units[k] = "S2/M2";
            k++;

            props[k][i][j] = oilSystem.getPhase(0).getdrhodT();
            names[k] = "DRHOL/DT";
            units[k] = "KG/M3-K";
            k++;

            props[k][i][j] = oilSystem.getPhase(0).getPhysicalProperties().getViscosity();
            names[k] = "LIQUID VISCOSITY";
            units[k] = "NS/M2";
            k++;

            props[k][i][j] =
                oilSystem.getPhase(0).getCp() / oilSystem.getPhase(0).getNumberOfMolesInPhase()
                    / oilSystem.getPhase(0).getMolarMass();
            names[k] = "LIQUID HEAT CAPACITY";
            units[k] = "J/KG-K";
            k++;

            props[k][i][j] = oilSystem.getPhase(0).getEnthalpy()
                / oilSystem.getPhase(0).getNumberOfMolesInPhase()
                / oilSystem.getPhase(0).getMolarMass();
            names[k] = "LIQUID ENTHALPY";
            units[k] = "J/KG";
            k++;

            props[k][i][j] =
                oilSystem.getPhase(0).getEntropy() / oilSystem.getPhase(0).getNumberOfMolesInPhase()
                    / oilSystem.getPhase(0).getMolarMass();
            names[k] = "LIQUID ENTROPY";
            units[k] = "J/KG/K";
            k++;

            props[k][i][j] = oilSystem.getPhase(0).getPhysicalProperties().getConductivity();
            names[k] = "LIQUID THERMAL CONDUCTIVITY";
            units[k] = "W/M-K";
            k++;
          }
          // setOilProperties();
          // set gas properties
        }

        if (thermoSystem.hasPhaseType("aqueous") && acceptedFlash) {
          int phaseNumb = thermoSystem.getPhaseNumberOfPhase("aqueous");

          props[k][i][j] = thermoSystem.getPhase(0).getComponent("water").getx()
              * thermoSystem.getPhase(0).getComponent("water").getMolarMass()
              / thermoSystem.getPhase(0).getMolarMass();
          names[k] = "WATER VAPOR MASS FRACTION";
          units[k] = "-";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getDensity();
          names[k] = "WATER DENSITY";
          units[k] = "KG/M3";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getdrhodP() / 1.0e5;
          names[k] = "DRHOWAT/DP";
          units[k] = "S2/M2";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getdrhodT();
          names[k] = "DRHOWAT/DT";
          units[k] = "KG/M3-K";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getViscosity();
          names[k] = "WATER VISCOSITY";
          units[k] = "NS/M2";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getCp()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "WATER HEAT CAPACITY";
          units[k] = "J/KG-K";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getEnthalpy()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "WATER ENTHALPY";
          units[k] = "J/KG";
          k++;

          props[k][i][j] = thermoSystem.getPhase(phaseNumb).getEntropy()
              / thermoSystem.getPhase(phaseNumb).getNumberOfMolesInPhase()
              / thermoSystem.getPhase(phaseNumb).getMolarMass();
          names[k] = "WATER ENTROPY";
          units[k] = "J/KG/K";
          k++;

          props[k][i][j] =
              thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getConductivity();
          names[k] = "WATER THERMAL CONDUCTIVITY";
          units[k] = "W/M-K";
          k++;
          hasWaterValues = true;
        } else {
          if (continuousDerivativesExtrapolation && hasWaterValues) {
            do {
              if (j > 1) {
                props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                    / (temperatures[j - 1] - temperatures[j - 2])
                    * (temperatures[j] - temperatures[j - 1]);
              }
              /*
               * if (i < 2) { props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] -
               * props[k][i][j - 2]) / (temperatures[j - 1] - temperatures[j - 2]) *
               * (temperatures[j] - temperatures[j - 1]); } else if (j < 2) { props[k][i][j] =
               * props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j]) / (pressures[i - 1]
               * - pressures[i - 2]) * (pressures[i] - pressures[i - 1]); } else { props[k][i][j] =
               * props[k][i - 1][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2]) /
               * (temperatures[j - 1] - temperatures[j - 2]) * (temperatures[j] - temperatures[j -
               * 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) / (pressures[i - 1] - pressures[i -
               * 2]) * (pressures[i] - pressures[i - 1]); } props[k][i][j] = 0.0;
               */
              k++;
            } while (k < 26); // names[k] = "GAS DENSITY";
            // units[k] = "KG/M3";
          } else {
            waterSystem.setTemperature(temperatures[j]);
            waterSystem.setPressure(pressures[i]);
            waterSystem.setPhaseType(0, PhaseType.LIQUID);
            waterSystem.init(3);
            waterSystem.initPhysicalProperties();

            if (thermoSystem.getPhase(0).hasComponent("water")) {
              props[k][i][j] = thermoSystem.getPhase(0).getComponent("water").getz()
                  * thermoSystem.getPhase(0).getComponent("water").getMolarMass()
                  / thermoSystem.getPhase(0).getMolarMass();
            } else {
              props[k][i][j] = 0.0;
            }
            names[k] = "WATER VAPOR MASS FRACTION";
            units[k] = "-";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getPhysicalProperties().getDensity();
            names[k] = "WATER DENSITY";
            units[k] = "KG/M3";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getdrhodP() / 1.0e5;
            names[k] = "DRHOWAT/DP";
            units[k] = "S2/M2";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getdrhodT();
            names[k] = "DRHOWAT/DT";
            units[k] = "KG/M3-K";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getPhysicalProperties().getViscosity();
            names[k] = "WATER VISCOSITY";
            units[k] = "NS/M2";
            k++;

            props[k][i][j] =
                waterSystem.getPhase(0).getCp() / waterSystem.getPhase(0).getNumberOfMolesInPhase()
                    / waterSystem.getPhase(0).getMolarMass();
            names[k] = "WATER HEAT CAPACITY";
            units[k] = "J/KG-K";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getEnthalpy()
                / waterSystem.getPhase(0).getNumberOfMolesInPhase()
                / waterSystem.getPhase(0).getMolarMass();
            names[k] = "WATER ENTHALPY";
            units[k] = "J/KG";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getEntropy()
                / waterSystem.getPhase(0).getNumberOfMolesInPhase()
                / waterSystem.getPhase(0).getMolarMass();
            names[k] = "WATER ENTROPY";
            units[k] = "J/KG/K";
            k++;

            props[k][i][j] = waterSystem.getPhase(0).getPhysicalProperties().getConductivity();
            names[k] = "WATER THERMAL CONDUCTIVITY";
            units[k] = "W/M-K";
            k++;
          }
        }

        if (thermoSystem.hasPhaseType("gas") && thermoSystem.hasPhaseType("oil") && acceptedFlash) {
          props[k][i][j] = thermoSystem.getInterphaseProperties().getSurfaceTension(
              thermoSystem.getPhaseNumberOfPhase("gas"), thermoSystem.getPhaseNumberOfPhase("oil"));
          names[k] = "VAPOR-LIQUID SURFACE TENSION";
          units[k] = "N/M";
          k++;
        } else {
          if (continuousDerivativesExtrapolation && (i >= 2 || j >= 2)) {
            if (i < 2) {
              props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                  / (temperatures[j - 1] - temperatures[j - 2])
                  * (temperatures[j] - temperatures[j - 1]);
            } else if (j < 2) {
              props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                  / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
            } else {
              props[k][i][j] = props[k][i - 1][j - 1]
                  + (props[k][i][j - 1] - props[k][i][j - 2])
                      / (temperatures[j - 1] - temperatures[j - 2])
                      * (temperatures[j] - temperatures[j - 1])
                  + (props[k][i - 1][j] - props[k][i - 2][j])
                      / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
            }
            props[k][i][j] = 10.0e-3;
            k++;
          } else {
            props[k][i][j] = 10.0e-3;
            names[k] = "VAPOR-LIQUID SURFACE TENSION";
            units[k] = "N/M";
            k++;
          }
        }

        if (thermoSystem.hasPhaseType("gas") && thermoSystem.hasPhaseType("aqueous")
            && acceptedFlash) {
          props[k][i][j] = thermoSystem.getInterphaseProperties().getSurfaceTension(
              thermoSystem.getPhaseNumberOfPhase("gas"),
              thermoSystem.getPhaseNumberOfPhase("aqueous"));
          names[k] = "VAPOR-WATER SURFACE TENSION";
          units[k] = "N/M";
          k++;
        } else {
          if (continuousDerivativesExtrapolation && (i >= 2 || j >= 2)) {
            if (i < 2) {
              props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                  / (temperatures[j - 1] - temperatures[j - 2])
                  * (temperatures[j] - temperatures[j - 1]);
            } else if (j < 2) {
              props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                  / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
            } else {
              props[k][i][j] = props[k][i - 1][j - 1]
                  + (props[k][i][j - 1] - props[k][i][j - 2])
                      / (temperatures[j - 1] - temperatures[j - 2])
                      * (temperatures[j] - temperatures[j - 1])
                  + (props[k][i - 1][j] - props[k][i - 2][j])
                      / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
            }
            props[k][i][j] = 60.0e-3;
            k++;
          } else {
            props[k][i][j] = 60.0e-3;
            names[k] = "VAPOR-WATER SURFACE TENSION";
            units[k] = "N/M";
            k++;
          }
        }

        if (thermoSystem.hasPhaseType("oil") && thermoSystem.hasPhaseType("aqueous")
            && acceptedFlash) {
          props[k][i][j] = thermoSystem.getInterphaseProperties().getSurfaceTension(
              thermoSystem.getPhaseNumberOfPhase("oil"),
              thermoSystem.getPhaseNumberOfPhase("aqueous"));
          names[k] = "LIQUID-WATER SURFACE TENSION";
          units[k] = "N/M";
          k++;
        } else {
          if (continuousDerivativesExtrapolation && (i >= 2 || j >= 2)) {
            if (i < 2) {
              props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                  / (temperatures[j - 1] - temperatures[j - 2])
                  * (temperatures[j] - temperatures[j - 1]);
            } else if (j < 2) {
              props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                  / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
            } else {
              props[k][i][j] = props[k][i - 1][j - 1]
                  + (props[k][i][j - 1] - props[k][i][j - 2])
                      / (temperatures[j - 1] - temperatures[j - 2])
                      * (temperatures[j] - temperatures[j - 1])
                  + (props[k][i - 1][j] - props[k][i - 2][j])
                      / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
            }
            props[k][i][j] = 20.0e-3;
            k++;
          } else {
            props[k][i][j] = 20.0e-3;
            names[k] = "LIQUID-WATER SURFACE TENSION";
            units[k] = "N/M";
            k++;
          }
        }
      }
    }
    logger.info("Finished TPflash...");
    if (thermoSystem.getPhase(0).hasComponent("water")) {
      thermoSystem.removeComponent("water");
    }
    bubP = calcBubP(temperatures);
    dewP = calcDewP(temperatures);
    // bubT = calcBubT(temperatures);
    logger.info("Finished creating arrays");
    BicubicInterpolatingFunction funcGasDens =
        interpolationFunc.interpolate(pressures, temperatures, props[0]);
    logger.info("interpolated value " + funcGasDens.value(40, 298.0));
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    logger.info("TC " + TC + " PC " + PC);
    for (int i = 0; i < pressures.length; i++) {
      thermoSystem.setPressure(pressures[i]);
      for (int j = 0; j < temperatures.length; j++) {
        logger.info("pressure " + pressureLOG[i] + " temperature " + temperatureLOG[j]);
        // + " ROG " + ROG[i][j] + " ROL " + ROL[i][j]);
      }
    }
    writeOLGAinpFile(fileName);
  }

  /**
   * <p>
   * writeOLGAinpFile2.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   */
  public void writeOLGAinpFile2(String filename) {
    /*
     * try { writer = new BufferedWriter(new OutputStreamWriter( new
     * FileOutputStream("C:/Users/Kjetil Raul/Documents/Master KRB/javacode_ROG55.txt" ), "utf-8"));
     * writer.write("GAS DENSITY (KG/M3) = ("); for (int i = 0; i < pressures.length; i++) {
     * thermoSystem.setPressure(pressures[i]); for (int j = 0; j < temperatures.length; j++) {
     * thermoSystem.setTemperature(temperatures[j]); writer.write(ROG[i][j] + ","); } }
     * writer.write(")"); } catch (IOException ex) { // report } finally { try { } writer.close(); }
     * catch (Exception ex) { } }
     */
    try (Writer writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "utf-8"))) {
      writer.write("'WATER-OPTION ENTROPY NONEQ '" + "\n");

      writer.write(pressures.length + "   " + temperatures.length + "    " + RSWTOB + "\n");
      int Pcounter = 0;
      for (int i = 0; i < pressures.length; i++) {
        if (Pcounter > 4) {
          writer.write("\n");
          Pcounter = 0;
        }
        writer.write(pressureLOG[i] + "    ");
        Pcounter++;
      }
      writer.write("\n");

      int Tcounter = 0;
      for (int i = 0; i < temperatures.length; i++) {
        if (Tcounter > 4) {
          writer.write("\n");
          Tcounter = 0;
        }
        writer.write(temperatureLOG[i] + "    ");
        Tcounter++;
      }
      writer.write("\n");

      int bubPcounter = 0;
      for (int i = 0; i < temperatures.length; i++) {
        if (bubPcounter > 4) {
          writer.write("\n");
          bubPcounter = 0;
        }
        writer.write(bubPLOG[i] + "    ");
        bubPcounter++;
      }
      writer.write("\n");

      int dewPcounter = 0;
      for (int i = 0; i < temperatures.length; i++) {
        if (dewPcounter > 4) {
          writer.write("\n");
          dewPcounter = 0;
        }
        writer.write(dewPLOG[i] + "    ");
        dewPcounter++;
      }
      writer.write("\n");

      for (int k = 0; k < nProps; k++) {
        if (names[k] == null) {
          continue;
        }
        logger.info("Writing variable: " + names[k]);
        writer.write(names[k] + " (" + units[k] + ")\n");
        for (int i = 0; i < pressures.length; i++) {
          // thermoSystem.setPressure(pressures[i]);
          int counter = 0;
          for (int j = 0; j < temperatures.length; j++) {
            // thermoSystem.setTemperature(temperatures[j]);
            if (counter > 4) {
              writer.write("\n");
              counter = 0;
            }
            writer.write(props[k][i][j] + "    ");
            counter++;
          }
          writer.write("\n");
        }
      }
    } catch (IOException ex) {
      // report
    }
  }

  /**
   * <p>
   * writeOLGAinpFile.
   * </p>
   *
   * @param filename a {@link java.lang.String} object
   */
  public void writeOLGAinpFile(String filename) {
    /*
     * try { writer = new BufferedWriter(new OutputStreamWriter( new
     * FileOutputStream("C:/Users/Kjetil Raul/Documents/Master KRB/javacode_ROG55.txt" ), "utf-8"));
     * writer.write("GAS DENSITY (KG/M3) = ("); for (int i = 0; i < pressures.length; i++) {
     * thermoSystem.setPressure(pressures[i]); for (int j = 0; j < temperatures.length; j++) {
     * thermoSystem.setTemperature(temperatures[j]); writer.write(ROG[i][j] + ","); } }
     * writer.write(")"); } catch (IOException ex) { // report } finally { try { } writer.close(); }
     * catch (Exception ex) { } }
     */
    try (Writer writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "utf-8"))) {
      writer.write("'WATER-OPTION ENTROPY NONEQ '" + "\n");

      writer.write(pressures.length + "   " + temperatures.length + "    " + RSWTOB + "\n");
      int Pcounter = 0;
      for (int i = 0; i < pressures.length; i++) {
        if (Pcounter > 4) {
          writer.write("\n");
          Pcounter = 0;
        }
        writer.write(pressureLOG[i] + "    ");
        Pcounter++;
      }
      writer.write("\n");

      int Tcounter = 0;
      for (int i = 0; i < temperatures.length; i++) {
        if (Tcounter > 4) {
          writer.write("\n");
          Tcounter = 0;
        }
        writer.write(temperatureLOG[i] + "    ");
        Tcounter++;
      }
      writer.write("\n");

      int bubPcounter = 0;
      for (int i = 0; i < temperatures.length; i++) {
        if (bubPcounter > 4) {
          writer.write("\n");
          bubPcounter = 0;
        }
        writer.write(bubPLOG[i] + "    ");
        bubPcounter++;
      }
      writer.write("\n");

      int dewPcounter = 0;
      for (int i = 0; i < temperatures.length; i++) {
        if (dewPcounter > 4) {
          writer.write("\n");
          dewPcounter = 0;
        }
        writer.write(dewPLOG[i] + "    ");
        dewPcounter++;
      }
      writer.write("\n");

      writer.write("GAS DENSITY (KG/M3)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[0][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID DENSITY (KG/M3)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[9][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER DENSITY (KG/M3)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[18][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("DRHOG/DP (S2/M2)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[1][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("DRHOL/DP (S2/M2)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[10][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("DRHOWAT/DP (S2/M2)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[19][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("DRHOG/DT (KG/M3-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[2][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("DRHOL/DT (KG/M3-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[11][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("DRHOWAT/DT (KG/M3-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[20][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("GAS MASS FRACTION (-)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[3][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER VAPOR MASS FRACTION (-)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[17][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("GAS VISCOSITY (NS/M2)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[4][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID VISCOSITY (NS/M2)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[12][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER VISCOSITY (NS/M2)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[21][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("GAS HEAT CAPACITY (J/KG-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[5][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID HEAT CAPACITY (J/KG-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[13][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER HEAT CAPACITY (J/KG-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[22][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("GAS ENTHALPY (J/KG)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[6][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID ENTHALPY (J/KG)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[14][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER ENTHALPY (J/KG)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[23][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("GAS THERMAL CONDUCTIVITY (W/M-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[7][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID THERMAL CONDUCTIVITY (W/M-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[16][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER THERMAL CONDUCTIVITY (W/M-K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[25][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("VAPOR-LIQUID SURFACE TENSION (N/M)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[26][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("VAPOR-WATER SURFACE TENSION (N/M)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[27][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID-WATER SURFACE TENSION (N/M)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[28][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("GAS ENTROPY (J/KG/K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[8][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("LIQUID ENTROPY (J/KG/K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[15][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }

      writer.write("WATER ENTROPY (J/KG/K)" + "\n");
      for (int i = 0; i < pressures.length; i++) {
        int counter = 0;
        for (int j = 0; j < temperatures.length; j++) {
          if (counter > 4) {
            writer.write("\n");
            counter = 0;
          }
          writer.write(props[24][i][j] + "    ");
          counter++;
        }
        writer.write("\n");
      }
      /*
       * for (int k = 0; k < nProps; k++) { if (names[k] == null) { continue; }
       * logger.info("Writing variable: " + names[k]); writer.write(names[k] + " (" + units[k] +
       * ")\n"); for (int i = 0; i < pressures.length; i++) {
       * //thermoSystem.setPressure(pressures[i]); int counter = 0; for (int j = 0; j <
       * temperatures.length; j++) { // thermoSystem.setTemperature(temperatures[j]); if (counter >
       * 4) { writer.write("\n"); counter = 0; } writer.write(props[k][i][j] + "    "); counter++; }
       * writer.write("\n"); } }
       */
    } catch (IOException ex) {
      // report
    }
  }

  /**
   * <p>
   * extrapolateTable.
   * </p>
   */
  public void extrapolateTable() {
    for (int j = 0; j < temperatures.length; j++) {
      for (int i = 0; i < pressures.length; i++) {
        if (!hasValue[26][i][j]) {
        }
      }
    }
  }
}
