package de.saar.coli.amtools.script.amr_templates;

import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;

import java.io.IOException;
import java.util.*;

public class SampleFromTemplate {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws IOException, ParseException {
        int numSamples = 10;

        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/see_with.irtg");
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        Map<String, List<Tree<String>>> templateCounter = new HashMap<>();
        for (Tree<String> grammarTree : irtg.getAutomaton().language()) {
            templateCounter.computeIfAbsent(grammarTree.getLabel(), s -> new ArrayList<>()).add(grammarTree);
        }
        for (String template : templateCounter.keySet()) {
            System.out.println(template);
            System.out.println(templateCounter.get(template).size());
            List<Tree<String>> trees = templateCounter.get(template);
            Collections.shuffle(trees);
            for (int i = 0; i < Math.min(numSamples, trees.size()); i++) {
                Tree<String> tree = trees.get(i);
                Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(tree));
                Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(tree));
                System.out.println(stringInterp.getAlgebra().representAsString(stringResult));
                System.out.println(graphInterp.getAlgebra().representAsString(graphResult));
            }
            System.out.println("\n\n\n");
        }

    }

}
