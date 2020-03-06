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
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 *  Find and count conjunctions in DM graphs (e.g. 'Trees and flowers grow', 'He evades and ducks', ...)
 * @author jonas, pia
 */
public class FindDMConjunction {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindDMConjunction cli = new FindDMConjunction();
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
        int totalConj = 0;
        int conjgraphs = 0;

        Counter<String> counter = new Counter<>();

        while ((sdpGraph = gr.readGraph()) != null){
            totalGraphs += 1;
            totalEdges += sdpGraph.getNEdges();
            boolean hasconj = false;
            for (Edge edge : sdpGraph.getEdges()) {
                if (edge.label.equals("conj") || edge.label.endsWith("_c")) {
                    totalConj++;
                    counter.add(edge.label);
                    hasconj = true;
                }
            }
            conjgraphs += hasconj ? 1 : 0;
        }

        System.err.println(String.format("total graphs:               %10d", totalGraphs));
        System.err.println(String.format("total edges:                %10d", totalEdges));
        System.err.println(String.format("total conjunction edges:    %10d || %8.3f percent of all edges", totalConj, 100*totalConj/(float)totalEdges));
        System.err.println(String.format("graphs with >=1 conjuntion: %10d || %8.3f percent of all graphs", conjgraphs, 100*conjgraphs / (float)totalGraphs));

        //counter.printAllSorted();
        List<Object2IntMap.Entry<String>> list = counter.getAllSorted();
        System.err.println(String.format("%20s : %10s : %s", "Edge label", "Count", "Percentage of all counts"));
        for (Object2IntMap.Entry<String> o : list) {
            System.err.println(String.format("%20s : %10d : %8.3f", o.getKey(), o.getIntValue(), 100* o.getIntValue() / (float)totalConj));
        }
    }


}
    

    

