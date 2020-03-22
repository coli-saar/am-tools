/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;

import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;

import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;


import java.util.List;
import java.util.Map;
import java.util.Set;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;
import se.liu.ida.nlp.sdp.toolkit.io.GraphWriter2015;

/**
 * Takes an amconll corpus with edges attribute (encoding all edges) and puts them back into an SDP file.
 * @author matthias
 */
public class RestoreEdges {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/COLING_20/baseline-graph-parser/data/SemEval/2015/DM/dev/dev.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/tmp/dm";
    
    @Parameter(names={"--gold","-g"}, description = "Path to gold corpus. Make sure it contains exactly the same instances, in the same order.")//, required=true)
    private String goldCorpus = null;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        RestoreEdges cli = new RestoreEdges();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("constraint_extractor");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }
        
        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);
        GraphReader2015 goldReader = null;
        if (cli.goldCorpus != null){
            goldReader = new GraphReader2015(cli.goldCorpus);
        }
        GraphWriter2015 grW = null;
        if (cli.outPath != null){
            grW = new GraphWriter2015(cli.outPath+".sdp");
        }
        
        Scorer scorer = new Scorer();
        
        for (AmConllSentence s : sents){
            // prepare raw output without edges
            String id = s.getAttr("id") != null ? s.getAttr("id") : "#NO-ID";
            if (! id.startsWith("#")) id = "#" + id;
            Graph sdpSent = new Graph(id);
            sdpSent.addNode(Constants.WALL_FORM, Constants.WALL_LEMMA, Constants.WALL_POS, false, false, Constants.WALL_SENSE); //some weird dummy node.
            
            // edges
            String edges = s.getAttr("edges");
            List<Edge> edgeObjects = new ArrayList<>();
            Set<Integer> isPred = new HashSet<>();
            
            if (!edges.equals("[]")){
                // Example for edges:
                // [[4,1,"ARG1"],[1,3,"_and_c"],[5,4,"loc"],[6,4,"ARG1"],[6,7,"ARG2"]]
                String[] triples = edges.split("\\],\\["); // split at ],[ 
                // -> [[4,1,"ARG1"   1,3,"_and_c"   5,4,"loc"   6,4,"ARG1"  6,7,"ARG2"]]

                for (String triple : triples){
                    triple = triple.replace("[[", "").replace("]]","").replace("\"","");
                    String[] parts = triple.split(",");
                    int from = Integer.parseInt(parts[0]);
                    int to = Integer.parseInt(parts[1]);
                    String label = parts[2];

                    edgeObjects.add(new Edge(0, from, to, label));
                    isPred.add(from);
                }
            }
            
            // tops
            Set<Integer> isTop = new HashSet<>();
            
            if (!s.getAttr("tops").equals("[]")){
                String topsStr = s.getAttr("tops").replace("]","").replace("[","");

                for (String top : topsStr.split(",")){
                    isTop.add(Integer.parseInt(top));
                }
            }

            for (AmConllEntry word : s){ //build a SDP Graph with only the words copied from the input.
                if (! word.getForm().equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)){
                    sdpSent.addNode(word.getForm(), word.getLemma(), word.getPos(), isTop.contains(word.getId()), isPred.contains(word.getId()), "_");
                }
            }
            
            for (Edge e : edgeObjects){
                sdpSent.addEdge(e.source, e.target, e.label);
            }
            

            if (goldReader != null){
                Graph goldGraph = goldReader.readGraph();
                scorer.update(goldGraph, sdpSent);
            }

            if (grW != null){
                grW.writeGraph(sdpSent);
            }

        }
        if (grW != null){
            grW.close();
        }
        if (goldReader != null){
           System.out.println("Labeled Scores");
           System.out.println("Precision "+scorer.getPrecision());
           System.out.println("Recall "+scorer.getRecall());
           System.out.println("F "+scorer.getF1());
           System.out.println("Exact Match "+scorer.getExactMatch());
           System.out.println("------------------------");
           System.out.println("Core Predications");
           System.out.println("Precision "+scorer.getCorePredicationsPrecision());
           System.out.println("Recall "+scorer.getCorePredicationsRecall());
           System.out.println("F "+scorer.getCorePredicationsF1());
           System.out.println("------------------------");
           System.out.println("Semantic Frames");
           System.out.println("Precision "+scorer.getSemanticFramesPrecision());
           System.out.println("Recall "+scorer.getSemanticFramesRecall());
           System.out.println("F "+scorer.getSemanticFramesF1());
        }
 
        
    }
    

    
}
