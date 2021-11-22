package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechManwaySection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author esol
 */
public class ManwaySection extends SeparatorSection {
    private static final long serialVersionUID = 1000;

    public ManwaySection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MechManwaySection(this);
    }

    public ManwaySection(String name, String type, Separator sep) {
        this(type, sep);
        setName(name);
    }

    @Override
    public double calcEfficiency() {
        return 1.0;
    }
}
