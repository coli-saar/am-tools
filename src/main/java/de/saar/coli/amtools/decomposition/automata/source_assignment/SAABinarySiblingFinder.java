package de.saar.coli.amtools.decomposition.automata.source_assignment;

import de.saar.basic.Pair;
import de.up.ling.irtg.siblingfinder.SiblingFinder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class SAABinarySiblingFinder extends SiblingFinder {

    private final SourceAssignmentAutomaton sourceAssignmentAutomaton;
    private final Map<Pair<IntList, Integer>, Set<SAAState>> headMap;
    private final Map<IntList, Set<SAAState>> argumentMap;

    /**
     * Creates a new sibling finder for an operation with given arity.
     *
     */
    public SAABinarySiblingFinder(SourceAssignmentAutomaton sourceAssignmentAutomaton) {
        super(2);
        this.sourceAssignmentAutomaton = sourceAssignmentAutomaton;
        headMap = new HashMap<>();
        argumentMap = new HashMap<>();
    }


    @Override
    public Iterable<int[]> getPartners(int stateID, int pos) {
        if (pos == 0) {
            // we have a head
            SAAState head = sourceAssignmentAutomaton.getStateForId(stateID);
            List<String> operationList = sourceAssignmentAutomaton.position2operations.get(head.position);
            if (operationList.size() > head.childrenProcessed) {
                // only return something if the head matches the operation
                IntList argumentPosition = new IntArrayList(head.position);
                argumentPosition.add(head.childrenProcessed);
                return new Iterable<int[]>() {
                    @NotNull
                    @Override
                    public Iterator<int[]> iterator() {

                        Iterator<SAAState> args = argumentMap.getOrDefault(argumentPosition, Collections.EMPTY_SET).iterator();

                        return new Iterator<int[]>() {
                            @Override
                            public boolean hasNext() {
                                return args.hasNext();
                            }

                            @Override
                            public int[] next() {
                                return new int[]{stateID, sourceAssignmentAutomaton.getIdForState(args.next())};
                            }
                        };
                    }
                };

            } else {
                return Collections.emptyList();
            }
        } else if (pos == 1) {
            SAAState argument = sourceAssignmentAutomaton.getStateForId(stateID);
            int posSize = argument.position.size();
            if (posSize > 0 && argument.childrenProcessed == sourceAssignmentAutomaton.position2operations.get(argument.position).size()) {

                IntList headPosition = argument.position.subList(0, argument.position.size()-1);
                int argPos = argument.position.getInt(argument.position.size()-1);
                return new Iterable<int[]>() {
                    @NotNull
                    @Override
                    public Iterator<int[]> iterator() {

                        Iterator<SAAState> args = headMap.getOrDefault(new Pair<>(headPosition, argPos), Collections.EMPTY_SET).iterator();

                        return new Iterator<int[]>() {
                            @Override
                            public boolean hasNext() {
                                return args.hasNext();
                            }

                            @Override
                            public int[] next() {
                                return new int[]{sourceAssignmentAutomaton.getIdForState(args.next()), stateID};
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
            SAAState head = sourceAssignmentAutomaton.getStateForId(stateID);
            List<String> operationList = sourceAssignmentAutomaton.position2operations.get(head.position);
            if (operationList.size() > head.childrenProcessed) {
                Pair<IntList, Integer> key = new Pair<>(head.position, head.childrenProcessed);
                Set<SAAState> stateSet = headMap.computeIfAbsent(key, k -> new HashSet<>());
                stateSet.add(head);
            }
        } else if (pos == 1) {
            SAAState argument = sourceAssignmentAutomaton.getStateForId(stateID);
            int posSize = argument.position.size();
            if (posSize > 0 && argument.childrenProcessed == sourceAssignmentAutomaton.position2operations.get(argument.position).size()) {

                Set<SAAState> stateSet = argumentMap.computeIfAbsent(argument.position, k -> new HashSet<>());
                stateSet.add(argument);
            }
        }
    }
}
