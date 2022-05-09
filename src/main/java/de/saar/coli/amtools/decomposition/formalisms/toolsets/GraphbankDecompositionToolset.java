package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
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

    /**
     * Applies preprocessing, like applyPreprocessing(List<MRInstance> corpus), but if readCorpusOnlyInput leaves
     * alignments and graphs as null, then this function should be able to deal with it. The default implementation
     * simply calls applyPreprocessing(List<MRInstance> corpus), since the default readCorpusOnlyInput also reads
     * the graphs and alignments.
     * @param corpus
     */
    public void applyPreprocessingOnlyInput(List<MRInstance> corpus) {
        applyPreprocessing(corpus);
    }

    public DecompositionPackage makeDecompositionPackage(MRInstance instance) {
        return new DecompositionPackage(instance, getEdgeHeuristic(), fasterModeForTesting);
    }

    public abstract EdgeAttachmentHeuristic getEdgeHeuristic();// maybe default implementation?

    /**
     * Reads a corpus but is only required to store the sentences, not the alignments and graphs. I.e. in all
     * MRInstance objects, the alignment and graph fields may be (but don't have to be) null. The default implementation
     * simply calls readCorpus(String filePath).
     * @param filePath
     * @return
     * @throws IOException
     */
    public List<MRInstance> readCorpusOnlyInput(String filePath) throws IOException {
        return readCorpus(filePath);
    }

}
