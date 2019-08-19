package de.saar.coli.amrtagging.formalisms.amr.tools.preproc;

public class PreprocessingException extends Exception {
    public PreprocessingException() {
        super();
    }

    public PreprocessingException(String message) {
        super(message);
    }

    public PreprocessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PreprocessingException(Throwable cause) {
        super(cause);
    }

    protected PreprocessingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
