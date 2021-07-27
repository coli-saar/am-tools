package de.saar.coli.amtools.astar;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;

class ParsingResult {
    public Tree<Or<String,SupertagWithType>> amTerm;
    public double logProb;
    public IntList leafOrderToStringOrder;

    public ParsingResult(Tree<Or<String,SupertagWithType>> amTerm, double logProb, IntList leafOrderToStringOrder) {
        this.amTerm = amTerm;
        this.logProb = logProb;
        this.leafOrderToStringOrder = leafOrderToStringOrder;
    }

    @Override
    public String toString() {
        return "ParsingResult{" + "amTerm=" + amTerm + ", logProb=" + logProb + ", leafOrderToStringOrder=" + leafOrderToStringOrder + '}';
    }

    public Pair<SGraph, ApplyModifyGraphAlgebra.Type> evaluate() {
        Tree<String> stringAmTerm = amTerm.dfs((node, childrenValues) -> {
            if (childrenValues.isEmpty()) {
                SupertagWithType st = node.getLabel().getRightValue();
                return Tree.create(st.getGraph().toIsiAmrStringWithSources()+ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP+st.getType().toString());
            } else {
                return Tree.create(node.getLabel().getLeftValue(), childrenValues);
            }
        });
        return new ApplyModifyGraphAlgebra().evaluate(stringAmTerm);
    }

}
