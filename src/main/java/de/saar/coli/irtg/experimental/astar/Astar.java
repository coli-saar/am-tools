/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.Util;
import de.up.ling.irtg.algebra.Algebra;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.saar.coli.irtg.experimental.astar.TypeInterner.AMAlgebraTypeInterner;
import de.up.ling.irtg.siblingfinder.SiblingFinder;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.ArrayMap;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.UnsupportedLookAndFeelException;
import me.tongfei.progressbar.ProgressBar;

/**
 *
 * @author koller
 */
public class Astar {

    static final double FAKE_NEG_INFINITY = -1000000;
    private boolean declutterAgenda = false; // previously dequeued items will never be enqueued again

    private int N;
    private final EdgeProbabilities edgep;
    private final SupertagProbabilities tagp;
    private final OutsideEstimator outside;
    private final Int2ObjectMap<Pair<SGraph, Type>> idToSupertag;
    private final Interner<String> supertagLexicon;
    private final Interner<String> edgeLabelLexicon;
    private final AMAlgebraTypeInterner typeLexicon;
    private RuntimeStatistics runtimeStatistics = null;
    private final Int2IntMap supertagTypes;
    private Consumer<String> logger;

    public Astar(EdgeProbabilities edgep, SupertagProbabilities tagp, Int2ObjectMap<Pair<SGraph, Type>> idToAsGraph, Interner<String> supertagLexicon, Interner<String> edgeLabelLexicon, AMAlgebraTypeInterner typeLexicon) {
        logger = (s) -> System.err.println(s);  // by default, log to stderr
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        w.record();

        this.edgep = edgep;
        this.tagp = tagp;
        this.idToSupertag = idToAsGraph;
        this.edgeLabelLexicon = edgeLabelLexicon;
        this.supertagLexicon = supertagLexicon;
        this.N = tagp.getLength();              // sentence length

        // create type interner for the supertags in tagp
        Set<Type> types = new HashSet<>();
        for (int i = 0; i < tagp.getLength(); i++) {
            tagp.foreachInOrder(i, (id, prob) -> {
                types.add(idToAsGraph.get(id).right);
            });
        }

        w.record();
        this.typeLexicon = typeLexicon; // new AMAlgebraTypeInterner(types, edgeLabelLexicon);  // <--- TODO: this is expensive for some reason
        w.record();
        this.outside = new StaticOutsideEstimator(edgep, tagp);

        w.record();
        // precompute supertag types
        supertagTypes = new Int2IntOpenHashMap();
        for (int supertagId : idToSupertag.keySet()) {
            supertagTypes.put(supertagId, typeLexicon.resolveObject(idToSupertag.get(supertagId).right));
        }

        w.record();
//        w.printMilliseconds("prep", "typelex", "outside", "supertag types");

//        System.err.println("\nSUPERTAGS:");
//        this.tagp.prettyprint(this.idToSupertag, System.out);
    }

    public void setDeclutterAgenda(boolean declutterAgenda) {
        this.declutterAgenda = declutterAgenda;
    }

    public void setBias(double bias) {
        outside.setBias(bias);
    }

    public RuntimeStatistics getRuntimeStatistics() {
        return runtimeStatistics;
    }

    private class RuntimeStatistics {

        long numDequeuedItems;
        long runtime;
        double score;

        public RuntimeStatistics(long numDequeuedItems, long runtime, double score) {
            this.numDequeuedItems = numDequeuedItems;
            this.runtime = runtime;
            this.score = score;
        }

        public double getScore() {
            return score;
        }

        public long getRuntime() {
            return runtime;
        }

        public long getNumDequeuedItems() {
            return numDequeuedItems;
        }

        @Override
        public String toString() {
            return String.format("length=%d, time=%dms, dequeued=%d, logprob=%f", N, runtime / 1000000, numDequeuedItems, score);
        }
    }

    private Item process() {
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        long numDequeuedItems = 0;

        w.record();

        Agenda agenda = new PriorityQueueAgenda(declutterAgenda);

        SiblingFinder[] siblingFinders = new SiblingFinder[edgeLabelLexicon.size()];  // siblingFinders[op] = type-sibling-finder for operation op, for all seen types
        for (int j : edgeLabelLexicon.getKnownIds()) {
            siblingFinders[j - 1] = typeLexicon.makeSiblingFinder(j);
        }

        Int2ObjectMap<Set<Item>>[] rightChart = new Int2ObjectMap[N + 1];      // rightChart[i].get(t) = all previously dequeued items that start at i with type-ID t
        Int2ObjectMap<Set<Item>>[] leftChart = new Int2ObjectMap[N + 1];       // leftChart[i].get(t) = all previously dequeued items that end at i with type-ID t
        for (int i = 0; i < N + 1; i++) {
            rightChart[i] = new Int2ObjectOpenHashMap<>();
            leftChart[i] = new Int2ObjectOpenHashMap<>();
        }

        // initialize agenda
        for (int i = 0; i < N; i++) {
            final int i_final = i;

            tagp.foreachInOrder(i, (supertagId, prob) -> {
                if (supertagId != tagp.getNullSupertagId()) { // skip NULL entries - NULL items are created on the fly during the agenda exploration phase
                    Item it = new Item(i_final, i_final + 1, i_final, getSupertagType(supertagId), prob);
                    it.setCreatedBySupertag(supertagId);
                    it.setOutsideEstimate(outside.evaluate(it));
                    agenda.enqueue(it);
                }
            });
        }

        // iterate over agenda
        while (!agenda.isEmpty()) {
            Item it = agenda.dequeue();

            if (it == null) {
                // emptied agenda without finding goal item
                w.record(); // agenda looping time
                runtimeStatistics = new RuntimeStatistics(numDequeuedItems, w.getTimeBefore(1), Double.NaN);
                return null;
            }

            numDequeuedItems++;

//            System.err.printf("[%5d] pop: %s\n", numDequeuedItems, it);
            // return first found goal item
            if (isGoal(it)) {
                w.record(); // agenda looping time

                runtimeStatistics = new RuntimeStatistics(numDequeuedItems, w.getTimeBefore(1), it.getLogProb());
                return it;
            }

            // add it to chart
            for (int edgeID : edgeLabelLexicon.getKnownIds()) {
                // TODO maybe better just fill the sibling finder with all possible states right from the start,
                // instead of doing all these spurious additinons (check profiling).
                siblingFinders[edgeID - 1].addState(it.getType(), 0);
                siblingFinders[edgeID - 1].addState(it.getType(), 1);
            }

            addItemToChart(rightChart[it.getStart()], it);
            addItemToChart(leftChart[it.getEnd()], it);

            // combine it with partners on the right
            for (int op : edgeLabelLexicon.getKnownIds()) {
                for (int[] types : siblingFinders[op - 1].getPartners(it.getType(), 0)) {
                    // here, 'it' is the functor and partner is the argument
                    int partnerType = types[1];

                    // items with matching types on the right
                    for (Item partner : (Set<Item>) rightChart[it.getEnd()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                        Item result = combineRight(op, it, partner);
                        assert result.getScore() <= it.getScore() + EPS : "[0R] Generated " + result + " from " + it;
                        agenda.enqueue(result);
                    }

                    // items with matching types on the left  *** GUT
                    for (Item partner : (Set<Item>) leftChart[it.getStart()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                        Item result = combineLeft(op, it, partner);
                        assert result.getScore() <= it.getScore() + EPS : "[0L] Generated " + result + " from " + it;
                        agenda.enqueue(result);
                    }
                }

                for (int[] types : siblingFinders[op - 1].getPartners(it.getType(), 1)) {
                    // here, 'it' is the argument and partner is the functor
                    int partnerType = types[0];

                    // items with matching types on the right     ** SCHLECHT
                    for (Item partner : (Set<Item>) rightChart[it.getEnd()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                        Item result = combineLeft(op, partner, it);
                        assert result.getScore() <= it.getScore() + EPS : "[1R] Generated " + result + " from " + it;
                        agenda.enqueue(result);
                    }

                    // items with matching types on the left
                    for (Item partner : (Set<Item>) leftChart[it.getStart()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                        Item result = combineRight(op, partner, it);
                        assert result.getScore() <= it.getScore() + EPS : "[1L] Generated " + result + " from " + it;
                        agenda.enqueue(result);
                    }
                }
            }

            // skip to the right
            if (it.getEnd() < N) {
                Item skipRight = makeSkipItem(it, it.getStart(), it.getEnd() + 1, it.getEnd());
                assert skipRight.getScore() <= it.getScore() + EPS;
                agenda.enqueue(skipRight);
            }

            // skip to the left
            if (it.getStart() > 0) {
                Item skipLeft = makeSkipItem(it, it.getStart() - 1, it.getEnd(), it.getStart() - 1);
                assert skipLeft.getScore() <= it.getScore() + EPS;
                agenda.enqueue(skipLeft);
            }
        }

        w.record();
        runtimeStatistics = new RuntimeStatistics(numDequeuedItems, w.getTimeBefore(1), Double.NaN);

        return null;
    }

    private static final double EPS = 1e-6;

    private Item makeSkipItem(Item originalItem, int newStart, int newEnd, int skippedPosition) {
        double nullProb = tagp.get(skippedPosition, tagp.getNullSupertagId()); // ID for NULL
        double newItemCost = originalItem.getLogProb() + nullProb;

        Item itemAfterSkip = new Item(newStart, newEnd, originalItem.getRoot(), originalItem.getType(), newItemCost);
        itemAfterSkip.setOutsideEstimate(outside.evaluate(itemAfterSkip));
        itemAfterSkip.setCreatedByOperation(-1, originalItem, null); // -1 is arbitrary, the thing that counts is that right=null

        return itemAfterSkip;
    }

    private void addItemToChart(Int2ObjectMap<Set<Item>> chart, Item item) {
        Set<Item> set = chart.get(item.getType());

        if (set == null) {
            set = new HashSet<>();
            chart.put(item.getType(), set);
        }

        set.add(item);
    }

    private Tree<String> decode(Item item, double logProbGoalItem, IntList leafOrderToStringOrder, MutableInteger nextLeafPosition) {
        double realOutside = logProbGoalItem - item.getLogProb();
//        System.err.printf("item: %s\n", item);

//        System.err.printf("%s -> logprob=%f, real_outside=%f, outside_estimate=%f\n", item.shortString(), item.getLogProb(), realOutside, item.getOutsideEstimate());
        if (realOutside > item.getOutsideEstimate() + EPS) {
            logger.accept(String.format("WARNING: Inadmissible estimate (realOutside=%f, item=%s).", realOutside, item.toString()));
        }

        if (item.getLeft() == null) {
            // leaf; decode op as supertag
            String supertag = supertagLexicon.resolveId(item.getOperation());
            leafOrderToStringOrder.set(nextLeafPosition.incValue(), item.getStart());
            return Tree.create(supertag);

            /*
            Pair<SGraph, Type> asGraph = idToSupertag.get(item.getOperation());

//            System.err.printf("           @%d: supertag=%d %s\n", item.getStart(), item.getOperation(), asGraph.left.toString());
            String graphS = asGraph.left.toIsiAmrStringWithSources();
            graphS = graphS.replace(DependencyExtractor.LEX_MARKER, "\"" + Parser.LEXMARKER_OUT + item.getStart() + "\"");

            leafOrderToStringOrder.set(nextLeafPosition.incValue(), item.getStart());
            return Tree.create(graphS + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + asGraph.right.toString());
             */
        } else if (item.getRight() == null) {
            // skip
//            System.err.printf("           @%d: NULL %s to %s, logp(skip)=%f\n", item.subtract(item.getLeft()).getStart(), item.getLeft().shortString(), item.shortString(), item.getLogProb() - item.getLeft().getLogProb());

            return decode(item.getLeft(), logProbGoalItem, leafOrderToStringOrder, nextLeafPosition);
        } else {
            // non-leaf; decode op as edge

//            System.err.printf("           %s --%s--> %s\n", item.getLeft().shortString(), edgeLabelLexicon.resolveId(item.getOperation()), item.getRight().shortString());
            Tree<String> left = decode(item.getLeft(), logProbGoalItem, leafOrderToStringOrder, nextLeafPosition);
            Tree<String> right = decode(item.getRight(), logProbGoalItem, leafOrderToStringOrder, nextLeafPosition);
            return Tree.create(edgeLabelLexicon.resolveId(item.getOperation()), left, right);
        }
    }

    ParsingResult decode(Item goalItem) {
        if (goalItem == null) {
            return null;
        } else {
//            System.err.println("goal item final score: " + goalItem.getLogProb());
//            System.err.println("goal item outside estimate (for sanity): " + goalItem.getOutsideEstimate());

            double goalItemLogProb = goalItem.getLogProb();
            IntList leafOrderToStringOrder = new IntArrayList(N);
            for (int i = 0; i < N; i++) {
                leafOrderToStringOrder.add(0);
            }

            Tree<String> amTerm = decode(goalItem, goalItemLogProb, leafOrderToStringOrder, new MutableInteger(0));

            return new ParsingResult(amTerm, goalItemLogProb, leafOrderToStringOrder);
        }
    }

    static class ParsingResult {

        public Tree<String> amTerm;
        public double logProb;
        public IntList leafOrderToStringOrder;

        public ParsingResult(Tree<String> amTerm, double logProb, IntList leafOrderToStringOrder) {
            this.amTerm = amTerm;
            this.logProb = logProb;
            this.leafOrderToStringOrder = leafOrderToStringOrder;
        }

        @Override
        public String toString() {
            return "ParsingResult{" + "amTerm=" + amTerm + ", logProb=" + logProb + ", leafOrderToStringOrder=" + leafOrderToStringOrder + '}';
        }

        
        
    }

    // check whether the item is a goal item
    private boolean isGoal(Item item) {
        return item.getStart() == 0 && item.getEnd() == N && typeLexicon.resolveID(item.getType()).keySet().isEmpty();
    }

    // combine functor with an argument on the right
    private Item combineRight(int op, Item functor, Item argument) {
        int t = combine(op, functor.getType(), argument.getType());

        assert functor.getEnd() == argument.getStart();

        double logEdgeProbability = edgep.get(functor.getRoot(), argument.getRoot(), op);
        Item ret = new Item(functor.getStart(), argument.getEnd(), functor.getRoot(), t, functor.getLogProb() + argument.getLogProb() + logEdgeProbability);
        ret.setCreatedByOperation(op, functor, argument);
        ret.setOutsideEstimate(outside.evaluate(ret));
        return ret;
    }

    // combine functor with an argument on the left
    private Item combineLeft(int op, Item functor, Item argument) {
        int t = combine(op, functor.getType(), argument.getType());

        assert functor.getStart() == argument.getEnd();

        double logEdgeProbability = edgep.get(functor.getRoot(), argument.getRoot(), op);
        Item ret = new Item(argument.getStart(), functor.getEnd(), functor.getRoot(), t, functor.getLogProb() + argument.getLogProb() + logEdgeProbability);
        ret.setCreatedByOperation(op, functor, argument);
        ret.setOutsideEstimate(outside.evaluate(ret));
        return ret;
    }

    // combine a functor and argument type using the given operation
    private int combine(int op, int functor, int argument) {
        return typeLexicon.combine(op, functor, argument);
    }

    public static interface Evaluable {

        public double getTotalValue();
    }

    private int getSupertagType(int supertagId) {
        return supertagTypes.get(supertagId);
    }

    private static class Edge {

        private int from, to;
        private String label;

        // o[i,j]
        public static Edge parse(String s) {
            String[] parts = s.split("\\[|\\]|,");
            Edge ret = new Edge();
            ret.label = parts[0];
            ret.from = Integer.parseInt(parts[1]);
            ret.to = Integer.parseInt(parts[2]);
            return ret;
        }

        @Override
        public String toString() {
            return "Edge{" + "from=" + from + ", to=" + to + ", label=" + label + '}';
        }
    }

    //    private IntSet ignorableEdgeLabels;
//    private Set<Item> itemsInBestParse = new HashSet<>();
//
//    private Set<Item> interestingItems;
//    private void setInterestingItems(Set<Item> usedIn41Parse) {
//        interestingItems = usedIn41Parse;
//    }
//    public Set<Item> getItemsInBestParse() {
//        return itemsInBestParse;
//    }
//    
//    
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * ************************************************** MAIN
     * *************************************
     */
    private static class Args {

        @Parameter
        private List<String> arguments = null;

        @Parameter(names = "--bias", description = "Bias to speed up the A* search.")
        private Double bias = 0.0;

        @Parameter(names = "--declutter", description = "Declutter the agenda.")
        private boolean declutter = false;

        @Parameter(names = "--parse-only", description = "Parse only the sentence with the given index.")
        private Integer parseOnly = null;

        @Parameter(names = "--threads", description = "Number of threads to use.")
        private Integer numThreads = 1;

        @Parameter(names = "--sort", description = "Sort corpus by sentence length.")
        private boolean sort = false;

        @Parameter(names = "--typecache", description = "Save/load the type lexicon to this file.")
        private String typeInternerFilename = null;

        @Parameter(names = {"--scores", "-s"}, description = "File with supertag and edge scores.", required = true)
        private String probsFilename;

        @Parameter(names = {"--outdir", "-o"}, description = "Directory to which outputs are written.")
        private String outFilename = "";

        @Parameter(names = "--help", help = true)
        private boolean help = false;

        private File resolveFilename(String filename) {
            if (filename == null) {
                return null;
            } else {
                return Paths.get(filename).toFile();
            }
        }

        private File resolveOutputFilename(String filename) {
            if (filename == null) {
                return null;
            } else {
                return Paths.get(outFilename).resolve(filename).toFile();
            }
        }

        public File getTypeInternerFile() {
            return resolveFilename(typeInternerFilename);
        }

        public File getScoreFile() {
            return resolveFilename(probsFilename);
        }

        public File getOutFile() {
            return resolveOutputFilename("results_" + timestamp + ".amconll");
        }

        public File getLogFile() {
            return resolveOutputFilename("log_" + timestamp + ".txt");
        }

        private String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
    }

    public static void main(String[] args) throws IOException, ParserException, ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException, InterruptedException, ParseException {
        Args arguments = new Args();
        JCommander jc = JCommander.newBuilder().addObject(arguments).build();
        jc.setProgramName("java -cp am-tools-all.jar de.saar.coli.irtg.experimental.astar.Astar");

        try {
            jc.parse(args);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.err.println();
            jc.usage();
            System.exit(1);
        }

        if (arguments.help) {
            jc.usage();
            System.exit(0);
        }

        // read supertags
        ZipFile probsZipFile = new ZipFile(arguments.getScoreFile());
        ZipEntry supertagsZipEntry = probsZipFile.getEntry("tagProbs.txt");
        Reader supertagsReader = new InputStreamReader(probsZipFile.getInputStream(supertagsZipEntry));

        int nullSupertagId = -1;
        List<List<List<AnnotatedSupertag>>> supertags = Util.readSupertagProbs(supertagsReader, true);
        Interner<String> supertagLexicon = new Interner<>();
        Int2ObjectMap<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> idToSupertag = new ArrayMap<>();
        Algebra<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> alg = new ApplyModifyGraphAlgebra();
        int numSupertagsPerToken = 0;
        Set<Type> types = new HashSet<>();

        // calculate supertag lexicon
        for (List<List<AnnotatedSupertag>> sentence : supertags) {
            for (List<AnnotatedSupertag> token : sentence) {
                // check same #supertags for each token
                if (numSupertagsPerToken == 0) {
                    numSupertagsPerToken = token.size();
                } else {
                    assert numSupertagsPerToken == token.size();
                }

                for (AnnotatedSupertag st : token) {
                    String supertag = st.graph;

                    if (!supertagLexicon.isKnownObject(supertag)) {
                        int id = supertagLexicon.addObject(supertag);
                        Pair<SGraph, Type> gAndT = alg.parseString(supertag);
                        
                        if( st.type != null ) {
                            // if supertag had an explicit type annotation in the file,
                            // use that one
                            gAndT.right = new Type(st.type);
                        }
                        
                        idToSupertag.put(id, gAndT);
                        types.add(gAndT.right);
                        
                        if ("NULL".equals(supertag)) {
                            nullSupertagId = id;
                        }
                    }
                }
            }
        }

        if (nullSupertagId < 0) {
            System.err.println("Did not find an entry for the NULL supertag - exiting.");
            System.exit(1);
        }

        // build supertag array
        List<SupertagProbabilities> tagp = new ArrayList<>();  // one per sentence
        for (List<List<AnnotatedSupertag>> sentence : supertags) {
            SupertagProbabilities tagpHere = new SupertagProbabilities(FAKE_NEG_INFINITY, nullSupertagId);
            for (int tokenPos = 0; tokenPos < sentence.size(); tokenPos++) {
                List<AnnotatedSupertag> token = sentence.get(tokenPos);
                for (int stPos = 0; stPos < numSupertagsPerToken; stPos++) {
                    AnnotatedSupertag st = token.get(stPos);
                    String supertag = st.graph;
                    int supertagId = supertagLexicon.resolveObject(supertag);
                    tagpHere.put(tokenPos, supertagId, Math.log(st.probability)); // wasteful: first exp in Util.readProbs, then log again here
                }
            }

            tagp.add(tagpHere);
        }

        // calculate edge-label lexicon
        ZipEntry edgeZipEntry = probsZipFile.getEntry("opProbs.txt");
        Reader edgeReader = new InputStreamReader(probsZipFile.getInputStream(edgeZipEntry));
        List<List<List<Pair<String, Double>>>> edges = Util.readEdgeProbs(edgeReader, true, 0.01, 5, true);  // TODO make these configurable
        Interner<String> edgeLabelLexicon = new Interner<>();

        for (List<List<Pair<String, Double>>> sentence : edges) {
            for (List<Pair<String, Double>> b : sentence) {
                for (Pair<String, Double> edge : b) {
                    Edge e = Edge.parse(edge.left);
                    edgeLabelLexicon.addObject(e.label);
                }
            }
        }

        // build edge array
        List<EdgeProbabilities> edgep = new ArrayList<>();
        for (List<List<Pair<String, Double>>> sentence : edges) {
            for (List<Pair<String, Double>> b : sentence) {
                EdgeProbabilities edgepHere = new EdgeProbabilities(FAKE_NEG_INFINITY);

                for (Pair<String, Double> edge : b) {
                    Edge e = Edge.parse(edge.left);
                    int edgeLabelId = edgeLabelLexicon.resolveObject(e.label);

                    try {
                        edgepHere.set(e.from, e.to, edgeLabelId, Math.log(edge.right));
                    } catch (ArrayIndexOutOfBoundsException ee) {
                        throw ee;
                    }
                }

                edgep.add(edgepHere);
            }
        }

        // precalculate type interner for the supertags in tagp;
        // this can take a few minutes
        AMAlgebraTypeInterner typecache = null;

        if (arguments.typeInternerFilename != null) {
            if (arguments.getTypeInternerFile().exists()) {
                try (InputStream is = new GZIPInputStream(new FileInputStream(arguments.getTypeInternerFile()))) {
                    System.err.printf("\nLoad type interner from file %s ...\n", arguments.getTypeInternerFile());
                    typecache = AMAlgebraTypeInterner.read(is);
                    System.err.println("Done.");
                }
            }
        }

        if (typecache == null) {
            System.err.printf("\nBuild type interner from %d types ...\n", types.size());
            CpuTimeStopwatch typew = new CpuTimeStopwatch();
            typew.record();
            typecache = new AMAlgebraTypeInterner(types, edgeLabelLexicon);
            typew.record();
            System.err.printf("Done, %.1f ms\n", typew.getMillisecondsBefore(1));

            if (arguments.typeInternerFilename != null) {
                try (OutputStream os = new GZIPOutputStream(new FileOutputStream(arguments.getTypeInternerFile()))) {
                    System.err.printf("Write type interner to file %s ...\n", arguments.getTypeInternerFile());
                    typecache.save(os);
                    os.flush();
                    System.err.println("Done.");
                }
            }
        }

        final AMAlgebraTypeInterner typeLexicon = typecache;

        // load input amconll file
        ZipEntry inputEntry = probsZipFile.getEntry("corpus.amconll");
        final List<ConllSentence> corpus = ConllSentence.read(new InputStreamReader(probsZipFile.getInputStream(inputEntry)));

        // parse corpus
        ForkJoinPool forkJoinPool = new ForkJoinPool(arguments.numThreads);

        File logfile = arguments.getLogFile();
        File outfile = arguments.getOutFile();
        PrintWriter logW = new PrintWriter(new FileWriter(logfile));

        System.err.printf("\nWriting graphs to %s.\n\n", outfile.getAbsolutePath());

        List<Integer> sentenceIndices = IntStream.rangeClosed(0, tagp.size() - 1).boxed().collect(Collectors.toList());
        if (arguments.sort) {
            sentenceIndices.sort((a, b) -> Integer.compare(tagp.get(a).getLength(), tagp.get(b).getLength()));
        }

        final ProgressBar pb = new ProgressBar("Parsing", sentenceIndices.size());

        for (int i : sentenceIndices) { // loop over corpus
            if (arguments.parseOnly == null || i == arguments.parseOnly) {  // restrict to given sentence
                final int ii = i;

//                System.err.printf("\n[%02d] EDGES:\n", ii);
//                edgep.get(ii).prettyprint(edgeLabelLexicon, System.err);
                forkJoinPool.execute(() -> {
                    Astar astar = null;
                    ParsingResult parsingResult = null;
                    String result = "(u / unparseable)";
                    CpuTimeStopwatch w = new CpuTimeStopwatch();

                    try {
                        w.record();

                        astar = new Astar(edgep.get(ii), tagp.get(ii), idToSupertag, supertagLexicon, edgeLabelLexicon, typeLexicon);
                        astar.setBias(arguments.bias);
                        astar.setDeclutterAgenda(arguments.declutter);
                        astar.setLogger((s) -> {
                            synchronized (logW) {
                                logW.println(s);
                            }
                        });

                        w.record();

                        Item goalItem = astar.process();
                        System.err.println("goal item:");
                        System.err.println(goalItem);
                        
                        parsingResult = astar.decode(goalItem);
                        
                        System.err.println("parsing result:");
                        System.err.println(parsingResult);
                        
                        w.record();
                    } catch (Throwable e) {
                        synchronized (logW) {
                            logW.printf("Exception (sentence id=%d):\n", ii);
                            e.printStackTrace(logW);
                        }
                    } finally {
                        ConllSentence sent = corpus.get(ii);

                        if (parsingResult != null) {
                            // TODO find out how this can happen - this doesn't look like a normal
                            // "no parse" case.
                            // TODO what did I mean with that??

                            sent.setDependenciesFromAmTerm(parsingResult.amTerm, parsingResult.leafOrderToStringOrder, astar.getSupertagToTypeFunction());
                        }

                        w.record();
                        String reportString = (astar == null || astar.getRuntimeStatistics() == null)
                                ? String.format("[%04d] no runtime statistics available", ii)
                                : String.format("[%04d] %s %s", ii, sent.getId(), astar.getRuntimeStatistics().toString());

                        synchronized (logW) {
                            logW.println(reportString);
                            logW.printf("[%04d] init %.1f ms; parse %.1f ms; evaluate %.1f ms\n", ii,
                                    w.getMillisecondsBefore(1),
                                    w.getMillisecondsBefore(2),
                                    w.getMillisecondsBefore(3));
                            logW.flush();
                        }

                        synchronized (pb) {
                            pb.step();
                        }
                    }

                });
            }
        }

        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1000, TimeUnit.MINUTES);

        pb.close();
        logW.close();

        // write parsed corpus to output file
        ConllSentence.write(new FileWriter(arguments.getOutFile()), corpus);
    }

    /**
     * For testing only.
     *
     * @param n
     */
    void setN(int n) {
        N = n;
    }
    
    public Function<String,Type> getSupertagToTypeFunction() {
        return (supertag) -> {
            int supertagId = supertagLexicon.resolveObject(supertag);
            int typeId = supertagTypes.get(supertagId);
            return typeLexicon.resolveID(typeId);
        };
    }
}
