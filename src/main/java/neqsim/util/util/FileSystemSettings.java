/*
 * FileSystemSettings.java
 *
 * Created on 3. november 2001, 17:55
 */

package neqsim.util.util;

/**
 *
 * @author esol
 * @version
 */
public interface FileSystemSettings {
    String root = "c:";
    String tempDir = root + "/temp/";
    String defaultFileTreeRoot = root + "/Program Files/NeqSim/pythonScript/";
    String defaultDatabaseRootRoot = root + "/java/NeqSim/util/database";
    String relativeFilePath = root;
    String fileExtension = ".py";
}
