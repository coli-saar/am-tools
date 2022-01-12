package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;

public class DMDecompositionToolset extends SDPDecompositionToolset {

    AMRBlobUtils blobUtils = new DMBlobUtils();

    /**
     * @param useStanfordTagger If useStanfordTagger is true, the decomposition packages will fill the empty NE/lemma/POS slots of
     *                          the amconll file with the Stanford NLP solution. Else, the slots remain empty.
     */
    public DMDecompositionToolset(Boolean useStanfordTagger) {
        super(useStanfordTagger);
    }


    @Override
    public AMRBlobUtils getEdgeHeuristics() {
        return blobUtils;
    }
}
