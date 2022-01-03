/*
 * StaticNeqMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.processSimulation.processEquipment.mixer;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>StaticNeqMixer class.</p>
 *
 * @author  Even Solbraa
 */
public class StaticNeqMixer extends StaticMixer {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new StaticNeqMixer
     */
    public StaticNeqMixer() {
    }

    /**
     * <p>Constructor for StaticNeqMixer.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public StaticNeqMixer(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void mixStream() {
        int index = 0;
        String compName = new String();

        for (int k = 1; k < streams.size(); k++) {

            for (int i = 0; i < streams.get(k).getThermoSystem().getPhases()[0].getNumberOfComponents(); i++) {

                boolean gotComponent = false;
                String componentName = streams.get(k).getThermoSystem().getPhases()[0].getComponents()[i].getName();
                System.out.println("adding: " + componentName);
                int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();
                double[] moles = new double[numberOfPhases];
                // her maa man egentlig sjekke at phase typen er den samme !!! antar at begge er
                // to fase elle gass - tofase
                for (int p = 0; p < numberOfPhases; p++) {
                    moles[p] = streams.get(k).getThermoSystem().getPhases()[p].getComponents()[i]
                            .getNumberOfMolesInPhase();
                }
                for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0].getNumberOfComponents(); p++) {
                    if (mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName()
                            .equals(componentName)) {
                        gotComponent = true;
                        index = streams.get(0).getThermoSystem().getPhases()[0].getComponents()[p].getComponentNumber();
                        compName = streams.get(0).getThermoSystem().getPhases()[0].getComponents()[p]
                                .getComponentName();

                    }
                }

                if (gotComponent) {
                    System.out.println("adding moles starting....");
                    for (int p = 0; p < numberOfPhases; p++) {
                        mixedStream.getThermoSystem().addComponent(index, moles[p], p);
                    }
                    System.out.println("adding moles finished");
                } else {
                    System.out.println("ikke gaa hit");
                    for (int p = 0; p < numberOfPhases; p++) {
                        mixedStream.getThermoSystem().addComponent(compName, moles[p], p);
                    }
                }
            }
        }
        mixedStream.getThermoSystem().init_x_y();
        mixedStream.getThermoSystem().initBeta();
        mixedStream.getThermoSystem().init(2);

    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        double enthalpy = 0.0;
        for (int k = 0; k < streams.size(); k++) {
            streams.get(k).getThermoSystem().init(3);
            enthalpy += streams.get(k).getThermoSystem().getEnthalpy();
        }

        mixedStream.setThermoSystem(((SystemInterface) streams.get(0).getThermoSystem().clone()));
        mixedStream.getThermoSystem().setNumberOfPhases(2);
        mixedStream.getThermoSystem().reInitPhaseType();
        mixStream();

        SystemInterface syst = (SystemInterface) mixedStream.getThermoSystem().clone();
        syst.setTemperature(streams.get(0).getThermoSystem().getTemperature());
        syst.setPressure(streams.get(0).getThermoSystem().getPressure());
        ThermodynamicOperations testOps = new ThermodynamicOperations(syst);
        testOps.PHflash(enthalpy, 0);
        System.out.println("temp " + syst.getTemperature());
        mixedStream.getThermoSystem().setTemperature(syst.getTemperature());
        mixedStream.getThermoSystem().init(3);
        // double enthalpy = calcMixStreamEnthalpy();
        // System.out.println("temp guess " + guessTemperature());
        // mixedStream.getThermoSystem().setTemperature(guessTemperature());
        // testOps = new ThermodynamicOperations(mixedStream.getThermoSystem());
        // testOps.TPflash();
        // testOps.PHflash(enthalpy, 0);
        // System.out.println("enthalpy: " +
        // mixedStream.getThermoSystem().getEnthalpy());
        // System.out.println("enthalpy: " + enthalpy);
        // System.out.println("temperature: " +
        // mixedStream.getThermoSystem().getTemperature());
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

}
