package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 *
 * @author esol
 */
public class PackedSection extends SeparatorSection {
    private static final long serialVersionUID = 1000;

    public PackedSection(String type, Separator sep) {
        super(type, sep);
    }

    public PackedSection(String name, String type, Separator sep) {
        super(name, type, sep);
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
