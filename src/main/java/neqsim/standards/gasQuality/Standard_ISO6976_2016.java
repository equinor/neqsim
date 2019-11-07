/*
 * Standard_ISO1992.java
 *
 * Created on 13. juni 2004, 23:30
 */
package neqsim.standards.gasQuality;

import java.util.ArrayList;

import org.apache.log4j.Logger;

import neqsim.fluidMechanics.flowSystem.twoPhaseFlowSystem.shipSystem.LNGship;
import neqsim.thermo.system.SystemInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author ESOL
 */
public class Standard_ISO6976_2016 extends Standard_ISO6976 implements neqsim.thermo.ThermodynamicConstantsInterface {

    private static final long serialVersionUID = 1000;

    //metering conditions
   
    double R = 8.3144621;
 
  
    double Zmix0 = 1.0, Zmix15 = 1.0, Zmix20 = 1.0, Zmix60F = 1.0;
    double Zair0 = 0.999419, Zair15 = 0.999595, Zair60F = 999601, Zair20 = 0.999645;
 
    double[] Z0, Z15, Z20, Z60F;
    double[] bsqrt0, bsqrt15, bsqrt20, bsqrt60F;
    double[] Hsup0, Hsup15, Hsup20, Hsup25, Hsup60F;
    double[] Hinf0, Hinf15, Hinf20, Hinf25, Hinf60F;
    double HsupIdeal0 = 0.0, HsupIdeal15 = 0.0, HsupIdeal20 = 0.0, HsupIdeal25 = 0.0, HsupIdeal60F = 0.0;
    double HinfIdeal0 = 0.0, HinfIdeal15 = 0.0, HinfIdeal20 = 0.0, HinfIdeal25 = 0.0, HinfIdeal60F = 0.0;
    static Logger logger = Logger.getLogger(Standard_ISO6976_2016.class);

    /**
     * Creates a new instance of Standard_ISO1992
     */
    public Standard_ISO6976_2016() {
        name = "Standard_ISO6976_2016";
        componentsNotDefinedByStandard = new ArrayList<String>();
        standardDescription = "Calculation of calorific values, density, relative density and Wobbe index from composition based on ISO6976 version 2016";
    }

    /**
     * Creates a new instance of Standard_ISO1992
     */
    public Standard_ISO6976_2016(SystemInterface thermoSystem) {
        super(thermoSystem);
        componentsNotDefinedByStandard = new ArrayList<String>();
        name = "Standard_ISO6976_2016";
        M = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        carbonNumber = new int[thermoSystem.getPhase(0).getNumberOfComponents()];

        Z0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Z15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Z60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        Z20 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];

        bsqrt0 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        bsqrt15 = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
        bsqrt60F = new double[thermoSystem.getPhase(0).getNumberOfComponents()];
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
                    dataSet = database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='" + this.thermoSystem.getPhase(0).getComponent(i).getName() + "'"));
                    dataSet.next();
                    M[i] = Double.parseDouble(dataSet.getString("MolarMass"));

                } catch (Exception e) {
                    try {
                        dataSet.close();
                        if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("inert")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='nitrogen'"));
                        } else if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("HC")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='n-heptane'"));
                        } else if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("alcohol") || this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("glycol")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='methanol'"));
                        } else if (this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("TPB") || this.thermoSystem.getPhase(0).getComponent(i).getComponentType().equals("plus")) {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='n-heptane'"));
                        } else {
                            dataSet = database.getResultSet(("SELECT * FROM iso6976constants2016 WHERE ComponentName='nitrogen'"));
                        }
                        M[i] = this.thermoSystem.getPhase(0).getComponent(i).getMolarMass();
                        dataSet.next();
                    } catch (Exception er) {
                    	logger.error(er.toString());
                    }
                    componentsNotDefinedByStandard.add("this.thermoSystem.getPhase(0).getComponent(i).getComponentName()");
                    logger.info("added component not specified by ISO6976constants2016 " + this.thermoSystem.getPhase(0).getComponent(i).getComponentName());

                }

                carbonNumber[i] = Integer.parseInt(dataSet.getString("numberOfCarbon"));

                Z0[i] = Double.parseDouble(dataSet.getString("Z0"));
                Z15[i] = Double.parseDouble(dataSet.getString("Z15"));
                Z60F[i] = Double.parseDouble(dataSet.getString("Z60F"));
                Z20[i] = Double.parseDouble(dataSet.getString("Z20"));

                bsqrt0[i] = Double.parseDouble(dataSet.getString("srtb0"));
                bsqrt15[i] = Double.parseDouble(dataSet.getString("srtb15"));
                bsqrt60F[i] = Double.parseDouble(dataSet.getString("srtb60F"));
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
            logger.error(err);
        }

    }

    public Standard_ISO6976_2016(SystemInterface thermoSystem, double volumetricReferenceTemperaturedegC, double energyReferenceTemperaturedegC, String calculationType) {
        this(thermoSystem);
        this.referenceType = calculationType;
        volRefT = volumetricReferenceTemperaturedegC;
        energyRefT = energyReferenceTemperaturedegC;
    }

    public void calculate() {
        Zmix0 = 1.0;
        Zmix15 = 1.0;
        Zmix60F = 1.0;
        Zmix20 = 1.0;
        double Zmixtemp0 = 0.0;
        double Zmixtemp15 = 0.0;
        double Zmixtemp60F = 0.0;
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
            Zmixtemp60F += thermoSystem.getPhase(0).getComponent(i).getz() * bsqrt60F[i];
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
        Zmix60F -= Math.pow(Zmixtemp60F, 2.0);
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
            returnValue = Zmix60F;
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
        if (getReferenceState().equals("ideal")) {
            relativeDens = relDensIdeal;
        } else if (getVolRefT() == 0) {
            relativeDens = relDensIdeal * Zair0 / Zmix0;
        } else if (getVolRefT() == 15) {
            relativeDens = relDensIdeal * Zair15 / Zmix15;
        } else if (getVolRefT() == 15.55) {
            relativeDens = relDensIdeal * Zair60F / Zmix60F;
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


    /**
     * @return the energyRefT
     */


  

  

}
