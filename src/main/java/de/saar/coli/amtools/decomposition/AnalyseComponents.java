/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.util.Counter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 *
 * @author JG
 */
public class AnalyseComponents {
    
    
    public static void main(String[] args) throws IOException {
        
        String corpusPath = "/Users/jonas/Documents/data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";
        AMRBlobUtils blobUtils = new DMBlobUtils();
        
        
        GraphReader2015 gr = new GraphReader2015(corpusPath);
        Graph sdpGraph;
        
        int max = 100000;
        int i = 0;
        
        Counter<Long> treeCountFrequencies = new Counter<>();
        long maxTreeCount = 0;
        float maxLogTreeCount = 0;
        
        while ((sdpGraph = gr.readGraph()) != null && i++ < max){
            
            try {
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);

                SGraph graph = inst.getGraph();
//                GraphNode root = graph.getNode(graph.getNodeForSource("root"));
//
//                DAGComponent dagComp = new DAGComponent(graph, root, blobUtils);
//
//                Collection<GraphNode> removed = dagComp.getAllAsGraphNodes();
//                Collection<ConnectedComponent> connComps = ConnectedComponent.getAllConnectedComponents(graph, removed);
//
//    //            System.err.println(graph.toIsiAmrStringWithSources());
//                int totalNodeCount = graph.nodeCount();
//                int mainComponentCount = dagComp.getAllNodes().size();
//                int remaining = totalNodeCount - mainComponentCount;
//    //            System.err.println("Total nodes: "+totalNodeCount);
//    //            System.err.println("DAG nodes: "+mainComponentCount);
//    //            System.err.println("Remaining: "+remaining);
//    //            System.err.println(dagComp.getAllNodes());
//    //            System.err.println("MOD component sizes: "+connComps.stream().map(ConnectedComponent::size).collect(Collectors.toList()));
//    //            System.err.println("Max MOD component size: "+connComps.stream().map(ConnectedComponent::size).collect(Collectors.maxBy(Comparators.naturalOrder())).get());
//
//                List<Integer> modRootChoices = new ArrayList<>();
//                for (ConnectedComponent connComp : connComps) {
//                    Set<GraphNode> possibleRoots = new HashSet<>();
//                    for (GraphEdge e : dagComp.getEdgesTo(connComp.getAllNodes())) {
//                        if (connComp.getAllNodes().contains(e.getTarget())) {
//                            possibleRoots.add(e.getTarget());
//                        } else {
//                            possibleRoots.add(e.getSource());
//                        }
//                    }
//                    Integer nrChoices = possibleRoots.size();
//                    if (nrChoices != 1) {
//                        System.err.println();
//                        System.err.println("More just one choice: "+nrChoices);
//                        System.err.println("i: "+i);
//                        System.err.println(inst.getSentence().stream().collect(Collectors.joining(" ")));
//                        System.err.println(graph);
//                        System.err.println(dagComp.getAllNodes());
//                        System.err.println(connComp.getAllNodes());
//
//
//                    }
//                    modRootChoices.add(nrChoices);
//                }

                ConcreteTreeAutomaton auto = new ComponentAutomaton(graph, blobUtils).asConcreteTreeAutomatonTopDown();
                long treeCount = auto.countTrees();
                if (treeCount == 4096) {
                    System.err.println(graph);
                }
                treeCountFrequencies.add(treeCount);
                maxTreeCount = Math.max(maxTreeCount, treeCount);
                maxLogTreeCount = Math.max(maxLogTreeCount, (float)(Math.log(treeCount)/Math.log(2)));
            } catch (java.lang.Exception ex) {
                System.err.println(ex);
            }
            
//            System.err.println("MOD root choices: "+modRootChoices);
        }
        
        
        System.err.println("max tree count: "+maxTreeCount);
        System.err.println("max log2 tree count: "+maxLogTreeCount);
        treeCountFrequencies.printAllSorted();
        
    }
    
    
    
}
