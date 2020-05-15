/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;

import java.io.Serializable;

/**
 *
 * @author koller
 */
public class AnnotatedSupertag implements Serializable {
    
    public final String graph;
    public final String type;
    public final double probability;

    public AnnotatedSupertag(String graph, String type, double probability) {
        this.graph = graph;
        this.type = type;
        this.probability = probability;
    }
    
    public boolean isNull() {
        return type==null || graph.equals("NULL"); // either is sufficient, and one of them might change in the future, so this seems safest. --JG
    }
    
    public String graphAndTypeString() {
        return graph+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+type;
    }

    @Override
    public String toString() {
        return String.format("%s\t%s\t%g", graph, type, probability);
    }
}
