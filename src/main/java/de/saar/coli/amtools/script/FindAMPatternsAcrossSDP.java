package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static de.saar.coli.amtools.script.FindPatternsAcrossSDP.*;

public class FindAMPatternsAcrossSDP {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.pas.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "../../experimentData/uniformify2020/original_decomps/dm/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "../../experimentData/uniformify2020/original_decomps/pas/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "../../experimentData/uniformify2020/original_decomps/psd/gold-dev/gold-dev.amconll";


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
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        //just getting command line args
        FindAMPatternsAcrossSDP cli = new FindAMPatternsAcrossSDP();
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
        List<AmConllSentence> amDM = AmConllSentence.read(new FileReader(cli.amconllPathDM));
        List<AmConllSentence> amPSD = AmConllSentence.read(new FileReader(cli.amconllPathPSD));
        List<AmConllSentence> amPAS = AmConllSentence.read(new FileReader(cli.amconllPathPAS));
        // map IDs to AmConllSentences so we can look the AmConllSentences up
        Map<String, AmConllSentence> id2amDM = new HashMap<>();
        amDM.stream().forEach(sent -> id2amDM.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPAS = new HashMap<>();
        amPAS.stream().forEach(sent -> id2amPAS.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPSD = new HashMap<>();
        amPSD.stream().forEach(sent -> id2amPSD.put(sent.getId(), sent));
        Set<String> decomposedIDs = Sets.intersectAll(id2amDM.keySet(), id2amPAS.keySet(), id2amPSD.keySet());

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
            if (decomposedIDs.contains(dmGraph.id)) {
                //now we know the graph was decomposed in all graphbanks, and we have all three AM dep trees for it.
                String id = dmGraph.id;
                //ignore 0 in next loop, since it is the artificial root of the SDP graph
                for (int i = 1; i < psdGraph.getNNodes(); i++) {
                    String patternCombination = getPatternCombination(id2amDM.get(id), id2amPAS.get(id), id2amPSD.get(id),
                            dmGraph, pasGraph, psdGraph, i);
                    patterns2lemmaCounter.get(patternCombination).add(psdGraph.getNode(i).lemma);
                    patterns2posCounter.get(patternCombination).add(psdGraph.getNode(i).pos);
                }
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
     * @param amDepTree
     * @param i is 0-based for SDP graph, 1-based for AmConllEntry
     * @return
     */
    public static int getPattern(AmConllSentence amDepTree, Graph sdpGraph, int i) {
        if (i == 0) {
            return 0;
        }
        List<AmConllEntry> children = amDepTree.getChildren(i-1);
        AmConllEntry entry = amDepTree.get(i-1);
        if (entry.getDelexSupertag() == null || entry.getDelexSupertag().equals("") || entry.getDelexSupertag().equals("_")) {
            return 0;
        } else if (children.isEmpty() && entry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
            return 7;
        } else if (children.size() == 2 && isHead(amDepTree, i)) {
            if (!areConnected(children.get(0).getId(), children.get(1).getId(), sdpGraph)) {
                return 1;
            } else {
                return 3;
            }
        } else if (children.size() == 1) {
            if (isHead(amDepTree, i)) {
                return 5;
            } else {
                if (!areConnected(entry.getHead(), children.get(0).getId(), sdpGraph)) {
                    return 2;
                } else {
                    return 4;
                }
            }
        } else if (children.size() == 0) {
            if (isHead(amDepTree, i)) {
                return 7;
            } else {
                return 6;
            }
        } else {
            return 8;
        }
    }


    /**
     * i is 0-based for SDP graph, 1-based for AmConllEntry
     * @param graph
     * @param i
     * @return
     */
    private static boolean isHead(AmConllSentence graph, int i) {
        AmConllEntry parent = graph.getParent(i-1);
        if (parent == null) {
            return true;
        } else if (parent.getForm().equals("ART-ROOT")) { //TODO replace with variable in code
            return true;
        } else {
            return graph.get(i-1).getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION);
        }
    }

    /**
     * i, j are 0-based for SDP graph, 1-based for AmConllEntry
     * @param i
     * @param j
     * @param sdpGraph
     * @return
     */
    private static boolean areConnected(int i, int j, Graph sdpGraph) {
        Node child0 = sdpGraph.getNode(i);
        Node child1 = sdpGraph.getNode(j);
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


    public static String getPatternCombination(AmConllSentence dmAMDepTree, AmConllSentence pasAMDepTree,
                                               AmConllSentence psdAMDepTree, Graph dmGraph, Graph pasGraph,
                                               Graph psdGraph, int i) {
        int patternDM = getPattern(dmAMDepTree, dmGraph, i);
        int patternPAS = getPattern(pasAMDepTree, pasGraph, i);
        int patternPSD = getPattern(psdAMDepTree, psdGraph, i);
        return patterns2string(patternDM, patternPAS, patternPSD);
    }

}
