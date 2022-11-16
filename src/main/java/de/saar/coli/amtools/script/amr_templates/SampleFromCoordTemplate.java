package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.Tree;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static de.saar.coli.amtools.script.amr_templates.SampleFromTemplate.fixAMRString;

public class SampleFromCoordTemplate {

    public static void main(String[] args) throws IOException {

        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/and_ice_cream.irtg");
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");


        String outputFile = "examples/amr_template_grammars/long_lists.txt";
        String description = "Ice cream flavor lists. Created by a grammar.";

        String conjunct_state = "NP_flavor";
        int maxConjuncts = 50;
        int minConjuncts = 4;
        int samplesPerConjunctCount = 3;
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

        if (maxConjuncts > conjunct_rules.size() - 1) {
            System.out.println("Warning: max_conjuncts ("+maxConjuncts+") is larger than the number of conjunct rules ("+conjunct_rules.size()+") minus 1. " +
                    "Using the number of conjunct rules minus 1 instead.");
            maxConjuncts = conjunct_rules.size() - 1;
        }

        for (int i = minConjuncts; i<= maxConjuncts; i++) {


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

        List<Tree<String>> samples = new ArrayList<>();
        for (int i = minConjuncts; i<=maxConjuncts; i++) {
            int emergency_break = samplesPerConjunctCount * 100;
            int attemptCounter = 0;
            Set<Tree<String>> samplesForThisConjunctCount = new HashSet<>();
            while (samplesForThisConjunctCount.size() < samplesPerConjunctCount && attemptCounter < emergency_break) {
                Collections.shuffle(conjunct_rules);
                List<Tree<String>> conjuncts = conjunct_rules.stream().limit(i).map(rule ->
                        Tree.create(rule.getLabel(automaton))).collect(Collectors.toList());
                Tree<String> coordTree = Tree.create("coord_" + i, conjuncts);
                samplesForThisConjunctCount.add(Tree.create("template", coordTree));
                attemptCounter++;
            }
            if (attemptCounter >= emergency_break) {
                System.out.println("Warning: Could not find "+samplesPerConjunctCount+" samples for "+i+" conjuncts. Only found "+samplesForThisConjunctCount.size());
            }
            samples.addAll(samplesForThisConjunctCount);
        }


        for (Tree<String> sample : samples) {
            System.out.println(sample);
            System.out.println(postprocessString((List<String>)stringInterp.interpret(sample)));
            Object graphResult = graphInterp.interpret(sample);
            System.out.println(fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>)graphResult).left.toIsiAmrString()));
            System.out.println();
        }

        SampleFromTemplate.writeSamplesToFile(outputFile, samples, description, irtg);

    }

    private static String postprocessString(List<String> tokens) {
        return tokens.stream().collect(Collectors.joining(" ")).replaceAll(" , ", ", ")
                .replaceAll(" \\.", ".");
    }

}
