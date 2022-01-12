package de.saar.coli.amtools.decomposition.automata.source_assignment;

import de.saar.basic.Pair;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;
import java.util.function.Consumer;

/**
 * Each object of this class specifies an order on an AMDependencyTree. It does so by assigning an address to each
 * node; this information is stored via a map from addresses to the dependency-subtree rooted at that address.
 * For convenient use in the SourceAssignmentAutomaton class, for each address we also store a list of all outgoing
 * operations at that address, in left-to-right order.
 */
class OrderOnAMDependencyTree {

    private final Map<IntList, AMDependencyTree> treeAddress2dominatedDependencyTree;
    private final Map<IntList, List<String>> treeAddress2allOutgoingOperationsInOrder;

    private OrderOnAMDependencyTree(Map<IntList, AMDependencyTree> treeAddress2dominatedDependencyTree,
                            Map<IntList, List<String>> treeAddress2allPossibleOperations) {
        this.treeAddress2dominatedDependencyTree = treeAddress2dominatedDependencyTree;
        this.treeAddress2allOutgoingOperationsInOrder = treeAddress2allPossibleOperations;
    }

    void forEachAddressAndLocalDependencyTree(Consumer<Map.Entry<IntList, AMDependencyTree>> functionToApply) {
        for (Map.Entry<IntList, AMDependencyTree> addressWithDependencyTree : treeAddress2dominatedDependencyTree.entrySet()) {
            functionToApply.accept(addressWithDependencyTree);
        }
    }

    Map<IntList, List<String>> getTreeAddress2allOutgoingOperationsInOrder() {
        return treeAddress2allOutgoingOperationsInOrder;
    }

    static OrderOnAMDependencyTree createArbitraryOrder(AMDependencyTree dep) {
        Map<IntList, AMDependencyTree> treeAddress2dominatedDependencyTree = new HashMap<>();
        Map<IntList, List<String>> treeAddress2allPossibleOperations = new HashMap<>();
        establishArbitraryOrderForDependencyTreeRecursive(dep, new IntArrayList(), treeAddress2dominatedDependencyTree, treeAddress2allPossibleOperations);
        return new OrderOnAMDependencyTree(treeAddress2dominatedDependencyTree, treeAddress2allPossibleOperations);
    }

    private static void establishArbitraryOrderForDependencyTreeRecursive(AMDependencyTree depToSort, IntList address,
                                                                                             Map<IntList, AMDependencyTree> treeAddress2dominatedDependencyTree,
                                                                                             Map<IntList, List<String>> treeAddress2allOutgoingOperationsInOrder) {
        treeAddress2dominatedDependencyTree.put(address, depToSort);
        List<String> operationsHere = new ArrayList<>();
        treeAddress2allOutgoingOperationsInOrder.put(address, operationsHere);
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
            establishArbitraryOrderForDependencyTreeRecursive(opAndChild.right, childPosition, treeAddress2dominatedDependencyTree, treeAddress2allOutgoingOperationsInOrder);
            index++;
        }
    }
}
