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
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */
package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author even solbraa
 * @version
 */
public class PVrefluxflash extends Flash implements java.io.Serializable {

    private static final long serialVersionUID = 1000;

    Flash tpFlash;
    int refluxPhase = 0;
    double refluxSpec = 0.5;

    /** Creates new PHflash */
    public PVrefluxflash() {
    }

    public PVrefluxflash(SystemInterface system, double refluxSpec, int refluxPhase) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.refluxSpec = refluxSpec;
        this.refluxPhase = refluxPhase;
    }

    @Override
	public void run() {
        // System.out.println("enthalpy: " + system.getEnthalpy());
        double err = 0;
        int iter = 0;
        double f_func = 0.0, f_func_old = 0.0, df_func_dt = 0, t_old = 0, t_oldold = 0.0;
        tpFlash.run();
        double dt = 1.0;
        do {
            iter++;

            f_func_old = f_func;
            t_oldold = t_old;
            t_old = system.getTemperature();

            f_func = refluxSpec - (1.0 / system.getBeta(refluxPhase) - 1.0);// system.getPhase(refluxPhase).getVolume()
                                                                            // / system.getVolume();
            df_func_dt = (f_func - f_func_old) / (t_old - t_oldold);

            err = Math.abs(f_func);

            if (iter < 4) {
                if (f_func > 0) {
                    system.setTemperature(system.getTemperature() + 0.1);
                } else if (f_func < 0) {
                    system.setTemperature(system.getTemperature() - 0.1);
                }
            } else {
                dt = f_func / df_func_dt;
                if (Math.abs(dt) > 2.0) {
                    dt = Math.signum(dt) * 2.0;
                }

                system.setTemperature(system.getTemperature() - dt * (1.0 * iter) / (iter + 50.0));
            }
            tpFlash.run();

            // System.out.println("temp " + system.getTemperature() + " err " + err + "
            // volfor " + system.getPhase(refluxPhase).getVolume() / system.getVolume());
        } while (Math.abs(dt) > 1e-8 && Math.abs(f_func) > 1e-6 && iter < 1000);

    }

    @Override
	public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
