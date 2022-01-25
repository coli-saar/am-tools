package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;

public class DMDecompositionToolset extends SDPDecompositionToolset {

    AMRBlobUtils blobUtils = new DMBlobUtils();

    /**
     * @param fasterModeForTesting If fasterModeForTesting is true, the decomposition packages will not fill the empty NE/lemma/POS slots of
     *                             the amconll file with the Stanford NLP solution.
     */
    public DMDecompositionToolset(Boolean fasterModeForTesting) {
        super(fasterModeForTesting);
    }


    @Override
    public AMRBlobUtils getEdgeHeuristics() {
        return blobUtils;
    }
}
