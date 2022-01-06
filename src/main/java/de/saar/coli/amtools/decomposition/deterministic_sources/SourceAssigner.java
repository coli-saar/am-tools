package de.saar.coli.amtools.decomposition.deterministic_sources;

public interface SourceAssigner {

    /**
     * For an AM dependency edge from parent to child with the operation being a MOD or APP operation,
     * which source name should be used? This abstract class handles the case where we make a deterministic
     * choice just on that information.
     * @param parent
     * @param child
     * @param operation starts with MOD or APP
     * @return
     */
    public abstract String getSourceName(int parent, int child, String operation);

}
