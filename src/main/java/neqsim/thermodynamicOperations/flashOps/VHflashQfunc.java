package neqsim.thermodynamicOperations.flashOps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author even solbraa
 * @version
 */
public class VHflashQfunc extends Flash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(VHflashQfunc.class);

    double Vspec = 0, Hspec = 0.0;
    Flash tpFlash;

    public VHflashQfunc() {}

    public VHflashQfunc(SystemInterface system, double Vspec, double Hspec) {
        this.system = system;
        this.tpFlash = new TPflash(system);
        this.Vspec = Vspec;
        this.Hspec = Hspec;
    }

    public double calcdQdPP() {
        double dQdVV = (system.getVolume() - Vspec)
                / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature())
                + system.getPressure() * (system.getdVdPtn())
                        / (neqsim.thermo.ThermodynamicConstantsInterface.R
                                * system.getTemperature());
        return dQdVV;
    }

    public double calcdQdTT() {
        double dQdTT = -system.getCp()
                / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R)
                - calcdQdT() / system.getTemperature();
        return dQdTT;
    }

    public double calcdQdT() {
        double dQdT = (Hspec - system.getEnthalpy())
                / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R);
        return dQdT;
    }

    public double calcdQdP() {
        double dQdP = system.getPressure() * (system.getVolume() - Vspec)
                / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
        return dQdP;
    }

    public double solveQ() {
        double oldPres = system.getPressure(), nyPres = system.getPressure(),
                nyTemp = system.getTemperature(), oldTemp = system.getTemperature();
        double iterations = 1;
        // logger.info("Vspec: " + Vspec);
        // logger.info("Uspec: " + Uspec);
        do {
            iterations++;
            oldPres = nyPres;
            oldTemp = nyTemp;
            system.init(3);
            // logger.info("dQdP: " + calcdQdP());
            // logger.info("dQdT: " + calcdQdT());
            nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdP() / calcdQdPP();
            nyTemp = oldTemp - (iterations) / (iterations + 10.0) * calcdQdT() / calcdQdTT();
            // logger.info("volume: " + system.getVolume());
            // logger.info("inernaleng: " + system.getInternalEnergy());
            system.setPressure(nyPres);
            system.setTemperature(nyTemp);
            tpFlash.run();
            // logger.info("error1: " + Math.abs((nyPres - oldPres) / (nyPres)));
            // logger.info("error2: " + Math.abs((nyTemp - oldTemp) / (nyTemp)));
            // logger.info("inernaleng: " + system.getInternalEnergy());
        } while (Math.abs((nyPres - oldPres) / (nyPres))
                + Math.abs((nyTemp - oldTemp) / (nyTemp)) > 1e-9 && iterations < 1000);
        return nyPres;
    }

    @Override
    public void run() {
        tpFlash.run();
        // logger.info("internaleng: " + system.getInternalEnergy());
        // logger.info("volume: " + system.getVolume());
        solveQ();
    }

    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(273.15 + 55, 50.0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 31.0);
        testSystem.addComponent("ethane", 4.0);
        testSystem.addComponent("n-heptane", 0.2);
        testSystem.init(0);
        try {
            testOps.TPflash();
            testSystem.display();

            double energy = testSystem.getEnthalpy() * 1.1;
            double volume = testSystem.getVolume() * 0.9;

            testOps.VHflash(volume, energy);
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }
}
