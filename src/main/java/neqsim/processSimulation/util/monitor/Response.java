package neqsim.processSimulation.util.monitor;

import java.util.HashMap;
import java.util.Map;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.NamedBaseClass;
import com.google.gson.Gson; 

public class Response {
 
    public String toJson() throws Exception {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

}