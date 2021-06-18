package neqsim.PVTsimulation.reservoirProperties;

/**
 * @author esol
 */
public class CompositionEstimation {

    private static final long serialVersionUID = 1000;

    double reservoirTemperature;
    double reservoirPressure;

    public CompositionEstimation(double reservoirTemperature, double reservoirPressure) {
        this.reservoirTemperature = reservoirTemperature;
        this.reservoirPressure = reservoirPressure;
    }

    // correltaion from Haaland et. al. 1999
    public double estimateH2Sconcentration() {
        return 5.0e7 * Math.exp(-6543.0 / reservoirTemperature);
    }

    // reservoir temperatur in Kelvin CO2concentration in molfraction
    public double estimateH2Sconcentration(double CO2concentration) {
        return Math.exp(11.7 - 4438.3 / reservoirTemperature + 0.7 * Math.log(CO2concentration * 100.0));
    }
}
