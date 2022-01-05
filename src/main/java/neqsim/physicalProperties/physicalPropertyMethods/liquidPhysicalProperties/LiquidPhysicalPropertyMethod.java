package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties;

/**
 * <p>
 * LiquidPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LiquidPhysicalPropertyMethod
        extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod {
    private static final long serialVersionUID = 1000;

    protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase;

    /**
     * <p>
     * Constructor for LiquidPhysicalPropertyMethod.
     * </p>
     */
    public LiquidPhysicalPropertyMethod() {
        super();
    }

    /**
     * <p>
     * Constructor for LiquidPhysicalPropertyMethod.
     * </p>
     *
     * @param liquidPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public LiquidPhysicalPropertyMethod(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super();
        this.liquidPhase = liquidPhase;
    }

    /** {@inheritDoc} */
    @Override
    public void setPhase(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        this.liquidPhase = phase;
    }
}
