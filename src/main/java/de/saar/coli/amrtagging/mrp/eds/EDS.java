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
import de.saar.coli.amrtagging.mrp.graphs.MRPEdge;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.MRPNode;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.saar.coli.amrtagging.mrp.utils.StupidPOSTagger;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.tree.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        MRPUtils.addArtificalRoot(sentence, mrpgraph);
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
        
        
        MRInstance inst = Aligner.extractAlignment(asg, tokDifferentForHyphens, sentence.lemmas());
        //AlignerFixes.fixSingleEdgeUnaligned(inst);

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

            MRPGraph mrpGraph = MRPUtils.fromAnchoredSGraph(evaluatedGraph, 1, "eds",
                    amconll.getId(), amconll.getAttr("raw"), amconll.getAttr("version"), amconll.getAttr("time"));
            return MRPUtils.removeArtificalRoot(mrpGraph);
            
        } catch (ParseException | ParserException | AMDependencyTree.ConllParserException e){
             throw new IllegalArgumentException(e);
         }
    }

    
    @Override
    public AMSignatureBuilder getSignatureBuilder(MRInstance inst) {
         return new ConcreteAlignmentSignatureBuilder(inst.getGraph(), inst.getAlignments(), new EDSBlobUtils());
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
    
        

    
    
}
