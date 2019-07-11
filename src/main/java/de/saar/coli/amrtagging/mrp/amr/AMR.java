/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.amr;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.AMSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.Relabel;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.tree.ParseException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *
 * @author matthias
 */
public class AMR implements Formalism{
    
    private final Relabel relabeler;
    
    //List of most frequent "properties" (we treat them as edge labels)
    public static final List<Pattern> PROPERTY_EDGES = Arrays.asList( 
        Pattern.compile("op[0-9]+"), Pattern.compile("polarity"), Pattern.compile("quant"), Pattern.compile("mode"),
        Pattern.compile("year[0-9]*"),Pattern.compile("month"), Pattern.compile("day"),
        Pattern.compile("li"), Pattern.compile("polite"),
        Pattern.compile("decade"), Pattern.compile("century"),
        Pattern.compile("timezone"), Pattern.compile("era"));
    
    
    
    
    public AMR(String wordnetPath, String conceptnetPath, String mapsPath, int nnThreshold) throws IOException, MalformedURLException, InterruptedException{
        relabeler = new Relabel(wordnetPath, conceptnetPath, mapsPath, nnThreshold, 0);
    }
    
    
    @Override
    public ConlluSentence refine(ConlluSentence sentence) {
        return sentence;
    }

    @Override
    public MRPGraph preprocess(MRPGraph mrpgraph) {
        return mrpgraph;
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
        SGraph evaluatedGraph;
        try {
            AMDependencyTree amdep = AMDependencyTree.fromSentence(amconll);
            evaluatedGraph = amdep.evaluateWithoutRelex(true);
        } catch (ParserException | AMDependencyTree.ConllParserException  | ParseException ex ) {
            ex.printStackTrace();
            System.err.println("Returning empty graph");
            return MRPUtils.getDummy("amr", amconll.getId(), amconll.getAttr("raw"), amconll.getAttr("time"), amconll.getAttr("version"));
        }
        //rename nodes names from 1@@m@@--LEX-- to LEX@0
        List<String> labels = amconll.lemmas();
        for (String n : evaluatedGraph.getAllNodeNames()){
            if (evaluatedGraph.getNode(n).getLabel().contains("LEX")){
                Pair<Integer,Pair<String,String>> info = AMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                labels.set(info.left-1, amconll.get(info.left-1).getReLexLabel());
                evaluatedGraph.getNode(n).setLabel("LEX@"+(info.left-1));
            } else {
                Pair<Integer,Pair<String,String>> info = AMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                evaluatedGraph.getNode(n).setLabel(info.right.right);
            }
        }
        evaluatedGraph = evaluatedGraph.withFreshNodenames();
        relabeler.fixGraph(evaluatedGraph, amconll.getFields((ConllEntry entry) ->
         {
             if (entry.getReplacement().equals("_")) {
                 return entry.getForm().toLowerCase();
             } else {
                 return entry.getReplacement().toLowerCase();
             }
        }), amconll.words(), amconll.getFields(entry -> {
            String relex = entry.getReLexLabel();
            if (relex.equals("_")) return "NULL";
                    else return relex;
        }));
        removeWikiEdges(evaluatedGraph);
        
        MRPGraph g = MRPUtils.fromSGraph(evaluatedGraph, PROPERTY_EDGES, 2, "amr", amconll.getId(), amconll.getAttr("raw"), amconll.getAttr("version"), amconll.getAttr("time"));
        return g;
        
    }
    
    /**
     * Removes wiki edges and the nodes at their end since they don't occur in the MRP data.
     * @param sg 
     */
    private void removeWikiEdges(SGraph sg){
        List<GraphEdge> edges = new ArrayList<>(sg.getGraph().edgeSet());
        for (GraphEdge e : edges){
            if (e.getLabel().equals("wiki")){
                sg.getGraph().removeEdge(e);
                sg.removeNode(e.getTarget().getName());
            }
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
        
    }
    
}
