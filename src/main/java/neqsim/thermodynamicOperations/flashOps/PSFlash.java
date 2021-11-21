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
public class PSFlash extends QfuncFlash {

    private static final long serialVersionUID = 1000;

    double Sspec = 0;
    Flash tpFlash;
    int type = 0;

    /**
     * Creates new PHflash
     */
    public PSFlash() {}

    public PSFlash(SystemInterface system, double Sspec, int type) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Sspec = Sspec;
        this.type = type;
    }

    @Override
    public double calcdQdTT() {
        double cP1 = 0.0, cP2 = 0.0;

        if (system.getNumberOfPhases() == 1) {
            return -system.getPhase(0).getCp() / system.getTemperature();
        }

        double dQdTT = 0.0;
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
        }
        return dQdTT;
    }

    @Override
    public double calcdQdT() {
        double dQ = -system.getEntropy() + Sspec;
        return dQ;
    }

    @Override
    public double solveQ() {
        double oldTemp = system.getTemperature(), nyTemp = system.getTemperature();
        int iterations = 1;
        double error = 1.0, erorOld = 10.0e10;
        double factor = 0.8;

        boolean correctFactor = true;
        double newCorr = 1.0;
        do {
            if (error > erorOld && factor > 0.1 && correctFactor) {
                factor *= 0.5;
            } else if (error < erorOld && correctFactor) {
                factor = 1.0;
            }

            iterations++;
            oldTemp = system.getTemperature();
            system.init(2);
            newCorr = factor * calcdQdT() / calcdQdTT();
            nyTemp = oldTemp - newCorr;
            if (Math.abs(system.getTemperature() - nyTemp) > 10.0) {
                nyTemp = system.getTemperature()
                        - Math.signum(system.getTemperature() - nyTemp) * 10.0;
                correctFactor = false;
            } else if (nyTemp < 0) {
                nyTemp = Math.abs(system.getTemperature() - 10.0);
                correctFactor = false;
            } else if (Double.isNaN(nyTemp)) {
                nyTemp = oldTemp + 1.0;
                correctFactor = false;
            } else {
                correctFactor = true;
            }

            system.setTemperature(nyTemp);
            tpFlash.run();
            erorOld = error;
            error = Math.abs(calcdQdT());// Math.abs((nyTemp - oldTemp) / (nyTemp));
            // if(error>erorOld) factor *= -1.0;
            // System.out.println("temp " + system.getTemperature() + " iter "+ iterations +
            // " error "+ error + " correction " + newCorr + " factor "+ factor);
            // newCorr = Math.abs(factor * calcdQdT() / calcdQdTT());
        } while (((error + erorOld) > 1e-8 || iterations < 3) && iterations < 200);
        return nyTemp;
    }

    public void onPhaseSolve() {

    }

    @Override
    public void run() {
        tpFlash.run();

        if (type == 0) {
            solveQ();
        } else {
            sysNewtonRhapsonPHflash secondOrderSolver = new sysNewtonRhapsonPHflash(system, 2,
                    system.getPhases()[0].getNumberOfComponents(), 1);
            secondOrderSolver.setSpec(Sspec);
            secondOrderSolver.solve(1);
        }
        // System.out.println("Entropy: " + system.getEntropy());
        // System.out.println("Temperature: " + system.getTemperature());
    }
}
