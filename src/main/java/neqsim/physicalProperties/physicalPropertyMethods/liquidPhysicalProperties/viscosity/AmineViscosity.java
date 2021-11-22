package neqsim.physicalProperties.physicalPropertyMethods.liquidPhysicalProperties.viscosity;

/**
 *
 * @author Even Solbraa
 * @version Method was checked on 2.8.2001 - seems to be correct - Even Solbraa
 */
public class AmineViscosity extends Viscosity {
    private static final long serialVersionUID = 1000;

    /** Creates new Conductivity */
    public AmineViscosity() {}

    public AmineViscosity(
            neqsim.physicalProperties.physicalPropertySystem.PhysicalPropertiesInterface liquidPhase) {
        super(liquidPhase);
    }

    @Override
    public double calcViscosity() {
        super.calcViscosity();
        double wtFracA = liquidPhase.getPhase().getComponent("MDEA").getx()
                * liquidPhase.getPhase().getComponent("MDEA").getMolarMass()
                / liquidPhase.getPhase().getMolarMass();
        wtFracA += liquidPhase.getPhase().getComponent("MDEA+").getx()
                * liquidPhase.getPhase().getComponent("MDEA+").getMolarMass()
                / liquidPhase.getPhase().getMolarMass();
        double viscA = -12.197 - 8.905 * wtFracA;
        double viscB = 1438.717 + 4218.749 * wtFracA;
        double logviscosity = viscA + viscB / liquidPhase.getPhase().getTemperature();// //N-sek/m2
        // System.out.println("visc " + Math.exp(logviscosity));
        return Math.exp(logviscosity);
    }
}
