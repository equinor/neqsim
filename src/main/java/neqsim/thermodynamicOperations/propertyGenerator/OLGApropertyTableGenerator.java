/*
 * Copyright 2018 ESOL.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.thermodynamicOperations.propertyGenerator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/**
 *
 * @author ESOL
 */
public class OLGApropertyTableGenerator extends neqsim.thermodynamicOperations.BaseOperation {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(OLGApropertyTableGenerator.class);

    SystemInterface thermoSystem = null;
    ThermodynamicOperations thermoOps = null;
    double[] pressures, temperatureLOG, temperatures, pressureLOG = null;
    double[][] ROG = null, DROGDP, DROHLDP, DROGDT, DROHLDT;
    double[][] ROL, CPG, CPHL, HG, HHL, TCG, TCHL, VISG, VISHL, SIGGHL, SEG, SEHL, RS;
    double TC, PC;

    public OLGApropertyTableGenerator(SystemInterface system) {
        this.thermoSystem = system;
        thermoOps = new ThermodynamicOperations(thermoSystem);

    }

    public void setPressureRange(double minPressure, double maxPressure, int numberOfSteps) {
        pressures = new double[numberOfSteps];
        pressureLOG = new double[numberOfSteps];
        double step = (maxPressure - minPressure) / (numberOfSteps * 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            pressures[i] = minPressure + i * step;
            pressureLOG[i] = pressures[i] * 1e5;
        }
    }

    public void setTemperatureRange(double minTemperature, double maxTemperature, int numberOfSteps) {
        temperatures = new double[numberOfSteps];
        temperatureLOG = new double[numberOfSteps];
        double step = (maxTemperature - minTemperature) / (numberOfSteps * 1.0);
        for (int i = 0; i < numberOfSteps; i++) {
            temperatures[i] = minTemperature + i * step;
            temperatureLOG[i] = temperatures[i] - 273.15;
        }
    }

    public void calcPhaseEnvelope() {
        try {
            thermoOps.calcPTphaseEnvelope();
            TC = thermoSystem.getTC() - 273.15;
            PC = thermoSystem.getPC() * 1e5;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//thermoOps.ge
    public void run() {

        //calcPhaseEnvelope();
        ROG = new double[pressures.length][temperatures.length];
        ROL = new double[pressures.length][temperatures.length];
        CPG = new double[pressures.length][temperatures.length];
        CPHL = new double[pressures.length][temperatures.length];
        HG = new double[pressures.length][temperatures.length];
        HHL = new double[pressures.length][temperatures.length];
        VISG = new double[pressures.length][temperatures.length];
        VISHL = new double[pressures.length][temperatures.length];
        TCG = new double[pressures.length][temperatures.length];
        TCHL = new double[pressures.length][temperatures.length];
        SIGGHL = new double[pressures.length][temperatures.length];
        SEG = new double[pressures.length][temperatures.length];
        SEHL = new double[pressures.length][temperatures.length];
        RS = new double[pressures.length][temperatures.length];
        DROGDP = new double[pressures.length][temperatures.length];
        DROHLDP = new double[pressures.length][temperatures.length];
        DROGDT = new double[pressures.length][temperatures.length];
        DROHLDT = new double[pressures.length][temperatures.length];
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            for (int j = 0; j < temperatures.length; j++) {
                thermoSystem.setTemperature(temperatures[j]);
                try {
                    thermoOps.TPflash();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                thermoSystem.init(3);
                thermoSystem.initPhysicalProperties();

                if (thermoSystem.hasPhaseType("gas")) {

                    // set gas properties
                }

                if (thermoSystem.hasPhaseType("oil")) {

                    // set oil properties
                }

                if (thermoSystem.hasPhaseType("aqueous")) {

                    // set aqueous properties
                }

                if (!thermoSystem.hasPhaseType("gas")) {
                    thermoSystem.setPhaseType("oil", 1);
                    thermoSystem.init(3);
                    thermoSystem.initPhysicalProperties();

                    //setGasProperties();
                    // set gas properties
                }

                if (!thermoSystem.hasPhaseType("oil")) {
                    thermoSystem.setPhaseType("gas", 1);
                    thermoSystem.init(3);
                    thermoSystem.initPhysicalProperties();

                    //setOilProperties();
                    // set gas properties
                }

                if (!thermoSystem.hasPhaseType("aqueous")) {
                    thermoSystem.setPhaseType(1, 1);
                    thermoSystem.init(3);
                    thermoSystem.initPhysicalProperties();

                    //setOilProperties();
                    // set gas properties
                }

                // set extropalated oil values   
                // set gas properties as it was liquid
                ROG[i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
                ROL[i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getDensity();
                DROGDP[i][j] = thermoSystem.getPhase(0).getdrhodP();
                DROHLDP[i][j] = thermoSystem.getPhase(1).getdrhodP();
                DROGDT[i][j] = thermoSystem.getPhase(0).getdrhodT();
                DROHLDT[i][j] = thermoSystem.getPhase(1).getdrhodT();
                CPG[i][j] = thermoSystem.getPhase(0).getCp();
                CPHL[i][j] = thermoSystem.getPhase(1).getCp();
                HG[i][j] = thermoSystem.getPhase(0).getEnthalpy();
                HHL[i][j] = thermoSystem.getPhase(1).getEnthalpy();
                TCG[i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getConductivity();
                TCHL[i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getConductivity();
                VISG[i][j] = thermoSystem.getPhase(0).getPhysicalProperties().getViscosity();
                VISHL[i][j] = thermoSystem.getPhase(1).getPhysicalProperties().getViscosity();
                SIGGHL[i][j] = thermoSystem.getInterphaseProperties().getSurfaceTension(0, 1);
                SEG[i][j] = thermoSystem.getPhase(0).getEntropy();
                SEHL[i][j] = thermoSystem.getPhase(1).getEntropy();
                RS[i][j] = thermoSystem.getPhase(0).getBeta();
            }
        }
    }

    public void displayResult() {
        logger.info("TC " + TC + " PC " + PC);
        for (int i = 0; i < pressures.length; i++) {
            thermoSystem.setPressure(pressures[i]);
            for (int j = 0; j < temperatures.length; j++) {
                logger.info("pressure " + pressureLOG[i] + " temperature " + temperatureLOG[j] + " ROG " + ROG[i][j] + " ROL " + ROL[i][j]);
            }
        }
        writeOLGAinpFile("");
    }

    public void writeOLGAinpFile(String filename) {
        Writer writer = null;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream("c:/temp/filename.txt"), "utf-8"));
            writer.write("PRESSURE= (");
            for (int i = 0; i < pressures.length; i++) {
                thermoSystem.setPressure(pressures[i]);
                for (int j = 0; j < temperatures.length; j++) {
                    thermoSystem.setTemperature(temperatures[j]);
                    writer.write(ROG[i][j] + ",");
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
}
