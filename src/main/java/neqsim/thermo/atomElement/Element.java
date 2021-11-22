/*
 * Element.java
 *
 * Created on 4. februar 2001, 22:11
 */

package neqsim.thermo.atomElement;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * @author Even Solbraa
 * @version
 */
public class Element implements ThermodynamicConstantsInterface {
    private static final long serialVersionUID = 1000;
    String[] nameArray;
    double[] coefArray;
    static Logger logger = LogManager.getLogger(Element.class);

    /** Creates new Element */
    public Element() {}

    public Element(String name) {
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<String> stocCoef = new ArrayList<String>();
        neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
        try {
            java.sql.ResultSet dataSet = database
                    .getResultSet(("SELECT * FROM element WHERE componentname='" + name + "'"));
            dataSet.next();
            // System.out.println("comp name " + dataSet.getString("componentname"));
            do {
                names.add(dataSet.getString("atomelement").trim());
                // System.out.println("name " + dataSet.getString("atomelement"));
                stocCoef.add(dataSet.getString("number"));
            } while (dataSet.next());

            nameArray = new String[names.size()];
            coefArray = new double[nameArray.length];
            for (int i = 0; i < nameArray.length; i++) {
                coefArray[i] = Double.parseDouble((String) stocCoef.get(i));
                nameArray[i] = (String) names.get(i);
            }
            dataSet.close();
            database.getConnection().close();
        } catch (Exception e) {
            try {
                database.getConnection().close();
            } catch (Exception ex) {
                logger.error(ex);
            }
            String err = e.toString();
            logger.error(err);
            // System.out.println(err);
        }
    }

    public String[] getElementNames() {
        return nameArray;
    }

    public double[] getElementCoefs() {
        return coefArray;
    }
}
