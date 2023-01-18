package de.saar.coli.amtools.script.amr_templates;

import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeParser;

import java.io.IOException;

public class ExploreTemplate {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws IOException, ParseException {
        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/see_with.irtg");
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        Counter<String> templateCounter = new Counter<>();
        for (Tree<String> grammarTree : irtg.getAutomaton().language()) {
            templateCounter.add(grammarTree.getLabel());
//            System.out.println(grammarTree);
            Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(grammarTree));
            Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(grammarTree));
//            System.out.println(stringInterp.getAlgebra().representAsString(stringResult));
//            System.out.println(graphInterp.getAlgebra().representAsString(graphResult));
        }
        templateCounter.printAllSorted();

    }

}
