/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.dm.tools;

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

import se.liu.ida.nlp.sdp.toolkit.graph.*;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;
import se.liu.ida.nlp.sdp.toolkit.tools.Scorer;


import java.util.List;
import se.liu.ida.nlp.sdp.toolkit.io.Constants;
import se.liu.ida.nlp.sdp.toolkit.io.GraphWriter2015;

/**
 * Converts an AM Dependency corpus (amconll) into an SDP corpus (.sdp). Is known to work for DM and PAS. 
 * FOR PSD PLEASE USE THE SEPARATE CLASS!
 * @author matthias
 */
public class ToSDPCorpus {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/tmp/dm/dev_epoch_SDP-DM-2015_2.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/tmp/dm/";
    
    @Parameter(names={"--gold","-g"}, description = "Path to gold corpus. Make sure it contains exactly the same instances, in the same order.")//, required=true)
    private String goldCorpus = null; //"/home/matthias/uni/multi-amparser/data/SemEval/2015/DM/dev/dev.sdp";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{      
        ToSDPCorpus cli = new ToSDPCorpus();
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
            Graph sdpSent = new Graph(id);
            sdpSent.addNode(Constants.WALL_FORM, Constants.WALL_LEMMA, Constants.WALL_POS, false, false, Constants.WALL_SENSE); //some weird dummy node.

            for (AmConllEntry word : s){ //build a SDP Graph with only the words copied from the input.
                if (! word.getForm().equals(SGraphConverter.ARTIFICAL_ROOT_LABEL)){
                    sdpSent.addNode(word.getForm(), word.getLemma(), word.getPos(), false, false, "_");
                }
            }
            
            boolean read = false;
            
            try {
                AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluate(true);
                Graph outputSent = SGraphConverter.toSDPGraph(evaluatedGraph, sdpSent); //add edges
                
                if (goldReader != null){
                    read = true;
                    Graph goldGraph = goldReader.readGraph();
                    scorer.update(goldGraph, outputSent);
                }
                
                if (grW != null){
                    grW.writeGraph(outputSent);
                }
            } catch (Exception ex){
                System.err.printf("In line %d, id=%s: ignoring exception.\n", s.getLineNr(), id);
                
//                AMDependencyTree amdep = AMDependencyTree.fromSentence(s);
//                SGraph evaluatedGraph = amdep.evaluate(true);
//                SGraphDrawer.draw(evaluatedGraph, "");
                
                ex.printStackTrace();
                System.err.println("Writing graph without edges instead.\n");
                
                grW.writeGraph(sdpSent);
                
                if (!read && goldReader != null){
                    Graph goldGraph = goldReader.readGraph();
                    scorer.update(goldGraph, sdpSent);
                }
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
