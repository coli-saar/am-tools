package de.saar.coli.amtools.decomposition;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
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

import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

public class SourceAssignmentAutomaton extends TreeAutomaton<SourceAssignmentAutomaton.State> {

    private final Map<String, Integer> constant2wordID;
    private final Map<IntList, List<String>> position2operations;
    private final Map<String, SourceAssignmentAutomaton.State> constant2state;

    private final int headChildCount;

    private SourceAssignmentAutomaton(Signature signature,
                                      Map<String, Integer> constant2wordID,
                                      Map<String, SourceAssignmentAutomaton.State> constant2state,
                                      Map<IntList, List<String>> position2operations) {
        super(signature);
        this.constant2wordID = constant2wordID;
        this.constant2state = constant2state;
        this.position2operations = position2operations;
        this.headChildCount = position2operations.get(new IntArrayList()).size();

    }


    public static SourceAssignmentAutomaton makeAutomatonFromConstants(AMDependencyTree dep, Collection<String> constants,
                                                                       DecompositionPackage decompositionPackage)
            throws ParserException {
        ApplyModifyGraphAlgebra alg = new ApplyModifyGraphAlgebra();
        Signature signature = new Signature();
        for (String c : constants) {
            signature.addSymbol(c, 0);
            Pair<SGraph, Type> graph = alg.parseString(c);
            for (String source : graph.right.getAllSources()) {
                signature.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+source, 2);
                signature.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+source, 2);
            }
        }
        //TODO implement rest

        return new SourceAssignmentAutomaton(signature, null, null, null);
    }

    /**
     * assumes that all constants in the dependency tree are different from each other.
     * @param dep
     * @param nrSources
     * @param decompositionPackage
     * @return
     */
    public static SourceAssignmentAutomaton makeAutomatonWithAllSourceCombinations(AMDependencyTree dep, int nrSources,
                                                                                   DecompositionPackage decompositionPackage) {
        Map<IntList, AMDependencyTree> position2dep = new HashMap<>();
        Map<IntList, List<String>> position2operations = new HashMap<>();
        sortRecursive(dep, position2dep, position2operations, new IntArrayList(), decompositionPackage);

        Signature signature = new Signature();
        for (int i = 0; i<nrSources; i++) {
            signature.addSymbol(ApplyModifyGraphAlgebra.OP_APPLICATION+makeSource(i), 2);
            signature.addSymbol(ApplyModifyGraphAlgebra.OP_MODIFICATION+makeSource(i), 2);
        }
        Map<String, SourceAssignmentAutomaton.State> constant2state = new HashMap<>();
        Map<String, Integer> constant2wordID = new HashMap<>();

        for (Map.Entry<IntList, AMDependencyTree> entry : position2dep.entrySet()) {
            Pair<SGraph, Type> graph = entry.getValue().getHeadGraph();
            Set<String> allSources = graph.right.getAllSources();
            List<Map<String, String>> allAssignments = getAllMaps(allSources, nrSources);
            for (Map<String, String> sourceAssignment : allAssignments) {
                Type newType = Type.EMPTY_TYPE;
                for (String oldSource : allSources) {
                    // could also just add everything in keySet of sourceAssignment
                    newType = newType.addSource(sourceAssignment.get(oldSource));
                }
                for (String oldSource : allSources) {
                    for (String requestSource : graph.right.getRequest(oldSource).getAllSources()) {
                        String edgeLabel = graph.right.getRenameTarget(oldSource, requestSource);
                        // this is not quite right, but should work with the input we're getting right now
                        newType = newType.setDependency(sourceAssignment.get(oldSource),
                                sourceAssignment.get(requestSource), sourceAssignment.get(edgeLabel));
                    }
                }
                SGraph newSGraph = graph.left;
                for (String source : graph.left.getAllSources()) {
                    if (!source.equals(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME)) {
                        newSGraph = newSGraph.renameSource(source, sourceAssignment.get(source));
                    }
                }
                Pair<SGraph, Type> constant = new Pair<>(newSGraph, newType);
                String label = constant.left.toIsiAmrStringWithSources() + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + constant.right.toString();
                signature.addSymbol(label, 0);
                State state = new State(entry.getKey(), 0, sourceAssignment);
                if (constant2state.containsKey(label)) {
                    throw new IllegalArgumentException("constant label in SourceAssignmentAutomaton occurred twice, aborting!");
                }
                constant2state.put(label, state);
                if (constant2wordID.containsKey(label)) {
                    throw new IllegalArgumentException("constant label in SourceAssignmentAutomaton occurred twice, aborting!");
                }
                constant2wordID.put(label, decompositionPackage.getSentencePositionForGraphFragment(constant.left));

            }
        }
        return new SourceAssignmentAutomaton(signature, constant2wordID, constant2state, position2operations);
    }


    public static List<Map<String, String>> getAllMaps(Set<String> inputSources, int nrSources) {
        if (inputSources.size() > nrSources) {
            return Collections.emptyList();
        }
        List<Map<String, String>> changingList = new ArrayList<>();
        changingList.add(new HashMap<>());
        for (String inputSource : inputSources) {
            List<Map<String, String>> replacingList = new ArrayList<>();
            for (Map<String, String> oldMap : changingList) {
                for (int i = 0; i<nrSources; i++) {
                    String si = makeSource(i);
                    if (!oldMap.values().contains(si)) {
                        Map<String, String> newMap = new HashMap<>(oldMap);
                        newMap.put(inputSource, si);
                        replacingList.add(newMap);
                    }
                }
            }
            changingList = replacingList;
        }
        return changingList;
    }

    private static String makeSource(int i) {
        return "S"+i;
    }

    private static void sortRecursive(AMDependencyTree depToSort, Map<IntList, AMDependencyTree> position2dep,
                                      Map<IntList, List<String>> position2operations, IntList position, DecompositionPackage decompositionPackage) {
        position2dep.put(position, depToSort);
        List<String> operationsHere = new ArrayList<>();
        position2operations.put(position, operationsHere);
        List<Pair<String, AMDependencyTree>> childrenList = new ArrayList<>(depToSort.getOperationsAndChildren());
        childrenList.sort(new Comparator<Pair<String, AMDependencyTree>>() {
            @Override
            public int compare(Pair<String, AMDependencyTree> o1, Pair<String, AMDependencyTree> o2) {
                return 0;//TODO implement; for now random order
            }
        });
        int index = 0;
        for (Pair<String, AMDependencyTree> opAndChild : childrenList) {
            operationsHere.add(opAndChild.left);
            IntList childPosition = new IntArrayList(position);
            childPosition.add(index);
            sortRecursive(opAndChild.right, position2dep, position2operations, childPosition, decompositionPackage);
            index++;
        }
    }

    @Override
    public Iterable<Rule> getRulesBottomUp(int labelId, int[] childStates) {
        if (childStates.length == 2) {
            State head = getStateForId(childStates[0]);
            State argument = getStateForId(childStates[1]);
            // check that label matches operation in dep tree
            String depOperation = position2operations.get(head.position).get(head.childrenProcessed);
            String mappedDepSource;
            if (depOperation.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
                mappedDepSource = head.sourceAssignments.get(depOperation.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
            } else {
                mappedDepSource = argument.sourceAssignments.get(depOperation.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
            }
            String mappedDepOperation = depOperation.substring(0, ApplyModifyGraphAlgebra.OP_APPLICATION.length())+mappedDepSource;
            if (signature.resolveSymbolId(labelId).equals(mappedDepOperation) && argumentMatches(head, argument)) {
                State result = head.increment();
                if (result.position.isEmpty() && result.childrenProcessed == headChildCount) {
                    addFinalState(addState(result));
                }
                return Collections.singleton(createRule(addState(head.increment()), labelId, childStates, 1.0));
            } else {
                return Collections.emptyList();
            }
        } else if (childStates.length == 0) {

            State state = constant2state.get(signature.resolveSymbolId(labelId));
            if (state.position.isEmpty() && state.childrenProcessed == headChildCount) {
                addFinalState(addState(state));
            }
            return Collections.singleton(createRule(addState(state), labelId, childStates, 1.0));
        } else {
            throw new IllegalArgumentException();
        }
    }



    @Override
    public SiblingFinder newSiblingFinder(int labelID) {
        if (signature.getArity(labelID) == 2) {
            return new BinarySiblingFinder();
        } else {
            return super.newSiblingFinder(labelID);
        }
    }

    private class BinarySiblingFinder extends SiblingFinder {

        private final Map<Pair<IntList, Integer>, Set<State>> headMap;
        private final Map<IntList, Set<State>> argumentMap;

        /**
         * Creates a new sibling finder for an operation with given arity.
         *
         */
        public BinarySiblingFinder() {
            super(2);
            headMap = new HashMap<>();
            argumentMap = new HashMap<>();
        }


        @Override
        public Iterable<int[]> getPartners(int stateID, int pos) {
            if (pos == 0) {
                // we have a head
                State head = getStateForId(stateID);
                List<String> operationList = position2operations.get(head.position);
                if (operationList.size() > head.childrenProcessed) {
                    // only return something if the head matches the operation
                    IntList argumentPosition = new IntArrayList(head.position);
                    argumentPosition.add(head.childrenProcessed);
                    return new Iterable<int[]>() {
                        @NotNull
                        @Override
                        public Iterator<int[]> iterator() {

                            Iterator<State> args = argumentMap.getOrDefault(argumentPosition, Collections.EMPTY_SET).iterator();

                            return new Iterator<int[]>() {
                                @Override
                                public boolean hasNext() {
                                    return args.hasNext();
                                }

                                @Override
                                public int[] next() {
                                    return new int[]{stateID, getIdForState(args.next())};
                                }
                            };
                        }
                    };

                } else {
                    return Collections.emptyList();
                }
            } else if (pos == 1) {
                State argument = getStateForId(stateID);
                int posSize = argument.position.size();
                if (posSize > 0 && argument.childrenProcessed == position2operations.get(argument.position).size()) {

                    IntList headPosition = argument.position.subList(0, argument.position.size()-1);
                    int argPos = argument.position.getInt(argument.position.size()-1);
                    return new Iterable<int[]>() {
                        @NotNull
                        @Override
                        public Iterator<int[]> iterator() {

                            Iterator<State> args = headMap.getOrDefault(new Pair<>(headPosition, argPos), Collections.EMPTY_SET).iterator();

                            return new Iterator<int[]>() {
                                @Override
                                public boolean hasNext() {
                                    return args.hasNext();
                                }

                                @Override
                                public int[] next() {
                                    return new int[]{getIdForState(args.next()), stateID};
                                }
                            };
                        }
                    };
                } else {
                    return Collections.emptyList();
                }
            } else {
                return Collections.emptyList();
            }
        }

        @Override
        protected void performAddState(int stateID, int pos) {
            if (pos == 0) {
                // we have a head
                State head = getStateForId(stateID);
                List<String> operationList = position2operations.get(head.position);
                if (operationList.size() > head.childrenProcessed) {
                    Pair<IntList, Integer> key = new Pair<>(head.position, head.childrenProcessed);
                    Set<State> stateSet = headMap.computeIfAbsent(key, k -> new HashSet<>());
                    stateSet.add(head);
                }
            } else if (pos == 1) {
                State argument = getStateForId(stateID);
                int posSize = argument.position.size();
                if (posSize > 0 && argument.childrenProcessed == position2operations.get(argument.position).size()) {
                    
                    Set<State> stateSet = argumentMap.computeIfAbsent(argument.position, k -> new HashSet<>());
                    stateSet.add(argument);
                }
            }
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

    private boolean argumentMatches(State head, State argument) {
        if (argument.childrenProcessed != position2operations.get(argument.position).size()) {
            // argument must have all children processed
            return false;
        }
        if (argument.position.size() != head.position.size() +1) {
            // argument must be one level below head
            return false;
        }
        if (!argument.position.subList(0, head.position.size()).equals(head.position)) {
            // argument must be exactly below head
            return false;
        }
        for (String source : Sets.intersect(head.sourceAssignments.keySet(), argument.sourceAssignments.keySet())) {
            // source mappings must match
            if (!head.sourceAssignments.get(source).equals(argument.sourceAssignments.get(source))) {
                return false;
            }
        }
        if (argument.position.getInt(argument.position.size()-1) != head.childrenProcessed) {
            // argument position must be the next for head to process
            return false;
        }
        return true;
    }

    public static class State {

        private final IntList position;
        private final int childrenProcessed;
        private final Map<String, String> sourceAssignments;

        public State(IntList position, int childrenProcessed, Map<String, String> sourceAssignments) {
            this.position = position;
            this.childrenProcessed = childrenProcessed;
            this.sourceAssignments = sourceAssignments;
        }


        public State increment() {
            //TODO change source assignments to remove the unnecessary ones, once a proper children order is implemented.
            return new State(position, childrenProcessed+1, sourceAssignments);
        }

        @Override
        /**
         * This is designed to for each State create a unique string (and always the same string for equal states).
         */
        public String toString() {
            List<String> sourceAssignmentKeysSorted = new ArrayList<>(sourceAssignments.keySet());
            sourceAssignmentKeysSorted.sort(Comparator.naturalOrder());
            StringJoiner stringJoiner = new StringJoiner(",");
            for (String key : sourceAssignmentKeysSorted) {
                stringJoiner.add(key+"=>"+sourceAssignments.get(key));
            }
            return "["+position.toString() +
                    "(" + childrenProcessed + ")" +
                    ", {" + stringJoiner.toString()
                    +"}]";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return childrenProcessed == state.childrenProcessed &&
                    Objects.equals(position, state.position) &&
                    Objects.equals(sourceAssignments, state.sourceAssignments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, childrenProcessed, sourceAssignments);
        }
    }


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

                    DecompositionPackage decompositionPackage = new SDPDecompositionPackage(sdpGraph, blobUtils);

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
                            ConcreteTreeAutomaton<State> concreteTreeAutomaton = auto.asConcreteTreeAutomatonBottomUp();
//                            System.out.println(auto.signature);
                            //System.out.println(result);
//                            System.out.println(concreteTreeAutomaton);
//                            System.out.println(concreteTreeAutomaton.viterbi());
                            if (concreteTreeAutomaton.viterbi() != null) {
                                successCounter.add("success");
                                concreteTreeAutomaton = (ConcreteTreeAutomaton<State>)concreteTreeAutomaton.reduceTopDown();
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


    public AmConllSentence tree2amConll(Tree<String> tree, DecompositionPackage decompositionPackage, SupertagDictionary supertagDictionary) throws ParserException {
        AmConllSentence sentenceToAddTo = decompositionPackage.makeBaseAmConllSentence();
        addTreeToAmConllAndReturnHeadIndex(tree, decompositionPackage, sentenceToAddTo, supertagDictionary);
        return sentenceToAddTo;
    }

    private int addTreeToAmConllAndReturnHeadIndex(Tree<String> treeToAdd, DecompositionPackage decompositionPackage,
                                                          AmConllSentence sentenceToAddTo, SupertagDictionary supertagDictionary) throws ParserException {
        List<Tree<String>> children = treeToAdd.getChildren();
        String label = treeToAdd.getLabel();
        if (children.size() == 2) {
            int headID = addTreeToAmConllAndReturnHeadIndex(children.get(0), decompositionPackage, sentenceToAddTo, supertagDictionary);
            int childID = addTreeToAmConllAndReturnHeadIndex(children.get(1), decompositionPackage, sentenceToAddTo, supertagDictionary);
            AmConllEntry childEntry = sentenceToAddTo.get(childID-1);//careful, this get function is 0-based
            childEntry.setHead(headID);
            childEntry.setEdgeLabel(label);
            return headID;
        } else if (children.size() == 0) {
            Pair<SGraph, Type> asGraph = new ApplyModifyGraphAlgebra().parseString(label);
            int wordID = constant2wordID.get(treeToAdd.getLabel());
            AmConllWithSourcesCreator.setSupertag(asGraph.left, asGraph.right, decompositionPackage, wordID,
                    sentenceToAddTo, supertagDictionary);
            return wordID;
        } else {
            throw new IllegalArgumentException();
        }
    }

}
