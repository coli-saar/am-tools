package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.SDPDecompositionPackage;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract GraphbankDecompositionToolset baseclass for the SDP corpora. Handles reading the corpus
 */
public abstract class SDPDecompositionToolset extends GraphbankDecompositionToolset {

    private final String framework;

    /**
     * @param fasterModeForTesting If fasterModeForTesting is true, the decomposition packages will not fill the empty NE/lemma/POS slots of
     *                          the amconll file with the Stanford NLP solution.
     */
    public SDPDecompositionToolset(Boolean fasterModeForTesting, String framework) {
        super(fasterModeForTesting);
        this.framework = framework;
    }

    @Override
    public List<MRInstance> readCorpus(String filePath) throws IOException {
        GraphReader2015 gr = new GraphReader2015(filePath);
        List<MRInstance> ret = new ArrayList<>();
        Graph sdpGraph = null;
        while ((sdpGraph = gr.readGraph()) != null) {
            MRInstance mrInstance = SGraphConverter.toSGraph(sdpGraph);
            mrInstance.setExtra("id", sdpGraph.id);
            ret.add(mrInstance);
        }
        return ret;
    }

    @Override
    public DecompositionPackage makeDecompositionPackage(MRInstance instance) {
        SDPDecompositionPackage ret = new SDPDecompositionPackage(instance, getEdgeHeuristics(), fasterModeForTesting);
        ret.setFramework(framework);
        return ret;
    }


}
