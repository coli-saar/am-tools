package de.saar.coli.amtools.decomposition;

public abstract class SourceAssigner {

    /**
     *
     * @param parent
     * @param child
     * @param operation starts with MOD or APP
     * @return
     */
    public abstract String getSourceName(int parent, int child, String operation);

}
