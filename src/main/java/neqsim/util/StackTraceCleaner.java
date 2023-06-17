package neqsim.util;

import java.util.ArrayList;

public class StackTraceCleaner {

  public static StackTraceElement[] cleanStackTrace(StackTraceElement[] st, String packageRoot) {
    ArrayList<StackTraceElement> list = new ArrayList<>(0);

    for (StackTraceElement el : st) {
      if (el.getClassName().startsWith(packageRoot)) {
        list.add(el);
      }
    }

    StackTraceElement[] filtered_st = new StackTraceElement[list.size()];

    return list.toArray(filtered_st);
  }
}
