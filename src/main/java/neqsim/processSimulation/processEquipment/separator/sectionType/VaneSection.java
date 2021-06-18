package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MechVaneSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author esol
 */
public class VaneSection extends SeparatorSection {

    private static final long serialVersionUID = 1000;

    public VaneSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MechVaneSection(this);
    }

    public VaneSection(String name, String type, Separator sep) {
        this(type, sep);
        setName(name);
    }

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
