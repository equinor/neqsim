package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechNozzleSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author esol
 */
public class NozzleSection extends SeparatorSection {

    private static final long serialVersionUID = 1000;

    public NozzleSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MechNozzleSection(this);
    }

    public NozzleSection(String name, String type, Separator sep) {
        this(type, sep);
        setName(name);
    }

    @Override
	public double calcEfficiency() {
        return 1.0;
    }
}
