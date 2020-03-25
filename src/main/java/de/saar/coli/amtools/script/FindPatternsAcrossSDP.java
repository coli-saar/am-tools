package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FindPatternsAcrossSDP {


    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPathDM = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.dm.sdp";// data/sdp/
    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")//, required = true)
    private String corpusPathPAS = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.pas.sdp";// data/corpora/semdep/
    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")//, required = true)
    private String corpusPathPSD = "../../data/corpora/semdep/sdp2014_2015/data/2015/en.psd.sdp";// data/corpora/semdep/


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

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
    public static void main(String[] args) throws FileNotFoundException, IOException {
        //just getting command line args
        FindPatternsAcrossSDP cli = new FindPatternsAcrossSDP();
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

        Map<String, Counter<String>> patterns2lemmaCounter = new HashMap<>();
        Map<String, Counter<String>> patterns2posCounter = new HashMap<>();

        for (int i = 0; i<=MAX_PATTERN; i++) {
            for (int j = 0; j<=MAX_PATTERN; j++) {
                for (int k = 0; k<=MAX_PATTERN; k++) {
                    patterns2lemmaCounter.put(patterns2string(i, j, k), new Counter<>());
                    patterns2posCounter.put(patterns2string(i, j, k), new Counter<>());
                }
            }
        }

        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null) {
            for (int i = 0; i < psdGraph.getNNodes(); i++) {
                String patternCombination = getPatternCombination(dmGraph, pasGraph, psdGraph, i);
                patterns2lemmaCounter.get(patternCombination).add(psdGraph.getNode(i).lemma);
                patterns2posCounter.get(patternCombination).add(psdGraph.getNode(i).pos);
            }
        }

        List<String> sortedPatterns = patterns2lemmaCounter.keySet().stream()
                .sorted((o1, o2) -> patterns2lemmaCounter.get(o2).sum()-patterns2lemmaCounter.get(o1).sum())
                .collect(Collectors.toList());

        for (int i = 0; i<50; i++) {
            String pattern = sortedPatterns.get(i);
            Counter<String> lemmaC = patterns2lemmaCounter.get(pattern);
            Counter<String> posC = patterns2posCounter.get(pattern);
            System.err.println(pattern+": "+lemmaC.sum());

            System.err.println("\nPOS");
            int k = 0;
            for (Object2IntMap.Entry<String> entry : posC.getAllSorted()) {
                System.err.println(consistentIndent(entry.getKey())+entry.getIntValue());
                k++;
                if (k>=3) {
                    break;
                }
            }
            System.err.println("\nLEMMA");
            k = 0;
            for (Object2IntMap.Entry<String> entry : lemmaC.getAllSorted()) {
                System.err.println(consistentIndent(entry.getKey())+entry.getIntValue());
                k++;
                if (k>=5) {
                    break;
                }
            }
            System.err.println("\n");
        }

    }


    static String consistentIndent(String s) {
        return (s+"                    ").substring(0,20);
    }




    static String patterns2string(int i, int j, int k) {
        return String.valueOf(i)+String.valueOf(j)+String.valueOf(k);
    }


    public static final int MAX_PATTERN = 8;

    /**
     * 0: ignored in graph
     * 1: 2 blob edges, is possible root (has inc or is root), no connection between children
     * 2: 2 blob edges, is not root (no inc, not root), no connection between children
     * 3: 2 blob edges, is possible root (has inc or is root), connection between children
     * 4: 2 blob edges, is not root (no inc, not root), connection between children
     * 5: 1 blob edge, is possible root
     * 6: 1 blob edge, is not root
     * 7: no blob edge, but part of graph
     * 8: other
     * @param graph
     * @param i is 0 based of SDP graphs, 1 based for AM dependency trees
     * @return
     */
    public static int getPattern(Graph graph, int i, AMRBlobUtils blobUtils) {
        Node node = graph.getNode(i);
        if (!hasOutg(node, blobUtils)) {
            if (!hasInc(node, blobUtils)) {
                return 0;
            } else {
                return 7;
            }
        } else if (getNBlobEdges(node, blobUtils) == 2) {
            if (!childrenAreConnected(graph, node, blobUtils)) {
                if (isPossibleRoot(node, blobUtils)) {
                    return 1;
                } else {
                    return 2;
                }
            } else {
                if (isPossibleRoot(node, blobUtils)) {
                    return 3;
                } else {
                    return 4;
                }
            }
        } else if (getNBlobEdges(node, blobUtils) == 1) {
            if (isPossibleRoot(node, blobUtils)) {
                return 5;
            } else {
                return 6;
            }
        } else {
            return 8;
        }
    }

    /**
     *
     * @param dmGraph
     * @param pasGraph
     * @param psdGraph
     * @param i  is 0 based of SDP graphs, 1 based for AM dependency trees
     * @return
     */
    public static String getPatternCombination(Graph dmGraph, Graph pasGraph, Graph psdGraph, int i) {
        int patternDM = getPattern(dmGraph, i, dmBlobUtils);
        int patternPAS = getPattern(pasGraph, i, pasBlobUtils);
        int patternPSD = getPattern(psdGraph, i, psdBlobUtils);
        return patterns2string(patternDM, patternPAS, patternPSD);
    }


    private static int getNBlobEdges(Node node, AMRBlobUtils blobUtils) {
        return getBlobEdges(node, blobUtils).size();
    }

    public static List<Edge> getBlobEdges(Node node, AMRBlobUtils blobUtils) {
        //need to work around the interface here a bit, unfortunately
        GraphNode dummy = new GraphNode("dummy", "dummy");
        List<Edge> ret = new ArrayList<>();
        for (Edge outgoing : node.getOutgoingEdges()) {
            if (blobUtils.isOutbound(new GraphEdge(dummy, dummy, outgoing.label))) {
                ret.add(outgoing);
            }
        }
        for (Edge incoming : node.getIncomingEdges()) {
            if (blobUtils.isInbound(new GraphEdge(dummy, dummy, incoming.label))) {
                ret.add(incoming);
            }
        }

        return ret;
    }

    public static List<Edge> getNonblobEdges(Node node, AMRBlobUtils blobUtils) {
        //need to work around the interface here a bit, unfortunately
        GraphNode dummy = new GraphNode("dummy", "dummy");
        List<Edge> ret = new ArrayList<>();
        for (Edge outgoing : node.getOutgoingEdges()) {
            if (!blobUtils.isOutbound(new GraphEdge(dummy, dummy, outgoing.label))) {
                ret.add(outgoing);
            }
        }
        for (Edge incoming : node.getIncomingEdges()) {
            if (!blobUtils.isInbound(new GraphEdge(dummy, dummy, incoming.label))) {
                ret.add(incoming);
            }
        }

        return ret;
    }

    private static int getNNonblobEdges(Node node, AMRBlobUtils blobUtils) {
        return getNonblobEdges(node, blobUtils).size();
    }


    private static boolean hasInc(Node node, AMRBlobUtils blobUtils) {
        return getNNonblobEdges(node, blobUtils) > 0;
    }


    private static boolean hasOutg(Node node, AMRBlobUtils blobUtils) {
        return getNBlobEdges(node, blobUtils) > 0;
    }

    /**
     * assumes node has exactly 2 outgoing edges
     * @param graph
     * @param node
     * @return
     */
    private static boolean childrenAreConnected(Graph graph, Node node, AMRBlobUtils blobUtils) {
        List<Edge> outgoingEdges = getBlobEdges(node, blobUtils);
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

    private static boolean isPossibleRoot(Node node, AMRBlobUtils blobUtils) {
        return getNNonblobEdges(node, blobUtils) > 0;
    }

}
