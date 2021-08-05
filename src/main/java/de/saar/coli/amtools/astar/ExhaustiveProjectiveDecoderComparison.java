/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.astar;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.saar.coli.amrtagging.Parser;
import de.saar.coli.amrtagging.Util;
import de.saar.coli.amtools.astar.io.ScoreReader;
import de.saar.coli.amtools.astar.io.TextScoreReader;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import de.up.ling.irtg.util.MutableBoolean;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import edu.stanford.nlp.util.MutableLong;
import it.unimi.dsi.fastutil.ints.*;

import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static de.saar.coli.amtools.astar.Astar.IGNORE_EDGELABEL;
import static de.saar.coli.amtools.astar.Astar.ROOT_EDGELABEL;

/**
 * Combines the old projective decoder with the data reading I/O of the Astar script, for easier comparison.
 * (For checking correctness of the Astar parser)
 * @author Jonas Groschwitz
 */
public class ExhaustiveProjectiveDecoderComparison {

    /**
     *
     * ************************************************** MAIN
     * *************************************
     */
    private static class Args {

        @Parameter
        private List<String> arguments = null;

        @Parameter(names = "--parse-only", description = "Parse only the sentence with the given index.")
        private Integer parseOnly = null;

        @Parameter(names = "--threads", description = "Number of threads to use.")
        private Integer numThreads = 1;

        @Parameter(names = "--sort", description = "Sort corpus by sentence length.")
        private boolean sort = false;

        @Parameter(names = "--compareAstar", description = "whether to also run Astar parser and compare outputs.")
        private boolean compareAstar = false;

        @Parameter(names = {"--scores", "-s"}, description = "File with supertag and edge scores.", required = true)
        private String probsFilename;

        @Parameter(names = {"--outdir", "-o"}, description = "Directory to which outputs are written.")
        private String outFilename = "";


        @Parameter(names = {"--itemlimit"}, description = "timeout per graph for projective parser, in seconds.")
        private long itemLimit = 1000000;


        @Parameter(names = {"--sentenceLengthLimit"}, description = "maximum sentence length this attempts to parse.")
        private long sentenceLengthLimit = 120;

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

        public File getScoreFile() {
            return resolveFilename(probsFilename);
        }

        public File getOutFile() {
            return resolveOutputFilename("results_" + timestamp + ".amconll");
        }

        public File getLogFile() {
            return resolveOutputFilename("log_" + timestamp + ".txt");
        }



        public ScoreReader createScoreReader() throws IOException, ParseException, ParserException {
            if( probsFilename != null ) {
                return new TextScoreReader(getScoreFile(), ROOT_EDGELABEL, IGNORE_EDGELABEL);
            } else {
                throw new RuntimeException("You must specify either a scores file (-s) or a serialized score file (-S).");
            }
        }



        private String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
    }

    public static void main(String[] args) throws IOException, InterruptedException, ParseException, ParserException {
        Args arguments = new Args();
        JCommander jc = JCommander.newBuilder().addObject(arguments).build();
        jc.setProgramName("java -cp am-tools-all.jar de.saar.coli.amtools.astar.ExhausiveProjectiveDecoderComparison");

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

        // setup for A* decoder
        // read supertag and edge probs
        ScoreReader scoreReader;
        List<SupertagProbabilities> tagp;
        final TypeInterner.AMAlgebraTypeInterner typeLexicon;
        if (arguments.compareAstar) {
            scoreReader = arguments.createScoreReader();

            tagp = scoreReader.getSupertagProbabilities();



            // calculate edge-label lexicon


            // precalculate type interner for the supertags in tagp;
            // this can take a few minutes
            System.err.printf("Building type interner from %d types ... ", scoreReader.getAllTypes().size());
            typeLexicon = new TypeInterner.AMAlgebraTypeInterner(scoreReader.getAllTypes(), scoreReader.getEdgeLabelLexicon());
            System.err.printf("done\n");
        } else {
            scoreReader = null;
            tagp = null;
            typeLexicon = null;
        }



        // setup for projective decoder
        // read supertags
        ZipFile probsZipFile = new ZipFile(arguments.getScoreFile());
        ZipEntry supertagsZipEntry = probsZipFile.getEntry("tagProbs.txt");
        Reader supertagsReader = new InputStreamReader(probsZipFile.getInputStream(supertagsZipEntry));

        List<List<List<AnnotatedSupertag>>> supertags = Util.readSupertagProbs(supertagsReader, true);


        // calculate edge-label lexicon
        ZipEntry edgeZipEntry = probsZipFile.getEntry("opProbs.txt");
        Reader edgeReader = new InputStreamReader(probsZipFile.getInputStream(edgeZipEntry));
        List<List<List<Pair<String, Double>>>> edgesProbs = Util.readEdgeProbs(edgeReader, true, 0.0, 7, true); //0.0, 7, false are the same as in Astar.java -- Jan 29, JG
        List<Map<String, Int2ObjectMap<Int2DoubleMap>>> edgeLabel2pos2pos2prob = new ArrayList<>();
        if (edgesProbs != null) {
            for (List<List<Pair<String, Double>>> edgesInSentence : edgesProbs) {
                Map<String, Int2ObjectMap<Int2DoubleMap>> mapHere = new HashMap<>();
                edgeLabel2pos2pos2prob.add(mapHere);
                for (List<Pair<String, Double>> list : edgesInSentence) {
                    for (Pair<String, Double> pair : list) {
                        String label = pair.left.split("\\[")[0];
                        //NOTE in earlier supertagger output, first and second was swapped. To properly evaluate that old output, maybe switch it here temporarily.
                        int first = Integer.valueOf(pair.left.split("\\[")[1].split(",")[0]);
                        int second = Integer.valueOf(pair.left.split(",")[1].split("\\]")[0]);
                        Int2ObjectMap<Int2DoubleMap> pos2pos2prob = mapHere.get(label);
                        if (pos2pos2prob == null) {
                            pos2pos2prob = new Int2ObjectOpenHashMap<>();
                            mapHere.put(label, pos2pos2prob);
                        }
                        Int2DoubleMap pos2prob = pos2pos2prob.get(first);
                        if (pos2prob == null) {
                            pos2prob = new Int2DoubleOpenHashMap();
                            pos2pos2prob.put(first, pos2prob);
                        }
                        pos2prob.put(second, pair.right.doubleValue());
                    }
                }
            }
        }


        // load input amconll file
        ZipEntry inputEntry = probsZipFile.getEntry("corpus.amconll");
        final List<AmConllSentence> corpus = AmConllSentence.read(new InputStreamReader(probsZipFile.getInputStream(inputEntry)));

        // parse corpus

        File logfile = arguments.getLogFile();
        File outfile = arguments.getOutFile();
        PrintWriter logW = new PrintWriter(new FileWriter(logfile));

        System.err.printf("\nWriting graphs to %s.\n\n", outfile.getAbsolutePath());

        List<Integer> sentenceIndices = IntStream.rangeClosed(0, supertags.size() - 1).boxed().collect(Collectors.toList()); //-1 here because rangeClosed is inclusive
        if (arguments.sort) {
            sentenceIndices.sort(Comparator.comparingInt(a -> supertags.get(a).size()));
        }

//        final ProgressBar pb = new ProgressBar("Parsing", sentenceIndices.size());

        final MutableLong totalParsingTimeNs = new MutableLong(0);

        for (int i : sentenceIndices) { // loop over corpus
            if (arguments.parseOnly == null || i == arguments.parseOnly) {  // restrict to given sentence
                //if (tagp.get(i).getLength() == 1) {
                final int ii = i;
//                ForkJoinPool forkJoinPool = new ForkJoinPool(1);
                MutableBoolean success = new MutableBoolean(false);

//                System.err.printf("\n[%02d] EDGES:\n", ii);
                //edgep.get(ii).prettyprint(edgeLabelLexicon, System.err);
//                forkJoinPool.execute(() -> {
                    Parser parser = null;
                    Parser.ParsingResult projResult = new Parser.ParsingResult(null, null, Double.NaN);//to be overridden
                    ParsingResult parsingResult = null;
    //                    String result = "(u / unparseable)";
                    CpuTimeStopwatch w = new CpuTimeStopwatch();

                    try {

                        //projective comparison
                        w.record();
                        AmConllSentence sent = corpus.get(ii);
                        if (sent.size() > arguments.sentenceLengthLimit) {
                            System.err.println("Skipping sentence "+ii+" because its length "+sent.size()+"is longer than the limit "+arguments.sentenceLengthLimit);
                            System.err.println("Use setting --sentenceLengthLimit to change that limit");
                        } else {

                            parser = Parser.createNonlabellingParserWithDefaultValues(supertags.get(ii), edgeLabel2pos2pos2prob.get(ii), sent.words(), 6);

                            w.record();

                            //the following lines write the IRTG rules (in incomplete form) to text files, sorted by probability
                            //                        Map<String, Double> label2weight = new HashMap<>();
                            //                        TreeAutomaton<String> irtgAuto = parser.getIrtg().getAutomaton();
                            //                        for (Rule rule : irtgAuto.getRuleSet()) {
                            //                            String ruleLabel = rule.getLabel(irtgAuto);
                            //                            ruleLabel = ruleLabel.substring(0, ruleLabel.lastIndexOf("_"));
                            //                            double ruleWeight = Math.max(rule.getWeight(), label2weight.getOrDefault(ruleLabel, 0.0));
                            //                            label2weight.put(ruleLabel, ruleWeight);
                            //                        }
                            //                        List<String> sortedLabels = new ArrayList<>(label2weight.keySet());
                            //                        sortedLabels.sort(new Comparator<String>() {
                            //                            @Override
                            //                            public int compare(String o1, String o2) {
                            //                                return - Double.compare(label2weight.get(o1), label2weight.get(o2));
                            //                            }
                            //                        });
                            //                        FileWriter irtgWriter = new FileWriter(arguments.outFilename+"/irtg_"+i+".txt");
                            //                        for (String label : sortedLabels) {
                            //                            irtgWriter.write(label+"  ["+label2weight.get(label)+"]\n");
                            //                        }
                            //                        irtgWriter.close();

                            try {
                                projResult = parser.run(arguments.itemLimit);
                            } catch (java.lang.IllegalStateException ex) {
                                logW.printf("[%04d] skipped\n", ii);
                                logW.flush();
                            }

                            if (projResult.tree == null) {
                                parsingResult = null;
                            } else {
                                IntList leafOrderToStringOrder = irtgTerm2OrderMap(projResult.tree);
                                Tree<Or<String, SupertagWithType>> amTerm = fixAmTerm(parser.getIrtg().getInterpretation("graph").getHomomorphism().apply(projResult.tree));
                                parsingResult = new ParsingResult(amTerm, Math.log(projResult.prob), leafOrderToStringOrder);
                            }
                            //                        System.err.println(graphTreeScore.left.right);
                            //                        System.err.println(amTerm);
                            //                        System.err.println(leafOrderToStringOrder);
                            //
                            //                        System.err.println("parsing result:");
                            //                        System.err.println(parsingResult);
                            w.record();

                            if (parsingResult != null) {
                                sent.setDependenciesFromAmTerm(parsingResult.amTerm, parsingResult.leafOrderToStringOrder);
                            }
                            w.record();

                            // A*

                            if (arguments.compareAstar) {
                                Astar astar = new Astar(scoreReader.getEdgeProbabilities().get(ii), tagp.get(ii), scoreReader.getIdToSupertag(), scoreReader.getSupertagLexicon(), scoreReader.getEdgeLabelLexicon(), typeLexicon, "trivial", false);
                                astar.setBias(0.0);
                                astar.setDeclutterAgenda(false);

                                w.record();

                                Item goalItem = astar.process(-1);
                                ParsingResult parsingResultAstar = astar.decode(goalItem);

                                SGraph graph = parsingResult.evaluate().left;
                                SGraph aStarGraph = parsingResultAstar.evaluate().left;


                                AmConllSentence sentAStar = (AmConllSentence) sent.clone();
                                sentAStar.setDependenciesFromAmTerm(parsingResultAstar.amTerm, parsingResultAstar.leafOrderToStringOrder);

                                if ((Math.abs(parsingResult.logProb - parsingResultAstar.logProb) > 0.001)
                                        || !sent.equals(sentAStar)) {
                                    System.out.println("Difference detected at graph " + ii);
                                    System.out.println("Original projective parser:");
                                    System.out.println(parsingResult.logProb);
                                    System.out.println(parsingResult);
                                    System.out.println(graph);
                                    System.out.println("A* parser:");
                                    System.out.println(parsingResultAstar.logProb);
                                    System.out.println(parsingResultAstar);
                                    System.out.println(aStarGraph);
                                }

                            }
                        }

                    } catch (Throwable e) {
                        System.err.println(ii);
                        System.err.println(corpus.get(ii));
                        e.printStackTrace();
                    } finally {

    //                        String reportString = (astar == null || astar.getRuntimeStatistics() == null)
    //                                ? String.format("[%04d] no runtime statistics available", ii)
    //                                : String.format("[%04d] %s %s", ii, sent.getId(), astar.getRuntimeStatistics().toString());

    //                            logW.println(reportString);
                        logW.printf("[%04d] init %.1f ms; parse %.1f ms; evaluate %.1f ms\n", ii,
                                w.getMillisecondsBefore(1),
                                w.getMillisecondsBefore(2),
                                w.getMillisecondsBefore(3));
                        double logProb = parsingResult != null ? parsingResult.logProb : Double.NaN;
                        logW.printf("[%04d] logprob: %.1f\n", ii, logProb);
                        logW.flush();

                        if (parsingResult != null) {
                            totalParsingTimeNs.incValue(w.getTimeBefore(1) + w.getTimeBefore(2) + w.getTimeBefore(3));
                            success.setValue(true);
                        }

    //                        synchronized (pb) {
    //                            pb.step();
    //                        }
                    }

//                });
//                forkJoinPool.shutdown();
//                forkJoinPool.awaitTermination(arguments.timeout, TimeUnit.SECONDS);
//                if (!success.booleanValue()) {
//                    totalParsingTimeNs.incValue(new Double(arguments.timeout*1.e9).longValue());
//                }
//                logW.printf("[%04d] did not complete in %.1f ms\n", ii, arguments.timeout*1.e3);
//                logW.printf("[%04d] logprob: %.1f\n", ii, Double.NaN);
//                logW.flush();
            }
        }


//        pb.close();
        logW.close();

        // write parsed corpus to output file
        AmConllSentence.write(new FileWriter(arguments.getOutFile()), corpus);

        System.out.printf(Locale.ROOT, "Total parsing time: %.1f seconds.\n", totalParsingTimeNs.longValue() / 1.e9);
    }

    private static Tree<Or<String, SupertagWithType>> fixAmTerm(Tree<String> amTerm) {
        //fix lex notation
        Tree<String> lexFixed = amTerm.dfs((tree, list) -> Tree.create(tree.getLabel().replaceAll("\"LEX@[0-9]+\"", "--LEX--"), list));
        // turn leaves into supertags
        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();
        Tree<Or<String, SupertagWithType>> ret = lexFixed.dfs(
                new TreeBottomUpVisitor<String, Tree<Or<String, SupertagWithType>>>() {
                    @Override
                    public Tree<Or<String, SupertagWithType>> combine(Tree<String> node, List<Tree<Or<String, SupertagWithType>>> childrenValues) {
                        if (childrenValues.isEmpty()) {
                            Pair<SGraph, Type> constant;
                            try {
                                constant = alg.parseString(node.getLabel());
                            } catch (ParserException e) {
                                throw new IllegalArgumentException();
                            }
                            return Tree.create(Or.createRight(new SupertagWithType(constant.left, constant.right)));
                        } else {
                            return Tree.create(Or.createLeft(node.getLabel()), childrenValues);
                        }
                    }
                }
        );

        return ret;
        //return lexFixed.dfs((tree, list) -> Tree.create(tree.getLabel().split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP)[0], list));
    }

    private static Function<String, Type> getSupertagToTypeFunction() {
        return (supertag) -> {
            try {
                return new Type(supertag.split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP)[1]);
            } catch (ParseException e) {
                e.printStackTrace();
                return null;
            }
        };
    }

    private static IntList irtgTerm2OrderMap(Tree<String> irtgTerm) {
        IntList ret;
        if (irtgTerm.getLabel().startsWith("const_")) {
            assert irtgTerm.getChildren().isEmpty();
            int id = Integer.parseInt(irtgTerm.getLabel().split("_")[1]);
            ret = new IntArrayList();
            ret.add(id);
        } else if (irtgTerm.getLabel().startsWith("NULL_")) {
            assert irtgTerm.getChildren().size() == 1;
            ret = irtgTerm2OrderMap(irtgTerm.getChildren().get(0));
        } else {
            assert irtgTerm.getChildren().size() == 2;
            ret = new IntArrayList();
            ret.addAll(irtgTerm2OrderMap(irtgTerm.getChildren().get(0)));
            ret.addAll(irtgTerm2OrderMap(irtgTerm.getChildren().get(1)));
        }
        return ret;
    }
}
