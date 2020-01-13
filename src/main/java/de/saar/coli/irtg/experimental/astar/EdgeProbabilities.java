/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.saar.basic.Pair;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.MutableDouble;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.PrintStream;

// import org.codehaus.groovy.runtime.powerassert.SourceText;

/**
 *
 * @author koller
 */
public class EdgeProbabilities {

    private Int2ObjectMap<Int2ObjectMap<Int2DoubleMap>> probs;    // probs[from][to][labelId] = log P(edge)
    private double defaultValue;
    private int maxFrom, maxTo;
    private IntIterable EMPTY = new IntArrayList();
    private IntSet ignoredEdgeLabels; // edge labels that should be ignored by #getBestIncomingProb and #getBestIncomingEdge
    private int ignoreEdgeId;
    private int rootEdgeId;

    /**
     * Creates a new edge probs structure.
     * 
     * @param defaultValue log-probability of edges that don't exist (approximates -INFINITY)
     * @param ignoreEdgeId the ID of the edge label "IGNORE"
     */
    public EdgeProbabilities(double defaultValue, int ignoreEdgeId, int rootEdgeId) {
        probs = new Int2ObjectOpenHashMap<>();
        this.defaultValue = defaultValue;
        ignoredEdgeLabels = new IntOpenHashSet();
        this.ignoreEdgeId = ignoreEdgeId;
        this.rootEdgeId = rootEdgeId;
    }

    public void addIgnoredEdgeLabel(int labelId) {
        ignoredEdgeLabels.add(labelId);
    }

    public void set(int from, int to, int labelId, double value) {
        Int2ObjectMap<Int2DoubleMap> probsFrom = probs.get(from);
        if (probsFrom == null) {
            probsFrom = new Int2ObjectOpenHashMap<>();
            probs.put(from, probsFrom);
        }

        Int2DoubleMap probsFromTo = probsFrom.get(to);
        if (probsFromTo == null) {
            probsFromTo = new Int2DoubleOpenHashMap();
            probsFromTo.defaultReturnValue(defaultValue);
            probsFrom.put(to, probsFromTo);
        }

        probsFromTo.put(labelId, value);

        maxFrom = Math.max(maxFrom, from);
        maxTo = Math.max(maxTo, to);
    }

    public double get(int from, int to, int labelId) {
        Int2ObjectMap<Int2DoubleMap> probsFrom = probs.get(from);

        if (probsFrom == null) {
            return defaultValue;
        } else {
            Int2DoubleMap pft = probsFrom.get(to);

            if (pft == null) {
                return defaultValue;
            } else {
                return pft.get(labelId);
            }
        }
    }

    public IntIterable getEdgeLabelIds(int from, int to) {
        Int2ObjectMap<Int2DoubleMap> probsFrom = probs.get(from);

        if (probsFrom == null) {
            return EMPTY;
        } else {
            Int2DoubleMap pft = probsFrom.get(to);

            if (pft == null) {
                return EMPTY;
            } else {
                return pft.keySet();
            }
        }
    }

    public int getLength() {
        return Math.max(maxFrom, maxTo);
    }

    /**
     * Returns the max probability of edges into this position. When calculating
     * the max probability, edges with one of the labels specified by {@link #addIgnoredEdgeLabel(int)
     * } will be ignored.
     *
     * @param to
     * @return
     */
    public double getBestIncomingProb(int to) {
        MutableDouble ret = new MutableDouble(defaultValue);

        for (Int2ObjectMap.Entry<Int2ObjectMap<Int2DoubleMap>> entry : probs.int2ObjectEntrySet()) {
            Int2DoubleMap m = entry.getValue().get(to);
            if (m != null) {
                for (Int2DoubleMap.Entry e : m.int2DoubleEntrySet()) {
                    if (!ignoredEdgeLabels.contains(e.getIntKey())) {
                        ret.setValue(Math.max(ret.getValue(), e.getDoubleValue()));
                    }
                }
            }
        }

        return ret.getValue();
    }

    /**
     * Returns the max probability of edges into this position. Edges marked as
     * "ignored" will be ignored. Furthermore, edges that come from a position
     * in the given item except for the item's root node will also be ignored.
     *
     * @param to
     * @param item
     * @return
     */
    public double getBestIncomingProbExceptIntoItemNonroot(int to, Item item) {
        MutableDouble ret = new MutableDouble(defaultValue);

        for (Int2ObjectMap.Entry<Int2ObjectMap<Int2DoubleMap>> entry : probs.int2ObjectEntrySet()) {
            int from = entry.getIntKey();

            if (from == item.getRoot() || from < item.getStart() || from >= item.getEnd()) {
                Int2DoubleMap m = entry.getValue().get(to);
                if (m != null) {
                    for (Int2DoubleMap.Entry e : m.int2DoubleEntrySet()) {
                        if (!ignoredEdgeLabels.contains(e.getIntKey())) {
                            ret.setValue(Math.max(ret.getValue(), e.getDoubleValue()));
                        }
                    }
                }
            }
        }

        return ret.getValue();
    }

    public double getBestIncomingProbWithEdgeLabel(int to, int edgeLabelId) {
        MutableDouble ret = new MutableDouble(defaultValue);

        for (Int2ObjectMap.Entry<Int2ObjectMap<Int2DoubleMap>> entry : probs.int2ObjectEntrySet()) {
            Int2DoubleMap m = entry.getValue().get(to);
            if (m != null) {
                ret.setValue(Math.max(ret.getValue(), m.get(edgeLabelId)));
            }
        }

        return ret.getValue();
    }

    /**
     * edgeLabelId < 0 => any edge
     *
     * @param to
     * @param edgeLabelId
     * @return
     */
    public Pair<Edge, Double> getBestIncomingEdgeWithLabel(int to, int edgeLabelId) {
        MutableDouble val = new MutableDouble(defaultValue);
        Edge edge = new Edge(0, 0, 0);

        for (Int2ObjectMap.Entry<Int2ObjectMap<Int2DoubleMap>> entry : probs.int2ObjectEntrySet()) {
            Int2DoubleMap m = entry.getValue().get(to);
            if (m != null) {
                for (Int2DoubleMap.Entry e : m.int2DoubleEntrySet()) {
                    if (edgeLabelId == e.getIntKey()) {
                        if (e.getDoubleValue() > val.getValue()) {
                            val.setValue(e.getDoubleValue());
                            edge.setFrom(entry.getIntKey());
                            edge.setTo(to);
                            edge.setLabelId(e.getIntKey());
                        }
                    }
                }
            }
        }

        if (val.getValue() == defaultValue) {
            System.err.printf("No incoming edges for %d with label %d\n", to, edgeLabelId);
            edge = null;
        }

        return new Pair(edge, val.getValue());
    }

    /**
     * Returns the highest-probability edge into this position. Edges with one
     * of the labels specified by {@link #addIgnoredEdgeLabel(int)
     * } will be ignored.
     *
     * @param to
     * @return
     */
    public Pair<Edge, Double> getBestIncomingEdge(int to) {
        MutableDouble val = new MutableDouble(defaultValue);
        Edge edge = new Edge(0, 0, 0);

        for (Int2ObjectMap.Entry<Int2ObjectMap<Int2DoubleMap>> entry : probs.int2ObjectEntrySet()) {
            Int2DoubleMap m = entry.getValue().get(to);
            if (m != null) {
                for (Int2DoubleMap.Entry e : m.int2DoubleEntrySet()) {
                    if (!ignoredEdgeLabels.contains(e.getIntKey())) {
                        if (e.getDoubleValue() > val.getValue()) {
                            val.setValue(e.getDoubleValue());
                            edge.setFrom(entry.getIntKey());
                            edge.setTo(to);
                            edge.setLabelId(e.getIntKey());
                        }
                    }
                }
            }
        }

        if (val.getValue() == defaultValue) {
            edge = null;
        }

        return new Pair(edge, val.getValue());
    }

    public static class Edge {

        private int from, to, labelId;

        public Edge(int from, int to, int labelId) {
            this.from = from;
            this.to = to;
            this.labelId = labelId;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        public int getLabelId() {
            return labelId;
        }

        public void setFrom(int from) {
            this.from = from;
        }

        public void setTo(int to) {
            this.to = to;
        }

        public void setLabelId(int labelId) {
            this.labelId = labelId;
        }

    }

    public void prettyprint(Interner<String> edgeLabelLexicon, PrintStream out) {
        Int2ObjectMap<String> pp = new Int2ObjectOpenHashMap<>();
        pp.defaultReturnValue("");

        for (int from : probs.keySet()) {
            Int2ObjectMap<Int2DoubleMap> a = probs.get(from);

            for (int to : a.keySet()) {
                Int2DoubleMap b = a.get(to);

                for (int labelId : b.keySet()) {
                    String s = String.format("[%d -> %d] %f %s\n", from, to, b.get(labelId), edgeLabelLexicon.resolveId(labelId));
                    pp.put(to, pp.get(to) + s);
                }
            }
        }

        for (int to : pp.keySet()) {
            out.println();
            out.println(pp.get(to));
        }
    }

    public int getIgnoreEdgeId() {
        return ignoreEdgeId;
    }

    public int getRootEdgeId() {
        return rootEdgeId;
    }
    
}
