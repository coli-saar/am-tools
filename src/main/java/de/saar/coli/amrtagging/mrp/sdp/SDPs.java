/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;

import static de.saar.coli.amrtagging.Util.fixPunct;
import de.saar.coli.amrtagging.mrp.graphs.MRPAnchor;
import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author matthias
 */
public abstract class SDPs implements Formalism {
    

    @Override
    public ConlluSentence refine(ConlluSentence sentence) {
        // tokenization is OK but we have to lower case lemmata
        ConlluSentence copy = sentence.withSameMetaData();
        for (ConlluEntry e : sentence.copy()){
            e.setLemma(fixPunct(e.getLemma().toLowerCase()));
            copy.add(e);
        }
        return copy;
    }
    

    @Override
    public MRPGraph preprocess(MRPGraph mrpgraph) {
        //we have to encode the POS and frame attributes into the nodes.
        //format: label_prop1=val1_prop2=val2
        
        MRPGraph copy = mrpgraph.deepCopy();
        
        MRPUtils.encodePropertiesInLabels(copy);
        
        return copy;
    }

    @Override
    public MRPGraph postprocess(MRPGraph mrpgraph) {
        MRPGraph copy = mrpgraph.deepCopy();
        
        MRPUtils.decodePropertiesInLabels(copy);
        
        //SDP also adds artifical root, so remove it here
        return MRPUtils.removeArtificalRoot(copy);
    }
    
    
    /**
     * Converts an SGraph coming from a SDP corpus into an MRP graph.
     * @param evaluatedGraph
     * @param s
     * @return 
     */
    protected MRPGraph sGraphToMRP(SGraph evaluatedGraph, AmConllSentence s){
            MRPGraph output = new MRPGraph();
            output.sanitize();
            output.setId(s.getId());
            output.setFlavor(0); //flavor of SDP graphs
            String input = s.getAttr("raw");
            if (input == null){
                input = s.getAttr("input");
            }
            output.setInput(input);
            output.setVersion(s.getAttr("version"));
            output.setTime(s.getAttr("time"));

            //add nodes in the order they appear in the sentence
            Map<Integer,String> id2node = new HashMap<>();
            Map<String,Integer> node2id = new HashMap<>();
            for (String node : evaluatedGraph.getAllNodeNames()){
                Pair<Integer,Pair<String,String>> triple = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(node));
                int position = triple.left-1; //position we get from triple is 1-based (AmConllEntry)
                id2node.put(position,node);
                node2id.put(node,position);
            }
            for (int id : id2node.keySet().stream().sorted().collect(Collectors.toList())){
                Pair<Integer,Pair<String,String>> triple = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(id2node.get(id)));
                int position = triple.left-1; //position we get from triple is 1-based (AmConllEntry)
                String label = triple.right.right;
                List<MRPAnchor> anchors = new ArrayList<>();
                anchors.add(MRPAnchor.fromTokenRange(s.get(position).getRange()));
                output.getNodes().add(new MRPNode(position,label,new ArrayList<>(),new ArrayList<>(),anchors));
            }
            
            //add top node (ART-ROOT will be done later)
            Set<Integer> tops = new HashSet<>();
            String rootName = evaluatedGraph.getNodeForSource("root");
            tops.add(node2id.get(rootName));
            output.setTops(tops);
            
            //add edges
            for (GraphEdge e : evaluatedGraph.getGraph().edgeSet() ){
                output.getEdges().add(new MRPEdge(node2id.get(e.getSource().getName()),node2id.get(e.getTarget().getName()), e.getLabel()));
            }
            
            return output;
    }
    
    
    @Override
    public void refineDelex(AmConllSentence sentence){
        return; // we don't need this possibility
    }
    
}
