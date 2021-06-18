package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.DistillationTraySection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * @author esol
 */
public class ValveSection extends SeparatorSection {

    private static final long serialVersionUID = 1000;

    public ValveSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new DistillationTraySection(this);
    }

    public ValveSection(String name, String type, Separator sep) {
        this(type, sep);
        this.name = name;
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
