/*
 * staticMixer.java
 *
 * Created on 11. mars 2001, 01:49
 */

package neqsim.processSimulation.processEquipment.mixer;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * StaticPhaseMixer class.
 * </p>
 *
 * @author Even Solbraa
 */
public class StaticPhaseMixer extends StaticMixer {

    private static final long serialVersionUID = 1000;

    public StaticPhaseMixer() {}

    /**
     * <p>
     * Constructor for StaticPhaseMixer.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public StaticPhaseMixer(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void mixStream() {
        int index = 0;
        String compName = new String();

        for (int k = 1; k < streams.size(); k++) {

            for (int i = 0; i < streams.get(k).getThermoSystem().getPhases()[0]
                    .getNumberOfComponents(); i++) {

                boolean gotComponent = false;
                String componentName =
                        streams.get(k).getThermoSystem().getPhases()[0].getComponents()[i]
                                .getName();
                System.out.println("adding: " + componentName);
                int numberOfPhases = streams.get(k).getThermoSystem().getNumberOfPhases();
                double[] moles = new double[numberOfPhases];
                int[] phaseType = new int[numberOfPhases];

                //
                // her maa man egentlig sjekke at phase typen er den samme !!! antar at begge er
                // to fase elle gass - tofase
                for (int p = 0; p < numberOfPhases; p++) {
                    moles[p] = streams.get(k).getThermoSystem().getPhase(p).getComponents()[i]
                            .getNumberOfMolesInPhase();
                    phaseType[p] = streams.get(k).getThermoSystem().getPhase(p).getPhaseType();
                }
                if (k == 1) {
                    phaseType[0] = 0;//
                    mixedStream.getThermoSystem().getPhase(1)
                            .setTemperature(streams.get(k).getThermoSystem().getTemperature());
                }

                for (int p = 0; p < mixedStream.getThermoSystem().getPhases()[0]
                        .getNumberOfComponents(); p++) {
                    if (mixedStream.getThermoSystem().getPhases()[0].getComponents()[p].getName()
                            .equals(componentName)) {
                        gotComponent = true;
                        index = streams.get(0).getThermoSystem().getPhases()[0].getComponents()[p]
                                .getComponentNumber();
                        compName =
                                streams.get(0).getThermoSystem().getPhases()[0].getComponents()[p]
                                        .getComponentName();

                    }
                }

                if (gotComponent) {
                    System.out.println("adding moles starting....");
                    for (int p = 0; p < numberOfPhases; p++) {
                        if (phaseType[p] == 0) {
                            System.out.println("adding liq");
                            mixedStream.getThermoSystem().addComponent(index, moles[p], 1);
                        } else if (phaseType[p] == 1) {
                            System.out.println("adding gas");
                            mixedStream.getThermoSystem().addComponent(index, moles[p], 0);
                        } else {
                            System.out.println("not here....");
                        }
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
        mixedStream.getThermoSystem().init(0);
        mixedStream.getThermoSystem().setBeta(1, 1e-10);
        mixedStream.getThermoSystem().init(2);
        mixedStream.getThermoSystem().reInitPhaseType();

        mixStream();

        mixedStream.getThermoSystem().init(3);

    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

}
