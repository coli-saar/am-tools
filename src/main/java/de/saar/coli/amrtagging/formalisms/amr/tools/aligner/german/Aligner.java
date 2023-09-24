/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.aligner.german;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.MrpPreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordPreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.wordnet.IWordnet;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import edu.stanford.nlp.ling.TaggedWord;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates alignments for AMRs, as used in 'AMR Dependency Parsing with a Typed
 * Semantic Algebra' (ACL 2018). Run with --help to see options.
 *
 * @author Jonas
 */
@SuppressWarnings("DuplicatedCode")
public class Aligner {


    @Parameter(names = {"--corpus", "-c"}, description = "Path to corpus", required = true)
    private String corpusPath;

    @Parameter(names = {"--outfile", "-o"}, description = "Path to output alignment file", required = true)
    private String alignmentPath;

    @Parameter(names = {"--posmodel", "-pos"}, description = "Path to POS tagger model", required = true)
    private String posModelPath;

    @Parameter(names = {"--mode", "-m"}, description = "Mode. Can be 'p' for probability align, 'ap' for all with probabilities; default is 'p'")
    private String mode = "p";

    @Parameter(names = {"--nodual"}, description = "Disallows multiple lex nodes in an alignment (as one would get for ellipsis in coordination or similar phenomena).")
    private boolean nodual = false;

    @Parameter(names = {"--accuracyWRTGold", "-acc"}, description = "If given, requires that the corpus has an `alignment` interpretation, and measures accuracy compared to those gold alignments.")
    private boolean accuracyWRTGold = false;

    @Parameter(names = {"--verbose", "-v"}, description = "Writes in this directory: alignments and scores when they are fixed/spread.")
    private String verboseDir = null;

    @Parameter(names = {"--max", "-max"}, description = "Maximum number of instances in the corpus considered (negative values = all instances; default=-1)")
    private int maxInstances = -1;

    @Parameter(names = {"--companion"}, description = "Path to MRP companion data (will disable builtin tokenization and POS tagging")
    private String companionDataFile = null;

    @Parameter(names = {"--help", "-?"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;



    int accCorrect = 0;
    int accTotal = 0;

    Object2DoubleMap al2Matchscore;

    private final Counter<String> unalignedLabelCounter;

    public Aligner() {
        unalignedLabelCounter = new Counter<>();
    }

    //    private static boolean dual = true;
    private boolean isDual() {
        return !nodual;
    }

    /**
     * Creates alignments for AMRs, as used in 'AMR Dependency Parsing with a
     * Typed Semantic Algebra' (ACL 2018). Run with --help to see options.
     *
     * @param args
     * @throws IOException
     * @throws CorpusReadingException
     * @throws MalformedURLException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, CorpusReadingException, MalformedURLException, InterruptedException {

        Aligner aligner = new Aligner();

        JCommander commander = new JCommander(aligner);
        commander.setProgramName("viz");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex);
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (aligner.help) {
            commander.usage();
            return;
        }

        aligner.align();
    }

    public void align() throws IOException, CorpusReadingException, InterruptedException {
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton<>());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation(new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig), "graph"));
        loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "string"));
        loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "id"));
        if (accuracyWRTGold) {
            loaderIRTG.addInterpretation(new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "alignment"));
        }
        Corpus corpus = Corpus.readCorpus(new FileReader(corpusPath), loaderIRTG);

        IWordnet we = new IWordnet.DummyIWordnet();
        
        PreprocessedData preprocData;
        if (companionDataFile != null) {
            preprocData = new MrpPreprocessedData(new File(companionDataFile));
        } else {
            preprocData = new StanfordPreprocessedData(posModelPath);
            ((StanfordPreprocessedData) preprocData).readTokenizedFromCorpus(corpus);
        }

        Writer alignmentWriter = new FileWriter(alignmentPath);

        int i = 0;
        for (Instance inst : corpus) {
            if (i >= maxInstances && !(maxInstances < 0)) {
                break;
            }
            try {
                List<String> sent = (List) inst.getInputObjects().get("string");
                SGraph graph = (SGraph) inst.getInputObjects().get("graph");

                List<String> ids = (List) inst.getInputObjects().get("id");
                String id = ids.get(0);

                List<Alignment> goldAligments = null;
                if (accuracyWRTGold) {
                    goldAligments = ((List<String>)inst.getInputObjects().get("alignment")).stream().map(Alignment::read).collect(Collectors.toList());
                }

                if (graph == null) {
                    alignmentWriter.write("\n");
                } else {

//                    Counter<String> nnCounter = new Counter<>();
//                    Counter<Integer> wordCounter = new Counter<>();
//                    Set<TokenAlignment> alignments = null;

                    List<TaggedWord> posTags = preprocData.getPosTags(id);

                    switch (mode) {
                        case "p":
                            alignmentWriter.write(probabilityAlign(graph, sent, i, we, posTags, goldAligments) + "\n");
                            break;
                        case "ap":
                            alignmentWriter.write(allProbableAlign(graph, sent, i, we, posTags) + "\n");
                            break;
                    }

                }
            } catch (Exception ex) {
                alignmentWriter.write("\n");
                System.err.println(i);
                ex.printStackTrace();
            }
            i++;
        }

        if (accuracyWRTGold) {
            System.out.println("Accuracy: " + ((double)accCorrect) / accTotal);
        }

        alignmentWriter.close();
        unalignedLabelCounter.printAllSorted();
    }

    /**
     * The central function. First creates a list of candidate alignments, and
     * then proceeds to perform the highest-scoring action (can be fixing a
     * candidate alignment, or spreading a fixed alignment to another node).
     * Satisfies the constraints that every word and every node can participate
     * in at most one alignment, and tries to align as many nodes as possible.
     *
     * @param graph
     * @param sent
     * @param instanceIndex
     * @param we
     * @param posTags
     * @param goldAligments May be null. If not null, accuracy compared to these gold alignments is computed.
     * @return
     * @throws IOException
     */
    private String probabilityAlign(SGraph graph, List<String> sent, int instanceIndex, IWordnet we,
                                    List<TaggedWord> posTags, Collection<Alignment> goldAligments) throws IOException {

        Writer verboseWriter = null;
        if (verboseDir != null) {
            verboseWriter = new FileWriter(verboseDir + instanceIndex + ".verbose");
        }
        Map<String, Alignment> nn2fixedAlign = new HashMap<>();//use valueSet as set of all alignments
        IntSet coveredIndices = new IntOpenHashSet();
        Pair<Map<String, Set<Alignment>>, Set<Alignment>> candidateData = CandidateMatcher.findCandidatesForProb(graph, sent, posTags, we);
        Map<String, Set<Alignment>> nn2candidates = candidateData.left;//has one to one correspondence between node names and candidates
        //to be more precise: in one set, all alignments have the same nodes (vary only in span). One of the nodes is chosen as the key. The set of these sets
        //partitions the set of all graph nodes.
        Set<Set<String>> coordinationTuples = Util.coordinationTuples(graph);
        //System.err.println(coordinationTuples);
        Set<String> unalignedNns = new HashSet<>(nn2candidates.keySet());//only need to align these
        Set<String> nnsAlignedBySpread = new HashSet<>();

        while (!unalignedNns.isEmpty()) {
            //round 1
            Map<String, List<Pair<Object, Double>>> nn2objsAndScores = new HashMap<>();
            Map<String, List<Pair<Object, Double>>> nn2objsAndProbs = new HashMap<>();
            Map<String, Set<Pair<Alignment.Span, Double>>> dummyNn2scoredCandidate = new HashMap<>();
            nn2fixedAlign.entrySet().stream().forEach(entry -> dummyNn2scoredCandidate.put(entry.getKey(), Collections.singleton(new Pair<>(entry.getValue().span, 1.0))));
            List<Pair<Pair<String, Object>, Double>> nnsAndBestAndScore = new ArrayList<>();
            for (String nn : unalignedNns) {
                List<Pair<Object, Double>> objsAndScores = new ArrayList<>();//contains either alignments, or pairs of nodenames as extensions (from, to).
                GraphNode node = graph.getNode(nn);
                //new alignments
                Set<Alignment> candidates = nn2candidates.get(nn);
                for (Alignment c : candidates) {
                    objsAndScores.add(new Pair<>(c, c.getWeight()
                            * Math.exp(AlignmentScorer.extendingNeighborScoreForProb(node, c.span, graph, posTags, nn2fixedAlign, dummyNn2scoredCandidate, we))));
                }

                //expansions
                if (candidates.isEmpty() || candidates.stream().map(al -> al.nodes.size()).max(Comparator.naturalOrder()).get() == 1) {
                    //only consider expansions to this node, if this is a normal alignment, not a special one with multiple nodes
                    //(this technically allows expansions to 'multi-sentence' nodes and pronouns, but that's alright I think. Mostly names, dates ((and verbalizations)--depr) are important.)
                    for (GraphEdge edge : graph.getGraph().edgesOf(node)) {
                        GraphNode other = GeneralBlobUtils.otherNode(node, edge);
                        Set<Pair<TaggedWord, Double>> alignedWordAndProb = new HashSet<>();
                        if (nn2fixedAlign.containsKey(other.getName())) {
                            Alignment.Span span = nn2fixedAlign.get(other.getName()).span;
                            if (span.isSingleton()) {
                                alignedWordAndProb.add(new Pair<>(posTags.get(span.start), 1.0));//can also extend non-singletons, but this score is only relevant if span is singleton (I think)
                            }
                        }
                        double score = AlignmentExtender.scoreExtension(other, edge, node, alignedWordAndProb, graph, we);
                        if (score > 0) {
                            if (nn2fixedAlign.containsKey(other.getName())) {
                                score *= Math.exp(AlignmentScorer.extendingNeighborScoreForProb(node, nn2fixedAlign.get(other.getName()).span,
                                        graph, posTags, nn2fixedAlign, dummyNn2scoredCandidate, we));
                            }
                            objsAndScores.add(new Pair<>(new Pair<>(other.getName(), nn), score));
                        }
                    }
                    if (isDual()) {
                        for (GraphNode dual : graph.getGraph().vertexSet()) {
                            if (Util.areCompatibleForDualAlignment(nn, dual.getName(), coordinationTuples)
                                    && !nnsAlignedBySpread.contains(dual.getName())) {
                                double score = AlignmentScorer.SCP_EXTENSION;
                                if (nn2fixedAlign.containsKey(dual.getName())) {
                                    score *= Math.exp(AlignmentScorer.extendingNeighborScoreForProb(node, nn2fixedAlign.get(dual.getName()).span,
                                            graph, posTags, nn2fixedAlign, dummyNn2scoredCandidate, we));
                                }
                                objsAndScores.add(new Pair<>(new Pair<>(dual.getName(), nn), score));
                            }
                        }
                    }
                }
                objsAndScores.removeIf((Pair<Object, Double> p) -> {
                    if (p.left instanceof Pair) {
                        return false;
                    } else {
                        Alignment al = (Alignment) p.left;
                        return Util.containsOne(coveredIndices, al.span);
                    }
                });
                //take total sum after removing no longer possible alignments, but before removing not yet possible extensions
                double totalSum = objsAndScores.stream().map(p -> p.right).mapToDouble(d -> d).sum();
                objsAndScores.removeIf((Pair<Object, Double> p) -> {
                    if (p.left instanceof Pair) {
                        Pair<String, String> fromAndTo = (Pair) p.left;
                        return !nn2fixedAlign.containsKey(fromAndTo.left);
                    } else {
                        return false;
                    }
                });
//                if (objsAndScores.isEmpty()) {
//                    nnsAndBestAndScore.add(new Pair<>(new Pair<>(nn, null), Double.MIN_VALUE));
//                } else {
//                    
//                    objsAndScores.sort((Pair<Object, Double> o1, Pair<Object, Double> o2) -> -Double.compare(o1.right, o2.right));
//                    nnsAndBestAndScore.add(new Pair<>(new Pair<>(nn, objsAndScores.get(0).left), objsAndScores.get(0).right*objsAndScores.get(0).right/totalSum));//
//                }
                nn2objsAndScores.put(nn, objsAndScores);
                nn2objsAndProbs.put(nn, objsAndScores.stream().map(new Function<Pair<Object, Double>, Pair<Object, Double>>() {
                    @Override
                    public Pair<Object, Double> apply(Pair<Object, Double> p) {
                        return new Pair<>(p.left, p.right / totalSum);//TODO maybe use softmax instead? would allow use of negative scores
                    }
                }).collect(Collectors.toList()));
            }

            //round 2
            Map<String, List<Pair<Object, Double>>> debug_nn2objsAndScores = new HashMap<>();
            Map<String, List<Pair<Object, Double>>> debug_nn2objsAndProbs = new HashMap<>();
            Object2DoubleMap<Alignment.Span> span2sum = new Object2DoubleOpenHashMap<>();//default return is 0
            Set<Alignment.Span> allSpans = new HashSet<>();
            for (String nn : unalignedNns) {
                Set<Alignment> candidatesHere = nn2candidates.get(nn);
                if (candidatesHere != null) {
                    for (Alignment c : candidatesHere) {
                        allSpans.add(c.span);
                    }
                }
            }
            for (Alignment.Span span : allSpans) {
                for (String nn : unalignedNns) {
                    Iterator<Pair<Object, Double>> itScore = nn2objsAndScores.get(nn).iterator();
                    Iterator<Pair<Object, Double>> itProb = nn2objsAndProbs.get(nn).iterator();
                    while (itScore.hasNext() && itProb.hasNext()) {
                        Pair<Object, Double> s = itScore.next();
                        Pair<Object, Double> p = itProb.next();
                        if (s.left instanceof Alignment) {
                            Alignment.Span spanHere = ((Alignment) s.left).span;
                            if (span.overlaps(spanHere)) {
                                span2sum.put(span, span2sum.getDouble(span) + s.right * p.right);
                            }
                        }
                    }
                    if (itScore.hasNext() || itProb.hasNext()) {
                        System.err.println("self-test failed: unequal lengths of score and probability iterators!");
                    }
                }
            }
            for (String nn : unalignedNns) {
                List<Pair<Object, Double>> objsAndScores = new ArrayList<>();//contains either alignments, or pairs of nodenames as extensions (from, to).
                Iterator<Pair<Object, Double>> itScore = nn2objsAndScores.get(nn).iterator();
                Iterator<Pair<Object, Double>> itProb = nn2objsAndProbs.get(nn).iterator();
                while (itScore.hasNext() && itProb.hasNext()) {
                    Pair<Object, Double> s = itScore.next();
                    Pair<Object, Double> p = itProb.next();
                    if (s.left instanceof Alignment) {
                        Alignment al = (Alignment) s.left;
                        double spanNormalizer = span2sum.getDouble(al.span);
                        if (spanNormalizer < 0.0000000000001) {
                            objsAndScores.add(new Pair<>(s.left, 0.0));
                        } else {
                            objsAndScores.add(new Pair<>(s.left, s.right * (s.right * p.right / spanNormalizer)));
                        }
                    } else {
                        objsAndScores.add(s);
                    }
                }
                double totalSum = objsAndScores.stream().map(p -> p.right).mapToDouble(d -> d).sum();
                //now remove the extensions we can't do yet. Also remove the expansions that are invalid due to AM algebra blob constraints (single root etc)
                objsAndScores.removeIf((Pair<Object, Double> p) -> {
                    if (p.left instanceof Pair) {
                        Pair<String, String> fromAndTo = (Pair) p.left;
                        if (!nn2fixedAlign.containsKey(fromAndTo.left)) {
                            return true;//remove extensions for nodes that are not yet fixed aligned
                        } else {
                            GraphNode v1 = graph.getNode(fromAndTo.left);
                            GraphNode v2 = graph.getNode(fromAndTo.right);
                            //remove if spread via neighbour, and if spread is not allowed due to AM alg blob constraints (i.e. allow violation of constraints for coord dual spreads)
                            //TODO think this through properly, when rethinking coord dual stuff in general.
                            return (graph.getGraph().getEdge(v1, v2) != null || graph.getGraph().getEdge(v2, v1) != null)
                                    && !AlignmentExtender.isSpreadFineWithBlobs(nn2fixedAlign.get(fromAndTo.left).nodes, fromAndTo.right, graph);
                        }
                    } else {
                        return false;
                    }
                });
                if (objsAndScores.isEmpty()) {
                    nnsAndBestAndScore.add(new Pair<>(new Pair<>(nn, null), -1.0));
                } else {
                    objsAndScores.sort((Pair<Object, Double> o1, Pair<Object, Double> o2) -> -Double.compare(o1.right, o2.right));
                    nnsAndBestAndScore.add(new Pair<>(new Pair<>(nn, objsAndScores.get(0).left), objsAndScores.get(0).right * (objsAndScores.get(0).right / totalSum)));
                }
                debug_nn2objsAndScores.put(nn, objsAndScores);
                debug_nn2objsAndProbs.put(nn, objsAndScores.stream().map(new Function<Pair<Object, Double>, Pair<Object, Double>>() {
                    @Override
                    public Pair<Object, Double> apply(Pair<Object, Double> p) {
                        return new Pair<>(p.left, p.right / totalSum);//TODO maybe use softmax instead? would allow use of negative scores
                    }
                }).collect(Collectors.toList()));
            }

            nnsAndBestAndScore.sort((Pair<Pair<String, Object>, Double> o1, Pair<Pair<String, Object>, Double> o2) -> {
                return -Double.compare(o1.right, o2.right);
            });
            Pair<Pair<String, Object>, Double> bestP = nnsAndBestAndScore.get(0);
            if (bestP.right <= 0) {//was == Double.MIN_VALUE
//                System.err.println("Could not align all nodes in graph "+instanceIndex);
                for (String nn : unalignedNns) {
                    unalignedLabelCounter.add(graph.getNode(nn).getLabel());
                }
//                System.err.println(unalignedNns);
                break;
            } else {
                String nn = bestP.left.left;
                Object action = bestP.left.right;
                if (action instanceof Pair) {
                    Pair<String, String> fromAndTo = (Pair) action;
                    Alignment extendee = nn2fixedAlign.get(fromAndTo.left);//we checked above that this key exists
                    if (!fromAndTo.right.equals(nn)) {
                        System.err.println("Self-test failed: wrong extension format!!");
                    }
                    extendee.nodes.add(nn);
                    if (graph.getNode(nn).getLabel().equals(graph.getNode(fromAndTo.left).getLabel())
                            && extendee.lexNodes.contains(fromAndTo.left)) {
                        extendee.lexNodes.add(nn);
                    }
                    nn2fixedAlign.put(nn, extendee);
                    unalignedNns.remove(nn);
                    nnsAlignedBySpread.add(nn);
                    if (verboseWriter != null) {
                        verboseWriter.write("Extended " + fromAndTo.left + " to " + fromAndTo.right + "; Score " + bestP.right + "\n");
                        verboseWriter.write("Round 1 Scores: " + nn2objsAndScores.get(nn) + "\n");
                        verboseWriter.write("Round 1 Probs: " + nn2objsAndProbs.get(nn) + "\n");
                        verboseWriter.write("Round 2 Scores: " + debug_nn2objsAndScores.get(nn) + "\n");
                        verboseWriter.write("Round 2 Probs: " + debug_nn2objsAndProbs.get(nn) + "\n");
                        verboseWriter.write("\n");
                    }
                } else {
                    Alignment al = (Alignment) action;
                    for (String alignedNn : al.nodes) {
                        nn2fixedAlign.put(alignedNn, al);
                        unalignedNns.remove(alignedNn);
                    }
                    for (int i = al.span.start; i < al.span.end; i++) {
                        coveredIndices.add(i);
                    }
                    if (verboseWriter != null) {
                        verboseWriter.write("Added alignment " + al + "; Score " + bestP.right + "\n");
                        verboseWriter.write("Base score: " + al.getWeight()
                                + "; Neighbor score: " + AlignmentScorer.extendingNeighborScoreForProb(graph.getNode(nn), al.span, graph,
                                posTags, nn2fixedAlign, dummyNn2scoredCandidate, we) + "\n");
                        verboseWriter.write("Round 1 Scores: " + nn2objsAndScores.get(nn) + "\n");
                        verboseWriter.write("Round 1 Probs: " + nn2objsAndProbs.get(nn) + "\n");
                        verboseWriter.write("Round 2 Scores: " + debug_nn2objsAndScores.get(nn) + "\n");
                        verboseWriter.write("Round 2 Probs: " + debug_nn2objsAndProbs.get(nn) + "\n");
                        verboseWriter.write("\n");
                    }
                }
            }
        }

        if (verboseWriter != null) {
            verboseWriter.close();
        }

        if (goldAligments != null) {
            Map<String, Alignment> nn2goldAlign = new HashMap<>();
            for (Alignment al : goldAligments) {
                for (String nn : al.nodes) {
                    nn2goldAlign.put(nn, al);
                }
            }
            int goldAlignedNodeCount = nn2goldAlign.size();
            int predictedAlignedNodeCount = nn2fixedAlign.size();
            if (goldAlignedNodeCount < graph.nodeCount()) {
                System.err.println("WARNING: Gold alignments do not seem to cover all nodes!");
                System.err.println("Graph: " + graph.toIsiAmrString());
                System.err.println("Gold alignments: " + goldAligments);
                System.err.println("Gold aligned nodes: " + nn2goldAlign.keySet());
            }
            if (predictedAlignedNodeCount < graph.nodeCount()) {
                System.err.println("WARNING: Predicted alignments do not seem to cover all nodes!");
//                System.err.println("Graph: " + graph.toIsiAmrString());
//                System.err.println("Predicted alignments: " + nn2fixedAlign.values());
//                System.err.println("Predicted aligned nodes: " + nn2fixedAlign.keySet());
            }

            for (String nn : nn2goldAlign.keySet()) {
                Alignment predicted = nn2fixedAlign.get(nn);
                Alignment gold = nn2goldAlign.get(nn);
                accTotal++;
                if (predicted == null) {
                    System.err.println("WARNING: Predicted alignment " + predicted + " for node " + nn + " not found.");
                } else {
                    if (predicted.span.start == gold.span.start && predicted.span.end == gold.span.end) {
                        accCorrect++;
                    }
                }
            }

        }

        StringJoiner sj = new StringJoiner(" ");
        for (Alignment al : new HashSet<>(nn2fixedAlign.values())) {
            //now simply collect all fixed matches
            sj.add(al.toString());
        }
        return sj.toString();
    }

    /**
     * Same as probabilityAlign, but produces ALL possible alignments obtainable
     * from the candidate alignments and reasonable spreads. Thus does not
     * satisfy the constraint that every node and word can participate in at
     * most one alignment.
     *
     * @param graph
     * @param sent
     * @param instanceIndex
     * @param we
     * @return
     * @throws IOException
     */
    private String allProbableAlign(SGraph graph, List<String> sent, int instanceIndex,
                                    IWordnet we, List<TaggedWord> posTags) {

//        List<TaggedWord> tags = tagger.apply(sent.stream().map(word -> new Word(word)).collect(Collectors.toList()));
        Set<Alignment> candidates = CandidateMatcher.findCandidatesForProb(graph, sent, posTags, we).right;

        Set<Set<String>> coordinationTuples = Util.coordinationTuples(graph);

        Set<Alignment> ret = new HashSet<>();

        for (Alignment seed : candidates) {

            Queue<Alignment> agenda = new LinkedList<>();
            Set<Alignment> seen = new HashSet<>();
            agenda.add(seed);
            seen.add(seed);
            while (!agenda.isEmpty()) {
                Alignment current = agenda.poll();
                Map<String, Set<GraphNode>> duals = new HashMap<>();
                for (String nn : current.nodes) {
                    GraphNode node = graph.getNode(nn);
                    for (GraphEdge edge : graph.getGraph().edgesOf(node)) {
                        GraphNode other = GeneralBlobUtils.otherNode(node, edge);
                        Set<Pair<TaggedWord, Double>> alignedWordAndProb = new HashSet<>();
                        Alignment.Span span = current.span;
                        if (span.isSingleton()) {
                            alignedWordAndProb.add(new Pair<>(posTags.get(span.start), 1.0));//can also extend non-singletons, but this score is only relevant if span is singleton (I think)
                        }
                        double score = AlignmentExtender.scoreExtension(node, edge, other, alignedWordAndProb, graph, we);
                        if (current.getWeight() * score > 0.01 && AlignmentExtender.isSpreadFineWithBlobs(current.nodes, other.getName(), graph)) {
                            //System.err.println("spreading from "+nn+" to "+other.getName()+" ("+score+")");
                            Alignment extendee = new Alignment(current.nodes, current.span, current.lexNodes, current.color, current.getWeight() * score);
                            extendee.nodes.add(other.getName());
                            if (!seen.contains(extendee)) {
                                seen.add(extendee);
                                agenda.add(extendee);
                            }
                        }
                    }
                    //collect coord duals candidates
                    if (isDual()) {
                        Set<GraphNode> dualsHere = new HashSet<>();
                        for (GraphNode dual : graph.getGraph().vertexSet()) {
                            if (Util.areCompatibleForDualAlignment(nn, dual.getName(), coordinationTuples)) {
                                dualsHere.add(dual);
                            }
                        }
                        duals.put(nn, dualsHere);
                    }
                }
                //check whether the dual sets have equal sizes, and are non-empty
                //only then add them, and add them all at once!
                Set<Integer> sizes = duals.values().stream().map(set -> set.size()).collect(Collectors.toSet());
                if (sizes.size() == 1 && sizes.iterator().next() != 0) {
                    double score = AlignmentScorer.SCP_EXTENSION;
                    Alignment extendee = new Alignment(current.nodes, current.span, current.lexNodes, current.color, current.getWeight() * score);
                    for (String nn : duals.keySet()) {
                        Set<GraphNode> dualsHere = duals.get(nn);
                        for (GraphNode dual : dualsHere) {
                            extendee.nodes.add(dual.getName());
                            if (current.lexNodes.contains(nn)) {
                                extendee.lexNodes.add(dual.getName());
                            }
                        }
                    }
                    if (!seen.contains(extendee)) {
                        seen.add(extendee);
                        agenda.add(extendee);
                    }
                }

                if (seen.size() > 1000) {
                    System.err.println("Too many alignments! " + instanceIndex);
                    break;
                }

            }
            ret.addAll(seen);
        }
        StringJoiner sj = new StringJoiner(" ");
        for (Alignment al : ret) {
            //now simply collect all fixed matches
            sj.add(al.toString());
        }
        return sj.toString();
    }


    public void setCorpusPath(String corpusPath) {
        this.corpusPath = corpusPath;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
