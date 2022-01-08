package neqsim.thermodynamicOperations.flashOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * PSFlashGERG2008 class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSFlashGERG2008 extends QfuncFlash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(PSFlashGERG2008.class);

    double Sspec = 0;
    Flash tpFlash;
    
    double entropy_GERG2008 = 0.0;
    double cP_GERG2008 = 0.0;

    /**
     * <p>
     * Constructor for PSFlashGERG2008.
     * </p>
     */
    public PSFlashGERG2008() {}

    /**
     * <p>
     * Constructor for PSFlashGERG2008.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param Sspec a double
     */
    public PSFlashGERG2008(SystemInterface system, double Sspec) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Sspec = Sspec;
        
        
    }

    /** {@inheritDoc} */
    @Override
    public double calcdQdTT() {
        // double cP1 = 0.0, cP2 = 0.0;

        if (system.getNumberOfPhases() == 1) {
            return -system.getPhase(0).getCp() / system.getTemperature();
        }

        double dQdTT = 0.0;
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
            dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
        }
        return dQdTT;
    }

    /** {@inheritDoc} */
    @Override
    public double calcdQdT() {
        double dQ = -system.getEntropy() + Sspec;
        return dQ;
    }

    /** {@inheritDoc} */
    @Override
    public double solveQ() {
        double oldTemp = system.getTemperature(), nyTemp = system.getTemperature();
        int iterations = 1;
        double error = 1.0, erorOld = 10.0e10;
        double factor = 0.8;

        boolean correctFactor = true;
        double newCorr = 1.0;
        double[] gergProps;
        do {
            if (error > erorOld && factor > 0.1 && correctFactor) {
                factor *= 0.5;
            } else if (error < erorOld && correctFactor) {
                factor = 1.0;
            }

            iterations++;
            oldTemp = system.getTemperature();
            system.init(2);
            gergProps = system.getPhase(0).getProperties_GERG2008();
            entropy_GERG2008 = gergProps[11]*system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
            cP_GERG2008 = gergProps[13]*system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
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
            erorOld = error;
            error = Math.abs(calcdQdT());// Math.abs((nyTemp - oldTemp) / (nyTemp));
            // if(error>erorOld) factor *= -1.0;
            // System.out.println("temp " + system.getTemperature() + " iter "+ iterations +
            // " error "+ error + " correction " + newCorr + " factor "+ factor);
            // newCorr = Math.abs(factor * calcdQdT() / calcdQdTT());
        } while (((error + erorOld) > 1e-8 || iterations < 3) && iterations < 200);
        return nyTemp;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        tpFlash.run();
        if(system.getNumberOfPhases()>1) {
        	logger.error("PSFlashGERG2008 only supprt single phase gas calculations");
        	return;
        }
	    solveQ();
	    return;
    }
}
