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

    @Override
    public Object clone();
}
