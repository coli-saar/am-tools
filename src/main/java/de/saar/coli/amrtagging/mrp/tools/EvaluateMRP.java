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
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.graphs.TestSentence;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Creates mrp corpus from amconll corpus by evaluating all the AM dependency trees to graphs.
 * 
 * @author matthias
 */
public class EvaluateMRP {
    @Parameter(names = {"--corpus"}, description = "Input corpus file (*.amconll)")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Mario/ucca/new/train.amconll";
    
    @Parameter(names = {"--out", "-o"}, description = "Output corpus file (*.mrp)")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Mario/ucca/new/train.mrp";
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode")
    private boolean debug=false;
    
    @Parameter(names = {"--input"}, description = "input.mrp file to extract input strings, only required when run on TEST data")//, required = true)
    private String input = null;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        EvaluateMRP cli = new EvaluateMRP();
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
        
        Map<String,TestSentence> id2testsent = null;
        if (cli.input != null){
            id2testsent = TestSentence.read(new FileReader(cli.input));
        }

        for (ConllSentence sentence : parsed){
            String framework = sentence.getAttr("framework");
            Formalism formalism;
            if (framework.equals("dm")){
                formalism = new DM();
            } else if (framework.equals("psd")){
                formalism = new PSD();
            } else if (framework.equals("eds")){
                formalism = new EDS(new ArrayList<>()); //don't need tagging here
            } else if (framework.equals("ucca")){
                formalism = new UCCA();
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+framework+" not supported yet.");
            }
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
                evaluatedGraph = MRPUtils.getDummy(framework, sentence.getId(),input,sentence.getAttr("time"), sentence.getAttr("version"));
                evaluatedGraph.setTime(new SimpleDateFormat("yyyy-MM-dd (hh:mm)").format(new Date())); //2019-04-10 (20:10)
            }
            if (id2testsent != null){
                if (id2testsent.containsKey(evaluatedGraph.getId())){
                    String inp = evaluatedGraph.getInput();
                    if (!inp.equals(evaluatedGraph.getInput())){
                        System.err.println("Input string differs for graph "+evaluatedGraph.getId()+" restoring string from --input");
                        evaluatedGraph.setInput(inp);
                    }
                } else {
                    System.err.println("Couldn't find input belonging to id "+evaluatedGraph.getId()+ ". The --input option should be used only for the test data");
                }
            }
            MRPUtils.removeInvalidAnchros(evaluatedGraph, true);
            
            outputCodec.write(evaluatedGraph, output);
        }
        
    }
        
    
}
