package de.saar.coli.amtools.analysis;

import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import edu.stanford.nlp.simple.Sentence;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GetAMRNodeLabelTokenAndLemmaCounts {

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

        Counter<String> nodeLabelCounter = new Counter<>();
        Counter<String> nodeLabelWithSenseCounter = new Counter<>();
        Counter<String> tokenCounter = new Counter<>();
        Counter<String> lemmaCounter = new Counter<>();

        int i = 0;
        for (Instance inst : corpus) {

            SGraph graph = (SGraph)inst.getInputObjects().get("graph");
            List<String> sentence = (List<String>)inst.getInputObjects().get("string");

            for (GraphNode node : graph.getGraph().vertexSet()) {
                nodeLabelCounter.add(node.getLabel());
                if (node.getLabel().matches(".*-\\d\\d")) {
                    nodeLabelWithSenseCounter.add(node.getLabel());
                }
            }
            for (String token : sentence) {
                tokenCounter.add(token);
            }
            Sentence stanfordSentence = new Sentence(sentence);
            for (String lemma : stanfordSentence.lemmas()) {
                lemmaCounter.add(lemma);
            }

            i++;

        }


        writeCountsToFile(outputPath+"/nodeLabelCounts_"+i+".txt", nodeLabelCounter);
        writeCountsToFile(outputPath+"/nodeLabelWithSenseCounts_"+i+".txt", nodeLabelWithSenseCounter);
        writeCountsToFile(outputPath+"/tokenCounts_"+i+".txt", tokenCounter);
        writeCountsToFile(outputPath+"/lemmaCounts_"+i+".txt", lemmaCounter);


    }


    private static void writeCountsToFile(String filePath, Counter<String> edgeLabelCounter) throws IOException {
        System.out.println("Writing counts " + filePath);
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



}
