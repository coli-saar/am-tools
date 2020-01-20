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
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;


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
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Tatiana/gold-dev.amconll";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Tatiana/";
    
   @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
        
   //For relabeler, all optional
   @Parameter(names = {"--relabel"}, description = "perform relabeling automatically")
    private boolean relabel = false;
   
   @Parameter(names = {"--keep-aligned"}, description = "keep index of token position in node label")
    private boolean keepAligned = false;
    
    @Parameter(names = {"--wn"}, description = "Path to WordNet")
    private String wordnet = "/home/matthias/Schreibtisch/Hiwi/am-parser/external_eval_tools/2019rerun/metadata/wordnet/3.0/dict/";
    
    @Parameter(names = {"--lookup"}, description = "Lookup path. Path to where the files nameLookup.txt, nameTypeLookup.txt, wikiLookup.txt, words2labelsLookup.txt are.")//, required = true)
    private String lookup = "/home/matthias/Schreibtisch/Hiwi/Tatiana/lookup/";
    
    
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
        
        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);
        PrintWriter o = new PrintWriter(cli.outPath+"/parserOut.txt"); //will contain graphs, potentially relabeled
        PrintWriter l = null;
        PrintWriter indices = null;
        
        Relabel relabeler = null;
        
        if (!cli.relabel){
            l = new PrintWriter(cli.outPath+"/labels.txt");
            indices = new PrintWriter(cli.outPath+"/indices.txt");
        } else {
             relabeler = new Relabel(cli.wordnet, null, cli.lookup, 10, 0,false);
        }

        int index = 0;
        for (AmConllSentence s : sents){
            
            if (!cli.relabel){
                indices.println(index);
            }
            
            index++;
            //prepare raw output without edges
            try {
                AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
                SGraph evaluatedGraph = amdep.evaluateWithoutRelex(true);
                
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
                    
                    //we have to get fresh node names to make sure that relabeling works smoothly
                    evaluatedGraph = evaluatedGraph.withFreshNodenames();
                    
                    //now let's reconstruct the alignments that are still explicit in the labels
                    Map<String, Integer> nodeNameToPosition = new HashMap<>();
                    for (GraphNode n : evaluatedGraph.getGraph().vertexSet()){
                        if (n.getLabel().matches(Relabel.LEXMARKER+"[0-9]+")){
                            int i = Integer.parseInt(n.getLabel().substring(Relabel.LEXMARKER.length()));
                            nodeNameToPosition.put(n.getName(), i);
                        }
                    }
                    
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
                    }), s.words(), s.getFields(entry -> {
                        String relex = entry.getReLexLabel();
                        if (relex.equals("_")) return "NULL";
                                else return relex;
                    }));
                    
                    if (cli.keepAligned){
                        //now add alignment again, format: POSITION|NODE LABEL where POSITION is 0-based.
                        for (String nodeName : nodeNameToPosition.keySet()){
                            GraphNode node = evaluatedGraph.getNode(nodeName);
                            if (node == null){
                                System.err.println("Warning: a nodename for which we have an alignment cannot be found in the relabeled graph");
                            }
                            node.setLabel(nodeNameToPosition.get(nodeName) + AL_LABEL_SEP + node.getLabel());
                        }

                        //iterate over all nodes in the graph for which we didn't get an alignment in the previous step
//                        for (GraphNode node : evaluatedGraph.getGraph().vertexSet()) {
//                            if (!nodeNameToPosition.containsKey(node.getName())) {
                                  // make sure there is exactly one incident edge.
//                                Set<GraphEdge> edges = evaluatedGraph.getGraph().edgesOf(node);
//                                if (edges.size() == 1) {
                                      // get node label of the unique neighbour
//                                    String otherLabel = BlobUtils.otherNode(node, edges.iterator().next()).getLabel();
                                      //make sure that label has an alignment marker
//                                    if (otherLabel.contains(AL_LABEL_SEP)) {
                                          // copy that alignment marker over.
//                                        String alignment = otherLabel.substring(0, otherLabel.indexOf(AL_LABEL_SEP));
//                                        node.setLabel(alignment+AL_LABEL_SEP+node.getLabel());
//                                    } else {
//                                        System.err.println("Warning: cannot find unique alignment for a node with no inherent alignment.");
//                                    }
//                                } else {
//                                    System.err.println("Warning: cannot find unique alignment for a node with no inherent alignment.");
//                                }
//                            }
//                        }

                    }
                    
                }
                
                o.println(evaluatedGraph.toIsiAmrString());
                
            } catch (Exception ex){
                System.err.println("In line "+s.getLineNr());
                System.err.println("Ignoring exception:");
                ex.printStackTrace();
                System.err.println("Writing dummy graph instead");
                
                o.println("(d / dummy)");
                
                if (!cli.relabel){
                    l.println();
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
