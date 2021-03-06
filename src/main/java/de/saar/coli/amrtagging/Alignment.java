/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Represents an alignment between a span in a string and a set of nodes in a graph,
 * also marking nodes lexically related to the span. 
 * @author Jonas
 */
public class Alignment {
    
    public final Set<String> nodes;
    public Span span;
    public final Set<String> lexNodes;
    public final int color;
    private double weight;

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
    
    /**
     * Full constructor, creates a weighted alignment between nodes and span, marking
     * lexNodes as lexical.
     * @param nodes
     * @param span
     * @param lexNodes
     * @param color the color used for visualization, just use 0 if you don't know what to put here.
     * @param weight some algorithms use weighted alignments, use 1.0 as default if you don't know what to put here.
     */
    public Alignment(Set<String> nodes, Span span, Set<String> lexNodes, int color, double weight) {
        this.nodes = new HashSet(nodes);//such that if nodes is an abstract collection, we can still add something later.
        this.span = span;
        this.lexNodes = new HashSet(lexNodes);//such that if lexNodes is an abstract collection, we can still add something later.
        this.color = color;
        this.weight = weight;
    }
    
    /**
     * Creates an alignment between nodes and span, marking
     * lexNodes as lexical.
     * @param nodes
     * @param span
     * @param lexNodes
     * @param color the color used for visualization.
     */
    public Alignment(Set<String> nodes, Span span, Set<String> lexNodes, int color) {
        this(nodes, span, lexNodes, color, 1.0);
    }
    
    /**
     * Creates an alignment between nodes and span, with no nodes marked as lexical.
     * @param nodes
     * @param span 
     */
    public Alignment(Set<String> nodes, Span span) {
        this(nodes, span, Collections.EMPTY_SET, 0);
    }
    
    /**
     * Creates an alignment between the one node nn and the span [index,index+1];
     * also marks nn as lexical.
     * @param nn
     * @param index 
     */
    public Alignment(String nn, int index) {
        this(Collections.singleton(nn), new Span(index, index+1), Collections.singleton(nn), 0);
    }
    
    /**
     * Format is n1|n2|...||span||weight, where n1, n2,... are the aligned nodes,
     * followed by an exclamation point '!' if lexical. 
     * @return 
     */
    @Override
    public String toString() {
        StringJoiner sjN = new StringJoiner("|");
        for (String nn : nodes) {
            if (lexNodes.contains(nn)) {
                sjN.add(nn+"!");
            } else {
                sjN.add(nn);
            }
        }
        DecimalFormat df = new DecimalFormat();
        df.setMaximumFractionDigits(6);
        df.setMinimumFractionDigits(1);
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        return sjN.toString() + "||" + span.toString() + "||" + df.format(getWeight());
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.nodes);
        hash = 79 * hash + Objects.hashCode(this.span);
        hash = 79 * hash + Objects.hashCode(this.lexNodes);
        hash = 79 * hash + this.color;
        return hash;
    }

    /**
     * Equality depends on everything except weight.
     * @param obj
     * @return 
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Alignment other = (Alignment) obj;
        if (this.color != other.color) {
            return false;
        }
        if (!Objects.equals(this.nodes, other.nodes)) {
            return false;
        }
        if (!Objects.equals(this.span, other.span)) {
            return false;
        }
        if (!Objects.equals(this.lexNodes, other.lexNodes)) {
            return false;
        }
        return true;
    }
    
    /**
     * returns true iff nodes and span of the two objects are equal, i.e. if
     * they describe the same things being aligned to each other.
     * @param other
     * @return 
     */
    public boolean equalsBasic(Alignment other) {
        return nodes.equals(other.nodes) && span.equals(other.span);
    }
    
    /**
     * adds all nodes, indices and lexNodes of other into this alignment. (keeps color
     * of this alignment).
     * @param other 
     */
    public void mergeIntoThis(Alignment other) {
        nodes.addAll(other.nodes);
        
        span = span.merge(other.span);
        lexNodes.addAll(other.lexNodes);
    }
    
    
    /**
     * Inverse of the toString method, allowing a color to be specified for
     * visualization. Can read inputs that do not have a weight, using default
     * weight 1.0 there.
     * @param input
     * @param color
     * @return 
     */
    public static Alignment read(String input, int color) {
        if (!input.contains("||")) {
            return null;
        }
        String[] nodesAndIndeces = input.split("\\|\\|");
        Set<String> nodes = new HashSet<>();
        Set<String> lexNodes = new HashSet<>();
        for (String nn : nodesAndIndeces[0].split("\\|")) {
            if (nn.endsWith("!")) {
                nn = nn.substring(0, nn.length()-1);
                lexNodes.add(nn);
            }
            nodes.add(nn);
        }
        Span span;
        if (nodesAndIndeces[1].contains("-")) {
            String[] spanIndices = nodesAndIndeces[1].split("-");
            span = new Span(Integer.valueOf(spanIndices[0]), Integer.valueOf(spanIndices[1]));
        } else {
            int index = Integer.valueOf(nodesAndIndeces[1]);
            span = new Span(index, index+1);
        }
        double weight;
        if (nodesAndIndeces.length >= 3) {
            weight = Double.valueOf(nodesAndIndeces[2]);
        } else {
            weight = 1.0;
        }
        return new Alignment(nodes, span, lexNodes, color, weight);
    }
    
    /**
     * Inverse of the toString method, using default color 1 for visualization.
     * Can read inputs that do not have a weight, using default weight 1.0 there.
     * Use this read method if you don't know which one to use.
     * @param input
     * @return 
     */
    public static Alignment read(String input) {
        return read(input, 0);
    }
    
    /**
     * A span of indices, including start, excluding end.
     */
    public static class Span {
        
        public final int start;
        public final int end;
        
        /**
         * A span of indices, including start, excluding end.
         * @param start
         * @param end
         */
        public Span(int start, int end) {
            if (start >= end) {
                System.err.println("invalid values "+start+"-"+end+" for span; fixing to "+start+"-"+(start+1)+".");
                end = start+1;
            }
            this.start = start;
            this.end = end;
        }
        
        /**
         * From a string representation i-j, creates the span [i,j].
         * @param rep 
         */
        public Span(String rep) {
            String[] parts = rep.split("-");
            this.start = Integer.valueOf(parts[0]);
            this.end = Integer.valueOf(parts[1]);
        }
        
        @Override
        public String toString() {
            return start+"-"+end;
        }
        
        /**
         * Returns true iff end >= other.start && start <= other.end.
         * @param other
         * @return 
         */
        public boolean overlapsOrTouches(Span other) {
            return end >= other.start && start <= other.end; 
        }
        
        /**
         * Returns true iff end > other.start && start < other.end.
         * @param other
         * @return 
         */
        public boolean overlaps(Span other) {
            return end > other.start && start < other.end; 
        }
        
        /**
         * merges the two spans into another, returning [Math.min(start, other.start), Math.max(end, other.end)].
         * Prints a warning if the spans don't overlap or touch.
         * @param other
         * @return 
         */
        public Span merge(Span other) {
            if (!overlapsOrTouches(other)) {
                System.err.println("Warning: merging non-touching spans "+this.toString() +" and "+other.toString()+".");
            }
            return new Span(Math.min(start, other.start), Math.max(end, other.end));
        }
        
        /**
         * returns true iff end == start+1.
         * @return 
         */
        public boolean isSingleton() {
            return end == start+1;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + this.start;
            hash = 79 * hash + this.end;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Span other = (Span) obj;
            if (this.start != other.start) {
                return false;
            }
            if (this.end != other.end) {
                return false;
            }
            return true;
        }
        
        
        
    }
    
}
