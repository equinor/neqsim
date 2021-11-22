
package neqsim.physicalProperties.interfaceProperties.surfaceTension;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * @brief Collection of general utility functions used by the gradient theory classes.
 * 
 * @author Olaf Trygve Berglihn <olaf.trygve.berglihn@sintef.no>
 */
public class GTSurfaceTensionUtils {
    private static final long serialVersionUID = 1000;

    private static final double Pa = 1e-5;
    private static final double m3 = 1e-5;

    /**
     * Calculate chemical potential, chemical potential derivative, and pressure.
     * 
     * Note that the volume/pressure units used in NeqSim renders the number density incorrect by a
     * factor of 1e5. When selecting the volume to unity to avoid caring about volume derivatives
     * when using number density as the free variable, the mole number input to NeqSIM must be
     * scaled by a factor 1e-5. The chemical potential is unaffected by this as it is a intensive
     * property. The chemical potential derivative will scale inversely proportional with the mole
     * numbers according to the Euler homogeneity of the Gibbs energy function.
     * 
     * @param[in] t Temperature [K]
     * @param[in] rho Number density [mol/m3]
     * @param[out] mu Chemical potential [J/mol]
     * @param[out] dmu_drho Derivatives of the chemical potential with respect to number density
     *             [J/mol^2]
     * @param[out] p Pressure [Pa]
     *
     */
    public static void mufun(SystemInterface sys, int ncomp, double t, double[] rho, double[] mu,
            double[][] dmu_drho, double[] p) {
        double v = 1.0;
        double n;
        int i, j;
        String compname;
        // MuValues retval;
        PhaseInterface phase;

        double[] nv = new double[ncomp];
        phase = sys.getPhase(0);
        sys.setTemperature(t);
        phase.setTotalVolume(v);

        for (i = 0; i < ncomp; i++) {
            double n0;
            ComponentInterface component;

            if (rho[i] < 0.) {
                throw new RuntimeException("Number density is negative.");
            }
            component = phase.getComponent(i);
            compname = component.getName();
            n = rho[i] * Pa;
            n0 = component.getNumberOfMolesInPhase();
            // System.out.println(n-n0);
            // this.sys.addComponent(compname, n - n0);
            nv[i] = n;
        }

        // Set the composition multiple times to overcome round off error
        // in NeqSIM due to n += (n_new - nold)
        // sys.setMolarComposition(nv);
        // sys.setMolarComposition(nv);
        // sys.setMolarComposition(nv);

        sys.setMolarFlowRates(nv);
        // sys.setMolarFlowRates(nv);
        // sys.setMolarFlowRates(nv);

        sys.init_x_y();
        sys.setBeta(1.0);
        sys.init(3);

        for (i = 0; i < ncomp; i++) {
            mu[i] = sys.getPhase(0).getComponent(i).getChemicalPotential(sys.getPhase(0));
            if (Double.isNaN(mu[i])) {
                throw new RuntimeException("Thermo returned NaN for chemical potential.");
            }
            for (j = 0; j < ncomp; j++) {
                dmu_drho[i][j] =
                        sys.getPhase(0).getComponent(i).getChemicalPotentialdNTV(j, sys.getPhase(0))
                                * Pa;
            }
        }
        p[0] = sys.getPhase(0).getPressure() / Pa;
    }

    private GTSurfaceTensionUtils() {}
}
