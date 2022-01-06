package de.saar.coli.amtools.decomposition.automata.source_assignment;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.*;

public class SAAState {

    final IntList position;
    final int childrenProcessed;
    final Map<String, String> sourceAssignments;

    public SAAState(IntList position, int childrenProcessed, Map<String, String> sourceAssignments) {
        this.position = position;
        this.childrenProcessed = childrenProcessed;
        this.sourceAssignments = sourceAssignments;
    }


    public SAAState increment() {
        //TODO can reduce automata size here by changing source assignments to remove the unnecessary ones, once a proper children order is implemented.
        return new SAAState(position, childrenProcessed+1, sourceAssignments);
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
        SAAState otherSAAState = (SAAState) o;
        return childrenProcessed == otherSAAState.childrenProcessed &&
                Objects.equals(position, otherSAAState.position) &&
                Objects.equals(sourceAssignments, otherSAAState.sourceAssignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, childrenProcessed, sourceAssignments);
    }
}
