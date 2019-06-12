/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.pas;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.sdp.dm.*;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.DOMAIN;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.MOD;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.OBJ;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.POSS;
import static de.saar.coli.amrtagging.formalisms.AMSignatureBuilder.SUBJ;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author matthias
 */
public class PASBlobUtils extends AMRBlobUtils{
    
    
    public static final String DET="det";
    public static final String PUNCT="pnct";
    
    
    @Override
    public boolean isOutbound(GraphEdge edge) {
        return true;
    }
    
    
         
     @Override
     public String edge2Source(GraphEdge edge, SGraph graph){
         String label = edge.getLabel();
         if (label.startsWith("op") || label.startsWith(SGraphConverter.ROOT_EDGE_LABEL)) {
                    return edge.getLabel();
         } else if (label.startsWith("det") ) {
             return DET;
         } else if (label.startsWith("punct")){
             return PUNCT;
         } else if (label.startsWith("adj")){
             return MOD;
         } else if (label.startsWith("coord_ARG")){
             return "op"+label.substring(label.length()-1);
         }
         String secondPart = label.split("_")[1];
        switch(secondPart){
            case "ARG1": if (label.startsWith("verb")) return SUBJ; else return OBJ;
            case "ARG2": return OBJ;
            default:  if (edge.getLabel().startsWith("ARG")) {
                   return OBJ + edge.getLabel().substring("ARG".length());                 
                    }  else {
                        return MOD;
                    }

        }
         
     }
     
    
     
    @Override
    public String getCoordRegex(){
        return "coord_ARG[0-9]+";
    }
    
    @Override
    public boolean isConjEdgeLabel(String edgeLabel) {
        return edgeLabel.matches(getCoordRegex()) || edgeLabel.contains("ARG");
    }
    
    @Override
    public boolean isConjunctionNode(SGraph graph, GraphNode node) {
        for (GraphEdge edg : graph.getGraph().edgeSet()){
            if (edg.getSource().equals(node)){
                if (edg.getLabel().matches(getCoordRegex())){
                    return true;
                }
            }
        }
        return false;
    }
    
    
     /**
     * A function that assigns a weight to each constant. Used for scoring source assignments according to heuristic preferences in the ACL 2018 experiments.
     * @param g
     * @return 
     */
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
                    GraphEdge e = edges.iterator().next();
                    if (e.getLabel().equals("verb_ARG1")) {
                        ret *= 2.0;
                    } else if (e.getLabel().equals("noun_ARG1")) {
                        ret *= 0.5;
                    }
                } else {
                    System.err.println("***WARNING*** no edges at node "+n);
                }
            }
        }
        return ret;
    }
    
    
}
