/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllEntry;

import static de.saar.coli.amrtagging.formalisms.eds.Aligner.ADDITIONAL_LEXICAL_NODES;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Some utility functions for working with EDS.
 * @author matthias
 */
public class EDSUtils {
    
    public static final String PNCT = "[\";.!?()”“']+";
    public static final String END_PNCT = ".*"+PNCT;
    public static final String START_PNCT = PNCT+".*";
    
    
     /**
     * Counts how many nodes have incoming edges.
     * @param eds
     * @param nodes
     * @return 
     */
    public static int rootsRequired(SGraph eds, Set<String> nodes){
        EDSBlobUtils b = new EDSBlobUtils();
        HashSet<String> inNodes = new HashSet<String>();
        if (nodes.contains(eds.getNodeForSource("root"))){
            inNodes.add(eds.getNodeForSource("root"));
        }
        for (String node : nodes){
            for (GraphEdge e : eds.getGraph().edgesOf(eds.getNode(node))){
                if (b.isOutbound(e) && !nodes.contains(e.getSource().getName()) && nodes.contains(e.getTarget().getName())){
                    inNodes.add(e.getTarget().getName());
                }else if ( ! b.isOutbound(e) && !nodes.contains(e.getTarget().getName()) && nodes.contains(e.getSource().getName())){
                    inNodes.add(e.getSource().getName());
                }
            }
        }
        return inNodes.size();
    }
    
        
    /**
     * Runs corenlp to tokenize the given string with the options used by Buys and Blunsom to ressemble ERG tokenization.Returns a list of spans and a list of words as they occur in the string.
     * @param sent
     * @param testTime indicates if we are producing training data or evaluating. Difference: During training, give hyphenated compounds (asbestos-related) different spans, during testing give them the same span.
     * @return 
     */
    public static Pair<  List<TokenRange> , List<String> > edsTokenizeString(String sent, boolean testTime){
       PTBTokenizer<CoreLabel> tokenizer = new PTBTokenizer(new StringReader(sent), new CoreLabelTokenFactory(), 
                "normalizeCurrency=False,normalizeFractions=False, normalizeParentheses=False,normalizeOtherBrackets=False," +
                "latexQuotes=False,unicodeQuotes=True," +
                "ptb3Ellipsis=False,unicodeEllipsis=True," +
                "escapeForwardSlashAsterisk=False"); //options used by Buys and Blunsom in DeepDeepParser
        List<TokenRange> stanfSpans = new ArrayList<>();
        List<String> words = new ArrayList<>();
        while (tokenizer.hasNext()){
            CoreLabel word = tokenizer.next();
            String wordText = word.originalText();
            if (wordText.contains(" ")) { //this is a SPECIAL kind of white space, which occurs in mixed fractions like 2 2/3
                String[] parts = wordText.split(" ");
                int position = word.beginPosition();
                for (String part : parts){
                    words.add(part);
                    stanfSpans.add(new TokenRange(position,position+part.length()));
                    position += part.length()+1;
                }
            } else if (wordText.contains("-") && ! wordText.matches("-+")) { //special treatment of hyphenated things like asbestos-related
                //the constituents of the hyphenated word get the same span but get split into two words
                String[] parts = wordText.split("-");
                int position = word.beginPosition();
                for (String part : parts){
                    words.add(part);
                    if (testTime){
                        stanfSpans.add(new TokenRange(word.beginPosition(),word.endPosition()));
                    } else {
                        stanfSpans.add(new TokenRange(position,position+part.length()));
                    }
                    position += part.length()+1;
                }
            
            } else {
                words.add(wordText);
                stanfSpans.add(new TokenRange(word.beginPosition(),word.endPosition()));
            }
            
        }
        //EDS tokenization attaches the very first and the very last punctuation in the sentence to its respective word, so we will extend the span
        //this is mainly important for evaluation time.
        int lastIndex = 0;
        for (int i = 0; i < words.size(); i++){
            String word =  Util.fixPunct(words.get(i));
            if (! word.matches(END_PNCT)){
                lastIndex = i;
            }
        }
        if (stanfSpans.get(lastIndex).getTo() < sent.length()){
            stanfSpans.set(lastIndex, new TokenRange(stanfSpans.get(lastIndex).getFrom(),sent.length()));
        }
        int firstIndex = -1;
        for (int i = words.size()-1; i >= 0; i--){
            String word =  Util.fixPunct(words.get(i));
            if (! word.matches(START_PNCT)){
                firstIndex = i;
            }
        }
        
        if (stanfSpans.get(firstIndex).getFrom() > 0){
            stanfSpans.set(firstIndex, new TokenRange(0,stanfSpans.get(firstIndex).getTo()));
        }

        
        //System.err.println(words);
        //System.err.println(stanfSpans);
        return new Pair<>(stanfSpans,words);
    }
    
    public static HashMap<TokenRange,Set<String>> spanToNodes (AnchoredSGraph eds){
       HashMap<TokenRange,Set<String>> spanToNodes = new HashMap<>(); //maps EDS spans to sets of nodes
        for (GraphNode n :eds.getGraph().vertexSet()){
            if (AnchoredSGraph.isLnkNode(n)){
                TokenRange span = AnchoredSGraph.getRangeOfLnkNode(n);
                Set<String> val = spanToNodes.getOrDefault(span, new HashSet<>());
                if (eds.getGraph().inDegreeOf(n) < 1){
                    System.err.println("Warning, there is a lnk node that does not have incoming edges! "+eds.toIsiAmrString());
                    continue;
                }
                GraphNode parent = ((GraphEdge) eds.getGraph().incomingEdgesOf(n).toArray()[0]).getSource();
                val.add(parent.getName());
                val.add(n.getName()); //also add lnk itself
                for (GraphEdge edge : eds.getGraph().outgoingEdgesOf(parent)){
                    if (edge.getLabel().equals("carg")){ //constants like "Pierre" are connected via a carg-edge
                        val.add(edge.getTarget().getName());
                    }
                }
                spanToNodes.put(span,val);
            }
        }
        return spanToNodes;

    }
    
    public static HashMap<String,TokenRange> nodeToSpan(AnchoredSGraph eds){
        
        HashMap<String,TokenRange> nodeToSpan = new HashMap<>(); //reverse mapping: maps nodes to their spans
        HashMap<TokenRange,Set<String>> s2n = spanToNodes(eds);
        for (Map.Entry e : s2n.entrySet()){
            for (String node : (Set<String>)e.getValue()){
                nodeToSpan.put(node, (TokenRange)e.getKey());
            }
        }
        return nodeToSpan;
    }
    
    
    
 
    
    /**
     * Returns the set of lexical nodes in the subgraph with node names in nodeNames of Graph eds. The aligned word has String "word" and lemma "lemma".
     * @param eds
     * @param nodeNames
     * @param word
     * @param lemma
     * @return 
     */
    public static Set<String> findLexialNodes(AnchoredSGraph eds,Set<String> nodeNames, String word, String lemma){
        Set<String> lexicalNodes = new HashSet<String>();
        word = word.replaceAll("[.,;]", ""); //delete punctuation
        lemma = lemma.replaceAll("[.,;]", ""); //delete punctuation
        for (String nodeName : nodeNames){ //highest priority lexical match
            if (eds.getGraph().incomingEdgesOf(eds.getNode(nodeName)).stream().anyMatch(edg -> edg.getLabel().equals(AnchoredSGraph.LNK_LABEL)) ) continue; //lnk nodes cannot be lexical nodes.
            String label = eds.getNode(nodeName).getLabel().replaceAll("[.,;]", ""); //delete punctuation
            if (label.contains(lemma) || label.contains(word) || word.contains(label) || lemma.contains(label) ){ 
                     lexicalNodes.add(nodeName);
                     break;
            }
        }
        if (lexicalNodes.isEmpty()){
            for (String nodeName : nodeNames){ //just take the carg node
                if (eds.getGraph().incomingEdgesOf(eds.getNode(nodeName)).stream().anyMatch(edg -> edg.getLabel().equals("carg")) ){
                    lexicalNodes.add(nodeName);
                    break;
                }
            }
        }
        if (lexicalNodes.isEmpty()){
            for (String nodeName : nodeNames){ //second highest priority: at least two occurences of _, e.g. _year_n_1 or _the_q
                if (eds.getGraph().incomingEdgesOf(eds.getNode(nodeName)).stream().anyMatch(edg -> edg.getLabel().equals(AnchoredSGraph.LNK_LABEL)) ) continue; //lnk nodes cannot be lexical nodes.
                 String label = eds.getNode(nodeName).getLabel();
                 if (label.length() - label.replace("_", "").length() >= 2){ 
                     lexicalNodes.add(nodeName);
                     break;
                 }
            }
        }
       if (lexicalNodes.isEmpty()){
           done:
            for (String nodeName : nodeNames){ //thid highest priority: additional tokens in the list
                if (eds.getGraph().incomingEdgesOf(eds.getNode(nodeName)).stream().anyMatch(edg -> edg.getLabel().equals(AnchoredSGraph.LNK_LABEL)) ) continue; //lnk nodes cannot be lexical nodes.
                 String label = eds.getNode(nodeName).getLabel();
                 for (String nodeLabel : ADDITIONAL_LEXICAL_NODES) {
                     if (label.equals(nodeLabel)){ 
                        lexicalNodes.add(nodeName);
                        break done;
                    }
                 }

            }
        }

       
        return lexicalNodes;
    }
    
    
    
    

    
    public static boolean checkSentence(AmConllSentence sent){
        boolean ok = true;
        for (AmConllEntry e : sent){
            if (e.getLexLabel().equals(EDSConverter.COMPLEX_SPAN) || e.getLexLabel().equals(EDSConverter.SIMPLE_SPAN)){
                ok = false;
                System.err.println("WARNING: A lexical node is marked with a span placeholder ("+EDSConverter.SIMPLE_SPAN+" or "+EDSConverter.COMPLEX_SPAN+"):");
                System.err.println(sent);
                break;
            }
        }
        return ok;
    }
    
    
}
