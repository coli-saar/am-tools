package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;

import java.util.List;

public class PSDDecompositionToolset extends SDPDecompositionToolset {

    AMRBlobUtils blobUtils = new PSDBlobUtils();

    /**
     * @param useStanfordTagger If useStanfordTagger is true, the decomposition packages will fill the empty NE/lemma/POS slots of
     *                          the amconll file with the Stanford NLP solution. Else, the slots remain empty.
     */
    public PSDDecompositionToolset(Boolean useStanfordTagger) {
        super(useStanfordTagger);
    }

    @Override
    public void applyPreprocessing(List<MRInstance> corpus) {
        for (MRInstance inst : corpus) {
            inst.setGraph(ConjHandler.handleConj(inst.getGraph(), (PSDBlobUtils)getEdgeHeuristics(), false));
        }
    }

    @Override
    public AMRBlobUtils getEdgeHeuristics() {
        return blobUtils;
    }
}
