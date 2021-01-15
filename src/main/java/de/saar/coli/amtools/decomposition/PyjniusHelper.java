package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.up.ling.irtg.automata.*;
import de.up.ling.irtg.codec.BinaryIrtgInputCodec;
import de.up.ling.irtg.semiring.LogDoubleArithmeticSemiring;
import de.up.ling.irtg.semiring.ViterbiWithBackpointerSemiring;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class PyjniusHelper {

    static LogDoubleArithmeticSemiring logDoubleArithmeticSemiring = new LogDoubleArithmeticSemiring();

    public static float[] computeOuterProbabilities(float[] ruleWeights, List<Rule> allRulesInBottomUpOrder, int maxStateIDPlusOne, IntSet finalStates) {
        int i = 0;
        for (Rule rule : allRulesInBottomUpOrder) {
            rule.setWeight(Math.exp(ruleWeights[i]));
            i++;
        }
        Double[] logInsides = TreeAutomaton.<Double>evaluateRuleListInSemiring(Double.class, LogDoubleArithmeticSemiring.INSTANCE,
                rule -> Math.log(rule.getWeight()), allRulesInBottomUpOrder, maxStateIDPlusOne);
        Double[] logOutsides = TreeAutomaton.evaluateRuleListInSemiringTopDown(Double.class, LogDoubleArithmeticSemiring.INSTANCE,
                new RuleEvaluatorTopDown<Double>() {
                    @Override
                    public Double initialValue() {
                        return 0.0;//0.0 is the 1.0 of log space
                    }

                    @Override
                    public Double evaluateRule(Rule rule, int i) {
                        Double ret = Math.log(rule.getWeight());
                        for (int j = 0; j < rule.getArity(); j++) {
                            if (j != i) {
                                ret += logInsides[rule.getChildren()[j]];
                            }
                        }
                        return ret;
                    }
                }, allRulesInBottomUpOrder, maxStateIDPlusOne, finalStates);
        double totalInside = Double.NaN;
        for (int finalState : finalStates) {
            if (Double.isNaN(totalInside)) {
                totalInside = logInsides[finalState];
            } else {
                totalInside = logDoubleArithmeticSemiring.add(totalInside, logInsides[finalState]);
            }
        }
        float[] ret = new float[ruleWeights.length]; // if ruleWeights has more weights than rules, then ret is 0 there
        // this is desired when using "fake rules" in python
        i = 0;
        for (Rule rule : allRulesInBottomUpOrder) {
            float outerWeight = logOutsides[rule.getParent()].floatValue();
            for (int child : rule.getChildren()) {
                outerWeight += logInsides[child];
            }
            outerWeight -= totalInside; // for normalization across automata, or, from a different perspective, so that we get *log* inside loss.
            ret[i]=outerWeight;
            i++;
        }
        return ret;
    }

    public static float getTotalLogInside(float[] ruleWeights, List<Rule> allRulesInBottomUpOrder, int maxStateIDPlusOne, IntSet finalStates) {
        int i = 0;
        for (Rule rule : allRulesInBottomUpOrder) {
            rule.setWeight(Math.exp(ruleWeights[i]));
            i++;
        }
        Double[] logInsides = TreeAutomaton.evaluateRuleListInSemiring(Double.class, LogDoubleArithmeticSemiring.INSTANCE,
                new RuleEvaluator<Double>() {
                    @Override
                    public Double evaluateRule(Rule rule) {
                        return Math.log(rule.getWeight());
                    }
                }, allRulesInBottomUpOrder, maxStateIDPlusOne);
        double totalInside = Double.NaN;
        for (int finalState : finalStates) {
            if (Double.isNaN(totalInside)) {
                totalInside = logInsides[finalState];
            } else {
                totalInside = logDoubleArithmeticSemiring.add(totalInside, logInsides[finalState]);
            }
        }
        return (float)totalInside;
    }


    public static Pair<List<Rule>, Double> getViterbi(float[] ruleWeights, List<Rule> allRulesInBottomUpOrder, int maxStateIDPlusOne, IntSet finalStates) {
        int i = 0;
        for (Rule rule : allRulesInBottomUpOrder) {
            rule.setWeight(Math.exp(ruleWeights[i]));
            i++;
        }
        ;
        Pair<Double, Rule>[] map = TreeAutomaton.evaluateRuleListInSemiring((Class<Pair<Double, Rule>>) new Pair<Double, Rule>(null, null).getClass(),
                ViterbiWithBackpointerSemiring.INSTANCE,
                (rule -> new Pair<>(rule.getWeight(), rule)), allRulesInBottomUpOrder, maxStateIDPlusOne);
        // find final state with highest weight
        int bestFinalState = -1;
        double weightBestFinalState = Double.NEGATIVE_INFINITY;

        for (int s : finalStates) {
            Pair<Double, Rule> result = map[s];

            // ignore final states that (for some crazy reason) can't
            // be expanded
            if (result.right != null) {
                if (map[s].left > weightBestFinalState) {
                    bestFinalState = s;
                    weightBestFinalState = map[s].left;
                }
            }
        }

        if( bestFinalState <= 0 ) {
            // no final state with weight > -inf found
            return null;
        }

//        assert bestFinalState > -1 : "Viterbi failed: no useful final state found";

        // extract best tree from backpointers
        Tree<Rule> bestTree = extractTreeFromViterbi(bestFinalState, map, 0);

        List<Rule> allRules = new ArrayList<>();
        bestTree.dfs((TreeBottomUpVisitor<Rule, Void>) (node, childrenValues) -> {
            allRules.add(node.getLabel());
            return null;
        });

        return new Pair(allRules, weightBestFinalState);
    }


    private static Tree<Rule> extractTreeFromViterbi(int state, Pair<Double, Rule>[] map, int depth) {

        if (map[state] != null) {

            Rule backpointer = map[state].right;
            List<Tree<Rule>> childTrees = new ArrayList<>();

            for (int child : backpointer.getChildren()) {
                Tree<Rule> childTree = extractTreeFromViterbi(child, map, depth + 1);

                childTrees.add(childTree);
            }

            Tree<Rule> ret = Tree.create(backpointer, childTrees);
            return ret;
        } else {
            System.err.println("(no entries for " + state + ")");
        }

//        System.err.println(getStateForId(state) + " -> null");
        return null; // if language is empty, return null
    }



    public static double assignRuleWeights(Rule[] rules, float[] weights) {
        CpuTimeStopwatch watch = new CpuTimeStopwatch();
        watch.record();
        for (int i = 0; i<rules.length; i++) {
            rules[i].setWeight(weights[i]);
        }
        watch.record();
        return watch.getMillisecondsBefore(1);
    }

    public static double assignRuleWeights(Rule rule, float[] weights) {
        CpuTimeStopwatch watch = new CpuTimeStopwatch();
        watch.record();
        for (int i = 0; i<weights.length; i++) {
            rule.setWeight(weights[i]);
        }
        watch.record();
        return watch.getMillisecondsBefore(1);
    }

    public static float[] assignRuleWeightsReturnWeights(Rule rule, float[] weights) {
        //CpuTimeStopwatch watch = new CpuTimeStopwatch();
        //watch.record();
        for (int i = 0; i<weights.length; i++) {
            rule.setWeight(weights[i]);
        }
        //watch.record();
        return weights;
    }


    public static void main(String[] args) throws IOException {
        float[] ruleWeights = new float[]{-0.7809f, -0.7924f, -0.7960f, -1.1170f, -1.6062f, -0.5287f,
                -0.5313f, -0.8815f,-0.5370f};
        TreeAutomaton<String> automaton = new BinaryIrtgInputCodec().read(
                new FileInputStream("C:\\Users\\Jonas\\Documents\\Work\\GitHub\\am-parser\\example\\minimalDMautomata\\0.irtb"))
                .getAutomaton();
        float[] outerProbs = computeOuterProbabilities(ruleWeights, automaton.getAllRulesInBottomUpOrder(), automaton.getStateInterner().getNextIndex(), automaton.getFinalStates());
        System.out.println(Arrays.toString(outerProbs));
    }


}
