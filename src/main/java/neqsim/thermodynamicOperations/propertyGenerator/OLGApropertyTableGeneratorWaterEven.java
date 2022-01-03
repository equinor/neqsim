package neqsim.thermodynamicOperations.propertyGenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>OLGApropertyTableGeneratorWaterEven class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class OLGApropertyTableGeneratorWaterEven
        extends neqsim.thermodynamicOperations.BaseOperation {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(OLGApropertyTableGeneratorWaterEven.class);

    SystemInterface thermoSystem = null;
    ThermodynamicOperations thermoOps = null;
    double[] pressures, temperatureLOG, temperatures, pressureLOG = null;
    double[] bubP, bubT, dewP, bubPLOG, dewPLOG;
    double[][] ROG = null;
    // double[][] ROL, CPG, CPHL, HG, HHL, TCG, TCHL, VISG, VISHL, SIGGHL, SEG,
    // SEHL, RS;
    double TC, PC;
    double RSWTOB;
    double[][][] props;
    int nProps;
    String[] names;
    String[] units;
    int temperatureSteps, pressureSteps;
    boolean continuousDerivativesExtrapolation = true;
    boolean hasGasValues = false;
    boolean hasOilValues = false;
    boolean hasWaterValues = false;
    boolean[][][] hasValue;
    String fileName = "c:/temp/OLGAneqsim.tab";

    /**
     * <p>Constructor for OLGApropertyTableGeneratorWaterEven.</p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public OLGApropertyTableGeneratorWaterEven(SystemInterface system) {
        this.thermoSystem = system;
        thermoOps = new ThermodynamicOperations(thermoSystem);

    }

    /**
     * <p>Setter for the field <code>fileName</code>.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setFileName(String name) {
        fileName = name;
    }

    /**
     * <p>setPressureRange.</p>
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
     * <p>setTemperatureRange.</p>
     *
     * @param minTemperature a double
     * @param maxTemperature a double
     * @param numberOfSteps a int
     */
    public void setTemperatureRange(double minTemperature, double maxTemperature,
            int numberOfSteps) {
        temperatures = new double[numberOfSteps];
        temperatureLOG = new double[numberOfSteps];
        double step = (maxTemperature - minTemperature) / (numberOfSteps * 1.0 - 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            temperatures[i] = minTemperature + i * step;
            temperatureLOG[i] = temperatures[i] - 273.15;
        }
    }

    /**
     * <p>calcPhaseEnvelope.</p>
     */
    public void calcPhaseEnvelope() {
        try {
            thermoOps.calcPTphaseEnvelope();
            TC = thermoSystem.getTC() - 273.15;
            PC = thermoSystem.getPC() * 1e5;
        } catch (Exception e) {
            logger.error("error", e);
        }
    }

    /**
     * <p>calcBubP.</p>
     *
     * @param temperatures an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public double[] calcBubP(double[] temperatures) {
        double[] bubP = new double[temperatures.length];
        bubPLOG = new double[temperatures.length];
        for (int i = 0; i < temperatures.length; i++) {

            thermoSystem.setTemperature(temperatures[i]);
            try {
                // thermoOps.bubblePointPressureFlash(false);
                bubP[i] = thermoSystem.getPressure();
                bubPLOG[i] = bubP[i] * 1e5;
            } catch (Exception e) {
                logger.error("error", e);
                bubP[i] = 0;
            }
        }
        return bubP;
    }

    /**
     * <p>calcDewP.</p>
     *
     * @param temperatures an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public double[] calcDewP(double[] temperatures) {
        double[] dewP = new double[temperatures.length];
        dewPLOG = new double[temperatures.length];
        for (int i = 0; i < temperatures.length; i++) {

            thermoSystem.setTemperature(temperatures[i]);
            try {
                // thermoOps.dewPointPressureFlashHC();
                dewP[i] = thermoSystem.getPressure();
                dewPLOG[i] = dewP[i] * 1e5;
            } catch (Exception e) {
                logger.error("error", e);
                dewP[i] = 0;
            }
        }
        return dewP;
    }

    /**
     * <p>calcBubT.</p>
     *
     * @param pressures an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public double[] calcBubT(double[] pressures) {
        double[] bubTemps = new double[pressures.length];
        for (int i = 0; i < pressures.length; i++) {

            thermoSystem.setPressure(pressures[i]);
            try {
                // thermoOps.bubblePointTemperatureFlash();
                bubT[i] = thermoSystem.getPressure();
            } catch (Exception e) {
                logger.error("error", e);
                bubT[i] = 0.0;
            }
        }
        return bubTemps;
    }

    /**
     * <p>initCalc.</p>
     */
    public void initCalc() {
        double stdTemp = 288.15, stdPres = 1.01325;
        double GOR, GLR, standgasdens, standliqdens, TC, PC;
        double molfracs[] = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        double MW[] = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        double dens[] = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        String components[] = new String[thermoSystem.getPhase(0).getNumberOfComponents()];

        for (int i = 0; i < molfracs.length; i++) {
            molfracs[i] = thermoSystem.getPhase(0).getComponent(i).getz();
            components[i] = thermoSystem.getPhase(0).getComponent(i).getComponentName();
            MW[i] = thermoSystem.getPhase(0).getComponent(i).getMolarMass() * 1000;
            dens[i] = thermoSystem.getPhase(0).getComponent(i).getNormalLiquidDensity();

        }

        thermoSystem.setTemperature(stdTemp);
        thermoSystem.setPressure(stdPres);

        thermoOps.TPflash();

        GOR = thermoSystem.getPhase(0).getTotalVolume() / thermoSystem.getPhase(1).getTotalVolume();
        GLR = thermoSystem.getPhase(0).getTotalVolume() / thermoSystem.getPhase(1).getTotalVolume();

    }

    /**
     * <p>calcRSWTOB.</p>
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

        int startGasTemperatures = 0, startLiquid = 0, startWater = 0;
        boolean acceptedFlash = true;
        for (int j = 0; j < temperatures.length; j++) {
            thermoSystem.setTemperature(temperatures[j]);
            for (int i = 0; i < pressures.length; i++) {
                thermoSystem.setPressure(pressures[i]);
                try {
                    // logger.info("TPflash... " + thermoSystem.getTemperature() + " pressure " +
                    // thermoSystem.getPressure());
                    thermoOps.TPflash();
                    // thermoSystem.createTable("test");
                    thermoSystem.init(3);
                    thermoSystem.initPhysicalProperties();
                    // if(thermoSystem.getPressure()>120){
                    // thermoSystem.display();
                    // }
                    // thermoSystem.display();
                    acceptedFlash = true;
                } catch (Exception e) {
                    acceptedFlash = false;
                    logger.info("fail temperature " + thermoSystem.getTemperature()
                            + " fail pressure " + thermoSystem.getPressure());

                    thermoSystem.display();
                    logger.error("error", e);
                }

                /*
                 * logger.info("water density " +
                 * thermoSystem.getPhase(2).getPhysicalProperties().getDensity());
                 * logger.info("RSW " + thermoSystem.getPhase(0).getComponent("water").getx() *
                 * thermoSystem.getPhase(0).getComponent("water").getMolarMass() /
                 * thermoSystem.getPhase(0).getMolarMass()); logger.info("surf tens oil-water  " +
                 * thermoSystem.getInterphaseProperties().getSurfaceTension(1, 2));
                 * logger.info("surf tens gas-water  " +
                 * thermoSystem.getInterphaseProperties().getSurfaceTension(0, 2));
                 */
                int k = 0;
                if (thermoSystem.hasPhaseType("gas") && acceptedFlash) {
                    int phaseNumb = thermoSystem.getPhaseNumberOfPhase("gas");

                    props[k][i][j] =
                            thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getDensity();
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

                    double beta = 0.0;
                    if (thermoSystem.hasPhaseType("oil")) {
                        props[k][i][j] = thermoSystem.getPhase(phaseNumb).getBeta()
                                * thermoSystem.getPhase(phaseNumb).getMolarMass()
                                / (thermoSystem.getPhase(phaseNumb).getBeta()
                                        * thermoSystem.getPhase(phaseNumb).getMolarMass()
                                        + thermoSystem.getPhase("oil").getBeta()
                                                * thermoSystem.getPhase("oil").getMolarMass());
                    } else {
                        props[k][i][j] = 1.0;// thermoSystem.getPhase(phaseNumb).getBeta() *
                                             // thermoSystem.getPhase(phaseNumb).getMolarMass() /
                                             // thermoSystem.getMolarMass();
                    }
                    names[k] = "GAS MASS FRACTION";
                    units[k] = "-";
                    k++;

                    props[k][i][j] =
                            thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getViscosity();
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

                    props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties()
                            .getConductivity();
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
                                props[k][i][j] = props[k][i - 1][j]
                                        + (props[k][i - 1][j] - props[k][i - 2][j])
                                                / (pressures[i - 1] - pressures[i - 2])
                                                * (pressures[i] - pressures[i - 1]);
                                // } //else if (j < 2) {
                                // props[k][i][j] = 0;//props[k][i - 1][j] + (props[k][i - 1][j] -
                                // props[k][i -
                                // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                                // pressures[i
                                // - 1]);
                                // } else {
                                // props[k][i][j] = 0;//props[k][i - 1][j - 1] + (props[k][i][j - 1]
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
                        } while (k < 9);// names[k] = "GAS DENSITY";
                        // units[k] = "KG/M3";
                    } else if (false && !hasGasValues) {
                        startGasTemperatures = j;
                    }
                }
                /*
                 * double[] gasVals = new double[9]; for (int kk = 0; kk < 9; kk++) { gasVals[kk] =
                 * props[kk][i][j]; } Matrix gasVector = new Matrix(gasVals, 1).transpose();
                 * XMatrixgas.setMatrix(0, 8, 0, 2, XMatrixgas.getMatrix(0, 8, 1, 3));
                 * XMatrixgas.setMatrix(0, 8, 3, 3, gasVector); if (i > 3) { for (int ii = 0; ii <
                 * 4; ii++) { aMatrix.set(ii, 0, 1.0); aMatrix.set(ii, 1, pressures[i - ii]);
                 * aMatrix.set(ii, 2, pressures[i - ii] * pressures[i - ii]); aMatrix.set(ii, 3,
                 * pressures[i - ii] * pressures[i - ii] * pressures[i - ii]); }
                 * 
                 * for (int jj = 0; jj < 9; jj++) { Matrix xg = XMatrixgas.getMatrix(jj, jj, 0, 3);
                 * 
                 * try { xcoef[jj] = aMatrix.solve(xg.transpose()); } catch (Exception e) {
                 * logger.error("error",e); } // logger.info("xcoef " + j); // xcoef.print(10, 10);
                 * //logger.info("dss: " +ds * dxds.get(speceq, 0)); // specVal = xcoef.get(0, 0) +
                 * sny * (xcoef.get(1, 0) + sny * (xcoef.get(2, 0) + sny * xcoef.get(3, 0))); //
                 * logger.info("vall" + vall); } }
                 */
                if (thermoSystem.hasPhaseType("oil") && acceptedFlash) {

                    int phaseNumb = thermoSystem.getPhaseNumberOfPhase("oil");

                    props[k][i][j] =
                            thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getDensity();
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

                    props[k][i][j] =
                            thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getViscosity();
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

                    props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties()
                            .getConductivity();
                    names[k] = "LIQUID THERMAL CONDUCTIVITY";
                    units[k] = "W/M-K";
                    k++;
                    hasOilValues = true;
                } else {
                    if (continuousDerivativesExtrapolation && hasOilValues) {
                        do {
                            if (j > 1) {
                                props[k][i][j] = props[k][i][j - 1]
                                        + (props[k][i][j - 1] - props[k][i][j - 2])
                                                / (temperatures[j - 1] - temperatures[j - 2])
                                                * (temperatures[j] - temperatures[j - 1]);
                            }
                            /*
                             * if (i < 2) { props[k][i][j] = props[k][i][j - 1] + (props[k][i][j -
                             * 1] - props[k][i][j - 2]) / (temperatures[j - 1] - temperatures[j -
                             * 2]) * (temperatures[j] - temperatures[j - 1]); } else if (j < 2) {
                             * props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] -
                             * props[k][i - 2][j]) / (pressures[i - 1] - pressures[i - 2]) *
                             * (pressures[i] - pressures[i - 1]); } else { props[k][i][j] =
                             * props[k][i - 1][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2]) /
                             * (temperatures[j - 1] - temperatures[j - 2]) * (temperatures[j] -
                             * temperatures[j - 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) /
                             * (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i -
                             * 1]); } props[k][i][j] = 0.0;
                             */
                            k++;
                        } while (k < 17);// names[k] = "GAS DENSITY";
                        // units[k] = "KG/M3";
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

                    props[k][i][j] =
                            thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getDensity();
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

                    props[k][i][j] =
                            thermoSystem.getPhase(phaseNumb).getPhysicalProperties().getViscosity();
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

                    props[k][i][j] = thermoSystem.getPhase(phaseNumb).getPhysicalProperties()
                            .getConductivity();
                    names[k] = "WATER THERMAL CONDUCTIVITY";
                    units[k] = "W/M-K";
                    k++;
                    hasWaterValues = true;
                } else {
                    if (continuousDerivativesExtrapolation && hasWaterValues) {
                        do {
                            if (j > 1) {
                                props[k][i][j] = props[k][i][j - 1]
                                        + (props[k][i][j - 1] - props[k][i][j - 2])
                                                / (temperatures[j - 1] - temperatures[j - 2])
                                                * (temperatures[j] - temperatures[j - 1]);
                            }
                            /*
                             * if (i < 2) { props[k][i][j] = props[k][i][j - 1] + (props[k][i][j -
                             * 1] - props[k][i][j - 2]) / (temperatures[j - 1] - temperatures[j -
                             * 2]) * (temperatures[j] - temperatures[j - 1]); } else if (j < 2) {
                             * props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] -
                             * props[k][i - 2][j]) / (pressures[i - 1] - pressures[i - 2]) *
                             * (pressures[i] - pressures[i - 1]); } else { props[k][i][j] =
                             * props[k][i - 1][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2]) /
                             * (temperatures[j - 1] - temperatures[j - 2]) * (temperatures[j] -
                             * temperatures[j - 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) /
                             * (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i -
                             * 1]); } props[k][i][j] = 0.0;
                             */
                            k++;
                        } while (k < 26);// names[k] = "GAS DENSITY";
                        // units[k] = "KG/M3";
                    }
                }

                if (thermoSystem.hasPhaseType("gas") && thermoSystem.hasPhaseType("oil")
                        && acceptedFlash) {
                    props[k][i][j] = thermoSystem.getInterphaseProperties().getSurfaceTension(
                            thermoSystem.getPhaseNumberOfPhase("gas"),
                            thermoSystem.getPhaseNumberOfPhase("oil"));
                    names[k] = "VAPOR-LIQUID SURFACE TENSION";
                    units[k] = "N/M";
                    k++;
                } else {
                    if (continuousDerivativesExtrapolation) {// && (i >= 2 || j >= 2)) {
                        if (!thermoSystem.hasPhaseType("gas")) {
                            if (i > 2 && j >= 2) {
                                // props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] -
                                // props[k][i][j -
                                // 2]) / (temperatures[j - 1] - temperatures[j - 2]) *
                                // (temperatures[j] -
                                // temperatures[j - 1]);
                            }
                        } else {
                            // props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] -
                            // props[k][i -
                            // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                            // pressures[i
                            // - 1]);
                        }

                        /*
                         * else if (j < 2) { props[k][i][j] = props[k][i - 1][j] + (props[k][i -
                         * 1][j] - props[k][i - 2][j]) / (pressures[i - 1] - pressures[i - 2]) *
                         * (pressures[i] - pressures[i - 1]); } else { props[k][i][j] = props[k][i -
                         * 1][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2]) / (temperatures[j -
                         * 1] - temperatures[j - 2]) * (temperatures[j] - temperatures[j - 1]) +
                         * (props[k][i - 1][j] - props[k][i - 2][j]) / (pressures[i - 1] -
                         * pressures[i - 2]) * (pressures[i] - pressures[i - 1]); } props[k][i][j] =
                         * 0.0;
                         */
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
                    if (continuousDerivativesExtrapolation) {// && (i >= 2 || j >= 2)) {
                        if (!thermoSystem.hasPhaseType("gas")) {
                            if (j >= 2) {
                                // props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] -
                                // props[k][i][j -
                                // 2]) / (temperatures[j - 1] - temperatures[j - 2]) *
                                // (temperatures[j] -
                                // temperatures[j - 1]);
                            }
                        } else {
                            // props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] -
                            // props[k][i -
                            // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                            // pressures[i
                            // - 1]);
                        }

                        if (i < 2) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (temperatures[j - 1] - temperatures[j - 2])
                                            * (temperatures[j] - temperatures[j - 1]);
                        } else if (j < 2) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                        } else {
                            props[k][i][j] = props[k][i - 1][j - 1]
                                    + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (temperatures[j - 1] - temperatures[j - 2])
                                            * (temperatures[j] - temperatures[j - 1])
                                    + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                        }
                        props[k][i][j] = 60.0e-3;
                        // props[k][i][j] = 10.0e-3;
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
                    if (continuousDerivativesExtrapolation) {// && (i >= 2 || j >= 2)) {
                        if (i < 2) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (temperatures[j - 1] - temperatures[j - 2])
                                            * (temperatures[j] - temperatures[j - 1]);
                        } else if (j < 2) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                        } else {
                            props[k][i][j] = props[k][i - 1][j - 1]
                                    + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (temperatures[j - 1] - temperatures[j - 2])
                                            * (temperatures[j] - temperatures[j - 1])
                                    + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
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
    }

    /** {@inheritDoc} */
    @Override
    public void displayResult() {
        logger.info("TC " + TC + " PC " + PC);
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            for (int j = 0; j < temperatures.length; j++) {
                logger.info("pressure " + pressureLOG[i] + " temperature " + temperatureLOG[j]);// +
                                                                                                // "
                                                                                                // ROG
                                                                                                // "
                                                                                                // +
                                                                                                // ROG[i][j]
                                                                                                // +
                                                                                                // "
                                                                                                // ROL
                                                                                                // "
                                                                                                // +
                                                                                                // ROL[i][j]);
            }
        }
        writeOLGAinpFile("");
    }

    /**
     * <p>writeOLGAinpFile2.</p>
     *
     * @param filename a {@link java.lang.String} object
     */
    public void writeOLGAinpFile2(String filename) {

        /*
         * try { writer = new BufferedWriter(new OutputStreamWriter( new
         * FileOutputStream("C:/Users/Kjetil Raul/Documents/Master KRB/javacode_ROG55.txt" ),
         * "utf-8")); writer.write("GAS DENSITY (KG/M3) = ("); for (int i = 0; i < pressures.length;
         * i++) { thermoSystem.setPressure(pressures[i]); for (int j = 0; j < temperatures.length;
         * j++) { thermoSystem.setTemperature(temperatures[j]); writer.write(ROG[i][j] + ","); } }
         * writer.write(")"); } catch (IOException ex) { // report } finally { try { }
         * writer.close(); } catch (Exception ex) { } }
         */
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream("C:/temp/temp.tab"), "utf-8"))) {
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
     * <p>writeOLGAinpFile.</p>
     *
     * @param filename a {@link java.lang.String} object
     */
    public void writeOLGAinpFile(String filename) {
        /*
         * try { writer = new BufferedWriter(new OutputStreamWriter( new
         * FileOutputStream("C:/Users/Kjetil Raul/Documents/Master KRB/javacode_ROG55.txt" ),
         * "utf-8")); writer.write("GAS DENSITY (KG/M3) = ("); for (int i = 0; i < pressures.length;
         * i++) { thermoSystem.setPressure(pressures[i]); for (int j = 0; j < temperatures.length;
         * j++) { thermoSystem.setTemperature(temperatures[j]); writer.write(ROG[i][j] + ","); } }
         * writer.write(")"); } catch (IOException ex) { // report } finally { try { }
         * writer.close(); } catch (Exception ex) { } }
         */
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "utf-8"))) {
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
             * logger.info("Writing variable: " + names[k]); writer.write(names[k] + " (" + units[k]
             * + ")\n"); for (int i = 0; i < pressures.length; i++) {
             * //thermoSystem.setPressure(pressures[i]); int counter = 0; for (int j = 0; j <
             * temperatures.length; j++) { // thermoSystem.setTemperature(temperatures[j]); if
             * (counter > 4) { writer.write("\n"); counter = 0; } writer.write(props[k][i][j] +
             * "    "); counter++; } writer.write("\n"); } }
             */
        } catch (IOException ex) {
            // report
        }
    }

    /**
     * <p>extrapolateTable.</p>
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
