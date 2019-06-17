/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

/**
 *
 * @author koller
 */
public class AnnotatedSupertag {
    
    public String graph;
    public String type;
    public double probability;

    public AnnotatedSupertag(String graph, String type, double probability) {
        this.graph = graph;
        this.type = type;
        this.probability = probability;
    }
    
}
