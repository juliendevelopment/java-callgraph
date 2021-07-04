package gr.gousiosg.javacg.util;

import gr.gousiosg.javacg.common.Constants;
import org.apache.bcel.generic.Type;

/**
 * @author adrninistrator
 * @date 2021/6/22
 * @description:
 */

public class CommonUtil {

    public static boolean isInnerAnonymousClass(String className) {
        if (!className.contains("$")) {
            return false;
        }

        String[] array = className.split("\\$");
        if (array.length != 2) {
            return false;
        }

        if (!isNumStr(array[1])) {
            return false;
        }
        return true;
    }

    public static boolean isNumStr(String str) {
        char[] charArray = str.toCharArray();
        for (char ch : charArray) {
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    public static String argumentList(Type[] arguments) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < arguments.length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(arguments[i].toString());
        }
        sb.append(")");
        return sb.toString();
    }

    public static String getLambdaOrigMethod(String lambdaMethod) {
        int indexLastLambda = lambdaMethod.lastIndexOf(Constants.FLAG_LAMBDA);
        String tmpString = lambdaMethod.substring(indexLastLambda + Constants.FLAG_LAMBDA_LENGTH);
        int indexDollar = tmpString.indexOf('$');
        return tmpString.substring(0, indexDollar);
    }

    private CommonUtil() {
        throw new IllegalStateException("illegal");
    }
}
// added end