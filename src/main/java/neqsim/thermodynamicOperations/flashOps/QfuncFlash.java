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

/*
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * @author even solbraa
 * @version
 */
public class QfuncFlash extends Flash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(QfuncFlash.class);

    double Hspec = 0;
    Flash tpFlash;
    int type = 0;

    /** Creates new PHflash */
    public QfuncFlash() {}

    public QfuncFlash(SystemInterface system, double Hspec, int type) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Hspec = Hspec;
        this.type = type;
    }

    public double calcdQdTT() {
        double dQdTT = -system.getTemperature() * system.getTemperature() * system.getCp();
        return dQdTT;
    }

    public double calcdQdT() {
        double dQ = system.getEnthalpy() - Hspec;
        return dQ;
    }

    public double solveQ() {
        double oldTemp = 1.0 / system.getTemperature(), nyTemp = 1.0 / system.getTemperature();
        double iterations = 1;
        do {
            iterations++;
            oldTemp = nyTemp;
            system.init(3);
            nyTemp = oldTemp - calcdQdT() / calcdQdTT();
            system.setTemperature(1.0 / nyTemp);
            tpFlash.run();
        } while (Math.abs((1.0 / nyTemp - 1.0 / oldTemp) / (1.0 / nyTemp)) > 1e-9
                && iterations < 1000);
        return 1.0 / nyTemp;
    }

    @Override
    public void run() {
        tpFlash.run();
        logger.info("entropy: " + system.getEntropy());
        sysNewtonRhapsonPHflash secondOrderSolver = new sysNewtonRhapsonPHflash(system, 2,
                system.getPhases()[0].getNumberOfComponents(), type);
        secondOrderSolver.setSpec(Hspec);
        secondOrderSolver.solve(1);
    }

    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
