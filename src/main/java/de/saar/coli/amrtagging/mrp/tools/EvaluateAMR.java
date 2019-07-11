/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.amr.AMR;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates mrp corpus from amconll corpus for AMR, does relabeling.
 * 
 * @author matthias
 */
public class EvaluateAMR {
    @Parameter(names = {"--corpus"}, description = "Path to the input amconll corpus")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/everything/amconll/split/dev.amconll";
    
    @Parameter(names = {"--wn"}, description = "Path to WordNet")//, required = true)
    private String wordnet = "/home/matthias/Schreibtisch/Hiwi/am-parser/external_eval_tools/2019rerun/metadata/wordnet/3.0/dict/";
    
    @Parameter(names = {"--conceptnet"}, description = "Path to ConceptNet (.csv.gz file)")//, required = true)
    private String conceptnet = null;
    
    @Parameter(names = {"--lookup"}, description = "Lookup path. Path to where the files nameLookup.txt, nameTypeLookup.txt, wikiLookup.txt, words2labelsLookup.txt are.")//, required = true)
    private String lookup = "/home/matthias/Schreibtisch/Hiwi/am-parser/external_eval_tools/2019rerun/MRP_first_run/";
    
    @Parameter(names = {"--out", "-o"}, description = "where to store the MRP graphs")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/everything/amconll/split/dev_relabeled.mrp";
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException, MalformedURLException, InterruptedException{      
        EvaluateAMR cli = new EvaluateAMR();
        JCommander commander = new JCommander(cli);

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
              
        List<ConllSentence> parsed = ConllSentence.readFromFile(cli.corpusPath);
        OutputStream output = new FileOutputStream(cli.outPath);
        MRPOutputCodec outputCodec = new MRPOutputCodec();
        Formalism formalism = new AMR(cli.wordnet, cli.conceptnet, cli.lookup, 10);
        
        for (ConllSentence sentence : parsed){
            String framework = sentence.getAttr("framework");
            MRPGraph evaluatedGraph;
            try {
                 evaluatedGraph = formalism.evaluate(sentence);
                 evaluatedGraph = formalism.postprocess(evaluatedGraph);
            } catch (Exception ex){
                System.err.println("Error in line "+sentence.getLineNr()+" with id "+sentence.getId());
                ex.printStackTrace();
                System.err.println("Writing empty MRP graph instead");
                evaluatedGraph = MRPUtils.getDummy(framework, sentence.getId(),sentence.getAttr("raw"),sentence.getAttr("time"), sentence.getAttr("version"));
            }
            
            outputCodec.write(evaluatedGraph, output);
        }
        
    }
        
    
}
