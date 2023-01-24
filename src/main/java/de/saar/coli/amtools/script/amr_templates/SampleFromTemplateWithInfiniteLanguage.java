package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleFromTemplateWithInfiniteLanguage {

    static int null_pointer_exception_count = 0;
    static final String SIZE_TYPE_STRING_LENGTH = "string length";
    static final String SIZE_TYPE_TREE_DEPTH = "tree depth";

    public static void main(String[] args) throws IOException, ParseException {
        // we want about 100 samples for most grammars

        // for in-line list creation, use Arrays.asList if multiple objects;
        // use Collections.singletonList if only one object


        // centre embedding
//        sampleFromGrammar(25, 4, 8, 2,
//                "examples/amr_template_grammars/centre_embedding.irtg",
//                "examples/amr_template_grammars/centre_embedding.txt",
//                "Randomly sampled examples of centre embedding. Created by a grammar.",
//                new HashSet<>(Arrays.asList("obj_rel", "NP_RC")),
//                Collections.singletonList(new Pair<>("ObjRC", 0.5)));

        // adjectives
        // we want all sentence lengths with at least 2 adjectives, but all trees are of depth 7 since we use syntactic glue
//        sampleFromGrammar(25, 4, 7, 1,
//                SIZE_TYPE_STRING_LENGTH,
//                "examples/amr_template_grammars/adjectives.irtg",
//                "examples/amr_template_grammars/adjectives.txt",
//                "Randomly sampled examples of stacked adjectives. Created by a grammar.",
//                new HashSet<>(),
//                new ArrayList<>());
//
//        // adjectives: sanity check with just one adjective
//        sampleFromGrammar(10, 3, 3, 1,
//                SIZE_TYPE_STRING_LENGTH,
//                "examples/amr_template_grammars/adjectives.irtg",
//                "examples/amr_template_grammars/adjectives_sanity_check.txt",
//                "Randomly sampled examples of stacked adjectives. Created by a grammar.",
//                new HashSet<>(),
//                new ArrayList<>());

        // nested control
        sampleFromGrammar(10, 12, 14, 1,
                SIZE_TYPE_TREE_DEPTH,
                "examples/amr_template_grammars/nested_control.irtg",
                "examples/amr_template_grammars/nested_control_more.txt",
                "Randomly sampled examples of nested control structures including nesting inside coordination. Created by a grammar.",
                new HashSet<>(Arrays.asList("TP_PRO", "VbarSubjCtrl", "VbarObjCtrl"))
        );
                // also if you get infinite loops with no output from grammar. Can change here or in grammar.
    }

    /**
     * Samples from a grammar and writes the samples to a file.
     * @param numSamples The number of samples to be generated.
     * @param minSize The minimum sentence length or (grammar-)tree depth of the samples, depending on sizeType
     * @param maxSize The maximum sentence length or (grammar-)tree depth of the samples, depending on sizeType
     * @param sizeStep The step size for increasing the length or depth, depending on sizeType
     * @param sizeType whether we're measuring output sentence length or derivation tree depth.
     *                 Determines what "Size" means in the above parameters (length vs depth)
     *                 Choose between the SIZE_TYPE_ prefixed global variables above
     * @param irtgPath The path to the grammar.
     * @param outputFile The path to the output file.
     * @param description A description that is added at the start of the output file.
     * @param ruleLabelsWithDuplicatesAllowed A set of rule labels for which multiple instances are allowed. This is
     *                                        important for rules that are used recursively. But by default we do
     *                                        not allow duplicate rules, so that every lexical item can appear
     *                                        at most once.
     */
    @SuppressWarnings({"rawtypes"})
    private static void sampleFromGrammar(int numSamples, int minSize, int maxSize, int sizeStep, String sizeType,
                                          String irtgPath,
                                          String outputFile, String description,
                                          Set<String> ruleLabelsWithDuplicatesAllowed
                                          ) throws IOException {
        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath(irtgPath);
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        irtg.getAutomaton().normalizeRuleWeights();

        Map<Integer, Double> inside = computeAndFixInsideProbabilities(irtg);

        List<Tree<String>> samples = getSamplesAccordingToInsideProbabilities(numSamples, minSize, maxSize, sizeStep,
                sizeType, ruleLabelsWithDuplicatesAllowed, irtg, stringInterp, graphInterp, inside);

        SampleFromTemplate.writeSamplesToFile(outputFile, samples, description, irtg);
        System.out.println("\nTotal samples: " + samples.size());
    }

    @SuppressWarnings({"rawtypes"})
    @NotNull
    private static List<Tree<String>> getSamplesAccordingToInsideProbabilities(int numSamples, int minSize,
                                                                               int maxSize, int sizeStep,
                                                                               String sizeType,
                                                                               Set<String> ruleLabelsWithDuplicatesAllowed,
                                                                               InterpretedTreeAutomaton irtg,
                                                                               Interpretation stringInterp,
                                                                               Interpretation graphInterp,
                                                                               Map<Integer, Double> inside) {
        List<Tree<String>> samples = new ArrayList<>();
        for (int targetSize = minSize; targetSize <= maxSize; targetSize += sizeStep) {
            sampleForTargetSize(numSamples, ruleLabelsWithDuplicatesAllowed, irtg, stringInterp, graphInterp, inside,
                    samples, targetSize, sizeType);
        }
        return samples;
    }

    @SuppressWarnings({"rawtypes"})
    private static void sampleForTargetSize(int numSamples, Set<String> ruleLabelsWithDuplicatesAllowed,
                                            InterpretedTreeAutomaton irtg, Interpretation stringInterp,
                                            Interpretation graphInterp, Map<Integer, Double> inside,
                                            List<Tree<String>> samples, int targetSize, String sizeType) {
        System.out.println("\nSamples for target size " + targetSize + ":");
        null_pointer_exception_count = 0;
        List<Tree<String>> samplesHere = new ArrayList<>();
        int backupCounter = 0;
        int sampleFactor = 1000000;
        while (samplesHere.size() < numSamples && backupCounter < numSamples * sampleFactor) {
            attemptToAddNewSample(ruleLabelsWithDuplicatesAllowed, irtg, stringInterp, graphInterp, inside, targetSize,
                    sizeType,
                    samplesHere);
            backupCounter++;
        }
        samples.addAll(samplesHere);
        System.out.println("Samples obtained: " + samplesHere.size());
        System.out.println("Attempts: " + backupCounter);
        System.out.println("Null pointer exceptions: " + null_pointer_exception_count);
    }

    @SuppressWarnings("rawtypes")
    private static void attemptToAddNewSample(Set<String> ruleLabelsWithDuplicatesAllowed,
                                              InterpretedTreeAutomaton irtg,
                                              Interpretation stringInterpretation,
                                              Interpretation graphInterpretation,
                                              Map<Integer, Double> inside,
                                              int targetSize,
                                              String sizeType,
                                              List<Tree<String>> samplesHere) {
        Tree<String> tree = irtg.getAutomaton().getRandomTree(inside);
        if (checkTree(tree, samplesHere, targetSize,
                sizeType,
                stringInterpretation,
                ruleLabelsWithDuplicatesAllowed)) {
            addSample(stringInterpretation, graphInterpretation, samplesHere, tree);
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
    private static Map<Integer, Double> computeAndFixInsideProbabilities(InterpretedTreeAutomaton irtg) {
        Map<Integer, Double> inside = irtg.getAutomaton().inside();
        // if the grammar contains loops, the inside calculation will not always be correct
        // we can override erroneous inside values with a sort of made up version
        // (doesn't have to be exact; inside may in fact be undefined)
        printAllInsideProbabilities(irtg, inside);  // so we can catch wrongly computed inside probabilities
        fixErroneousInsideProbabilities(inside);
        return inside;
    }

    private static void printAllInsideProbabilities(InterpretedTreeAutomaton irtg, Map<Integer, Double> inside) {
        System.out.println("Inside probabilities:");
        for (int state_id : inside.keySet()) {
            System.out.println(irtg.getAutomaton().getStateForId(state_id) + " " + inside.get(state_id));
        }
    }

    /**
     * set all inside probabilities to 1 to prevent problems with infinite languages
     * still need to keep recursive, tree-widening rule probabilities lower than unary and constants in the grammar
     * @param inside: inside probabilities to update
     */
    private static void fixErroneousInsideProbabilities(
                                                        Map<Integer, Double> inside) {
        for (Integer i : new HashSet<>(inside.keySet())) {
            inside.put(i, 1.0);
        }
    }


    static int total_depths_printed = 0;
    public static boolean checkTree(Tree<String> tree, Collection<Tree<String>> samples, int targetSize,
                                    String sizeType,
                                    Interpretation stringInterpretation,
                                    Set<String> ruleLabelsWithDuplicatesAllowed) {
        if (samples.contains(tree)) { return false; }
        // This is catching a bug where sometimes the tree.getHeight method (and other tree methods) throws
        // a null pointer exception. This seems to be an alto bug, and we're just ignoring it here.
        try {
            // System.out.println(tree);
            if (sizeType.equals(SIZE_TYPE_STRING_LENGTH)) {
                Object stringResult = stringInterpretation.interpret(tree);
                int sentenceLength = ((List<String>) stringResult).size();
                if (sentenceLength != targetSize) {
                    return false;
                }
            } else if (sizeType.equals(SIZE_TYPE_TREE_DEPTH)) {
                int depth = tree.getHeight();
//                        if (total_depths_printed < 100) {
//                            System.err.println("Depth: " + depth);
//                            total_depths_printed++;
//                        }
                if (depth != targetSize) {
                    return false;
                }
            }
        } catch (NullPointerException ex) {
            null_pointer_exception_count++;
            return false;
        }

        List<String> allLabels = tree.getAllNodes().stream().map(Tree::getLabel).collect(Collectors.toList());
        boolean hasDuplicates = allLabels.stream().anyMatch(label -> {
            boolean frequency = Collections.frequency(allLabels, label) > 1;
            boolean allowed = ruleLabelsWithDuplicatesAllowed.contains(label);
            return frequency && !allowed;
        });

        // return true if we want to keep this one
        return !hasDuplicates;
    }

}
