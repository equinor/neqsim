/*
 * Class.java
 *
 * Created on 19. november 2001, 11:43
 */
package neqsim.thermo.component;

import neqsim.thermo.phase.PhaseInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author esol
 * @version
 */
public class ComponentHydrate extends Component {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(ComponentHydrate.class);

    //double emptyHydrateVapourPressureConstant[][] =  {{17.6025820786, -6056.0650578668},{17.332, -6017.6}}; //fitted
    double emptyHydrateVapourPressureConstant[][] = {{17.44, -6003.9}, {17.332, -6017.6}}; //Sloan (1990)
    //double emptyHydrateVapourPressureConstant[][] =  {{ 17.5061457754, -6030.6886435166},{17.332, -6017.6}}; //fitted (1990)
    int hydrateStructure = 0;
    double coordNumb[][] = new double[2][2]; //[structure][cavitytype]
    double cavRadius[][] = new double[2][2]; //[structure][cavitytype]
    double cavNumb[][] = new double[2][2]; //[structure][cavitytype]
    double cavprwat[][] = new double[2][2]; //[structure][cavitytype]
    // double[] dGfHydrate = {-236539.2, -235614.0};
    //  double[] dHfHydrate = {-292714.5, -292016.0};
    double[] dGfHydrate = {-235557, -235614};
    double[] dHfHydrate = {-291786, -292016};
    double reffug[] = new double[neqsim.thermo.ThermodynamicConstantsInterface.MAX_NUMBER_OF_COMPONENTS];
    private double sphericalCoreRadiusHydrate = 0.0;
    private double lennardJonesEnergyParameterHydrate = 0.0;
    private double lennardJonesMolecularDiameterHydrate = 0.0;
    PhaseInterface refPhase = null;

    /**
     * Creates new Class
     */
    public ComponentHydrate() {
    }

    public ComponentHydrate(String component_name, double moles, double molesInPhase, int compnumber) {
        super(component_name, moles, molesInPhase, compnumber);
        coordNumb[0][0] = 20.0;
        coordNumb[0][1] = 24.0;
        cavRadius[0][0] = 3.95;
        cavRadius[0][1] = 4.33;
        cavNumb[0][0] = 2.0;
        cavNumb[0][1] = 6.0;
        cavprwat[0][0] = 1.0 / 23.0;
        cavprwat[0][1] = 3.0 / 23.0;

        coordNumb[1][0] = 20.0;
        coordNumb[1][1] = 28.0;
        cavRadius[1][0] = 3.91;
        cavRadius[1][1] = 4.73;
        cavNumb[1][0] = 16.0;
        cavNumb[1][1] = 8.0;
        cavprwat[1][0] = 2.0 / 17.0;
        cavprwat[1][1] = 1.0 / 17.0;

        reffug[0] = 10.0;
        reffug[1] = 1.0;

        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        java.sql.ResultSet dataSet = null;
        try {
            if (!component_name.equals("default")) {
                try {
                    if (database.createTemporaryTables()) {
                        dataSet = database.getResultSet(("SELECT * FROM COMPTEMP WHERE name='" + component_name + "'"));
                    } else {
                        dataSet = database.getResultSet(("SELECT * FROM COMP WHERE name='" + component_name + "'"));

                    }
                    dataSet.next();
                    dataSet.getString("FORMULA");
                } catch (Exception e) {
                    dataSet.close();
                    logger.info("no parameters in tempcomp -- trying comp.. " + component_name);
                    dataSet = database.getResultSet(("SELECT * FROM COMP WHERE name='" + component_name + "'"));
                    dataSet.next();
                }
                lennardJonesMolecularDiameterHydrate = Double.parseDouble(dataSet.getString("LJdiameterHYDRATE"));        //BF
                lennardJonesEnergyParameterHydrate = Double.parseDouble(dataSet.getString("LJepsHYDRATE"));
                sphericalCoreRadiusHydrate = Double.parseDouble(dataSet.getString("SphericalCoreRadiusHYDRATE"));
            }
        } catch (Exception e) {
            logger.error("error in comp", e);

        } finally {
            try {
                if (dataSet != null) {
                    dataSet.close();
                }
                if (database.getStatement() != null) {
                    database.getStatement().close();
                }
                if (database.getConnection() != null) {
                    database.getConnection().close();
                }
            } catch (Exception e) {
                logger.error("error closing database.....", e);

            }
        }
    }

    public void readHydrateParameters() {
    }

    public double fugcoef(PhaseInterface phase) {
        return fugcoef(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure());
    }

    public void setHydrateStructure(int structure) {
        this.hydrateStructure = structure;
    }

    public int getHydrateStructure() {
        return this.hydrateStructure;
    }

    public double fugcoef(PhaseInterface phase, int numberOfComps, double temp, double pres) {
        if (componentName.equals("water")) {
            fugasityCoeffisient = -1e50;
            double val = 1.0;
            double tempy = 1.0;
            double fugold = 0.0;

            do {

                val = 0;
                tempy = 0.0;
                fugold = fugasityCoeffisient;
                if (hydrateStructure >= 0) {
                    for (int cavType = 0; cavType < 2; cavType++) {
                        tempy = 0.0;
                        for (int j = 0; j < phase.getNumberOfComponents(); j++) {
                            //System.out.println(phase.getComponent(j));
                            tempy += ((ComponentHydrate) phase.getComponent(j)).calcYKI(hydrateStructure, cavType, phase);
                            //System.out.println("tempny " +tempy);
                            //System.out.println("temp ny " + this);//phase.getComponent(j));
                        }
                        val += getCavprwat()[hydrateStructure][cavType] * Math.log(1.0 - tempy);
                    }
                }
                //System.out.println("val " +(val));
                //System.out.println("fugasityCoeffisient bef " + fugasityCoeffisient);
                double solvol = 1.0 / 906.0 * getMolarMass();
                fugasityCoeffisient = Math.exp(val) * getEmptyHydrateStructureVapourPressure(hydrateStructure, temp) * Math.exp(solvol / (R * temp) * ((pres - getEmptyHydrateStructureVapourPressure(hydrateStructure, temp))) * 1e5) / pres;
                //System.out.println("struct " + hydrateStruct + " fug " + tempfugcoef + " val "+ val);

                //fugasityCoeffisient = Math.exp(val)*getEmptyHydrateStructureVapourPressure(hydrateStructure,temp)*Math.exp(solvol/(R*temp)*((pres-getEmptyHydrateStructureVapourPressure(hydrateStructure,temp)))*1e5)/pres;
                //fugasityCoeffisient = getAntoineVaporPressure(temp)/pres;
                //logFugasityCoeffisient = Math.log(fugasityCoeffisient);
                //logFugasityCoeffisient += val*boltzmannConstant/R;
                //fugasityCoeffisient = Math.exp(logFugasityCoeffisient);
                //System.out.println("fugasityCoeffisient " + fugasityCoeffisient);
            } while (Math.abs((fugasityCoeffisient - fugold) / fugold) > 1e-6);
        } else {
            fugasityCoeffisient = 1e5;
        }
        logFugasityCoeffisient = Math.log(fugasityCoeffisient);
        //System.out.println("fug " + fugasityCoeffisient);
        return fugasityCoeffisient;
    }

    public double getEmptyHydrateStructureVapourPressure(int type, double temperature) {
        if (type == -1) {
            return getSolidVaporPressure(temperature);
        } else {
            return Math.exp(getEmptyHydrateVapourPressureConstant(type, 0) + getEmptyHydrateVapourPressureConstant(type, 1) / temperature) * 1.01325;
        }
    }

    public void setEmptyHydrateVapourPressureConstant(int hydrateStructure, int parameterNumber, double value) {
        emptyHydrateVapourPressureConstant[hydrateStructure][parameterNumber] = value;
    }

    public double getEmptyHydrateVapourPressureConstant(int hydrateStructure, int parameterNumber) {
        return emptyHydrateVapourPressureConstant[hydrateStructure][parameterNumber];
    }

    public double calcChemPotEmpty(PhaseInterface phase, int numberOfComps, double temp, double pres, int hydrateStruct) {
        double dGf = 0.0, dHf = 0.0, Cpa = getCpA(), Cpb = getCpB(), Cpc = getCpC(), Cpd = getCpD();
        double molarvolume = 1.0 / (55493.0);//*0.9);
        double deltaMolarVolume = 0.0;
        if (hydrateStruct == 1) {
            dGf = getDGfHydrate()[1];
            dHf = getDHfHydrate()[1];
            molarvolume = getMolarVolumeHydrate(hydrateStruct, temp);
            deltaMolarVolume = 5.0e-6;
        } else {
            dGf = getDGfHydrate()[0];
            dHf = getDHfHydrate()[0];
            molarvolume = getMolarVolumeHydrate(hydrateStruct, temp);
            deltaMolarVolume = 4.6e-6;
        }
        double T0 = 298.15;

        dHf += Cpa * (temp - T0) + Cpb * Math.log(temp / T0) - Cpc * (1.0 / temp - 1.0 / T0) - 1.0 / 2.0 * Cpd * (1.0 / (temp * temp) - 1.0 / (T0 * T0));

        return (dGf / R / T0 + 1.0 * dHf * (1.0 / R / temp - 1.0 / R / T0)) + deltaMolarVolume / R / ((temp + T0) / 2.0) * (pres * 1e5 - 1e5);
    }

    public double calcChemPotIdealWater(PhaseInterface phase, int numberOfComps, double temp, double pres, int hydrateStruct) {
        double dGf = -228700.0, dHf = -242000.0, Cpa = getCpA(), Cpb = getCpB(), Cpc = getCpC(), Cpd = getCpD();
        //   Cpa = 0.7354;
        //   Cpb = 0.01418;
        //   Cpc = -1.727e-5;
        double T0 = 298.15;
        dHf += Cpa * (temp - T0) + Cpb * Math.log(temp / T0) - Cpc * (1.0 / temp - 1.0 / T0) - 1.0 / 2.0 * Cpd * (1.0 / (temp * temp) - 1.0 / (T0 * T0));
        return (dGf / R / T0 + 1.0 * dHf * (1.0 / R / temp - 1.0 / R / T0));
    }

    public double calcYKI(int stucture, int cavityType, PhaseInterface phase) {
        if (componentName.equals("water")) {
            return 0.0;
        }
        //if(componentName.equals("methane")){
        double yki = calcCKI(stucture, cavityType, phase) * reffug[componentNumber];
        double temp = 1.0;
        for (int i = 0; i < phase.getNumberOfComponents(); i++) {
            if (!phase.getComponent(i).getComponentName().equals("water")) {
                if (phase.getComponent(i).isHydrateFormer()) {
                    temp += ((ComponentHydrate) phase.getComponent(i)).calcCKI(stucture, cavityType, phase) * reffug[i];
                }
            }
        }
        return yki / temp;
        // }
        // else return 0.0;
    }

    public double calcCKI(int stucture, int cavityType, PhaseInterface phase) {
        double cki = 4.0 * pi / (boltzmannConstant * phase.getTemperature()) * potIntegral(stucture, cavityType, phase);
        //System.out.println("cki " + cki);
        return cki;
    }

    public void setRefFug(int compNumbm, double val) {
        //System.out.println("ref fug setting " + val);
        reffug[compNumbm] = val;
    }

    public double potIntegral(int stucture, int cavityType, PhaseInterface phase) {
        double val = 0.0;
        double endval = cavRadius[stucture][cavityType] - getSphericalCoreRadiusHydrate();
        double x = 0.0, step = endval / 100.0;
        x = step;
        for (int i = 1; i < 100; i++) {
            //System.out.println("x" +x);
            //System.out.println("pot " + getPot(x,stucture,cavityType,phase));
            val += step * ((getPot(x, stucture, cavityType, phase) + 4 * getPot((x + 0.5 * step), stucture, cavityType, phase) + getPot(x + step, stucture, cavityType, phase)) / 6.0);
            x = i * step;
        }
        return val / 100000.0;
    }

    public double getPot(double radius, int struccture, int cavityType, PhaseInterface phase) {
        double pot = 2.0 * coordNumb[struccture][cavityType] * this.getLennardJonesEnergyParameterHydrate() * ((Math.pow(this.getLennardJonesMolecularDiameterHydrate(), 12.0) / (Math.pow(cavRadius[struccture][cavityType], 11.0) * radius) * (delt(10.0, radius, struccture, cavityType, this) + this.getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType] * delt(11.0, radius, struccture, cavityType, this)))
                - (Math.pow(this.getLennardJonesMolecularDiameterHydrate(), 6.0) / (Math.pow(cavRadius[struccture][cavityType], 5.0) * radius) * (delt(4.0, radius, struccture, cavityType, this) + this.getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType] * delt(5.0, radius, struccture, cavityType, this))));
        //System.out.println("lenjones " +this.getLennardJonesMolecularDiameterHydrate() );
        //System.out.println("pot bef " + pot);
        pot = Math.exp(-pot / (phase.getTemperature())) * radius * radius / 1.0e20;
        //System.out.println("pot " + pot);
        return pot;
    }

    public double delt(double n, double radius, int struccture, int cavityType, ComponentInterface comp) {
        double delt = 1.0 / n * (Math.pow(1.0 - radius / cavRadius[struccture][cavityType] - ((ComponentHydrate) comp).getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType], -n)
                - Math.pow(1.0 + radius / cavRadius[struccture][cavityType] - ((ComponentHydrate) comp).getSphericalCoreRadiusHydrate() / cavRadius[struccture][cavityType], -n));

        //System.out.println("delt " + delt);
        return delt;
    }

    public double[] getDGfHydrate() {
        return dGfHydrate;
    }

    public void setDGfHydrate(double[] dGfHydrate) {
        this.dGfHydrate = dGfHydrate;
    }

    public void setDGfHydrate(double dGfHydrate, int i) {
        this.dGfHydrate[i] = dGfHydrate;
    }

    public void setDHfHydrate(double dHfHydrate, int i) {
        this.dHfHydrate[i] = dHfHydrate;
    }

    public double[] getDHfHydrate() {
        return dHfHydrate;
    }

    public void setDHfHydrate(double[] dHfHydrate) {
        this.dHfHydrate = dHfHydrate;
    }

    public double getMolarVolumeHydrate(int structure, double temperature) {
        // taken from chem.eng.sci Avlonitis 1994
        double TO = 273.15;
        if (structure == 0) {
            double v0 = 22.35, k1 = 3.1075e-4, k2 = 5.9537e-7, k3 = 1.3707e-10;
            return v0 * (1.0 + k1 * (temperature - TO) + k2 * Math.pow(temperature - TO, 2.0) + k3 * Math.pow(temperature - TO, 3.0)) / 1.0e6;
        } else if (structure == 1) {
            double v0 = 22.57, k1 = 1.9335e-4, k2 = 2.1768e-7, k3 = -1.4786e-10;
            return v0 * (1.0 + k1 * (temperature - TO) + k2 * Math.pow(temperature - TO, 2.0) + k3 * Math.pow(temperature - TO, 3.0)) / 1.0e6;
        } else if (structure == -1) {
            double v0 = 19.6522, k1 = 1.6070e-4, k2 = 3.4619e-7, k3 = -1.4786e-10;
            return v0 * (1.0 + k1 * (temperature - TO) + k2 * Math.pow(temperature - TO, 2.0) + k3 * Math.pow(temperature - TO, 3.0)) / 1.0e6;
        } else {
            return 0.0;
        }
    }

    public double getSphericalCoreRadiusHydrate() {
        return sphericalCoreRadiusHydrate;
    }

    public void setSphericalCoreRadiusHydrate(double sphericalCoreRadiusHydrate) {
        this.sphericalCoreRadiusHydrate = sphericalCoreRadiusHydrate;
    }

    public /**
             * Creates new Class
             */
            double getLennardJonesEnergyParameterHydrate() {
        return lennardJonesEnergyParameterHydrate;
    }

    public void setLennardJonesEnergyParameterHydrate(double lennardJonesEnergyParameterHydrate) {
        this.lennardJonesEnergyParameterHydrate = lennardJonesEnergyParameterHydrate;
    }

    public double getLennardJonesMolecularDiameterHydrate() {
        return lennardJonesMolecularDiameterHydrate;
    }

    public void setLennardJonesMolecularDiameterHydrate(double lennardJonesMolecularDiameterHydrate) {
        this.lennardJonesMolecularDiameterHydrate = lennardJonesMolecularDiameterHydrate;
    }

    public void setSolidRefFluidPhase(PhaseInterface phase) {
        try {
            refPhase = phase.getClass().newInstance();
            refPhase.setTemperature(273.0);
            refPhase.setPressure(1.0);
            refPhase.addcomponent("water", 10.0, 10.0, 0);
            refPhase.init(refPhase.getNumberOfMolesInPhase(), 1, 0, 1, 1.0);
        } catch (Exception e) {
            logger.error("error occured", e);
        }
    }
//    public double dfugdt(PhaseInterface phase, int numberOfComps, double temp, double pres){
//        if(componentName.equals("water")){
//            double solvol = 1.0/getPureComponentSolidDensity(getMeltingPointTemperature())*molarMass;
//            dfugdt = Math.log((getEmptyHydrateStructureVapourPressuredT(hydrateStructure,temp))/pres);
//        } else dfugdt=0;
//        return dfugdt;
//    }
//
//    public double getEmptyHydrateStructureVapourPressure2(int type, double temperature){
//        double par1_struc1=4.6477;
//        double par2_struc1=-5242.979;
//        double par3_struc1=2.7789;
//        double par4_struc1=-8.7156e-3;
//        if(type==0){
//            return Math.exp(par1_struc1*Math.log(temperature)+par2_struc1/temperature+par3_struc1+par4_struc1*temperature)/1.0e5;
//        }
//        if(type==1){
//            return Math.exp(par1_struc2+par2_struc2/temperature)*1.01325;
//        } else return 0.0;
//    }
//
//    public double getEmptyHydrateStructureVapourPressure(int type, double temperature){
//
//        if(type==0){
//            return Math.exp(par1_struc1+par2_struc1/temperature)*1.01325;
//        }
//        if(type==1){
//            return Math.exp(par1_struc2+par2_struc2/temperature)*1.01325;
//        } else return 0.0;
//    }
//
//    public double getEmptyHydrateStructureVapourPressuredT(int type, double temperature){
//
//        if(type==0){
//            return -par2_struc1/(temperature*temperature)*Math.exp(par1_struc1+par2_struc1/temperature);
//        }
//        if(type==1){
//            return -par2_struc2/(temperature*temperature)*Math.exp(par1_struc2+par2_struc2/temperature);
//        } else return 0.0;
//    }

    /**
     * @return the cavprwat
     */
    public double getCavprwat(int structure, int cavityType) {
        return getCavprwat()[structure][cavityType];
    }

    /**
     * @return the cavprwat
     */
    public double[][] getCavprwat() {
        return cavprwat;
    }

}
