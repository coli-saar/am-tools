package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;

public class PASDecompositionToolset extends SDPDecompositionToolset {

    EdgeAttachmentHeuristic edgeAttachmentHeuristic = new PASBlobUtils();

    /**
     * @param fasterModeForTesting If fasterModeForTesting is true, the decomposition packages will not fill the empty NE/lemma/POS slots of
     *                              the amconll file with the Stanford NLP solution.
     */
    public PASDecompositionToolset(Boolean fasterModeForTesting) {
        super(fasterModeForTesting, "pas");
    }


    @Override
    public EdgeAttachmentHeuristic getEdgeHeuristics() {
        return edgeAttachmentHeuristic;
    }
}
