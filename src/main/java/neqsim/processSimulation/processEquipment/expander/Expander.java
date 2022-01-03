/*
 * ThrottelValve.java
 *
 * Created on 22. august 2001, 17:20
 */
package neqsim.processSimulation.processEquipment.expander;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>Expander class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Expander extends Compressor implements ExpanderInterface {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new ThrottelValve
     */
    public Expander() {
        super();
    }

    /**
     * <p>Constructor for Expander.</p>
     *
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public Expander(StreamInterface inletStream) {
        super(inletStream);
    }

    /**
     * <p>Constructor for Expander.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public Expander(String name, StreamInterface inletStream) {
        super(name, inletStream);
    }

	/** {@inheritDoc} */
    @Override
	public void run() {
        // System.out.println("expander running..");
        thermoSystem = (SystemInterface) inletStream.getThermoSystem().clone();
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(getThermoSystem());
        thermoOps = new ThermodynamicOperations(thermoSystem);
        thermoSystem.init(3);
        double presinn = getThermoSystem().getPressure();
        double hinn = getThermoSystem().getEnthalpy();
        double densInn = getThermoSystem().getDensity();
        double entropy = getThermoSystem().getEntropy();
        inletEnthalpy = hinn;

        if (usePolytropicCalc) {
            int numbersteps = 40;
            double dp = (pressure - getThermoSystem().getPressure()) / (1.0 * numbersteps);
            for (int i = 0; i < numbersteps; i++) {
                entropy = getThermoSystem().getEntropy();
                hinn = getThermoSystem().getEnthalpy();
                getThermoSystem().setPressure(getThermoSystem().getPressure() + dp);
                thermoOps.PSflash(entropy);
                double hout = hinn + (getThermoSystem().getEnthalpy() - hinn) * polytropicEfficiency;
                thermoOps.PHflash(hout, 0);
            }
            /*
             * HYSYS method double oldPolyt = 10.5; int iter = 0; do {
             *
             *
             * iter++; double n = Math.log(thermoSystem.getPressure() / presinn) /
             * Math.log(thermoSystem.getDensity() / densInn); double k =
             * Math.log(thermoSystem.getPressure() / presinn) / Math.log(densOutIdeal /
             * densInn); double factor = ((Math.pow(thermoSystem.getPressure() / presinn, (n
             * - 1.0) / n) - 1.0) * (n / (n - 1.0)) * (k - 1) / k) /
             * (Math.pow(thermoSystem.getPressure() / presinn, (k - 1.0) / k) - 1.0);
             * oldPolyt = polytropicEfficiency; polytropicEfficiency = factor *
             * isentropicEfficiency; dH = thermoSystem.getEnthalpy() - hinn; hout = hinn +
             * dH / polytropicEfficiency; thermoOps.PHflash(hout, 0);
             * System.out.println(" factor " + factor + " n " + n + " k " + k +
             * " polytropic effici " + polytropicEfficiency + " iter " + iter);
             *
             * } while (Math.abs((oldPolyt - polytropicEfficiency) / oldPolyt) > 1e-5 &&
             * iter < 500); // polytropicEfficiency = isentropicEfficiency * ();
             *
             */
        } else {
            getThermoSystem().setPressure(pressure);

            // System.out.println("entropy inn.." + entropy);
            thermoOps.PSflash(entropy);
            double densOutIdeal = getThermoSystem().getDensity();
            if (!powerSet) {
                dH = (getThermoSystem().getEnthalpy() - hinn) * isentropicEfficiency;
            }
            double hout = hinn + dH;
            isentropicEfficiency = dH / (getThermoSystem().getEnthalpy() - hinn);
            // System.out.println("isentropicEfficiency.. " + isentropicEfficiency);
            dH = hout - hinn;
            thermoOps.PHflash(hout, 0);
        }
        // thermoSystem.display();
        outStream.setThermoSystem(getThermoSystem());
    }

}
