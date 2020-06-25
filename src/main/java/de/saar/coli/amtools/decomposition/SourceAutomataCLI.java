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
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.util.Counter;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.irtg.util.ProgressListener;
import de.up.ling.tree.Tree;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class SourceAutomataCLI {

    @Parameter(names = {"--trainingCorpus", "-t"}, description = "Path to the input training corpus (*.sdp file)")//, required = true)
    private String trainingCorpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\minimalDev.sdp";

    @Parameter(names = {"--devCorpus", "-d"}, description = "Path to the input dev corpus (*.sdp file)")//, required = true)
    private String devCorpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\minimalDev.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where amconll and supertag dictionary files are created")//, required = true)
    private String outPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\";


    @Parameter(names = {"--corpusType", "-ct"}, description = "values can be DM, PAS or PSD, default is DM")//, required = true)
    private String corpusType = "DM";

    @Parameter(names = {"--nrSources", "-s"}, description = "how many sources to use")//, required = true)
    private int nrSources = 1;

    @Parameter(names = {"--iterations"}, description = "max number of EM iterations")//, required = true)
    private int iterations = 100;

    @Parameter(names = {"--difference"}, description = "difference in log likelihood for early EM stopping")//, required = true)
    private double difference = 0.1;

    //TODO document what each option does
    @Parameter(names = {"--algorithm", "-a"}, description = "options: EM, random, arbitraryViterbi, automata")//, required = true)
    private String algorithm = "automata";

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    public static void main(String[] args) throws IOException, ParserException {

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




        AMRBlobUtils blobUtils;
        switch (cli.corpusType) {
            case "DM": blobUtils = new DMBlobUtils(); break;
            case "PAS": blobUtils = new PASBlobUtils(); break;
            case "PSD": blobUtils = new PSDBlobUtils(); break;
            default: throw new IllegalArgumentException("Illegal corpus type '"+cli.corpusType+"'. Legal are 'DM', 'PAS' and 'PSD'.");
        }
        SupertagDictionary supertagDictionary = new SupertagDictionary();//future: load from file for dev set (better: get dev scores from training EM)

        //get automata for training set
        GraphReader2015 gr = new GraphReader2015(cli.trainingCorpusPath);

        List<TreeAutomaton<?>> concreteDecompositionAutomata = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomata = new ArrayList<>();
        List<DecompositionPackage> decompositionPackages = new ArrayList<>();

        cli.processCorpus(gr, blobUtils, concreteDecompositionAutomata, originalDecompositionAutomata, decompositionPackages);


        //get automata for dev set
        GraphReader2015 grDev = new GraphReader2015(cli.devCorpusPath);

        List<TreeAutomaton<?>> concreteDecompositionAutomataDev = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomataDev = new ArrayList<>();
        List<DecompositionPackage> decompositionPackagesDev = new ArrayList<>();

        cli.processCorpus(grDev, blobUtils, concreteDecompositionAutomataDev, originalDecompositionAutomataDev, decompositionPackagesDev);


        if (cli.algorithm.equals("automata")) {
            ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();
            for (int i = 0; i<originalDecompositionAutomata.size(); i++) {
                SourceAssignmentAutomaton decomp = originalDecompositionAutomata.get(i);
                DecompositionPackage decompositionPackage = decompositionPackages.get(i);

                Map<SourceAssignmentAutomaton.State, Integer> stateToWordPosition = new HashMap<>();
                ConcreteTreeAutomaton<String> fakeIRTGAutomaton = new ConcreteTreeAutomaton<>();

                decomp.processAllRulesBottomUp(rule -> {
                    if (rule.getArity() == 0) {
                        try {
                            // we want a new rule format
                            // state.toString() -> wordPosition_supertag
                            // which is, below, parent.toString() -> newRuleLabel
                            String oldRuleLabel = rule.getLabel(decomp);
                            Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant = alg.parseString(oldRuleLabel);
                            int wordPosition = decompositionPackage.getSentencePositionForGraphFragment(constant.left);
                            SourceAssignmentAutomaton.State parent = decomp.getStateForId(rule.getParent());
                            // obtain delexicalized graph fragment
                            GraphNode lexicalNode = decompositionPackage.getLexNodeFromGraphFragment(constant.left);
                            lexicalNode.setLabel(AmConllEntry.LEX_MARKER);
                            String newRuleLabel = wordPosition+"_"
                                    + constant.left.toIsiAmrStringWithSources()+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+constant.right.toString();
                            //add rule to new automaton, with array of size 0 for children
                            fakeIRTGAutomaton.addRule(fakeIRTGAutomaton.createRule(parent.toString(), newRuleLabel, new String[0]));
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
                        SourceAssignmentAutomaton.State parent = decomp.getStateForId(rule.getParent());
                        SourceAssignmentAutomaton.State child0 = decomp.getStateForId(rule.getChildren()[0]);
                        SourceAssignmentAutomaton.State child1 = decomp.getStateForId(rule.getChildren()[1]);
                        int wordPosition0 = stateToWordPosition.get(child0);
                        int wordPosition1 = stateToWordPosition.get(child1);
                        int parentWordPosition = wordPosition0; // in AM operations, the left child is the head, and this rule reflects that.
                        // add entry to state-to-word-position map
                        stateToWordPosition.put(parent, parentWordPosition);
                        // make and add new rule to new automaton
                        String newRuleLabel = wordPosition0+"_"+wordPosition1+"_"+rule.getLabel(decomp);
                        fakeIRTGAutomaton.addRule(fakeIRTGAutomaton.createRule(parent.toString(), newRuleLabel,
                                new String[]{child0.toString(), child1.toString()}));
                    } else {
                        System.err.println("uh-oh, rule arity was "+rule.getArity());
                    }
                });

                // transfer final states over to new automaton
                for (int finalStateID : decomp.getFinalStates()) {
                    // a whole lot of converting IDs and states and strings..
                    fakeIRTGAutomaton.addFinalState(fakeIRTGAutomaton.getIdForState(decomp.getStateForId(finalStateID).toString()));
                }

                System.out.println(fakeIRTGAutomaton);
                System.out.println(fakeIRTGAutomaton.viterbi());

            }

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

    private void processCorpus(GraphReader2015 gr, AMRBlobUtils blobUtils,
                               List<TreeAutomaton<?>> concreteDecompositionAutomata, List<SourceAssignmentAutomaton> originalDecompositionAutomata,
                                List<DecompositionPackage> decompositionPackages) throws IOException {

        int[] buckets = new int[]{0, 3, 10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000};
        Counter<Integer> bucketCounter = new Counter<>();
        Counter<String> successCounter = new Counter<>();
        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        Graph sdpGraph;
        while ((sdpGraph = gr.readGraph()) != null) {
            if (index % 100 == 0) {
                System.err.println(index);
                bucketCounter.printAllSorted();
            }
            if (true) { //index == 1268
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
                SGraph graph = inst.getGraph();
                if (corpusType.equals("PSD")) {
                    graph = ConjHandler.handleConj(graph, (PSDBlobUtils)blobUtils);
                }


                try {

                    DecompositionPackage decompositionPackage = new SDPDecompositionPackage(sdpGraph, blobUtils);

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton, graph);


                    try {
                        SGraph resultGraph = result.evaluate().left;
                        resultGraph.removeNode("ART-ROOT");

                        graph.setEqualsMeansIsomorphy(false);

                        if (graph.equals(resultGraph)) {
                            SourceAssignmentAutomaton auto = SourceAssignmentAutomaton
                                    .makeAutomatonWithAllSourceCombinations(result, nrSources, decompositionPackage);
                            ConcreteTreeAutomaton<SourceAssignmentAutomaton.State> concreteTreeAutomaton = auto.asConcreteTreeAutomatonBottomUp();
//                            System.out.println(auto.signature);
                            //System.out.println(result);
//                            System.out.println(concreteTreeAutomaton);
//                            System.out.println(concreteTreeAutomaton.viterbi());
                            if (concreteTreeAutomaton.viterbi() != null) {
                                successCounter.add("success");
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
                    } catch (java.lang.Exception ex) {
                        System.err.println(index);
//                        System.err.println(graph.toIsiAmrStringWithSources());
//                        System.err.println(result);
                        ex.printStackTrace();
                        fails++;
                    }
                } catch (DAGComponent.NoEdgeToRequiredModifieeException | DAGComponent.CyclicGraphException ex) {
                    nondecomposeable++;
                } catch (java.lang.Exception ex) {
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

}
