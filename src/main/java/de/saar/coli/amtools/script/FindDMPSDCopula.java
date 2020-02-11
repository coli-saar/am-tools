/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
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
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 *  Create PSD training data.
 * @author jonas
 */
public class FindDMPSDCopula {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the primary input corpus")//, required = true)
    private String corpusPathDM = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpus2", "-c2"}, description = "Path to the secondary input corpus")//, required = true)
    private String corpusPathPSD = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   

    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindDMPSDCopula cli = new FindDMPSDCopula();
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
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph psdGraph;
        int psdBe = 0;
        int withPSDArgEdges = 0;
        int alsoWithDMEdge = 0;

        Counter<Pair<String, String>> beFormAndDMLemmaCounter = new Counter<>();
        Counter<String> psdPatPOSCounter = new Counter<>();
        Counter<String> psdPatPOSCounterWithDMEdge = new Counter<>();
        Counter<String> dmEdgeCounter = new Counter<>();

        int sentCount = 0;
        int linesPrinted = 0;
        while ((dmGraph = grDM.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null){
            sentCount++;
            List<Node> nodeListDM = dmGraph.getNodes();
            List<Node> nodeListPSD = psdGraph.getNodes();
            if (nodeListDM.size() != nodeListPSD.size()) {
                System.err.println("WARNING: sentences of different length");
            }
            for (int i = 0; i< nodeListDM.size() && i<nodeListPSD.size(); i++) {
                Node nodeDM = nodeListDM.get(i);
                Node nodePSD = nodeListPSD.get(i);
                //check PSD lemma
                if (nodePSD.lemma.equals("be")) {
                    psdBe++;
                    // out of curiosity: get word forms and DM lemmas
                    beFormAndDMLemmaCounter.add(new Pair(nodePSD.form, nodeDM.lemma));

                    //check PSD outgoing edges
                    Set<String> outgoingLabels = getOutgoingEdgeLabels(nodePSD);
                    if (outgoingLabels.size() == 2 &&
                            outgoingLabels.contains("ACT-arg") && outgoingLabels.contains("PAT-arg")) {
                        withPSDArgEdges++;
                        Edge psdActEdge = null;
                        Edge psdPatEdge = null;
                        for (Edge edge : nodePSD.outgoingEdges) {
                            if (edge.label.equals("ACT-arg")) {
                                psdActEdge = edge;
                            } else {
                                psdPatEdge = edge;
                            }
                        }
                        assert psdActEdge != null;
                        assert psdPatEdge != null;


                        //check DM edge across
                        boolean hasDMEdge = false;
                        String dmEdgeLabel = "";
                        Node dmPatNode = dmGraph.getNode(psdPatEdge.target);
                        for (Edge dmEdge : dmPatNode.outgoingEdges) {
                            if (dmEdge.target == psdActEdge.target) {  //(dmEdge.label.equals("ARG1") && dmEdge.target == psdPatEdge.target) {
                                hasDMEdge = true;
                                dmEdgeLabel = dmEdge.label;
                            }
                        }

                        if (hasDMEdge && nodeDM.outgoingEdges.isEmpty() && nodeDM.incomingEdges.isEmpty()) {
                            alsoWithDMEdge++;
                            dmEdgeCounter.add(dmEdgeLabel);
                            psdPatPOSCounterWithDMEdge.add(nodeListPSD.get(psdPatEdge.target).pos);
                        }
                        //check POS tag
                        psdPatPOSCounter.add(nodeListPSD.get(psdPatEdge.target).pos);

                        String posTag = nodeListPSD.get(psdPatEdge.target).pos;

                        // print relevant span of sentence
                        //if (hasDMEdge && dmEdgeLabel.startsWith("ARG") && posTag.equals("VBN")) {
                        if (!hasDMEdge && posTag.startsWith("NN")) {
                            if (linesPrinted < 20) {
                                int spanStart = Math.min(i, Math.min(psdActEdge.target, psdPatEdge.target));//inclusive
                                int spanEnd = Math.max(i, Math.max(psdActEdge.target, psdPatEdge.target))+1;//exclusive
                                StringJoiner sj = new StringJoiner(" ");
                                for (int k = spanStart; k < spanEnd; k++) {
                                    sj.add(nodeListPSD.get(k).form);
                                }
                                System.err.println(sj.toString() + " || 1-based indices: "+(psdActEdge.target-spanStart+1)+" "+(i-spanStart+1)+" "+(psdPatEdge.target-spanStart+1));
                                linesPrinted++;
                            }
                        }

                    }
                }
            }
        }

        System.err.println();
        System.err.println("sentences analyzed: "+sentCount);
        System.err.println("PSD 'be' lemmas:    "+psdBe);
        System.err.println("Having exactly one ACT-arg and one PAT-arg as outgoing PSD edges: "+withPSDArgEdges);
        System.err.println("Having a DM edge between these targets and no DM edge at 'be':    "+alsoWithDMEdge);

        //beFormAndDMLemmaCounter.printAllSorted();
        System.err.println("\n--> PSD: PoS tag of PAT-arg argument of copula:");
        //psdPatPOSCounter.printAllSorted();
        printCounter(psdPatPOSCounter);
        System.err.println("\n--> PSD: PoS tag of PAT-arg argument of copula where DM has edge connecting pat and act:");
        //psdPatPOSCounterWithDMEdge.printAllSorted();
        printCounter(psdPatPOSCounterWithDMEdge);
        System.err.println("\n--> DM: edge label of edge connecting PSD's PATtarget and ACTtarget node:");
        //dmEdgeCounter.printAllSorted();
        printCounter(dmEdgeCounter);
    }

    /**
     * Prints keys of counter in descending frequency order, additionally prints relative frequency
     */
    private static void printCounter(Counter<String> counter) throws IllegalArgumentException {
        List<Object2IntMap.Entry<String>> list = counter.getAllSorted();
        int total = counter.sum();
        if (total == 0) {
            throw new IllegalArgumentException("Counter sum is 0");
        }
        System.err.println(String.format("%20s : %10s : %s", "Key", "Count", "Percentage of all counts"));
        for (Object2IntMap.Entry<String> o : list) {
            System.err.println(String.format("%20s : %10d : %8.3f", o.getKey(), o.getIntValue(), 100* o.getIntValue() / (float)total));
        }
        System.err.println("  -------------------------------------------");
        System.err.println(String.format("%20s : %10d : %8.3f", "total", total, (float) 100));
    }

    private static Set<String> getOutgoingEdgeLabels(Node node) {
        List<Edge> edges = node.getOutgoingEdges();
        return edges.stream().map(edge -> edge.label).collect(Collectors.toSet());
    }

}
    

    

