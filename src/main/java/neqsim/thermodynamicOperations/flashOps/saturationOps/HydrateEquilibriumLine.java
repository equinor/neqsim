package neqsim.thermodynamicOperations.flashOps.saturationOps;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>HydrateEquilibriumLine class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class HydrateEquilibriumLine extends constantDutyTemperatureFlash {

    private static final long serialVersionUID = 1000;

    double[][] hydratePoints = null;
    double minPressure = 1.0, maxPressure = 200.0;
    int numberOfPoints = 10;

    /**
     * <p>Constructor for HydrateEquilibriumLine.</p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     * @param minPres a double
     * @param maxPres a double
     */
    public HydrateEquilibriumLine(SystemInterface system, double minPres, double maxPres) {
        super(system);
        minPressure = minPres;
        maxPressure = maxPres;
    }

    /** {@inheritDoc} */
    @Override
	public void run() {

        SystemInterface system = (SystemInterface) this.system.clone();
        hydratePoints = new double[2][numberOfPoints];
        system.setHydrateCheck(true);
        ThermodynamicOperations ops = new ThermodynamicOperations(system);

        system.setPressure(minPressure);
        double dp = (maxPressure - minPressure) / (numberOfPoints - 1.0);
        for (int i = 0; i < numberOfPoints; i++) {

            system.setPressure(minPressure + dp * i);
            try {
                ops.hydrateFormationTemperature();
            } catch (Exception e) {
                // logger.error("error",e);
            }
            hydratePoints[0][i] = system.getTemperature();
            hydratePoints[1][i] = system.getPressure();
            // system.display();

        }
    }

    /** {@inheritDoc} */
    @Override
	public double[][] getPoints(int i) {
        return hydratePoints;
    }

}
