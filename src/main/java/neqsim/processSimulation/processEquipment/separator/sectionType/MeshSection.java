package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MecMeshSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>
 * MeshSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class MeshSection extends SeparatorSection {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for MeshSection.
     * </p>
     *
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public MeshSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MecMeshSection(this);

    }

    /**
     * <p>
     * Constructor for MeshSection.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public MeshSection(String name, String type, Separator sep) {
        this(type, sep);
        setName(name);
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
