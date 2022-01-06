package de.saar.coli.amtools.decomposition.automata.source_assignment;

import de.saar.basic.Pair;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.signature.Signature;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class RuleBuilderWithAllSourceCombinations {
    private final Signature signature;
    private final int nrSources;
    private final DecompositionPackage decompositionPackage;
    private final OrderOnAMDependencyTree orderOnAMDependencyTree;

    public RuleBuilderWithAllSourceCombinations(Signature signature, int nrSources, DecompositionPackage decompositionPackage,
                                         OrderOnAMDependencyTree orderOnAMDependencyTree) {
        this.signature = signature;
        this.nrSources = nrSources;
        this.decompositionPackage = decompositionPackage;
        this.orderOnAMDependencyTree = orderOnAMDependencyTree;
    }


    public SourceAssignmentAutomaton makeAutomaton() {

        Map<String, SAAState> constant2state = new HashMap<>();
        Map<String, Integer> constant2wordID = new HashMap<>();

        orderOnAMDependencyTree.forEachAddressAndLocalDependencyTree(addressWithDependencyTree -> {
            recordRuleInformationAtAddress(addressWithDependencyTree, constant2state, constant2wordID);
        });

        return new SourceAssignmentAutomaton(signature, constant2wordID, constant2state,
                                             orderOnAMDependencyTree.getTreeAddress2allOutgoingOperationsInOrder());
    }


    private void recordRuleInformationAtAddress(Map.Entry<IntList, AMDependencyTree> addressWithDependencyTree,
                                                Map<String, SAAState> constant2state,
                                                Map<String, Integer> constant2wordID) {
        Pair<SGraph, ApplyModifyGraphAlgebra.Type> constantAtDependencyNode = addressWithDependencyTree.getValue().getHeadGraph();
        Set<String> placeholderSourcesAtConstant = constantAtDependencyNode.right.getAllSources();
        List<Map<String, String>> allPossibleSourceAssignments = getAllSourceAssignmentMaps(placeholderSourcesAtConstant, nrSources);
        for (Map<String, String> sourceAssignment : allPossibleSourceAssignments) {
            createAndRegisterConstantFromSourceAssignment(addressWithDependencyTree.getKey(), constant2state, constant2wordID,
                    constantAtDependencyNode, placeholderSourcesAtConstant, sourceAssignment);
        }
    }

    private void createAndRegisterConstantFromSourceAssignment(IntList address, Map<String, SAAState> constant2state, Map<String, Integer> constant2wordID,
                                                               Pair<SGraph, ApplyModifyGraphAlgebra.Type> constantAtDependencyNode, Set<String> placeholderSourcesAtConstant,
                                                               Map<String, String> sourceAssignment) {
        Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant = createConstantWithGeneralizableSourceNames(constantAtDependencyNode, placeholderSourcesAtConstant, sourceAssignment);
        String constantInStringForm = constant.left.toIsiAmrStringWithSources() + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + constant.right.toString();
        registerConstantAndSourceAssignment(address, constant2state, constant2wordID, sourceAssignment, constant, constantInStringForm);
    }

    private void registerConstantAndSourceAssignment(IntList address, Map<String, SAAState> constant2state, Map<String, Integer> constant2wordID, Map<String, String> sourceAssignment, Pair<SGraph, ApplyModifyGraphAlgebra.Type> constant, String constantInStringForm) {
        signature.addSymbol(constantInStringForm, 0);
        checkForDuplicateConstants(constant2state, constant2wordID, constantInStringForm);
        constant2state.put(constantInStringForm, createConstantState(address, sourceAssignment));
        constant2wordID.put(constantInStringForm, decompositionPackage.getSentencePositionForGraphFragment(constant.left));
    }

    private SAAState createConstantState(IntList address, Map<String, String> sourceAssignment) {
        return new SAAState(address, 0, sourceAssignment);
    }

    @NotNull
    private Pair<SGraph, ApplyModifyGraphAlgebra.Type> createConstantWithGeneralizableSourceNames(Pair<SGraph, ApplyModifyGraphAlgebra.Type> constantAtDependencyNode, Set<String> placeholderSourcesAtConstant, Map<String, String> sourceAssignment) {
        ApplyModifyGraphAlgebra.Type newType = ApplyModifyGraphAlgebra.Type.EMPTY_TYPE;
        newType = addGeneralizableSourceNamesToType(placeholderSourcesAtConstant, sourceAssignment, newType);
        newType = addRequestsToType(constantAtDependencyNode, placeholderSourcesAtConstant, sourceAssignment, newType);
        SGraph newSGraph = generateSGraphWithNewSources(constantAtDependencyNode, sourceAssignment);
        return new Pair<>(newSGraph, newType);
    }

    private void checkForDuplicateConstants(Map<String, SAAState> constant2state, Map<String, Integer> constant2wordID, String label) {
        if (constant2state.containsKey(label)) {
            throw new IllegalArgumentException("constant label in SourceAssignmentAutomaton occurred twice, aborting!");
        }
        if (constant2wordID.containsKey(label)) {
            throw new IllegalArgumentException("constant label in SourceAssignmentAutomaton occurred twice, aborting!");
        }
    }

    private SGraph generateSGraphWithNewSources(Pair<SGraph, ApplyModifyGraphAlgebra.Type> constantAtDependencyNode, Map<String, String> sourceAssignment) {
        SGraph newSGraph = constantAtDependencyNode.left;
        for (String source : constantAtDependencyNode.left.getAllSources()) {
            if (!source.equals(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME)) {
                newSGraph = newSGraph.renameSource(source, sourceAssignment.get(source));
            }
        }
        return newSGraph;
    }

    private ApplyModifyGraphAlgebra.Type addRequestsToType(Pair<SGraph, ApplyModifyGraphAlgebra.Type> constantAtDependencyNode, Set<String> placeholderSourcesAtConstant, Map<String, String> sourceAssignment, ApplyModifyGraphAlgebra.Type newType) {
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

    private ApplyModifyGraphAlgebra.Type addGeneralizableSourceNamesToType(Set<String> placeholderSourcesAtConstant, Map<String, String> sourceAssignment, ApplyModifyGraphAlgebra.Type newType) {
        for (String oldSource : placeholderSourcesAtConstant) {
            // could also just add everything in keySet of sourceAssignment
            newType = newType.addSource(sourceAssignment.get(oldSource));
        }
        return newType;
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
                    String si = SourceAssignmentAutomaton.makeSource(i);
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


}
