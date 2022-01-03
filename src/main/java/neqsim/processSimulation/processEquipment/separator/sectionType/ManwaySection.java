package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechManwaySection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>ManwaySection class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ManwaySection extends SeparatorSection {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for ManwaySection.</p>
     *
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public ManwaySection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MechManwaySection(this);
    }

    /**
     * <p>Constructor for ManwaySection.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public ManwaySection(String name, String type, Separator sep) {
        this(type, sep);
        setName(name);
    }

    /** {@inheritDoc} */
    @Override
	public double calcEfficiency() {
        return 1.0;
    }
}
