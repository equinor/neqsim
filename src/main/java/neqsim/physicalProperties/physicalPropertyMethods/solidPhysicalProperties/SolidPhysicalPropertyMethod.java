/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalProperties.physicalPropertyMethods.solidPhysicalProperties;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class SolidPhysicalPropertyMethod
        extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod {

    private static final long serialVersionUID = 1000;

    protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase;

    public SolidPhysicalPropertyMethod() {
        super();
    }

    public SolidPhysicalPropertyMethod(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        super();
        this.solidPhase = solidPhase;
    }

    @Override
	public void setPhase(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface solidPhase) {
        this.solidPhase = solidPhase;
    }
}
