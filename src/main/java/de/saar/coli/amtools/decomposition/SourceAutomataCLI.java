package de.saar.coli.amtools.decomposition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amtools.decomposition.analysis.SupertagEntropy;
import de.saar.coli.amtools.decomposition.automata.component_analysis.ComponentAnalysisToAMDep;
import de.saar.coli.amtools.decomposition.automata.component_analysis.ComponentAutomaton;
import de.saar.coli.amtools.decomposition.automata.component_analysis.DAGComponent;
import de.saar.coli.amtools.decomposition.automata.source_assignment.SAAState;
import de.saar.coli.amtools.decomposition.automata.source_assignment.SourceAssignmentAutomaton;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.saar.coli.amtools.decomposition.formalisms.toolsets.GraphbankDecompositionToolset;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.BinaryIrtgOutputCodec;
import de.up.ling.irtg.util.Counter;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.irtg.util.ProgressListener;
import de.up.ling.tree.Tree;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SourceAutomataCLI {

    @Parameter(names = {"--trainingCorpus", "-t"}, description = "Path to the input training corpus (*.sdp file)")//, required = true)
    private String trainingCorpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\smallDev.sdp";

    @Parameter(names = {"--devCorpus", "-d"}, description = "Path to the input dev corpus (*.sdp file)")//, required = true)
    private String devCorpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\minimalDev.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where zip files (or in legacy versions amconll and supertag dictionary files) are created")//, required = true)
    private String outPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\small\\";


    @Parameter(names = {"--decompositionToolset", "-dt"}, description = "Classname for the GraphbankDecompositionToolset to be used." +
            "If the classpath is in de.saar.coli.amtools.decomposition.formalisms.toolsets, that prefix can be omitted.")//, required = true)
    private String decompositionToolset = "DMDecompositionToolset";


    @Parameter(names = {"--fasterModeForTesting", "-f"}, description = "skips computation of e.g. named entity tags if this flag is set; this can save a lot of time.")
    private boolean fasterModeForTesting =false;

    @Parameter(names = {"--nrSources", "-s"}, description = "how many sources to use")//, required = true)
    private int nrSources = 2;

    @Parameter(names = {"--iterations"}, description = "max number of EM iterations")//, required = true)
    private int iterations = 100;

    @Parameter(names = {"--difference"}, description = "difference in log likelihood for early EM stopping")//, required = true)
    private double difference = 0.1;

    //TODO document what each option does
    @Parameter(names = {"--algorithm", "-a"}, description = "options: EM, random, arbitraryViterbi, automata")//, required = true)
    private String algorithm = "automata";

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    public static void main(String[] args) throws IOException, ParserException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        // read command line arguments
        // cli stands for CommandLineInterface
        SourceAutomataCLI cli = new SourceAutomataCLI();
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

        Class<?> clazz;
        try {
            clazz = Class.forName(cli.decompositionToolset);
        } catch (ClassNotFoundException ex) {
            try {
                clazz = Class.forName("de.saar.coli.amtools.decomposition.formalisms.toolsets." + cli.decompositionToolset);
            } catch (ClassNotFoundException ex2) {
                throw new RuntimeException("Neither class "+cli.decompositionToolset+
                        " nor de.saar.coli.amtools.decomposition.formalisms.toolsets." + cli.decompositionToolset+" could be found! Aborting.");
            }
        }
        Constructor<?> ctor = clazz.getConstructor(Boolean.class);
        GraphbankDecompositionToolset decompositionToolset = (GraphbankDecompositionToolset)ctor.newInstance(new Object[] { cli.fasterModeForTesting});



        SupertagDictionary supertagDictionary = new SupertagDictionary();//future: load from file for dev set (better: get dev scores from training EM)

        //get automata for training set
        System.err.println("Creating automata for training set");
        List<MRInstance> corpusTrain = decompositionToolset.readCorpus(cli.trainingCorpusPath);
        decompositionToolset.applyPreprocessing(corpusTrain);

        List<TreeAutomaton<?>> concreteDecompositionAutomata = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomata = new ArrayList<>();
        List<DecompositionPackage> decompositionPackages = new ArrayList<>();

        processCorpus(corpusTrain, decompositionToolset, cli.nrSources, concreteDecompositionAutomata, originalDecompositionAutomata, decompositionPackages);


        //get automata for dev set
        System.err.println("Creating automata for dev set");
        List<MRInstance> corpusDev = decompositionToolset.readCorpus(cli.devCorpusPath);
        decompositionToolset.applyPreprocessing(corpusDev);

        List<TreeAutomaton<?>> concreteDecompositionAutomataDev = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomataDev = new ArrayList<>();
        List<DecompositionPackage> decompositionPackagesDev = new ArrayList<>();

        processCorpus(corpusDev, decompositionToolset, cli.nrSources, concreteDecompositionAutomataDev, originalDecompositionAutomataDev, decompositionPackagesDev);

        Files.createDirectories(Paths.get(cli.outPath));

        if (cli.algorithm.equals("automata")) {

            createAutomataZip(originalDecompositionAutomata, decompositionPackages, supertagDictionary, "train", cli.outPath);
            createAutomataZip(originalDecompositionAutomataDev, decompositionPackagesDev, supertagDictionary, "dev", cli.outPath);

        } else {


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
                            Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant = alg.parseString(grammarLabel);
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
                            Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant = alg.parseString(label);
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

    private static void processCorpus(List<MRInstance> corpus, GraphbankDecompositionToolset decompositionToolset, int nrSources,
                               List<TreeAutomaton<?>> concreteDecompositionAutomata, List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                                List<DecompositionPackage> decompositionPackages) {

        int[] buckets = new int[]{0, 3, 10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000};
        Counter<Integer> bucketCounter = new Counter<>();
        Counter<String> successCounter = new Counter<>();
        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        Graph sdpGraph;
        for (MRInstance inst : corpus) {
            if (index % 500 == 0) {
                System.err.println("Processing instance " + index);
                bucketCounter.printAllSorted();
            }
            if (true) { //index == 1268
                SGraph graph = inst.getGraph();

                try {

                    DecompositionPackage decompositionPackage = decompositionToolset.makeDecompositionPackage(inst);

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, decompositionToolset.getEdgeHeuristic());

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton);

                    for (Set<String> nodesInConstant : decompositionPackage.getMultinodeConstantNodeNames()) {
                        result = MultinodeContractor.contractMultinodeConstant(result, nodesInConstant, decompositionPackage);
                    }


                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);

                        if (graph.equals(resultGraph)) {
                            SourceAssignmentAutomaton auto = SourceAssignmentAutomaton
                                    .makeAutomatonWithAllSourceCombinations(result, nrSources, decompositionPackage);
                            ConcreteTreeAutomaton<SAAState> concreteTreeAutomaton = auto.asConcreteTreeAutomatonBottomUp();
//                            System.out.println(auto.signature);
                            //System.out.println(result);
//                            System.out.println(concreteTreeAutomaton);
//                            System.out.println(concreteTreeAutomaton.viterbi());
                            if (concreteTreeAutomaton.viterbi() != null) {
                                successCounter.add("success");
                                concreteTreeAutomaton = (ConcreteTreeAutomaton<SAAState>)concreteTreeAutomaton.reduceTopDown();
                                concreteDecompositionAutomata.add(concreteTreeAutomaton);
                                decompositionPackages.add(decompositionPackage);
                                originalDecompositionAutomata.add(auto);
//                                if (concreteTreeAutomaton.getNumberOfRules() < 30) {
//                                    System.err.println(concreteTreeAutomaton);
//                                    System.err.println();
//                                    System.err.println();
//                                }
                            } else {
                                successCounter.add("fail");
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
                            System.err.println(graph.toIsiAmrStringWithSources());
                            System.err.println(resultGraph.toIsiAmrStringWithSources());
                            fails++;
                        }
                    } catch (Exception ex) {
                        System.err.println(index);
//                        System.err.println(graph.toIsiAmrStringWithSources());
//                        System.err.println(result);
                        ex.printStackTrace();
                        fails++;
                    }
                } catch (DAGComponent.NoEdgeToRequiredModifieeException | DAGComponent.CyclicGraphException ex) {
                    nondecomposeable++;
                } catch (Exception | Error ex) {
                    System.err.println(index);
//                    System.err.println(graph.toIsiAmrStringWithSources());
                    ex.printStackTrace();
                    fails++;
                }
            }

            index++;
        }
        bucketCounter.printAllSorted();
        successCounter.printAllSorted();
    }


    static void createAutomataZip(List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                                   List<DecompositionPackage> decompositionPackages,
                                   SupertagDictionary supertagDictionary, String zipFileName, String outPath) throws IOException {
        //create zip file
        System.err.println("Writing zip file "+Paths.get(outPath, zipFileName+ ".zip"));
        ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(Paths.get(outPath, zipFileName+ ".zip").toFile()));

        //write metadata file
        ZipEntry meta = new ZipEntry("meta.txt");
        zipFile.putNextEntry(meta);
        zipFile.write(Integer.toString(originalDecompositionAutomata.size()).getBytes());
        zipFile.closeEntry();

        //create base amconll file
        List<AmConllSentence> baseAmConllSentences = decompositionPackages.parallelStream().map(DecompositionPackage::makeBaseAmConllSentence).collect(Collectors.toList());



        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();
        for (int i = 0; i<originalDecompositionAutomata.size(); i++) {
            if (i % 500 == 0) {
                System.err.println(i);
            }
            SourceAssignmentAutomaton decomp = originalDecompositionAutomata.get(i);
            DecompositionPackage decompositionPackage = decompositionPackages.get(i);
            AmConllSentence amConllSentence = baseAmConllSentences.get(i);

            Map<SAAState, Integer> stateToWordPosition = new HashMap<>();

            ConcreteTreeAutomaton<String> fakeIRTGAutomaton = new ConcreteTreeAutomaton<>();
            Map<Rule, Pair<Integer, String>> rule2supertag = new HashMap<>();
            Map<Rule, Pair<Pair<Integer, Integer>, String>> rule2edge = new HashMap<>();

            try {


                decomp.processAllRulesBottomUp(rule -> {
                    if (rule.getArity() == 0) {
                        try {
                            // we want a new rule format
                            // state.toString() -> wordPosition_supertag
                            // which is, below, parent.toString() -> newRuleLabel
                            String oldRuleLabel = rule.getLabel(decomp);
                            Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant = alg.parseString(oldRuleLabel);
                            int wordPosition = decompositionPackage.getSentencePositionForGraphFragment(constant.left);
                            SAAState parent = decomp.getStateForId(rule.getParent());
                            // obtain delexicalized graph fragment
                            GraphNode lexicalNode = decompositionPackage.getLexNodeFromGraphFragment(constant.left);
                            constant.left.addNode(lexicalNode.getName(), AmConllEntry.LEX_MARKER);
                            //                            String newRuleLabel = wordPosition+"_"
                            //                                    + constant.left.toIsiAmrStringWithSources()+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+constant.right.toString();


                            String constantString;
                            try {
                                constantString = supertagDictionary.getRepr(constant.left) + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + constant.right.toString();
                            } catch (UnsupportedOperationException ex) {
                                System.err.println("Could not linearize graph constant"+constant.left.toString());
                                System.err.println("Skipping this instance");
                                throw new IllegalArgumentException(ex);
                            }
                            String newRuleLabel = constantString;
                            //add rule to new automaton, with array of size 0 for children
                            Rule newRule = fakeIRTGAutomaton.createRule(parent.toString(), newRuleLabel, new String[0]);
                            fakeIRTGAutomaton.addRule(newRule);
                            rule2supertag.put(newRule, new Pair<>(wordPosition, constantString));
                            // add entry to state-to-word-position map
                            stateToWordPosition.put(parent, wordPosition);
                        } catch (ParserException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (rule.getArity() == 2) {
                        // we want a new rule format
                        // state.toString() -> i_j_oldRuleLabel(state0.toString(), state1.toString())
                        // where below state = parent, state0 = child0, state1 = child1
                        // and i = child0 wordPosition, j = child1 wordPosition
                        SAAState parent = decomp.getStateForId(rule.getParent());
                        SAAState child0 = decomp.getStateForId(rule.getChildren()[0]);
                        SAAState child1 = decomp.getStateForId(rule.getChildren()[1]);
                        int wordPosition0 = stateToWordPosition.get(child0);
                        int wordPosition1 = stateToWordPosition.get(child1);
                        int parentWordPosition = wordPosition0; // in AM operations, the left child is the head, and this rule reflects that.
                        // add entry to state-to-word-position map
                        stateToWordPosition.put(parent, parentWordPosition);
                        // make and add new rule to new automaton
                        //                        String newRuleLabel = wordPosition0+"_"+wordPosition1+"_"+rule.getLabel(decomp);
                        String operationLabel = rule.getLabel(decomp);
                        String newRuleLabel = operationLabel;
                        Rule newRule = fakeIRTGAutomaton.createRule(parent.toString(), newRuleLabel,
                                new String[]{child0.toString(), child1.toString()});
                        fakeIRTGAutomaton.addRule(newRule);
                        rule2edge.put(newRule, new Pair<>(new Pair<>(wordPosition0, wordPosition1), operationLabel));

                        //add edge existence into amconll sentence
                        amConllSentence.get(wordPosition1 - 1).setHead(wordPosition0);
                    } else {
                        System.err.println("uh-oh, rule arity was " + rule.getArity());
                    }
                });

                // transfer final states over to new automaton
                for (int finalStateID : decomp.getFinalStates()) {
                    // a whole lot of converting IDs and states and strings..
                    fakeIRTGAutomaton.addFinalState(fakeIRTGAutomaton.getIdForState(decomp.getStateForId(finalStateID).toString()));
                }

                //                System.out.println(fakeIRTGAutomaton);
                //                System.out.println(fakeIRTGAutomaton.viterbi());

                //                for (Rule rule : rule2supertag.keySet()) {
                //                    System.out.println(rule.toString(fakeIRTGAutomaton));
                //                    System.out.println(rule2supertag.get(rule));
                //                }

                //write supertagmap
                ZipEntry supertagMapZip = new ZipEntry(i + ".supertagmap");
                zipFile.putNextEntry(supertagMapZip);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(zipFile);
                objectOutputStream.writeObject(rule2supertag);
                objectOutputStream.flush();
                zipFile.closeEntry();

                //write edgemap
                ZipEntry edgeMapZip = new ZipEntry(i + ".edgemap");
                zipFile.putNextEntry(edgeMapZip);
                objectOutputStream = new ObjectOutputStream(zipFile);
                objectOutputStream.writeObject(rule2edge);
                objectOutputStream.flush();
                zipFile.closeEntry();

                //write automaton
                ZipEntry automatonZip = new ZipEntry(i + ".irtb");
                zipFile.putNextEntry(automatonZip);
                BinaryIrtgOutputCodec codec = new BinaryIrtgOutputCodec();
                codec.write(new InterpretedTreeAutomaton(fakeIRTGAutomaton), zipFile);
                zipFile.closeEntry();

                //                FileInputStream fileInputStream = new FileInputStream(file);
                //                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                //                HashMap<Rule, Pair<Integer, String>> fileObj = (HashMap<Rule, Pair<Integer, String>>) objectInputStream.readObject();
                //                objectInputStream.close();
                //                for (Rule rule : fileObj.keySet()) {
                //                    System.out.println(rule.toString(fakeIRTGAutomaton));
                //                    System.out.println(fileObj.get(rule));
                //                }
            } catch (IllegalArgumentException ex) {
                System.err.println("Skipped instance "+i+" due to the following error:");
                ex.printStackTrace();
            }

        }

        //write AMConll file
        ZipEntry amconllZip = new ZipEntry("corpus.amconll");
        zipFile.putNextEntry(amconllZip);
        Writer amConllWriter = new OutputStreamWriter(zipFile);
        AmConllSentence.write(amConllWriter, baseAmConllSentences);
        amConllWriter.flush();
        zipFile.closeEntry();

        //write supertag dictionary
        ZipEntry supertagZip = new ZipEntry("supertags.txt");
        zipFile.putNextEntry(supertagZip);
        Writer supertagWriter = new OutputStreamWriter(zipFile);
        supertagDictionary.writeToWriter(supertagWriter);
        supertagWriter.flush();
        zipFile.closeEntry();


        zipFile.finish();
    }




}
