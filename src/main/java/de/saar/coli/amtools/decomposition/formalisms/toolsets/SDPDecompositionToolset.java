package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amtools.decomposition.DecompositionPackage;
import de.saar.coli.amtools.decomposition.SDPDecompositionPackage;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract GraphbankDecompositionToolset baseclass for
 */
public abstract class SDPDecompositionToolset extends GraphbankDecompositionToolset {

    @Override
    public List<MRInstance> readCorpus(String filePath) throws IOException {
        GraphReader2015 gr = new GraphReader2015(filePath);
        List<MRInstance> ret = new ArrayList<>();
        Graph sdpGraph = null;
        while ((sdpGraph = gr.readGraph()) != null) {
            ret.add(SGraphConverter.toSGraph(sdpGraph));
        }
        return ret;
    }

    @Override
    public DecompositionPackage makeDecompositionPackage(MRInstance instance) {
        return null;//new SDPDecompositionPackage();
    }

    @Override
    public AMRBlobUtils getEdgeHeuristics() {
        return null;
    }
}
