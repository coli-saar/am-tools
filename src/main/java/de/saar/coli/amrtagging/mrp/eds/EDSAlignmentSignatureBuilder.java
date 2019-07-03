/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.eds;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import static de.saar.coli.amrtagging.formalisms.eds.EDSConverter.COMPLEX_SPAN;
import static de.saar.coli.amrtagging.formalisms.eds.EDSConverter.SIMPLE_SPAN;
import static de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements special hacks into the AlignmentSignatureBuilder that prevents us from using LNK nodes as roots.
 * This is not pretty :(
 * @author matthias
 */
public class EDSAlignmentSignatureBuilder extends ConcreteAlignmentSignatureBuilder {
    
    public EDSAlignmentSignatureBuilder(SGraph sGraph, List<Alignment> alignments, AMRBlobUtils blobUtils) {
        super(sGraph, alignments, blobUtils);
    }
    
    @Override
    protected GraphNode getPreferredRoot(SGraph graph, Set<GraphNode> inNodes, Alignment al) {
        if (!al.lexNodes.isEmpty()) {
                return graph.getNode(al.lexNodes.iterator().next());
        } else {
            Set<String> nonLNKNodes = al.nodes.stream().
                    filter( node -> !graph.getNode(node).getLabel().equals(COMPLEX_SPAN) &&
                           !graph.getNode(node).getLabel().equals(SIMPLE_SPAN) ).collect(Collectors.toSet());
            if (!nonLNKNodes.isEmpty()){
                return graph.getNode(nonLNKNodes.iterator().next());
            }
            return graph.getNode(al.nodes.iterator().next());
        }
    }
    
}
