/*
 * PhaseHydrate.java
 *
 * Created on 18. august 2001, 12:50
 */
package neqsim.thermo.phase;

import neqsim.thermo.component.ComponentHydrate;
import neqsim.thermo.component.ComponentHydrateGF;
import neqsim.thermo.component.ComponentHydratePVTsim;

/**
 *
 * @author esol
 * @version
 */
public class PhaseHydrate extends Phase {
    private static final long serialVersionUID = 1000;
    String hydrateModel = "PVTsimHydrateModel";

    /**
     * Creates new PhaseHydrate
     */
    public PhaseHydrate() {
        phaseTypeName = "hydrate";
    }

    public PhaseHydrate(String fluidModel) {
        if (fluidModel.isEmpty()) {
            hydrateModel = "PVTsimHydrateModel";
        } else if (fluidModel.equals("CPAs-SRK-EOS-statoil") || fluidModel.equals("CPAs-SRK-EOS")
                || fluidModel.equals("CPA-SRK-EOS")) {
            hydrateModel = "CPAHydrateModel";
        } else {
            hydrateModel = "PVTsimHydrateModel";
        }
    }

    @Override
    public PhaseHydrate clone() {
        PhaseHydrate clonedPhase = null;
        try {
            clonedPhase = (PhaseHydrate) super.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }

        return clonedPhase;
    }

    @Override
    public double molarVolume(double pressure, double temperature, double A, double B, int phase)
            throws neqsim.util.exception.IsNaNException,
            neqsim.util.exception.TooManyIterationsException {
        double sum = 1.0;
        int hydrateStructure = ((ComponentHydrate) getComponent(0)).getHydrateStructure();
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < numberOfComponents; i++) {
                sum += ((ComponentHydrate) getComponent(i)).getCavprwat(hydrateStructure, j)
                        * ((ComponentHydrate) getComponent(i)).calcYKI(hydrateStructure, j, this);
            }
        }
        return sum / (((ComponentHydrate) getComponent(0)).getMolarVolumeHydrate(hydrateStructure,
                temperature));
        // return 1.0;
    }

    @Override
    public void addcomponent(String componentName, double molesInPhase, double moles,
            int compNumber) {
        super.addcomponent(molesInPhase);
        // componentArray[compNumber] = new ComponentHydrateStatoil(componentName,
        // moles, molesInPhase, compNumber);
        if (hydrateModel.equals("CPAHydrateModel")) {
            componentArray[compNumber] =
                    new ComponentHydrateGF(componentName, moles, molesInPhase, compNumber);
            // System.out.println("hydrate model: CPA-EoS hydrate model selected");
        } else {
            componentArray[compNumber] =
                    new ComponentHydratePVTsim(componentName, moles, molesInPhase, compNumber);
            // System.out.println("hydrate model: standard PVTsim hydrate model selected");
        }
        // componentArray[compNumber] = new ComponentHydrateBallard(componentName,
        // moles, molesInPhase, compNumber);
        // componentArray[compNumber] = new ComponentHydratePVTsim(componentName, moles,
        // molesInPhase, compNumber);
    }

    @Override
    public void init(double totalNumberOfMoles, int numberOfComponents, int type, int phase,
            double beta) { // type = 0
                           // start
                           // init type
                           // =1 gi nye
                           // betingelser
        super.init(totalNumberOfMoles, numberOfComponents, type, phase, beta);
    }

    @Override
    public void resetMixingRule(int type) {}

    public void setSolidRefFluidPhase(PhaseInterface refPhase) {
        for (int i = 0; i < numberOfComponents; i++) {
            if (componentArray[i].getName().equals("water")) {
                ((ComponentHydrate) componentArray[i]).setSolidRefFluidPhase(refPhase);
            }
        }
    }
}
