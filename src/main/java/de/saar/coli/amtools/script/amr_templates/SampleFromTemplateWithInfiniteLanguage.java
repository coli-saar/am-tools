package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleFromTemplateWithInfiniteLanguage {

    static int null_pointer_exception_count = 0;

    public static void main(String[] args) throws IOException, ParseException {

        int numSamples = 25;
        int minDepth = 4;
        int maxDepth = 8;

        int depthStep = 2;

        String irtgPath = "examples/amr_template_grammars/centre_embedding.irtg";
        String outputFile = "examples/amr_template_grammars/centre_embedding.txt";
        String description = "Randomly sampled examples of centre embedding. Created by a grammar.";

        Set<String> ruleLabelsWithDuplicatesAllowed = new HashSet<>(Arrays.asList("obj_rel", "NP_RC"));
        List<Pair<String, Double>> insideOverrides = new ArrayList<>();
        insideOverrides.add(new Pair<>("ObjRC", 0.5));

        sampleFromGrammar(numSamples, minDepth, maxDepth, depthStep, irtgPath, outputFile, description,
                ruleLabelsWithDuplicatesAllowed, insideOverrides);
    }

    /**
     * Samples from a grammar and writes the samples to a file.
     * @param numSamples The number of samples to be generated.
     * @param minDepth The minimum (grammar-)tree depth of the samples.
     * @param maxDepth The maximum (grammar-)tree depth of the samples.
     * @param depthStep The step size for increasing the depth.
     * @param irtgPath The path to the grammar.
     * @param outputFile The path to the output file.
     * @param description A description that is added at the start of the output file.
     * @param ruleLabelsWithDuplicatesAllowed A set of rule labels for which multiple instances are allowed. This is
     *                                        important for rules that are used recursively. But by default we do
     *                                        not allow duplicate rules, so that every lexical item can appear
     *                                        at most once.
     * @param insideOverrides A list of pairs of (state, insideProbability) to override erroneous computations of
     *                        inside probabilities for those states. For each such pair, the computed inside probability
     *                        for the state will be replaced by the given insideProbability.
     */
    @SuppressWarnings({"rawtypes"})
    private static void sampleFromGrammar(int numSamples, int minDepth, int maxDepth, int depthStep, String irtgPath,
                                          String outputFile, String description,
                                          Set<String> ruleLabelsWithDuplicatesAllowed,
                                          List<Pair<String, Double>> insideOverrides) throws IOException {
        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath(irtgPath);
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");

        Map<Integer, Double> inside = computeAndFixInsideProbabilities(insideOverrides, irtg);

        List<Tree<String>> samples = getSamplesAccordingToInsideProbabilities(numSamples, minDepth, maxDepth, depthStep,
                ruleLabelsWithDuplicatesAllowed, irtg, stringInterp, graphInterp, inside);

        SampleFromTemplate.writeSamplesToFile(outputFile, samples, description, irtg);
    }

    @SuppressWarnings({"rawtypes"})
    @NotNull
    private static List<Tree<String>> getSamplesAccordingToInsideProbabilities(int numSamples, int minDepth,
                                                                               int maxDepth, int depthStep,
                                                                               Set<String> ruleLabelsWithDuplicatesAllowed,
                                                                               InterpretedTreeAutomaton irtg,
                                                                               Interpretation stringInterp,
                                                                               Interpretation graphInterp,
                                                                               Map<Integer, Double> inside) {
        List<Tree<String>> samples = new ArrayList<>();
        for (int targetDepth = minDepth; targetDepth <= maxDepth; targetDepth += depthStep) {
            sampleForTargetDepth(numSamples, ruleLabelsWithDuplicatesAllowed, irtg, stringInterp, graphInterp, inside,
                    samples, targetDepth);
        }
        return samples;
    }

    @SuppressWarnings({"rawtypes"})
    private static void sampleForTargetDepth(int numSamples, Set<String> ruleLabelsWithDuplicatesAllowed,
                                              InterpretedTreeAutomaton irtg, Interpretation stringInterp,
                                              Interpretation graphInterp, Map<Integer, Double> inside,
                                              List<Tree<String>> samples, int targetDepth) {
        System.out.println("\nSamples for target depth " + targetDepth + ":");
        null_pointer_exception_count = 0;
        List<Tree<String>> samplesHere = new ArrayList<>();
        int backupCounter = 0;
        while (samplesHere.size() < numSamples && backupCounter < numSamples * 1000) {
            attemptToAddNewSample(ruleLabelsWithDuplicatesAllowed, irtg, stringInterp, graphInterp, inside, targetDepth, samplesHere);
            backupCounter++;
        }
        samples.addAll(samplesHere);
        System.out.println("Samples obtained: " + samplesHere.size());
        System.out.println("Attempts: " + backupCounter);
        System.out.println("Null pointer exceptions: " + null_pointer_exception_count);
    }

    private static void attemptToAddNewSample(Set<String> ruleLabelsWithDuplicatesAllowed, InterpretedTreeAutomaton irtg, Interpretation stringInterp, Interpretation graphInterp, Map<Integer, Double> inside, int targetDepth, List<Tree<String>> samplesHere) {
        Tree<String> tree = irtg.getAutomaton().getRandomTree(inside);
        if (checkTree(tree, samplesHere, targetDepth, ruleLabelsWithDuplicatesAllowed)) {
            addSample(stringInterp, graphInterp, samplesHere, tree);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addSample(Interpretation stringInterp, Interpretation graphInterp, List<Tree<String>> samplesHere, Tree<String> tree) {
        samplesHere.add(tree);
        System.out.println(tree);
        Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(tree));
        Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(tree));
        String sentenceString = stringInterp.getAlgebra().representAsString(stringResult);
        System.out.println(sentenceString);
        String graphString = SampleFromTemplate.fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>) graphResult).left.toIsiAmrString());
        System.out.println(graphString);
    }

    @NotNull
    private static Map<Integer, Double> computeAndFixInsideProbabilities(List<Pair<String, Double>> insideOverrides, InterpretedTreeAutomaton irtg) {
        Map<Integer, Double> inside = irtg.getAutomaton().inside();
        // if the grammar contains loops, the inside calculation will not always be correct
        // we can override erroneous inside values with a sort of made up version
        // (doesn't have to be exact; inside may in fact be undefined)
        printAllInsideProbabilities(irtg, inside);  // so we can catch wrongly computed inside probabilities
        fixErroneousInsideProbabilities(insideOverrides, irtg, inside);
        return inside;
    }

    private static void printAllInsideProbabilities(InterpretedTreeAutomaton irtg, Map<Integer, Double> inside) {
        System.out.println("Inside probabilities:");
        for (int state_id : inside.keySet()) {
            System.out.println(irtg.getAutomaton().getStateForId(state_id) + " " + inside.get(state_id));
        }
    }

    private static void fixErroneousInsideProbabilities(List<Pair<String, Double>> insideOverrides,
                                                        InterpretedTreeAutomaton irtg, Map<Integer, Double> inside) {
        for (Pair<String, Double> override : insideOverrides) {
            inside.put(irtg.getAutomaton().getIdForState(override.getLeft()), override.getRight());
        }
    }


    static int total_depths_printed = 0;
    public static boolean checkTree(Tree<String> tree, Collection<Tree<String>> samples, int targetDepth,
                                    Set<String> ruleLabelsWithDuplicatesAllowed) {
        if (samples.contains(tree)) { return false; }

        // This is catching a bug where sometimes the tree.getHeight method (and other tree methods) throws
        // a null pointer exception. This seems to be an alto bug, and we're just ignoring it here.
        try {
            int depth = tree.getHeight();
            //        if (total_depths_printed < 100) {
            //            System.err.println("Depth: " + depth);
            //            total_depths_printed++;
            //        }
            if (depth != targetDepth) {
                return false;
            }
        } catch (NullPointerException ex) {
            null_pointer_exception_count++;
            return false;
        }

//        return true;

        List<String> allLabels = tree.getAllNodes().stream().map(Tree::getLabel).collect(Collectors.toList());
        boolean hasDuplicates = allLabels.stream().anyMatch(label -> {
            boolean frequency = Collections.frequency(allLabels, label) > 1;
            boolean allowed = ruleLabelsWithDuplicatesAllowed.contains(label);
            return frequency && !allowed;
        });
        return !hasDuplicates;
    }

}
