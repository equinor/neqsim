package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechVaneSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>
 * VaneSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class VaneSection extends SeparatorSection {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for VaneSection.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public VaneSection(String name, String type, Separator sep) {
        super(name, type, sep);
    }

    public MechVaneSection getMechanicalDesign() {
        return new MechVaneSection(this);
    }

    /** {@inheritDoc} */
    @Override
    public double calcEfficiency() {
        double gasLoadF = getSeparator().getGasLoadFactor();
        if (gasLoadF > 0.1) {
            return 0.1 / gasLoadF;
        } else {
            return 1.0;
        }
    }
}
