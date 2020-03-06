/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.Util;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.simple.Sentence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.jgrapht.graph.DefaultEdge;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;

/**
 * Converts an SDP graph into an SGraph. The main method produces an instance that can be fed to the AlignmentTrackingAutomaton.
 * @author matthias
 */
public class SGraphConverter {
    
    public static final String ARTIFICAL_ROOT_LABEL = "ART-ROOT";
    public static final String ROOT_EDGE_LABEL = "art-snt";
    
    public static final String PREFIX = "i_";
    
    public static final String SENSE_SEP = "~~";
    
    public static boolean READABLE_NODE_LABELS = true; //if set to true the word form will be added to the node label (instead of just using the word sense)
    
    private static final String[] RESERVED_SUBSTRINGS = {SENSE_SEP, AlignedAMDependencyTree.ALIGNED_SGRAPH_SEP};
    
    /**
     * Takes an SDP graph and returns an MRInstance.
     * @param sdpGraph
     * @return 
     */
    public static MRInstance toSGraph(Graph sdpGraph){
        
        DirectedGraph<Node,Edge> jg = new DirectedAcyclicGraph(DefaultEdge.class);
        for (Node n :sdpGraph.getNodes()){
            jg.addVertex(n);
        }
        for (Edge e:sdpGraph.getEdges()){
            jg.addEdge(sdpGraph.getNode(e.source), sdpGraph.getNode(e.target), e);
        }
        
        SGraph sg =  new SGraph();
        ArrayList<String> words = new ArrayList<>();
        for (Node word : jg.vertexSet()){
            if (word.id > 0){
                words.add(word.id-1,word.form);
            }
            
        }
        ArrayList<Alignment> alignments = new ArrayList<>();
        
        sg.addNode(ARTIFICAL_ROOT_LABEL, ARTIFICAL_ROOT_LABEL);
        sg.addSource("root",ARTIFICAL_ROOT_LABEL);
        ConnectivityInspector<Node, Edge> inspector =  new ConnectivityInspector(jg);
        Sentence sent = null;
        int sntCounter = 1;
        for (Set<Node> connectedSet : inspector.connectedSets()){
            if (connectedSet.size() > 1){
                boolean foundTop = false;
                for (Node n : connectedSet){
                    if (READABLE_NODE_LABELS){
                        String nodeLabel = Util.fixPunct(n.form);  //for now...??? If we want to train a parser, we might want remove the word form!
                        if (! NodelabelOk(nodeLabel)) throw new IllegalArgumentException("The node with label'"+nodeLabel+"' contains a forbidden substring");
                        sg.addNode(PREFIX+String.valueOf(n.id), nodeLabel+SENSE_SEP+n.sense);
                    } else {
                        sg.addNode(PREFIX+String.valueOf(n.id), SENSE_SEP+n.sense);
                    }
                    alignments.add(new Alignment(PREFIX+String.valueOf(n.id), n.id-1));
                    if (n.isTop) {
                        sg.addEdge(sg.getNode(ARTIFICAL_ROOT_LABEL), sg.getNode(PREFIX+String.valueOf(n.id)), ROOT_EDGE_LABEL+String.valueOf(sntCounter));
                        foundTop = true;
                     }
                }
                // if the component didn't have top node find the head of the span (corenlp) of this component (assumes this component of the graph forms a contiguous span)
                // and make it the root of this subgraph
                if (! foundTop){
                    if (sent == null){
                        sent = new Sentence(words);
                    }
                    //throw new IllegalArgumentException("This graph is connected please comment in the corenlp stuff.");
                    int firstPos = connectedSet.stream().min((Node n1,Node n2) -> Integer.compare(n1.id, n2.id)).get().id;
                    int lastPos = connectedSet.stream().max((Node n1,Node n2) -> Integer.compare(n1.id, n2.id)).get().id;
                    edu.stanford.nlp.ie.machinereading.structure.Span thisSpan = new edu.stanford.nlp.ie.machinereading.structure.Span(firstPos,lastPos);
                    int head = sent.algorithms().headOfSpan(thisSpan) + 1; //corenlp uses 0 addressing, we use 1-addressing
                    if (connectedSet.stream().anyMatch((Node n) -> n.id == head)){ //ok the head of the span is actually in the current component
                        sg.addEdge(sg.getNode(ARTIFICAL_ROOT_LABEL), sg.getNode(PREFIX+String.valueOf(head)), ROOT_EDGE_LABEL+String.valueOf(sntCounter));
                    } else { //otherwise, just pick the first node of the component to be the root of this component
                        sg.addEdge(sg.getNode(ARTIFICAL_ROOT_LABEL), sg.getNode(PREFIX+String.valueOf(firstPos)), ROOT_EDGE_LABEL+String.valueOf(sntCounter));
                    }  
                }
                sntCounter++;
            }
        }
        for (Edge e: jg.edgeSet()){
            sg.addEdge(sg.getNode(PREFIX+String.valueOf(e.source)), sg.getNode(PREFIX+String.valueOf(e.target)), e.label);
        }
        words.add(ARTIFICAL_ROOT_LABEL);
        alignments.add(new Alignment(ARTIFICAL_ROOT_LABEL, words.size()-1));
        return new MRInstance(words,sg,alignments);
    }
    
    /**
     * Converts an SGraph constructed from an AM dependency tree (set align=true!) into an SDPGraph.The Graph sentence provides the words and maybe additional info like POS tags.
     * @param sg
     * @param sentence
     * @return 
     */
    public static Graph toSDPGraph(SGraph sg, Graph sentence){
       Graph output = new Graph(sentence.id);
       GraphNode artRoot = sg.getNode(ARTIFICAL_ROOT_LABEL);
       ArrayList<GraphNode> realRoots = new ArrayList<>();
       HashSet<GraphNode> predicates = new HashSet<>();
       for (GraphEdge edg : sg.getGraph().edgeSet()){ //find real roots and predicates
           if (edg.getSource().equals(artRoot)){
               realRoots.add(edg.getTarget());
           } else {
               predicates.add(edg.getSource()); //predicates are things with outgoing edges
           }
       }
       HashMap<Integer,GraphNode> int2node = new HashMap<>();
       for (GraphNode n : sg.getGraph().vertexSet()){
           if (! n.equals(artRoot)){
               Pair<Integer,Pair<String,String>> triple = AlignedAMDependencyTree.decodeNode(n);
                int2node.put(triple.getLeft(),n);
                
           }
           
       }
       for (Node n: sentence.getNodes()){ //add all nodes
           if (int2node.containsKey(n.id)){
               GraphNode correspondingGraphNode = int2node.get(n.id);
               Pair<Integer,Pair<String,String>> triple = AlignedAMDependencyTree.decodeNode(correspondingGraphNode);
               String[] wordPlusSense = triple.getRight().getRight().split(SENSE_SEP);
               Node currentNode = sentence.getNode(triple.getLeft());
               //isTop is theoretically independent of being a predicate but the vast majority of top nodes is are also predicate nodes.
               boolean isTop = realRoots.contains(correspondingGraphNode) && (realRoots.size() == 1 || predicates.contains(correspondingGraphNode));
               if (wordPlusSense.length == 2) {
                   output.addNode(currentNode.form, currentNode.lemma, currentNode.pos, isTop, predicates.contains(correspondingGraphNode), wordPlusSense[1]);
               } else {
                   output.addNode(currentNode.form, currentNode.lemma, currentNode.pos, isTop, predicates.contains(correspondingGraphNode), triple.getRight().getRight());
               }
               
           } else {
               output.addNode(n.form, n.lemma, n.pos, false, false, "_"); //???
           }
       }

        for (GraphEdge edg : sg.getGraph().edgeSet()){ //add all edges
            if (! edg.getSource().getLabel().contains(ARTIFICAL_ROOT_LABEL) && ! edg.getTarget().getLabel().contains(ARTIFICAL_ROOT_LABEL)){
                Pair<Integer,Pair<String,String>> tripleSource = AlignedAMDependencyTree.decodeNode(edg.getSource());
                Pair<Integer,Pair<String,String>> tripleTarget = AlignedAMDependencyTree.decodeNode(edg.getTarget());
                output.addEdge(tripleSource.getLeft(), tripleTarget.getLeft(), edg.getLabel());
            }
        }
        return output;
        
    }
    
    private static boolean NodelabelOk(String label){
        for (String s: RESERVED_SUBSTRINGS){
            if (label.contains(s)) return false;
        }
        return true;
    }
    
    public static List<String> extractPOS(Graph g){
        ArrayList<String> pos = new ArrayList<>();
        for (Node n : g.getNodes()){
            if (n.id > 0){
                pos.add(n.pos);
            }
        }
        return pos;
    }
    
    public static List<String> extractLemmas(Graph g){
        ArrayList<String> lemmas = new ArrayList<>();
        for (Node n : g.getNodes()){
            if (n.id > 0){
                lemmas.add(n.lemma);
            }
        }
        return lemmas;
    }
    
    
}
