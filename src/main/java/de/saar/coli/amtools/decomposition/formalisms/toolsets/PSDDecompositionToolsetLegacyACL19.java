package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;

import java.util.List;

public class PSDDecompositionToolsetLegacyACL19 extends PSDDecompositionToolset {


    /**
     * @param useStanfordTagger If useStanfordTagger is true, the decomposition packages will fill the empty NE/lemma/POS slots of
     *                          the amconll file with the Stanford NLP solution. Else, the slots remain empty.
     */
    public PSDDecompositionToolsetLegacyACL19(Boolean useStanfordTagger) {
        super(useStanfordTagger);
    }

    @Override
    public void applyPreprocessing(List<MRInstance> corpus) {
        for (MRInstance inst : corpus) {
            inst.setGraph(ConjHandler.handleConj(inst.getGraph(), (PSDBlobUtils)getEdgeHeuristics(), true));
        }
    }

}
