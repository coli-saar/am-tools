package de.saar.coli.amtools.decomposition;

import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.BinaryIrtgInputCodec;
import de.up.ling.irtg.semiring.LogDoubleArithmeticSemiring;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class PyjniusHelper {

    static LogDoubleArithmeticSemiring logDoubleArithmeticSemiring = new LogDoubleArithmeticSemiring();

    public static float[] computeOuterProbabilities(float[] ruleWeights, Iterable<Rule> ruleIterable,
                                                    TreeAutomaton<String> automaton) {
        int i = 0;
        for (Rule rule : ruleIterable) {
            rule.setWeight(Math.exp(ruleWeights[i]));
            i++;
        }
        Int2ObjectMap<Double> logInsides = automaton.logInside();
        Map<Integer, Double> logOutsides = automaton.logOutside(logInsides);
        double totalInside = Double.NaN;
        for (int finalState : automaton.getFinalStates()) {
            if (Double.isNaN(totalInside)) {
                totalInside = logInsides.get(finalState);
            } else {
                totalInside = logDoubleArithmeticSemiring.add(totalInside, logInsides.get(finalState));
            }
        }
        float[] ret = new float[ruleWeights.length]; // if ruleWeights has more weights than rules, then ret is 0 there
        // this is desired when using "fake rules" in python
        i = 0;
        for (Rule rule : ruleIterable) {
            float outerWeight = logOutsides.get(rule.getParent()).floatValue();
            for (int child : rule.getChildren()) {
                outerWeight += logInsides.get(child);
            }
            outerWeight -= totalInside; // for normalization across automata
            ret[i]=outerWeight;
            i++;
        }
        return ret;
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
        float[] outerProbs = computeOuterProbabilities(ruleWeights, automaton.getRuleSet(), automaton);
        System.out.println(Arrays.toString(outerProbs));
    }


}
