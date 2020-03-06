package de.saar.coli.amtools.script;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.tree.Tree;

public class TestMultisource {

    public static void main(String[] args) {

        IsiAmrInputCodec codec = new IsiAmrInputCodec();

        String and = "(op1 <root, op1> :and_c (op2 <op2>))--TYPE--(op1, op2)";

        String g = "(g<root>/graph)";

        Tree<String> term = Tree.create("APP_op2", Tree.create("APP_op1", Tree.create(and), Tree.create(g)), Tree.create(g));

        System.err.println(new ApplyModifyGraphAlgebra().evaluate(term));

    }

}
