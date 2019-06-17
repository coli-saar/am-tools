/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.saar.basic.Pair;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.MutableInteger;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Supertag probabilities for one sentence.
 *
 * @author koller
 */
public class SupertagProbabilities {

    private Int2ObjectMap<Int2DoubleMap> supertags;
    private double defaultValue;
    private int nullSupertagId;

    public static interface Consumer {

        public void accept(int supertagId, double prob);
    }

    public SupertagProbabilities(double defaultValue, int nullSupertagId) {
        supertags = new Int2ObjectOpenHashMap<>();
        this.defaultValue = defaultValue;
        this.nullSupertagId = nullSupertagId;
    }

    public void put(int pos, int supertagId, double prob) {
        Int2DoubleMap m = supertags.get(pos);
        if (m == null) {
            m = new Int2DoubleOpenHashMap();
            m.defaultReturnValue(this.defaultValue);
            supertags.put(pos, m);
        }

        m.put(supertagId, prob);
    }

    public double get(int pos, int supertagId) {
        Int2DoubleMap m = supertags.get(pos);
        if (m == null) {
            return defaultValue;
        } else {
            return m.get(supertagId);
        }
    }

    public void foreachInOrder(int pos, Consumer fn) {
        Int2DoubleMap m = supertags.get(pos);
        if (m != null) {
            List<Int2DoubleMap.Entry> sortedEntries = new ArrayList<>(m.int2DoubleEntrySet());
            sortedEntries.sort((Int2DoubleMap.Entry o1, Int2DoubleMap.Entry o2)
                    -> -Double.compare(o1.getDoubleValue(), o2.getDoubleValue()));//sort in decreasing order
            for (Int2DoubleMap.Entry entry : sortedEntries) {
                fn.accept(entry.getIntKey(), entry.getDoubleValue());
                //System.err.println(pos+" || "+entry);
            }
        }
    }

    public double getMaxProb(int pos) {
        Int2DoubleMap m = supertags.get(pos);
        double max = Double.NEGATIVE_INFINITY;

        if (m != null) {
            for (Int2DoubleMap.Entry entry : m.int2DoubleEntrySet()) {
                // DON'T skip NULL
                max = Math.max(max, entry.getDoubleValue());

                
//                
//                if (entry.getIntKey() != nullSupertagId) { // skip NULL
//                    max = Math.max(max, entry.getDoubleValue());
//                }
            }
        }

        return max;
    }
    
    public Pair<Integer,Double> getBestSupertag(int pos) {
        Int2DoubleMap m = supertags.get(pos);
        double max = Double.NEGATIVE_INFINITY;
        int tag = -1;

        if (m != null) {
            for (Int2DoubleMap.Entry entry : m.int2DoubleEntrySet()) {
                if (entry.getIntKey() != nullSupertagId) { // skip NULL
                    if( entry.getDoubleValue() > max ) {
                        max = entry.getDoubleValue();
                        tag = entry.getIntKey();
                    }
                }
            }
        }

        return new Pair(tag, max);
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner("\n");
        for (int i = 0; i < supertags.size(); i++) {
            sj.add(i + "=>" + supertags.get(i).toString());
        }
        return "SupertagProbabilities:\n" + sj.toString();
    }

    public int getLength() {
        return supertags.size();
    }

    public void prettyprint(Int2ObjectMap<Pair<SGraph, Type>> idToSupertag, PrintStream out) {
        for (int pos = 0; pos < getLength(); pos++) {
            String prefix = String.format("[%2d] ", pos);
            String blank = String.format("%" + prefix.length() + "s", " ");
            MutableInteger x = new MutableInteger(0);

            foreachInOrder(pos, (supertagId, score) -> {
                if( x.incValue() < 5 ) {
                    out.format("%s%d %f %s\n", x.getValue() == 1 ? prefix : blank, supertagId, score, idToSupertag.get(supertagId).left);
                }
            });
        }
    }

    public int getNullSupertagId() {
        return nullSupertagId;
    }
    
    
}
