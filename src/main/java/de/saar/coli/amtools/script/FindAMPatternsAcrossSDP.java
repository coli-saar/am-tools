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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static de.saar.coli.amtools.script.FindPatternsAcrossSDP.*;

public class FindAMPatternsAcrossSDP {

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\dm\\train\\train.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\pas\\train\\train.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\psd\\train\\train.amconll";


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;



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
        System.err.println("reading amDM");
        List<AmConllSentence> amDM = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(cli.amconllPathDM), StandardCharsets.UTF_8));
        System.err.println("reading amPAS");
        List<AmConllSentence> amPAS = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(cli.amconllPathPAS), StandardCharsets.UTF_8));
        System.err.println("reading amPSD");
        List<AmConllSentence> amPSD = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(cli.amconllPathPSD), StandardCharsets.UTF_8));
        System.err.println("done reading");
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
        int totalNodes = 0;
        int totalDiffs = 0;
        int alreadyUnifiedDiffs = 0;
        String equivalentControl = "13";//indicating that the AM algebra already makes 1 and 3 equivalent
        String equivalentMod = "46";//indicating that the AM algebra already makes 4 and 6 equivalent
        Set<String> equalPatterns = new HashSet<>();
        for (int i = 0; i <= MAX_PATTERN; i++) {
            String iString = String.valueOf(i);
            equalPatterns.add(iString+iString+iString);
        }

        for (int i = 0; i<=MAX_PATTERN; i++) {
            for (int j = 0; j<=MAX_PATTERN; j++) {
                for (int k = 0; k<=MAX_PATTERN; k++) {
                    patterns2lemmaCounter.put(patterns2string(i, j, k), new Counter<>());
                    patterns2posCounter.put(patterns2string(i, j, k), new Counter<>());
                }
            }
        }

        for (String sentenceID : decomposedIDs) {
            //ignore 0 in next loop, since it is the artificial root of the SDP graph
            AmConllSentence dmDep = id2amDM.get(sentenceID);
            for (int i = 0; i < dmDep.size(); i++) {
                String patternCombination = getPatternCombination(id2amDM.get(sentenceID),
                        id2amPAS.get(sentenceID), id2amPSD.get(sentenceID), i+1);
                patterns2lemmaCounter.get(patternCombination).add(dmDep.get(i).getLemma());
                patterns2posCounter.get(patternCombination).add(dmDep.get(i).getPos());
                totalNodes++;
                if (!equalPatterns.contains(patternCombination)) {
                    totalDiffs++;
                    if ((equivalentControl.contains(patternCombination.substring(0,1))
                        && equivalentControl.contains(patternCombination.substring(1,2))
                        && equivalentControl.contains(patternCombination.substring(2,3)))
                        || (equivalentMod.contains(patternCombination.substring(0,1))
                            && equivalentMod.contains(patternCombination.substring(1,2))
                            && equivalentMod.contains(patternCombination.substring(2,3)))) {
                        alreadyUnifiedDiffs++;
                    }
                }
            }
        }

        List<String> sortedPatterns = patterns2lemmaCounter.keySet().stream()
                .sorted((o1, o2) -> patterns2lemmaCounter.get(o2).sum()-patterns2lemmaCounter.get(o1).sum())
                .collect(Collectors.toList());

        System.err.println("Total nodes: "+totalNodes);
        System.err.println("Total diffs: "+totalDiffs);
        System.err.println("Already unified by AM algebra: "+totalDiffs);

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


        for (String pattern : sortedPatterns) {
            System.err.println(pattern+","+patterns2lemmaCounter.get(pattern).sum());
        }

        System.err.println("DIFFERENCES:");
        for (String pattern : sortedPatterns) {
            if (!equalPatterns.contains(pattern)) {
                System.err.println(pattern + "," + patterns2lemmaCounter.get(pattern).sum());
            }
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
     * @param id is 0-based for SDP graph, 1-based for AmConllEntry
     * @return
     */
    public static int getPattern(AmConllSentence amDepTree, int id) {
        if (id == 0) {
            return 0; // id is 1-based
        }
        AmConllEntry entry = amDepTree.get(id-1);
        String edgeLabel = entry.getEdgeLabel();
        List<AmConllEntry> children = amDepTree.getChildren(id-1);
        Set<String> childAppSources = new HashSet<>();
        for (AmConllEntry child : children) {
            if (child.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
                childAppSources.add(child.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
            }
        }
        int numAppChildren = childAppSources.size();
        ApplyModifyGraphAlgebra.Type type = entry.getType();
        if (edgeLabel.equals(AmConllEntry.IGNORE)) {
            return 0;
        } else if (edgeLabel.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
            // this works also for the case where the parent is the artificial root. So this is true iff our entry is a head.
            if (numAppChildren == 0) {
                return 7;
            } else if (numAppChildren == 1) {
                return 5;
            } else if (numAppChildren == 2) {
                boolean hasTypeEdge = false;
                for (ApplyModifyGraphAlgebra.Type.Edge edge : type.getAllEdges()) {
                    if (childAppSources.contains(edge.getSource()) || childAppSources.contains(edge.getTarget())) {
                        // the above line is a bit of a roundabout way of checking this, but should work
                        hasTypeEdge = true;
                    }
                }
                return hasTypeEdge ? 3 : 1;
            }
        } else if (edgeLabel.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
            if (numAppChildren == 0) {
                if (type.getAllSources().size() == 1) {
                    return 6;
                } else if (type.getAllSources().size() == 2) {
                    return 4;
                }
            } else if (numAppChildren == 1) {
                return 2;
            }
        }
        return 8;
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
                                               AmConllSentence psdAMDepTree, int i) {
        int patternDM = getPattern(dmAMDepTree, i);
        int patternPAS = getPattern(pasAMDepTree, i);
        int patternPSD = getPattern(psdAMDepTree, i);
        return patterns2string(patternDM, patternPAS, patternPSD);
    }

}
