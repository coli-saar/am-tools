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

import java.util.*;
import java.util.function.Consumer;

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

        RuleBuilderWithAllSourceCombinations ruleBuilder = new RuleBuilderWithAllSourceCombinations(signature, nrSources, decompositionPackage);

        ruleBuilder.establishArbitraryOrderForDependencyTree(dep);

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

    private static class RuleBuilderWithAllSourceCombinations {
        private final Map<IntList, AMDependencyTree> treeAddress2dominatedDependencyTree = new HashMap<>();
        private final Map<IntList, List<String>> treeAddress2allPossibleOperations = new HashMap<>();
        private final Signature signature;
        private final int nrSources;
        private final DecompositionPackage decompositionPackage;

        private RuleBuilderWithAllSourceCombinations(Signature signature, int nrSources, DecompositionPackage decompositionPackage) {
            this.signature = signature;
            this.nrSources = nrSources;
            this.decompositionPackage = decompositionPackage;
        }

        private void establishArbitraryOrderForDependencyTree(AMDependencyTree dep) {
            establishArbitraryOrderForDependencyTreeRecursive(dep, new IntArrayList());
        }

        private void establishArbitraryOrderForDependencyTreeRecursive(AMDependencyTree depToSort, IntList address) {
            treeAddress2dominatedDependencyTree.put(address, depToSort);
            List<String> operationsHere = new ArrayList<>();
            treeAddress2allPossibleOperations.put(address, operationsHere);
            List<Pair<String, AMDependencyTree>> childrenList = new ArrayList<>(depToSort.getOperationsAndChildren());
            childrenList.sort(new Comparator<Pair<String, AMDependencyTree>>() {
                @Override
                public int compare(Pair<String, AMDependencyTree> o1, Pair<String, AMDependencyTree> o2) {
                    //TODO a clever order could allow for forgetting source assignments early, to make the automaton smaller; for now we use a random order
                    return 0;
                }
            });
            int index = 0;
            for (Pair<String, AMDependencyTree> opAndChild : childrenList) {
                operationsHere.add(opAndChild.left);
                IntList childPosition = new IntArrayList(address);
                childPosition.add(index);
                establishArbitraryOrderForDependencyTreeRecursive(opAndChild.right, childPosition);
                index++;
            }
        }

        private SourceAssignmentAutomaton makeAutomaton() {

            Map<String, SourceAssignmentAutomaton.State> constant2state = new HashMap<>();
            Map<String, Integer> constant2wordID = new HashMap<>();

            forEachAddressAndLocalDependencyTree(addressWithDependencyTree -> {
                recordRuleInformationAtAddress(addressWithDependencyTree, constant2state, constant2wordID);
            });

            return new SourceAssignmentAutomaton(signature, constant2wordID, constant2state, treeAddress2allPossibleOperations);
        }


        private void recordRuleInformationAtAddress(Map.Entry<IntList, AMDependencyTree> addressWithDependencyTree,
                                                    Map<String, SourceAssignmentAutomaton.State> constant2state,
                                                    Map<String, Integer> constant2wordID) {
            Pair<SGraph, Type> constantAtDependencyNode = addressWithDependencyTree.getValue().getHeadGraph();
            Set<String> placeholderSourcesAtConstant = constantAtDependencyNode.right.getAllSources();
            List<Map<String, String>> allPossibleSourceAssignments = getAllSourceAssignmentMaps(placeholderSourcesAtConstant, nrSources);
            for (Map<String, String> sourceAssignment : allPossibleSourceAssignments) {
                createAndRegisterConstantFromSourceAssignment(addressWithDependencyTree.getKey(), constant2state, constant2wordID,
                        constantAtDependencyNode, placeholderSourcesAtConstant, sourceAssignment);
            }
        }

        private void createAndRegisterConstantFromSourceAssignment(IntList address, Map<String, State> constant2state, Map<String, Integer> constant2wordID,
                                                                   Pair<SGraph, Type> constantAtDependencyNode, Set<String> placeholderSourcesAtConstant,
                                                                   Map<String, String> sourceAssignment) {
            Pair<SGraph, Type> constant = createConstantWithGeneralizableSourceNames(constantAtDependencyNode, placeholderSourcesAtConstant, sourceAssignment);
            String constantInStringForm = constant.left.toIsiAmrStringWithSources() + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + constant.right.toString();
            registerConstantAndSourceAssignment(address, constant2state, constant2wordID, sourceAssignment, constant, constantInStringForm);
        }

        private void registerConstantAndSourceAssignment(IntList address, Map<String, State> constant2state, Map<String, Integer> constant2wordID, Map<String, String> sourceAssignment, Pair<SGraph, Type> constant, String constantInStringForm) {
            signature.addSymbol(constantInStringForm, 0);
            checkForDuplicateConstants(constant2state, constant2wordID, constantInStringForm);
            constant2state.put(constantInStringForm, createConstantState(address, sourceAssignment));
            constant2wordID.put(constantInStringForm, decompositionPackage.getSentencePositionForGraphFragment(constant.left));
        }

        private State createConstantState(IntList address, Map<String, String> sourceAssignment) {
            return new State(address, 0, sourceAssignment);
        }

        @NotNull
        private Pair<SGraph, Type> createConstantWithGeneralizableSourceNames(Pair<SGraph, Type> constantAtDependencyNode, Set<String> placeholderSourcesAtConstant, Map<String, String> sourceAssignment) {
            Type newType = Type.EMPTY_TYPE;
            newType = addGeneralizableSourceNamesToType(placeholderSourcesAtConstant, sourceAssignment, newType);
            newType = addRequestsToType(constantAtDependencyNode, placeholderSourcesAtConstant, sourceAssignment, newType);
            SGraph newSGraph = generateSGraphWithNewSources(constantAtDependencyNode, sourceAssignment);
            return new Pair<>(newSGraph, newType);
        }

        private void checkForDuplicateConstants(Map<String, State> constant2state, Map<String, Integer> constant2wordID, String label) {
            if (constant2state.containsKey(label)) {
                throw new IllegalArgumentException("constant label in SourceAssignmentAutomaton occurred twice, aborting!");
            }
            if (constant2wordID.containsKey(label)) {
                throw new IllegalArgumentException("constant label in SourceAssignmentAutomaton occurred twice, aborting!");
            }
        }

        private SGraph generateSGraphWithNewSources(Pair<SGraph, Type> constantAtDependencyNode, Map<String, String> sourceAssignment) {
            SGraph newSGraph = constantAtDependencyNode.left;
            for (String source : constantAtDependencyNode.left.getAllSources()) {
                if (!source.equals(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME)) {
                    newSGraph = newSGraph.renameSource(source, sourceAssignment.get(source));
                }
            }
            return newSGraph;
        }

        private Type addRequestsToType(Pair<SGraph, Type> constantAtDependencyNode, Set<String> placeholderSourcesAtConstant, Map<String, String> sourceAssignment, Type newType) {
            for (String oldSource : placeholderSourcesAtConstant) {
                for (String requestSource : constantAtDependencyNode.right.getRequest(oldSource).getAllSources()) {
                    String edgeLabel = constantAtDependencyNode.right.getRenameTarget(oldSource, requestSource);
                    // this is not quite right, but should work with the input we're getting right now
                    newType = newType.setDependency(sourceAssignment.get(oldSource),
                            sourceAssignment.get(requestSource), sourceAssignment.get(edgeLabel));
                }
            }
            return newType;
        }

        private Type addGeneralizableSourceNamesToType(Set<String> placeholderSourcesAtConstant, Map<String, String> sourceAssignment, Type newType) {
            for (String oldSource : placeholderSourcesAtConstant) {
                // could also just add everything in keySet of sourceAssignment
                newType = newType.addSource(sourceAssignment.get(oldSource));
            }
            return newType;
        }

        private void forEachAddressAndLocalDependencyTree(Consumer<Map.Entry<IntList, AMDependencyTree>> functionToApply) {
            for (Map.Entry<IntList, AMDependencyTree> addressWithDependencyTree : treeAddress2dominatedDependencyTree.entrySet()) {
                functionToApply.accept(addressWithDependencyTree);
            }
        }
    }


    private static List<Map<String, String>> getAllSourceAssignmentMaps(Set<String> inputSources, int nrSources) {
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
        State head = getStateForId(childStates[0]);
        State dependant = getStateForId(childStates[1]);

        if (isBinaryRuleAllowed(head, dependant, labelId)) {
            return makeBinaryRuleSingleton(labelId, childStates, head);
        } else {
            return Collections.emptyList();
        }
    }


    private boolean isBinaryRuleAllowed(State head, State dependant, int labelId) {
        String theOnlyAllowedLabelAccordingToDependencyTreeAndStates = getOperationWithGeneralizableSourceNameFromDependencyTreeAndStates(head, dependant);
        boolean labelIdMatchesReconstructedLabel = signature.resolveSymbolId(labelId).equals(theOnlyAllowedLabelAccordingToDependencyTreeAndStates);
        return labelIdMatchesReconstructedLabel && argumentMatches(head, dependant);
    }


    @NotNull
    private Iterable<Rule> makeBinaryRuleSingleton(int labelId, int[] childStates, State head) {
        State resultState = head.increment();
        if (satisfiesPropertiesOfFinalState(resultState)) {
            addFinalState(addState(resultState));
        }
        return Collections.singleton(createRule(addState(resultState), labelId, childStates, 1.0));
    }


    @NotNull
    private String getOperationWithGeneralizableSourceNameFromDependencyTreeAndStates(State head, State dependant) {
        String operationWithPlaceholderSourceName = position2operations.get(head.position).get(head.childrenProcessed);
        String mappedDepSource;
        if (operationWithPlaceholderSourceName.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
            mappedDepSource = head.sourceAssignments.get(operationWithPlaceholderSourceName.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
        } else {
            mappedDepSource = dependant.sourceAssignments.get(operationWithPlaceholderSourceName.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length()));
        }
        return operationWithPlaceholderSourceName.substring(0, ApplyModifyGraphAlgebra.OP_APPLICATION.length())+mappedDepSource;
    }

    private boolean argumentMatches(State head, State dependant) {
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
        State resultState = constant2state.get(signature.resolveSymbolId(labelId));
        if (satisfiesPropertiesOfFinalState(resultState)) {
            addFinalState(addState(resultState));
        }
        return Collections.singleton(createRule(addState(resultState), labelId, childStates, 1.0));
    }


    private boolean satisfiesPropertiesOfFinalState(State state) {
        return state.position.isEmpty() && state.childrenProcessed == headChildCount;
    }


    @Override
    public SiblingFinder newSiblingFinder(int labelID) {
        if (signature.getArity(labelID) == 2) {
            return new BinarySiblingFinder();
        } else {
            return super.newSiblingFinder(labelID); // this is a dummy, we only need sibling finders for binary rules
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


}
