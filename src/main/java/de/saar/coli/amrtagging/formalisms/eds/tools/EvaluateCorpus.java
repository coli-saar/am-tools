/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import de.saar.coli.amrtagging.formalisms.sdp.dm.tools.*;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;

import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;

import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.irtg.codec.GraphVizDotOutputCodec;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.CycleDetector;

/**
 * Converts an AM Dependency corpus (amconll) into an EDS corpus in EDM format used by Buys and Blunsom for evaluation and to AMR format (without character spans).
 * @author matthias
 */
public class EvaluateCorpus {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/tmp/eds/sent.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path and name for output files")//, required = true)
    private String outPath = "/tmp/eds/out";
    
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        EvaluateCorpus cli = new EvaluateCorpus();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("EDS evaluator");

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

        PrintWriter amr = new PrintWriter(cli.outPath+".amr.txt");
        PrintWriter edm = new PrintWriter(cli.outPath+".edm");

        for (ConllSentence s : sents){
            //prepare raw output without edges
            try {
                
                SGraph evaluated = EDSConverter.ensureRootIsWritable(EDSConverter.evaluateDependencyTree(s));
                try {
                    SGraph str = EDSUtils.stripLnks(evaluated);
                    ConnectivityInspector<GraphNode,GraphEdge> con = new ConnectivityInspector(str.getGraph());
                    if (con.isGraphConnected()){
                        amr.println(str.toIsiAmrString());
                        amr.println(); //extra new line
                    } else { //disconnected graph: take the largest subgraph
                        List<Set<GraphNode>> connectedSets = con.connectedSets();
                        GraphNode rootNode = str.getNode(evaluated.getNodeForSource("root"));
                        Set<GraphNode> rootSet = connectedSets.stream().filter( set -> set.contains(rootNode)).findFirst().get();
                        SGraph subg = new SGraph();
                        for (GraphNode n : str.getGraph().vertexSet()){
                            if (rootSet.contains(n)){
                                subg.addNode(n.getName(), n.getLabel());
                            }
                        }
                      subg.addSource("root",rootNode.getName()); //give root source
                      for (GraphEdge e : str.getGraph().edgeSet()){
                          if (rootSet.contains(e.getSource()) && rootSet.contains(e.getTarget())){
                              subg.addEdge(e.getSource(), e.getTarget(), e.getLabel());
                          }
                      }
                    amr.println(subg.toIsiAmrString());
                    amr.println(); //extra new line
                    }

                } catch (Exception ex) {
                    System.err.println("In line "+s.getLineNr());
                    System.err.println("Ignoring exception:");
                    ex.printStackTrace();
                    System.err.println("Writing dummy AMR graph");
                    amr.println("(d / dummy)");
                    amr.println(); //extra new line
                }
                
                try {
                    edm.println(EDSConverter.toEDM(evaluated));
                } catch (Exception ex) {
                    System.err.println("In line "+s.getLineNr());
                    System.err.println("Ignoring exception:");
                    ex.printStackTrace();
                    System.err.println("Writing empty graph instead");
                    edm.println();
                }
                
                
            } catch (Exception ex){
                System.err.println("In line "+s.getLineNr());
                //AMDependencyTree amdep = AMDependencyTree.fromSentence(s);
                //SGraph evaluatedGraph = amdep.evaluate(true);
                //SGraphDrawer.draw(evaluatedGraph, "");
                System.err.println("Ignoring exception:");
                ex.printStackTrace();
                System.err.println("Writing empty graph instead / dummy AMR graph");
                edm.println();
                amr.println("(d / dummy)");
                amr.println(); //extra new line
            }
            
        }
        amr.close();
        edm.close();

 
        
    }
    

    
}
