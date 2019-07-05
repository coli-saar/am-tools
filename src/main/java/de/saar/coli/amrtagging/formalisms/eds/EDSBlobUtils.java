/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author matthias
 */
public class EDSBlobUtils extends AMRBlobUtils{
    
    public static final String MOD = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.MOD;
    public static final String SUBJ = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
    public static final String OBJ = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
    public static final String POSS = de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.POSS;
    public static final String COMP = "comp";
    public static final String DET = "det";
    public static final String COORD = "op";
    //public static final String SUBORD = "sub";
    
    public static final Set<String> EXCEPTIONS_TARGET = new HashSet<String>(){{
        add("udef_q");
        add("nominalization"); //this rule is really bs but it helps us decompose more graphs.
    }};
    
    public static final Set<String> EXCEPTIONS_SOURCE = new HashSet<String>(){{
    }};
    
    
    @Override
    public boolean isOutbound(GraphEdge edge) {
        if (EXCEPTIONS_TARGET.contains(edge.getTarget().getLabel())) return false;
        if (EXCEPTIONS_SOURCE.contains(edge.getSource().getLabel())) return false;
        return true;
    }
    
    
         
     @Override
     public String edge2Source(GraphEdge edge, SGraph graph){
        switch (edge.getLabel()) {
            case "ARG1": return SUBJ;
            case "ARG2": return OBJ;
            case "BV": return DET;
            case "R-INDEX": return COORD+"2";
            case "L-INDEX": return COORD+"1";
            case "R-HNDL": return COORD+"2";
            case "L-HNDL": return COORD+"1";
            default:
                if (edge.getLabel().startsWith("ARG")) {
                    return OBJ;  
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
                Set<GraphEdge> edges = graph.getGraph().edgesOf(n).stream().filter(edge -> !edge.getLabel().equals("BV")).collect(Collectors.toSet());
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

     @Override
    public String getCoordRegex(){
        return "[RL]-(HNDL|INDEX)";
    }
    
    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        return edgeLabel.matches(getCoordRegex());
    }

    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        return node.getLabel().matches("_.*_c");
    }
     
}
