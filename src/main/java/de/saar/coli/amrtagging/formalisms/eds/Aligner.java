/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.Alignment.Span;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.TokenRange;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.StringUtils;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * Extracts the alignments from an EDS graph and the corresponding untokenized string with the tokenization options of Buys and Blunsom.
 * @author matthias
 */
public class Aligner {
    
    
    public static final String[] ADDITIONAL_LEXICAL_NODES = {"pron","_dollar_n_1","_percent_n_of"}; //lexical nodes that are not easy to guess with lemmatization

    
    /**
     * Creates an MRInstance from an EDS graph.Returns a pair of SGraph, Complex spans.The complex spans 
     * @param eds
     * @param tokenizedString Pair of TokenRanges and the corresponding tokens, 
     * hyphenated compounds need to be split but their TokenRanges should be the same. Use EDSUtils.edsTokenizeString(sent,false)
     * @param lemmas list of same length as tokens providing the lemmas.
     * @return 
     */
    public static MRInstance extractAlignment(AnchoredSGraph eds, Pair< List<TokenRange> , List<String> > tokenizedString, List<String> lemmas){
        
        /*
        Steps:
        0.) Preprocessing of hyphens:  Identify hyphenated spans and replace them by (moved!)
        1.) Tokenize string
        2.) map EDS spans to EDS nodes
        3.) filter our complex EDS spans and assign their nodes N to a simple span, whose nodes are incident to N
        4.) map tokenized spans (called Stanford spans) to EDS spans, some tokenized spans will remain unaligned, if one Stanford span translates to several EDS spans, take the largest EDS span
        5.) compose mappings from Stanford spans to EDS spans with mapping from EDS spans to EDS nodes to create alignments.
        6.) identify lexical nodes.
        7.) Postprocessing, which changes the alignment of certain nodes (nodes with label "unknown" get attached to implicit_conj if that's present)
        */
        List<TokenRange> stanfSpans ;
        List<String> words ;
        stanfSpans = tokenizedString.left;
        words = tokenizedString.right;
        
        HashMap<TokenRange,Set<String>> spanToNodes = EDSUtils.spanToNodes(eds);
        HashMap<String,TokenRange> nodeToSpan = EDSUtils.nodeToSpan(eds);
        
        Set<TokenRange> complexSpans = eds.getComplexSpans();
        Set<TokenRange> minimalSpans = eds.getMinimalSpans();
        
        List<TokenRange> complexSpansSmallToLarge = new ArrayList<>(complexSpans);
        complexSpansSmallToLarge.sort((p1,p2) -> Integer.compare(p1.length(), p2.length())); 
        List<TokenRange> minimalSpansLeftToRight = new ArrayList<>(minimalSpans);
        minimalSpansLeftToRight.sort((p1,p2) -> Integer.compare(p1.getFrom(), p2.getFrom())); 
        
        //go over complex spans and heuristically align the nodes to one of its simple subspans
        //this won't reassign all complex spans but its a start
//        for (Pair<Integer,Integer> complexSpan : complexSpansSmallToLarge){
//            Set<String> myNodes = spanToNodes.get(complexSpan);
//            Pair<Integer,Integer> assignedSpan = null;
//            for (String nodeName : myNodes){
//                if (eds.getNode(nodeName).getLabel().equals("implicit_conj")){
//                    GraphNode dest = eds.getGraph().outgoingEdgesOf(eds.getNode(nodeName)).stream().filter(edge -> edge.getLabel().equals("R-INDEX")).findFirst().get().getTarget();
//                    assignedSpan = nodeToSpan.get(dest.getName());
//                    break;
//                }
//            }
//            if (assignedSpan  != null){
//                spanToNodes.get(assignedSpan).addAll(myNodes);
//                spanToNodes.remove(complexSpan);
//                complexSpans.remove(complexSpan);
//
//                for (String n : myNodes){
//                    nodeToSpan.put(n, assignedSpan);
//                }
//            }
//        }
        
        //Now let's look at what is left of the compplex spans that need to be reassigned
        //go from left to right over the string and try to expand what is aligned to the simple span as much as possible when you encounter nodes of a complex span 
        Set<String> nodesOfComplexSpans = new HashSet<>(); //contains all nodenames that belong to a complex span
        complexSpans.forEach(span -> nodesOfComplexSpans.addAll(spanToNodes.get(span)));
        for (TokenRange span : minimalSpansLeftToRight){
            Stack<String> agenda = new Stack<>(); //init agenda
            agenda.addAll(spanToNodes.get(span));
            HashSet<String> visited = new HashSet<>();
            while (!agenda.isEmpty()){
                String currentNode = agenda.pop();
                visited.add(currentNode);
                Set<String> tentativeNodes = new HashSet<>(spanToNodes.get(span));
                if (spanToNodes.get(nodeToSpan.get(currentNode)) != null){
                    tentativeNodes.addAll(spanToNodes.get(nodeToSpan.get(currentNode)));
                }
                if ( nodesOfComplexSpans.contains(currentNode) && TokenRange.isSubSpan(span,nodeToSpan.get(currentNode)) && EDSUtils.rootsRequired(eds,tentativeNodes) <= 1){
                    spanToNodes.get(span).add(currentNode); //add this node to the minimal span
                    nodesOfComplexSpans.remove(currentNode);
                    spanToNodes.remove(nodeToSpan.get(currentNode)); //remove this node from the complex span
                    nodeToSpan.put(currentNode, span);
                }
                if (spanToNodes.get(span).contains(currentNode)){ //if currentNode (now) belongs to "our" blob
                    for (GraphEdge edg : eds.getGraph().edgesOf(eds.getNode(currentNode))){ //add all adjacent nodes that are not visited yet
                        String src = edg.getSource().getName();
                        String tgt = edg.getTarget().getName();
                        if (! visited.contains(src)){
                            agenda.add(src);
                        }
                        if (! visited.contains(tgt)){
                            agenda.add(tgt);
                        }
                    }
                }

            }
            
        }

        //now translate the stanford spans to spans in the graph. Some stanford spans may be unaligned.
        HashMap<TokenRange,TokenRange> stanfSpanToGraphSpan = new HashMap<>();
        for (TokenRange graphSpan: minimalSpans  ){
            TokenRange associatedSpan = null;
            for (TokenRange stanfSpan : stanfSpans){
                if (TokenRange.isSubSpan(stanfSpan,graphSpan) && (associatedSpan == null || stanfSpan.length() > associatedSpan.length()) ){ 
                    associatedSpan = stanfSpan;
                }
            }
           if (associatedSpan != null){
                stanfSpanToGraphSpan.put(associatedSpan, graphSpan);
            } else {
               //throw new IllegalArgumentException("Could not find corresponding coreNLP span for EDS span "+graphSpan);
           }
        }
//        System.err.println(stanfSpanToGraphSpan);
//        System.err.println(spanToNodes);
        
        //now we have a mapping from Stanford Spans to EDS spans and a mapping from EDS spans to nodes so we can compose those mappings.
       
       List<Alignment> alignments = new ArrayList<>();
       //Sentence stanfSent = new Sentence(words);
       for (TokenRange span : stanfSpanToGraphSpan.keySet()){
           if (spanToNodes.containsKey(stanfSpanToGraphSpan.get(span))){
               TokenRange correspondingGraphSpan = stanfSpanToGraphSpan.get(span);
               int wordPosition = stanfSpans.indexOf(span);
               String lemma = lemmas.get(wordPosition); //stanfSent.lemma(wordPosition);
               Set<String> lexicalNodes = EDSUtils.findLexialNodes(eds, spanToNodes.get(correspondingGraphSpan), words.get(wordPosition),lemma);

               alignments.add(new Alignment(spanToNodes.get(correspondingGraphSpan), new Span(wordPosition,wordPosition+1), lexicalNodes, 0));
           }
       }
       MRInstance ret = new MRInstance(words, eds, alignments); 
       postprocess(ret);
       return ret;
        
    }
    
    /**
     * This function modifies an EDS graph in such a way that hyphenated compounds (asbestos-related) are treated as two distinct words.To achieve that, we identify hyphenated words and their corresponding nodes in the graph and change the lnk nodes to point to the two distinct words.
     * @param eds
     * @param tokSameForHyphens  tokenization where asbestos-related will count as two words which have the same character span
     * @param tokDifferentForHyphens tokenization where asbestos-related will count as two words which have DIFFERENT characters spans
     */
    public static void preprocessHyphens(AnchoredSGraph eds, Pair< List<TokenRange> , List<String> > tokSameForHyphens, Pair< List<TokenRange> , List<String> > tokDifferentForHyphens){
        
        assert(tokSameForHyphens.left.size() == tokDifferentForHyphens.left.size());
        
        HashMap<TokenRange,Set<String>> spanToNodes = EDSUtils.spanToNodes(eds);
        
        
        for (int i = 0; i < tokSameForHyphens.left.size();i++){ //go over all spans
            String word = tokDifferentForHyphens.right.get(i);
            if (!tokSameForHyphens.left.get(i).equals(tokDifferentForHyphens.left.get(i))){
                // we are at a hyphenated word. The variable word contains a part of it.
                if (spanToNodes.containsKey(tokSameForHyphens.left.get(i))) { //and we find a corresponding span in the graph
                    String closestNode = null;
                    int minDistance = Integer.MAX_VALUE;
                    for (String nodeName : spanToNodes.get(tokSameForHyphens.left.get(i))){
                        int dist = StringUtils.levenshteinDistance(word, eds.getNode(nodeName).getLabel());
                        if (dist < minDistance){
                            minDistance = dist;
                            closestNode = nodeName;
                        }
                    }
                    //now let's take that node that probably belongs to word and change its lnk daughter to point to the adjusted span (treating the hyphenated word as several words)
                    if (closestNode != null){
                        Optional<GraphNode> lnkNode = eds.lnkDaughter(closestNode);
                        if (lnkNode.isPresent()){
                            TokenRange newSpan = tokDifferentForHyphens.left.get(i);
                            lnkNode.get().setLabel("<"+newSpan.getFrom()+":"+newSpan.getTo()+">");
                        } else {
                            //we might be at a carg node, which doesn't have a lnk node but its parent probably has, so let's try this:
                            Optional<GraphEdge> parent = eds.getGraph().incomingEdgesOf(eds.getNode(closestNode)).stream().findFirst();
                            if (parent.isPresent()){
                                lnkNode = eds.lnkDaughter(parent.get().getSource().getName());
                                if (lnkNode.isPresent()){
                                    TokenRange newSpan = tokDifferentForHyphens.left.get(i);
                                    lnkNode.get().setLabel("<"+newSpan.getFrom()+":"+newSpan.getTo()+">");
                                }
                            }
                           
                        }
                    }
                    
                }
                
            }
        }
        
        
    }
    
    
    
    /**
     * This function reassigns "unknown" nodes to their "implicit_conj" nodes.
     * @param inst
     */
    public static void postprocess(MRInstance inst){
        //System.err.println("Davor "+inst.getAlignments());
            for (GraphNode n : inst.getGraph().getGraph().vertexSet()){
                if (n.getLabel().equals("implicit_conj")) {
                    Optional<GraphEdge> unk = inst.getGraph().getGraph().outgoingEdgesOf(n).stream().filter(edge -> edge.getTarget().getLabel().equals("unknown")).findFirst();
                    if (unk.isPresent()){
                        ArrayList<String> nodesToBeReassigned = new ArrayList<String>();
                        nodesToBeReassigned.add(unk.get().getTarget().getName());
                        //also add its lnk node the collection of nodes that we want to reassign.
                        for (GraphEdge lnk : inst.getGraph().getGraph().outgoingEdgesOf(unk.get().getTarget())){
                            if (AnchoredSGraph.isLnkEdge(lnk)){
                                nodesToBeReassigned.add(lnk.getTarget().getName());
                            }
                        }
                        //remove the nodesToBeReassigned from their current assignment:
                        for (String nodeName : nodesToBeReassigned){
                            for (Alignment al : inst.getAlignments()){
                                if (al.nodes.contains(nodeName)){
                                    al.nodes.remove(nodeName);
                                }
                            }
                        }
                        //add the nodesToBeReassigned to their new blob:
                        for (Alignment al : inst.getAlignments()){
                            if (al.nodes.contains(n.getName())) { //this is the alignment where the implicit_conj node belongs to
                                al.nodes.addAll(nodesToBeReassigned);
                            }
                        }
                        
                    }
                }
            }
            //System.err.println("Danach "+inst.getAlignments());
    }
    
    
}
