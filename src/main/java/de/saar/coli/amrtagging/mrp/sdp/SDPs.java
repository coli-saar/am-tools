/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.sdp;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.Util;
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
    
    public static final String SEPARATOR ="__";
    public static final String EQUALS = "=";

    @Override
    public ConlluSentence refine(ConlluSentence sentence) {
        // tokenization is OK but we have to lower case lemmata
        ConlluSentence copy = new ConlluSentence();
        copy.setLineNr(sentence.getLineNr());
        for (ConlluEntry e : sentence){
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
        
        for (MRPNode n : copy.getNodes()){
            for (int i = 0; i < n.getProperties().size();i++){
                n.setLabel(fixPunct(n.getLabel())+SEPARATOR+n.getProperties().get(i)+EQUALS+n.getValues().get(i));
            }
            n.setProperties(new ArrayList<>());
            n.setValues(new ArrayList<>());
        }
        
        return copy;
    }

    @Override
    public MRPGraph postprocess(MRPGraph mrpgraph) {
        MRPGraph copy = mrpgraph.deepCopy();
        
        for (MRPNode n : copy.getNodes()){
            String[] parts = n.getLabel().split(SEPARATOR);
            n.setLabel(Util.unfixPunct(parts[0])); //original label
            for (int i = 1; i < parts.length;i++){
                String[] keyVal = parts[i].split(EQUALS);
                if (n.getProperties() == null){
                    n.setProperties(new ArrayList<>());
                }
                n.getProperties().add(keyVal[0]);
                if (n.getValues() == null){
                    n.setValues(new ArrayList<>());
                }
                n.getValues().add(keyVal[1]);
            }
        }
        //SDP also adds artifical root, so remove it here
        return MRPUtils.removeArtificalRoot(copy);
    }
    
    
    /**
     * Converts an SGraph coming from SDP into an MRP graph.
     * @param evaluatedGraph
     * @param s
     * @return 
     */
    protected MRPGraph sGraphToMRP(SGraph evaluatedGraph, ConllSentence s){
            MRPGraph output = new MRPGraph();
            output.sanitize();
            output.setId(s.getId());
            output.setFlavor(0); //flavor of SDP graphs
            output.setInput(s.getAttr("raw"));
            output.setVersion(s.getAttr("version"));
            output.setTime(s.getAttr("time"));

            //add nodes in the order they appear in the sentence
            Map<Integer,String> id2node = new HashMap<>();
            Map<String,Integer> node2id = new HashMap<>();
            for (String node : evaluatedGraph.getAllNodeNames()){
                Pair<Integer,Pair<String,String>> triple = AMDependencyTree.decodeNode(evaluatedGraph.getNode(node));
                int position = triple.left-1; //position we get from triple is 1-based (ConllEntry)
                String label = triple.right.right;
                id2node.put(position,node);
                node2id.put(node,position);
            }
            for (int id : id2node.keySet().stream().sorted().collect(Collectors.toList())){
                Pair<Integer,Pair<String,String>> triple = AMDependencyTree.decodeNode(evaluatedGraph.getNode(id2node.get(id)));
                int position = triple.left-1; //position we get from triple is 1-based (ConllEntry)
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
    
}
