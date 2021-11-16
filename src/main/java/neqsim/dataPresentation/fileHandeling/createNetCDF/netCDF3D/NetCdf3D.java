/*
 * NetCdf.java
 *
 * Created on 5. august 2001, 21:52
 */

package neqsim.dataPresentation.fileHandeling.createNetCDF.netCDF3D;

import java.io.IOException;
import ucar.ma2.Array;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;

/**
 *
 * @author esol
 * @version
 */
public class NetCdf3D {
    private static final long serialVersionUID = 1000;

    String fileName = "c:/temp/example.nc";
    double[] xvalues = new double[3];
    String xName = "xDefault", yName = "yDefault", zName = "zDefault";
    double[] yvalues = new double[4];
    double[][] zvalues = new double[4][4];
    double[][][] zvalues2 = new double[10][4][3];
    String[] zName2 = new String[10];
    NetcdfFileWriteable ncfile;
    int zLength = 0;

    /** Creates new NetCdf */
    public NetCdf3D() {}

    public void setOutputFileName(String name) {
        fileName = name;
    }

    public void setXvalues(double[] x, String name, String unit) {
        xvalues = x;
        xName = name;
    }

    public void setYvalues(double[] y, String name, String unit) {
        yvalues = y;
        yName = name;
    }

    public void setZvalues(double[][] z, String name, String unit) {
        zvalues = z;
        zName = name;

        zName2[zLength] = name;
        zvalues2[zLength] = z;
        zLength++;
    }

    public void createFile() {
        ncfile = new NetcdfFileWriteable();
        ncfile.setName(fileName);

        Dimension latD = ncfile.addDimension(xName, xvalues.length);
        Dimension lonD = ncfile.addDimension(yName, yvalues.length);

        Dimension[] dim2 = new Dimension[2];
        dim2[0] = latD;
        dim2[1] = lonD;

        for (int i = 0; i < zLength; i++) {
            ncfile.addVariable(zName2[i], double.class, dim2);
            ncfile.addVariableAttribute(zName2[i], "long_name", "surface temperature");
            ncfile.addVariableAttribute(zName2[i], "units", "degC");
        }

        ncfile.addVariable(latD.getName(), double.class, new Dimension[] {latD});
        ncfile.addVariableAttribute(latD.getName(), "units", "degrees_north");

        ncfile.addVariable(lonD.getName(), double.class, new Dimension[] {lonD});
        ncfile.addVariableAttribute(lonD.getName(), "units", "degrees_east");

        ncfile.addGlobalAttribute("title", "Example Data");

        try {
            ncfile.create();
        } catch (IOException e) {
            System.err.println("ERROR creating file");
        }

        try {
            for (int i = 0; i < zLength; i++) {
                ncfile.write(zName2[i], Array.factory(zvalues2[i]));
            }
            ncfile.write(latD.getName(), Array.factory(xvalues));
            ncfile.write(lonD.getName(), Array.factory(yvalues));
        } catch (Exception e) {
            System.err.println("ERROR writing file");
        }

        try {
            ncfile.close();
        } catch (IOException e) {
        }
        System.out.println("created " + fileName + " successfully");
    }
}
