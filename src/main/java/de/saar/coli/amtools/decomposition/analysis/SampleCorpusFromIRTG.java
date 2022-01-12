package de.saar.coli.amtools.decomposition.analysis;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.Algebra;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.codec.IrtgInputCodec;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusWriter;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.tree.Tree;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleCorpusFromIRTG {

    private static final int TOTAL_SENT_COUNT = 100;

    public static void main(String[] args) throws IOException {

        InterpretedTreeAutomaton grammar = new IrtgInputCodec().read(new FileInputStream("examples/toyGraphbankGenerator.irtg"));

        System.out.println(grammar);


        Homomorphism homString = grammar.getInterpretation("string").getHomomorphism();
        Homomorphism homGraph = grammar.getInterpretation("graph").getHomomorphism();
        Algebra algString = grammar.getInterpretation("string").getAlgebra();
        Algebra algGraph = grammar.getInterpretation("graph").getAlgebra();

        int skippedDuplicates = 0;
        Corpus corpus = new Corpus();
        for (int i = 0; i < TOTAL_SENT_COUNT; i++) {
            Tree<String> rndTree = grammar.getAutomaton().getRandomTree();
            Pair<SGraph, ApplyModifyGraphAlgebra.Type> graph = (Pair<SGraph, ApplyModifyGraphAlgebra.Type>)algGraph.evaluate(homGraph.apply(rndTree));
            while (hasDuplicates(graph)) {
                System.out.println("skipping a duplicate: "+rndTree);
                skippedDuplicates++;
                rndTree = grammar.getAutomaton().getRandomTree();
                graph = (Pair<SGraph, ApplyModifyGraphAlgebra.Type>)algGraph.evaluate(homGraph.apply(rndTree));

            }
            Map<String, Object> objects = new HashMap<>();
            List<String> sent = (List<String>)algString.evaluate(homString.apply(rndTree));
            objects.put("string", sent);
            objects.put("graph", graph);
            Instance inst = new Instance();
            inst.setInputObjects(objects);
            inst.setDerivationTree(grammar.getAutomaton().getSignature().mapSymbolsToIds(rndTree));
            // compute alignments (relying on the fact that no graph nodes are duplciates, and node labels are always prefixes of the words)
            List<Alignment> alignments = new ArrayList<>();
            for (GraphNode node : graph.left.getGraph().vertexSet()) {
                int alignedIndex = 0;
                while (!sent.get(alignedIndex).startsWith(node.getLabel())) {
                    alignedIndex++;
                }
                Alignment al = new Alignment(node.getName(), alignedIndex);
                alignments.add(al);
            }
            objects.put("alignment", alignments.stream().map(Alignment::toString).collect(Collectors.toList()));
//            objects.put("alignmentObjects", alignments);

//            System.out.println(objects);
//            System.out.println(inst);
            corpus.addInstance(inst);
        }
        System.out.println("Skipped duplicates: "+skippedDuplicates);


        //TODO write output in file, writing graphs in graphviz dot format
        FileWriter w = new FileWriter("examples/output/toyGraphbankOwnFormat.txt");
        for (Instance inst : corpus) {
            List<String> sent = (List<String>)inst.getInputObjects().get("string");
            Pair<SGraph, ApplyModifyGraphAlgebra.Type> graph = (Pair<SGraph, ApplyModifyGraphAlgebra.Type>)inst.getInputObjects().get("graph");
            List<String> alignments = (List<String>)inst.getInputObjects().get("alignment");

            //writing sentence
            w.write(sent.stream().collect(Collectors.joining(" "))+"\n");

            // writing graph
            w.write("digraph G {\n");
            // writing nodes
            for (GraphNode node : graph.left.getGraph().vertexSet()) {
                if (graph.left.getSourcesAtNode(node.getName()).contains("root")) {
                    w.write(node.getName() + " [label=\""+node.getLabel()+"\", style=bold, shape=rectangle, margin=0.04];\n");
                } else {
                    w.write(node.getName() + " [label=\""+node.getLabel()+"\", shape=rectangle, margin=0.04];\n");
                }
            }
            // writing edges
            for (GraphEdge edge : graph.left.getGraph().edgeSet()) {
                w.write(edge.getSource().getName() + "->" + edge.getTarget().getName() + " [label=\"" + edge.getLabel() + "\"];\n");
            }
            w.write("}\n");

            // writing alignments
            w.write(alignments.stream().collect(Collectors.joining(" "))+"\n");
            w.write("\n");
        }
        w.close();

        // Just writing corpus in alto format for debugging
        grammar.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(new Signature(), new Signature()), "alignment"));
        CorpusWriter cw = new CorpusWriter(grammar, "", new FileWriter("examples/output/toyGraphbank.txt"));
        cw.writeCorpus(corpus);
        cw.close();


    }

    private static boolean hasDuplicates(Pair<SGraph, ApplyModifyGraphAlgebra.Type> graph) {
        Set<GraphNode> nodes = graph.left.getGraph().vertexSet();
        Set<String> nodeLabels = nodes.stream().map(GraphNode::getLabel).collect(Collectors.toSet());
        return nodes.size() != nodeLabels.size();
    }
}
