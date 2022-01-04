/*
 * GasPhysicalProperties.java
 *
 * Created on 29. oktober 2000, 16:18
 */

package neqsim.physicalProperties.physicalPropertyMethods.gasPhysicalProperties;

/**
 * <p>
 * GasPhysicalPropertyMethod class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class GasPhysicalPropertyMethod
        extends neqsim.physicalProperties.physicalPropertyMethods.PhysicalPropertyMethod {
    private static final long serialVersionUID = 1000;

    protected neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase;
    public double binaryMolecularDiameter[][];
    public double binaryEnergyParameter[][];
    public double binaryMolecularMass[][];

    /**
     * <p>
     * Constructor for GasPhysicalPropertyMethod.
     * </p>
     */
    public GasPhysicalPropertyMethod() {
        super();
    }

    /**
     * <p>
     * Constructor for GasPhysicalPropertyMethod.
     * </p>
     *
     * @param gasPhase a
     *        {@link neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface}
     *        object
     */
    public GasPhysicalPropertyMethod(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface gasPhase) {
        super();
        this.gasPhase = gasPhase;
        binaryMolecularDiameter = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
                .getPhase().getNumberOfComponents()];
        binaryMolecularMass = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
                .getPhase().getNumberOfComponents()];
        binaryEnergyParameter = new double[gasPhase.getPhase().getNumberOfComponents()][gasPhase
                .getPhase().getNumberOfComponents()];

        for (int i = 0; i < gasPhase.getPhase().getNumberOfComponents(); i++) {
            for (int j = 0; j < gasPhase.getPhase().getNumberOfComponents(); j++) {
                binaryMolecularMass[i][j] = 2.0 * Math.pow(
                        (1.0 / gasPhase.getPhase().getComponents()[i].getMolarMass() / 1000.0 + 1.0
                                / gasPhase.getPhase().getComponents()[j].getMolarMass() / 1000.0),
                        -1.0);
                binaryMolecularDiameter[i][j] = (gasPhase.getPhase().getComponents()[i]
                        .getLennardJonesMolecularDiameter()
                        + gasPhase.getPhase().getComponents()[j].getLennardJonesMolecularDiameter())
                        / 2.0;
                binaryEnergyParameter[i][j] = Math.pow(gasPhase.getPhase().getComponents()[i]
                        .getLennardJonesEnergyParameter()
                        * gasPhase.getPhase().getComponents()[j].getLennardJonesEnergyParameter(),
                        0.5);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setPhase(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface phase) {
        this.gasPhase = phase;
    }
}
