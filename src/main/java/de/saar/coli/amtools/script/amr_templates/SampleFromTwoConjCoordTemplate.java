package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeParser;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static de.saar.coli.amtools.script.amr_templates.SampleFromTemplate.fixAMRString;

public class SampleFromTwoConjCoordTemplate {

    public static void main(String[] args) throws IOException, ParseException {



        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/i_counted.irtg");
        String outputFile = "examples/amr_template_grammars/i_counted.txt";
        String outputFileSingletons = "examples/amr_template_grammars/i_counted_singletons.txt";
        String description = "Things that I counted (long list). Created by a grammar.";
        String descriptionSingletons = "Singletons for checking long list grammar. Created by a grammar.";
        String conjunct_state = "NP_full";
        String conjunct_state_left = "NP_thing";
        String conjunct_state_right = "Number";
        String combining_rule_label = "combine";

        int maxConjuncts = 35;
        int minConjuncts = 4;
        int samplesPerConjunctCount = 1;
        String coord_state = "NP_and";
        String coordination = "and";

        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        ConcreteTreeAutomaton<String> automaton = (ConcreteTreeAutomaton)irtg.getAutomaton();

        List<Rule> conjunct_rules_left = new ArrayList<>();
        List<Rule> conjunct_rules_right = new ArrayList<>();
        for (Rule rule : automaton.getRuleSet()) {
            String parentState = automaton.getStateForId(rule.getParent());
            if (parentState.equals(conjunct_state_left)) {
                conjunct_rules_left.add(rule);
            }
        }
        for (Rule rule : automaton.getRuleSet()) {
            String parentState = automaton.getStateForId(rule.getParent());
            if (parentState.equals(conjunct_state_right)) {
                conjunct_rules_right.add(rule);
            }
        }

        System.err.println("Found "+conjunct_rules_left.size()+" conjunct rules for left side.");
        System.err.println("Found "+conjunct_rules_right.size()+" conjunct rules for right side.");

        int conjuncts_found = Math.min(conjunct_rules_left.size(), conjunct_rules_right.size());
        if (maxConjuncts > conjuncts_found - 1) {
            System.out.println("Warning: max_conjuncts ("+maxConjuncts+") is larger than the number of conjunct rules ("+conjuncts_found+") minus 1. " +
                    "Using the number of conjunct rules minus 1 instead.");
            maxConjuncts = conjuncts_found - 1;
        }

//        Rule single_rule = automaton.createRule(coord_state, "singleton", new String[]{coord_state});
        String singleRuleLabel = "singleton";
        automaton.getSignature().addSymbol(singleRuleLabel, 1);
        Tree<String> single_tree = Tree.create("?1");
        stringInterp.getHomomorphism().add(singleRuleLabel, single_tree);
        graphInterp.getHomomorphism().add(singleRuleLabel, single_tree);

        List<Tree<String>> singletonSamples = new ArrayList<>();
        for (int i = 0; i < Math.max(conjunct_rules_left.size(), conjunct_rules_right.size()); i++) {
            String label_left = conjunct_rules_left.get(i % conjunct_rules_left.size()).getLabel(automaton);
            String label_right = conjunct_rules_right.get(i % conjunct_rules_right.size()).getLabel(automaton);
            Tree<String> grammar_tree = TreeParser.parse("template("+singleRuleLabel+"("+combining_rule_label+
                    "("+label_left+", "+label_right+")))");
            singletonSamples.add(grammar_tree);
        }
        SampleFromTemplate.writeSamplesToFile(outputFileSingletons, singletonSamples, descriptionSingletons, irtg);
        // never actually added the rule to the automaton, so we don't have to remove it either

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
                Collections.shuffle(conjunct_rules_left);
                Collections.shuffle(conjunct_rules_right);
                List<Tree<String>> conjuncts = new ArrayList();
                for (int j = 0; j < i; j++) {
                    String label_left = conjunct_rules_left.get(j).getLabel(automaton);
                    String label_right = conjunct_rules_right.get(j).getLabel(automaton);
                    Tree<String> grammar_tree = TreeParser.parse(combining_rule_label+
                            "("+label_left+", "+label_right+")");
                    conjuncts.add(grammar_tree);
                }
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
            System.out.println(SampleFromTemplate.postprocessString((List<String>)stringInterp.interpret(sample)));
            Object graphResult = graphInterp.interpret(sample);
            System.out.println(fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>)graphResult).left.toIsiAmrString()));
            System.out.println();
        }

        SampleFromTemplate.writeSamplesToFile(outputFile, samples, description, irtg);

    }

}
