package de.saar.coli.amtools.evaluation.toolsets;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;

public class PSDEvaluationToolset extends SDPEvaluationToolset {

    private final PSDBlobUtils blobUtils = new PSDBlobUtils();

    @Override
    public void applyPostprocessing(MRInstance mrInstance, AmConllSentence origAMConllSentence) {
        super.applyPostprocessing(mrInstance, origAMConllSentence);
        mrInstance.setGraph(ConjHandler.restoreConj(mrInstance.getGraph(), blobUtils, false));
    }

}
