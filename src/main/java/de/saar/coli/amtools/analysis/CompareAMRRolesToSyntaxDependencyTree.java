package de.saar.coli.amtools.analysis;

import de.saar.coli.amrtagging.Alignment;
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
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.simple.Sentence;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompareAMRRolesToSyntaxDependencyTree {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws IOException, CorpusReadingException {

        String corpusPath = args[0];
        String outputPath = args[1];
//        // read corpus with IRTG
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation(new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig), "graph"));
        loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "string"));
        loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "alignment"));
        Corpus corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(corpusPath), loaderIRTG);

        Map<String, Counter<String>> edgeLabel2SyntaxPaths = new HashMap<>();
        Map<String, Map<String, Counter<String>>> nodeLabel2edgeLabel2SyntaxPaths = new HashMap<>();

        int i = 0;
        for (Instance inst : corpus) {

            SGraph graph = (SGraph)inst.getInputObjects().get("graph");
            List<String> sentence = (List<String>)inst.getInputObjects().get("string");
            List<String> alStrings = (List<String>)inst.getInputObjects().get("alignment");
            List<Alignment> alignments = alStrings.stream().map(Alignment::read).collect(Collectors.toList());

            Sentence stanfordSentence = new Sentence(sentence);

            SemanticGraph depTree = stanfordSentence.dependencyGraph();

            for (Alignment alignment : alignments) {
                for (String nn : alignment.nodes) {
                    GraphNode node = graph.getNode(nn);
                    if (node.getLabel().matches(".*-\\d\\d")) {
                        for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(node)) {
                            if (edge.getLabel().matches("ARG\\d+")) {
                                GraphNode other = BlobUtils.otherNode(node, edge);
                                if (!alignment.nodes.contains(other.getName())) {
                                    Alignment otherAlignment = null;
                                    for (Alignment oA : alignments) {
                                        if (oA.nodes.contains(other.getName())) {
                                            otherAlignment = oA;
                                            break;
                                        }
                                    }
                                    if (otherAlignment != null) {
                                        // then we actually have a thing going
                                        IndexedWord parentWord = depTree.getNodeByIndex(alignment.span.start + 1);
                                        IndexedWord childWord = depTree.getNodeByIndex(otherAlignment.span.start + 1);
                                        // find the closest path between parent and child
                                        String syntaxPathString = "-> ";
                                        List<SemanticGraphEdge> path = depTree.getShortestDirectedPathEdges(parentWord, childWord);
                                        if (path != null) {
                                            syntaxPathString += path.stream().map(e -> getSyntacticLabel(e))
                                                    .collect(Collectors.joining(" "));
                                        } else {
                                            syntaxPathString = "<- ";
                                            path = depTree.getShortestDirectedPathEdges(childWord, parentWord);
                                            if (path != null) {
                                                syntaxPathString += path.stream().map(e -> getSyntacticLabel(e))
                                                        .collect(Collectors.joining(" "));
                                            } else {
                                                syntaxPathString = "<--> ";
                                                path = depTree.getShortestUndirectedPathEdges(parentWord, childWord);
                                                if (path != null) {
                                                    syntaxPathString += path.stream().map(e -> getSyntacticLabel(e))
                                                            .collect(Collectors.joining(" "));
                                                } else {
                                                    syntaxPathString = "NO_PATH_FOUND";
                                                    System.out.println("NO_PATH_FOUND");
                                                    System.out.println(node.getLabel());
                                                    System.out.println(other.getLabel());
                                                    System.out.println(parentWord.word());
                                                    System.out.println(childWord.word());
                                                    System.out.println();
                                                }
                                            }
                                        }

                                        // store in maps
                                        edgeLabel2SyntaxPaths.computeIfAbsent(edge.getLabel(), k -> new Counter<>()).add(syntaxPathString);
                                        nodeLabel2edgeLabel2SyntaxPaths.computeIfAbsent(node.getLabel(), k -> new HashMap<>()).
                                                computeIfAbsent(edge.getLabel(), k -> new Counter<>()).add(syntaxPathString);

                                    }
                                }
                            }
                        }
                    }
                }
            }

            i++;

            if (i % 1000 == 0 || i == 100 || i == 10) {
                writeEdgeLabel2SyntaxPathsToFile(outputPath+"/el2sp_specific_"+i+".txt", edgeLabel2SyntaxPaths);
                writeNodeLabel2EdgeLabel2SyntaxPathsToFile(outputPath+"/nl2el2sp_specific_"+i+".txt", nodeLabel2edgeLabel2SyntaxPaths);
            }

        }


    }

    private static String getSyntacticLabel(SemanticGraphEdge edge) {
        if (edge.getRelation().getSpecific() != null) {
            return edge.getRelation().getShortName()+"_"+edge.getRelation().getSpecific();
        } else {
            return edge.getRelation().getShortName();
        }
    }


    private static void writeEdgeLabel2SyntaxPathsToFile(String filePath, Map<String, Counter<String>> edgeLabel2SyntaxPaths) throws IOException {
        System.out.println("Writing edge label to syntax paths to " + filePath);
        FileWriter writer = new FileWriter(filePath);
        // sort keys in a list
        List<String> keys = edgeLabel2SyntaxPaths.keySet().stream().sorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -Integer.compare(edgeLabel2SyntaxPaths.get(o1).sum(), edgeLabel2SyntaxPaths.get(o2).sum());
            }
        }).collect(Collectors.toList());
        for (String edgeLabel : keys) {
            writer.write(edgeLabel + "\n");
            // sort syntax paths in a list, by count
            List<String> syntaxPathsSorted = edgeLabel2SyntaxPaths.get(edgeLabel).getAllSeen().stream().sorted(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return -Integer.compare(edgeLabel2SyntaxPaths.get(edgeLabel).get(o1), edgeLabel2SyntaxPaths.get(edgeLabel).get(o2));
                }
            }).collect(Collectors.toList());
            for (String syntaxPath :syntaxPathsSorted) {
                writer.write(edgeLabel2SyntaxPaths.get(edgeLabel).get(syntaxPath) + "\t" + syntaxPath + "\n");
            }
            writer.write("\n\n");
        }
        writer.close();
    }


    private static void writeNodeLabel2EdgeLabel2SyntaxPathsToFile(String filePath, Map<String, Map<String, Counter<String>>> nodeLabel2EdgeLabel2SyntaxPaths) throws IOException {
        System.out.println("Writing node label to edge label to syntax paths to " + filePath);
        FileWriter writer = new FileWriter(filePath);
        // sort keys in a list
        List<String> keys = nodeLabel2EdgeLabel2SyntaxPaths.keySet().stream().sorted(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return -Integer.compare(getTotalNodeLabelCount(nodeLabel2EdgeLabel2SyntaxPaths.get(o1)),
                        getTotalNodeLabelCount(nodeLabel2EdgeLabel2SyntaxPaths.get(o2)));
            }
        }).collect(Collectors.toList());
        for (String nodeLabel : keys) {
            writer.write(nodeLabel + "\n");
            // sort edge labels in a list, by count
            List<String> edgeLabelsSorted = nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).keySet().stream().sorted(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return -Integer.compare(nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).get(o1).sum(),
                            nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).get(o2).sum());
                }
            }).collect(Collectors.toList());
            for (String edgeLabel : edgeLabelsSorted) {
                writer.write("\t" + edgeLabel + "\n");
                // sort syntax paths in a list, by count
                List<String> syntaxPathsSorted = nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).get(edgeLabel).getAllSeen().stream().sorted(new Comparator<String>() {
                    @Override
                    public int compare(String o1, String o2) {
                        return -Integer.compare(nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).get(edgeLabel).get(o1),
                                nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).get(edgeLabel).get(o2));
                    }
                }).collect(Collectors.toList());
                for (String syntaxPath : syntaxPathsSorted) {
                    writer.write("\t\t" +  nodeLabel2EdgeLabel2SyntaxPaths.get(nodeLabel).get(edgeLabel).get(syntaxPath) + "\t" + syntaxPath + "\n");
                }
                writer.write("\n");
            }
            writer.write("\n\n\n");
        }
        writer.close();
    }

    private static int getTotalNodeLabelCount(Map<String, Counter<String>> el2sp) {
        int total = 0;
        for (String edgeLabel : el2sp.keySet()) {
            total += el2sp.get(edgeLabel).sum();
        }
        return total;
    }

}
