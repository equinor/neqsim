package neqsim.thermo.component;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * ComponentHydrateGF class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ComponentHydrateGF extends ComponentHydrate {
    private static final long serialVersionUID = 1000;

    double Ak[][] = new double[2][2]; // [structure][cavitytype]
    double Bk[][] = new double[2][2]; // [structure][cavitytype]
    static Logger logger = LogManager.getLogger(ComponentHydrateGF.class);

    /**
     * <p>
     * Constructor for ComponentHydrateGF.
     * </p>
     */
    public ComponentHydrateGF() {}

    /**
     * <p>
     * Constructor for ComponentHydrateGF.
     * </p>
     *
     * @param component_name a {@link java.lang.String} object
     * @param moles a double
     * @param molesInPhase a double
     * @param compnumber a int
     */
    public ComponentHydrateGF(String component_name, double moles, double molesInPhase,
            int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);

        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        if (!component_name.equals("default")) {
            try {
                // System.out.println("reading GF hydrate parameters ..............");
                try {
                    dataSet = database.getResultSet(
                            ("SELECT * FROM comp WHERE name='" + component_name + "'"));
                    dataSet.next();
                    dataSet.close();
                } catch (Exception e) {
                    logger.info("no parameters in tempcomp -- trying comp.. " + component_name);
                    dataSet = database.getResultSet(
                            ("SELECT * FROM comp WHERE name='" + component_name + "'"));
                    dataSet.next();
                }
                Ak[0][0] = Double.parseDouble(dataSet.getString("A1_SmallGF"));
                Bk[0][0] = Double.parseDouble(dataSet.getString("B1_SmallGF"));
                Ak[0][1] = Double.parseDouble(dataSet.getString("A1_LargeGF"));
                Bk[0][1] = Double.parseDouble(dataSet.getString("B1_LargeGF"));

                Ak[1][0] = Double.parseDouble(dataSet.getString("A2_SmallGF"));
                Bk[1][0] = Double.parseDouble(dataSet.getString("B2_SmallGF"));
                Ak[1][1] = Double.parseDouble(dataSet.getString("A2_LargeGF"));
                Bk[1][1] = Double.parseDouble(dataSet.getString("B2_LargeGF"));
                dataSet.close();
            } catch (Exception e) {
                logger.error("error in ComponentHydrateGF", e);
            } finally {
                try {
                    dataSet.close();
                    database.getConnection().close();
                } catch (Exception e) {
                    logger.error("error closing database.....");
                    // e.printStackTrace();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public double fugcoef(PhaseInterface phase) {
        return fugcoef(phase, phase.getNumberOfComponents(), phase.getTemperature(),
                phase.getPressure());
    }

    /**
     * <p>
     * fugcoef2.
     * </p>
     *
     * @param phase a {@link neqsim.thermo.phase.PhaseInterface} object
     * @param numberOfComps a int
     * @param temp a double
     * @param pres a double
     * @return a double
     */
    public double fugcoef2(PhaseInterface phase, int numberOfComps, double temp, double pres) {
        // this is empty hydrate latice fugacity coefficient equation 8.9
        if (componentName.equals("water")) {
            // this is the empty hydrate fugacity devited by pressure (why??)
            double solvol = getMolarVolumeHydrate(hydrateStructure, temp);
            if (hydrateStructure == -1) {
                fugasityCoeffisient = getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
                        * Math.exp(solvol / (R * temp) * (pres
                                - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp))
                                * 1e5)
                        / pres;
            } else {
                double val = 0.0;
                double tempy = 1.0;

                for (int cavType = 0; cavType < 2; cavType++) {
                    tempy = 0.0;
                    for (int j = 0; j < phase.getNumberOfComponents(); j++) {
                        double tee = ((ComponentHydrate) phase.getComponent(j))
                                .calcYKI(hydrateStructure, cavType, phase);
                        tempy += tee;
                    }
                    val += getCavprwat()[hydrateStructure][cavType] * Math.log(1.0 - tempy);
                }
                // System.out.println("val " + Math.exp(val));
                /*
                 * System.out.println("fugacity " +
                 * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol
                 * / (R * temp) * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure,
                 * temp)) * 1e5) / pres)); System.out.println("temperature " + temp);
                 * System.out.println("pressure " + pres); System.out.println("vapor pressure " +
                 * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)));
                 * System.out.println("fugacity folas " +
                 * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol
                 * / (R * temp) * (pres - getEmptyHydrateStructureVapourPressure(hydrateStructure,
                 * temp)) * 1e5)));
                 */

                // System.out.println("pointing "
                // +(Math.exp(solvol/(R*temp)*((pres-getEmptyHydrateStructureVapourPressure(hydrateStruct,temp))*1e5))));
                fugasityCoeffisient = Math.exp(val)
                        * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
                        * Math.exp(solvol / (R * temp) * (pres
                                - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp))
                                * 1e5)
                        / pres;
                // System.out.println("fugcoef " + tempfugcoef + "structure " +
                // (hydrateStruct+1));

                // System.out.println("structure " + (hydrateStructure+1));
            }
        } else {
            fugasityCoeffisient = 1e50;
        }

        logFugasityCoeffisient = Math.log(fugasityCoeffisient);

        // System.out.println("reading fugacity coeffiicent calculation");

        return fugasityCoeffisient;
    }

    /** {@inheritDoc} */
    @Override
    public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres) {
        double maxFug = 1.0e100;
        int stableStructure = 0;
        if (hydrateStructure == -1) {
            stableStructure = -1;
            // fugasityCoeffisient = 1.02 *
            // getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) *
            // Math.exp(getMolarVolumeHydrate(hydrateStructure, temp) / (R * temp) * (pres -
            // getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5) /
            // pres;

            refPhase.setTemperature(temp);
            refPhase.setPressure(pres);
            refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 3, 0, 1.0);
            double refWaterFugacityCoef =
                    Math.log(refPhase.getComponent("water").fugcoef(refPhase));

            double dhf = 6010.0, tmi = 273.15, dcp = 37.29;

            double LNFUG_ICEREF = refWaterFugacityCoef - (dhf / (R * tmi)) * (tmi / temp - 1.0)
                    + (dcp / R) * ((tmi / temp) - 1.0 - Math.log(tmi / temp));
            double VM = 0.0;
            double K1 = 1.6070E-4;
            double K2 = 3.4619E-7;
            double K3 = -0.2637E-10;
            VM = 1E-6 * 19.6522 * (1 + K1 * (temp - tmi) + K2 * Math.pow((temp - tmi), 2)
                    + K3 * Math.pow((temp - tmi), 3));

            double LNFUG_ICE = LNFUG_ICEREF + (VM * 100000 * (pres - 1.0) / (R * temp));
            fugasityCoeffisient = Math.exp(LNFUG_ICE);
        } else {
            for (int structure = 0; structure < 2; structure++) {
                hydrateStructure = structure;
                if (componentName.equals("water")) {
                    // this is the empty hydrate fugacity devited by pressure (why??)
                    double solvol = getMolarVolumeHydrate(hydrateStructure, temp);

                    double val = 0.0;
                    double tempy = 1.0;

                    for (int cavType = 0; cavType < 2; cavType++) {
                        tempy = 0.0;
                        for (int j = 0; j < phase.getNumberOfComponents(); j++) {
                            double tee = ((ComponentHydrate) phase.getComponent(j))
                                    .calcYKI(hydrateStructure, cavType, phase);
                            tempy += tee;
                        }
                        val += getCavprwat()[hydrateStructure][cavType] * Math.log(1.0 - tempy);
                    }
                    // System.out.println("val " + Math.exp(val));
                    /*
                     * System.out.println("fugacity " +
                     * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) *
                     * Math.exp(solvol / (R * temp) * (pres -
                     * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5) /
                     * pres)); System.out.println("temperature " + temp);
                     * System.out.println("pressure " + pres); System.out.println("vapor pressure "
                     * + (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)));
                     * System.out.println("fugacity folas " +
                     * (getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) *
                     * Math.exp(solvol / (R * temp) * (pres -
                     * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)) * 1e5)));
                     * 
                     */
                    // System.out.println("pointing "
                    // +(Math.exp(solvol/(R*temp)*((pres-getEmptyHydrateStructureVapourPressure(hydrateStruct,temp))*1e5))));
                    fugasityCoeffisient = Math.exp(val)
                            * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp)
                            * Math.exp(solvol / (R * temp)
                                    * (pres - getEmptyHydrateStructureVapourPressure(
                                            hydrateStructure, temp))
                                    * 1e5)
                            / pres;
                    // System.out.println("fugcoef " + tempfugcoef + "structure " +
                    // (hydrateStruct+1));

                    // System.out.println("structure " + (hydrateStructure+1));
                    if (fugasityCoeffisient < maxFug) {
                        maxFug = fugasityCoeffisient;
                        stableStructure = hydrateStructure;
                    }
                } else {
                    fugasityCoeffisient = 1e50;
                }
            }
            fugasityCoeffisient = maxFug;
        }
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        hydrateStructure = stableStructure;
        return fugasityCoeffisient;
    }

    /** {@inheritDoc} */
    @Override
    public double calcYKI(int stucture, int cavityType, PhaseInterface phase) {
        if (componentName.equals("water")) {
            return 0.0;
        }
        double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber];
        // * 1.0e5;
        double temp = 1.0;
        for (int i = 0; i < phase.getNumberOfComponents(); i++) {
            if (phase.getComponent(i).isHydrateFormer()) {
                temp += ((ComponentHydrate) phase.getComponent(i)).calcCKI(stucture, cavityType,
                        phase) * reffug[i];
            }

            // System.out.println("yk2 "+
            // ((ComponentHydrateBallard)phase.getComponent(i)).calcCKI(stucture,
            // cavityType, phase)*reffug[i]);
            // System.out.println("CYJI" +yki + " ref fug " +(1.0e5*reffug[i]));
        }

        return yki / temp;
    }

    /** {@inheritDoc} */
    @Override
    public double calcCKI(int stucture, int cavityType, PhaseInterface phase) {
        // this is equation 8.8
        if (componentName.equals("water")) {
            return 0.0;
        }
        return Ak[stucture][cavityType] / (phase.getTemperature())
                * Math.exp(Bk[stucture][cavityType] / (phase.getTemperature()));
    }
}
