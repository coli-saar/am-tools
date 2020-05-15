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
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.util.Counter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 *
 * @author JG
 */
public class AnalyseComponents {
    
    
    public static void main(String[] args) throws IOException {
        
        String corpusPath = "C://Users/Jonas/Documents/Work/data/sdp/2015/psd/train.sdp";
            //"/Users/jonas/Documents/data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";
        PSDBlobUtils blobUtils = new PSDBlobUtils();
        
        
        GraphReader2015 gr = new GraphReader2015(corpusPath);
        Graph sdpGraph;
        
        int treeCountReportThreshold = 100;
        
        int max = 100000;
        int i = 0;
        
        List<MRInstance> cyclicGraphs = new ArrayList<>();
        List<MRInstance> failedModifieeGraphs = new ArrayList<>();
        List<MRInstance> manyChoicesGraphs = new ArrayList<>();
        
        Counter<Long> treeCountFrequencies = new Counter<>();
        long maxTreeCount = 0;
        float maxLogTreeCount = 0;
        
        while ((sdpGraph = gr.readGraph()) != null && i++ < max){
            
            if (i%1000 == 0) {
                System.err.println(i);
            }
            
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            inst = new MRInstance(inst.getSentence(), ConjHandler.handleConj(inst.getGraph(), blobUtils, false), inst.getAlignments());
            SGraph graph = inst.getGraph();
            try {

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
                if (treeCount > treeCountReportThreshold) {
                    manyChoicesGraphs.add(inst);
                }
                treeCountFrequencies.add(treeCount);
                maxTreeCount = Math.max(maxTreeCount, treeCount);
                maxLogTreeCount = Math.max(maxLogTreeCount, (float)(Math.log(treeCount)/Math.log(2)));
            } catch (DAGComponent.CyclicGraphException ex) {
                cyclicGraphs.add(inst);
            } catch (DAGComponent.NoEdgeToRequiredModifieeException ex) {
                failedModifieeGraphs.add(inst);
            } catch (java.lang.Exception ex) {
                System.err.println(ex);
            }
            
//            System.err.println("MOD root choices: "+modRootChoices);
        }
        
        
        System.err.println("Cyclic graphs: "+cyclicGraphs.size());
        System.err.println("missing Modifiee edge graphs: "+failedModifieeGraphs.size());
        System.err.println("Graphs with more than "+treeCountReportThreshold+" component trees: "+manyChoicesGraphs.size());
        System.err.println("max tree count: "+maxTreeCount);
        System.err.println("max log2 tree count: "+maxLogTreeCount);
        treeCountFrequencies.printAllSorted();
//
//        System.err.println("\n\n");
//        System.err.println("Cyclic graphs: "+cyclicGraphs.size());
//        for (MRInstance inst : cyclicGraphs) {
//            System.err.println(inst.getSentence().stream().collect(Collectors.joining(" ")));
//            System.err.println(inst.getGraph().toIsiAmrStringWithSources());
//            System.err.println();
//        }
//
//        System.err.println("\n\n");
//        System.err.println("missing Modifiee edge graphs: "+failedModifieeGraphs.size());
//        for (MRInstance inst : failedModifieeGraphs) {
//            System.err.println(inst.getSentence().stream().collect(Collectors.joining(" ")));
//            System.err.println(inst.getGraph().toIsiAmrStringWithSources());
//            System.err.println();
//        }
//
//        System.err.println("\n\n");
//        System.err.println("Graphs with more than "+treeCountReportThreshold+" component trees: "+manyChoicesGraphs.size());
//        for (MRInstance inst : manyChoicesGraphs) {
//            System.err.println(inst.getSentence().stream().collect(Collectors.joining(" ")));
//            System.err.println(inst.getGraph().toIsiAmrStringWithSources());
//            System.err.println();
//        }
        
    }
    
    
    
}
