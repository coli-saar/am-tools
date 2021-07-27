/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An instance of a Meaning Representation that will be given to the AlignmentTrackingAutomaton
 * @author matthias
 */
public class MRInstance {
    
    private List<String> sentence;
    private List<String> lemmas;
    private List<String> posTags;
    private List<String> neTags;
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

    public List<String> getLemmas() {
        return lemmas;
    }

    public List<String> getPosTags() {
        return posTags;
    }

    public List<String> getNeTags() {
        return neTags;
    }

    public void setLemmas(List<String> lemmas) {
        this.lemmas = lemmas;
    }

    public void setPosTags(List<String> posTags) {
        this.posTags = posTags;
    }

    public void setNeTags(List<String> neTags) {
        this.neTags = neTags;
    }

    /**
     * @return the alignments
     */
    public List<Alignment> getAlignments() {
        return alignments;
    }
    
    
    /**
     * Gives for every node the number of alignments it participates in.
     * @return 
     */
    public Map<String,Integer> getAlignmentCounter(){
       Map<String,Integer> counter = new HashMap<>();
        for (String node : graph.getAllNodeNames()){
            counter.put(node, 0);
        }
        
        for (Alignment al : alignments){
            for (String node : al.nodes){
                counter.put(node, counter.get(node)+1);
            }
        }
        return counter;
    }
    
    /**
     * Returns true iff every node is aligned to exactly one token.
     * @throws de.saar.coli.amrtagging.MRInstance.UnalignedNode
     * @throws de.saar.coli.amrtagging.MRInstance.MultipleAlignments
     */
    public void checkEverythingAligned() throws UnalignedNode, MultipleAlignments{
        
        Map<String,Integer> counter = getAlignmentCounter();
        
        for (String node : graph.getAllNodeNames()){
            if (counter.get(node) == 0){
                throw new UnalignedNode(node+" with label "+graph.getNode(node).getLabel());
            } else if (counter.get(node) > 1){
                throw new MultipleAlignments(node+" with label "+graph.getNode(node).getLabel());
            }
        }
        
    }
    
    public class UnalignedNode extends Exception {
        public UnalignedNode(String msg){
            super(msg);
        }
        
    }
    
    public class MultipleAlignments extends Exception {
        
        public MultipleAlignments(String msg){
            super(msg);
        }
        
    }
    
}
