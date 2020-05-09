/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;

/**
 *
 * @author koller
 */
public class Item implements Comparable<Item>  {
    private int start, end, root;
    private int type;
    private double logProb;
    private double outsideEstimate;
    
    private int operation;
    private Item left, right;

    public Item(int start, int end, int root, int type, double logProb) {
        this.start = start;
        this.end = end;
        this.root = root;
        this.type = type;
        this.logProb = logProb;
        this.outsideEstimate = 0;
    }
    
    public void setCreatedBySupertag(int operation) {
        this.operation = operation;
        left = null;
        right = null;
    }
    
    public void setCreatedByOperation(int operation, Item functor, Item argument) {
        this.operation = operation;
        this.left = functor;
        this.right = argument;
    }

    public double getOutsideEstimate() {
        return outsideEstimate;
    }

    public void setOutsideEstimate(double outsideEstimate) {
        this.outsideEstimate = outsideEstimate;
    }
    
    public double getScore() {
        return getLogProb() + getOutsideEstimate();
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getRoot() {
        return root;
    }

    public int getType() {
        return type;
    }

    public double getLogProb() {
        return logProb;
    }

    public int getOperation() {
        return operation;
    }

    public Item getLeft() {
        return left;
    }

    public Item getRight() {
        return right;
    }

    public boolean isCreatedBySupertag() {
        return left == null && right == null;
    }
    
    /** 
     * for printing debug information
     * @return 
     */
    public String getSpan() {
        return "["+start+","+end+"]";
    }
    

    @Override
    public String toString() {
        return String.format("<[%d-%d] root %d  Type %s (prob=%f est=%f)>", start, end, root, type, logProb, getScore());
    }
    
    public String toString(TypeInterner<Type> typelex) {
        return String.format("<[%d-%d] root %d  Type %s (prob=%f est_outside=%f est_total=%f)>", start, end, root, typelex.resolveID(type), logProb, outsideEstimate, getScore());
    }
    
    public String shortString() {
        return String.format("[%d-%d:%d]", start, end, root);
    }

    @Override
    public int compareTo(Item o) {
        return - Double.compare(getScore(), o.getScore()); // sort in descending order
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + this.start;
        hash = 79 * hash + this.end;
        hash = 79 * hash + this.root;
        hash = 79 * hash + this.type;
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
        final Item other = (Item) obj;
        if (this.start != other.start) {
            return false;
        }
        if (this.end != other.end) {
            return false;
        }
        if (this.root != other.root) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return true;
    }
    
    public Item subtract(Item other) {
        if( start != other.start && end != other.end ) {
            return null;
        } else if( start == other.start ) {
            int s = Math.min(end, other.end);
            int e = Math.max(end, other.end);
            return new Item(s, e, 0, 0, 0);
        } else {
            int s = Math.min(start, other.start);
            int e = Math.max(start, other.start);
            return new Item(s, e, 0, 0, 0);
        }
    }
    
}
