/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.eds;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.TokenRange;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import static de.saar.coli.amrtagging.mrp.utils.MRPUtils.ROOT_EDGE_LABEL;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import edu.stanford.nlp.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fixes some issues the EDS Aligner has.
 * @author matthias
 */
public class AlignerFixes {
    
    public static final Set<String> PUNCTUATION = new HashSet<String>(){{
        add(",");
        add(";");
        add("-");
        add("--");
        add("–");
    }};
    
    /**
     * Fixes alignment problem when a node with a single incident edge is unaligned, by adding it to its neighbour.
     * @param inst 
     * @return  if something was fixed
     */
    public static boolean fixSingleEdgeUnaligned(MRInstance inst){
        //identify fixable nodes
        boolean fixed = false;
        Map<String,Integer> counter = inst.getAlignmentCounter();
        for (String node : counter.keySet()){
            GraphNode theNode = inst.getGraph().getNode(node);
            Set<GraphEdge> itsEdges;
            if (inst.getGraph().getGraph().edgesOf(theNode).size() > 1) {
               itsEdges = inst.getGraph().getGraph().edgesOf(theNode).stream().
                    filter(e -> ! AnchoredSGraph.isLnkEdge(e)).collect(Collectors.toSet());  
            } else {
                itsEdges = inst.getGraph().getGraph().edgesOf(theNode);
            }
            itsEdges = itsEdges.stream().
                    filter(e -> ! e.getLabel().startsWith(ROOT_EDGE_LABEL)).collect(Collectors.toSet()); 
            
            
            if (counter.get(node) == 0 && itsEdges.size() == 1){
                GraphEdge edge = itsEdges.stream().findAny().get();
                GraphNode otherNode = GeneralBlobUtils.otherNode(theNode, edge);
                for (Alignment al : inst.getAlignments()){
                    if (al.nodes.contains(otherNode.getName())){
                        al.nodes.add(node);
                        fixed=true;
                        break;
                    }
                }
            }
        }
        return fixed;
    }
    
    
    
    /**
     * Fixes alignment issue / multiple roots issue for some comparatives. See EDS graph 20044076 for an example.
     * It puts the subord node into the same alignment as its comp node.
     * @param inst
     * @return 
     */
    public static boolean fixComparatives(MRInstance inst){
        //identify fixable nodes
        boolean fixed = false;
        Map<String,Integer> counter = inst.getAlignmentCounter();
        for (String node : counter.keySet()){
            GraphNode theNode = inst.getGraph().getNode(node);
            if (counter.get(node) == 0 && theNode.getLabel().equals("subord")){
                Set<GraphEdge> itsArg2 = inst.getGraph().getGraph().outgoingEdgesOf(theNode).
                        stream().filter(edge -> edge.getLabel().equals("ARG2")).collect(Collectors.toSet());
                if (itsArg2.size() == 1){
                    GraphEdge arg2 = itsArg2.iterator().next();
                    GraphNode adjective = GeneralBlobUtils.otherNode(theNode,arg2);
                    Set<GraphEdge> compNodes = inst.getGraph().getGraph().incomingEdgesOf(adjective)
                            .stream().filter(edge -> edge.getSource().getLabel().contains("comp")).collect(Collectors.toSet());
                    if (compNodes.size() == 1){
                        GraphNode compNode = compNodes.iterator().next().getSource();
                        //find alignment of compNode and add theNode to it
                         for (Alignment al : inst.getAlignments()){
                            if (al.nodes.contains(compNode.getName())){
                                al.nodes.add(node);
                                fixed=true;
                                break;
                            }
                        }
                    }
                            
                }
            }
        }
        return fixed;
    }
    
    /**
     * If a node is unaligned and there is also a word that is not aligned to anything in the graph
     * these might actually belong together. 
     * @param inst
     * @param minWordLength minimum word length for words that might be taken into account
     * @param maxRatio maximum ratio between distance / word length
     * @return 
     */
    public static boolean inventAlignment(MRInstance inst, int minWordLength, double maxRatio){
        boolean fixed=false;
        //identify alignment counts for words:
        Set<Integer> alignedWords = inst.getAlignments().stream().map(al -> al.span.start).collect(Collectors.toSet());
        Set<Integer> candidates = new HashSet<>();
        for (int position = 0; position < inst.getSentence().size(); position++){
            if (inst.getSentence().get(position).length() >= minWordLength && ! alignedWords.contains(position)){
                 candidates.add(position);
            }
        }
        Map<String,Integer> counter = inst.getAlignmentCounter();
        for (String node : counter.keySet()){
            if (counter.get(node) == 0){
                String label = inst.getGraph().getNode(node).getLabel().replaceAll("_._[0-9a-z]+", "").replace("_", "");
                for (int candidate : candidates){
                    String word = inst.getSentence().get(candidate);
                    double distance = StringUtils.levenshteinDistance(label, word);
                    if ( distance / word.length() <= maxRatio ){
                        System.err.println("[inventAlignment] : "+label+" --> "+word+"\tbecause ratio is "+distance / word.length());
                        inst.getAlignments().add(new Alignment(node, candidate));
                        candidates.remove(candidate);
                        fixed=true;
                        break;
                    }
                }
            }
        }
        
        return fixed;
    }
    
    
    
     /**
     * Aligns un-aligned implicit_conj and subord nodes to commas close to their range.
     * @param asg
     * @param sentence
     * @return 
     */
    public static List<Alignment> alignImplicitConj(AnchoredSGraph asg, ConlluSentence sentence){
        Set<Integer> candidates = new HashSet<>();
        for (int position = 0; position < sentence.size(); position++){
                candidates.add(position);
        }
        List<Alignment> alreadyAligned = new ArrayList<>();
        for (String node : asg.getAllNodeNames()){
            GraphNode theNode = asg.getNode(node);
                if (theNode.getLabel().equals("subord")  || theNode.getLabel().equals("implicit_conj")){
                    Optional<GraphNode> lnkDaughter = asg.lnkDaughter(node);
                    if (lnkDaughter.isPresent() ){
                        TokenRange range = AnchoredSGraph.getRangeOfLnkNode(lnkDaughter.get()); //token range of subord or implicit_conj node
                        int closest = closestPunctuation(range, sentence, candidates);
                        if (closest > 0) { //actually found some position
                            Set<String> nodes = new HashSet<>();
                            nodes.add(node);
                            nodes.add(lnkDaughter.get().getName());
                            candidates.remove(closest);
                            alreadyAligned.add(new Alignment(nodes, new Alignment.Span(closest, closest+1)));
                            break;
                        }
                    }
            }
        }
        return alreadyAligned;
    }
    
    private static int closestPunctuation(TokenRange r, ConlluSentence sent, Set<Integer> candidates){
        int position = -1;
        int distance = Integer.MAX_VALUE;
        int index = 0;
        for (ConlluEntry e : sent){
            TokenRange entryRange = e.getTokenRange();
            int dist_l = Math.abs(r.getFrom() - entryRange.getFrom());
            int dist_r = Math.abs(r.getTo() - entryRange.getTo());
            int dist = Math.min(dist_l, dist_r);
            if (candidates.contains(index) && (PUNCTUATION.contains(e.getForm()) || PUNCTUATION.contains(e.getLemma())) && dist < distance){
                position = index;
                distance = dist;
            }
            index++;
        }
        return position;
    }
    
    
    
    
}
