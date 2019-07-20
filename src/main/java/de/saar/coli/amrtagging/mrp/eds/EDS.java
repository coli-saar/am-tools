/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.eds;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.TokenRange;
import de.saar.coli.amrtagging.Util;
import static de.saar.coli.amrtagging.Util.fixPunct;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.eds.Aligner;
import de.saar.coli.amrtagging.formalisms.eds.EDSBlobUtils;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import static de.saar.coli.amrtagging.formalisms.eds.EDSConverter.encodeLnks;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;
import static de.saar.coli.amrtagging.formalisms.eds.EDSUtils.END_PNCT;
import static de.saar.coli.amrtagging.formalisms.eds.EDSUtils.START_PNCT;
import de.saar.coli.amrtagging.formalisms.eds.PostprocessLemmatize;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPAnchor;
import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import static de.saar.coli.amrtagging.mrp.utils.MRPUtils.ART_ROOT;
import static de.saar.coli.amrtagging.mrp.utils.MRPUtils.ROOT_EDGE_LABEL;
import static de.saar.coli.amrtagging.mrp.utils.MRPUtils.addArtificialRootToSent;
import de.saar.coli.amrtagging.mrp.utils.StupidPOSTagger;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.tree.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.alg.ConnectivityInspector;

/**
 *
 * @author matthias
 */
public class EDS implements Formalism{
    
    private StupidPOSTagger posTagger;
    private StupidPOSTagger uposTagger;
    
    public EDS(List<ConlluSentence> trainingDataForPosTagger){
        posTagger = new StupidPOSTagger(trainingDataForPosTagger, (ConlluEntry e) -> e.getPos());
        uposTagger = new StupidPOSTagger(trainingDataForPosTagger, (ConlluEntry e) -> e.getUPos());
    }

    @Override
    public ConlluSentence refine(ConlluSentence sentence) {
        // Tokenization
        sentence = sentence.copy();
        ConlluSentence copy = sentence.withSameMetaData();
        //int idx = 1;
        int addedSoFar = 0;
        Map<Integer,Integer> indexTranslation = new HashMap<>();
        indexTranslation.put(0,0); //root stays root.
        for (ConlluEntry e : sentence){
            e.setLemma(fixPunct(e.getLemma().toLowerCase()));
            
            if (e.getForm().contains("-") && ! e.getForm().matches("-+") && ! e.getForm().equals(MRPUtils.ART_ROOT)) { //special treatment of hyphenated things like asbestos-related
                //the constituents of the hyphenated word get the same span but get split into two words
                String[] parts = e.getForm().split("-");
                String[] lemmaParts = e.getLemma().split("-");
                
                if (parts.length != lemmaParts.length){
                    throw new IllegalArgumentException("Different number of form parts and lemma parts for: form="+e.getForm()+" lemma="+e.getLemma());
                }
                
                TokenRange r = e.getTokenRange();
                for (int tokenPart = 0; tokenPart < parts.length;tokenPart++){
                    ConlluEntry newE = new ConlluEntry(-1, parts[tokenPart]);
                    newE.setLemma(lemmaParts[tokenPart]);
                    newE.setHead(e.getHead());
                    if (tokenPart == parts.length -1) { //last part, probably the head in English
                        newE.setPos(e.getPos());
                        newE.setUPos(e.getUPos());
                    } else {
                        newE.setPos(posTagger.tag(parts[tokenPart]));
                        newE.setUPos(uposTagger.tag(parts[tokenPart]));
                    }
                    
                    newE.setTokenRange(r);
                    newE.setEdgeLabel(e.getEdgeLabel());
                    copy.add(newE);
                    //idx++;
                }
                indexTranslation.put(e.getId(), e.getId()+addedSoFar);
                addedSoFar += parts.length -1 ; //-1 because we would have added one token anyway
            
            } else {
                indexTranslation.put(e.getId(), e.getId()+addedSoFar);
                copy.add(e);
                //idx++;
            }
        }
        int idx = 1;
        for (ConlluEntry e : copy){
            e.setId(idx);
            e.setHead(indexTranslation.get(e.getHead()));
            idx++;
        }
        
        //EDS tokenization attaches the very first and the very last punctuation in the sentence to its respective word, so we will extend the span
        //this is mainly important for evaluation time.
        int lastIndex = 0;
        for (int i = 0; i < copy.size(); i++){
            String word =  fixPunct(copy.get(i).getForm());
            if (! word.matches(END_PNCT)){
                lastIndex = i;
            }
        }
        if (copy.ranges().get(lastIndex).getTo() < copy.ranges().get(copy.size()-1).getTo()){
            TokenRange toEnd = new TokenRange(copy.ranges().get(lastIndex).getFrom(), copy.ranges().get(copy.size()-1).getTo());
            copy.get(lastIndex).setTokenRange(toEnd);
        }
        int firstIndex = -1;
        for (int i = copy.size()-1; i >= 0; i--){
            String word =  fixPunct(copy.get(i).getForm());
            if (! word.matches(START_PNCT)){
                firstIndex = i;
            }
        }
        
        if (copy.get(firstIndex).getTokenRange().getFrom() > 0){
            copy.get(firstIndex).setTokenRange(new TokenRange(0,copy.get(firstIndex).getTokenRange().getTo()));
        }
        
        return copy;
    }

    @Override
    public MRPGraph preprocess(MRPGraph mrpgraph) {
        MRPGraph copy = mrpgraph.deepCopy();
        //remove HNDL edges
//        for (MRPEdge e : mrpgraph.getEdges()){
//            if (e.label.endsWith("-HNDL")){
//                copy.getEdges().remove(e);
//            }
//        }
        //put attributes into node labels
        MRPUtils.encodePropertiesInLabels(copy);
        
        for (MRPNode n : copy.getNodes()){
            n.setLabel(Util.fixPunct(n.getLabel()));
        }
        
        return copy;
    }
    

    @Override
    public MRPGraph postprocess(MRPGraph mrpgraph) {
        MRPGraph copy = mrpgraph.deepCopy();
        for (MRPNode n : copy.getNodes()){
            n.setLabel(Util.unfixPunct(n.getLabel()));
        }
        MRPUtils.decodePropertiesInLabels(copy);
        return MRPUtils.removeArtificalRoot(copy);
    }
    
    /**
     * Given a refined ConlluSentence (i.e. hyphenated compounds are split but have the same ranges)
     * returns the list of TokenRanges where hyphenated compounds are split and have "correct" ranges,
     * that is, ranges that reflect the splitting.
     * @param sentence
     * @return 
     */
    private List<TokenRange> differentRangesForHyphens(ConlluSentence sentence){
        List<TokenRange> differentForHyphens = new ArrayList<>(sentence.ranges());
        
        for (int i = 1; i < sentence.size(); i++){
            TokenRange r1 = sentence.get(i-1).getTokenRange();
            TokenRange r2 = sentence.get(i).getTokenRange();
            if (r1.equals(r2)){ //two consecutive tokens with same span means split compound
                int lengthFirstPart = sentence.get(i-1).getForm().length();
                differentForHyphens.set(i-1, new TokenRange(r1.getFrom(), r1.getFrom() + lengthFirstPart));
                differentForHyphens.set(i, new TokenRange(r1.getFrom() + lengthFirstPart + 1, r2.getTo()));
            }
            
        }
        
        return differentForHyphens;
    }

    
    
    @Override
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph) {
        //add ART-ROOT
        //MRPUtils.addArtificalRoot(sentence, mrpgraph);
        AnchoredSGraph asg = MRPUtils.toSGraphWithAnchoring(mrpgraph);
        //EDSConverter.makeNodeNamesExplicit(asg);
        List<String> words = sentence.words();
        
        // sentence contains the "refined" tokenization where hyphenated compounds are split
        // in that version, the split compounds still cover the whole range of the original token (= that's what the evaluation wants)
        // here, we also need a version where the ranges are adapted (i.e. reflect the splitting)
        // (usually, the split is also made in the graph, so we want to modify the alignment to reflect this)
        
        Pair< List<TokenRange> , List<String>> tokSameForHyphens = new Pair<>(sentence.ranges(), sentence.words());
        Pair< List<TokenRange> , List<String>> tokDifferentForHyphens = new Pair<>(differentRangesForHyphens(sentence), sentence.words());
       
        Aligner.preprocessHyphens(asg, tokSameForHyphens, tokDifferentForHyphens);
        
        //first align implicit conjunctions
        List<Alignment> alreadyAligned = AlignerFixes.alignImplicitConj(asg,sentence);
        
        MRInstance inst = Aligner.extractAlignment(asg, tokDifferentForHyphens, sentence.lemmas(), alreadyAligned);
        boolean fixedSomething = true;
        while(fixedSomething){ //fix multiple times, fixing something might enable us to fix more.
            fixedSomething = AlignerFixes.fixSingleEdgeUnaligned(inst);
            fixedSomething = fixedSomething | AlignerFixes.fixComparatives(inst);
            fixedSomething = fixedSomething | AlignerFixes.inventAlignment(inst, 5, 0.3);
        }
        try {
            addArtificalRoot(sentence, inst);
            mrpgraph.setInput(addArtificialRootToSent(mrpgraph.getInput()));
        } catch (ParserException | ParseException ex){
            return null;
        }
        
        inst.setGraph(encodeLnks((AnchoredSGraph) inst.getGraph()));
        return inst;
    }

    @Override
    public MRPGraph evaluate(ConllSentence amconll) {
        try {
            AMDependencyTree amdep = AMDependencyTree.fromSentence(amconll);
            SGraph evaluatedWithAlignmentsGraph = amdep.evaluate(true);
            List<Alignment> als = AMDependencyTree.extractAlignments(evaluatedWithAlignmentsGraph);
            List<String> words = amconll.words();
            List<TokenRange> stanfSpans = amconll.ranges();
            SGraph evaluated = evaluatedWithAlignmentsGraph;
            AMDependencyTree.stripAlignments(evaluated); //now alignment info is not present in nodes anymore

            //now we replace the placeholder SIMPLE_SPAN with its character span
            EDSConverter.restoreSimpleSpans(evaluated, words,stanfSpans, als);

            AnchoredSGraph evaluatedGraph = EDSConverter.restoreComplexSpans(evaluated);
            
            //SGraphDrawer.draw(evaluatedGraph, "mhm");
            //System.err.println(amconll.getId()+"\t"+evaluatedGraph.toString());
            
            String input = amconll.getAttr("raw");
            if (input == null){
                input = amconll.getAttr("input");
            }
            MRPGraph mrpGraph = MRPUtils.fromAnchoredSGraph(evaluatedGraph, false, 1, "eds",
                    amconll.getId(), input, amconll.getAttr("version"), amconll.getAttr("time"));
            return removeArtificalRoot(mrpGraph);
            
        } catch (ParseException | ParserException | AMDependencyTree.ConllParserException e){
             throw new IllegalArgumentException(e);
         }
    }

    
    @Override
    public AMSignatureBuilder getSignatureBuilder(MRInstance inst) {
         return new EDSAlignmentSignatureBuilder(inst.getGraph(), inst.getAlignments(), new EDSBlobUtils());
    }
    
    @Override
    public AlignmentTrackingAutomaton getAlignmentTrackingAutomaton(MRInstance instance) throws ParseException{
        AMSignatureBuilder sigBuilder = getSignatureBuilder(instance);
        return ConcreteAlignmentTrackingAutomaton.create(instance, sigBuilder, false);
    }

    @Override
    public void refineDelex(ConllSentence sentence) {
        PostprocessLemmatize.edsLemmaPostProcessing(sentence);
    }
    
    

    /**
     * Adds an additional token (ART-ROOT) that unifies all top nodes and makes the graph connected.This version differs in several aspects from the generic version in the MRPUtils:
   - it takes an MRInstance 
   - art-snt1 always points to the actual (unique) top node
   - it uses the signature builder to identify the root of a disconnected component
     * @param sent
     * @param inst
     * @throws de.up.ling.tree.ParseException
     * @throws de.up.ling.irtg.algebra.ParserException
     */
    public void addArtificalRoot(ConlluSentence sent, MRInstance inst) throws ParseException, ParserException{
        // How does it work?
        // we add an additional node to the graph
        // we go over all connected components and draw an edge to the top node
        // if there is no top node, we read of the head of the span the component comprises from the companion data
        // and draw the edge into the corresponding node
        
        MRPUtils.addArtificialRootToSent(sent);
        
        AMSignatureBuilder sigBuilder = getSignatureBuilder(inst);
        ApplyModifyGraphAlgebra am =  new ApplyModifyGraphAlgebra();
        AnchoredSGraph asg = (AnchoredSGraph) inst.getGraph();
        
        String artRootName = "artroot";
        GraphNode artRootNode = asg.addNode(artRootName, MRPUtils.ART_ROOT);
        GraphNode lnkArtRoot = asg.addNode(artRootName+"lnk",EDSConverter.SIMPLE_SPAN); //we add SIMPLE already here! (rest of graph contains correct references)
        
        asg.addEdge(artRootNode, lnkArtRoot, AnchoredSGraph.LNK_LABEL);
        inst.getSentence().add(MRPUtils.ART_ROOT);
        Set<String> nodes = new HashSet<>();
        nodes.add(artRootName);
        Set<String> lexNodes = new HashSet<>(nodes);
        nodes.add(lnkArtRoot.getName());
        
        inst.getAlignments().add(new Alignment(nodes, new Alignment.Span(inst.getSentence().size()-1, inst.getSentence().size()), lexNodes, 0)); //aligned to last word
        
        
        ConnectivityInspector<GraphNode, GraphEdge> inspector =  new ConnectivityInspector(asg.getGraph());
        
        int sntCounter = 1;
        String oldRoot = asg.getNodeForSource("root");
        GraphNode oldRootNode = asg.getNode(oldRoot);
        Set<String> sources = asg.getAllSources();
        sources.remove("root");
        asg.forgetSourcesExcept(sources);
        asg.addSource("root", artRootName);
        asg.addEdge(artRootNode, asg.getNode(oldRoot), ROOT_EDGE_LABEL+sntCounter);
        sntCounter++;
        for (Set<GraphNode> connectedSet : inspector.connectedSets()){
            // find the head of the span (companion data) of this component
            // (assumes this component of the graph forms a contiguous span)
            // and make it the root of this subgraph
            // the head word of the span may align to multiple words 
            if (connectedSet.contains(oldRootNode)) continue;
            TokenRange firstPos = minAnchorStart(connectedSet);
            TokenRange lastPos = maxAnchorEnd(connectedSet);

            int head = sent.headOfSpan(sent.getCorrespondingIndex(firstPos),
                        sent.getCorrespondingIndex(lastPos));
            Alignment headAlignment = null;
            for (Alignment al : inst.getAlignments()){
                if (al.span.start == head){
                    headAlignment = al;
                    break;
                }
            }
            if ( headAlignment != null){
                SGraph headFragment = am.parseString(sigBuilder.getConstantsForAlignment(headAlignment, asg, false).iterator().next()).left;
                String headRoot = headFragment.getNodeForSource("root");
                asg.addEdge(artRootNode, asg.getNode(headRoot), ROOT_EDGE_LABEL+sntCounter);
            } else {
               System.err.println("[ADD ART-ROOT] WARNING: -- taking arbitrary root for component");
               GraphNode someNode = connectedSet.stream().filter(node -> !AnchoredSGraph.isLnkNode(node)).findFirst().get();
               asg.addEdge(artRootNode,someNode, ROOT_EDGE_LABEL+sntCounter);
            }
        sntCounter++;
        }
    }
    
    
        /**
     * Removes the artificial root.
     * @param mrpGraph
     * @return 
     */
    public static MRPGraph removeArtificalRoot(MRPGraph mrpGraph){
        MRPGraph copy = mrpGraph.deepCopy();
        if (mrpGraph.getTops().size() != 1){
            return copy; //no artificial root
        }
        
        int rootId = copy.getTops().iterator().next();
        
        if (!copy.getNode(rootId).getLabel().equals(ART_ROOT)){
            return copy; //no artificial root
        }
        copy.setTops(new HashSet<>());
        
        for (MRPEdge outg : copy.outgoingEdges(rootId)){
            if (outg.label.equals(ROOT_EDGE_LABEL+"1")){
                copy.getTops().add(outg.target); //add a new top node.
                break;
            }
        }
        //remove all edges attached to ART-ROOT:
        for (MRPEdge e : copy.edgesOf(rootId)){
            copy.getEdges().remove(e);
        }
        //finally, remove ART-ROOT node:
        copy.getNodes().remove(copy.getNode(rootId));
        copy.setInput(copy.getInput().substring(0, copy.getInput().length() - ART_ROOT.length() -1 )); //-1 for space before ART_ROOT
        
        //If the parser didn't produce perfect results, some anchors might refer to the artificial root:
        int maxAnchor = copy.getInput().length();
        for (MRPNode n : copy.getNodes()){
            for (MRPAnchor a: n.getAnchors()){
                if (a.from > maxAnchor){
                    a.from = maxAnchor;
                }
                if (a.to > maxAnchor){
                    a.to = maxAnchor;
                }
            }
        }
        
        return copy;
        
    }
    
    
     /**
     * Returns the position of the first character described by the anchoring of any node in the list.
     * @param nodes
     * @return 
     */
    private static TokenRange minAnchorStart(Collection<GraphNode> nodes){
        int m = Integer.MAX_VALUE;
        TokenRange anchor = null;
        for (GraphNode n : nodes){
            if (AnchoredSGraph.isLnkNode(n)){
                TokenRange r = AnchoredSGraph.getRangeOfLnkNode(n);
                if (r.getFrom() < m) {
                    m = r.getFrom();
                    anchor = r;
                }
            }
        }
        return anchor;
    }
    
    private static TokenRange maxAnchorEnd(Collection<GraphNode> nodes){
        int m = Integer.MIN_VALUE;
        TokenRange anchor = null;
        for (GraphNode n : nodes){
            if (AnchoredSGraph.isLnkNode(n)){
                TokenRange r = AnchoredSGraph.getRangeOfLnkNode(n);
                if (r.getTo() > m) {
                    m = r.getTo();
                    anchor = r;
                }
            }
        }
        return anchor;
    }
    
    
        

    
    
}
