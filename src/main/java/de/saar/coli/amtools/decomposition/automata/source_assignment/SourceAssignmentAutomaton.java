package de.saar.coli.amtools.decomposition.automata.source_assignment;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amtools.decomposition.deterministic_sources.AmConllWithSourcesCreator;
import de.saar.coli.amtools.decomposition.analysis.SupertagEntropy;
import de.saar.coli.amtools.decomposition.automata.component_analysis.ComponentAnalysisToAMDep;
import de.saar.coli.amtools.decomposition.automata.component_analysis.ComponentAutomaton;
import de.saar.coli.amtools.decomposition.automata.component_analysis.DAGComponent;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.SDPDecompositionPackage;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.siblingfinder.SiblingFinder;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.Tree;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.eclipse.collections.impl.factory.Sets;
import org.jetbrains.annotations.NotNull;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.util.*;

public class SourceAssignmentAutomaton extends TreeAutomaton<SAAState> {

    private final Map<String, Integer> constant2wordID;
    final Map<IntList, List<String>> position2operations;
    private final Map<String, SAAState> constant2state;

    private final int headChildCount;

    SourceAssignmentAutomaton(Signature signature,
                                      Map<String, Integer> constant2wordID,
                                      Map<String, SAAState> constant2state,
                                      Map<IntList, List<String>> position2operations) {
        super(signature);
        this.constant2wordID = constant2wordID;
        this.constant2state = constant2state;
        this.position2operations = position2operations;
        this.headChildCount = position2operations.get(new IntArrayList()).size();

    }

//    I never finished implementing this function, but it was a neat idea -- JG
//    public static SourceAssignmentAutomaton makeAutomatonFromConstants(AMDependencyTree dep, Collection<String> constants,
//                                                                       DecompositionPackage decompositionPackage)
//            throws ParserException {
//        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();
//        Signature signature = new Signature();
//        for (String c : constants) {
//            signature.addSymbol(c, 0);
//            Pair<SGraph, Type> graph = alg.parseString(c);
//            for (String source : graph.right.getAllSources()) {
//                signature.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+source, 2);
//                signature.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+source, 2);
//            }
//        }
//        //TODO implement rest
//
//        return new SourceAssignmentAutomaton(signature, null, null, null);
//    }

    /**
     * This function creates a SourceAssignmentAutomaton based on an AMDependencyTree dep that uses constants with
     * placeholder source names. This function assumes that all constants in the dependency tree are different from each other.
     * The source assignment automaton is exhaustive in the sense that it will use all possible combinations of source names
     * (from a fixed-size set of generalizable source names) in the constants.
     *
     * @param dep The dependency tree with placeholder source names that the resulting automaton will be based on.
     * @param nrSources total number of generalizable source names that the resulting automaton will use
     * @param decompositionPackage
     * @return
     */
    public static SourceAssignmentAutomaton makeAutomatonWithAllSourceCombinations(AMDependencyTree dep, int nrSources,
                                                                                   DecompositionPackage decompositionPackage) {


        Signature signature = createSignatureWithBinaryOperationsForAllSources(nrSources);

        OrderOnAMDependencyTree orderOnAMDependencyTree = OrderOnAMDependencyTree.createArbitraryOrder(dep);

        RuleBuilderWithAllSourceCombinations ruleBuilder = new RuleBuilderWithAllSourceCombinations(signature, nrSources,
                decompositionPackage, orderOnAMDependencyTree);

        return ruleBuilder.makeAutomaton();

    }

    @NotNull
    private static Signature createSignatureWithBinaryOperationsForAllSources(int nrSources) {
        Signature signature = new Signature();
        for (int i = 0; i<nrSources; i++) {
            signature.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+makeSource(i), 2);
            signature.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+makeSource(i), 2);
        }
        return signature;
    }

    public static String makeSource(int i) {
        return "S"+i;
    }



    @Override
    public Iterable<Rule> getRulesBottomUp(int labelId, int[] childStates) {
        if (childStates.length == 2) {
            return makeBinaryRules(labelId, childStates);
        } else if (childStates.length == 0) {
            return makeConstantRules(labelId, childStates);
        } else {
            throw new IllegalArgumentException();
        }
    }

        @NotNull
        private Iterable<Rule> makeBinaryRules(int labelId, int[] childStates) {
            SAAState head = getStateForId(childStates[0]);
            SAAState dependant = getStateForId(childStates[1]);

            if (isBinaryRuleAllowed(head, dependant, labelId)) {
                return makeBinaryRuleSingleton(labelId, childStates, head);
            } else {
                return Collections.emptyList();
            }
        }


        private boolean isBinaryRuleAllowed(SAAState head, SAAState dependant, int labelId) {
            String theOnlyAllowedLabelAccordingToDependencyTreeAndStates = getOperationWithGeneralizableSourceNameFromDependencyTreeAndStates(head, dependant);
            boolean labelIdMatchesReconstructedLabel = signature.resolveSymbolId(labelId).equals(theOnlyAllowedLabelAccordingToDependencyTreeAndStates);
            return labelIdMatchesReconstructedLabel && argumentMatches(head, dependant);
        }


        @NotNull
        private Iterable<Rule> makeBinaryRuleSingleton(int labelId, int[] childStates, SAAState head) {
            SAAState resultState = head.increment();
            if (satisfiesPropertiesOfFinalState(resultState)) {
                addFinalState(addState(resultState));
            }
            return Collections.singleton(createRule(addState(resultState), labelId, childStates, 1.0));
        }


        @NotNull
        private String getOperationWithGeneralizableSourceNameFromDependencyTreeAndStates(SAAState head, SAAState dependant) {
            String operationWithPlaceholderSourceName = position2operations.get(head.position).get(head.childrenProcessed);
            String mappedDepSource;
            if (operationWithPlaceholderSourceName.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
                mappedDepSource = head.sourceAssignments.get(operationWithPlaceholderSourceName.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
            } else {
                mappedDepSource = dependant.sourceAssignments.get(operationWithPlaceholderSourceName.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
            }
            return operationWithPlaceholderSourceName.substring(0, ApplyModifyGraphAlgebra.OP_APPLICATION.length())+mappedDepSource;
        }

        private boolean argumentMatches(SAAState head, SAAState dependant) {
            // we check all conditions one after the other here to get both readability and runtime effectiveness
            if (dependant.childrenProcessed != position2operations.get(dependant.position).size()) {
                // dependant must have all children processed
                return false;
            }
            if (dependant.position.size() != head.position.size() +1) {
                // dependant must be one level below head
                return false;
            }
            if (!dependant.position.subList(0, head.position.size()).equals(head.position)) {
                // the dependant must actually be a dependant of the head (head address must be prefix of dependant address)
                return false;
            }
            for (String source : Sets.intersect(head.sourceAssignments.keySet(), dependant.sourceAssignments.keySet())) {
                // source mappings must match where they overlap
                if (!head.sourceAssignments.get(source).equals(dependant.sourceAssignments.get(source))) {
                    return false;
                }
            }
            if (dependant.position.getInt(dependant.position.size()-1) != head.childrenProcessed) {
                // the dependant's position (with respect to the order of the dependants of the head) must be the next for head to process
                return false;
            }
            return true;
        }


        @NotNull
        private Iterable<Rule> makeConstantRules(int labelId, int[] childStates) {
            SAAState resultState = constant2state.get(signature.resolveSymbolId(labelId));
            if (satisfiesPropertiesOfFinalState(resultState)) {
                addFinalState(addState(resultState));
            }
            return Collections.singleton(createRule(addState(resultState), labelId, childStates, 1.0));
        }


        private boolean satisfiesPropertiesOfFinalState(SAAState state) {
            return state.position.isEmpty() && state.childrenProcessed == headChildCount;
        }


    @Override
    public SiblingFinder newSiblingFinder(int labelID) {
        if (signature.getArity(labelID) == 2) {
            return new SAABinarySiblingFinder(this);
        } else {
            return super.newSiblingFinder(labelID); // this is a dummy, we only need sibling finders for binary rules
        }
    }

    @Override
    public Iterable<Rule> getRulesTopDown(int labelId, int parentState) {
        return null;
    }

    @Override
    public boolean isBottomUpDeterministic() {
        return false;
    }




    public AmConllSentence tree2amConll(Tree<String> tree, DecompositionPackage decompositionPackage, SupertagDictionary supertagDictionary) throws ParserException {
        AmConllSentence returnValue = decompositionPackage.makeBaseAmConllSentence();
        addTreeToAmConll(tree, decompositionPackage, returnValue, supertagDictionary);
        return returnValue;
    }


        private void addTreeToAmConll(Tree<String> treeToAdd, DecompositionPackage decompositionPackage,
                                      AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary) throws ParserException {
            // this is really just a wrapper to get a simpler function name and return value
            addTreeToAmConllAndReturnHeadIndexRecursive(treeToAdd, decompositionPackage, sentenceToAddTo, supertagDictionary);
        }

        private int addTreeToAmConllAndReturnHeadIndexRecursive(Tree<String> treeToAdd, DecompositionPackage decompositionPackage,
                                                                AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary) throws ParserException {
            List<Tree<String>> childrenAtTreeRoot = treeToAdd.getChildren();
            String labelAtTreeRoot = treeToAdd.getLabel();
            boolean weAreAtBinaryBranch = childrenAtTreeRoot.size() == 2;
            boolean weAreAtLeaf = childrenAtTreeRoot.size() == 0;
            if (weAreAtBinaryBranch) {
                return processBinaryBranchRecursivelyAndReturnHeadIndex(decompositionPackage, sentenceToAddTo, supertagDictionary, childrenAtTreeRoot, labelAtTreeRoot);
            } else if (weAreAtLeaf) {
                return processLeafAndReturnHeadIndex(decompositionPackage, sentenceToAddTo, supertagDictionary, labelAtTreeRoot);
            } else {
                throw new IllegalArgumentException();
            }
        }

        private int processBinaryBranchRecursivelyAndReturnHeadIndex(DecompositionPackage decompositionPackage,
                                                                     AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary,
                                                                     List<Tree<String>> children, String label) throws ParserException {
            // the head is always left in a binary branch of an AM tree
            Tree<String> headTreeBranch = children.get(0);
            Tree<String> dependantTreeBranch = children.get(1);
            int headID = addTreeToAmConllAndReturnHeadIndexRecursive(headTreeBranch, decompositionPackage, sentenceToAddTo, supertagDictionary);
            int dependantID = addTreeToAmConllAndReturnHeadIndexRecursive(dependantTreeBranch, decompositionPackage, sentenceToAddTo, supertagDictionary);

            // a binary branch in the tree that we are adding corresponds to an edge in the AM dependency tree
            addEdgeInformationToSentence(sentenceToAddTo, label, headID, dependantID);
            return headID;
        }

        private void addEdgeInformationToSentence(AmConllSentence sentenceToAddTo, String label, int headID, int dependantID) {
            AmConllEntry dependantAmConllEntry = sentenceToAddTo.get(dependantID-1);//careful, this get function is 0-based
            dependantAmConllEntry.setHead(headID);
            dependantAmConllEntry.setEdgeLabel(label);
        }


        private int processLeafAndReturnHeadIndex(DecompositionPackage decompositionPackage, AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary, String label) throws ParserException {
            // get the position in the sentence that is aligned to the constant at this leaf
            int wordID = constant2wordID.get(label);

            // a leaf in the tree that we are adding corresponds to a supertag / constant in the AM dependency tree
            addSupertagInformationToSentence(decompositionPackage, sentenceToAddTo, supertagDictionary, label, wordID);
            return wordID;
        }

        private void addSupertagInformationToSentence(DecompositionPackage decompositionPackage, AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary, String label, int wordID) throws ParserException {
            Pair<SGraph, Type> asGraphSupertag = new ApplyModifyGraphAlgebra().parseString(label);
            AmConllWithSourcesCreator.setSupertag(asGraphSupertag.left, asGraphSupertag.right, decompositionPackage, wordID,
                    sentenceToAddTo, supertagDictionary);
        }




    /**
     * Just for testing/debugging things
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String corpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\dev.sdp";
        String corpusOutPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\dev.amconll";
        int nrSources = 2;


        int[] buckets = new int[]{0, 3, 10, 30, 100, 300, 1000, 3000, 10000, 30000, 100000, 300000, 1000000};
        Counter<Integer> bucketCounter = new Counter<>();
        Counter<String> successCounter = new Counter<>();

        DMBlobUtils blobUtils = new DMBlobUtils();
        GraphReader2015 gr = new GraphReader2015(corpusPath);


        SupertagDictionary supertagDictionary = new SupertagDictionary();//future: load from file for dev set (better: get dev scores from training EM)

        Graph sdpGraph;

        List<TreeAutomaton<?>> concreteDecompositionAutomata = new ArrayList<>();
        List<SourceAssignmentAutomaton> originalDecompositionAutomata = new ArrayList<>();
        List<DecompositionPackage> decompositionPackages = new ArrayList<>();

        int index = 0;
        int fails = 0;
        int nondecomposeable = 0;
        while ((sdpGraph = gr.readGraph()) != null) {
            if (index % 100 == 0) {
                System.err.println(index);
                bucketCounter.printAllSorted();
            }
            if (true) { //index == 1268
                MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
                SGraph graph = inst.getGraph();


                try {

                    DecompositionPackage decompositionPackage = new SDPDecompositionPackage(inst, blobUtils, true);

                    ComponentAnalysisToAMDep converter = new ComponentAnalysisToAMDep(graph, decompositionPackage);

                    ComponentAutomaton componentAutomaton = new ComponentAutomaton(graph, blobUtils);

                    AMDependencyTree result = converter.componentAnalysis2AMDep(componentAutomaton);


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



        ConcreteTreeAutomaton<String> grammarAutomaton = new ConcreteTreeAutomaton<>();
        String dummyState = "X";
        grammarAutomaton.addFinalState(grammarAutomaton.addState(dummyState));
        Random random = new Random();
        List<Map<Rule, Rule>> dataRuleToGrammarRule = new ArrayList<>();
        ListMultimap<Rule, Rule> grammarRuleToDataRules = ArrayListMultimap.create();
        SupertagDictionary grammarSupertagDictionary = new SupertagDictionary();

        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();

        double ruleSum = 0;
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
                    String nodeName = constant.left.getNodeForSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME);
                    constant.left.getNode(nodeName).setLabel(AmConllEntry.LEX_MARKER);
                    grammarLabel = grammarSupertagDictionary.getRepr(constant.left)+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+constant.right.toString();
                }

                Rule grammarRule = grammarAutomaton.createRule(dummyState, grammarLabel, children, random.nextDouble());
                ruleSum += grammarRule.getWeight();
                rulesMapForThisAuto.put(dataRule, grammarRule);
                grammarRuleToDataRules.put(grammarRule, dataRule);//can just do it like this, if same grammar rule shows up multiple times, the ListMultimap will keep multiple entries
                grammarAutomaton.addRule(grammarRule);
            }
        }

        //normalize rule weights, or else EM won't work right
        for (Rule grammarRule : grammarRuleToDataRules.keySet()) {
            grammarRule.setWeight(grammarRule.getWeight()/ruleSum);
        }

        //need to give data automata the same weights
        for (Rule grammarRule : grammarRuleToDataRules.keySet()) {
            for (Rule dataRule : grammarRuleToDataRules.get(grammarRule)) {
                dataRule.setWeight(grammarRule.getWeight());
            }
        }

        System.out.println(grammarAutomaton);

        Pair<Integer, Double> iterationAndDiff = grammarAutomaton.trainEM(concreteDecompositionAutomata,
                dataRuleToGrammarRule, grammarRuleToDataRules, 100, 0.0001, true, null);

        System.out.println("EM stopped after iteration "+iterationAndDiff.left+" with difference "+iterationAndDiff.right);

        System.out.println(grammarAutomaton);

        List<AmConllSentence> outputCorpus = new ArrayList<>();
        Iterator<DecompositionPackage> decompositionPackageIterator = decompositionPackages.iterator();
        Iterator<SourceAssignmentAutomaton> originalAutomataIterator = originalDecompositionAutomata.iterator();

        for (TreeAutomaton<?> dataAutomaton : concreteDecompositionAutomata) {
            Tree<String> viterbiTree = dataAutomaton.viterbi();
            DecompositionPackage decompositionPackage = decompositionPackageIterator.next();
            outputCorpus.add(originalAutomataIterator.next().tree2amConll(viterbiTree, decompositionPackage, supertagDictionary));
        }

        AmConllSentence.writeToFile(corpusOutPath, outputCorpus);

        System.out.println("Entropy in amconll file: " + SupertagEntropy.computeSupertagEntropy(outputCorpus));
    }


}
