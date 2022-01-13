package neqsim.api.ioc;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author jo.lyshoel
 */
@XmlRootElement
public class CalcResponse {

    public boolean success;
    public String errormessage;
    public String underlying_error;
    public Double[][] calcresult;
    public String[] calcerrors;

    public CalcResponse() {}

    public CalcResponse(CalculationResult calcresult) {
        this.success = true;
        this.calcresult = calcresult.fluidProperties;
        this.calcerrors = calcresult.calculationError;
    }

    public CalcResponse(Exception ex) {
        try {
            this.errormessage = ex.getMessage();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            this.underlying_error = sw.toString();
        } catch (Exception e) {
        }
    }
}
