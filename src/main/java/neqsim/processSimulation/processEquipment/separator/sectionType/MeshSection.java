package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.MecMeshSection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author esol
 */
public class MeshSection extends SeparatorSection {
    private static final long serialVersionUID = 1000;

    public MeshSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new MecMeshSection(this);
    }

    public MeshSection(String name, String type, Separator sep) {
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
