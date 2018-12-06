/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalProperties.physicalPropertyMethods.commonPhasePhysicalProperties;

/**
 *
 * @author  Even Solbraa
 * @version
 */
public class CommonPhysicalPropertyMethod extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod{

    private static final long serialVersionUID = 1000;
    
    transient protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase;
    
    public CommonPhysicalPropertyMethod() {
        super();
    }
    
    public CommonPhysicalPropertyMethod(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        super();
        this.phase = phase;
      
    }
    
    public void setPhase(neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase){
        this.phase = phase;
    }
}
