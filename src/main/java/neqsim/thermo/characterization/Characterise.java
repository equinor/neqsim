package neqsim.thermo.characterization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * @author esol
 */
public class Characterise implements java.io.Serializable, Cloneable {
    private static final long serialVersionUID = 1000;
    SystemInterface system = null;
    TBPCharacterize TBPCharacterise = null;
    private TBPModelInterface TBPfractionModel = null;
    private PlusFractionModel plusFractionModelSelector = null;
    private PlusFractionModelInterface plusFractionModel = null;
    private LumpingModelInterface lumpingModel = null;
    protected String TBPFractionModelName = "PedersenSRK";
    protected LumpingModel lumpingModelSelector = null;
    protected TBPfractionModel TBPfractionModelSelector;
    static Logger logger = LogManager.getLogger(Characterise.class);

    /**
     * Creates a new instance of TBPCharacterize
     */
    public Characterise() {}

    public Characterise(SystemInterface system) {
        this.system = system;

        TBPCharacterise = new neqsim.thermo.characterization.TBPCharacterize(system);

        TBPfractionModelSelector = new TBPfractionModel();
        TBPfractionModel = TBPfractionModelSelector.getModel("");

        lumpingModelSelector = new LumpingModel(system);
        lumpingModel = lumpingModelSelector.getModel("");

        plusFractionModelSelector = new PlusFractionModel(system);
        plusFractionModel = plusFractionModelSelector.getModel("");
    }

    public void setThermoSystem(SystemInterface system) {
        this.system = system;
    }

    @Override
    public Object clone() {
        Characterise clonedSystem = null;
        try {
            clonedSystem = (Characterise) super.clone();
            // clonedSystem.chemicalReactionOperations = (ChemicalReactionOperations)
            // chemicalReactionOperations.clone();
        } catch (Exception e) {
            logger.error("Cloning failed.", e);
        }
        return clonedSystem;
    }

    public TBPModelInterface getTBPModel() {
        return TBPfractionModel;
    }

    public void setTBPModel(String name) {
        TBPfractionModel = TBPfractionModelSelector.getModel(name);
    }

    public void setLumpingModel(String name) {
        lumpingModel = lumpingModelSelector.getModel(name);
    }

    public void setPlusFractionModel(String name) {
        plusFractionModel = plusFractionModelSelector.getModel(name);
    }

    public PlusFractionModelInterface getPlusFractionModel() {
        return plusFractionModel;
    }

    public LumpingModelInterface getLumpingModel() {
        return lumpingModel;
    }

    public void characterisePlusFraction() {
        system.init(0);
        if (plusFractionModel.hasPlusFraction()) {

            if (plusFractionModel.getMPlus() > plusFractionModel.getMaxPlusMolarMass()) {
                logger.error(
                        "plus fraction molar mass too heavy for " + plusFractionModel.getName());
                plusFractionModel = plusFractionModelSelector.getModel("heavyOil");
                logger.info("changing to " + plusFractionModel.getName());
            }
            plusFractionModel.characterizePlusFraction(TBPfractionModel);
            lumpingModel.generateLumpedComposition(this);
        }
    }

    /*
     *
     * public boolean addPlusFraction(int start, int end) { plusFractionModel = new
     * PlusCharacterize(system); if (TBPCharacterise.hasPlusFraction()) {
     * TBPCharacterise.groupTBPfractions(); TBPCharacterise.generateTBPFractions(); return true; }
     * else { System.out.println("not able to generate pluss fraction"); return false; } }
     *
     *
     * public boolean characterize2() { if (TBPCharacterise.groupTBPfractions()) {
     * TBPCharacterise.solve(); return true; } else { System.out.println("not able to generate pluss
     * fraction"); return false; } }
     *
     *
     */
}
