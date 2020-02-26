package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FindModalsAcrossSDP {

    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPathDM = "../../data/sdp/sdp2014_2015/data/2015/en.dm.sdp";// data/corpora/semdep/
    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")//, required = true)
    private String corpusPathPAS = "../../data/sdp/sdp2014_2015/data/2015/en.pas.sdp";// data/corpora/semdep/
    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")//, required = true)
    private String corpusPathPSD = "../../data/sdp/sdp2014_2015/data/2015/en.psd.sdp";// data/corpora/semdep/


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static List<String> auxLemmas = Arrays.asList(new String[]{"be", "can", "could", "dare", "do", "have", "may", "might", "must", "need",
        "ought", "shall", "should", "will", "would"});

    /**
     * prints CSV tables for all auxiliary verbs according to wikipedia. Information includes total counts, and counts of
     * edge patterns.
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws ParserException
     * @throws AlignedAMDependencyTree.ConllParserException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        //just getting command line args
        FindModalsAcrossSDP cli = new FindModalsAcrossSDP();
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
        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph pasGraph;
        Graph psdGraph;
        Counter<String> lemmaCounter = new Counter<>();
        Map<String, Counter<Integer>> lemma2patternCounterDM = new HashMap<>();
        Map<String, Counter<Integer>> lemma2patternCounterPAS = new HashMap<>();
        Map<String, Counter<Integer>> lemma2patternCounterPSD = new HashMap<>();

        for (String lemma : auxLemmas) {
            lemma2patternCounterDM.put(lemma, new Counter<>());
            lemma2patternCounterPAS.put(lemma, new Counter<>());
            lemma2patternCounterPSD.put(lemma, new Counter<>());
        }


        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null){
            for (int i = 0; i< psdGraph.getNNodes(); i++) {
                String psdLemma = psdGraph.getNode(i).lemma;
                if (auxLemmas.contains(psdLemma)) {
                    lemmaCounter.add(psdLemma);

                    lemma2patternCounterDM.get(psdLemma).add(getPattern(dmGraph, i));
                    lemma2patternCounterPAS.get(psdLemma).add(getPattern(pasGraph, i));
                    lemma2patternCounterPSD.get(psdLemma).add(getPattern(psdGraph, i));

                }
            }
        }

        lemmaCounter.printAllSorted();


        for (String lemma : lemmaCounter.getAllSorted().stream().map(entry -> entry.getKey()).collect(Collectors.toList())) {
            System.err.println("\n\n");
            System.err.println(lemma);
            System.err.println("DM:");
            lemma2patternCounterDM.get(lemma).printAllSorted();
            System.err.println("PAS:");
            lemma2patternCounterPAS.get(lemma).printAllSorted();
            System.err.println("PSD:");
            lemma2patternCounterPSD.get(lemma).printAllSorted();
        }

    }

    /**
     * 0: ignored in graph
     * 1: 2 outgoing edges, is possible root (has inc or is root), no connection between children
     * 2: 2 outgoing edges, is not root (no inc, not root), no connection between children
     * 3: 2 outgoing edges, is possible root (has inc or is root), connection between children
     * 4: 2 outgoing edges, is not root (no inc, not root), connection between children
     * 5: 1 outgoing edge, is possible root
     * 6: 1 outgoing edge, is not root
     * 7: other
     * @param graph
     * @param i
     * @return
     */
    private static int getPattern(Graph graph, int i) {
        Node node = graph.getNode(i);
        if (!hasInc(node) && !hasOutg(node)) {
            return 0;
        } else if (node.getNOutgoingEdges() == 2) {
            if (!childrenAreConnected(graph, node)) {
                if (isPossibleRoot(node)) {
                    return 1;
                } else {
                    return 2;
                }
            } else {
                if (isPossibleRoot(node)) {
                    return 3;
                } else {
                    return 4;
                }
            }
        } else if (node.getNOutgoingEdges() == 1) {
            if (isPossibleRoot(node)) {
                return 5;
            } else {
                return 6;
            }
        } else {
            return 7;
        }
    }

    private static boolean hasInc(Node node) {
        return node.getNIncomingEdges() > 0;
    }


    private static boolean hasOutg(Node node) {
        return node.getNOutgoingEdges() > 0;
    }

    /**
     * assumes node has exactly 2 outgoing edges
     * @param graph
     * @param node
     * @return
     */
    private static boolean childrenAreConnected(Graph graph, Node node) {
        List<Edge> outgoingEdges = node.getOutgoingEdges();
        Node child0 = graph.getNode(outgoingEdges.get(0).target);
        Node child1 = graph.getNode(outgoingEdges.get(1).target);
        for (Edge e : child0.getOutgoingEdges()) {
            if (e.target == child1.id) {
                return true;
            }
        }
        for (Edge e : child1.getOutgoingEdges()) {
            if (e.target == child0.id) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPossibleRoot(Node node) {
        return node.getNIncomingEdges() > 0 || node.isTop;
    }

}
