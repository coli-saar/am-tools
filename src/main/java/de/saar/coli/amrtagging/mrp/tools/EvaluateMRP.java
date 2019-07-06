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
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates mrp corpus from amconll corpus.
 * 
 * @author matthias
 */
public class EvaluateMRP {
    @Parameter(names = {"--corpus"}, description = "Path to the input corpus")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/EDS/bla.amconll";
    
    @Parameter(names = {"--out", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/EDS/bla.mrp";
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
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

        for (ConllSentence sentence : parsed){
            String framework = sentence.getAttr("framework");
            Formalism formalism;
            if (framework.equals("dm")){
                formalism = new DM();
            } else if (framework.equals("psd")){
                formalism = new PSD();
            } else if (framework.equals("eds")){
                formalism = new EDS(new ArrayList<>()); //don't need tagging here
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+framework+" not supported yet.");
            }
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
