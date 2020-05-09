package de.saar.coli.irtg.experimental.astar;

import de.up.ling.tree.Tree;
import it.unimi.dsi.fastutil.ints.IntList;

class ParsingResult {
    public Tree<String> amTerm;
    public double logProb;
    public IntList leafOrderToStringOrder;

    public ParsingResult(Tree<String> amTerm, double logProb, IntList leafOrderToStringOrder) {
        this.amTerm = amTerm;
        this.logProb = logProb;
        this.leafOrderToStringOrder = leafOrderToStringOrder;
    }

    @Override
    public String toString() {
        return "ParsingResult{" + "amTerm=" + amTerm + ", logProb=" + logProb + ", leafOrderToStringOrder=" + leafOrderToStringOrder + '}';
    }

}
