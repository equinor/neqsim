package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechNozzleSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>
 * NozzleSection class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class NozzleSection extends SeparatorSection {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for NozzleSection.
     * </p>
     *
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public NozzleSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MechNozzleSection(this);
    }

    /**
     * <p>
     * Constructor for NozzleSection.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public NozzleSection(String name, String type, Separator sep) {
        this(type, sep);
        setName(name);
    }

    /** {@inheritDoc} */
    @Override
    public double calcEfficiency() {
        return 1.0;
    }
}
