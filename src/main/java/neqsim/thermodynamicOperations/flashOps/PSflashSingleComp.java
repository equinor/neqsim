package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSflashSingleComp class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSflashSingleComp extends Flash {
    private static final long serialVersionUID = 1000;

    double Sspec = 0;

    /**
     * <p>
     * Constructor for PSflashSingleComp.
     * </p>
     */
    public PSflashSingleComp() {}

    /**
     * <p>
     * Constructor for PSflashSingleComp.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param Sspec a double
     * @param type a int
     */
    public PSflashSingleComp(SystemInterface system, double Sspec, int type) {
        this.system = system;
        this.Sspec = Sspec;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        neqsim.thermodynamicOperations.ThermodynamicOperations bubOps =
                new neqsim.thermodynamicOperations.ThermodynamicOperations(system);
        double initTemp = system.getTemperature();

        if (system.getPressure() < system.getPhase(0).getComponent(0).getPC()) {
            try {
                bubOps.TPflash();
                if (system.getPhase(0).getPhaseTypeName().equals("gas")) {
                    bubOps.dewPointTemperatureFlash();
                } else {
                    bubOps.bubblePointTemperatureFlash();
                }
            } catch (Exception e) {
                system.setTemperature(initTemp);
                logger.error("error", e);
            }
        } else {
            bubOps.PSflash2(Sspec);
            return;
        }

        system.init(3);
        double gasEntropy = system.getPhase(0).getEntropy()
                / system.getPhase(0).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();
        double liqEntropy = system.getPhase(1).getEntropy()
                / system.getPhase(1).getNumberOfMolesInPhase() * system.getTotalNumberOfMoles();

        if (Sspec < liqEntropy || Sspec > gasEntropy) {
            system.setTemperature(initTemp);
            bubOps.PSflash2(Sspec);
            return;
        }
        double beta = (Sspec - liqEntropy) / (gasEntropy - liqEntropy);
        system.setBeta(beta);
        system.init(3);
    }

    /** {@inheritDoc} */
    @Override
    public org.jfree.chart.JFreeChart getJFreeChart(String name) {
        return null;
    }
}
