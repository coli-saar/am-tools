/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.PropertyDetection;


import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.BlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.*;

import java.util.stream.Collectors;


/**
 * A simple tool to evaluate the AM Dependency Terms in a conll corpus and outputs the graphs one by one in AMR ISI notation so that they can be relexicalized later.
 * @author matthias
 */
public class EvaluateCorpus {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/debugging_tratz/properties/17_dev.amconll";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/debugging_tratz/properties/";
    
   @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
        
   //For relabeler, all optional
   @Parameter(names = {"--relabel"}, description = "perform relabeling automatically")
    private boolean relabel = false;
   
   @Parameter(names = {"--keep-aligned"}, description = "keep index of token position in node label")
    private boolean keepAligned = false;
   
   @Parameter(names = {"--th"}, description = "Threshold for relabeler. Default: 10")
    private int threshold = 10;

    @Parameter(names = {"--wn"}, description = "Path to WordNet")
    private String wordnet = "/home/matthias/Schreibtisch/Hiwi/am-parser/external_eval_tools/2019rerun/metadata/wordnet/3.0/dict/";
    
    @Parameter(names = {"--lookup"}, description = "Lookup path. Path to where the files nameLookup.txt, nameTypeLookup.txt, wikiLookup.txt, words2labelsLookup.txt are.")//, required = true)
    private String lookup = "/home/matthias/Schreibtisch/Hiwi/am-parser/external_eval_tools/2019rerun/lookupdata17/";
    
    
    public static final String AL_LABEL_SEP = "|";

    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException, MalformedURLException, InterruptedException{      
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
        
        if (cli.keepAligned && ! cli.relabel){
            System.err.println("Keeping the alignments without relabeling is not supported");
            return;
        }
        
        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);
        PrintWriter o = new PrintWriter(cli.outPath+"/parserOut.txt"); //will contain graphs, potentially relabeled
        PrintWriter l = null;
        PrintWriter indices = null;
        
        Relabel relabeler = null;
        
        if (!cli.relabel){
            l = new PrintWriter(cli.outPath+"/labels.txt");
            indices = new PrintWriter(cli.outPath+"/indices.txt");
        } else {
             relabeler = new Relabel(cli.wordnet, null, cli.lookup, cli.threshold, 0,false);
        }

        int index = 0;
        for (AmConllSentence s : sents){
            
            //fix the REPL problem:
            //the NN was trained with data where REPL was used for some nouns because the lexical label was lower-cased
            //we don't want $REPL$ in our output, so let's replace predictions that contain REPL but where there is no replacement field
            //with the word form.
            
            for (AmConllEntry e : s){
                if (e.getLexLabel().contains(AmConllEntry.REPL_PLACEHOLDER) && e.getReplacement().equals(AmConllEntry.DEFAULT_NULL)){
                    e.setLexLabel(e.getReLexLabel().replace(AmConllEntry.REPL_PLACEHOLDER, AmConllEntry.FORM_PLACEHOLDER));
                }
            }
            
            if (!cli.relabel){
                indices.println(index);
            }
            
            index++;
            //prepare raw output without edges
            try {
                AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluateWithoutRelex(true);
                
                List<Alignment> alignments = null;
                
                if (cli.relabel){ // in principle, this shouldn't hurt if we also did that in case of not relabeling but this is safer.
                    evaluatedGraph = evaluatedGraph.withFreshNodenames();
                    alignments = AlignedAMDependencyTree.extractAlignments(evaluatedGraph);
                }

                
                //rename nodes names from 1@@m@@--LEX-- to LEX@0
                List<String> labels = s.lemmas();
                for (String n : evaluatedGraph.getAllNodeNames()){
                    if (evaluatedGraph.getNode(n).getLabel().contains("LEX")){
                        Pair<Integer,Pair<String,String>> info = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                        labels.set(info.left-1, s.get(info.left-1).getReLexLabel());
                        evaluatedGraph.getNode(n).setLabel(Relabel.LEXMARKER+(info.left-1));
                    } else {
                        Pair<Integer,Pair<String,String>> info = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                        evaluatedGraph.getNode(n).setLabel(info.right.right);
                    }
                }
                
                if (!cli.relabel){
                    
                    l.println(labels.stream().map(lbl -> lbl.equals(AmConllEntry.DEFAULT_NULL) ? "NULL" : lbl).collect(Collectors.joining(" ")));
                    
                } else {
                    
                    // relabel graph                    
                    relabeler.fixGraph(evaluatedGraph, s.getFields((AmConllEntry entry) ->
                     {
                         if (entry.getReplacement().equals("_")) {
                             //if supertagger thinks this is a named entity, we trust it
                             if (entry.getLexLabel().toLowerCase().equals("_name_")){
                                 return entry.getLexLabel().toLowerCase();
                             }
                             return entry.getForm().toLowerCase();
                         } else {
                             return entry.getReplacement().toLowerCase();
                         }
                    }), s.words(), s.getFields(entry -> entry.getReLexLabel()));
                    
                    if (cli.keepAligned){
                        //now add alignment again, format: POSITION|NODE LABEL where POSITION is 0-based.
                        
                        Map<String,Integer> nodeNameToPosition = new HashMap<>();
                        
                        for (Alignment al : alignments){
                            for (String nodeName : al.nodes){
                                nodeNameToPosition.put(nodeName, al.span.start);
                            }
                        }             
                        
                        for (String nodeName : nodeNameToPosition.keySet()){
                            GraphNode node = evaluatedGraph.getNode(nodeName);
                            if (node == null){
                                System.err.println("Warning: a nodename for which we have an alignment cannot be found in the relabeled graph");
                            } else {
                                node.setLabel(nodeNameToPosition.get(nodeName) + AL_LABEL_SEP + node.getLabel());
                            }
                            
                        }               
                        
                       // try to find alignments for nodes that the relabeling introduced.
                       
                        for (GraphNode node : evaluatedGraph.getGraph().vertexSet()) {
                            if (!nodeNameToPosition.containsKey(node.getName())) {
                                Set<GraphEdge> edges = evaluatedGraph.getGraph().edgesOf(node);
                                if (edges.size() == 1) {
                                    GraphNode endPoint = BlobUtils.otherNode(node, edges.iterator().next());
                                    if (nodeNameToPosition.containsKey(endPoint.getName())) {
                                        node.setLabel(nodeNameToPosition.get(endPoint.getName()) + AL_LABEL_SEP + node.getLabel());
                                    } else {
                                        System.err.println("Warning: cannot find unique alignment for a node with no inherent alignment.");
                                    }
                                } else {
                                    System.err.println("Warning: cannot find unique alignment for a node with no inherent alignment and multiple adjacent edges.");
                                }
                            }
                        }
                    }
                    
                }
                
                if (cli.relabel) {
                     //fix properties
                    evaluatedGraph = PropertyDetection.fixProperties(evaluatedGraph);
                }
                
                o.println(evaluatedGraph.toIsiAmrString());
                if (cli.relabel) o.println();
                
            } catch (Exception ex){
                System.err.println("In line "+s.getLineNr());
                System.err.println("Ignoring exception:");
                ex.printStackTrace();
                System.err.println("Writing dummy graph instead");
                
                o.println("(d / dummy)");
                
                if (!cli.relabel){
                    l.println();
                } else {
                    o.println();
                }
            }
        }
        if (!cli.relabel){
          indices.close();
          l.close();
        }
        
        o.close();
        
 
        
    }
    
    
    
}
