/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableMap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.irtg.experimental.astar.agenda.Agenda;
import de.saar.coli.irtg.experimental.astar.agenda.PriorityQueueAgenda;
import de.saar.coli.irtg.experimental.astar.heuristics.*;
import de.saar.coli.irtg.experimental.astar.io.ScoreReader;
import de.saar.coli.irtg.experimental.astar.io.SerializedScoreReader;
import de.saar.coli.irtg.experimental.astar.io.TextScoreReader;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.saar.coli.irtg.experimental.astar.TypeInterner.AMAlgebraTypeInterner;
import de.up.ling.irtg.siblingfinder.SiblingFinder;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.util.MutableLong;
import it.unimi.dsi.fastutil.ints.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.UnsupportedLookAndFeelException;
import me.tongfei.progressbar.ProgressBar;

/**
 * Run on command line through Gradle like this:
 * ./gradlew -PmainClass=de.saar.coli.irtg.experimental.astar.Astar run --args='-s EMNLP20/DM/dev/mtl-bert/scores.zip --threads 2 --typecache EMNLP20/DM/dev/mtl-bert/typecache.dat -o EMNLP20/DM/dev/'
 *
 * @author koller
 */
public class Astar {

    public static final double FAKE_NEG_INFINITY = -1000000;
    public static final String IGNORE_EDGELABEL = "IGNORE";
    public static final String ROOT_EDGELABEL = "ROOT";
    
    private boolean declutterAgenda = false; // previously dequeued items will never be enqueued again

    private int N;
    private final EdgeProbabilities edgep;
    private final SupertagProbabilities tagp;
    private final OutsideEstimator outside;
    private final String outsideEstimatorString;
    private final Int2ObjectMap<Pair<SGraph, Type>> idToSupertag;
    private final Interner<String> supertagLexicon;
    private final Interner<String> edgeLabelLexicon;
    private final AMAlgebraTypeInterner typeLexicon;
    private RuntimeStatistics runtimeStatistics = null;
    private final Int2IntMap supertagTypes;
    private Consumer<String> logger;

    private static final Map<String, BiFunction<SupertagProbabilities, EdgeProbabilities, OutsideEstimator>> OUTSIDE_ESTIMATORS = ImmutableMap.of(
            "supertagonly", (tagp, edgep) -> new SupertagOnlyOutsideEstimator(tagp),
            "static", (tagp, edgep) -> new StaticOutsideEstimator(edgep, tagp),
            "trivial", (tagp, edgep) -> new TrivialOutsideEstimator(),
            "root_aware", (tagp, edgep) -> new RootAwareStaticEstimator(edgep, tagp),
            "ignore_aware", (tagp, edgep) -> new RootAndIgnoreAwareStaticEstimator(edgep, tagp)
            );

    public Astar(EdgeProbabilities edgep, SupertagProbabilities tagp, Int2ObjectMap<Pair<SGraph, Type>> idToAsGraph, Interner<String> supertagLexicon, Interner<String> edgeLabelLexicon, AMAlgebraTypeInterner typeLexicon, String outsideEstimatorString) {
        logger = (s) -> System.err.println(s);  // by default, log to stderr
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        w.record();

        this.edgep = edgep;
        this.tagp = tagp;
        this.idToSupertag = idToAsGraph;
        this.edgeLabelLexicon = edgeLabelLexicon;
        this.supertagLexicon = supertagLexicon;
        this.N = tagp.getLength();              // sentence length
        this.outsideEstimatorString = outsideEstimatorString;

        w.record();
        this.typeLexicon = typeLexicon;
        w.record();

        this.outside = OUTSIDE_ESTIMATORS.get(this.outsideEstimatorString).apply(tagp, edgep);

        w.record();
        // precompute supertag types
        supertagTypes = new Int2IntOpenHashMap();
        for (int supertagId : idToSupertag.keySet()) {
            supertagTypes.put(supertagId, typeLexicon.resolveObject(idToSupertag.get(supertagId).right));
        }

        w.record();
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

    private Item process() {
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        long numDequeuedItems = 0;
        long numDequeuedSupertags = 0;

        w.record();

        Agenda agenda = new PriorityQueueAgenda(declutterAgenda);
//        Agenda agenda = new StanfordAgenda(); // Wow, for some reason Stanford is really a lot slower now.

        SiblingFinder[] siblingFinders = new SiblingFinder[edgeLabelLexicon.size()];  // siblingFinders[op] = type-sibling-finder for operation op, for all seen types
        for (int j : edgeLabelLexicon.getKnownIds()) {
            siblingFinders[j - 1] = typeLexicon.makeSiblingFinder(j);
        }

        Int2ObjectMap<Set<Item>>[] rightChart = new Int2ObjectMap[N + 2];      // rightChart[i].get(t) = all previously dequeued items that start at i with type-ID t
        Int2ObjectMap<Set<Item>>[] leftChart = new Int2ObjectMap[N + 2];       // leftChart[i].get(t) = all previously dequeued items that end at i with type-ID t
        for (int i = 1; i <= N + 1; i++) {
            rightChart[i] = new Int2ObjectOpenHashMap<>();
            leftChart[i] = new Int2ObjectOpenHashMap<>();
        }

        // initialize agenda
        for (int i = 1; i <= N; i++) {  // no items for 0
            final int i_final = i;
            final IntSet seenTypesHere = new IntOpenHashSet();

            tagp.foreachInOrder(i, (supertagId, prob) -> {
                //System.err.printf("[%02d] supertag %d [%s], p=%f\n", i_final, supertagId, supertagLexicon.resolveId(supertagId), prob);

                if (supertagId != tagp.getNullSupertagId()) { // skip NULL entries - NULL items are created on the fly during the agenda exploration phase
                    int type = getSupertagType(supertagId);

                    if( seenTypesHere.add(type)) { // only add best supertag at each position for each type
                        Item it = new Item(i_final, i_final + 1, i_final, getSupertagType(supertagId), prob);
                        it.setCreatedBySupertag(supertagId);
                        it.setOutsideEstimate(outside.evaluate(it));
                        //System.err.println(it);
                        agenda.enqueue(it);
                    }
                }
            });
        }

        // iterate over agenda
        int j = 0;
        while (!agenda.isEmpty()) {
            Item it = agenda.dequeue();
            //System.err.println(it);
            //if (j % 500 == 0) {
                // System.err.println(it.getOutsideEstimate());
                // System.err.println(it.getLogProb());
                // System.err.println(it.getScore());
                // System.err.println("");
            //}

            if (it == null) {
                //System.err.println(j);
                // emptied agenda without finding goal item
                w.record(); // agenda looping time
                runtimeStatistics = new RuntimeStatistics(N, numDequeuedItems, numDequeuedSupertags, w.getTimeBefore(1), Double.NaN);
                return null;
            }

            numDequeuedItems++;

            if( it.isCreatedBySupertag() ) {
                numDequeuedSupertags++;
            }

//            System.err.printf("[%5d] pop: %s\n", numDequeuedItems, it.toString(typeLexicon));

            // return first found goal item
            if (isGoal(it)) {
                w.record(); // agenda looping time

                runtimeStatistics = new RuntimeStatistics(N, numDequeuedItems, numDequeuedSupertags, w.getTimeBefore(1), it.getLogProb());
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
            if (it.getType() != 0) {
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

                        // items with matching types on the left
                        for (Item partner : (Set<Item>) leftChart[it.getStart()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                            Item result = combineLeft(op, it, partner);
                            assert result.getScore() <= it.getScore() + EPS : "[0L] Generated " + result + " from " + it;
                            agenda.enqueue(result);
                        }
                    }

                    for (int[] types : siblingFinders[op - 1].getPartners(it.getType(), 1)) {
                        // here, 'it' is the argument and partner is the functor
                        int partnerType = types[0];

                        // items with matching types on the right
                        for (Item partner : (Set<Item>) rightChart[it.getEnd()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                            Item result = combineLeft(op, partner, it);
                            double logEdgeProbability = edgep.get(partner.getRoot(), it.getRoot(), op);
                            assert result.getScore() <= it.getScore() + EPS : String.format("[1R] Generated %s from it: %s <--[%s:%f]-- partner: %s", result.toString(typeLexicon), it.toString(typeLexicon), edgeLabelLexicon.resolveId(op), logEdgeProbability, partner.toString(typeLexicon));
                            agenda.enqueue(result);
                        }

                        // items with matching types on the left
                        for (Item partner : (Set<Item>) leftChart[it.getStart()].getOrDefault(partnerType, Collections.EMPTY_SET)) {
                            Item result = combineRight(op, partner, it);
                            assert result.getScore() <= it.getScore() + EPS : "[1L] Generated " + result.toString(typeLexicon) + " from " + it.toString(typeLexicon);
                            agenda.enqueue(result);
                        }
                    }

                }
            }

            // Skip rules maintain the invariant that the root of an item is not NULL.

            // skip to the right
            if (it.getEnd() < N) {
                Item skipRight = makeSkipItem(it, it.getStart(), it.getEnd() + 1, it.getEnd());

                if (skipRight != null) {
                    assert skipRight.getScore() <= it.getScore() + EPS : String.format("skipRight=%f, it=%f", skipRight.getScore(), it.getScore());
                    agenda.enqueue(skipRight);
                }
            }

            // skip to the left
            if (it.getStart() > 1) {
                Item skipLeft = makeSkipItem(it, it.getStart() - 1, it.getEnd(), it.getStart() - 1);

                if (skipLeft != null) {
                    assert skipLeft.getScore() <= it.getScore() + EPS : String.format("skipLeft=%f, it=%f", skipLeft.getScore(), it.getScore());
                    agenda.enqueue(skipLeft);
                }
            }
            
            // add ROOT edge from 0
            if( isAlmostGoal(it) ) {
                Item goalItem = makeGoalItem(it);
                //System.err.println(" --> goal: " + goalItem);
                agenda.enqueue(goalItem);
            }

            j += 1;
        }

        w.record();
        runtimeStatistics = new RuntimeStatistics(N, numDequeuedItems, numDequeuedSupertags, w.getTimeBefore(1), Double.NaN);
        return null;
    }
    
    private boolean isAlmostGoal(Item it) {
        return it.getStart() == 1 && it.getEnd()-1 == N && typeLexicon.resolveID(it.getType()).getOrigins().isEmpty();
    }
    
    private Item makeGoalItem(Item almostGoalItem) {
        double rootProb = edgep.get(0, almostGoalItem.getRoot(), edgep.getRootEdgeId());
        //double rootProb = 0;
        //System.err.println(rootProb);
        Item goalItem = new Item(almostGoalItem.getStart()-1, almostGoalItem.getEnd()-1, almostGoalItem.getRoot(), almostGoalItem.getType(), almostGoalItem.getLogProb() + rootProb);
        goalItem.setOutsideEstimate(0);
        goalItem.setCreatedByOperation(-1, almostGoalItem, null);
        return goalItem;
    }

    private static final double EPS = 1e-6;

    private Item makeSkipItem(Item originalItem, int newStart, int newEnd, int skippedPosition) {
        double nullProb = tagp.get(skippedPosition, tagp.getNullSupertagId());        // log P(supertag = NULL | skippedPosition)
        double ignoreProb = edgep.get(0, skippedPosition, edgep.getIgnoreEdgeId());   // log P(inedge = IGNORE from 0 | skippedPosition)

        if (nullProb + ignoreProb < FAKE_NEG_INFINITY / 2) {
            // either NULL or IGNORE didn't exist - probably IGNORE
            return null;
        }

        double newItemCost = originalItem.getLogProb() + nullProb + ignoreProb;
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

        if (realOutside > item.getOutsideEstimate() + EPS) {
            logger.accept(String.format("WARNING: Inadmissible estimate (realOutside=%f, item=%s).", realOutside, item.toString()));
        }

        if (item.getLeft() == null) {
            // leaf; decode op as supertag
            String supertag = supertagLexicon.resolveId(item.getOperation());
            leafOrderToStringOrder.set(nextLeafPosition.incValue(), item.getStart()-1);
            return Tree.create(supertag);
        } else if (item.getRight() == null) {
            // skip
            return decode(item.getLeft(), logProbGoalItem, leafOrderToStringOrder, nextLeafPosition);
        } else {
            // non-leaf; decode op as edge
            Tree<String> left = decode(item.getLeft(), logProbGoalItem, leafOrderToStringOrder, nextLeafPosition);
            Tree<String> right = decode(item.getRight(), logProbGoalItem, leafOrderToStringOrder, nextLeafPosition);
            return Tree.create(edgeLabelLexicon.resolveId(item.getOperation()), left, right);
        }
    }

    ParsingResult decode(Item goalItem) {
        if (goalItem == null) {
            return null;
        } else {
            double goalItemLogProb = goalItem.getLogProb();
            IntList leafOrderToStringOrder = new IntArrayList(N);
            for (int i = 0; i < N; i++) {
                leafOrderToStringOrder.add(0);
            }

            Tree<String> amTerm = decode(goalItem, goalItemLogProb, leafOrderToStringOrder, new MutableInteger(0));

            return new ParsingResult(amTerm, goalItemLogProb, leafOrderToStringOrder);
        }
    }

    // check whether the item is a goal item
    private boolean isGoal(Item item) {
        return item.getStart() == 0 && item.getEnd() == N && typeLexicon.resolveID(item.getType()).getOrigins().isEmpty();
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

    private int getSupertagType(int supertagId) {
        return supertagTypes.get(supertagId);
    }

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

        @Parameter(names = "--outside-estimator", description = "Outside estimator to use.")
        private String outsideEstimatorString = "static";

        @Parameter(names = "--sort", description = "Sort corpus by sentence length.")
        private boolean sort = false;

        @Parameter(names = "--typecache", description = "Save/load the type lexicon to this file.")
        private String typeInternerFilename = null;

        @Parameter(names = {"--scores", "-s"}, description = "File with supertag and edge scores.")
        private String probsFilename;

        @Parameter(names = {"--serialized-scores", "-S"}, description = "File with serialized supertag and edge scores.")
        private String serializedProbsFilename;

        @Parameter(names = {"--outdir", "-o"}, description = "Directory to which outputs are written.")
        private String outFilename = "";

        @Parameter(names = "--log-to-stderr", description = "Write log messages to stderr instead of logfile")
        private boolean logToStderr = false;

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

        public File getSerializedScoreFile() {
            return resolveFilename(serializedProbsFilename);
        }

        public File getOutFile() {
            return resolveOutputFilename("results_" + timestamp + ".amconll");
        }

        public File getLogFile() {
            return resolveOutputFilename("log_" + timestamp + ".txt");
        }

        public ScoreReader createScoreReader() throws IOException, ParseException, ParserException {
            if( serializedProbsFilename != null ) {
                return new SerializedScoreReader(getSerializedScoreFile());
            } else if( probsFilename != null ) {
                return new TextScoreReader(getScoreFile(), ROOT_EDGELABEL, IGNORE_EDGELABEL);
            } else {
                throw new RuntimeException("You must specify either a scores file (-s) or a serialized score file (-S).");
            }
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
            System.out.println("Available outside estimators: " + OUTSIDE_ESTIMATORS.keySet());
            System.exit(1);
        }

        if (arguments.help) {
            jc.usage();
            System.out.println("Available outside estimators: " + OUTSIDE_ESTIMATORS.keySet());
            System.exit(0);
        }

        // initialize outside estimator
        if( ! OUTSIDE_ESTIMATORS.containsKey(arguments.outsideEstimatorString) ) {
            System.err.printf("Outside estimator '%s' is invalid, known outside estimators:\n", arguments.outsideEstimatorString);
            System.err.println(OUTSIDE_ESTIMATORS.keySet());
            System.exit(1);
        }

        // read supertag and edge probs
        ScoreReader scoreReader = arguments.createScoreReader();

        List<SupertagProbabilities> tagp = scoreReader.getSupertagProbabilities();



        // calculate edge-label lexicon


        // precalculate type interner for the supertags in tagp;
        // this can take a few minutes
        AMAlgebraTypeInterner typecache = null;
        if (arguments.typeInternerFilename != null) {
            if (arguments.getTypeInternerFile().exists()) {
                try (InputStream is = new GZIPInputStream(new FileInputStream(arguments.getTypeInternerFile()))) {
                    System.err.printf("Reading type interner from file %s ... ", arguments.getTypeInternerFile());
                    typecache = AMAlgebraTypeInterner.read(is);
                    System.err.println("done.");
                }
            }
        }

        if (typecache == null) {
            System.err.printf("Building type interner from %d types ... ", scoreReader.getAllTypes().size());
            CpuTimeStopwatch typew = new CpuTimeStopwatch();
            typew.record();
            typecache = new AMAlgebraTypeInterner(scoreReader.getAllTypes(), scoreReader.getEdgeLabelLexicon());
            typew.record();
            System.err.printf("done, %.1f ms\n", typew.getMillisecondsBefore(1));

            if (arguments.typeInternerFilename != null) {
                try (OutputStream os = new GZIPOutputStream(new FileOutputStream(arguments.getTypeInternerFile()))) {
                    System.err.printf("Writing type interner to file %s ... ", arguments.getTypeInternerFile());
                    typecache.save(os);
                    os.flush();
                    System.err.println("done.");
                }
            }
        }

        final AMAlgebraTypeInterner typeLexicon = typecache;

        // load input amconll file
        System.err.println("Reading input AM-CoNLL file ...");
        final List<AmConllSentence> corpus = scoreReader.getInputCorpus();

        // parse corpus
        ForkJoinPool forkJoinPool = new ForkJoinPool(arguments.numThreads);

        File logfile = arguments.getLogFile();
        File outfile = arguments.getOutFile();
        PrintWriter logW = new PrintWriter(new FileWriter(logfile));

        System.err.printf("\nWriting AM-CoNLL trees to %s\n\n", outfile.getAbsolutePath());

        List<Integer> sentenceIndices = IntStream.rangeClosed(0, tagp.size() - 1).boxed().collect(Collectors.toList());
        if (arguments.sort) {
            sentenceIndices.sort(Comparator.comparingInt(a -> tagp.get(a).getLength()));
        }

        final ProgressBar pb = new ProgressBar("Parsing", sentenceIndices.size());
        final MutableLong totalParsingTimeNs = new MutableLong(0);
        final MutableLong totalWords = new MutableLong(0);
        final MutableLong totalDequeuedItems = new MutableLong(0);
        final MutableLong totalDequeuedSupertags = new MutableLong(0);

        for (int i : sentenceIndices) { // loop over corpus
            if (arguments.parseOnly == null || i == arguments.parseOnly) {  // restrict to given sentence
            //if (tagp.get(i).getLength() == 1) {
                final int ii = i;


//                System.err.printf("\n[%02d] EDGES:\n", ii);
                //edgep.get(ii).prettyprint(edgeLabelLexicon, System.err);
                forkJoinPool.execute(() -> {
                    Astar astar = null;
                    ParsingResult parsingResult = null;
//                    String result = "(u / unparseable)";
                    CpuTimeStopwatch w = new CpuTimeStopwatch();

                    try {
                        w.record();

                        astar = new Astar(scoreReader.getEdgeProbabilities().get(ii), tagp.get(ii), scoreReader.getIdToSupertag(), scoreReader.getSupertagLexicon(), scoreReader.getEdgeLabelLexicon(), typeLexicon, arguments.outsideEstimatorString);
                        astar.setBias(arguments.bias);
                        astar.setDeclutterAgenda(arguments.declutter);

                        if (!arguments.logToStderr) {
                            astar.setLogger((s) -> {
                                synchronized (logW) {
                                    logW.println(s);
                                    logW.flush();
                                }
                            });
                        }

                        w.record();

                        Item goalItem = astar.process();
                        parsingResult = astar.decode(goalItem);
                        w.record();
                    } catch (Throwable e) {
                        astar.logger.accept(String.format("Exception (sentence id=%d):\n", ii));
                        StringWriter ww = new StringWriter();
                        e.printStackTrace(new PrintWriter(ww));
                        astar.logger.accept(ww.toString());
                        System.exit(2); // AKAKAK
                    } finally {
                        AmConllSentence sent = corpus.get(ii);

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

                            totalParsingTimeNs.incValue(w.getTimeBefore(1) + w.getTimeBefore(2) + w.getTimeBefore(3));
                            totalWords.incValue(astar.getRuntimeStatistics().getSentenceLength());
                            totalDequeuedItems.incValue(astar.getRuntimeStatistics().getNumDequeuedItems());
                            totalDequeuedSupertags.incValue(astar.getRuntimeStatistics().getNumDequeuedSupertags());
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

        System.out.printf(Locale.ROOT, "Total parsing time: %f seconds.\n", totalParsingTimeNs.longValue() / 1.e9);
        System.out.printf(Locale.ROOT, "Total dequeued items: %d (%.1f per token).\n", totalDequeuedItems.longValue(), ((double) totalDequeuedItems.longValue())/totalWords.longValue());
        System.out.printf(Locale.ROOT, "Total dequeued supertags: %d (%.1f per token).\n", totalDequeuedSupertags.longValue(), ((double) totalDequeuedSupertags.longValue())/totalWords.longValue());

        // write parsed corpus to output file
        AmConllSentence.write(new FileWriter(arguments.getOutFile()), corpus);
    }

    /**
     * For testing only.
     *
     * @param n
     */
    void setN(int n) {
        N = n;
    }

    public Function<String, Type> getSupertagToTypeFunction() {
        return (supertag) -> {
            int supertagId = supertagLexicon.resolveObject(supertag);
            int typeId = supertagTypes.get(supertagId);
            return typeLexicon.resolveID(typeId);
        };
    }
}
