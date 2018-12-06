/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class LiquidPhysicalPropertyMethod extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod{

    private static final long serialVersionUID = 1000;
    
    transient protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase;
    
    public LiquidPhysicalPropertyMethod() {
        super();
    }
    
    public LiquidPhysicalPropertyMethod(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super();
        this.liquidPhase = liquidPhase;
    }
    
     public void setPhase(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase){
        this.liquidPhase = phase;
    }
}
