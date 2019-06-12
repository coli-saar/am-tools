/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.List;

/**
 *
 * @author Jonas
 */
public class PSDConcreteSignatureBuilder extends ConcreteAlignmentSignatureBuilder {
    
    public PSDConcreteSignatureBuilder(SGraph sGraph, List<Alignment> alignments, AMRBlobUtils blobUtils) {
        super(sGraph, alignments, blobUtils);
    }

    @Override
    protected boolean isRaising(GraphNode node, String s, GraphNode nestedArg, GraphNode blobTarget) {
        GraphEdge blobEdge = sGraph.getGraph().getEdge(node, blobTarget);
        return blobEdge != null
                && ((blobEdge.getLabel().equals("PAT-arg")
                       && !node.getLabel().equals("ART-ROOT") && s.equals(SUBJ))
                    || blobEdge.getLabel().equals("CPR")); // comparator structure is similar to raising structure
        
    }
    
    
    
}
