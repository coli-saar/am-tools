package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amtools.decomposition.DecompositionPackage;

import java.io.IOException;
import java.util.List;

public abstract class GraphbankDecompositionToolset {

    protected final boolean useStanfordTagger;

    /**
     *
     * @param useStanfordTagger If useStanfordTagger is true, the decomposition packages will fill the empty NE/lemma/POS slots of
     * the amconll file with the Stanford NLP solution. Else, the slots remain empty.
     */
    public GraphbankDecompositionToolset(Boolean useStanfordTagger) {
        this.useStanfordTagger = false;
    }

    public abstract List<MRInstance> readCorpus(String filePath) throws IOException;

    /**
     * Applies any preprocessing you want to the corpus. Modifies the corpus in place. The default implementation
     * leaves the corpus unchanged, i.e. applies no preprocessing.
     * @param corpus
     */
    public void applyPreprocessing(List<MRInstance> corpus) {

    }

    public abstract DecompositionPackage makeDecompositionPackage(MRInstance instance); // maybe default implementation?

    public abstract AMRBlobUtils getEdgeHeuristics();// maybe default implementation?


}
