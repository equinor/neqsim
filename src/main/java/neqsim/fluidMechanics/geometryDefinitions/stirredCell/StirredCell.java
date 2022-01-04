package neqsim.fluidMechanics.geometryDefinitions.stirredCell;

import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinition;

/**
 * <p>
 * StirredCell class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class StirredCell extends GeometryDefinition {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for StirredCell.
     * </p>
     */
    public StirredCell() {}

    /**
     * <p>
     * Constructor for StirredCell.
     * </p>
     *
     * @param diameter a double
     */
    public StirredCell(double diameter) {
        super(diameter);
    }

    /**
     * <p>
     * Constructor for StirredCell.
     * </p>
     *
     * @param diameter a double
     * @param roughness a double
     */
    public StirredCell(double diameter, double roughness) {
        super(diameter, roughness);
    }

    /** {@inheritDoc} */
    @Override
    public void init() {
        super.init();
    }

    /** {@inheritDoc} */
    @Override
    public StirredCell clone() {
        StirredCell clonedPipe = null;
        try {
            clonedPipe = (StirredCell) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedPipe;
    }
}
