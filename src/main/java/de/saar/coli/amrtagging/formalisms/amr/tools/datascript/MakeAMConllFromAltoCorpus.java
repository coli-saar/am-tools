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
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Designed to take an alto irtg file as input
 * This is the output of RawAMRCorpus2TrainingData, which is usually a file called namesDatesNumber_AlsFixed_sorted.corpus
 * You can tell the script to only do part of its job with flags and missing parameters:
 *  -nt: no new AM trees should be made
 *  -nr: there are no reentrancies in the corpus (true of output of older versions of am-tools)
 *  no -sd: won't read or write a new supertag dictionary
 *  no -o: won't write a new amconll file
 *  no -cp: won't write a file of graph IDs that were changed by UnifyInEdges
 *  no -v: won't print as much
 */
public class MakeAMConllFromAltoCorpus {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to alto corpus", required=true)
    private String corpusPath;

    @Parameter(names = {"--supertagDictionary", "-sd"}, description = "Path to supertag dictionary. If file exists, loads dictionary from file. In the end, writes updated dictionary to file.")
    private String supertagDictionaryPath;

    @Parameter(names = {"--output", "-o"}, description = "Path to output file if writing new amconll file")
    private String outputPath;

    @Parameter(names = {"--changedGraphPath", "-g"}, description = "Path to file to write changed graph IDs to")
    private String changedGraphPath;

    @Parameter(names = {"--timeout"}, description = "Seconds for timeout for a single sentence")
    private int timeout = 3600;

    @Parameter(names = {"--threads", "-th"}, description = "Number of threads to be used in parallel computation")
    private int nrThreads = 1;

    @Parameter(names = {"--reentrancies", "-r"}, arity = 1, description = "Default true. Set to false for old alto corpora that don't include reentrancies")
    private boolean reentrancies = true;

    @Parameter(names = {"--newTrees", "-nt"}, arity = 1, description = "Default true. Set to false if you don't want to compute new AM trees")
    private boolean newTrees = true;

    @Parameter(names = {"--verbose", "-v"}, description = "Flag: if set, prints info about multiple-root problem moved edges")
    private boolean verbose = false;

    @Parameter(names = {"--help", "-h", "-?"}, description = "displays help", help = true)
    private boolean help = false;

    private List<MRInstance> corpus;
    private final List<AmConllSentence> amConllSentences = new ArrayList<>();
    private final AMRBlobUtils blobUtils = new AMRBlobUtils();
    private final AMRSignatureBuilderWithMultipleOutNodes signatureBuilder = new AMRSignatureBuilderWithMultipleOutNodes();

    public static void main(String[] args) throws CorpusReadingException, IOException, ParseException, ParserException {

        MakeAMConllFromAltoCorpus m = readCommandLine(args);

        if (m.help) {
            return;
        }

        if (!m.reentrancies) {
            System.err.println("Warning: no reentrancies in Alto corpus means some graphs probably cannot be made fully decomposable");
        }

        if (!m.newTrees || m.outputPath == null) {
            System.err.println("Warning: no new amconll file will be printed");
        }

        m.readAltoCorpus();

        m.filterOutBadGraphsAndAlignments();

        // if passed the -nr flag (no reentrancies in the alto corpus), this will only partly work
        List<MRInstance> graphsWithMovedEdges = m.makeGraphsDecomposeable();
        if (m.changedGraphPath != null) {
            try {
                File changedGraphFile = new File(m.changedGraphPath);
                FileWriter myWriter = new FileWriter(changedGraphFile);
                for (MRInstance mrInstance :
                        graphsWithMovedEdges) {
                    myWriter.write(mrInstance.getId() + "\n");
                }
                myWriter.close();
                System.out.println("Successfully wrote changed graph IDs to " + m.changedGraphPath);
            } catch (IOException e) {
                System.err.println("Couldn't write changed graph IDs to " + m.changedGraphPath);
                e.printStackTrace();
            }
        }


        if (m.newTrees) {
            m.computeAMTrees();
            if (m.outputPath != null) {
                m.writeAMConll();
            }
        }

    }

    public static MakeAMConllFromAltoCorpus readCommandLine(String[] args) {
        MakeAMConllFromAltoCorpus m = new MakeAMConllFromAltoCorpus();
        JCommander commander = new JCommander(m);
        // commander.parse(args);
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occurred: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            System.exit(1);
        }
        if (m.help) {
            commander.usage();
            System.exit(0);
        }
        return m;
    }

    private void readAltoCorpus() throws IOException, CorpusReadingException {
        InterpretedTreeAutomaton loaderIRTG = createLoaderIRTG();
        Corpus altoCorpus = Corpus.readCorpusWithStrictFormatting(new FileReader(corpusPath), loaderIRTG);
        System.out.println("Read " + altoCorpus.getNumberOfInstances() + " aligned sentence-graph pairs.");
        turnGraphStringsIntoRootedGraphs(altoCorpus);
        convertToListOfMRInstancesAndStore(altoCorpus);
    }

    @NotNull
    private InterpretedTreeAutomaton createLoaderIRTG() {
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("graph", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("alignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("id", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        if (this.reentrancies) {
            loaderIRTG.addInterpretation("reentrantedges", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        }
        return loaderIRTG;
    }

    private void convertToListOfMRInstancesAndStore(Corpus altoCorpus) {
        corpus = new ArrayList<>();
        for (Instance inst : altoCorpus) {
            SGraph graph = (SGraph) inst.getInputObjects().get("actual_graph");
            List<Alignment> alignments = ((List<String>) inst.getInputObjects().get("alignment")).stream()
                    .map(Alignment::read).collect(Collectors.toList());
            List<String> sentence = (List<String>) inst.getInputObjects().get("string");
            MRInstance mrInst = new MRInstance(sentence, graph, alignments);
            mrInst.setExtra("original_graph_string", String.join(" ", ((List<String>) inst.getInputObjects().get("graph"))));
            if (this.reentrancies) {
                Set<GraphEdge> reentrantEdges = readReentrantEdges(inst, mrInst);
                mrInst.setExtra("reentrant_edges", reentrantEdges);
            }
            mrInst.setId(String.join(" ", ((List<String>) inst.getInputObjects().get("id"))));
            corpus.add(mrInst);
        }
    }

    @NotNull
    private Set<GraphEdge> readReentrantEdges(Instance inst, MRInstance mrInst) {
        Set<GraphEdge> reentrantEdges = new HashSet<>();
        for (String reentrantEdgeString : (List<String>) inst.getInputObjects().get("reentrantedges")) {
            String[] parts = reentrantEdgeString.split("->");
            GraphNode source = mrInst.getGraph().getNode(parts[0]);
            GraphNode target = mrInst.getGraph().getNode(parts[2]);
            Set<GraphEdge> candidateEdges = mrInst.getGraph().getGraph().getAllEdges(source, target);
            for (GraphEdge e : candidateEdges) {
                if (e.getLabel().equals(parts[1])) {
                    reentrantEdges.add(e);
                }
            }
        }
        return reentrantEdges;
    }

    private void turnGraphStringsIntoRootedGraphs(Corpus altoCorpus) {
        IsiAmrInputCodec codec = new IsiAmrInputCodec();
        for (Instance instance : altoCorpus) {
            String graphString = ((List<String>)instance.getInputObjects().get("graph")).stream().collect(Collectors.joining(" "));
            // this was throwing an error when used with an old namesDatesNumbers_AlsFixed_sorted.corpus file
            // TODO should it be here?
//            graphString = graphString.replaceFirst("/", "<"+ ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME + "> /");
            instance.getInputObjects().put("actual_graph", codec.read(graphString));
//            System.out.println("Read graph: " + graphString);
//            System.out.println("Resulting graph: " + instance.getInputObjects().get("actual_graph"));
//            System.out.println(((SGraph)instance.getInputObjects().get("actual_graph")).toIsiAmrStringWithSources());
        }
    }


    private void filterOutBadGraphsAndAlignments() {
        filterOutInstancesWithOverlappingAlignmentSpans();
        filterOutGraphsWithDisconnectedAlignmentSubgraphs();
        filterOutGraphsWithTooLargeConstants();
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
        System.out.println("Filtered out " + (corpus.size() - filteredCorpus.size()) + " instances with disconnected " +
                "constants based on alignments.");
        corpus = filteredCorpus;
    }

    /**
     * Large numbers of nodes in a single
     */
    private void filterOutGraphsWithTooLargeConstants() {
        List<MRInstance> filteredCorpus = new ArrayList<>();
        for (MRInstance mrInst : corpus) {
            boolean hasTooBigAlignment = false;
            for (Alignment al : mrInst.getAlignments()) {
                if (al.nodes.size() > 10) {
                    hasTooBigAlignment = true;
                    break;
                }
            }
            if (!hasTooBigAlignment) {
                filteredCorpus.add(mrInst);
            }
        }
        System.out.println("Filtered out " + (corpus.size() - filteredCorpus.size()) + " instances with too " +
                "large constants ( >8 nodes).");
        corpus = filteredCorpus;
    }

    private boolean hasDisconnectedAlignmentSubgraphs(MRInstance mrInst) {
        // TODO does this work?
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
     * Note this only fully works if alto file has a reentrantedges interpretation
     * @throws ParseException if edge unification or coref removal fails
     */
    private List<MRInstance> makeGraphsDecomposeable() {
        MutableInteger totalReentrantEdges = new MutableInteger(0);
        MutableInteger totalRemovedEdges = new MutableInteger(0);
        MutableInteger totalMovedEdges = new MutableInteger(0);
        UnifyInEdges unifyInEdges = new UnifyInEdges(new AMRBlobUtils());
        unifyInEdges.setVerbose(verbose);  // a lot of the printing is done by UnifyInEdges
        List<MRInstance> graphsWithMovedEdges = new ArrayList<>();
        for (MRInstance mrInst : corpus) {
            String originalGraph = mrInst.getGraph().toString();
            try {
                boolean changed;
                changed = unifyInEdges.runOnInstance(mrInst, totalMovedEdges);
                if (changed) {
                    graphsWithMovedEdges.add(mrInst);
                }
                if (this.reentrancies) {
                    removeNondecomposableCorefEdgesFromInstance(mrInst, totalReentrantEdges, totalRemovedEdges);
                }

            } catch (Exception e) {
                System.err.println(originalGraph);
                System.err.println("Original graph directly above");
                System.err.println("Error while processing instance " + mrInst.getId() );
                System.err.println("Sentence: " + String.join(" ", mrInst.getSentence()));
                e.printStackTrace();
                System.err.println("This may yield a non-decomposeable graph down the line.");
            }
        }
        System.out.println("Total moved in-edges: " + totalMovedEdges.getValue());
        if (this.reentrancies) {
            System.out.println("Removed " + totalRemovedEdges + " edges out of " + totalReentrantEdges
                    + " total reentrant edges.");
        }
        System.out.println("number of graphs with moved edges: " + graphsWithMovedEdges.size());
        return graphsWithMovedEdges;
    }

    private void removeNondecomposableCorefEdgesFromInstance(MRInstance mrInst,
                                                             MutableInteger totalReentrantEdges,
                                                             MutableInteger totalRemovedEdges)
            throws ParseException {
        // this is faster than the process later, so we can use a shorter timeout
        SplitCoref splitCoref = new SplitCoref(signatureBuilder, mrInst.getAlignments(), timeout*1000/3);
        SGraph graph = mrInst.getGraph();
        int edges_before = graph.getGraph().edgeSet().size();
        // small note to self: this splitCoref does not take alignments into account. So it is OK that it uses the
        // simpler AMRSignatureBuilder class, but it may leave some graphs non-decomposeable.
        Function<GraphEdge, Double> edgeScorer = splitCoref.scoreEdgesWithAlignmentsAndPreferredRemovals(graph,
                mrInst.getAlignments(), (Set<GraphEdge>)mrInst.getExtra("reentrant_edges"));
        mrInst.setGraph(splitCoref.scoreBasedSplit(graph, edgeScorer, totalReentrantEdges));
        int edges_after = mrInst.getGraph().getGraph().edgeSet().size();
        totalRemovedEdges.setValue(totalRemovedEdges.getValue() + edges_before - edges_after);
    }

    private void computeAMTrees() throws ParserException, IOException {
        MutableInteger i = new MutableInteger(0);
        MutableInteger successCount = new MutableInteger(0);

        // create empty supertag dictionary, and load from file if file exists
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        File supertagDictionaryFile = new File(supertagDictionaryPath);
        if (supertagDictionaryFile.exists()) {
            System.out.println("Loading supertag dictionary from file: " + supertagDictionaryPath);
            supertagDictionary.readFromFile(supertagDictionaryPath);
        }

        System.out.println();//just making a line to overwrite later

        // c.f. https://stackoverflow.com/questions/21163108/custom-thread-pool-in-java-8-parallel-stream/22269778#22269778
        ForkJoinPool forkJoinPool = null;
        try {
            forkJoinPool = new ForkJoinPool(nrThreads);

            forkJoinPool.submit(() ->
                corpus.stream().parallel().forEach(mrInst -> {
                    computeAndStoreAMSentence(i, successCount, supertagDictionary, mrInst);
                }
            )).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }

        if (supertagDictionaryFile.exists()) {
            System.out.print("\nWriting supertag dictionary to file: " + supertagDictionaryPath);
            supertagDictionary.writeToFile(supertagDictionaryPath);
            System.out.print("\nDone! Successes: " + successCount.getValue() + "/" + i + "\n");
        } else {
            System.err.println("Warning: no supertag dictionary written, since no path was given");
        }
    }

    private void computeAndStoreAMSentence(MutableInteger i, MutableInteger successCount, SupertagDictionary supertagDictionary, MRInstance mrInst) {
        try {
            TreeAutomaton auto;

            // this automaton isn't really being given graph types for the scorer. Is this a problem?
            auto = AlignmentTrackingAutomaton.create(mrInst,
                    signatureBuilder, false, (AMRSignatureBuilder::scoreGraphPassiveSpecial));
            auto.processAllRulesBottomUp(null, timeout*1000);

            // write the automaton constants to a file for debugging
//                Path folderPath = Paths.get(outputPath).toAbsolutePath().getParent();
//                FileWriter automatonWriter =  new FileWriter(folderPath + "/constants_" + i + ".txt");
//                for (int j = 0; j<= auto.getSignature().getMaxSymbolId(); j++) {
//                    String symbol = auto.getSignature().resolveSymbolId(j);
//                    if (symbol != null && auto.getSignature().getArity(j) == 0) {
//                        automatonWriter.write(symbol + "\n");
//                    }
//                }
//                automatonWriter.close();

            Tree<String> vit = auto.viterbi();
            //System.err.println(vit);
            if (vit != null) {
                synchronized (this) {
                    successCount.incValue();
                }
                AmConllSentence amConllSentence;
                synchronized (this) {
                    amConllSentence = AmConllSentence.fromIndexedAMTerm(vit, mrInst, supertagDictionary);
                }
                amConllSentence = condenseMultiwordAlignmentSpans(amConllSentence, mrInst.getAlignments());
                amConllSentence.setId(mrInst.getId());
                amConllSentence.setAttr("original_graph_string", (String) mrInst.getExtra("original_graph_string"));
                synchronized(amConllSentences) {
                    amConllSentences.add(amConllSentence);
                }
            } else {
                System.err.println("\nCould not decompose instance " + mrInst.getSentence().stream().collect(Collectors.joining(" ")));
                System.err.println("Graph: " + mrInst.getGraph().toString());
                System.err.println("Original graph string: " + mrInst.getExtra("original_graph_string"));
            }
            synchronized (this) {
                i.incValue();
                System.out.println("\rSuccesses: " + successCount.getValue() + "/" + i.getValue());
            }
        } catch (Exception | Error ex) {
            System.err.println(mrInst.getId());
            ex.printStackTrace();
            System.err.println(mrInst.getGraph().toString());
        }
    }


    private static AmConllSentence condenseMultiwordAlignmentSpans(AmConllSentence amConllSentence, List<Alignment> alignments) {
        AmConllSentence ret = new AmConllSentence();
        Set<Integer> skipped_indices = new HashSet<>();
        for (int i = 0; i < amConllSentence.size(); i++) {
            AmConllEntry entry = amConllSentence.get(i);
            Alignment alignmentThatStartsHere = null;
            for (Alignment alignment : alignments) {
                if (alignment.span.start == i) {
                    alignmentThatStartsHere = alignment;
                    break;
                }
            }
            if (alignmentThatStartsHere != null && alignmentThatStartsHere.span.end -alignmentThatStartsHere.span.start > 1) {
                for (int j = alignmentThatStartsHere.span.start+1; j< alignmentThatStartsHere.span.end; j++) {
                    skipped_indices.add(j); // 0-based
                }
                StringBuilder newForm = new StringBuilder(entry.getForm());
                for (int j = alignmentThatStartsHere.span.start + 1; j < alignmentThatStartsHere.span.end; j++) {
                    newForm.append(" ").append(amConllSentence.get(j).getForm());
                }
                AmConllEntry newEntry = new AmConllEntry(ret.size(), newForm.toString());
                copyExtras(entry, newEntry);
                ret.add(newEntry);
                i = alignmentThatStartsHere.span.end - 1; // -1 because the for loop adds 1 in the end
            } else {
                AmConllEntry newEntry = new AmConllEntry(ret.size(), entry.getForm());
                copyExtras(entry, newEntry);
                ret.add(newEntry);
            }
        }
        // correct the incoming edge indices
        for (int i = 0; i < ret.size(); i++) {
            AmConllEntry entry = ret.get(i);
            int oldIndex = entry.getHead();
            int newIndex = oldIndex - (int)skipped_indices.stream().filter(j -> j < oldIndex).count();
            entry.setHead(newIndex);
        }
        return ret;
    }

    private static void copyExtras(AmConllEntry from, AmConllEntry to) {
        to.setAligned(from.isAligned());
        to.setDelexSupertag(from.getDelexSupertag());
        to.setLexLabel(from.getLexLabel());
        to.setHead(from.getHead());
        to.setEdgeLabel(from.getEdgeLabel());
        to.setRange(from.getRange());
        to.setType(from.getType());
        to.setNe(from.getNe());
        to.setPos(from.getPos());
        to.setLemma(from.getLemma());
        to.setReplacement(from.getReplacement());
    }

    private void writeAMConll() throws IOException {
        FileWriter writer = new FileWriter(outputPath);
        System.out.println("Writing a total of " +amConllSentences.size() + " AM trees to " + outputPath);
        AmConllSentence.write(writer, amConllSentences);
        writer.close();
    }

}