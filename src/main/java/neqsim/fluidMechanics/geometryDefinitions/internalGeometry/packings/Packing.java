/*
 * Packing.java
 *
 * Created on 25. august 2001, 23:34
 */

package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 *
 * @author esol
 * @version
 */
public class Packing implements PackingInterface {

    private static final long serialVersionUID = 1000;

    double voidFractionPacking = 0.951, size = 0, surfaceAreaPrVolume = 112.6;
    String name = null;

    /** Creates new Packing */
    public Packing() {
    }

    public Packing(String name) {
        name = name;
        try {
            System.out.println("init packing");
            neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
            java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM packing WHERE name='" + name + "'"));
            dataSet.next();
            size = 1e-3 * Double.parseDouble(dataSet.getString("size")); // C
            surfaceAreaPrVolume = Double.parseDouble(dataSet.getString("surfaceAreaPrVolume"));
            voidFractionPacking = Double.parseDouble(dataSet.getString("voidFraction"));
            System.out.println("packing ok");
        }

        catch (Exception e) {
            String err = e.toString();
            System.out.println(err);
        }
    }

    public Packing(String name, String material, int size) {
        name = name;
        try {
            System.out.println("init packing");
            neqsim.util.database.NeqSimDataBase database = new neqsim.util.database.NeqSimDataBase();
            java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM packing WHERE name='" + name
                    + "' AND size=" + size + " AND material='" + material + "'"));
            dataSet.next();
            this.size = 1e-3 * Double.parseDouble(dataSet.getString("size")); // C
            surfaceAreaPrVolume = Double.parseDouble(dataSet.getString("surfaceAreaPrVolume"));
            voidFractionPacking = Double.parseDouble(dataSet.getString("voidFraction"));
            System.out.println("packing ok");
        }

        catch (Exception e) {
            String err = e.toString();
            System.out.println(err);
        }
    }

    @Override
	public double getSurfaceAreaPrVolume() {
        return surfaceAreaPrVolume;
    }

    /**
     * Getter for property voidFractionPacking.
     * 
     * @return Value of property voidFractionPacking.
     */
    @Override
	public double getVoidFractionPacking() {
        return voidFractionPacking;
    }

    /**
     * Setter for property voidFractionPacking.
     * 
     * @param voidFractionPacking New value of property voidFractionPacking.
     */
    @Override
	public void setVoidFractionPacking(double voidFractionPacking) {
        this.voidFractionPacking = voidFractionPacking;
    }

    /**
     * Setter for property size.
     * 
     * @param size New value of property size.
     */
    public void setSize(double size) {
        this.size = size;
    }

    /**
     * Get size in mm
     * 
     * @param size
     */
    @Override
	public double getSize() {
        return size;
    }

}
