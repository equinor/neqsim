/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package neqsim.thermodynamicOperations.propertyGenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class OLGApropertyTableGeneratorWaterStudentsPH
        extends neqsim.thermodynamicOperations.BaseOperation {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(OLGApropertyTableGeneratorWaterStudentsPH.class);

    SystemInterface thermoSystem = null, gasSystem = null, oilSystem = null, waterSystem = null;
    ThermodynamicOperations thermoOps = null;
    double[] pressures, enthalpiesLOG, enthalpies, pressureLOG = null;
    double[] bubP, bubT, dewP, bubPLOG, dewPLOG;
    Matrix XMatrixgas, XMatrixoil, XMatrixwater;
    double[][] ROG = null;
    double maxPressure;
    double minPressure;
    double TLC, GLW, GL, GW; // TLC = Top left corner. GOW=Gas+Liquid+Water, GO = Gas+Liquid, GW =
                             // Gas+Water
    double VLS, VWS, LWS;; // VLS=Stop value for vapor-liqid surface tension. VWS=Stop value for
                           // vapor-water surface tension. LWS=Stop value for liquid-water surface
                           // tension
                           // double VLB, VLT, VWT, VWB, LWB, LWT;
                           // double[][] ROL, CPG, CPHL, HG, HHL, TCG, TCHL, VISG, VISHL, SIGGHL,
                           // SEG,
                           // SEHL, RS;
    double TC, PC;
    double RSWTOB;
    double[][][] props;
    int nProps;
    String[] names;
    Matrix[] xcoef = new Matrix[9];
    String[] units;
    int temperatureSteps, pressureSteps;
    boolean continuousDerivativesExtrapolation = true;
    boolean hasGasValues = false;
    boolean hasOilValues = false;
    boolean hasWaterValues = false;
    boolean[][][] hasValue;
    Matrix aMatrix = new Matrix(4, 4);
    Matrix s = new Matrix(1, 4);
    String fileName = "c:/Appl/OLGAneqsim.tab";

    public OLGApropertyTableGeneratorWaterStudentsPH(SystemInterface system) {
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

    public void setFileName(String name) {
        fileName = name;
    }

    public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
        pressures = new double[numberOfSteps];
        pressureLOG = new double[numberOfSteps];
        double step = (maxPressure - minPressure) / (numberOfSteps * 1.0 - 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            pressures[i] = minPressure + i * step;
            pressureLOG[i] = pressures[i] * 1e5;
        }
    }

    public void setEnthalpyRange(double minEnthalpy, double maxEnthalpy, int numberOfSteps) {
        enthalpies = new double[numberOfSteps];
        enthalpiesLOG = new double[numberOfSteps];
        double step = (maxEnthalpy - minEnthalpy) / (numberOfSteps * 1.0 - 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            enthalpies[i] = minEnthalpy + i * step;
            enthalpiesLOG[i] = enthalpies[i];
        }
    }

    public void calcPhaseEnvelope() {
        try {
            thermoOps.calcPTphaseEnvelope();
            TC = thermoSystem.getTC() - 273.15;
            PC = thermoSystem.getPC() * 1e5;
        } catch (Exception e) {
            logger.error("error", e);
        }
    }

    public double[] calcBubP(double[] enthalpies) {
        double[] bubP = new double[enthalpies.length];
        bubPLOG = new double[enthalpies.length];
        for (int i = 0; i < enthalpies.length; i++) {
            thermoSystem.setTemperature(enthalpies[i]);
            try {
                thermoOps.bubblePointPressureFlash(false);
                bubP[i] = thermoSystem.getPressure();
            } catch (Exception e) {
                logger.error("error", e);
                bubP[i] = 0;
                return bubP;
            }
            bubPLOG[i] = bubP[i] * 1e5;
        }
        return bubP;
    }

    public double[] calcDewP(double[] enthalpies) {
        double[] dewP = new double[enthalpies.length];
        dewPLOG = new double[enthalpies.length];
        for (int i = 0; i < enthalpies.length; i++) {
            thermoSystem.setTemperature(enthalpies[i]);
            try {
                thermoOps.dewPointPressureFlashHC();
                dewP[i] = thermoSystem.getPressure();
            } catch (Exception e) {
                logger.error("error", e);
                dewP[i] = 0;
                return dewP;
            }

            dewPLOG[i] = dewP[i] * 1e5;
        }
        return dewP;
    }

    public double[] calcBubT(double[] pressures) {
        double[] bubTemps = new double[pressures.length];
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            try {
                thermoOps.bubblePointTemperatureFlash();
                bubT[i] = thermoSystem.getPressure();
            } catch (Exception e) {
                logger.error("error", e);
                bubT[i] = 0.0;
            }
        }
        return bubTemps;
    }

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

    @Override
    @SuppressWarnings("empty-statement")
    public void run() {
        calcRSWTOB();
        logger.info("RSWTOB " + RSWTOB);
        nProps = 29;
        props = new double[nProps][pressures.length][enthalpies.length];
        units = new String[nProps];
        names = new String[nProps];
        GLW = 0;
        GL = 0;
        GW = 0;
        VLS = 0;
        LWS = 0;
        VWS = 0;
        maxPressure = pressures[pressures.length - 1];
        minPressure = pressures[0];
        // maxTemperature = enthalpies[enthalpies.length - 1];
        // minTemperature = enthalpies[0];
        // thermoSystem.setTemperature(minTemperature);
        thermoSystem.setPressure(maxPressure);
        thermoOps.TPflash();
        if (thermoSystem.hasPhaseType("gas") && thermoSystem.hasPhaseType("aqueous")
                && thermoSystem.hasPhaseType("oil")) {
            GLW = 1;
        }
        if (thermoSystem.hasPhaseType("gas") && thermoSystem.hasPhaseType("aqueous")) {
            GW = 1;
        }
        if (thermoSystem.hasPhaseType("gas") && thermoSystem.hasPhaseType("oil")) {
            GL = 1;
        }

        int startGasTemperatures = 0, startLiquid = 0, startWater = 0;
        boolean acceptedFlash = true;
        for (int j = 0; j < enthalpies.length; j++) {
            // thermoSystem.setTemperature(enthalpies[j]);
            for (int i = 0; i < pressures.length; i++) {
                thermoSystem.setPressure(pressures[i]);
                try {
                    logger.info("PHflash... " + thermoSystem.getTemperature() + " pressure "
                            + thermoSystem.getPressure());
                    thermoOps.PHflash(enthalpies[j], 0);
                    logger.info(" temperature " + thermoSystem.getTemperature() + " enthalpy "
                            + enthalpies[j]);
                    thermoSystem.init(3);
                    thermoSystem.initPhysicalProperties();
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
                } else if (continuousDerivativesExtrapolation && hasGasValues) {
                    do {
                        /*
                         * if (j>1 && i>1) { props[k][i][j] = 0.5 * ((props[k][i][j - 1] +
                         * (props[k][i][j - 1] - props[k][i][j - 2]) / (enthalpies[j - 1] -
                         * enthalpies[j - 2]) * (enthalpies[j] - enthalpies[j - 1])) + (props[k][i -
                         * 1][j] + (props[k][i - 1][j] - props[k][i - 2][j]) / (pressures[i - 1] -
                         * pressures[i - 2]) * (pressures[i] - pressures[i - 1]))); if
                         * (names[k].equals("GAS MASS FRACTION") && props[k][i][j] < 0) {
                         * props[k][i][j] = 0; } }
                         */
                        /*
                         * if (j > 1) { props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] -
                         * props[k][i][j - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) *
                         * (enthalpies[j] - enthalpies[j - 1]); }
                         */
                        if (i > 1) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                            // } //else if (j < 2) {
                            // props[k][i][j] = 0;//props[k][i - 1][j] + (props[k][i - 1][j] -
                            // props[k][i -
                            // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                            // pressures[i
                            // - 1]);
                            // } else {
                            // props[k][i][j] = 0;//props[k][i - 1][j - 1] + (props[k][i][j - 1] -
                            // props[k][i][j - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) *
                            // (enthalpies[j] - enthalpies[j - 1]) + (props[k][i - 1][j] -
                            // props[k][i -
                            // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                            // pressures[i
                            // - 1]);
                            // double newTemp = pressures[i];
                            // double vall = xcoef[k].get(0, 0) + newTemp * (xcoef[k].get(1, 0) +
                            // newTemp *
                            // (xcoef[k].get(2, 0) + newTemp * xcoef[k].get(3, 0)));
                            // props[k][i][j] = vall;
                            // if(i>0 && props[k][i-1][j]>1e-10) props[k][i][j] =
                            // props[k][i-1][j]*pressures[i]/pressures[i-1];

                            if (names[k].equals("GAS MASS FRACTION") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("GAS DENSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0.1;
                            }
                            if (names[k].equals("GAS VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("GAS THERMAL CONDUCTIVITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }
                        if (j > 1) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (enthalpies[j - 1] - enthalpies[j - 2])
                                            * (enthalpies[j] - enthalpies[j - 1]);
                            if (names[k].equals("GAS MASS FRACTION") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("GAS MASS FRACTION") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("GAS DENSITY") && props[k][i][j] <= 0) {
                                props[k][i][j] = 0.1;
                            }
                            if (names[k].equals("GAS VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("GAS THERMAL CONDUCTIVITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("GAS HEAT CAPACITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }
                        k++;
                    } while (k < 9);// names[k] = "GAS DENSITY";
                    // units[k] = "KG/M3";
                } else if (false && !hasGasValues) {
                    startGasTemperatures = j;
                } else {
                    gasSystem.setTemperature(enthalpies[j]);
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

                    props[k][i][j] = 0.0;// thermoSystem.getPhase(phaseNumb).getBeta() *
                                         // thermoSystem.getPhase(phaseNumb).getMolarMass() /
                                         // thermoSystem.getMolarMass();
                    names[k] = "GAS MASS FRACTION";
                    units[k] = "-";
                    k++;

                    props[k][i][j] = gasSystem.getPhase(0).getPhysicalProperties().getViscosity();
                    names[k] = "GAS VISCOSITY";
                    units[k] = "NS/M2";
                    k++;

                    props[k][i][j] = gasSystem.getPhase(0).getCp()
                            / gasSystem.getPhase(0).getNumberOfMolesInPhase()
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

                    props[k][i][j] =
                            gasSystem.getPhase(0).getPhysicalProperties().getConductivity();
                    names[k] = "GAS THERMAL CONDUCTIVITY";
                    units[k] = "W/M-K";
                    k++;

                    props[k][i][j] = gasSystem.getPhase(0).getEntropy()
                            / gasSystem.getPhase(0).getNumberOfMolesInPhase()
                            / gasSystem.getPhase(0).getMolarMass();
                    names[k] = "GAS ENTROPY";
                    units[k] = "J/KG/K";
                    k++;
                }

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
                } else if (continuousDerivativesExtrapolation && hasOilValues) {
                    do {
                        // if (i>1) {
                        // props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i -
                        // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                        // pressures[i
                        // - 1]);
                        // }
                        // if (j>1 && i>1) {
                        // props[k][i][j] = 0.5 * ((props[k][i - 1][j] + (props[k][i - 1][j] -
                        // props[k][i - 2][j]) / (enthalpies[i - 1] - enthalpies[i - 2]) *
                        // (enthalpies[i] - enthalpies[i - 1])) + (props[k][i - 1][j] + (props[k][i
                        // -
                        // 1][j] - props[k][i - 2][j]) / (pressures[i - 1] - pressures[i - 2]) *
                        // (pressures[i] - pressures[i - 1])));
                        // }
                        /*
                         * if (j > 2) { props[k][i][j] = 0.5*((props[k][i][j - 1] + (props[k][i][j -
                         * 1] - props[k][i][j - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) *
                         * (enthalpies[j] - enthalpies[j - 1])) +(props[k][i][j - 1] +
                         * (props[k][i][j - 1] - props[k][i][j - 3]) / (enthalpies[j - 1] -
                         * enthalpies[j - 3]) * (enthalpies[j] - enthalpies[j - 1]))); }
                         */
                        if (j < 2 && i < 2) {
                            if (names[k].equals("LIQUID DENSITY") && props[k][i][j] <= 0) {
                                props[k][i][j] = 100;
                            }
                            if (names[k].equals("LIQUID DENSITY") && props[k][i][j] > 900) {
                                props[k][i][j] = 900;
                            }
                            if (names[k].equals("LIQUID VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("LIQUID THERMAL CONDUCTIVITY")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("LIQUID HEAT CAPACITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }
                        if (j > 1) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (enthalpies[j - 1] - enthalpies[j - 2])
                                            * (enthalpies[j] - enthalpies[j - 1]);
                            if (names[k].equals("LIQUID DENSITY") && props[k][i][j] <= 0) {
                                props[k][i][j] = 100;
                            }
                            if (names[k].equals("LIQUID DENSITY") && props[k][i][j] > 900) {
                                props[k][i][j] = 900;
                            }
                            if (names[k].equals("LIQUID VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("LIQUID THERMAL CONDUCTIVITY")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("LIQUID HEAT CAPACITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }
                        if (i > 1) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                            if (names[k].equals("LIQUID DENSITY") && props[k][i][j] <= 0) {
                                props[k][i][j] = 100;
                            }
                            if (names[k].equals("LIQUID DENSITY") && props[k][i][j] > 900) {
                                props[k][i][j] = 900;
                            }
                            if (names[k].equals("LIQUID VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("LIQUID THERMAL CONDUCTIVITY")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("LIQUID HEAT CAPACITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }

                        // if (j > 1 && TLC==3) {
                        // props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j
                        // -
                        // 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) * (enthalpies[j] -
                        // enthalpies[j
                        // - 1]);
                        // if (j>1) {
                        // props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j
                        // -
                        // 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) * (enthalpies[j] -
                        // enthalpies[j
                        // - 1]);
                        // } else if (j < 2) {
                        // props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i -
                        // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                        // pressures[i
                        // - 1]);
                        // } else {
                        // props[k][i][j] = props[k][i - 1][j - 1] + (props[k][i][j - 1] -
                        // props[k][i][j
                        // - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) * (enthalpies[j] -
                        // enthalpies[j - 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) /
                        // (pressures[i
                        // - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
                        // }
                        // props[k][i][j] = 0.0;*/
                        // }
                        k++;
                    } while (k < 17);// names[k] = "GAS DENSITY";
                    // units[k] = "KG/M3";
                } else {
                    oilSystem.setPhaseType(0, 0);
                    // oilSystem.setTemperature(enthalpies[j]);
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

                    props[k][i][j] = oilSystem.getPhase(0).getCp()
                            / oilSystem.getPhase(0).getNumberOfMolesInPhase()
                            / oilSystem.getPhase(0).getMolarMass();
                    names[k] = "LIQUID HEAT CAPACITY";
                    units[k] = "J/KG-K";
                    k++;

                    props[k][i][j] = oilSystem.getPhase(0).getTemperature() - 273.15;
                    names[k] = "LIQUID TEMPERATURE";
                    units[k] = "C";
                    k++;

                    props[k][i][j] = oilSystem.getPhase(0).getEntropy()
                            / oilSystem.getPhase(0).getNumberOfMolesInPhase()
                            / oilSystem.getPhase(0).getMolarMass();
                    names[k] = "LIQUID ENTROPY";
                    units[k] = "J/KG/K";
                    k++;

                    props[k][i][j] =
                            oilSystem.getPhase(0).getPhysicalProperties().getConductivity();
                    names[k] = "LIQUID THERMAL CONDUCTIVITY";
                    units[k] = "W/M-K";
                    k++;
                } // setOilProperties();
                  // set gas properties

                if (thermoSystem.hasPhaseType("aqueous") && acceptedFlash) {
                    int phaseNumb = thermoSystem.getPhaseNumberOfPhase("aqueous");
                    if (thermoSystem.hasPhaseType("gas")) {
                        props[k][i][j] = thermoSystem.getPhase("gas").getComponent("water").getx()
                                * thermoSystem.getPhase("gas").getComponent("water").getMolarMass()
                                / thermoSystem.getPhase("gas").getMolarMass();
                    }
                    if (thermoSystem.hasPhaseType("oil")) {
                        props[k][i][j] += thermoSystem.getPhase("oil").getComponent("water").getx()
                                * thermoSystem.getPhase("oil").getComponent("water").getMolarMass()
                                / thermoSystem.getPhase("oil").getMolarMass();
                    }
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

                    props[k][i][j] = thermoSystem.getPhase(phaseNumb).getTemperature() - 273.15;
                    names[k] = "WATER TEMPERATURE";
                    units[k] = "C";
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
                } else if (continuousDerivativesExtrapolation && hasWaterValues) {
                    do {
                        /*
                         * if (j > 2) { props[k][i][j] = 0.5*((props[k][i][j - 1] + (props[k][i][j -
                         * 1] - props[k][i][j - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) *
                         * (enthalpies[j] - enthalpies[j - 1])) +(props[k][i][j - 1] +
                         * (props[k][i][j - 1] - props[k][i][j - 3]) / (enthalpies[j - 1] -
                         * enthalpies[j - 3]) * (enthalpies[j] - enthalpies[j - 1]))); if
                         * (names[k].equals("WATER VAPOR MASS FRACTION") && props[k][i][j] > 1) {
                         * props[k][i][j] = 1; } }
                         */
                        if (j > 1) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (enthalpies[j - 1] - enthalpies[j - 2])
                                            * (enthalpies[j] - enthalpies[j - 1]);
                            if (names[k].equals("WATER VAPOR MASS FRACTION")
                                    && props[k][i][j] > 1) {
                                props[k][i][j] = 1;
                            }
                            if (names[k].equals("WATER DENSITY") && props[k][i][j] <= 0) {
                                props[k][i][j] = 1000;
                            }
                            if (names[k].equals("WATER VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("WATER THERMAL CONDUCTIVITY")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("WATER VAPOR MASS FRACTION")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("WATER HEAT CAPACITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }
                        /*
                         * if (j > 1 && TLC == 3) { props[k][i][j] = props[k][i][j-1] +
                         * (props[k][i][j-1] - props[k][i][j-2]) / (enthalpies[j - 1] - enthalpies[j
                         * - 2]) * (enthalpies[j] - enthalpies[j - 1]); if (j>1) { props[k][i][j] =
                         * props[k][i][j-1] + (props[k][i][j-1] - props[k][i][j-2]) / (enthalpies[j
                         * - 1] - enthalpies[j - 2]) * (enthalpies[j] - enthalpies[j - 1]); if
                         * (names[k].equals("WATER VAPOR MASS FRACTION") && props[k][i][j] > 1) {
                         * props[k][i][j] = 1; } }
                         */
                        if (i > 1) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                            if (names[k].equals("WATER VAPOR MASS FRACTION")
                                    && props[k][i][j] > 1) {
                                props[k][i][j] = 1;
                            }

                            if (names[k].equals("WATER DENSITY") && props[k][i][j] <= 0) {
                                props[k][i][j] = 1000;
                            }
                            if (names[k].equals("WATER VISCOSITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("WATER THERMAL CONDUCTIVITY")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("WATER VAPOR MASS FRACTION")
                                    && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                            if (names[k].equals("WATER HEAT CAPACITY") && props[k][i][j] < 0) {
                                props[k][i][j] = 0;
                            }
                        }

                        /*
                         * if (i < 2) { props[k][i][j] = props[k][i][j - 1] + (props[k][i][j - 1] -
                         * props[k][i][j - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) *
                         * (enthalpies[j] - enthalpies[j - 1]); } else if (j < 2) { props[k][i][j] =
                         * props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j]) /
                         * (pressures[i - 1] - pressures[i - 2]) * (pressures[i] - pressures[i -
                         * 1]); } else { props[k][i][j] = props[k][i - 1][j - 1] + (props[k][i][j -
                         * 1] - props[k][i][j - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) *
                         * (enthalpies[j] - enthalpies[j - 1]) + (props[k][i - 1][j] - props[k][i -
                         * 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                         * pressures[i - 1]); } props[k][i][j] = 0.0;
                         */
                        k++;
                    } while (k < 26);// names[k] = "GAS DENSITY";
                    // units[k] = "KG/M3";
                } else {
                    waterSystem.setTemperature(enthalpies[j]);
                    waterSystem.setPressure(pressures[i]);
                    waterSystem.setPhaseType(0, 0);
                    waterSystem.init(3);
                    waterSystem.initPhysicalProperties();

                    if (thermoSystem.getPhase(0).hasComponent("water")) {
                        props[k][i][j] = thermoSystem.getPhase(0).getComponent("water").getz()
                                * thermoSystem.getPhase(0).getComponent("water").getMolarMass()
                                / thermoSystem.getPhase(0).getMolarMass();;
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

                    props[k][i][j] = waterSystem.getPhase(0).getCp()
                            / waterSystem.getPhase(0).getNumberOfMolesInPhase()
                            / waterSystem.getPhase(0).getMolarMass();
                    names[k] = "WATER HEAT CAPACITY";
                    units[k] = "J/KG-K";
                    k++;

                    props[k][i][j] = waterSystem.getPhase(0).getTemperature();
                    names[k] = "WATER TEMPERATURE";
                    units[k] = "C";
                    k++;

                    props[k][i][j] = waterSystem.getPhase(0).getEntropy()
                            / waterSystem.getPhase(0).getNumberOfMolesInPhase()
                            / waterSystem.getPhase(0).getMolarMass();
                    names[k] = "WATER ENTROPY";
                    units[k] = "J/KG/K";
                    k++;

                    props[k][i][j] =
                            waterSystem.getPhase(0).getPhysicalProperties().getConductivity();
                    names[k] = "WATER THERMAL CONDUCTIVITY";
                    units[k] = "W/M-K";
                    k++;
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
                    if (continuousDerivativesExtrapolation && (i >= 2 || j >= 2)) {
                        if (VLS == 1) {
                            props[k][i][j] = 5.0e-3;
                            // k++;
                        }
                        // if (j>1 && thermoSystem.hasPhaseType("gas") && VLS==0 && acceptedFlash) {
                        if (j > 1 && (GLW == 1 || GL == 1) && VLS == 0) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (enthalpies[j - 1] - enthalpies[j - 2])
                                            * (enthalpies[j] - enthalpies[j - 1]);
                            /*
                             * if (names[k].equals("VAPOR-LIQUID SURFACE TENSION") && props[k][i][j]
                             * < 5.0e-3) { props[k][i][j] = 7.5e-3; VLS=1; } if
                             * (names[k].equals("VAPOR-LIQUID SURFACE TENSION") && props[k][i][j] >
                             * 30.0e-3) { props[k][i][j] = 20.0e-3; VLS=1; }
                             */
                            if (props[k][i][j] < 5.0e-3) {
                                props[k][i][j] = 5.0e-3;
                                VLS = 1;
                            }
                            if (props[k][i][j] > 1.1 * props[k][i][j - 1]) {
                                props[k][i][j] = props[k][i][j - 1];
                            }
                            if (props[k][i][j] < 0.9 * props[k][i][j - 1]) {
                                props[k][i][j] = props[k][i][j - 1];
                            }
                            // } else if (i>1 && thermoSystem.hasPhaseType("oil") && VLS == 0 &&
                            // acceptedFlash) {
                            // } else if (i > 1 && TLC < 3 && VLS==0) {
                        } else if (i > 1 && (GLW == 0 || GL == 0) && VLS == 0) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                            if (props[k][i][j] < 5.0e-3) {
                                props[k][i][j] = 5.0e-3;
                                VLS = 1;
                            }
                            if (props[k][i][j] > 1.1 * props[k][i - 1][j]) {
                                props[k][i][j] = props[k][i - 1][j];
                            }
                            if (props[k][i][j] < 0.9 * props[k][i - 1][j]) {
                                props[k][i][j] = props[k][i - 1][j];
                            }
                        } else if (j > 0 && i > 0) {
                            props[k][i][j] = 0.5 * (props[k][i - 1][j] + props[k][i][j - 1]);
                        } else if (j > 0) {
                            props[k][i][j] = props[k][i][j - 1];
                        } else if (i > 0) {
                            props[k][i][j] = props[k][i - 1][j];
                        } else {
                            // props[k][i][j] = props[k][i - 1][j - 1] + (props[k][i][j - 1] -
                            // props[k][i][j
                            // - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) * (enthalpies[j] -
                            // enthalpies[j - 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) /
                            // (pressures[i
                            // - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
                            props[k][i][j] = 10.0e-3;
                        }

                        // k++;
                    } else if (j > 0 && i > 0) {
                        props[k][i][j] = 0.5 * (props[k][i - 1][j] + props[k][i][j - 1]);
                        // k++;
                    } else if (j > 0) {
                        props[k][i][j] = props[k][i][j - 1];
                        // k++;
                    } else if (i > 0) {
                        props[k][i][j] = props[k][i - 1][j];
                        // k++;
                    } else {
                        props[k][i][j] = 10.0e-3;
                        names[k] = "VAPOR-LIQUID SURFACE TENSION";
                        units[k] = "N/M";
                        // k++;
                    }
                    k++;
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
                        if (VWS == 1) {
                            props[k][i][j] = 5.0e-3;
                            // k++;
                        }
                        // if (j>1 && thermoSystem.hasPhaseType("gas") && VWS == 0 && acceptedFlash)
                        // {
                        // if (j > 1 && TLC==3 && VWS==0) {
                        if (j > 1 && (GLW == 1 || GW == 1) && VWS == 0) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (enthalpies[j - 1] - enthalpies[j - 2])
                                            * (enthalpies[j] - enthalpies[j - 1]);
                            /*
                             * if (names[k].equals("VAPOR-WATER SURFACE TENSION") && props[k][i][j]
                             * < 10.0e-3) { props[k][i][j] = 35.0e-3; VWS=1; } if
                             * (names[k].equals("VAPOR-WATER SURFACE TENSION") && props[k][i][j] >
                             * 150.0e-3) { props[k][i][j] = 105.0e-3; VWS=1; }
                             */
                            if (props[k][i][j] < 5.0e-3) {
                                props[k][i][j] = 5.0e-3;
                                VWS = 1;
                            }
                            if (props[k][i][j] > 1.1 * props[k][i][j - 1]) {
                                props[k][i][j] = props[k][i][j - 1];
                            }
                            if (props[k][i][j] < 0.9 * props[k][i][j - 1]) {
                                props[k][i][j] = props[k][i][j - 1];
                            }
                            // } else if (j>1 && thermoSystem.hasPhaseType("aqueous") && VWS ==0 &&
                            // acceptedFlash) {
                            // } else if (i > 1 && TLC < 3 && VWS==0) {
                        } else if (i > 1 && (GLW == 0 || GL == 0) && VWS == 0) {
                            props[k][i][j] =
                                    props[k][i - 1][j] + (props[k][i - 1][j] - props[k][i - 2][j])
                                            / (pressures[i - 1] - pressures[i - 2])
                                            * (pressures[i] - pressures[i - 1]);
                            /*
                             * if (names[k].equals("VAPOR-WATER SURFACE TENSION") && props[k][i][j]
                             * < 10.0e-3) { props[k][i][j] = 35.0e-3; VWS=1; } if
                             * (names[k].equals("VAPOR-WATER SURFACE TENSION") && props[k][i][j] >
                             * 150.0e-3) { props[k][i][j] = 105.0e-3; VWS=1; }
                             */
                            if (props[k][i][j] < 5.0e-3) {
                                props[k][i][j] = 5.0e-3;
                                VWS = 1;
                            }
                            if (props[k][i][j] > 1.1 * props[k][i - 1][j]) {
                                props[k][i][j] = props[k][i - 1][j];
                            }
                            if (props[k][i][j] < 0.9 * props[k][i - 1][j]) {
                                props[k][i][j] = props[k][i - 1][j];
                            }
                        } else if (j > 0 && i > 0) {
                            props[k][i][j] = 0.5 * (props[k][i - 1][j] + props[k][i][j - 1]);
                        } else if (j > 0) {
                            props[k][i][j] = props[k][i][j - 1];
                        } else if (i > 0) {
                            props[k][i][j] = props[k][i - 1][j];
                        } else {
                            // props[k][i][j] = props[k][i - 1][j - 1] + (props[k][i][j - 1] -
                            // props[k][i][j
                            // - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) * (enthalpies[j] -
                            // enthalpies[j - 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) /
                            // (pressures[i
                            // - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
                            props[k][i][j] = 60.0e-3;
                        }

                        // k++;
                    } else if (j > 0 && i > 0) {
                        props[k][i][j] = 0.5 * (props[k][i - 1][j] + props[k][i][j - 1]);
                        // k++;
                    } else if (j > 0) {
                        props[k][i][j] = props[k][i][j - 1];
                        // k++;
                    } else if (i > 0) {
                        props[k][i][j] = props[k][i - 1][j];
                        // k++;
                    } else {
                        props[k][i][j] = 60.0e-3;
                        names[k] = "VAPOR-WATER SURFACE TENSION";
                        units[k] = "N/M";
                        // k++;
                    }
                    k++;
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
                        if (LWS == 1) {
                            props[k][i][j] = 5.0e-3;
                            // k++;
                        }
                        if (j > 1 && LWS == 0) {
                            // if (j > 1 && TLC==3 && LWS==0) {
                            // if (j>1 && LWS==0 && acceptedFlash) {
                            props[k][i][j] =
                                    props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 2])
                                            / (enthalpies[j - 1] - enthalpies[j - 2])
                                            * (enthalpies[j] - enthalpies[j - 1]);
                            /*
                             * if (j > 2) { props[k][i][j] = 0.5*((props[k][i][j - 1] +
                             * (props[k][i][j - 1] - props[k][i][j - 2]) / (enthalpies[j - 1] -
                             * enthalpies[j - 2]) * (enthalpies[j] - enthalpies[j - 1]))
                             * +(props[k][i][j - 1] + (props[k][i][j - 1] - props[k][i][j - 3]) /
                             * (enthalpies[j - 1] - enthalpies[j - 3]) * (enthalpies[j] -
                             * enthalpies[j - 1]))); if
                             * (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j] <
                             * 10.0e-3) { props[k][i][j] = 25.0e-3; LWS=1; }
                             * 
                             * if (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j]
                             * > 120.0e-3) { props[k][i][j] = 80.0e-3; LWS=1; } }
                             */
                            /*
                             * if (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j]
                             * < 10.0e-3) { props[k][i][j] = 25.0e-3; LWS=1; } if
                             * (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j] >
                             * 120.0e-3) { props[k][i][j] = 80.0e-3; LWS=1; }
                             */
                            if (props[k][i][j] < 5.0e-3) {
                                props[k][i][j] = 5.0e-3;
                                LWS = 1;
                            }
                            if (props[k][i][j] > 1.1 * props[k][i][j - 1]) {
                                props[k][i][j] = props[k][i][j - 1];
                            }
                            if (props[k][i][j] < 0.9 * props[k][i][j - 1]) {
                                props[k][i][j] = props[k][i][j - 1];
                            }
                            // } else if (i > 1 && LWS==0 && acceptedFlash) {
                            // } else if (i > 1 && TLC < 3 && LWS==0) {
                            // props[k][i][j] = props[k][i - 1][j] + (props[k][i - 1][j] -
                            // props[k][i -
                            // 2][j]) / (pressures[i - 1] - pressures[i - 2]) * (pressures[i] -
                            // pressures[i
                            // - 1]);
                            /*
                             * if (i > 2) { props[k][i][j] = 0.5*((props[k][i - 1][j] + (props[k][i
                             * - 1][j] - props[k][i - 2][j]) / (pressures[i - 1] - pressures[i - 2])
                             * * (pressures[i] - pressures[i - 1])) +(props[k][i - 1][j] +
                             * (props[k][i - 1][j] - props[k][i - 3][j]) / (pressures[i - 1] -
                             * pressures[i - 3]) * (pressures[i] - pressures[i - 1]))); if
                             * (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j] <
                             * 10.0e-3) { props[k][i][j] = 25.0e-3; LWS=1; }
                             * 
                             * if (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j]
                             * > 120.0e-3) { props[k][i][j] = 80.0e-3; LWS=1; } }
                             */
                            /*
                             * if (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j]
                             * < 10.0e-3) { props[k][i][j] = 25.0e-3; LWS=1; } if
                             * (names[k].equals("LIQUID-WATER SURFACE TENSION") && props[k][i][j] >
                             * 120.0e-3) { props[k][i][j] = 80.0e-3; LWS=1; }
                             */
                            /*
                             * if (props[k][i][j] < 5.0e-3) { props[k][i][j] = 5.0e-3; LWS = 1; } if
                             * (props[k][i][j] > 1.1* props[k][i - 1][j]) { props[k][i][j] =
                             * props[k][i - 1][j]; } if (props[k][i][j] < 0.9* props[k][i - 1][j]) {
                             * props[k][i][j] = props[k][i - 1][j]; }
                             */
                            // } else if (j >0 && i > 0) {
                            // props[k][i][j] = 0.5 * (props[k][i - 1][j] + props[k][i][j - 1]);
                        } else if (j > 0) {
                            props[k][i][j] = props[k][i][j - 1];
                        } else if (i > 0) {
                            props[k][i][j] = props[k][i - 1][j];
                        } else {
                            // props[k][i][j] = props[k][i - 1][j - 1] + (props[k][i][j - 1] -
                            // props[k][i][j
                            // - 2]) / (enthalpies[j - 1] - enthalpies[j - 2]) * (enthalpies[j] -
                            // enthalpies[j - 1]) + (props[k][i - 1][j] - props[k][i - 2][j]) /
                            // (pressures[i
                            // - 1] - pressures[i - 2]) * (pressures[i] - pressures[i - 1]);
                            props[k][i][j] = 40.0e-3;
                        }

                        // k++;
                        // } else if (j >0 && i > 0) {
                        // props[k][i][j] = 0.5 * (props[k][i - 1][j] + props[k][i][j - 1]);
                        // k++;
                    } else if (j > 0) {
                        props[k][i][j] = props[k][i][j - 1];
                        // k++;
                    } else if (i > 0) {
                        props[k][i][j] = props[k][i - 1][j];
                        // k++;
                    } else {
                        props[k][i][j] = 40.0e-3;
                        names[k] = "LIQUID-WATER SURFACE TENSION";
                        units[k] = "N/M";
                        // k++;
                    }
                    k++;
                }
            }
        }
        logger.info("Finished TPflash...");
        if (thermoSystem.getPhase(0).hasComponent("water")) {
            thermoSystem.removeComponent("water");
        }
        // bubP = calcBubP(enthalpies);
        // dewP = calcDewP(enthalpies);
        // bubT = calcBubT(enthalpies);
        logger.info("Finished creating arrays");
        // BicubicSplineInterpolatingFunction funcGasDens =
        // interpolationFunc.interpolate(pressures, enthalpies, props[0]);
        // logger.info("interpolated value " + funcGasDens.value(40, 298.0));
    }

    @Override
    public void displayResult() {
        logger.info("TC " + TC + " PC " + PC);
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            for (int j = 0; j < enthalpies.length; j++) {
                logger.info("pressure " + pressureLOG[i] + " enthalpy " + enthalpiesLOG[j]);// + "
                                                                                            // ROG "
                                                                                            // +
                                                                                            // ROG[i][j]
                                                                                            // + "
                                                                                            // ROL "
                                                                                            // +
                                                                                            // ROL[i][j]);
            }
        }
        writeOLGAinpFile(fileName);
    }

    public void writeOLGAinpFile2(String filename) {
        Writer writer = null;

        /*
         * try { writer = new BufferedWriter(new OutputStreamWriter( new
         * FileOutputStream("C:/Users/Kjetil Raul/Documents/Master KRB/javacode_ROG55.txt" ),
         * "utf-8")); writer.write("GAS DENSITY (KG/M3) = ("); for (int i = 0; i < pressures.length;
         * i++) { thermoSystem.setPressure(pressures[i]); for (int j = 0; j < enthalpies.length;
         * j++) { thermoSystem.setTemperature(enthalpies[j]); writer.write(ROG[i][j] + ","); } }
         * writer.write(")"); } catch (IOException ex) { // report } finally { try { }
         * writer.close(); } catch (Exception ex) { } }
         */
        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(fileName), "utf-8"));

            writer.write("'WATER-OPTION ENTROPY NONEQ '" + "\n");

            writer.write(pressures.length + "   " + enthalpies.length + "    " + RSWTOB + "\n");
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
            for (int i = 0; i < enthalpies.length; i++) {
                if (Tcounter > 4) {
                    writer.write("\n");
                    Tcounter = 0;
                }
                writer.write(enthalpiesLOG[i] + "    ");
                Tcounter++;
            }
            writer.write("\n");

            int bubPcounter = 0;
            for (int i = 0; i < enthalpies.length; i++) {
                if (bubPcounter > 4) {
                    writer.write("\n");
                    bubPcounter = 0;
                }
                writer.write(bubPLOG[i] + "    ");
                bubPcounter++;
            }
            writer.write("\n");

            int dewPcounter = 0;
            for (int i = 0; i < enthalpies.length; i++) {
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
                    for (int j = 0; j < enthalpies.length; j++) {
                        // thermoSystem.setTemperature(enthalpies[j]);
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
        } finally {
            try {
                writer.close();
            } catch (Exception ex) {
            }
        }
    }

    public void writeOLGAinpFile(String filename) {
        Writer writer = null;

        /*
         * try { writer = new BufferedWriter(new OutputStreamWriter( new
         * FileOutputStream("C:/Users/Kjetil Raul/Documents/Master KRB/javacode_ROG55.txt" ),
         * "utf-8")); writer.write("GAS DENSITY (KG/M3) = ("); for (int i = 0; i < pressures.length;
         * i++) { thermoSystem.setPressure(pressures[i]); for (int j = 0; j < enthalpies.length;
         * j++) { thermoSystem.setTemperature(enthalpies[j]); writer.write(ROG[i][j] + ","); } }
         * writer.write(")"); } catch (IOException ex) { // report } finally { try { }
         * writer.close(); } catch (Exception ex) { } }
         */
        try {
            writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(filename), "utf-8"));

            writer.write("'WATER-OPTION ENTROPY NONEQ '" + "\n");

            writer.write(pressures.length + "   " + enthalpies.length + "    " + RSWTOB + "\n");
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
            for (int i = 0; i < enthalpies.length; i++) {
                if (Tcounter > 4) {
                    writer.write("\n");
                    Tcounter = 0;
                }
                writer.write(enthalpiesLOG[i] + "    ");
                Tcounter++;
            }
            writer.write("\n");

            int bubPcounter = 0;
            for (int i = 0; i < enthalpies.length; i++) {
                if (bubPcounter > 4) {
                    writer.write("\n");
                    bubPcounter = 0;
                }
                writer.write(bubPLOG[i] + "    ");
                bubPcounter++;
            }
            writer.write("\n");

            int dewPcounter = 0;
            for (int i = 0; i < enthalpies.length; i++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
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
                for (int j = 0; j < enthalpies.length; j++) {
                    if (counter > 4) {
                        writer.write("\n");
                        counter = 0;
                    }
                    writer.write(props[24][i][j] + "    ");
                    counter++;
                }
                writer.write("\n");
            }

            writer.close();

            /*
             * for (int k = 0; k < nProps; k++) { if (names[k] == null) { continue; }
             * logger.info("Writing variable: " + names[k]); writer.write(names[k] + " (" + units[k]
             * + ")\n"); for (int i = 0; i < pressures.length; i++) {
             * //thermoSystem.setPressure(pressures[i]); int counter = 0; for (int j = 0; j <
             * enthalpies.length; j++) { // thermoSystem.setTemperature(enthalpies[j]); if (counter
             * > 4) { writer.write("\n"); counter = 0; } writer.write(props[k][i][j] + "    ");
             * counter++; } writer.write("\n"); } }
             */
        } catch (IOException ex) {
            // report
        } finally {
            try {
                writer.close();
            } catch (Exception ex) {
            }
        }
    }

    public void extrapolateTable() {
        for (int j = 0; j < enthalpies.length; j++) {
            for (int i = 0; i < pressures.length; i++) {
                if (!hasValue[26][i][j]) {
                }
            }
        }
    }
}
