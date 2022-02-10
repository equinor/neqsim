/*
 * Packing.java
 *
 * Created on 25. august 2001, 23:34
 */
package neqsim.fluidMechanics.geometryDefinitions.internalGeometry.packings;

/**
 * <p>
 * Packing class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class Packing implements PackingInterface {
    double voidFractionPacking = 0.951, size = 0, surfaceAreaPrVolume = 112.6;
    String name = null;

    /**
     * <p>
     * Constructor for Packing.
     * </p>
     */
    public Packing() {}

    /**
     * <p>
     * Constructor for Packing.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public Packing(String name) {
        this.name = name;
        try {
            System.out.println("init packing");
            neqsim.util.database.NeqSimDataBase database =
                    new neqsim.util.database.NeqSimDataBase();
            java.sql.ResultSet dataSet =
                    database.getResultSet(("SELECT * FROM packing WHERE name='" + name + "'"));
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

    /**
     * <p>
     * Constructor for Packing.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @param material a {@link java.lang.String} object
     * @param size a int
     */
    public Packing(String name, String material, int size) {
        this.name = name;
        try {
            System.out.println("init packing");
            neqsim.util.database.NeqSimDataBase database =
                    new neqsim.util.database.NeqSimDataBase();
            java.sql.ResultSet dataSet = database.getResultSet(("SELECT * FROM packing WHERE name='"
                    + name + "' AND size=" + size + " AND material='" + material + "'"));
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

    /** {@inheritDoc} */
    @Override
    public double getSurfaceAreaPrVolume() {
        return surfaceAreaPrVolume;
    }

    /** {@inheritDoc} */
    @Override
    public double getVoidFractionPacking() {
        return voidFractionPacking;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public double getSize() {
        return size;
    }
}
