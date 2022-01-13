package neqsim.api.ioc.fluids;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.api.ioc.exceptions.NeqSimFractionsException;
import neqsim.thermo.system.SystemInterface;

/**
 *
 * @author jo.lyshoel
 */
public abstract class NeqSimAbstractFluid {

    /**
     * Add components to fluid
     * 
     * @param fluid
     */
    public abstract void addComponents(SystemInterface fluid);


    /**
     * Get component count
     * 
     * @return
     */
    public abstract int getComponentCount();

    /**
     * Get list of component names
     * 
     * @return
     */
    public abstract String[] getComponentNames();

    /**
     * Return component map from PVTsim to NeqSim components
     * 
     * @return
     */
    static LinkedHashMap<String, String> getComponentMap() {
        LinkedHashMap<String, String> c = new LinkedHashMap<>();
        c.put("H2O", "water");
        c.put("N2", "nitrogen");
        c.put("C1", "methane");
        c.put("C2", "ethane");
        c.put("C3", "propane");
        c.put("iC4", "i-butane");
        c.put("nC4", "n-butane");
        c.put("iC5", "i-pentane");
        c.put("nC5", "n-pentane");
        c.put("C6", "n-hexane");
        c.put("O2", "oxygen");
        c.put("He", "helium");
        c.put("H2", "hydrogen");
        c.put("Ar", "argon");
        c.put("H2S", "H2S");
        c.put("nC7", "n-heptane");
        c.put("nC8", "n-octane");
        c.put("nC9", "n-nonane");
        return c;
    }

    /**
     * Return the component name mapped from PVTsim. If not found, will return the same name. If
     * unsupported, NeqSim will return error
     * 
     * @param component
     * @return
     */
    static String getMappedComponent(String component) {
        try {
            String c = getComponentMap().get(component);
            return c != null ? c : component;
        } catch (Exception e) {
            return component;
        }
    }

    /**
     * Parse fractions and prepare for use in NeqSim
     * 
     * @param components
     * @param fractions
     * @return
     * @throws NeqSimFractionsException
     */
    public double[] parseFractions(String[] components, double[] fractions)
            throws NeqSimFractionsException {
        if (components.length != fractions.length)
            throw new NeqSimFractionsException("Components and fractions aren't the same length");


        Map<String, Double> newComponentList = new LinkedHashMap<>();
        for (String c : getComponentNames()) {
            newComponentList.put(c, 0.0);
        }

        for (int i = 0; i < components.length; i++) {
            String component = getMappedComponent(components[i]);
            if (!newComponentList.containsKey(component))
                throw new NeqSimFractionsException("Unsupported component " + component);

            newComponentList.put(component, fractions[i]);
        }

        double[] newFractionsList = new double[newComponentList.size()];
        int i = 0;
        for (Double d : newComponentList.values()) {
            newFractionsList[i] = (double) d;
            i++;
        }

        return newFractionsList;
    }

}
