/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.Alignment.Span;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.TokenRange;

import static de.saar.coli.amrtagging.formalisms.eds.Aligner.preprocessHyphens;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import edu.stanford.nlp.simple.Sentence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 * A class that provides methods for deleting and restoring lnks in EDS.
 * @author matthias
 */
public class EDSConverter {
    
    public static final String COMPLEX_SPAN = "COMPLEX"; //token that will be inserted into lnk-nodes that denote a complex span
    public static final String SIMPLE_SPAN = "SIMPLE" ; 
    
    public static final String HNDL = "-HNDL";
    public static final String INDEX = "-INDEX";
    
    public static final String EXPL = "explicitanon";
    
    
    /**
     * Prepares MRInstance, ACL 2019.
     * @param eds
     * @param sent
     * @return 
     */
    public static MRInstance toSGraph(AnchoredSGraph eds, String sent){
        eds = makeNodeNamesExplicit(eds); //this renames some node names so they will be printed correctly and smatch counts them in the right way.
        Pair< List<TokenRange> , List<String>> tokSameForHyphens = EDSUtils.edsTokenizeString(sent, true);
        Pair< List<TokenRange> , List<String> > tokDifferentForHyphens = EDSUtils.edsTokenizeString(sent, false);
        preprocessHyphens(eds, tokSameForHyphens, tokDifferentForHyphens);
        Sentence stanfSent = new Sentence(tokDifferentForHyphens.right);
        MRInstance inst = Aligner.extractAlignment(eds, tokDifferentForHyphens, stanfSent.lemmas());
        
        inst.setGraph(encodeLnks((AnchoredSGraph) inst.getGraph()));
        
        deleteHndls(inst.getGraph());
        
        return inst;
    }
    
    public static AnchoredSGraph evaluateDependencyTree(AmConllSentence sent) throws AMDependencyTree.ConllParserException, ParserException, ParseException{
        AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
        SGraph evaluatedWithAlignmentsGraph = amdep.evaluate(true);
        List<Alignment> als = AMDependencyTree.extractAlignments(evaluatedWithAlignmentsGraph);
        Pair p = EDSUtils.edsTokenizeString(sent.getAttr("raw"), true);
        List<String> words = (List<String>) p.right;
        List<TokenRange> stanfSpans = (List<TokenRange>) p.left;
        SGraph evaluated = evaluatedWithAlignmentsGraph;
        AMDependencyTree.stripAlignments(evaluated); //now alignment info is not present in nodes anymore

        //now we replace the placeholder SIMPLE_SPAN with its character span
        restoreSimpleSpans(evaluated, words,stanfSpans, als);
        
        //Tree<Set<String>> treeWithNodeNames = amdep.isomorphicTreeWithNodeNames(evaluated, als);
        //now we can replace the complex spans:
        AnchoredSGraph evaluatedGraph = restoreComplexSpans(evaluated);
        //SGraph evaluatedGraph = restoreComplexSpans(treeWithNodeNames, evaluated,stanfSpans, als);

//        MRInstance inst = new MRInstance(words, evaluatedGraph, als);
//        System.err.println(inst.getGraph().toIsiAmrStringWithSources());
//        System.err.println(EDSUtils.simpleAlignViz(inst));
        
        evaluatedGraph =  undoExplicitAnon(evaluatedGraph);
        return evaluatedGraph;
        
        /**
         * Idee:
         * Abbildung Tokenspannen -> Buchstabenspannen
         * Tokenspannen -> Knotennamen
         * Evaluieren des Dependenzbaumes mit Tokenspannen. 
         */
    }
    
    public static AnchoredSGraph ensureRootIsWritable(AnchoredSGraph sg){
        AnchoredSGraph copy;
        if (!sg.getNodeForSource("root").startsWith("_")){
            copy = sg.copy();
        } else {
            String oldRoot = sg.getNodeForSource("root");
            String newRoot = "newRoot_"+oldRoot;
            copy = new AnchoredSGraph();
            for(String nodeName : sg.getAllNodeNames()){
                String label = sg.getNode(nodeName).getLabel();
                if (nodeName.equals(oldRoot)){
                    nodeName = newRoot;
                }
                copy.addNode(nodeName,label);
            }
            for(String source : sg.getAllSources()){
                String node = sg.getNodeForSource(source);
                if (node.equals(oldRoot)){
                    node = newRoot;
                }
                copy.addSource(source, node);
            }
            for(GraphEdge e : sg.getGraph().edgeSet()){
                String label = e.getLabel();
                String src = e.getSource().getName();
                if (src.equals(oldRoot)){
                    src = newRoot;
                }
                String tgt = e.getTarget().getName();
                if (tgt.equals(oldRoot)){
                    tgt = newRoot;
                }
                copy.addEdge(sg.getNode(src), sg.getNode(tgt), label);
            }
 
        }
        return copy;
    }
    
    
    
    /**
     * Copies s-graph and renames nodes that start with _ to $EXPL_ followed by what they were named. 
     * This function is useful because the variable names of nodes that start with _ will not be printed by the AMR-ISI codec but we need that (they are represented as strings in the decomp automaton)
     * @param eds
     * @return 
     */
    public static AnchoredSGraph makeNodeNamesExplicit(AnchoredSGraph eds){
        AnchoredSGraph copy = new AnchoredSGraph();
        for (String n : eds.getAllNodeNames()){
            String name = n;
            if (n.startsWith("_")){
                name = EXPL + n;
            }
            copy.addNode(name, eds.getNode(n).getLabel());
            for (String source : eds.getSourcesAtNode(n)){
                copy.addSource(source, name);
            }
        }
        for (GraphEdge edge : eds.getGraph().edgeSet()){
            String src = edge.getSource().getName();
            String tgt = edge.getTarget().getName();
            if (src.startsWith("_")){
                src = EXPL + src;
            }
            if (tgt.startsWith("_")){
                tgt = EXPL + tgt;
            }
            copy.addEdge(copy.getNode(src), copy.getNode(tgt), edge.getLabel());
        }
        return copy;
    }
    
    /**
     * Undoes the renaming step for every node whose node label is hidden in ISI-AMR.
     * @param eds
     * @return 
     */
    public static AnchoredSGraph undoExplicitAnon(AnchoredSGraph eds){
        AnchoredSGraph copy = new AnchoredSGraph();
        for (String n : eds.getAllNodeNames()){
            String name = n;
            if (n.startsWith(EXPL) && hideNodeName(name, eds)){
                name = n.substring(EXPL.length()); 
            }
            copy.addNode(name, eds.getNode(n).getLabel());
            for (String source : eds.getSourcesAtNode(n)){
                copy.addSource(source, name);
            }
        }
        for (GraphEdge edge : eds.getGraph().edgeSet()){
            String src = edge.getSource().getName();
            String tgt = edge.getTarget().getName();
            if (src.startsWith(EXPL) && hideNodeName(src, eds) ){
                src = src.substring(EXPL.length()); 
            }
            if (tgt.startsWith(EXPL) && hideNodeName(tgt, eds)){
                tgt = tgt.substring(EXPL.length());
            }
            copy.addEdge(copy.getNode(src), copy.getNode(tgt), edge.getLabel());
        }
        return copy;
    }
    
    
    /**
     * Returns if the node name "node" should be hidden when printing the graph to ISI-AMR (Smatch artifact).
     * @param node
     * @param eds
     * @return 
     */
    private static boolean hideNodeName(String node, SGraph eds){
        return eds.getSourcesAtNode(node).isEmpty() && eds.getGraph().inDegreeOf(eds.getNode(node)) > 0 && eds.getGraph().incomingEdgesOf(eds.getNode(node)).stream().allMatch(edge -> AnchoredSGraph.isLnkEdge(edge) || edge.getLabel().equals("carg"));
    }
    
    /**
     * Deletes the character span information and writes SIMPLE if the span has no subspan and COMPLEX otherwise.
     * @param eds
     * @return 
     */
    public static SGraph encodeLnks(AnchoredSGraph eds){
        SGraph copy = eds.merge(new SGraph());
        Set<String> complexSpans = eds.getComplexSpans().stream().map(pair -> "<"+pair.getFrom()+":"+pair.getTo()+">").collect(Collectors.toSet());
        List<String> deleteMe = new ArrayList<>();
        for (GraphNode n : copy.getGraph().vertexSet()){
            if (AnchoredSGraph.isLnkNode(n)){
                deleteMe.add(n.getName());
                if (complexSpans.contains(n.getLabel())){
                    n.setLabel(COMPLEX_SPAN);
                } else {
                    n.setLabel(SIMPLE_SPAN);
                }
            }
        }
        //deleteMe.forEach(copy::removeNode);
        return copy;
    }
    
    
    /**
     * Deletes HNDL edges unless that makes the graph disconnected.
     * 
     * @param eds 
     */
    public static void deleteHndls(SGraph eds){
        List<GraphEdge> edges = new ArrayList<>(eds.getGraph().edgeSet());
        for (GraphEdge e : edges){
            
            if (e.getLabel().endsWith(HNDL)){
               eds.getGraph().removeEdge(e);
               try {
                   eds.toIsiAmrString();
               } catch (UnsupportedOperationException ex) { //we caused damage (disconnected graph), undo it!
                   eds.addEdge(e.getSource(), e.getTarget(), e.getLabel());
               }
            }
        }
    }
    
    
    /**
     * Replaces SIMPLE statements with character spans from alignment
     * @param sg
     * @param words
     * @param stanfSpans
     * @param als 
     */
    public static void restoreSimpleSpans(SGraph sg, List<String> words,List<TokenRange> stanfSpans, List<Alignment> als){
        for (Alignment a : als){
            for (String nodeName : a.nodes){
                if (sg.getNode(nodeName).getLabel().equals(SIMPLE_SPAN)){
                    Optional<GraphNode> nodeThatSpansSeveralTokens = sg.getGraph().incomingEdgesOf(sg.getNode(nodeName)).stream().map(edge -> edge.getSource()).filter(node -> node.getLabel().contains("+")).findFirst();
                    if (nodeThatSpansSeveralTokens.isPresent()){
                        //a node with label _for+example_p or so
                        //go leftwards from the current word until you find the first "word" and the same do on the right
                        Span mySpan = a.span; //token span
                        String[] subwords = nodeThatSpansSeveralTokens.get().getLabel().split("\\+");
                        subwords[0] = subwords[0].substring(1); //remove _
                        subwords[subwords.length-1] = subwords[subwords.length-1].split("_")[0]; //remove everything after _
                        //subwords[0] = for
                        //subwords[1] = example
                        //in our example
                        String rightmost = subwords[subwords.length-1];
                        //to the right:
                        int r = a.span.start;
                        for (int i = a.span.start;i < words.size(); i++){
                            if (words.get(i).toLowerCase().equals(rightmost)) {
                                r = i;
                                break;
                            }
                        }
                        String leftmost = subwords[0];
                        //to the left:
                        int l = a.span.start;
                        for (int i = a.span.start;i >= 0; i--){
                            if (words.get(i).toLowerCase().equals(leftmost)) {
                               l = i;
                                break;
                            }
                        }
                        TokenRange charSpanL = stanfSpans.get(l);
                        TokenRange charSpanR = stanfSpans.get(r);
                        sg.getNode(nodeName).setLabel("<"+charSpanL.getFrom()+":"+charSpanR.getTo()+">");   
                    } else {
                        Span mySpan = a.span; //token span
                        assert mySpan.isSingleton();
                        TokenRange charSpan = stanfSpans.get(mySpan.start);
                        sg.getNode(nodeName).setLabel("<"+charSpan.getFrom()+":"+charSpan.getTo()+">");   
                    }
                }
            }
        }
    }
    
    
    /**
     * Does what it is supposed to do but it's not good at its job.
     * @param treeWithNodeNames
     * @param eds
     * @param stanfSpans
     * @param als
     * @return
     * @deprecated
     */
    @Deprecated
    public static AnchoredSGraph restoreComplexSpans(Tree<Set<String>> treeWithNodeNames, SGraph eds,List<Pair<Integer,Integer>> stanfSpans, List<Alignment> als) {
        AnchoredSGraph copy = (AnchoredSGraph) eds.merge(new SGraph());
        treeWithNodeNames.dfs(new TreeBottomUpVisitor<Set<String>, Pair<Integer,Integer>>(){
            @Override
            public Pair<Integer, Integer> combine(Tree<Set<String>> node, List<Pair<Integer, Integer>> childrenValues) {
                //identify character span of my nodes:
                for (Alignment al : als){
                    if (al.nodes.equals(node.getLabel())){
                        childrenValues.add(stanfSpans.get(al.span.start));
                    }
                }
                //find minimum and maximum of character spans over me and my children:
                int min = childrenValues.stream().map(p -> p.left).min(Integer::compare).get();
                int max = childrenValues.stream().map(p -> p.right).max(Integer::compare).get();
                
                for (String nodeName : node.getLabel()){
                    if (copy.getNode(nodeName).getLabel().equals(COMPLEX_SPAN)){
                        copy.getNode(nodeName).setLabel("<"+min+":"+max+">");
                    }
                }
                return new Pair<>(min,max);
                
            }
            
        });
        return copy;
    }
      /**
     * Restores complex spans by assuming that we can do so by taking the minimum position of all children and the maximal position of all children.
     * Assumes that all simple spans are filled in and some nodes with label COMPLEX_SPAN are still there (both cases deleting HNDL edges).
     * Precision: 0.99
       Recall: 0.97
       F-score: 0.98
       compared to no restoring and simply deleting all complex spans:
        Precision: 0.97
        Recall: 0.94
        F-score: 0.96
     * @param eds
     * @return 
     */
    public static AnchoredSGraph restoreComplexSpans(SGraph eds){
        AnchoredSGraph copy = AnchoredSGraph.fromSGraph(eds);
        DirectedMultigraph<GraphNode,GraphEdge> g = copy.getGraph();
        TopologicalOrderIterator it = new TopologicalOrderIterator(copy.getGraph());
        ArrayList<GraphNode> topo = new ArrayList();
        it.forEachRemaining(n -> topo.add((GraphNode) n)); 
        Collections.reverse(topo); //reverse topological order children first, then parents
        
        HashMap<String,TokenRange> node2span =  new HashMap(); //maps a node (name) to the character span it covers
        
        //first add all simple spans
        for (GraphNode node : copy.getGraph().vertexSet()){
            Optional<GraphEdge> perhapsLnkEdge =  g.outgoingEdgesOf(node).stream().filter(e -> e.getLabel().equals(AnchoredSGraph.LNK_LABEL)).findFirst();
            if (perhapsLnkEdge.isPresent() && AnchoredSGraph.isLnkNode(perhapsLnkEdge.get().getTarget())) {
                //parse the label into an Int,Int pair and store it.
                node2span.put(node.getName(),AnchoredSGraph.getRangeOfLnkNode(perhapsLnkEdge.get().getTarget()));
            }
        }
        
        for (GraphNode node : topo){
            if (!g.incomingEdgesOf(node).stream().anyMatch(e -> e.getLabel().equals("carg"))) { //carg nodes don't get spans
                if (g.outDegreeOf(node) > 0) {
                    Optional<GraphEdge> outgoingLnkEdgeOptional = g.outgoingEdgesOf(node).stream().filter(e -> AnchoredSGraph.isLnkEdge(e)).findFirst();
                    if (outgoingLnkEdgeOptional.isPresent() && g.outgoingEdgesOf(node).stream().anyMatch(e -> e.getTarget().getLabel().equals(COMPLEX_SPAN))) {
                        //this node has a complex span at its lnk edge
                        int min;
                        int max;
                        if (g.outDegreeOf(node) < 2 || g.outgoingEdgesOf(node).stream().filter(e -> !e.getLabel().equals(AnchoredSGraph.LNK_LABEL)).anyMatch(e -> !node2span.containsKey(e.getTarget().getName()))) { 
                            //we can't because this node has no real children or there is a child which doesn't have a span yet (this kind of modelling failed!)
                            copy.removeNode(outgoingLnkEdgeOptional.get().getTarget().getName());
                            continue;
                            
                        } else {
                            min = g.outgoingEdgesOf(node).stream().filter(e -> !e.getLabel().equals(AnchoredSGraph.LNK_LABEL)).
                                    map(e -> node2span.get(e.getTarget().getName()).getFrom()).min(Integer::compare).get();
                            max = g.outgoingEdgesOf(node).stream().filter(e -> !e.getLabel().equals(AnchoredSGraph.LNK_LABEL)).
                                    map(e -> node2span.get(e.getTarget().getName()).getTo()).max(Integer::compare).get();
                        }
                        node2span.put(node.getName(), new TokenRange(min,max));

                        copy.setLnk(copy.getNode(outgoingLnkEdgeOptional.get().getTarget().getName()), new TokenRange(min,max)); //.outgoingLnkEdge.getTarget().setLabel("<"+min+":"+max+">");
                    }
            }
            }
            
        }
        return copy;
    }
    
    
    
    
    
    
    
    
    
    
    /**
     * Returns a string representation that can be used for computing EDM with the script by Buys and Blunsom. 
     * BEWARE: nodes that don't have a span won't be added (e.g. a complex span that couldn't be restored), along with all their edges.
     * @param eds
     * @return 
     */
    public static String toEDM(AnchoredSGraph eds){
        StringBuilder b = new StringBuilder();
        HashMap<String,TokenRange> n2s = EDSUtils.nodeToSpan(eds);
        for (GraphNode n : eds.getGraph().vertexSet()){

            if (eds.getGraph().incomingEdgesOf(n).stream().anyMatch(edge -> edge.getLabel().equals("carg"))) {
                addInfo(b,n2s,n.getName(),"CARG",'"' + n.getLabel()+ '"'); //e.g. 0:8 CARG "Rockwell" ; 
            } else if (eds.getGraph().incomingEdgesOf(n).stream().allMatch(edge -> !edge.getLabel().equals(AnchoredSGraph.LNK_LABEL))) {
                String label = n.getLabel();
                if (! label.endsWith("_rel")){//For now, always add _rel for consistent evaluation. (Buys and Blunsom)
                    label += "_rel";
                }
                addInfo(b,n2s,n.getName(),"NAME",label); //e.g. 9:13 NAME _say_v_to_rel ;
            }
        }
        String root = eds.getNodeForSource("root");
        if (n2s.containsKey(root)){
            b.append("-1:-1 /H "); //format -1:1 /H [SPAN_OF_ROOT_NODE]
            TokenRange p = (TokenRange) n2s.get(root);
            b.append(p.getFrom());
            b.append(":");
            b.append(p.getTo());
            b.append(" ; ");
        }
        
        for (GraphEdge e : eds.getGraph().edgeSet()){
            if (!e.getLabel().equals(AnchoredSGraph.LNK_LABEL) && !e.getLabel().equals("carg"))
                addEdgeInfo(b,n2s,e.getSource().getName(),e.getTarget().getName(),e.getLabel());
        }
        
        return b.toString();
    }
    
    private static void addInfo(StringBuilder b, HashMap<String,TokenRange> n2s, String nodeName, String rel, String rel2){
    if (n2s.containsKey(nodeName)){
            TokenRange p = (TokenRange) n2s.get(nodeName);
            b.append(p.getFrom());
            b.append(":");
            b.append(p.getTo());
            b.append(" ");
            b.append(rel);
            b.append(" ");
            b.append(rel2);
            b.append(" ; ");
        }
    }
    
    private static void addEdgeInfo(StringBuilder b, HashMap<String,TokenRange> n2s, String fromNode, String toNode, String label){
    if (n2s.containsKey(fromNode) && n2s.containsKey(toNode)){
            TokenRange p = (TokenRange) n2s.get(fromNode);
            b.append(p.getFrom());
            b.append(":");
            b.append(p.getTo());
            b.append(" ");
            b.append(label.toUpperCase());
            b.append(" ");
            p = (TokenRange) n2s.get(toNode);
            b.append(p.getFrom());
            b.append(":");
            b.append(p.getTo());
            b.append(" ; ");
        }
    }


    
    
}
