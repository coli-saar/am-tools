/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.amr.AMR;
import de.saar.coli.amrtagging.mrp.graphs.TestSentence;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Creates mrp corpus from amconll corpus for AMR, does relabeling.
 * 
 * @author matthias
 */
public class EvaluateAMR {
    @Parameter(names = {"--corpus"}, description = "Path to the input amconll corpus")//, required = true)
    private String corpusPath = "/tmp/dm/test_MRP-AMR.amconll";
    
    @Parameter(names = {"--wn"}, description = "Path to WordNet")//, required = true)
    private String wordnet = "/home/matthias/Schreibtisch/Hiwi/am-parser/external_eval_tools/2019rerun/metadata/wordnet/3.0/dict/";
    
    @Parameter(names = {"--conceptnet"}, description = "Path to ConceptNet (.csv.gz file)")//, required = true)
    private String conceptnet = null;// "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/conceptnet-assertions-5.7.0.csv.gz";
    
    @Parameter(names = {"--lookup"}, description = "Lookup path. Path to where the files nameLookup.txt, nameTypeLookup.txt, wikiLookup.txt, words2labelsLookup.txt are.")//, required = true)
    private String lookup = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/AMR/first_legal/lookup/";
    
    @Parameter(names = {"--out", "-o"}, description = "where to store the MRP graphs")//, required = true)
    private String outPath = "/tmp/dm/test_MRP-AMR.mrp";
    
    @Parameter(names = {"--input"}, description = "input.mrp file to extract input strings, only required when run on TEST data")//, required = true)
    private String input = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode")
    private boolean debug=false;

    @Parameter(names = {"--prop-suffix"}, description = "Turn this on if your training data uses the -prop suffix ")
    private boolean usePropSuffix=false;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException, MalformedURLException, InterruptedException{
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
              
        List<AmConllSentence> parsed = AmConllSentence.readFromFile(cli.corpusPath);
        OutputStream output = new FileOutputStream(cli.outPath);
        MRPOutputCodec outputCodec = new MRPOutputCodec();
        Formalism formalism = new AMR(cli.wordnet, cli.conceptnet, cli.lookup, 10,cli.usePropSuffix);
        
        Map<String,TestSentence> id2testsent = null;
        if (cli.input != null){
            id2testsent = TestSentence.read(new FileReader(cli.input));
        }
        
        for (AmConllSentence sentence : parsed){
            String framework = sentence.getAttr("framework");
            MRPGraph evaluatedGraph;
            try {
                 evaluatedGraph = formalism.evaluate(sentence);
                 evaluatedGraph = formalism.postprocess(evaluatedGraph);
                 evaluatedGraph.setTime(new SimpleDateFormat("yyyy-MM-dd (hh:mm)").format(new Date())); //2019-04-10 (20:10)
                 if (evaluatedGraph.getVersion() == null){
                     evaluatedGraph.setVersion("0.9"); //the version we've been using
                 }
            } catch (Exception ex){
                System.err.println("Error in line "+sentence.getLineNr()+" with id "+sentence.getId());
                ex.printStackTrace();
                System.err.println("Writing empty MRP graph instead");
                String input = sentence.getAttr("raw");
                if (input == null){
                    input = sentence.getAttr("input");
                }
                evaluatedGraph = MRPUtils.getDummy("amr", sentence.getId(),input,sentence.getAttr("time"), sentence.getAttr("version"));
                evaluatedGraph.setTime(new SimpleDateFormat("yyyy-MM-dd (hh:mm)").format(new Date())); //2019-04-10 (20:10)
            }
            if (id2testsent != null){
                if (id2testsent.containsKey(evaluatedGraph.getId())){
                    String inp = evaluatedGraph.getInput();
                    String properInput = id2testsent.get(evaluatedGraph.getId()).input;
                    if (inp == null){
                        evaluatedGraph.setInput(properInput);
                    } else if (!inp.equals(properInput)){
                        System.err.println("Input string differs for graph "+evaluatedGraph.getId()+" restoring string from --input");
                        evaluatedGraph.setInput(properInput);
                    }
                } else {
                    System.err.println("Couldn't find input belonging to id "+evaluatedGraph.getId()+ ". The --input option should be used only for the test data");
                }
            }
            //MRPUtils.removeInvalidAnchors(evaluatedGraph, true);
            
            outputCodec.write(evaluatedGraph, output);
        }
        
    }
        
    
}
