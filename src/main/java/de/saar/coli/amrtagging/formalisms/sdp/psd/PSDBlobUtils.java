/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.psd;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.sdp.dm.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.Set;

/**
 *
 * @author matthias
 */
public class PSDBlobUtils extends AMRBlobUtils{
    
    public static final String MOD = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.MOD;
    public static final String SUBJ = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
    public static final String OBJ = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
    public static final String POSS = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.POSS;
    public static final String OP = "op";
    public static final String OTHER_OBJ = "oo";
    
     //candidates?  "discourse"
    public static final String[] OUTBOUND_EDGES = new String[]{"[A-Z]+-arg", "[A-Z]+\\.member", "CPR", SGraphConverter.ROOT_EDGE_LABEL+"[0-9]*"};
    
    
    @Override
    public boolean isOutbound(GraphEdge edge) {
        for (String regex : OUTBOUND_EDGES) {
            if (edge.getLabel().matches(regex)) {
                return true;
            }
        }
        return false;   
    }
    
    
         
     @Override
     public String edge2Source(GraphEdge edge, SGraph graph){
        switch (edge.getLabel()) {
            case "ACT-arg": return SUBJ;
            case "PAT-arg": return OBJ;
            case "APP": return POSS;
            default:
                if (edge.getLabel().endsWith("-arg")) {
                    return OTHER_OBJ; //TODO what about passive here?
                } else if (edge.getLabel().endsWith(".member")) {
                    return OP;//TODO make numbers in getSourceAssignments take word order into account to break ties among same edge labels
                } else if (edge.getLabel().startsWith(SGraphConverter.ROOT_EDGE_LABEL)) {
                    return edge.getLabel();
                } else {
                    return MOD;
                }
        }
    }
    
     @Override
    public double scoreGraph(Pair<SGraph, ApplyModifyGraphAlgebra.Type> g){
        return scoreGraphPassiveSpecial(g.left);
    }
    
    private double scoreGraphPassiveSpecial(SGraph graph) {
        double ret = 1.0;
        for (String s : graph.getAllSources()) {
            if (s.matches(OBJ+"[0-9]+")) {
                double oNr = Integer.parseInt(s.substring(OBJ.length()));
                ret /= oNr;
            }
            if (s.equals(SUBJ)) {
                GraphNode n = graph.getNode(graph.getNodeForSource(s));
                Set<GraphEdge> edges = graph.getGraph().edgesOf(n);
                if (!edges.isEmpty()) {
                    if (edges.size() > 1) {
                        System.err.println("***WARNING*** more than one edge at node "+n);
                        System.err.println(edges);
                    }
                    GraphEdge e = edges.iterator().next();
                    if (e.getLabel().equals("ACT-arg")) {
                        ret *= 2.0;
                    } else if (e.getLabel().equals("PAT-arg")) {
                        ret *= 1.5;
                    }
                } else {
                    System.err.println("***WARNING*** no edges at node "+n);
                }
            }
        }
        return ret;
    }
     
    
    
    
    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        return edgeLabel != null && edgeLabel.endsWith(".member");
    }

    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        // return true if there is at least two outgoing conj edges
        int conjEdgesCount = 0;
        for (GraphEdge e : graph.getGraph().outgoingEdgesOf(node)) {
            if (isConjEdgeLabel(e.getLabel())) {
                 conjEdgesCount++;
            }
        }
        return conjEdgesCount >= 2;
    }
}
