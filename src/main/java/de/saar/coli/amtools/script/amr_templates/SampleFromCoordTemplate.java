package de.saar.coli.amtools.script.amr_templates;

import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.Tree;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleFromCoordTemplate {

    public static void main(String[] args) throws IOException {

        int numSamples = 10;

        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/and_ice_cream.irtg");
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");

        String conjunct_state = "NP_flavor";
        int maxConjuncts = 50;
        String coord_state = "NP_and";
        String coordination = "and";

        ConcreteTreeAutomaton<String> automaton = (ConcreteTreeAutomaton)irtg.getAutomaton();

        List<Rule> conjunct_rules = new ArrayList<>();
        for (Rule rule : automaton.getRuleSet()) {
            String parentState = automaton.getStateForId(rule.getParent());
            if (parentState.equals(conjunct_state)) {
                conjunct_rules.add(rule);
            }
        }

        if (maxConjuncts > conjunct_rules.size()) {
            System.out.println("Warning: max_conjuncts ("+maxConjuncts+") is larger than the number of conjunct rules ("+conjunct_rules.size()+"). " +
                    "Using the number of conjunct rules instead.");
            maxConjuncts = conjunct_rules.size();
        }

        for (int i = 2; i< maxConjuncts; i++) {
            List<String> children = new ArrayList<>();
            String parentState = coord_state;
            for (int j = 0; j < i; j++) {
                children.add(conjunct_state);
            }
            String grammarLabel = "coord_"+i;
            automaton.addRule(automaton.createRule(parentState, grammarLabel, children));

            Tree<String> stringTree = Tree.create("*", Tree.create(coordination), Tree.create("?"+i));
            stringTree = Tree.create("*", Tree.create("?"+(i-1)), stringTree);
            for (int j = i-2; j >= 1; j--) {
                stringTree = Tree.create("*", Tree.create("?"+j), Tree.create("*", Tree.create(","), stringTree));
            }
            stringInterp.getHomomorphism().add(grammarLabel, stringTree);
//            System.out.println(stringTree);

            StringBuilder graphConst = new StringBuilder("(r<root> / " + coordination);
            StringBuilder typeString = new StringBuilder("(");
            for (int j = 1; j<=i; j++) {
                graphConst.append(" :op").append(j).append(" (e").append(j).append("<op").append(j).append(">)");
                typeString.append("op").append(j).append("()");
                if (j < i) {
                    typeString.append(", ");
                }
            }
            graphConst.append(")");
            typeString.append(")");
            graphConst.append(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP).append(typeString);


            Tree<String> graphTree = Tree.create("APP_op"+i, Tree.create(graphConst.toString()), Tree.create("?"+i));
            for (int j = i-1; j >= 1; j--) {
                graphTree = Tree.create("APP_op"+j, graphTree, Tree.create("?"+j));
            }
            graphInterp.getHomomorphism().add(grammarLabel, graphTree);
//            System.out.println(graphTree);
        }


//        System.out.println(automaton);
//        Tree<String> sample = automaton.languageIterator().next();
//        System.out.println(sample);
//        System.out.println(stringInterp.getHomomorphism().apply(sample));
//        System.out.println(((List<String>)stringInterp.interpret(sample)).stream().collect(Collectors.joining(" ")));
//        System.out.println(graphInterp.getHomomorphism().apply(sample));
//        System.out.println(graphInterp.interpret(sample));


        Set<Tree<String>> samples = new HashSet<>();
        int emergency_break = numSamples*100;
        int attemptCounter = 0;
        Random random = new Random();
        while (samples.size() < numSamples && attemptCounter < emergency_break) {
            int numConjuncts = random.nextInt(maxConjuncts-2)+2;
            Collections.shuffle(conjunct_rules);
            List<Tree<String>> conjuncts = conjunct_rules.stream().limit(numConjuncts).map(rule ->
                    Tree.create(rule.getLabel(automaton))).collect(Collectors.toList());
            Tree<String> coordTree = Tree.create("coord_"+numConjuncts, conjuncts);
            samples.add(Tree.create("template", coordTree));
            attemptCounter++;
        }

        for (Tree<String> sample : samples) {
            System.out.println(sample);
            System.out.println(postprocessString((List<String>)stringInterp.interpret(sample)));
            System.out.println(graphInterp.interpret(sample));
            System.out.println();
        }



    }

    private static String postprocessString(List<String> tokens) {
        return tokens.stream().collect(Collectors.joining(" ")).replaceAll(" , ", ", ")
                .replaceAll(" \\.", ".");
    }

}
