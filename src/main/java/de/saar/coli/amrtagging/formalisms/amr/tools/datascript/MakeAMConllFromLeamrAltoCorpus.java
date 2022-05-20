package de.saar.coli.amrtagging.formalisms.amr.tools.datascript;

// import jcommander
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilderWithMultipleOutNodes;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
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

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.ServerError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MakeAMConllFromLeamrAltoCorpus {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to corpus", required=true)
    private String corpusPath;

    @Parameter(names = {"--output", "-o"}, description = "Path to output file", required=true)
    private String outputPath;

    private List<MRInstance> corpus;
    private final List<AmConllSentence> amConllSentences = new ArrayList<>();
    private final AMRBlobUtils blobUtils = new AMRBlobUtils();
    private final AMRSignatureBuilderWithMultipleOutNodes signatureBuilder = new AMRSignatureBuilderWithMultipleOutNodes();

    public static void main(String args[]) throws CorpusReadingException, IOException, ParseException {

        MakeAMConllFromLeamrAltoCorpus m = readCommandLine(args);

        m.readAltoCorpus();

        m.filterOutBadGraphsAndAlignments();

        m.makeGraphsDecomposeable();

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
        Corpus altoCorpus = Corpus.readCorpus(new FileReader(corpusPath), loaderIRTG);
        System.out.println("Read " + altoCorpus.getNumberOfInstances() + " aligned sentence-graph pairs.");
        turnGraphStringsIntoRootedGraphs(altoCorpus);
        convertToListOfMrInstancesAndStore(altoCorpus);
    }

    private void convertToListOfMrInstancesAndStore(Corpus altoCorpus) {
        corpus = new ArrayList<>();
        for (Instance inst : altoCorpus) {
            SGraph graph = (SGraph) inst.getInputObjects().get("actual_graph");
            List<Alignment> alignments = ((List<String>) inst.getInputObjects().get("alignment")).stream()
                    .map(Alignment::read).collect(Collectors.toList());
            List<String> sentence = (List<String>) inst.getInputObjects().get("string");
            MRInstance mrInst = new MRInstance(sentence, graph, alignments);
            mrInst.setExtra("original_graph_string", ((List<String>)inst.getInputObjects().get("graph")).stream().collect(Collectors.joining(" ")));
            corpus.add(mrInst);
        }
    }

    private void turnGraphStringsIntoRootedGraphs(Corpus altoCorpus) {
        IsiAmrInputCodec codec = new IsiAmrInputCodec();
        for (Instance instance : altoCorpus) {
            String graphString = ((List<String>)instance.getInputObjects().get("graph")).stream().collect(Collectors.joining(" "));
            graphString = graphString.replaceFirst("/", "<root> /");
            instance.getInputObjects().put("actual_graph", codec.read(graphString));
//            System.out.println("Read graph: " + graphString);
//            System.out.println("Resulting graph: " + instance.getInputObjects().get("actual_graph"));
//            System.out.println(((SGraph)instance.getInputObjects().get("actual_graph")).toIsiAmrStringWithSources());
        }
    }


    private void filterOutBadGraphsAndAlignments() {
        filterOutInstancesWithOverlappingAlignmentSpans();
        filterOutGraphsWithDisconnectedAlignmentSubgraphs();
    }

    private void filterOutInstancesWithOverlappingAlignmentSpans() {
        List<MRInstance> filteredCorpus = new ArrayList<>();
        for (MRInstance mrInst : corpus) {
            if (!hasOverlappingAlignmentSpans(mrInst)) {
                filteredCorpus.add(mrInst);
            }
        }
        System.out.println("Filtered out " + (corpus.size() - filteredCorpus.size()) + " instances with overlapping alignment spans.");
        corpus = filteredCorpus;
    }

    private boolean hasOverlappingAlignmentSpans(MRInstance mrInst) {
        Set<Integer> indicesCovered = new HashSet<>();
        for (Alignment alignment : mrInst.getAlignments()) {
            for (int i = alignment.span.start; i < alignment.span.end; i++) {
                if (indicesCovered.contains(i)) {
                    return true;
                } else {
                    indicesCovered.add(i);
                }
            }
        }
        return false;
    }

    private void filterOutGraphsWithDisconnectedAlignmentSubgraphs() {
        List<MRInstance> filteredCorpus = new ArrayList<>();
        for (MRInstance mrInst : corpus) {
            if (!hasDisconnectedAlignmentSubgraphs(mrInst)) {
                filteredCorpus.add(mrInst);
            }
        }
        System.out.println("Filtered out " + (corpus.size() - filteredCorpus.size()) + " instances with disconnected" +
                "constants based on alignments.");
        corpus = filteredCorpus;
    }

    private boolean hasDisconnectedAlignmentSubgraphs(MRInstance mrInst) {
        for (Alignment alignment : mrInst.getAlignments()) {
            if (alignment.isDisconnected(mrInst.getGraph(), blobUtils)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unifies in-edge attachment node (i.e. solves multiple root problem by changing the graph) and
     * removes reentrant edges that cannot be decomposed with this AM algebra signature.
     * @throws ParseException
     */
    private void makeGraphsDecomposeable() throws ParseException {
        MutableInteger totalReentrantEdges = new MutableInteger(0);
        MutableInteger totalRemovedEdges = new MutableInteger(0);
        UnifyInEdges unifyInEdges = new UnifyInEdges(new AMRBlobUtils());
        for (MRInstance mrInst : corpus) {
            try {
                SplitCoref splitCoref = new SplitCoref(); // here we split based on single node constants, since decomposition based on alignments may fail before unifyInEdges is called.
                removeNondecomposableEdgesFromInstance(mrInst, (String) mrInst.getExtra("original_graph_string"), totalReentrantEdges, totalRemovedEdges, splitCoref);
            } catch (Exception e) {
                System.err.println("Error while processing instance:   " + String.join(" ", mrInst.getSentence()));
                e.printStackTrace();
                System.err.println("This may yield a non-decomposeable graph down the line.");
            }
            try {
                SplitCoref splitCoref = new SplitCoref(signatureBuilder, mrInst.getAlignments());
                unifyInEdges.unifyInEdges(mrInst);
                removeNondecomposableEdgesFromInstance(mrInst, mrInst.getGraph().toIsiAmrString(), totalReentrantEdges, totalRemovedEdges, splitCoref);
            } catch (Exception e) {
                System.err.println("Error while processing instance:   " + String.join(" ", mrInst.getSentence()));
                e.printStackTrace();
                System.err.println("This may yield a non-decomposeable graph down the line.");
            }
        }
        System.out.println("Removed " + totalRemovedEdges + " edges out of " + totalReentrantEdges
                + " total reentrant edges.");
    }

    private void removeNondecomposableEdgesFromInstance(MRInstance inst, String graphString, MutableInteger totalReentrantEdges, MutableInteger totalRemovedEdges, SplitCoref splitCoref) throws ParseException {
        SGraph graph = inst.getGraph();
        int edges_before = graph.getGraph().edgeSet().size();
        // small note to self: this splitCoref does not take alignments into account. So it is OK that it uses the
        // simpler AMRSignatureBuilder class, but it may leave some graphs non-decomposeable.
        splitCoref.split(graphString, graph, totalReentrantEdges);
        int edges_after = graph.getGraph().edgeSet().size();
        totalRemovedEdges.setValue(totalRemovedEdges.getValue() + edges_before - edges_after);
    }

    private void computeAMTrees() {
        int i = 0;
        int successCount = 0;
        SupertagDictionary supertagDictionary = new SupertagDictionary();


        System.out.println();//just making a line to overwrite later
        for (MRInstance mrInst : corpus) {
            i++;


            try {
                TreeAutomaton auto;

                // this automaton isn't really being given graph types for the scorer. Is this a problem?
                auto = AlignmentTrackingAutomaton.create(mrInst,
                        signatureBuilder, false, (AMRSignatureBuilder::scoreGraphPassiveSpecial));
                auto.processAllRulesBottomUp(null);

                // write the automaton to a file
                Path folderPath = Paths.get(outputPath).toAbsolutePath().getParent();
                FileWriter automatonWriter =  new FileWriter(folderPath + "/constants_" + i + ".txt");
                for (int j = 0; j<= auto.getSignature().getMaxSymbolId(); j++) {
                    String symbol = auto.getSignature().resolveSymbolId(j);
                    if (symbol != null && auto.getSignature().getArity(j) == 0) {
                        automatonWriter.write(symbol + "\n");
                    }
                }
                automatonWriter.close();

                Tree<String> vit = auto.viterbi();
                //System.err.println(vit);
                if (vit != null) {
                    successCount += 1;
                    AmConllSentence amConllSentence = AmConllSentence.fromIndexedAMTerm(vit, mrInst, supertagDictionary);
                    amConllSentences.add(amConllSentence);
                } else {
                    System.err.println("Could not decompose instance " + mrInst.getSentence().stream().collect(Collectors.joining(" ")));
                    System.err.println("Graph: " + mrInst.getGraph().toString());
                    System.err.println("Original graph string: " + mrInst.getExtra("original_graph_string"));
                }
                System.out.print("\rSuccesses: "+successCount+"/"+i);
            } catch (Exception ex) {
                System.err.println(i);
                ex.printStackTrace();
                System.err.println(mrInst.getGraph().toString());
            }

        }
        System.out.print("\nDone! Successes: "+successCount+"/"+i + "\n");
    }

    private void writeAMConll() throws IOException {
        FileWriter writer = new FileWriter(outputPath);
        System.out.println("Writing a total of " +amConllSentences.size() + " AM trees to " + outputPath);
        AmConllSentence.write(writer, amConllSentences);
        writer.close();
    }

}
