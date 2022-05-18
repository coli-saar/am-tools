package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

// import jcommander
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MakeAMConllFromLeamrAltoCorpus {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to corpus", required=true)
    private String corpusPath;

    @Parameter(names = {"--output", "-o"}, description = "Path to output file", required=true)
    private String outputPath;

    private Corpus corpus;
    private final List<AmConllSentence> amConllSentences = new ArrayList<>();

    public static void main(String args[]) throws CorpusReadingException, IOException, ParseException {

        MakeAMConllFromLeamrAltoCorpus m = readCommandLine(args);

        m.readAltoCorpus();

        m.removeNondecomposableEdges();

        m.computeAMTrees();

        m.writeAMConll();

    }

    public static MakeAMConllFromLeamrAltoCorpus readCommandLine(String[] args) {
        MakeAMConllFromLeamrAltoCorpus m = new MakeAMConllFromLeamrAltoCorpus();
        JCommander commander = new JCommander(m);
        commander.parse(args);
        return m;
    }

    private void readAltoCorpus() throws IOException, CorpusReadingException {
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("graph", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("alignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        corpus = Corpus.readCorpus(new FileReader(corpusPath), loaderIRTG);
        System.out.println("Read " + corpus.getNumberOfInstances() + " aligned sentence-graph pairs.");
        turnGraphStringsIntoRootedGraphs();
    }

    private void turnGraphStringsIntoRootedGraphs() {
        IsiAmrInputCodec codec = new IsiAmrInputCodec();
        for (Instance instance : corpus) {
            String graphString = ((List<String>)instance.getInputObjects().get("graph")).stream().collect(Collectors.joining(" "));
            graphString = graphString.replaceFirst("/", "<root> /");
            instance.getInputObjects().put("actual_graph", codec.read(graphString));
        }
    }

    private void removeNondecomposableEdges() throws ParseException {
        MutableInteger totalReentrantEdges = new MutableInteger(0);
        MutableInteger totalRemovedEdges = new MutableInteger(0);
        for (Instance inst : corpus) {
            removeNondecomposableEdgesFromInstance(inst, totalReentrantEdges, totalRemovedEdges);
        }
        System.out.println("Removed " + totalRemovedEdges + " edges out of " + totalReentrantEdges
                + " total reentrant edges.");
    }

    private void removeNondecomposableEdgesFromInstance(Instance inst, MutableInteger totalReentrantEdges, MutableInteger totalRemovedEdges) throws ParseException {
        SGraph graph = (SGraph) inst.getInputObjects().get("actual_graph");
        String graphString = ((List<String>)inst.getInputObjects().get("graph")).stream().collect(Collectors.joining(" "));
        int edges_before = graph.getGraph().edgeSet().size();
        SplitCoref.split(graphString, graph, totalReentrantEdges);
        int edges_after = graph.getGraph().edgeSet().size();
        totalRemovedEdges.setValue(totalRemovedEdges.getValue() + edges_before - edges_after);
    }

    private void computeAMTrees() {
        int i = 0;
        int successCount = 0;
        SupertagDictionary supertagDictionary = new SupertagDictionary();

        for (Instance inst : corpus) {
            SGraph graph = (SGraph) inst.getInputObjects().get("actual_graph");
            List<Alignment> alignments = ((List<String>) inst.getInputObjects().get("alignment")).stream()
                    .map(Alignment::read).collect(Collectors.toList());
            List<String> sentence = (List<String>) inst.getInputObjects().get("string");
            MRInstance mrInst = new MRInstance( sentence, graph, alignments);

            try {
                TreeAutomaton auto;

                // this automaton isn't really being given graph types for the scorer. Is this a problem?
                AMRSignatureBuilder sigBuilder = new AMRSignatureBuilder();
                auto = AlignmentTrackingAutomaton.create(mrInst,
                        sigBuilder, false, (graphTypePair -> AMRSignatureBuilder.scoreGraphPassiveSpecial(graphTypePair)));
                auto.processAllRulesBottomUp(null);

                Tree<String> vit = auto.viterbi();
                //System.err.println(vit);
                if (vit != null) {
                    successCount += 1;
                    AmConllSentence amConllSentence = AmConllSentence.fromIndexedAMTerm(vit, mrInst, supertagDictionary);
                    amConllSentences.add(amConllSentence);
                }
                if ((i+1) % 500 == 0) {
                    System.err.println("\nSuccesses: "+successCount+"/"+i+"\n");
                }
            } catch (IllegalArgumentException ex) {
                System.err.println(i);
                System.err.println(graph.toIsiAmrStringWithSources());
                System.err.println(ex.toString());
            } catch (Exception ex) {
                System.err.println(i);
                System.err.println(graph.toIsiAmrStringWithSources());
                ex.printStackTrace();
            }


        }
    }

    private void writeAMConll() throws IOException {
        FileWriter writer = new FileWriter(outputPath);
        System.out.println("Writing a total of " +amConllSentences.size() + " AM trees to " + outputPath);
        AmConllSentence.write(writer, amConllSentences);
        writer.close();
    }

}
