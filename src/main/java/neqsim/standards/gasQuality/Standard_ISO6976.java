/*
 * Standard_ISO1992.java
 *
 * Created on 13. juni 2004, 23:30
 */
package neqsim.standards.gasQuality;

import java.text.*;
import java.util.ArrayList;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author ESOL
 */
public class Standard_ISO6976 extends neqsim.standards.Standard implements neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    //metering conditions
    ArrayList<String> componentsNotDefinedByStandard = null;
    double volRefT = 0;
    double volRefP = 1.01325;
    double R = 8.314510;
    double molRefm3 = 0.0;
    //combustion conditions
    double energyRefT = 25;
    double energyRefP = 1.01325;
    String referenceType = "volume"; // mass  volume  molar
    String energyUnit = "KJ/Nm3";
    double energy = 1.0;
    double Zmix0 = 1.0, Zmix15 = 1.0, Zmix20 = 1.0;
    double Zair0 = 0.99941, Zair15 = 0.99958, Zair20 = 0.99963;
    double averageCarbonNumber = 0.0;
    int[] carbonNumber;
    double[] M;
    double[] Z0, Z15, Z20;
    double[] bsqrt0, bsqrt15, bsqrt20;
    double[] Hsup0, Hsup15, Hsup20, Hsup25, Hsup60F;
    double[] Hinf0, Hinf15, Hinf20, Hinf25, Hinf60F;
    double Mmix = 0.0;
    double HsupIdeal0 = 0.0, HsupIdeal15 = 0.0, HsupIdeal20 = 0.0, HsupIdeal25 = 0.0, HsupIdeal60F = 0.0;
    double HinfIdeal0 = 0.0, HinfIdeal15 = 0.0, HinfIdeal20 = 0.0, HinfIdeal25 = 0.0, HinfIdeal60F = 0.0;
    double wobbeIdeal = 0.0, wobbeReal = 0.0;
    double relDensIdeal = 0.0, relDensReal = 0.0;
    double densIdeal = 0.0, densReal = 0.0;

    /**
     * Creates a new instance of Standard_ISO1992
     */
    public Standard_ISO6976() {
        name = "Standard_ISO6976";
        componentsNotDefinedByStandard = new ArrayList<String>();
        standardDescription = "Calculation of calorific values, density, relative density and Wobbe index from composition";
    }

    /**
     * Creates a new instance of Standard_ISO1992
     */
    public Standard_ISO6976(SystemInterface thermoSystem) {
        super(thermoSystem);
        componentsNotDefinedByStandard = new ArrayList<String>();
        name = "Standard_ISO6976";
        M = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        carbonNumber = new int[thermoSystem.getPhase(0).getNumberOfComponents()];

        Z0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Z15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Z20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

        bsqrt0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        bsqrt15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        bsqrt20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

        Hsup0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hsup15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hsup20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hsup25 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hsup60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

        Hinf0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hinf15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hinf20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hinf25 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Hinf60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        try {
            neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
            java.sql.ResultSet dataSet = null;

            for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {

                try {
                    dataSet = database.getResultSet(("SELECT * FROM iso6976constants WHERE ComponentName='" + this.thermoSystem.getPhase(0).getComponent(i).getName() + "'"));
                    dataSet.next();
                    M[i] = Double.parseDouble(dataSet.getString("MolarMass"));

                } catch (Exception e) {
                    try {
                        dataSet.close();
                        if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("inert")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants WHERE ComponentName='nitrogen'"));
                        } else if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("HC")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants WHERE ComponentName='n-heptane'"));
                        } else if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("alcohol") || this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("glycol")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants WHERE ComponentName='methanol'"));
                        } else if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("TPB") || this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("plus")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants WHERE ComponentName='n-heptane'"));
                        } else {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants WHERE ComponentName='nitrogen'"));
                        }
                        M[i] = this.thermoSystem.getPhase(0).getComponent(i).getMolarMass();
                        dataSet.next();
                    } catch (Exception er) {
                        er.printStackTrace();
                    }
                    componentsNotDefinedByStandard.add("this.thermoSystem.getPhase(0).getComponent(i).getComponentName()");
                    System.out.println("added component not specified by ISO6976 " + this.thermoSystem.getPhase(0).getComponent(i).getComponentName());

                }

                carbonNumber[i] = Integer.parseInt(dataSet.getString("numberOfCarbon"));

                Z0[i] = Double.parseDouble(dataSet.getString("Z0"));
                Z15[i] = Double.parseDouble(dataSet.getString("Z15"));
                Z20[i] = Double.parseDouble(dataSet.getString("Z20"));

                bsqrt0[i] = Double.parseDouble(dataSet.getString("srtb0"));
                bsqrt15[i] = Double.parseDouble(dataSet.getString("srtb15"));
                bsqrt20[i] = Double.parseDouble(dataSet.getString("srtb20"));

                Hsup0[i] = Double.parseDouble(dataSet.getString("Hsupmolar0"));
                Hsup15[i] = Double.parseDouble(dataSet.getString("Hsupmolar15"));
                Hsup20[i] = Double.parseDouble(dataSet.getString("Hsupmolar20"));
                Hsup25[i] = Double.parseDouble(dataSet.getString("Hsupmolar25"));
                Hsup60F[i] = Double.parseDouble(dataSet.getString("Hsupmolar60F"));

                Hinf0[i] = Double.parseDouble(dataSet.getString("Hinfmolar0"));
                Hinf15[i] = Double.parseDouble(dataSet.getString("Hinfmolar15"));
                Hinf20[i] = Double.parseDouble(dataSet.getString("Hinfmolar20"));
                Hinf25[i] = Double.parseDouble(dataSet.getString("Hinfmolar25"));
                Hinf60F[i] = Double.parseDouble(dataSet.getString("Hinfmolar60F"));
            }

            dataSet.close();
            database.getConnection().close();

        } catch (Exception e) {
            String err = e.toString();
            System.out.println(err);
        }
        System.out.println("ok adding components in " + getName());

    }

    public Standard_ISO6976(SystemInterface thermoSystem, double volumetricReferenceTemperaturedegC, double energyReferenceTemperaturedegC, String calculationType) {
        this(thermoSystem);
        this.referenceType = calculationType;
        volRefT = volumetricReferenceTemperaturedegC;
        energyRefT = energyReferenceTemperaturedegC;
    }

    public void calculate() {
        Zmix0 = 1.0;
        Zmix15 = 1.0;
        Zmix20 = 1.0;
        double Zmixtemp0 = 0.0;
        double Zmixtemp15 = 0.0;
        double Zmixtemp20 = 0.0;
        Mmix = 0.0;
        relDensIdeal = 0.0;
        HsupIdeal0 = 0.0;
        HsupIdeal15 = 0.0;
        HsupIdeal20 = 0.0;
        HsupIdeal25 = 0.0;
        HsupIdeal60F = 0.0;
        HinfIdeal0 = 0.0;
        HinfIdeal15 = 0.0;
        HinfIdeal20 = 0.0;
        HinfIdeal25 = 0.0;
        HinfIdeal60F = 0.0;

        for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {

            Mmix += thermoSystem.getPhase(0).getComponent(i).getz() * M[i];

            Zmixtemp0 += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt0[i];
            Zmixtemp15 += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt15[i];
            Zmixtemp20 += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt20[i];

            HsupIdeal0 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup0[i];
            HsupIdeal15 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup15[i];
            HsupIdeal20 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup20[i];
            HsupIdeal25 += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup25[i];
            HsupIdeal60F += thermoSystem.getPhase(0).getComponent(i).getz() * Hsup60F[i];

            HinfIdeal0 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf0[i];
            HinfIdeal15 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf15[i];
            HinfIdeal20 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf20[i];
            HinfIdeal25 += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf25[i];
            HinfIdeal60F += thermoSystem.getPhase(0).getComponent(i).getz() * Hinf60F[i];

            relDensIdeal += thermoSystem.getPhase(0).getComponent(i).getz() * M[i] / molarMassAir;
        }
        Zmix0 -= Math.pow(Zmixtemp0, 2.0);
        Zmix15 -= Math.pow(Zmixtemp15, 2.0);
        Zmix20 -= Math.pow(Zmixtemp20, 2.0);
        molRefm3 = volRefP * 1.0e5 * 1.0 / (R * (getVolRefT() + 273.15) * getValue("CompressionFactor"));
        //System.out.println("molRefm3 " + molRefm3);
    }

    public double getValue(String returnParameter, java.lang.String returnUnit) {
        if (returnParameter.equals("GCV")) {
            returnParameter = "SuperiorCalorificValue";
        }

        double returnValue = 0.0;

        if (getVolRefT() == 0) {
            returnValue = Zmix0;
        } else if (getVolRefT() == 15) {
            returnValue = Zmix15;
        } else if (getVolRefT() == 15.55) {
            returnValue = Zmix15;
        } else if (getVolRefT() == 20) {
            returnValue = Zmix20;
        }

        if (returnParameter.equals("CompressionFactor")) {
            return returnValue;
        }
        if (returnParameter.equals("MolarMass")) {
            return Mmix;
        }

        double realCorrection = 1.0;
        if (getReferenceState().equals("ideal")) {
            realCorrection = 1.0;
        } else {
            realCorrection = returnValue;
        }

        if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 0) {
            returnValue = HsupIdeal0;
        } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 15) {
            returnValue = HsupIdeal15;
        } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 20) {
            returnValue = HsupIdeal20;
        } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 25) {
            returnValue = HsupIdeal25;
        } else if (returnParameter.equals("SuperiorCalorificValue") && getEnergyRefT() == 15.55) {
            returnValue = HsupIdeal60F;
        } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 0) {
            returnValue = HinfIdeal0;
        } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 15) {
            returnValue = HinfIdeal15;
        } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 20) {
            returnValue = HinfIdeal20;
        } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 25) {
            returnValue = HinfIdeal25;
        } else if (returnParameter.equals("InferiorCalorificValue") && getEnergyRefT() == 15.55) {
            returnValue = HinfIdeal60F;
        } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 0) {
            returnValue = HsupIdeal0;
        } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 15) {
            returnValue = HsupIdeal15;
        } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 20) {
            returnValue = HsupIdeal20;
        } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 25) {
            returnValue = HsupIdeal25;
        } else if (returnParameter.equals("SuperiorWobbeIndex") && getEnergyRefT() == 15.55) {
            returnValue = HsupIdeal60F;
        } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 0) {
            returnValue = HinfIdeal0;
        } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 15) {
            returnValue = HinfIdeal15;
        } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 20) {
            returnValue = HinfIdeal20;
        } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 15.55) {
            returnValue = HinfIdeal60F;
        } else if (returnParameter.equals("InferiorWobbeIndex") && getEnergyRefT() == 25) {
            returnValue = HinfIdeal25;
        }
        if (returnUnit.equals("kWh")) {
            returnValue /= 3600.0;
        }

        double relativeDens = 0.0;
       // System.out.println("reference state " + getReferenceState());
        if (getReferenceState().equals("ideal")) {
            relativeDens = relDensIdeal;
        } else if (getVolRefT() == 0) {
            relativeDens = relDensIdeal * Zair0 / Zmix0;
        } else if (getVolRefT() == 15) {
            relativeDens = relDensIdeal * Zair15 / Zmix15;
        } else if (getVolRefT() == 15.55) {
            relativeDens = relDensIdeal * Zair15 / Zmix15;
        } else if (getVolRefT() == 20) {
            relativeDens = relDensIdeal * Zair20 / Zmix20;
        }
        if (returnParameter.equals("RelativeDensity")) {
            return relativeDens;
        }
        if (returnParameter.equals("InferiorWobbeIndex") || returnParameter.equals("SuperiorWobbeIndex")) {
            returnValue /= Math.sqrt(relativeDens);
        }
        if (returnParameter.equals("DensityIdeal")) {
            return volRefP * 1e5 / (R * (getVolRefT() + 273.15)) * Mmix / 1.0e3;
        }
        if (returnParameter.equals("DensityReal")) {
            return volRefP * 1e5 / (R * (getVolRefT() + 273.15)) * Mmix / 1.0e3 / realCorrection;
        }

        if (getReferenceType().equals("molar")) {
            return returnValue;
        } else if (getReferenceType().equals("mass")) {
            return returnValue / (Mmix / 1000.0);
        } else {
            return returnValue * volRefP * 1.0e5 / (R * (getVolRefT() + 273.15)) / realCorrection;
        }
    }

    public double getValue(String returnParameter) {
        return getValue(returnParameter, "");
    }

    public String getUnit(String returnParameter) {
        if (returnParameter.equals("CompressionFactor")) {
            return "-";
        } else {
            return energyUnit;
        }
    }

    public boolean isOnSpec() {
        return true;
    }

    public String[][] createTable(String name) {
        thermoSystem.setNumberOfPhases(1);

        thermoSystem.createTable(name);

        DecimalFormat nf = new DecimalFormat();
        nf.setMaximumFractionDigits(5);
        nf.applyPattern("#.#####E0");
        String[][] table = new String[thermoSystem.getPhases()[0].getNumberOfComponents() + 30][6];
        String[] names = {"", "Phase 1", "Phase 2", "Phase 3", "Unit"};
        table[0][0] = "";//getPhases()[0].getPhaseTypeName();//"";

        for (int i = 0; i < thermoSystem.getPhases()[0].getNumberOfComponents() + 30; i++) {
            for (int j = 0; j < 6; j++) {
                table[i][j] = "";
            }
        }
        for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
            table[0][i + 1] = thermoSystem.getPhase(i).getPhaseTypeName();
        }

        StringBuffer buf = new StringBuffer();
        FieldPosition test = new FieldPosition(0);

        String referenceTypeUnit = "";
        if (getReferenceType().equals("volume")) {
            referenceTypeUnit = "m^3";
        } else if (getReferenceType().equals("mass")) {
            referenceTypeUnit = "kg";
        } else if (getReferenceType().equals("molar")) {
            referenceTypeUnit = "mol";
        }
        for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
            for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
                table[j + 1][0] = thermoSystem.getPhases()[0].getComponents()[j].getName();
                buf = new StringBuffer();
                table[j + 1][i + 1] = nf.format(thermoSystem.getPhase(thermoSystem.getPhaseIndex(i)).getComponents()[j].getx(), buf, test).toString();
                table[j + 1][4] = "[-]";
            }

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][0] = "Compressibility Factor";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][i + 1] = nf.format(getValue("CompressionFactor"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 3][4] = "[-]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][0] = "Superior Calorific Value";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][i + 1] = nf.format(getValue("SuperiorCalorificValue"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 4][4] = "[kJ/" + referenceTypeUnit + "]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][0] = "Inferior Calorific Value";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][i + 1] = nf.format(getValue("InferiorCalorificValue"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 5][4] = "[kJ/" + referenceTypeUnit + "]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 6][0] = "Superior Wobbe Index";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 6][i + 1] = nf.format(getValue("SuperiorWobbeIndex") / 3600.0);
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 6][4] = "[kWh/" + referenceTypeUnit + "]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][0] = "Superior Wobbe Index";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][i + 1] = nf.format(getValue("SuperiorWobbeIndex"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 7][4] = "[kJ/" + referenceTypeUnit + "]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][0] = "Inferior Wobbe Index";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][i + 1] = nf.format(getValue("InferiorWobbeIndex"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 8][4] = "[kJ/" + referenceTypeUnit + "]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 9][0] = "Relative Density";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 9][i + 1] = nf.format(getValue("RelativeDensity"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 9][4] = "[-]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][0] = "Molar Mass";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][i + 1] = nf.format(getValue("MolarMass"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 10][4] = "[gr/mol]";

            buf = new StringBuffer();
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][0] = "Density";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][i + 1] = nf.format(getValue("DensityReal"));
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 11][4] = "[kg/m^3]";

            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][0] = "Reference Temperature Combustion";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][i + 1] = Double.toString(getEnergyRefT());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 13][4] = "[C]";

            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 14][0] = "Reference Temperature Volume";
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 14][i + 1] = Double.toString(getVolRefT());
            table[thermoSystem.getPhases()[0].getNumberOfComponents() + 14][4] = "[C]";

        }

        resultTable = table;
        return table;
    }

    /**
     * @return the energyRefT
     */
    public //combustion conditions
            double getEnergyRefT() {
        return energyRefT;
    }

    /**
     * @param energyRefT the energyRefT to set
     */
    public void setEnergyRefT(double energyRefT) {
        this.energyRefT = energyRefT;
    }

    /**
     * @return the energyRefP
     */
    public double getEnergyRefP() {
        return energyRefP;
    }

    /**
     * @param energyRefP the energyRefP to set
     */
    public void setEnergyRefP(double energyRefP) {
        this.energyRefP = energyRefP;
    }

    /**
     * @return the volRefT
     */
    public //metering conditions
            double getVolRefT() {
        return volRefT;
    }

    /**
     * @param volRefT the volRefT to set
     */
    public void setVolRefT(double volRefT) {
        this.volRefT = volRefT;
    }

    /**
     * @return the componentsNotDefinedByStandard
     */
    public //metering conditions
            ArrayList<String> getComponentsNotDefinedByStandard() {
        return componentsNotDefinedByStandard;
    }

    public double getTotalMolesOfInerts() {
        double inerts = 0.0;
        for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
            if (carbonNumber[j] == 0) {
                inerts += thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles();
            }
        }

        return inerts;
    }

    public void removeInertsButNitrogen() {
        for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
            if (carbonNumber[j] == 0 && !thermoSystem.getPhase(0).getComponent(j).getName().equals("nitrogen")) {
                thermoSystem.addComponent("nitrogen", thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles());
                thermoSystem.addComponent(thermoSystem.getPhase(0).getComponent(j).getName(), -thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles() * 0.99999);
            }
        }
    }

    /**
     * @return the averageCarbonNumber
     */
    public double getAverageCarbonNumber() {
        double inerts = getTotalMolesOfInerts();
        averageCarbonNumber = 0;
        for (int j = 0; j < thermoSystem.getPhases()[0].getNumberOfComponents(); j++) {
            averageCarbonNumber += carbonNumber[j] * thermoSystem.getPhase(0).getComponent(j).getNumberOfmoles() / (thermoSystem.getTotalNumberOfMoles() - inerts);
        }
        System.out.println("average carbon number " + averageCarbonNumber);
        return averageCarbonNumber;
    }

    /**
     * @return the referenceType
     */
    public String getReferenceType() {
        return referenceType;
    }

    /**
     * @param referenceType the referenceType to set
     */
    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

}
