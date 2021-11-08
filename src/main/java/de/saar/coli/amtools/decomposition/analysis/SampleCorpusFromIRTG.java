package de.saar.coli.amtools.decomposition.analysis;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.Algebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.codec.IrtgInputCodec;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusWriter;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.tree.Tree;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
            objects.put("string", algString.evaluate(homString.apply(rndTree)));
            objects.put("graph", graph);
            Instance inst = new Instance();
            inst.setInputObjects(objects);
            inst.setDerivationTree(grammar.getAutomaton().getSignature().mapSymbolsToIds(rndTree));
//            System.out.println(objects);
//            System.out.println(inst);
            corpus.addInstance(inst);
        }
        System.out.println("Skipped duplicates: "+skippedDuplicates);
        CorpusWriter cw = new CorpusWriter(grammar, "", new FileWriter("examples/output/toyGraphbank.txt"));
        cw.writeCorpus(corpus);
        cw.close();

        //TODO output in seperate file, write graphs in graphviz dot format
        //TODO compute and output alignments (rely on the fact that no graph nodes are duplciates, and node labels are always prefixes of the words)

    }

    private static boolean hasDuplicates(Pair<SGraph, ApplyModifyGraphAlgebra.Type> graph) {
        Set<GraphNode> nodes = graph.left.getGraph().vertexSet();
        Set<String> nodeLabels = nodes.stream().map(GraphNode::getLabel).collect(Collectors.toSet());
        return nodes.size() != nodeLabels.size();
    }
}
