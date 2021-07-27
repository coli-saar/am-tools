package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;

import java.util.List;

public class PSDDecompositionToolsetNoPreprocessing extends PSDDecompositionToolset {


    /**
     * @param useStanfordTagger If useStanfordTagger is true, the decomposition packages will fill the empty NE/lemma/POS slots of
     *                          the amconll file with the Stanford NLP solution. Else, the slots remain empty.
     */
    public PSDDecompositionToolsetNoPreprocessing(Boolean useStanfordTagger) {
        super(useStanfordTagger);
    }

    @Override
    public void applyPreprocessing(List<MRInstance> corpus) {
        // do nothing
    }
}
