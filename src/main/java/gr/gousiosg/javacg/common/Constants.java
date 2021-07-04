package gr.gousiosg.javacg.common;

/**
 * @author adrninistrator
 * @date 2021/6/26
 * @description:
 */

public class Constants {

    public static final String FLAG_LAMBDA = "lambda$";

    public static final int FLAG_LAMBDA_LENGTH = FLAG_LAMBDA.length();

    public static final String CALL_TYPE_INTERFACE = "ITF";
    public static final String CALL_TYPE_LAMBDA = "LM";
    public static final String CALL_TYPE_RUNNABLE_INIT_RUN = "RIR";
    public static final String CALL_TYPE_SUPER_CALL_CHILD = "SCC";
    public static final String CALL_TYPE_CHILD_CALL_SUPER = "CCS";

    private Constants() {
        throw new IllegalStateException("illegal");
    }
}
