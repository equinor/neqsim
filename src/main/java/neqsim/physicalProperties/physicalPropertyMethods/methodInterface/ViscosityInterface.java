/*
 * PhysicalPropertiesInterface.java
 *
 * Created on 29. oktober 2000, 16:14
 */

package neqsim.physicalProperties.physicalPropertyMethods.methodInterface;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface ViscosityInterface
        extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethodInterface {
    public double calcViscosity();

    public double getPureComponentViscosity(int i);

    public Object clone();
}