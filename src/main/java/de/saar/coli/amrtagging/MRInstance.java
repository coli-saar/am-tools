/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.List;

/**
 * An instance of a Meaning Representation that will be given to the AlignmentTrackingAutomaton
 * @author matthias
 */
public class MRInstance {
    
    private List<String> sentence;
    private SGraph graph;
    private List<Alignment> alignments;
    
    public MRInstance(List<String> sentence, SGraph sg, List<Alignment> alignments){
        this.sentence = sentence;
        this.graph = sg;
        this.alignments = alignments;
    }

    /**
     * @return the sentence
     */
    public List<String> getSentence() {
        return sentence;
    }

    /**
     * @return the graph
     */
    public SGraph getGraph() {
        return graph;
    }
    
    public void setGraph(SGraph sg){
        this.graph = sg;
    }

    /**
     * @return the alignments
     */
    public List<Alignment> getAlignments() {
        return alignments;
    }
    
}
