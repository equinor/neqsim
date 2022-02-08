package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * LumpingModel class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class LumpingModel implements java.io.Serializable {
    private static final long serialVersionUID = 1000;
    int numberOfLumpedComponents = 7, numberOfPseudocomponents = 7;
    private String[] lumpedComponentNames = null;
    double[] fractionOfHeavyEnd = null;
    String name = "";
    SystemInterface system = null;
    static Logger logger = LogManager.getLogger(LumpingModel.class);

    /**
     * <p>
     * Constructor for LumpingModel.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public LumpingModel(SystemInterface system) {
        this.system = system;
    }

    /**
     * StandardLumpingModel distributes the pseudo components into equal wwight frations starting
     * with the first TBP fraction in the fluid
     * 
     * @author Even Solbraa
     * @version 1.0
     */
    public class StandardLumpingModel
            implements LumpingModelInterface, Cloneable, java.io.Serializable {
        private static final long serialVersionUID = 1000;

        public StandardLumpingModel() {}

        @Override
        public void setNumberOfLumpedComponents(int lumpedNumb) {
            numberOfLumpedComponents = lumpedNumb;
        }

        @Override
        public int getNumberOfLumpedComponents() {
            return numberOfLumpedComponents;
        }

        @Override
        public void setNumberOfPseudoComponents(int lumpedNumb) {
            numberOfPseudocomponents = lumpedNumb;
        }

        @Override
        public int getNumberOfPseudoComponents() {
            return numberOfPseudocomponents;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getLumpedComponentName(int i) {
            return lumpedComponentNames[i];
        }

        @Override
        public String[] getLumpedComponentNames() {
            return lumpedComponentNames;
        }

        @Override
        public void generateLumpedComposition(Characterise charac) {
            numberOfLumpedComponents = numberOfPseudocomponents;
            lumpedComponentNames = new String[numberOfLumpedComponents];
            fractionOfHeavyEnd = new double[numberOfPseudocomponents];
            double[] zPlus = new double[numberOfPseudocomponents];
            // double[] MPlus = new double[numberOfPseudocomponents];

            double weightFrac = 0.0;
            double weightTot = 0.0;
            double molFracTot = 0.0;

            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if (system.getPhase(0).getComponent(i).isIsTBPfraction()
                        || system.getPhase(0).getComponent(i).isIsPlusFraction()) {
                    weightTot += system.getPhase(0).getComponent(i).getz()
                            * system.getPhase(0).getComponent(i).getMolarMass();
                    molFracTot += system.getPhase(0).getComponent(i).getz();
                }
            }

            double meanWeightFrac = weightTot / (numberOfPseudocomponents + 1e-10);
            int k = 0;
            // int firstPS = charac.getPlusFractionModel().getFirstTBPFractionNumber();
            double Maverage = 0.0, denstemp1 = 0.0, denstemp2 = 0.0;
            double totalNumberOfMoles = system.getNumberOfMoles();
            int i = 0;
            int added = 0;
            if (charac.getPlusFractionModel().hasPlusFraction()) {
                added++;
            }
            int pseudoNumber = 1;
            double accumulatedWeigthFrac = 0.0;

            for (int ii = 0; ii < system.getPhase(0).getNumberOfComponents() - added; ii++) {
                String name = system.getPhase(0).getComponent(ii).getComponentName();
                if (system.getPhase(0).getComponent(name).isIsTBPfraction()
                        || system.getPhase(0).getComponent(name).isIsPlusFraction()) {
                    Maverage += system.getPhase(0).getComponent(name).getz()
                            * system.getPhase(0).getComponent(name).getMolarMass();
                    weightFrac += system.getPhase(0).getComponent(name).getz()
                            * system.getPhase(0).getComponent(name).getMolarMass();
                    zPlus[k] += system.getPhase(0).getComponent(name).getz();
                    denstemp1 += system.getPhase(0).getComponent(name).getz()
                            * system.getPhase(0).getComponent(name).getMolarMass();
                    denstemp2 += system.getPhase(0).getComponent(name).getz()
                            * system.getPhase(0).getComponent(name).getMolarMass()
                            / system.getPhase(0).getComponent(name).getNormalLiquidDensity();
                    system.removeComponent(name);
                    // logger.info("removing component " + name);
                    ii--;
                }

                if (weightFrac >= meanWeightFrac) {
                    accumulatedWeigthFrac += weightFrac;
                    meanWeightFrac = (weightTot - accumulatedWeigthFrac)
                            / (numberOfPseudocomponents - pseudoNumber);
                    String addName = "PC" + Integer.toString(pseudoNumber++);
                    // String addName = (i == firstPS) ? "PC" + Integer.toString(firstPS) : "PC" +
                    // Integer.toString(firstPS) + "-" + Integer.toString(ii);
                    fractionOfHeavyEnd[k] = zPlus[k] / molFracTot;
                    lumpedComponentNames[pseudoNumber - 2] = addName;
                    system.addTBPfraction(addName, totalNumberOfMoles * zPlus[k],
                            Maverage / zPlus[k], denstemp1 / denstemp2);
                    denstemp1 = 0.0;
                    denstemp2 = 0.0;
                    weightFrac = 0.0;
                    Maverage = 0.0;
                    k++;
                    // firstPS = i + 1;

                    added++;
                }
            }

            for (i = charac.getPlusFractionModel().getFirstPlusFractionNumber(); i < charac
                    .getPlusFractionModel().getLastPlusFractionNumber(); i++) {
                Maverage += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                weightFrac += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                accumulatedWeigthFrac += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                zPlus[k] += charac.getPlusFractionModel().getZ()[i];
                denstemp1 += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                denstemp2 += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i]
                        / charac.getPlusFractionModel().getDens()[i];

                // System.out.println("weigth " + weightFrac + " i" + i);
                if ((weightFrac >= meanWeightFrac
                        && !((numberOfPseudocomponents - pseudoNumber) == 0))
                        || i == charac.getPlusFractionModel().getLastPlusFractionNumber() - 1) {
                    meanWeightFrac = (weightTot - accumulatedWeigthFrac)
                            / (numberOfPseudocomponents - pseudoNumber);
                    String addName = "PC" + Integer.toString(pseudoNumber++);
                    lumpedComponentNames[pseudoNumber - 2] = addName;
                    // System.out.println("adding " + addName);
                    fractionOfHeavyEnd[k] = zPlus[k] / molFracTot;
                    system.addTBPfraction(addName, totalNumberOfMoles * zPlus[k],
                            Maverage / zPlus[k], denstemp1 / denstemp2);
                    denstemp1 = 0.0;
                    denstemp2 = 0.0;
                    weightFrac = 0.0;
                    Maverage = 0.0;
                    k++;
                    // firstPS = i + 1;
                }
            }
            if (charac.getPlusFractionModel().hasPlusFraction()) {
                system.removeComponent(system.getPhase(0)
                        .getComponent(charac.getPlusFractionModel().getPlusComponentNumber())
                        .getName());
            }
        }

        @Override
        public double getFractionOfHeavyEnd(int i) {
            return fractionOfHeavyEnd[i];
        }
    }

    /**
     * PVTLumpingModel Model Object.
     * <P>
     * PVTLumpingModel
     * <P>
     * PVTLumpingModel distributes the pseudo components into equal wwight frations starting with
     * the plus fra
     * 
     * @author Even Solbraa
     * @version 1.0
     */
    public class PVTLumpingModel extends StandardLumpingModel {
        private static final long serialVersionUID = 1000;

        public PVTLumpingModel() {}

        @Override
        public void generateLumpedComposition(Characterise charac) {
            double weightFrac = 0.0;
            double weightTot = 0.0;
            double molFracTot = 0.0;

            int firstPlusFractionNumber = system.getCharacterization().getPlusFractionModel()
                    .getFirstPlusFractionNumber();
            int compNumberOfFirstComponentInPlusFraction =
                    system.getCharacterization().getPlusFractionModel().getPlusComponentNumber();
            int numberOfDefinedTBPcomponents = 0;

            for (int compNumb = 0; compNumb < firstPlusFractionNumber; compNumb++) {
                if (system.getPhase(0).hasComponent("C" + Integer.toString(compNumb) + "_PC")) {
                    numberOfDefinedTBPcomponents++;
                }
            }

            if ((numberOfPseudocomponents
                    - numberOfDefinedTBPcomponents) <= numberOfLumpedComponents)
                numberOfPseudocomponents = numberOfDefinedTBPcomponents + numberOfLumpedComponents;

            numberOfLumpedComponents = numberOfPseudocomponents - numberOfDefinedTBPcomponents;
            lumpedComponentNames = new String[numberOfLumpedComponents];
            fractionOfHeavyEnd = new double[numberOfLumpedComponents];
            double[] zPlus = new double[numberOfLumpedComponents];

            for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
                if ((system.getPhase(0).getComponent(i).isIsTBPfraction()
                        || system.getPhase(0).getComponent(i).isIsPlusFraction())
                        && (i >= compNumberOfFirstComponentInPlusFraction)) {
                    weightTot += system.getPhase(0).getComponent(i).getz()
                            * system.getPhase(0).getComponent(i).getMolarMass();
                    molFracTot += system.getPhase(0).getComponent(i).getz();
                }
            }

            double meanWeightFrac = weightTot / (numberOfLumpedComponents + 1e-10);
            int k = 0;
            double Maverage = 0.0, denstemp1 = 0.0, denstemp2 = 0.0;
            double totalNumberOfMoles = system.getNumberOfMoles();
            int i = 0;
            int pseudoNumber = 1;
            double accumulatedWeigthFrac = 0.0;

            int starti = charac.getPlusFractionModel().getFirstPlusFractionNumber();
            for (i = charac.getPlusFractionModel().getFirstPlusFractionNumber(); i < charac
                    .getPlusFractionModel().getLastPlusFractionNumber(); i++) {
                Maverage += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                weightFrac += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                accumulatedWeigthFrac += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                zPlus[k] += charac.getPlusFractionModel().getZ()[i];
                denstemp1 += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i];
                denstemp2 += charac.getPlusFractionModel().getZ()[i]
                        * charac.getPlusFractionModel().getM()[i]
                        / charac.getPlusFractionModel().getDens()[i];

                // System.out.println("weigth " + weightFrac + " i" + i);
                if ((weightFrac >= meanWeightFrac
                        && !((numberOfLumpedComponents - pseudoNumber) == 0))
                        || i == charac.getPlusFractionModel().getLastPlusFractionNumber() - 1) {
                    meanWeightFrac = (weightTot - accumulatedWeigthFrac)
                            / (numberOfLumpedComponents - pseudoNumber);
                    // String addName = "PC" + Integer.toString(pseudoNumber++);
                    pseudoNumber++;
                    String addName = "C" + Integer.toString(starti) + "-" + Integer.toString(i);
                    getLumpedComponentNames()[k] = addName;
                    // System.out.println("adding " + addName);
                    fractionOfHeavyEnd[k] = zPlus[k] / molFracTot;

                    system.addTBPfraction(addName, totalNumberOfMoles * zPlus[k],
                            Maverage / zPlus[k], denstemp1 / denstemp2);
                    denstemp1 = 0.0;
                    denstemp2 = 0.0;
                    weightFrac = 0.0;
                    Maverage = 0.0;
                    k++;
                    starti = i + 1;
                }
            }
            if (charac.getPlusFractionModel().hasPlusFraction()) {
                system.removeComponent(system.getPhase(0)
                        .getComponent(charac.getPlusFractionModel().getPlusComponentNumber())
                        .getName());
            }
        }

        @Override
        public double getFractionOfHeavyEnd(int i) {
            return fractionOfHeavyEnd[i];
        }
    }

    /**
     * <p>
     * getModel.
     * </p>
     *
     * @param modelName a {@link java.lang.String} object
     * @return a {@link neqsim.thermo.characterization.LumpingModelInterface} object
     */
    public LumpingModelInterface getModel(String modelName) {
        if (modelName.equals("PVTlumpingModel")) {
            return new PVTLumpingModel();
        } else
            return new StandardLumpingModel();
    }
}
