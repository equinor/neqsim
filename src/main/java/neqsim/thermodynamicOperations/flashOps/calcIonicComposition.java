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
 * calcIonicComposition.java
 *
 * Created on 8. mars 2001, 10:56
 */
package neqsim.thermodynamicOperations.flashOps;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import neqsim.thermo.system.SystemInterface;

/**
 * @author  even solbraa
 * @version
 */
public class calcIonicComposition extends Flash {

    private static final long serialVersionUID = 1000;

    int phaseNumber;
    String[][] resultTable = null;

    /**
     * Creates new PHflash
     */
    public calcIonicComposition() {
    }

    public calcIonicComposition(SystemInterface system, int phase) {
        this.system = system;
        phaseNumber = phase;
    }

    @Override
    public void run() {
        resultTable = new String[system.getPhase(0).getNumberOfComponents() + 2][4];
        resultTable[0][0] = "Component";
        resultTable[0][1] = "mmol/kgSolvent";
        resultTable[0][2] = "mg/kgSolvent";
        resultTable[0][3] = "Act.Coef";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        DecimalFormat nf = new DecimalFormat();
        symbols.setDecimalSeparator('.');
        nf.setDecimalFormatSymbols(symbols);
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");
        int ionNumber = 0;
        for (int i = 0; i < system.getPhase(phaseNumber).getNumberOfComponents(); i++) {
            // if (system.getPhase(phaseNumber).getComponent(i).isIsIon()) {
            resultTable[ionNumber + 1][0] = system.getPhase(phaseNumber).getComponent(i).getComponentName();
            resultTable[ionNumber + 1][1] = Double
                    .valueOf(
                            nf.format(system.getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase()
                                    / (system.getPhase(phaseNumber).getComponent("water").getNumberOfMolesInPhase()
                                            * system.getPhase(phaseNumber).getComponent("water").getMolarMass())
                                    * 1000))
                    .toString();
            resultTable[ionNumber + 1][2] = Double
                    .valueOf(nf.format(system.getPhase(phaseNumber).getComponent(i).getNumberOfMolesInPhase()
                            * system.getPhase(phaseNumber).getComponent(i).getMolarMass()
                            / (system.getPhase(phaseNumber).getComponent("water").getNumberOfMolesInPhase()
                                    * system.getPhase(phaseNumber).getComponent("water").getMolarMass())
                            * 1e6))
                    .toString();
            resultTable[ionNumber + 1][3] = Double
                    .valueOf(nf.format(system.getPhase(phaseNumber).getActivityCoefficient(i,
                            system.getPhase(phaseNumber).getComponent("water").getComponentNumber())))
                    .toString();

            ionNumber++;
            // }
        }
    }

    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    @Override
    public String[][] getResultTable() {
        return resultTable;
    }
}
