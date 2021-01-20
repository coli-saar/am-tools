package de.saar.coli.amtools.decomposition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.NamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordNamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.StanfordPreprocessedData;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.irtg.util.ProgressListener;
import de.up.ling.tree.Tree;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class SourceAutomataCLIAMR {

    @Parameter(names = {"--trainingCorpus", "-t"}, description = "Path to the input training corpus (*.sdp file)")//, required = true)
    private String trainingCorpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\smallDev.sdp";

    @Parameter(names = {"--devCorpus", "-d"}, description = "Path to the input dev corpus (*.sdp file)")//, required = true)
    private String devCorpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\minimalDev.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where amconll and supertag dictionary files are created")//, required = true)
    private String outPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\small\\";


    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz", required = true)
    private String stanfordNerFilename;

    @Parameter(names = {"--stanford-pos-model"}, description = "Path to the stanford POS tagger model file english-bidirectional-distsim.tagger", required = true)
    private String stanfordPosFilename;

    @Parameter(names = {"--nrSources", "-s"}, description = "how many sources to use")//, required = true)
    private int nrSources = 4;


    @Parameter(names = {"--useLexLabelReplacement"}, description = "If true, does the preprocessing step where lexical node labels can be replaced with lemma/token replacement markers")
    private boolean useLexLabelReplacement=false;

    @Parameter(names = {"--iterations"}, description = "max number of EM iterations")//, required = true)
    private int iterations = 100;

    @Parameter(names = {"--difference"}, description = "difference in log likelihood for early EM stopping")//, required = true)
    private double difference = 0.1;

    //TODO document what each option does
    @Parameter(names = {"--algorithm", "-a"}, description = "options: EM, random, arbitraryViterbi, automata")//, required = true)
    private String algorithm = "automata";

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    public static void main(String[] args) throws IOException, ParserException, ClassNotFoundException, CorpusReadingException {

        // read command line arguments
        // cli stands for CommandLineInterface
        SourceAutomataCLIAMR cli = new SourceAutomataCLIAMR();
        JCommander commander = new JCommander(cli);
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        if (cli.help) {
            commander.usage();
            return;
        }


        NamedEntityRecognizer neRecognizer = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename), true);


        AMRBlobUtils blobUtils = new AMRBlobUtils();
        SupertagDictionary supertagDictionary = new SupertagDictionary();//future: load from file for dev set (better: get dev scores from training EM)

        // load data
        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation(new Interpretation<>(new GraphAlgebra(), new Homomorphism(dummySig, dummySig), "repgraph"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "repstring"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "string"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "spanmap"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "id"));
        loaderIRTG.addInterpretation(new Interpretation<>(new StringAlgebra(), new Homomorphism(dummySig, dummySig), "repalignment"));



        //get automata for training set

        Corpus corpusTrain = Corpus.readCorpusWithStrictFormatting(new FileReader(cli.trainingCorpusPath), loaderIRTG);

        PreprocessedData preprocessedDataTrain = new StanfordPreprocessedData(cli.stanfordPosFilename);
        ((StanfordPreprocessedData) preprocessedDataTrain).readTokenizedFromCorpus(corpusTrain);

        List<TreeAutomaton<?>> concreteDecompositionAutomata = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomata = new ArrayList<>();
        List<DecompositionPackage> decompositionPackages = new ArrayList<>();

        cli.processCorpus(corpusTrain, blobUtils, concreteDecompositionAutomata, originalDecompositionAutomata, decompositionPackages,
                preprocessedDataTrain, neRecognizer, cli.useLexLabelReplacement);


        //get automata for dev set
        Corpus corpusDev = Corpus.readCorpusWithStrictFormatting(new FileReader(cli.devCorpusPath), loaderIRTG);


        PreprocessedData preprocessedDataDev = new StanfordPreprocessedData(cli.stanfordPosFilename);
        ((StanfordPreprocessedData) preprocessedDataDev).readTokenizedFromCorpus(corpusDev);

        List<TreeAutomaton<?>> concreteDecompositionAutomataDev = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomataDev = new ArrayList<>();
        List<DecompositionPackage> decompositionPackagesDev = new ArrayList<>();

        cli.processCorpus(corpusDev, blobUtils, concreteDecompositionAutomataDev, originalDecompositionAutomataDev, decompositionPackagesDev,
                preprocessedDataDev, neRecognizer, cli.useLexLabelReplacement);

        Files.createDirectories(Paths.get(cli.outPath));

        if (cli.algorithm.equals("automata")) {

            SourceAutomataCLI.createAutomataZip(originalDecompositionAutomata, decompositionPackages, supertagDictionary, "train", cli.outPath);
            SourceAutomataCLI.createAutomataZip(originalDecompositionAutomataDev, decompositionPackagesDev, supertagDictionary, "dev", cli.outPath);

        } else {

            // WARNING code below is not tested for AMR yet

            if (cli.algorithm.equals("EM")) {

                ConcreteTreeAutomaton<String> grammarAutomaton = new ConcreteTreeAutomaton<>();
                String dummyState = "X";
                grammarAutomaton.addFinalState(grammarAutomaton.addState(dummyState));
                Random random = new Random();
                List<Map<Rule, Rule>> dataRuleToGrammarRule = new ArrayList<>();
                ListMultimap<Rule, Rule> grammarRuleToDataRules = ArrayListMultimap.create();
                SupertagDictionary grammarSupertagDictionary = new SupertagDictionary();

                ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();

                for (TreeAutomaton<?> dataAutomaton : concreteDecompositionAutomata) {
                    Map<Rule, Rule> rulesMapForThisAuto = new HashMap<>();
                    dataRuleToGrammarRule.add(rulesMapForThisAuto);
                    for (Rule dataRule : dataAutomaton.getRuleSet()) {
                        List<String> children = new ArrayList<>();
                        for (int child : dataRule.getChildren()) {
                            children.add(dummyState);
                        }
                        String grammarLabel = dataRule.getLabel(dataAutomaton);
                        // delexicalize the constants in grammar, for now assuming that the root is the lexical label.
                        if (dataRule.getArity() == 0) {
                            Pair<SGraph, Type> constant = alg.parseString(grammarLabel);
                            String nodeName = constant.left.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME); //TODO make this use the decomposition package
                            constant.left.getNode(nodeName).setLabel(AmConllEntry.LEX_MARKER);
                            grammarLabel = grammarSupertagDictionary.getRepr(constant.left) + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + constant.right.toString();
                        }

                        Rule grammarRule = grammarAutomaton.createRule(dummyState, grammarLabel, children, random.nextDouble());
                        rulesMapForThisAuto.put(dataRule, grammarRule);
                        grammarRuleToDataRules.put(grammarRule, dataRule);//can just do it like this, if same grammar rule shows up multiple times, the ListMultimap will keep multiple entries
                        grammarAutomaton.addRule(grammarRule);
                    }
                }


                System.out.println(grammarAutomaton);

                MutableInteger iterationCounter = new MutableInteger();
                ProgressListener listener = (currentValue, maxValue, string) -> {
                    if (currentValue == 1) {
                        System.out.println("Starting EM iteration " + iterationCounter.getValue());
                        iterationCounter.incValue();
                    }
                    if (currentValue % 1000 == 0) {
                        System.out.println("E-step Automaton " + currentValue + "/" + maxValue);
                    }
                };

                Pair<Integer, Double> iterationAndDiff = grammarAutomaton.trainEM(concreteDecompositionAutomata,
                        dataRuleToGrammarRule, grammarRuleToDataRules, cli.iterations, cli.difference, false, listener);

                System.out.println("EM stopped after iteration " + iterationAndDiff.left + " with difference " + iterationAndDiff.right);

                System.out.println(grammarAutomaton);


                Map<String, Rule> label2grammarRule = new HashMap<>();
                for (Rule grammarRule : grammarAutomaton.getRuleSet()) {
                    label2grammarRule.put(grammarRule.getLabel(grammarAutomaton), grammarRule);
                }

                // assign weights to dev set
                for (TreeAutomaton<?> devAutomaton : concreteDecompositionAutomataDev) {
                    for (Rule devRule : devAutomaton.getRuleSet()) {
                        String label = devRule.getLabel(devAutomaton);
                        if (devRule.getArity() == 0) {
                            Pair<SGraph, Type> constant = alg.parseString(label);
                            String nodeName = constant.left.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME); //TODO make this use the decomposition package
                            constant.left.getNode(nodeName).setLabel(AmConllEntry.LEX_MARKER);
                            label = grammarSupertagDictionary.getRepr(constant.left) + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + constant.right.toString();
                        }
                        if (label2grammarRule.containsKey(label)) {
                            devRule.setWeight(label2grammarRule.get(label).getWeight());
                        } else {
                            devRule.setWeight(random.nextDouble());//just use random weight, don't matter too much
                        }
                    }
                }
            }


            //write training set
            List<AmConllSentence> outputCorpus = new ArrayList<>();
            Iterator<DecompositionPackage> decompositionPackageIterator = decompositionPackages.iterator();
            Iterator<SourceAssignmentAutomaton> originalAutomataIterator = originalDecompositionAutomata.iterator();

            for (TreeAutomaton<?> dataAutomaton : concreteDecompositionAutomata) {
                Tree<String> chosenTree;
                if (cli.algorithm.equals("EM") || cli.algorithm.equals("arbitraryViterbi")) {
                    chosenTree = dataAutomaton.viterbi();
                } else if (cli.algorithm.equals("random")) {
                    chosenTree = dataAutomaton.getRandomTree();
                } else {
                    throw new IllegalArgumentException("Algorithm must be EM, random or arbitraryViterbi");
                }
                DecompositionPackage decompositionPackage = decompositionPackageIterator.next();
                outputCorpus.add(originalAutomataIterator.next().tree2amConll(chosenTree, decompositionPackage, supertagDictionary));
            }

            System.out.println("Entropy in train.amconll file: " + SupertagEntropy.computeSupertagEntropy(outputCorpus));

            File trainPath = Paths.get(cli.outPath).toFile(); //,"train"
            trainPath.mkdirs();
            String amConllOutPath = Paths.get(cli.outPath, "train.amconll").toString();//,"train"
            AmConllSentence.writeToFile(amConllOutPath, outputCorpus);

            //write dev set
            List<AmConllSentence> outputCorpusDev = new ArrayList<>();
            Iterator<DecompositionPackage> decompositionPackageIteratorDev = decompositionPackagesDev.iterator();
            Iterator<SourceAssignmentAutomaton> originalAutomataIteratorDev = originalDecompositionAutomataDev.iterator();

            for (TreeAutomaton<?> dataAutomaton : concreteDecompositionAutomataDev) {
                Tree<String> chosenTree;
                if (cli.algorithm.equals("EM") || cli.algorithm.equals("arbitraryViterbi")) {
                    chosenTree = dataAutomaton.viterbi();
                } else if (cli.algorithm.equals("random")) {
                    chosenTree = dataAutomaton.getRandomTree();
                } else {
                    throw new IllegalArgumentException("Algorithm must be EM, random or arbitraryViterbi");
                }
                DecompositionPackage decompositionPackage = decompositionPackageIteratorDev.next();
                outputCorpusDev.add(originalAutomataIteratorDev.next().tree2amConll(chosenTree, decompositionPackage, supertagDictionary));
                //TODO the above does not add the ROOT edge label to the root!
            }

            File devPath = Paths.get(cli.outPath).toFile();//,"gold-dev"
            devPath.mkdirs();
            String amConllOutPathDev = Paths.get(cli.outPath, "dev.amconll").toString();//,"gold-dev"
            AmConllSentence.writeToFile(amConllOutPathDev, outputCorpusDev);

            //write supertag dictionary
            String supertagDictionaryPath = Paths.get(cli.outPath, "supertagDictionary.txt").toString();//,"train"
            supertagDictionary.writeToFile(supertagDictionaryPath);
        }
    }

    private void processCorpus(Corpus corpus, AMRBlobUtils blobUtils,
                               List<TreeAutomaton<?>> concreteDecompositionAutomata, List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                               List<DecompositionPackage> decompositionPackages, PreprocessedData preprocessedData, NamedEntityRecognizer neRecognizer,
                               boolean useLexLabelReplacement) throws IOException {

        int[] buckets = new int[]{0, 3, 10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000};
        Counter<Integer> bucketCounter = new Counter<>();
        Counter<String> outcomeCounter = new Counter<>();
        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        for (Instance corpusInstance : corpus) {
            if (index % 500 == 0) {
                System.err.println(index);
                bucketCounter.printAllSorted();
            }
            if (true) {
                SGraph graph = (SGraph)corpusInstance.getInputObjects().get("repgraph");

                try {

                    AMRDecompositionPackage decompositionPackage = new AMRDecompositionPackage(corpusInstance, blobUtils, preprocessedData, neRecognizer, useLexLabelReplacement);


                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton);

                    if (getDepthMax400(result) >= 400) {
                        throw new IllegalArgumentException("unexpectedly high depth in AM tree!");
                    }


                    for (Set<String> nodesInConstant : decompositionPackage.getMultinodeConstantNodeNames()) {
                        result = contractMultinodeConstant(result, nodesInConstant, decompositionPackage, outcomeCounter);
                    }


                    SGraph resultGraph = result.evaluate().left;
                    resultGraph.removeNode("ART-ROOT");

                    graph.setEqualsMeansIsomorphy(false);

                    if (graph.equals(resultGraph)) {
                        SourceAssignmentAutomaton auto = SourceAssignmentAutomaton
                                .makeAutomatonWithAllSourceCombinations(result, nrSources, decompositionPackage);
                        ConcreteTreeAutomaton<SourceAssignmentAutomaton.State> concreteTreeAutomaton = auto.asConcreteTreeAutomatonBottomUp();
//                            System.out.println(auto.signature);
                        //System.out.println(result);
//                        System.out.println(concreteTreeAutomaton);
//                            System.out.println(concreteTreeAutomaton.viterbi());
                        if (concreteTreeAutomaton.viterbi() != null) {
                            outcomeCounter.add("success");
                            concreteTreeAutomaton = (ConcreteTreeAutomaton<SourceAssignmentAutomaton.State>)concreteTreeAutomaton.reduceTopDown();
                            concreteDecompositionAutomata.add(concreteTreeAutomaton);
                            decompositionPackages.add(decompositionPackage);
                            originalDecompositionAutomata.add(auto);
//                                if (concreteTreeAutomaton.getNumberOfRules() < 30) {
//                                    System.err.println(concreteTreeAutomaton);
//                                    System.err.println();
//                                    System.err.println();
//                                }
                        } else {
                            outcomeCounter.add("fail");
                            outcomeCounter.add("subfail no viterbi tree");
                        }
//                            System.out.println(concreteTreeAutomaton.reduceTopDown().getNumberOfRules());
                        int automatonSize = (int)concreteTreeAutomaton.reduceTopDown().getNumberOfRules();
                        OptionalInt bucket = Arrays.stream(buckets).filter(bucketSize -> automatonSize > bucketSize).max();
                        if (bucket.isPresent()) {
                            bucketCounter.add(bucket.getAsInt());
                        }
//                            System.out.println();
                    } else {
                        System.err.println(index);
                        System.err.println("different evaluation result");
                        System.err.println(graph.toIsiAmrStringWithSources());
                        System.err.println(resultGraph.toIsiAmrStringWithSources());
                        outcomeCounter.add("fail");
                        outcomeCounter.add("subfail different evaluation result");
                    }
                } catch (DAGComponent.NoEdgeToRequiredModifieeException ex ) {
                    nondecomposeable++;
                    outcomeCounter.add("fail");
                    outcomeCounter.add("subfail deep decomp issue: NoEdgeToRequiredModifieeException");
                } catch (DAGComponent.CyclicGraphException ex) {
                    nondecomposeable++;
                    outcomeCounter.add("fail");
                    outcomeCounter.add("subfail deep decomp issue: CyclicGraphException");
                } catch (Exception ex) {
                    System.err.println(index);
                    System.err.println("Error found in this graph");
                    System.err.println(graph.toIsiAmrStringWithSources());
//                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                    outcomeCounter.add("fail");
                    outcomeCounter.add("subfail "+ex.toString());
                }
            }

            index++;
        }
        bucketCounter.printAllSorted();
        outcomeCounter.printAllSorted();
    }


    private static AMDependencyTree contractMultinodeConstant(AMDependencyTree amDep, Set<String> nodesInConstant, DecompositionPackage decompositionPackage,
                                                              Counter<String> outcomeCounter) {

        List<AMDependencyTree> attachInThisTree = new ArrayList<>();
        Set<Pair<String, AMDependencyTree>> replaceThis = new HashSet<>();

        Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> result;
        try {
            result = buildContractedTree(amDep, attachInThisTree,
                    replaceThis, nodesInConstant, decompositionPackage, false);
        } catch (IllegalArgumentException ex) {
            outcomeCounter.add("subfail illegal MOD move");
            throw ex;
        }
        AMDependencyTree toBeInserted = result.left;
        List<Pair<String, AMDependencyTree>> attachBelow = result.right;

        int virtuallyAttachAtTop = isInNodeset(amDep.getHeadGraph(), decompositionPackage, nodesInConstant)? 1 : 0;

        if (attachInThisTree.size() +virtuallyAttachAtTop > 1) {
            outcomeCounter.add("subfail disconnected alignment");
            throw new IllegalArgumentException("Constant to be contracted is disconnected");
        } else {
            try {
                toBeInserted.evaluate();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Constant to be contracted leads to illegal evaluation");
            }
            AMDependencyTree replacement = new AMDependencyTree(toBeInserted.evaluate(), attachBelow.toArray(new Pair[attachBelow.size()]));
            if (attachInThisTree.isEmpty()) {
                return replacement;
            } else {
                AMDependencyTree attachHere = attachInThisTree.iterator().next();
                attachHere.removeEdge(replaceThis.iterator().next().left, replaceThis.iterator().next().right);
                attachHere.addEdge(replaceThis.iterator().next().left, replacement);
                return amDep;
            }
        }

    }


    /**
     * returns the AMDependency tree that will give the contracted constant when evaluated, as well as the list of edges
     * that need to be attached at its top. Calls itself recursively.
     * @param amDep
     * @param attachInThisTree attach the contraction result below this tree (is a set to determine invalid results:
     * @param replaceThis
     * @param nodesInConstant
     * @param decompositionPackage
     * @return
     */
    public static Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> buildContractedTree(AMDependencyTree amDep, List<AMDependencyTree> attachInThisTree, Set<Pair<String, AMDependencyTree>> replaceThis,
                                                       Set<String> nodesInConstant, DecompositionPackage decompositionPackage, boolean percolationRequired) {

        if (isInNodeset(amDep.getHeadGraph(), decompositionPackage, nodesInConstant)) {
            AMDependencyTree ret = new AMDependencyTree(amDep.getHeadGraph());
            //then this node is in the constant to be contracted
            List<Pair<String, AMDependencyTree>> attachBelow = new ArrayList<>();
            for (Pair<String, AMDependencyTree> opAndChild : amDep.getOperationsAndChildren()) {
                Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> rec =buildContractedTree(opAndChild.right,
                        attachInThisTree, replaceThis, nodesInConstant, decompositionPackage, true);
                if (isInNodeset(opAndChild.right.getHeadGraph(), decompositionPackage, nodesInConstant)) {
                    AMDependencyTree retHere = rec.left;
                    attachBelow.addAll(rec.right);
                    ret.addEdge(opAndChild.left, retHere);
                    // fix type for evaluation later
                    if (opAndChild.left.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                        for (Pair<String, AMDependencyTree> opAndChildBelow : rec.right) {
                            Type newHeadType = addAllToType(amDep.getHeadGraph().right, opAndChildBelow.right.evaluate().right, getSource(opAndChildBelow.left), null);
                            ret.setHeadGraph(new Pair<>(ret.getHeadGraph().left, newHeadType));
                        }
                    } else {
                        // OP_APPLICATION
                        for (Pair<String, AMDependencyTree> opAndChildBelow : rec.right) {
                            Type newHeadType = addAllToType(amDep.getHeadGraph().right, opAndChildBelow.right.evaluate().right, getSource(opAndChildBelow.left), getSource(opAndChild.left));
                            ret.setHeadGraph(new Pair<>(ret.getHeadGraph().left, newHeadType));
                        }
                    }
                } else {
                    if (percolationRequired && opAndChild.left.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                        throw new IllegalArgumentException("Constant to be contracted leads to illegal MOD percolation");
                    }
                    attachBelow.add(opAndChild);
                }
            }
            return new Pair<>(ret, attachBelow);
        } else {
            //then this node is not in the constant to be contracted. We call the function recursively on the children (passing the result up if there is one)
            // and note whether the whole thing should be attached here.
            Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> ret = null; // will pass up any result from below
            for (Pair<String, AMDependencyTree> opAndChild : amDep.getOperationsAndChildren()) {
                Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> rec = buildContractedTree(opAndChild.right,
                        attachInThisTree, replaceThis, nodesInConstant, decompositionPackage, false);
                if (rec != null) {
                    ret = rec; // don't need to worry about overwriting / multiple results here; that will happen in the parent function (by checking that attachInThisTree is a singleton).
                }
                if (isInNodeset(opAndChild.right.getHeadGraph(), decompositionPackage, nodesInConstant)) {
                    attachInThisTree.add(amDep);
                    replaceThis.add(opAndChild);
                }
            }
            return ret;
        }

    }

    private static boolean isInNodeset(Pair<SGraph, Type> asgraph, DecompositionPackage decompositionPackage, Set<String> nodesInConstant) {
        return nodesInConstant.contains(decompositionPackage.getLexNodeFromGraphFragment(asgraph.left).getName());
    }

    private static String getSource(String applyOperation) {
        return applyOperation.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
    }


    private static Type addAllToType(Type origType, Type typeToAdd, String parentSourceToAdd, String addAsChildOfThisSource) {
        Type ret = origType;
        ret = ret.addSource(parentSourceToAdd);
        if (addAsChildOfThisSource != null) {
            ret = ret.setDependency(addAsChildOfThisSource, parentSourceToAdd, parentSourceToAdd);
        }
        for (String srcToAdd : typeToAdd.getAllSources()) {
            ret = ret.addSource(srcToAdd);
            ret = ret.setDependency(parentSourceToAdd, srcToAdd, srcToAdd);
        }
        for (Type.Edge edge : typeToAdd.getAllEdges()) {
            ret = ret.setDependency(edge.getSource(), edge.getTarget(), edge.getLabel());
        }
        return ret;
    }

    private static int getDepthMax400(AMDependencyTree amDep) {
        return getDepthMax400Recursion(amDep, 1);
    }
    private static int getDepthMax400Recursion(AMDependencyTree amDep, int currentDepth) {
        if (currentDepth >= 400) {
            return 400;
        } else {
            if (amDep.getOperationsAndChildren().isEmpty()) {
                return currentDepth;
            } else {
                int max = 0;
                for (Pair<String, AMDependencyTree> opAndChild : amDep.getOperationsAndChildren()) {
                    int childDepth = getDepthMax400Recursion(opAndChild.right, currentDepth+1);
                    max = Math.max(max, childDepth);
                }
                return max;
            }
        }
    }


}

// TODO add these as tests. Notation is from the NamesDates....AlsFixed_sorted.corpus

//next two should work
//In their decision .
//0-1 1-2 2-3 3-4
//(t2<root> / thing  :ARG1-of (d / decide-01  :ARG0 (t / they)))
//(t2<root> / thing  :ARG1-of (d / decide-01  :ARG0 (t / they)))
//In their decision .
//t!||1-2||1.0 d!|t2||2-3||1.0
//t!||1-2||0.6 d!||2-3||0.64 d!|t2||2-3||0.64
//t!||1-2||0.6 d!||2-3||0.64 d!|t2||2-3||0.64
//bolt-eng-DF-170-181103-8883574_0086.1 ::date 2015-08-20T05:09:43 ::annotator SDL-AMR-09 ::preferred
//t!||1-2||0.6 d!|t2||2-3||0.64
//
// NOTE this is as above, but one edge turned to change MOD to APP, just for testing
//In their decision .
//0-1 1-2 2-3 3-4
//(t2<root> / thing  :ARG1 (d / decide-01  :ARG0 (t / they)))
//(t2<root> / thing  :ARG1 (d / decide-01  :ARG0 (t / they)))
//In their decision .
//t!||1-2||1.0 d!|t2||2-3||1.0
//t!||1-2||0.6 d!||2-3||0.64 d!|t2||2-3||0.64
//t!||1-2||0.6 d!||2-3||0.64 d!|t2||2-3||0.64
//bolt-eng-DF-170-181103-8883574_0086.1 ::date 2015-08-20T05:09:43 ::annotator SDL-AMR-09 ::preferred
//t!||1-2||0.6 d!|t2||2-3||0.64
//
// next two should fail with disjoint alignment
//Interesting ... interesting
//0-1 1-2 2-3
//(a<root> / and  :op2 (i2 / interest-01)  :op1 (i / interest-01))
//(a<root> / and  :op1 (i / interest-01)  :op2 (i2 / interest-01))
//Interesting ... interesting
//i!|i2!||0-1||1.0 a!||1-2||0.1
//i2!||0-1||0.88 i2!||2-3||0.88 i2!|i!||0-1||0.88 i2!|i!||2-3||0.88 i!||0-1||0.88 i!||2-3||0.88
//i2!||0-1||0.88 i2!||2-3||0.88 i2!|i!||0-1||0.88 i2!|i!||2-3||0.88 i!||0-1||0.88 i!||2-3||0.88 a!||1-2||0.1
//bolt12_3988_7647.1 ::date 2012-11-29T18:26:22 ::annotator SDL-AMR-09 ::preferred
//i!|i2!||0-1||0.88
//
//Type of Fraud and Abuse
//0-1 1-2 2-3 3-4 4-5
//(a<root> / and  :op1 (t / type-03  :ARG2 (d / defraud-01))  :op2 (t2 / type-03  :ARG2 (a2 / abuse-01)))
//(a<root> / and  :op1 (t / type-03  :ARG2 (d / defraud-01))  :op2 (t2 / type-03  :ARG2 (a2 / abuse-01)))
//Type of Fraud and Abuse
//t!|t2!||0-1||1.0 d!||2-3||1.0 a!||3-4||1.0 a2!||4-5||1.0
//t!|t2!||0-1||1.0 t2!||0-1||1.0 d!||2-3||0.642857 a!||3-4||1.0 t!||0-1||1.0 a2!||4-5||1.0
//t!|t2!||0-1||1.0 t2!||0-1||1.0 d!||2-3||0.642857 a!||3-4||1.0 t!||0-1||1.0 a2!||4-5||1.0
//bolt-eng-DF-170-181105-8850229_0054.14 ::date 2015-12-05T05:49:56 ::annotator SDL-AMR-09 ::preferred
//t!|t2!||0-1||1.0 d!||2-3||0.642857 a!||3-4||1.0 a2!||4-5||1.0

//  this one should be classified as a problematic multinode-alignment (merges two nodes before the apply is possible)
//  TODO currently just gives null pointer exception
//Thanks for clarifying
//0-1 1-2 2-3
//(t<root> / thank-01  :ARG0 (i / i)  :ARG2 (c / clarify-10  :ARG0 (y / you  :ARG1-of t)))
//(t<root> / thank-01  :ARG0 (i / i)  :ARG1 (y / you  :ARG0-of (c / clarify-10  :ARG2-of t)))
//Thanks for clarifying
//t!|y||0-1||1.0 c!||2-3||1.0 i!||1-2||0.1
//t!|y||0-1||0.1 c!||2-3||1.0 t!||0-1||1.0 i!||2-3||0.02
//t!|y||0-1||0.1 c!||2-3||1.0 t!||0-1||1.0 i!||2-3||0.02
//bolt-eng-DF-170-181103-8882529_0237.6 ::date 2015-11-21T09:37:34 ::annotator SDL-AMR-09 ::preferred
//t!|y||0-1||1.0 c!||2-3||1.0