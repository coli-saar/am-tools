/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllSentence;


import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * A simple tool to evaluate the AM Dependency Terms in a conll corpus and outputs the graphs one by one in AMR ISI notation so that they can be relexicalized later.
 * @author matthias
 */
public class EvaluateCorpus {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/home/matthias/uni/multi-amparser/models/deleteme/dev_epoch_AMR-2015_1.amconll";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/uni/multi-amparser/models/deleteme/";
    

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        EvaluateCorpus cli = new EvaluateCorpus();
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
        
        List<ConllSentence> sents = ConllSentence.readFromFile(cli.corpusPath);
        PrintWriter o = new PrintWriter(cli.outPath+"/parserOut.txt");
        PrintWriter l = new PrintWriter(cli.outPath+"/labels.txt");
        PrintWriter indices = new PrintWriter(cli.outPath+"/indices.txt");
        int index = 0;
        for (ConllSentence s : sents){
            indices.println(index);
            index++;
            //prepare raw output without edges
            try {
                AMDependencyTree amdep = AMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluateWithoutRelex(true);
                //rename nodes names from 1@@m@@--LEX-- to LEX@0
                List<String> labels = s.lemmas();
                for (String n : evaluatedGraph.getAllNodeNames()){
                    if (evaluatedGraph.getNode(n).getLabel().contains("LEX")){
                        Pair<Integer,Pair<String,String>> info = AMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                        labels.set(info.left-1, s.get(info.left-1).getReLexLabel());
                        evaluatedGraph.getNode(n).setLabel("LEX@"+(info.left-1));
                    } else {
                        Pair<Integer,Pair<String,String>> info = AMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                        evaluatedGraph.getNode(n).setLabel(info.right.right);
                    }
                }
                l.println(labels.stream().map(lbl -> lbl.equals("_") ? "NULL" : lbl).collect(Collectors.joining(" ")));
                o.println(evaluatedGraph.toIsiAmrString());
            } catch (Exception ex){
                System.err.println("In line "+s.getLineNr());
                System.err.println("Ignoring exception:");
                ex.printStackTrace();
                System.err.println("Writing dummy graph instead");
                o.println("(d / dummy)");
                l.println();
            }
        }
        indices.close();
        o.close();
        l.close();
 
        
    }
    

    
}
