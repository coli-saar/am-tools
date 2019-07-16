/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.ucca;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.AnchoredSGraph;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.TokenRange;
import de.saar.coli.amrtagging.Util;
import static de.saar.coli.amrtagging.Util.fixPunct;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author matthias
 */
public class UCCA implements Formalism {

    @Override
    public ConlluSentence refine(ConlluSentence sentence) {
        ConlluSentence copy = sentence.withSameMetaData();
        List<String> withoutWeirdCharacters = refineTokens(sentence.words());
        for (int i = 0; i < sentence.size();i++){
            String form = withoutWeirdCharacters.get(i);
            copy.get(i).setForm(form);
        }
        return copy;
    }
    
    /**
     * Fixes non-ascii characters.
     * @param tokens
     * @return 
     */
    public List<String> refineTokens(List<String> tokens){
        List<String> copy = new ArrayList<>();
        for (String token : tokens){
            //normalize (separate accent from accented letter) and remove accents
            token = Util.fixPunct(token);
            token = Normalizer.normalize(token, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            token = Util.isiAMREscape(token);
            copy.add(token);
        }
        return copy;
    }
    

    @Override
    public MRPGraph preprocess(MRPGraph mrpgraph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MRPGraph postprocess(MRPGraph mrpgraph) {
        return mrpgraph;
    }

    @Override
    public MRInstance toMRInstance(ConlluSentence sentence, MRPGraph mrpgraph) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public MRPGraph evaluate(ConllSentence amconll) {
        
        //this should be moved to a better place
        List<String> withoutWeirdCharacters = refineTokens(amconll.words());
        for (int i = 0; i < amconll.size();i++){
            String form = withoutWeirdCharacters.get(i);
            amconll.get(i).setForm(form);
        }
        try {
            AMDependencyTree amdep = AMDependencyTree.fromSentence(amconll);
            SGraph evaluatedGraph = amdep.evaluate(true);
            AnchoredSGraph withAnchors = addAnchors(evaluatedGraph, amconll);
            MRPGraph output = MRPUtils.fromAnchoredSGraph(withAnchors, false, 1, "ucca", amconll.getId(), amconll.getAttr("input"), amconll.getAttr("version"), amconll.getAttr("time"),false);
            return output;
         } catch (ParseException | ParserException | AMDependencyTree.ConllParserException e){
             throw new IllegalArgumentException(e);
         }
    }

    @Override
    public AMSignatureBuilder getSignatureBuilder(MRInstance instance) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public AlignmentTrackingAutomaton getAlignmentTrackingAutomaton(MRInstance instance) throws ParseException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void refineDelex(ConllSentence sentence) {
        for (ConllEntry entry : sentence){
            entry.setLexLabelWithoutReplacing("$FORM$");
        }
    }
    
    /**
     * Adds anchoring to SGraph in EDS style.
     * @param evaluatedGraph
     * @param amconll
     * @return 
     */
    private AnchoredSGraph addAnchors(SGraph evaluatedGraph, ConllSentence amconll){
        SGraph copy = evaluatedGraph.merge(new SGraph());
        
        int nodeCounter = 0;
        List<GraphNode> vertices = new ArrayList<>(copy.getGraph().vertexSet());
        for (GraphNode n : vertices ){
            Pair<Integer, Pair<String, String>> info = AMDependencyTree.decodeNode(n);
            int position = info.left-1;
            String label = info.right.right;
            n.setLabel(label);
            if (! label.equals("Non-Terminal")){
               TokenRange r = amconll.get(position).getRange();
               GraphNode lnkNode = copy.addNode("an"+nodeCounter, "<"+r.getFrom()+":"+r.getTo()+">");
               copy.addEdge(n, lnkNode, AnchoredSGraph.LNK_LABEL);
               nodeCounter++;
            }
            
        }
        
        return AnchoredSGraph.fromSGraph(copy);
    }
    
}
