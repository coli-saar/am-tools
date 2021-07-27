package de.saar.coli.amtools.decomposition.formalisms.toolsets;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amtools.decomposition.DecompositionPackage;

import java.io.IOException;
import java.util.List;

public abstract class GraphbankDecompositionToolset {

    public abstract List<MRInstance> readCorpus(String filePath) throws IOException;

    public List<MRInstance> applyPreprocessing(List<MRInstance> instances) {
        return instances;
    }

    public abstract DecompositionPackage makeDecompositionPackage(MRInstance instance); // maybe default implementation?

    public abstract AMRBlobUtils getEdgeHeuristics();// maybe default implementation?


}
