package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;

import java.io.IOException;
import java.util.List;

public abstract class GraphbankDecompositionToolset {

    protected final boolean fasterModeForTesting;

    /**
     *
     * @param fasterModeForTesting If fasterModeForTesting is true, then slow preprocessing measures should be skipped.
     *                             In the default implementation, this skips getting POS, lemma and named entity tags via the
     *                             stanford tagger (implementations of this class may change the exact details).
     */
    public GraphbankDecompositionToolset(Boolean fasterModeForTesting) {
        this.fasterModeForTesting = fasterModeForTesting;
    }

    public abstract List<MRInstance> readCorpus(String filePath) throws IOException;

    /**
     * Applies any preprocessing you want to the corpus. Modifies the corpus in place. The default implementation
     * leaves the corpus unchanged, i.e. applies no preprocessing.
     * @param corpus
     */
    public void applyPreprocessing(List<MRInstance> corpus) {

    }

    public DecompositionPackage makeDecompositionPackage(MRInstance instance) {
        return new DecompositionPackage(instance, getEdgeHeuristics(), fasterModeForTesting);
    }

    public abstract AMRBlobUtils getEdgeHeuristics();// maybe default implementation?


}
