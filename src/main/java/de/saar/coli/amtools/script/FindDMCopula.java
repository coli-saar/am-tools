/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Find and count copula in DM graphs (e.g. 'Compromises are possible', 'Ada said that Bert has been lazy', ...)
 * @author jonas, pia
 */
public class FindDMCopula {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindDMCopula cli = new FindDMCopula();
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
        
        //setup
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);
        Graph sdpGraph;
        int totalGraphs = 0;
        int totalEdges = 0;
        int totalNodes = 0;
        int totalBe = 0;  // to-be nodes
        int copula = 0;

        while ((sdpGraph = gr.readGraph()) != null){
            totalGraphs += 1;
            totalEdges += sdpGraph.getNEdges();
            totalNodes += sdpGraph.getNNodes();
            for (Node node : sdpGraph.getNodes()) {
                if (node.lemma.equals("be")) {
                    totalBe++;

                    if (node.outgoingEdges.isEmpty() && node.incomingEdges.isEmpty()) {
                        // TODO: is copula if skipped in graph :/ Other reasons for skipping? 'Advertisers are showing interest'
                        copula++;
                    }

                }
            }
        }

        System.err.println(String.format("total graphs:               %10d", totalGraphs));
        System.err.println(String.format("total edges:                %10d", totalEdges));
        System.err.println(String.format("total nodes:                %10d", totalNodes));
        System.err.println(String.format("total occurrences of to be: %10d || %8.3f percent of nodes", totalBe, 100* totalBe /(float)totalNodes));
        System.err.println(String.format("detected as copula:         %10d || %8.3f percent of to-be-occ", copula, 100*copula / (float)totalBe));
        
    }


}
    

    

