

/*
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 * @author even solbraa
 * @version
 */
public class VHflash extends Flash {
    private static final long serialVersionUID = 1000;

    double Hspec = 0;
    double Vspec = 0;
    Flash pHFlash;

    /** Creates new PHflash */
    public VHflash() {}

    public VHflash(SystemInterface system, double Hspec, double Vspec) {
        this.system = system;
        this.pHFlash = new PHflash(system, Hspec, 0);
        this.Hspec = Hspec;
        this.Vspec = Vspec;
        // System.out.println("entalpy " + Hspec);
        // System.out.println("volume " + Vspec);
    }

    @Override
    public void run() {
        double oldVol = system.getVolume(), newVol = system.getVolume();
        double pNew = system.getPressure(), pOld = system.getPressure(), pOldOld = 0.0;
        double err = 0.0;
        int iterations = 0;
        // System.out.println("entalpy start " + system.getEnthalpy());
        double dPdV = 0.0;
        double wallHeat = 0.0;
        for (int i = 0; i < 1; i++) {
            wallHeat = 0 * i / 20.0 * 400.0 * 1295.0 * 1000.0 * (293.15 - system.getTemperature());
            // System.out.println("Hwall " + wallHeat + " i " + i);
            iterations = 1;
            do {
                iterations++;

                this.pHFlash = new PHflash(system, Hspec + wallHeat, 0);
                // System.out.println("Hspec " + Hspec);
                this.pHFlash.run();
                pOldOld = pOld;
                pOld = system.getPressure();
                oldVol = newVol;
                newVol = system.getVolume();

                err = (newVol - Vspec) / Vspec;

                // System.out.println("err................................................................................
                // " + err);
                if (iterations < -5) {
                    system.setPressure(system.getPressure() + err / 10.0);
                } else {
                    // System.out.println("pres " + (system.getPressure()+0.1*dPdV*(newVol-Vspec)));
                    system.setPressure(system.getPressure()
                            - 0.6 * 1.0 / system.getdVdPtn() * (newVol - Vspec));// system.getdVdPtn()*(newVol-Vspec));//dPdV*(newVol-Vspec));
                }
                pNew = system.getPressure();
                dPdV = (pOld - pOldOld) / (newVol - oldVol);
                // System.out.println("pressure " + system.getPressure());
            } while ((Math.abs(err) > 1e-10 && iterations < 1000) || iterations < 7);
        }
        // System.out.println("entalpy end " + system.getEnthalpy());
        // System.out.println("iterations " + iterations);
    }

    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
