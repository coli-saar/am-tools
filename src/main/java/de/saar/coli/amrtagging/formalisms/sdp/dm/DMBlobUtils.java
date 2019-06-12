/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.dm;

import de.saar.basic.Pair;
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
public class DMBlobUtils extends AMRBlobUtils{
    
    public static final String MOD = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.MOD;
    public static final String SUBJ = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
    public static final String OBJ = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
    public static final String POSS = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.POSS;
    public static final String COMP = "comp";
    public static final String DET = "det";
    public static final String COORD = "coord";
    //public static final String SUBORD = "sub";
    
     //candidates?  "discourse"
    public static final String[] OUTBOUND_EDGES = new String[]{"ARG[0-9]*","neg","of", "than", "appos",SGraphConverter.ROOT_EDGE_LABEL+"[0-9]*","BV","subord","compound",
        "part","poss","loc","times", "comp", "measure", "paranthetical", "comp_[a-z+]*", "temp", "manner", "discourse"};
    
    
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
            case "ARG1": return SUBJ;
            case "ARG2": return OBJ;
            case "poss": return POSS;
            case "compound": case "mwe": return COMP;
            case "BV": return DET;
            case "conj": return COORD;
            //case "subord": return SUBORD;
            default:
                if (edge.getLabel().startsWith("ARG")) {
                    return OBJ + String.valueOf(Integer.parseInt(edge.getLabel().substring("ARG".length()))-1);  
                } else if (edge.getLabel().endsWith("_c")) {
                    return COORD;
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
                    if (e.getLabel().equals("ARG1")) {
                        ret *= 2.0;
                    } else if (e.getLabel().equals("ARG2")) {
                        ret *= 1.5;
                    }
                } else {
                    System.err.println("***WARNING*** no edges at node "+n);
                }
            }
        }
        return ret;
    }
     
}
