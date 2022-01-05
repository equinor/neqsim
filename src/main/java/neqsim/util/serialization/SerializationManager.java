/*
 * SerializationManager.java
 *
 * Created on 27. desember 2002, 00:10
 */
package neqsim.util.serialization;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * <p>
 * SerializationManager class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class SerializationManager {
    /**
     * Creates a new instance of SerializationManager
     */
    public SerializationManager() {}

    /**
     * <p>
     * save.
     * </p>
     *
     * @param obj a {@link java.lang.Object} object
     * @param name a {@link java.lang.String} object
     */
    public static void save(Object obj, String name) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(name))) {
            out.writeObject(obj);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    /**
     * <p>
     * open.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    public static Object open(String name) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(name))) {
            return in.readObject();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return null;
    }
}
