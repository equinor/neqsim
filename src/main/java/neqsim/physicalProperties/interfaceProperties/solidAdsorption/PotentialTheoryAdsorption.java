/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neqsim.physicalProperties.interfaceProperties.solidAdsorption;

import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class PotentialTheoryAdsorption implements AdsorptionInterface {

    private static final long serialVersionUID = 1000;

    transient SystemInterface system;
    double[] eps0;// = 7.458;//7.630; // J/mol
    double[] z0;// = 3.284;// * 1e-3; // m^3/kg
    double[] beta;// = 2.0;
    int integrationSteps = 500;
    double totalSurfaceExcess;
    double[][] compositionSurface, fugacityField;
    double[][] zField, epsField;
    double[] pressureField, surfaceExcess, surfaceExcessMolFraction, deltaz;
    String solidMaterial = "AC";

    public PotentialTheoryAdsorption() {
    }

    public PotentialTheoryAdsorption(SystemInterface system) {
        this.system = system;
        compositionSurface = new double[integrationSteps][system.getPhase(0).getNumberOfComponents()];
        pressureField = new double[integrationSteps];
        zField = new double[system.getPhase(0).getNumberOfComponents()][integrationSteps];
        epsField = new double[system.getPhase(0).getNumberOfComponents()][integrationSteps];
        fugacityField = new double[system.getPhase(0).getNumberOfComponents()][integrationSteps];
        deltaz = new double[system.getPhase(0).getNumberOfComponents()];

    }

    public void setSolidMaterial(String solidM) {
        solidMaterial = solidM;
    }

    public void calcAdorption(int phase) {
        SystemInterface tempSystem = (SystemInterface) system.clone();
        tempSystem.init(3);
        double[] bulkFug = new double[system.getPhase(phase).getNumberOfComponents()];
        double[] corrx = new double[system.getPhase(phase).getNumberOfComponents()];
        surfaceExcess = new double[system.getPhase(phase).getNumberOfComponents()];
        surfaceExcessMolFraction = new double[system.getPhase(phase).getNumberOfComponents()];

        eps0 = new double[system.getPhase(phase).getNumberOfComponents()];
        z0 = new double[system.getPhase(phase).getNumberOfComponents()];
        beta = new double[system.getPhase(phase).getNumberOfComponents()];

        readDBParameters();

        for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
            bulkFug[comp] = system.getPhase(phase).getComponent(comp).getx() * system.getPhase(phase).getComponent(comp).getFugasityCoefficient() * system.getPhase(phase).getPressure();
            deltaz[comp] = z0[comp] / (integrationSteps * 1.0);
            zField[comp][0] = z0[comp];
            for (int i = 0; i < integrationSteps; i++) {
                zField[comp][i] = zField[comp][0] - deltaz[comp] * i;
                epsField[comp][i] = eps0[comp] * Math.pow(Math.log(z0[comp] / zField[comp][i]), 1.0 / beta[comp]);
            }
        }

        for (int i = 0; i < integrationSteps; i++) 

        totalSurfaceExcess = 0.0;
        for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
            totalSurfaceExcess += surfaceExcess[comp];
            //    System.out.println("surface excess " + surfaceExcess[comp]);
        }
        for (int comp = 0; comp < system.getPhase(phase).getNumberOfComponents(); comp++) {
            surfaceExcessMolFraction[comp] = surfaceExcess[comp] / totalSurfaceExcess;
            System.out.println("surface excess molfrac " + surfaceExcessMolFraction[comp] + " mol/kg adsorbent " + surfaceExcess[comp]);
        }
        System.out.println("pressure " + tempSystem.getPressure());
        /*
        error = fugacityField[comp][i] - fugComp;
        System.out.println("error " + error);
        tempSystem.getPhase(phase).setPressure(tempSystem.getPhase(phase).getPressure() + error);
        tempSystem.init(3);
        System.out.println("i " + i + " fug " + fugacityField[comp][i]);
         * */
    }

    public void readDBParameters() {
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        for (int comp = 0; comp < system.getPhase(0).getNumberOfComponents(); comp++) {
            try {
                dataSet = database.getResultSet(("SELECT * FROM AdsorptionParameters WHERE name='" + system.getPhase(0).getComponent(comp).getComponentName() + "' AND Solid='" + solidMaterial + "'"));
                dataSet.next();

                eps0[comp] = Double.parseDouble(dataSet.getString("eps"));
                beta[comp] = Double.parseDouble(dataSet.getString("z0")) ;
                z0[comp] = Double.parseDouble(dataSet.getString("beta"));

                System.out.println("adsorption parameters read ok for " + system.getPhase(0).getComponent(comp).getComponentName() + " eps " + eps0[comp]);
            } catch (Exception e) {
                System.out.println("Component not found in adsorption DB " + system.getPhase(0).getComponent(comp).getComponentName() + " on solid " + solidMaterial);
                System.out.println("using default parameters");
                eps0[comp] = 7.2;
                beta[comp] = 2.0;
                z0[comp] = 3.2;
                // e.printStackTrace();
            } finally {
                try {
                    if (dataSet != null) {
                        dataSet.close();
                    }
                } catch (Exception e) {
                    System.out.println("error closing adsorption database.....");
                    e.printStackTrace();
                }

            }
        }
    }

    public double getSurfaceExess(int component) {
        return 1.0;
    }
}
