/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.Sets;
import de.saar.basic.Pair;
import de.up.ling.irtg.util.Counter;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class EdgeDiffPOSAnalysis {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the primary input corpus")//, required = true)
    private String corpusPath = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpus2", "-c2"}, description = "Path to the secondary input corpus")//, required = true)
    private String corpusPath2 = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws IOException {
        //just getting command line args
        EdgeDiffPOSAnalysis cli = new EdgeDiffPOSAnalysis();
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
        GraphReader2015 gr2 = new GraphReader2015(cli.corpusPath2);
        Graph sdpGraph;
        Graph sdpGraph2;

        int totalEdges1 = 0;
        Counter<Pair<String, String>> posPairCounter = new Counter<>();
        Counter<String> sourceNodePOSCounter = new Counter<>();
        Counter<String> targetNodePOSCounter = new Counter<>();

        while ((sdpGraph = gr.readGraph()) != null && (sdpGraph2 = gr2.readGraph()) != null){
            totalEdges1 += sdpGraph.getEdges().size();
            Set<Pair<Integer, Integer>> edges1 = sdpGraph.getEdges().stream().map(e -> encodeEdge(e)).collect(Collectors.toSet());
            Set<Pair<Integer, Integer>> edges2 = sdpGraph2.getEdges().stream().map(e -> encodeEdge(e)).collect(Collectors.toSet());
            for (Pair<Integer, Integer> edge : edges1) {
                //use line below if you want to get edges that are in corpus 1 but not in corpus 2 (undirected check).
                if (!edges2.contains(edge) && !edges2.contains(new Pair(edge.right, edge.left))) {
                //use line below if you want to get flipped edges
                //if (edges2.contains(new Pair(edge.right, edge.left))) {
                    Node sourceNode = sdpGraph.getNode(edge.left);//POS is the same in both graphs, sdpGraph2 would give same result for POS tag.
                    Node targetNode = sdpGraph.getNode(edge.right);//POS is the same in both graphs, sdpGraph2 would give same result for POS tag.
                    posPairCounter.add(new Pair(sourceNode.pos, targetNode.pos));
                    sourceNodePOSCounter.add(sourceNode.pos);
                    targetNodePOSCounter.add(targetNode.pos);
                }
            }
        }

        System.err.println("total edges in graphbank 1: "+totalEdges1);
        System.err.println("total edge differences of this kind found: "+sourceNodePOSCounter.sum());
        sourceNodePOSCounter.printAllSorted();
        System.err.println();
        targetNodePOSCounter.printAllSorted();
        System.err.println();
        posPairCounter.printAllSorted();
    }


    private static Pair<Integer, Integer> encodeEdge(Edge e) {
        return new Pair(e.source, e.target);
    }

}
    

    

