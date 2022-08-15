package de.saar.coli.amtools.analysis;

import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GetAMRRolesExamples {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws IOException, CorpusReadingException {

        String corpusPath = args[0];
        String outputPath = args[1];
//        // read corpus with IRTG
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation(new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig), "graph"));
        loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "string"));
//        loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "alignment"));
        Corpus corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(corpusPath), loaderIRTG);

        Counter<String> edgeLabelCounter = new Counter<>();
        Map<String, Counter<String>> nodeLabel2edgeLabelCounts = new HashMap<>();
        Map<String, Map<String, String>> nodeLabel2edgeLabel2Example = new HashMap<>();
        Map<String, Set<String>> edgeLabelToNodeNames = new HashMap<>();

        int i = 0;
        for (Instance inst : corpus) {

            SGraph graph = (SGraph)inst.getInputObjects().get("graph");
            List<String> sentence = (List<String>)inst.getInputObjects().get("string");

            for (GraphNode node : graph.getGraph().vertexSet()) {
                String nodeLabel = node.getLabel();
                if (nodeLabel.matches(".*-\\d\\d")) {
                    for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(node)) {
                        String edgeLabel = edge.getLabel();
                        edgeLabelCounter.add(edgeLabel);
                        if (edgeLabel.matches("ARG\\d")) {
                            String example = "\t\t"+sentence.stream().collect(Collectors.joining(" "))
                                    + "\n\t\t" + graph.toIsiAmrString();
                            nodeLabel2edgeLabel2Example.putIfAbsent(nodeLabel, new HashMap<>());
                            nodeLabel2edgeLabel2Example.get(nodeLabel).putIfAbsent(edgeLabel, example);
                            nodeLabel2edgeLabelCounts.putIfAbsent(nodeLabel, new Counter<>());
                            nodeLabel2edgeLabelCounts.get(nodeLabel).add(edgeLabel);
                            edgeLabelToNodeNames.putIfAbsent(edgeLabel, new HashSet<>());
                            edgeLabelToNodeNames.get(edgeLabel).add(nodeLabel);
                        }
                    }
                }
            }

            i++;

        }


        writeEdgeLabelCountsToFile(outputPath+"/elCounts_"+i+".txt", edgeLabelCounter);
        writeNodeLabel2EdgeLabelCountsToFile(outputPath+"/nl2elCountsWithExamples_"+i+".txt",
                nodeLabel2edgeLabelCounts, nodeLabel2edgeLabel2Example);
        for (String edgeLabel : edgeLabelToNodeNames.keySet()) {
            writeEdgeLabelToNodeNamesToFile(outputPath+"/" + edgeLabel + "_"+i+".txt", edgeLabel, edgeLabelToNodeNames.get(edgeLabel),
                    nodeLabel2edgeLabelCounts, nodeLabel2edgeLabel2Example);
        }


    }

    private static void writeEdgeLabelToNodeNamesToFile(String filePath, String edgeLabel, Set<String> nodeLabels,
                                                        Map<String, Counter<String>> nodeLabel2edgeLabelCounts,
                                                        Map<String, Map<String, String>> nodeLabel2edgeLabel2Example) throws IOException {
        System.out.println("Writing edge label stats " + filePath);
        FileWriter writer = new FileWriter(filePath);
        // sort node names by count
        List<String> sortedNodeLabels = nodeLabels.stream().sorted(Comparator.comparingInt(nl -> nodeLabel2edgeLabelCounts.get(nl).get(edgeLabel)).reversed())
                .collect(Collectors.toList());
        for (String nodeLabel : sortedNodeLabels) {
            writer.write(nodeLabel + "\n");
            writer.write(nodeLabel2edgeLabelCounts.get(nodeLabel).get(edgeLabel) + "\n");
            writer.write(nodeLabel2edgeLabel2Example.get(nodeLabel).get(edgeLabel) + "\n\n");
        }
        writer.close();
    }

    private static void writeEdgeLabelCountsToFile(String filePath, Counter<String> edgeLabelCounter) throws IOException {
        System.out.println("Writing edge label counts " + filePath);
        FileWriter writer = new FileWriter(filePath);
        // sort keys in a list
        List<String> keys = edgeLabelCounter.getAllSeen().stream().sorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -Integer.compare(edgeLabelCounter.get(o1), edgeLabelCounter.get(o2));
            }
        }).collect(Collectors.toList());
        for (String edgeLabel : keys) {
            // normalize count string length
            StringBuilder countString = new StringBuilder(Integer.toString(edgeLabelCounter.get(edgeLabel)));
            while (countString.length() < 6) {
                countString.append(" ");
            }
            writer.write(countString + "\t" + edgeLabel + "\n");
        }
        writer.close();
    }


    private static void writeNodeLabel2EdgeLabelCountsToFile(String filePath,
                                                             Map<String, Counter<String>> nodeLabel2EdgeLabelCounts,
                                                             Map<String, Map<String, String>> nodeLabel2EdgeLabel2Example) throws IOException {
        System.out.println("Writing node label to edge label examples " + filePath);
        FileWriter writer = new FileWriter(filePath);
        // sort keys in a list
        List<String> keys = nodeLabel2EdgeLabelCounts.keySet().stream().sorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -Integer.compare(nodeLabel2EdgeLabelCounts.get(o1).sum(),
                        nodeLabel2EdgeLabelCounts.get(o2).sum());
            }
        }).collect(Collectors.toList());
        for (String nodeLabel : keys) {
            writer.write(nodeLabel + "\n");
            // sort edge labels in a list, by count
            List<Object2IntMap.Entry<String>> edgeLabelsSorted = nodeLabel2EdgeLabelCounts.get(nodeLabel).getAllSorted();
            for (Object2IntMap.Entry<String> edgeLabelWithCount : edgeLabelsSorted) {
                // normalize count string length
                StringBuilder countString = new StringBuilder(Integer.toString(edgeLabelWithCount.getIntValue()));
                while (countString.length() < 6) {
                    countString.append(" ");
                }
                writer.write("\t" + countString + "  " + edgeLabelWithCount.getKey() + "\n");
                // examples are already indented
                writer.write(nodeLabel2EdgeLabel2Example.get(nodeLabel).get(edgeLabelWithCount.getKey()) + "\n");
                writer.write("\n");
            }
            writer.write("\n\n\n");
        }
        writer.close();
    }


}
