package neqsim.processSimulation.processEquipment.separator.sectionType;

import neqsim.processSimulation.mechanicalDesign.separator.sectionType.DistillationTraySection;
import neqsim.processSimulation.processEquipment.separator.Separator;

/**
 * <p>ValveSection class.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ValveSection extends SeparatorSection {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for ValveSection.</p>
     *
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public ValveSection(String type, Separator sep) {
        super(type, sep);
        mechanicalDesign = new DistillationTraySection(this);
    }

    /**
     * <p>Constructor for ValveSection.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param type a {@link java.lang.String} object
     * @param sep a {@link neqsim.processSimulation.processEquipment.separator.Separator} object
     */
    public ValveSection(String name, String type, Separator sep) {
        this(type, sep);
        this.name = name;
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
