/*
 * PhysicalPropertyMixingRule.java
 *
 * Created on 2. august 2001, 13:42
 */
package neqsim.physicalProperties.mixingRule;

import java.util.*;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import org.apache.log4j.Logger;

/**
 *
 * @author esol
 * @version
 */
public class PhysicalPropertyMixingRule implements PhysicalPropertyMixingRuleInterface, ThermodynamicConstantsInterface, java.io.Serializable {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(PhysicalPropertyMixingRule.class);

    public double[][] Gij;

    /**
     * Creates new PhysicalPropertyMixingRule
     */
    public PhysicalPropertyMixingRule() {
    }

    public double getViscosityGij(int i, int j) {
        return Gij[i][j];
    }

    public void setViscosityGij(double val, int i, int j) {
        Gij[i][j] = val;
    }

    public PhysicalPropertyMixingRuleInterface getPhysicalPropertyMixingRule() {
        return this;
    }

    public void initMixingRules2(PhaseInterface phase) {
        Gij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
        StringTokenizer tokenizer;
        String token;
        /*
        for(int k=0; k<phase.getNumberOfComponents(); k++){
            String nameOfComponent = phase.getComponents()[k].getComponentName();
            ClassLoader c1 = this.getClass().getClassLoader();
            String name = "data/componentData/" + nameOfComponent + ".txt";
            InputStreamReader reader = new InputStreamReader(c1.getResourceAsStream(name));
            BufferedReader file = new BufferedReader(reader);
            
            try	{
                c1 = this.getClass().getClassLoader();
                reader = new InputStreamReader(c1.getResourceAsStream(name));
                file = new BufferedReader(reader);
                long filepointer = 0;
                int index;
                //long length = file.length();
                String s;
                
                for(int l=0; l<phase.getNumberOfComponents(); l++){
                    if(l==k){
                        Gij[k][l]=0.0;
                    }
                    else{
                        //                        file = new RandomAccessFile(path, "r");
                        c1 = this.getClass().getClassLoader();
                        reader = new InputStreamReader(c1.getResourceAsStream(name));
                        file = new BufferedReader(reader);
                        s = file.readLine();
                        s = file.readLine();
                        s = file.readLine();
                        s = file.readLine();
                        do {
                            s = file.readLine();
                            tokenizer = new StringTokenizer(s);
                            index = Integer.parseInt(tokenizer.nextToken());
                        }
                        while (!(index==phase.getComponents()[l].getIndex()));
                        
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken() ;
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        tokenizer.nextToken() ;
                        tokenizer.nextToken();
                        tokenizer.nextToken();
                        Gij[k][l] = Double.parseDouble( tokenizer.nextToken()) ;
                    }
                }
                file.close();
            }
            catch (Exception e) {
                String err = e.toString();
                logger.error(err);
            }

        }
        **/
    }

    public void initMixingRules(PhaseInterface phase) {
        if (Gij != null) {
            return;
        }
        // logger.info("reading mix Gij viscosity..");
        Gij = new double[phase.getNumberOfComponents()][phase.getNumberOfComponents()];
        neqsim.util.database.NeqSimDataBase database = null;
        java.sql.ResultSet dataSet;
        for (int l = 0; l < phase.getNumberOfComponents(); l++) {
            String component_name = phase.getComponents()[l].getComponentName();
            for (int k = l; k < phase.getNumberOfComponents(); k++) {
                if (k == l || phase.getComponent(l).getIonicCharge() != 0) {
                    Gij[l][k] = 0.0;
                    Gij[k][l] = Gij[l][k];
                } else {
                    try {
                        database = new neqsim.util.database.NeqSimDataBase();
                        dataSet = database.getResultSet("SELECT * FROM INTERTEMP WHERE (COMP1='" + component_name + "' AND COMP2='" + phase.getComponents()[k].getComponentName() + "') OR (COMP1='" + phase.getComponents()[k].getComponentName() + "' AND COMP2='" + component_name + "')");
                        if (dataSet.next()) {
                            Gij[l][k] = Double.parseDouble(dataSet.getString("gijvisc"));
                        }
                        else{
                           Gij[l][k] = 0.0; 
                        }
                        Gij[k][l] = Gij[l][k];
                        database.getConnection().close();
                    } catch (Exception e) {
                        logger.error("err in phys prop.....");
                        String err = e.toString();
                        logger.error(err);
                    } finally {
                        try {
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
            }
        }
    }
}
