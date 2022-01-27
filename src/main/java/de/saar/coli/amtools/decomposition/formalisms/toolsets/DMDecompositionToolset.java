package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amtools.decomposition.formalisms.EdgeAttachmentHeuristic;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;

public class DMDecompositionToolset extends SDPDecompositionToolset {

    EdgeAttachmentHeuristic edgeAttachmentHeuristic = new DMBlobUtils();

    /**
     * @param fasterModeForTesting If fasterModeForTesting is true, the decomposition packages will not fill the empty NE/lemma/POS slots of
     *                             the amconll file with the Stanford NLP solution.
     */
    public DMDecompositionToolset(Boolean fasterModeForTesting) {
        super(fasterModeForTesting, "dm");
    }


    @Override
    public EdgeAttachmentHeuristic getEdgeHeuristics() {
        return edgeAttachmentHeuristic;
    }
}
