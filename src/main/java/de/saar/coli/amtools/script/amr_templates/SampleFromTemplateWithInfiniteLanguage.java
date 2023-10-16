package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleFromTemplateWithInfiniteLanguage {

    static int null_pointer_exception_count = 0;
    static final String SIZE_TYPE_STRING_LENGTH = "string length";
    static final String SIZE_TYPE_TREE_DEPTH = "tree depth";
    static final String SIZE_TYPE_DEPTH_BELOW_RESOLVE_COREF = "depth below resolve coref";
    static final String SIZE_TYPE_DEPTH_BELOW_RC = "depth below relative clause";
    private final int minSize;
    private final int maxSize;
    private final int sizeStep;
    private final List<String> sizeTypes;
    private final InterpretedTreeAutomaton irtg;
    private final Interpretation stringInterp;
    private final Interpretation graphInterp;
    private final Map<Integer, Double> inside;
    private final String description;
    private final Set<String> ruleLabelsWithDuplicatesAllowed;
    private final boolean check_for_coordination_ambiguity;
    private final boolean add_hate_like_edge_in_postprocessing;



    public static void main(String[] args) throws IOException, ParseException {
        // we want about 100 samples for most grammars

        // for in-line list creation, use Arrays.asList if multiple objects;
        // use Collections.singletonList if only one object


        int numSamplesPerSize = 10;


        // centre embedding
        SampleFromTemplateWithInfiniteLanguage samplerCentreEmbedding = new SampleFromTemplateWithInfiniteLanguage(
                4, 8, 2,
                Collections.singletonList(SIZE_TYPE_TREE_DEPTH),
                "examples/amr_template_grammars/centre_embedding.irtg",
                "Randomly sampled examples of centre embedding. Created by a grammar. " +
                        "Here, size0 is the depth of the tree. The number of centre embeddings is (size0-2) / 2," +
                        "i.e. the sentences here have 1, 2 or 3 centre embeddings.",
                new HashSet<>(Arrays.asList("obj_rel", "NP_RC")),
                false,
                false
        );
        samplerCentreEmbedding.sampleFromGrammar(numSamplesPerSize, "examples/amr_template_grammars/centre_embedding.txt");

        // adjectives
        // we want all sentence lengths with at least 2 adjectives

//        SampleFromTemplateWithInfiniteLanguage samplerAdjectives = new SampleFromTemplateWithInfiniteLanguage(
//                4, 7, 1,
//                Collections.singletonList(SIZE_TYPE_STRING_LENGTH),
//                "examples/amr_template_grammars/adjectives.irtg",
//                "Randomly sampled examples of stacked adjectives. Created by a grammar. " +
//                        "Here, size0 is the string length. The number of adjectives is size0 - 2.",
//                new HashSet<>(Arrays.asList("obj_rel", "NP_RC")),
//                false,
//                false
//        );
//        samplerAdjectives.sampleFromGrammar(numSamplesPerSize, "examples/amr_template_grammars/adjectives.txt");


        // nested control  -- this is the correct format as of late May 2023
        SampleFromTemplateWithInfiniteLanguage samplerControl = new SampleFromTemplateWithInfiniteLanguage(
                4, 8, 1,
                Collections.singletonList(SIZE_TYPE_TREE_DEPTH),
                "examples/amr_template_grammars/nested_control.irtg",
                "Randomly sampled examples of nested control structures including nesting inside coordination. Created by a grammar." +
                        "Here, size0 is the depth of the tree. The number of recursions is ??",
                new HashSet<>(Arrays.asList("TP_PRO", "VbarSubjCtrl", "VbarObjCtrl")),
                true,
                false
        );
        samplerControl.sampleFromGrammar(10, "examples/amr_template_grammars/nested_control.txt");

        // deep recursion basic
        // The 0-based tree depth is (1-based) number of CPs + 1
        SampleFromTemplateWithInfiniteLanguage sampler = new SampleFromTemplateWithInfiniteLanguage(
                2, 11, 1,
                Collections.singletonList(SIZE_TYPE_TREE_DEPTH),
                "examples/amr_template_grammars/deep_recursion_basic.irtg",
                "Randomly sampled examples of deep CP recursion (standard version). Created by a grammar. " +
                        "Size (size0) here is number of CPs + 1 (i.e., to get the recursion depth, " +
                        "subtract 1 from the value of size0 given in this file).",
                new HashSet<>(Arrays.asList("TP_CP", "thought", "said", "believed", "knew", "heard", "mentioned")),
                false,
                false
        );
        sampler.sampleFromGrammar(10, "examples/amr_template_grammars/deep_recursion_basic.txt");

        // deep recursion pronouns
        // The 0-based tree depth is (1-based) number of CPs + 1
        SampleFromTemplateWithInfiniteLanguage samplerDeepRecursionPronouns = new SampleFromTemplateWithInfiniteLanguage(
                2, 11, 1,
                Arrays.asList(SIZE_TYPE_DEPTH_BELOW_RESOLVE_COREF, SIZE_TYPE_TREE_DEPTH),
                "examples/amr_template_grammars/deep_recursion_pronouns.irtg",
                "Randomly sampled examples of deep CP recursion (with 1st person singular/plural coreference). Created by a grammar. " +
                        "Here, size0 is the depth below the CP with the reentrancy (i.e., to get the recursion depth for" +
                        "the reentrancy part, " +
                        "subtract 1 from the value of size0 given in this file). And size1 is the depth of the whole tree " +
                        "(to get the recursion depth of the tree in total, subtract 1 from size1).",
                new HashSet<>(Arrays.asList("TP_3p", "TP_CP_1s", "TP_CP_1p", "TP_CP_2", "thought", "said", "believed", "knew", "heard", "mentioned",
                        "thought_coref", "said_coref", "believed_coref", "knew_coref", "heard_coref", "mentioned_coref")),
                false,
                false
        );
        samplerDeepRecursionPronouns.sampleFromGrammar(10, "examples/amr_template_grammars/deep_recursion_pronouns.txt");

        // deep recursion 3rd singular
        // The 0-based tree depth is (1-based) number of CPs + 1
//        SampleFromTemplateWithInfiniteLanguage samplerDeepRecursion3s = new SampleFromTemplateWithInfiniteLanguage(
//                3, 11, 1,
//                Arrays.asList(SIZE_TYPE_DEPTH_BELOW_RESOLVE_COREF, SIZE_TYPE_TREE_DEPTH),
//                "examples/amr_template_grammars/deep_recursion_3s.irtg",
//                "Randomly sampled examples of deep CP recursion (with 3rd person singular coreference). Created by a grammar. " +
//                        "Here, size0 is the depth below the CP with the reentrancy (i.e., to get the recursion depth for" +
//                        "the reentrancy part, " +
//                        "subtract 1 from the value of size0 given in this file). And size1 is the depth of the whole tree " +
//                        "(to get the recursion depth of the tree in total, subtract 1 from size1).",
//                new HashSet<>(Arrays.asList("TP_CP", "TP_CP_coref", "make_singular", "thought", "said", "believed", "knew", "heard", "mentioned",
//                        "thought_coref", "said_coref", "believed_coref", "knew_coref", "heard_coref", "mentioned_coref")),
//                false,
//                false
//        );
//        samplerDeepRecursion3s.sampleFromGrammar(10, "examples/amr_template_grammars/deep_recursion_3s.txt");

        // deep recursion with relative clauses
        // The 0-based tree depth is (1-based) number of CPs + 1
        SampleFromTemplateWithInfiniteLanguage samplerDeepRecursionRC = new SampleFromTemplateWithInfiniteLanguage(
                0, 5, 1,
                Arrays.asList(SIZE_TYPE_DEPTH_BELOW_RC, SIZE_TYPE_TREE_DEPTH),
                "examples/amr_template_grammars/deep_recursion_rc.irtg",
                "Randomly sampled examples of deep CP recursion (with relative clauses). Created by a grammar. " +
                        "Here, size0 is the number of recursions within the relative clause minus 1 " +
                        "(i.e., to get the recursion depth for" +
                        "the RC part, " +
                        "add 1 to the value of size0 given in this file). And size1 is the depth of the whole tree " +
                        "(to get the recursion depth of the tree in total, subtract 1 from size1) (Not exactly true, since" +
                        "tree depth depends on the type of lowest DP).",
                new HashSet<>(Arrays.asList("TP_CP", "NP_unary", "DP_the", "DP_the_obj", "CP_with_gap_recursive",
                        "CP_with_gap_base", "said", "thought", "claimed", "assumed")),
                false,
                false
        );
        samplerDeepRecursionRC.sampleFromGrammar(10, "examples/amr_template_grammars/deep_recursion_rc.txt");

        // deep recursion with relative clause and contrastive focus
        // The 0-based tree depth is (1-based) number of CPs + 1
        SampleFromTemplateWithInfiniteLanguage samplerDeepRecursionRCCoref = new SampleFromTemplateWithInfiniteLanguage(
                0, 6, 1,
                Arrays.asList(SIZE_TYPE_DEPTH_BELOW_RC, SIZE_TYPE_TREE_DEPTH),
                "examples/amr_template_grammars/deep_recursion_rc_contrastive_coref.irtg",
                "Randomly sampled examples of deep CP recursion (with relative clauses and contrastive coref). Created by a grammar. " +
                        "Here, size0 is the number of recursions within the relative clause minus 1 " +
                        "(i.e., to get the recursion depth for" +
                        "the RC part, " +
                        "add 1 to the value of size0 given in this file). And size1 is the depth of the whole tree " +
                        "(to get the recursion depth of the tree in total, subtract 1 from size1) (Not exactly true, since" +
                        "tree depth depends on the type of lowest DP).",
                new HashSet<>(Arrays.asList("TP_CP", "NP_unary", "DP_the", "CP_with_gap_recursive",
                        "CP_with_gap_base", "unary_N_adapter", "said", "thought", "claimed", "assumed")),
                false,
                true
        );
        samplerDeepRecursionRCCoref.sampleFromGrammar(10, "examples/amr_template_grammars/deep_recursion_rc_contrastive_coref.txt");
    }

    /**
     * A class that can sample from a grammar and write the samples to a file.
     * @param minSize The minimum sentence length or (grammar-)tree depth of the samples, depending on sizeType
     * @param maxSize The maximum sentence length or (grammar-)tree depth of the samples, depending on sizeType
     * @param sizeStep The step size for increasing the length or depth, depending on sizeType
     * @param sizeTypes whether we're measuring output sentence length or derivation tree depth.
     *                 Determines what "Size" means in the above parameters (length vs depth)
     *                 Choose between the SIZE_TYPE_ prefixed global variables above.
     *                  The sampling is done based on the first entry in this (i.e. minSize, maxSize and sizeStep
     *                  apply to the first entry in this list).
     * @param irtgPath The path to the grammar.
     * @param description A description that is added at the start of the output file. Must be all one line!
     * @param ruleLabelsWithDuplicatesAllowed A set of rule labels for which multiple instances are allowed. This is
     *                                        important for rules that are used recursively. But by default we do
            *                                        not allow duplicate rules, so that every lexical item can appear
     *                                        at most once.
     *
     **/
    public SampleFromTemplateWithInfiniteLanguage(int minSize, int maxSize, int sizeStep, List<String> sizeTypes,
                                                  String irtgPath, String description,
                                                  Set<String> ruleLabelsWithDuplicatesAllowed,
                                                  boolean check_for_coordination_ambiguity,
                                                  boolean add_hate_like_edge_in_postprocessing) throws IOException {
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.sizeStep = sizeStep;
        this.sizeTypes = sizeTypes;
        this.description = description;
        this.ruleLabelsWithDuplicatesAllowed = ruleLabelsWithDuplicatesAllowed;
        this.irtg = InterpretedTreeAutomaton.fromPath(irtgPath);
        this.stringInterp = irtg.getInterpretation("string");
        this.graphInterp = irtg.getInterpretation("graph");
        this.inside = computeAndFixInsideProbabilities();
        this.check_for_coordination_ambiguity = check_for_coordination_ambiguity;
        this.add_hate_like_edge_in_postprocessing = add_hate_like_edge_in_postprocessing;
    }

    /**
     * Samples from a grammar and writes the samples to a file.
     * @param numSamples The number of samples to be generated.
     * @param outputFile The path to the output file.
     */
    @SuppressWarnings({"rawtypes", "SameParameterValue"})
    private void sampleFromGrammar(int numSamples, String outputFile) throws IOException {
        irtg.getAutomaton().normalizeRuleWeights();


        List<Tree<String>> samples = getSamplesAccordingToInsideProbabilities(numSamples);

        writeSamplesToFile(outputFile, samples, description, irtg);
        System.out.println("\nTotal samples: " + samples.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void writeSamplesToFile(String fileName, Iterable<Tree<String>> samples, String description, InterpretedTreeAutomaton irtg) throws IOException {
        FileWriter w = new FileWriter(fileName);
        w.write("# " + description+"\n\n");
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        for (Tree<String> sample : samples) {
            Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(sample));
            Pair<SGraph, ApplyModifyGraphAlgebra.Type> graphResult = (Pair)graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(sample));
            if (add_hate_like_edge_in_postprocessing) {
                addHateLikeEdge(graphResult.left); // adds it in-place, so graphResult itself will be changed.
            }
            String sentenceString = SampleFromTemplate.postprocessString((List<String>)stringResult);
            w.write("# ::snt " + sentenceString+"\n");
            w.write("# ::tree " + sample.toString()+"\n");
            for (int i = 0; i < sizeTypes.size(); i++) {
                w.write("# ::size" + i + " " + computeTreeSize(sample, sizeTypes.get(i)) + "\n");
            }
            String graphString = SampleFromTemplate.fixAMRString((graphResult).left.toIsiAmrString());
            w.write(graphString+"\n\n");
        }
        w.close();
    }

    private static void addHateLikeEdge(SGraph graph) {
        try {
            GraphNode likeNode = graph.getGraph().vertexSet().stream().filter(n -> n.getLabel().startsWith("like")).findFirst().get();
            GraphNode hateNode = graph.getGraph().vertexSet().stream().filter(n -> n.getLabel().startsWith("hate")).findFirst().get();

            boolean likeIsRoot = graph.getSourcesAtNode(likeNode.getName()).contains("root");
            if (likeIsRoot) {
                addARG1EdgeFromFirstNodeToArgumentOfSecondNode(likeNode, hateNode, graph);
            } else {
                addARG1EdgeFromFirstNodeToArgumentOfSecondNode(hateNode, likeNode, graph);
            }
        } catch (NoSuchElementException e) {
            System.err.println("WARNING: Could not add like-hate edge to graph, because either like or hate was not found.");
            System.err.println(graph.toIsiAmrStringWithSources());
        }

    }

    private static void addARG1EdgeFromFirstNodeToArgumentOfSecondNode(GraphNode firstNode, GraphNode secondNode, SGraph graph) {
        GraphNode jointTarget = graph.getGraph().outgoingEdgesOf(secondNode).stream().filter(e -> e.getLabel().equals("ARG1")).findFirst().get().getTarget();
        graph.addEdge(firstNode, jointTarget, "ARG1");
    }

    @SuppressWarnings({"rawtypes"})
    @NotNull
    private List<Tree<String>> getSamplesAccordingToInsideProbabilities(int numSamples) {
        List<Tree<String>> samples = new ArrayList<>();
        for (int targetSize = minSize; targetSize <= maxSize; targetSize += sizeStep) {
            sampleForTargetSize(numSamples, samples, targetSize);
        }
        return samples;
    }

    private void sampleForTargetSize(int numSamples, List<Tree<String>> samples, int targetSize) {
        System.out.println("\nSamples for target size " + targetSize + ":");
        null_pointer_exception_count = 0;
        List<Tree<String>> samplesHere = new ArrayList<>();
        int backupCounter = 0;
        int sampleFactor = 1000000;
        while (samplesHere.size() < numSamples && backupCounter < numSamples * sampleFactor) {
            attemptToAddNewSample(targetSize, samplesHere);
            backupCounter++;
        }
        samples.addAll(samplesHere);
        System.out.println("Samples obtained: " + samplesHere.size());
        System.out.println("Attempts: " + backupCounter);
        System.out.println("Null pointer exceptions: " + null_pointer_exception_count);
    }

    private void attemptToAddNewSample(int targetSize, List<Tree<String>> samplesHere) {
        Tree<String> tree = irtg.getAutomaton().getRandomTree(inside);
        if (checkTree(tree, samplesHere, targetSize)) {
            addSample(samplesHere, tree);
        }
    }

    @SuppressWarnings({"unchecked"})
    private void addSample(List<Tree<String>> samplesHere, Tree<String> tree) {
        samplesHere.add(tree);
        System.out.println(tree);
        Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(tree));
        System.out.println("AM algebra term");
        System.out.println(graphInterp.getHomomorphism().apply(tree));
        Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(tree));
        String sentenceString = stringInterp.getAlgebra().representAsString(stringResult);
        System.out.println(sentenceString);
        String graphString = SampleFromTemplate.fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>) graphResult).left.toIsiAmrString());
        System.out.println(graphString);
    }

    @NotNull
    private Map<Integer, Double> computeAndFixInsideProbabilities() {
        Map<Integer, Double> inside = this.irtg.getAutomaton().inside();
        // if the grammar contains loops, the inside calculation will not always be correct
        // We therefore override the inside probablities.
//        printAllInsideProbabilities(this.irtg, inside);  // so we can catch wrongly computed inside probabilities
        fixErroneousInsideProbabilities(inside);
        return inside;
    }

    private void printAllInsideProbabilities(Map<Integer, Double> inside) {
        System.out.println("Inside probabilities:");
        for (int state_id : inside.keySet()) {
            System.out.println(irtg.getAutomaton().getStateForId(state_id) + " " + inside.get(state_id));
        }
    }

    /**
     * Set all inside probabilities to 1 to prevent problems with infinite languages.
     * We still need to keep recursive, tree-widening rule probabilities lower than unary and constants. But we do
     * this in the grammar! Then we normalize the rule weights in the code to make the grammar compatible with the
     * inside probabilities all being 1. So in the grammar, the rule weights that expand a state don't have to
     * sum to 1, but relative to each other, they have to make sure that the expansions don't outweight the
     * contractions.
     * Let's assume for simplicity that a cycle only occurs directly within rules that expand one state and then have
     * that state again as a child (if there are longer cycles between same states in the grammar, the math gets more
     * complicated). So let X be a state, and r_1, ..., r_n be the rules that expand X. Let w(r_i) be the weight of
     * rule r_i and let NX(r_i) be the number of times that X occurs as a child of r_i. I.e. if NX(r_i) is 0, then
     * we have a contraction, and if NX(r_i) > 1, we have an expansion. Then to avoid running into infinite loops
     * during sampling, we need SUM_i(w(r_i) * NX(r_i)) < SUM_i(w(r_i)). (Note that after normalization, the right
     * side would equal 1).
     * @param inside: inside probabilities to update
     */
    private static void fixErroneousInsideProbabilities(Map<Integer, Double> inside) {
        for (Integer i : new HashSet<>(inside.keySet())) {
            inside.put(i, 1.0);
        }
    }


    static int total_depths_printed = 0;
    public boolean checkTree(Tree<String> tree, Collection<Tree<String>> samples, int targetSize) {
        if (samples.contains(tree)) { return false; }
        // This is catching a bug where sometimes the tree.getHeight method (and other tree methods) throws
        // a null pointer exception. This seems to be an alto bug, and we're just ignoring it here.
        try {
            // System.out.println(tree);
            // use first size type to filter for size.
            int size = computeTreeSize(tree, sizeTypes.get(0));
            if (size != targetSize) {
                return false;
            }
        } catch (NullPointerException ex) {
            null_pointer_exception_count++;
            return false;
        }

        if (check_for_coordination_ambiguity) {
            // it's not the prettiest thing that we hardcode this here, but for the purpose this script serves,
            // it seems like the most efficient solution. No need to overgeneralize the code.

            // don’t allow any VbarSubjCtrl/VbarObjCtrl in ancestors of and_inf, and allow only one VP in left
            // child of and_inf (same for middle one if and_inf_3).
            // The and_inf rule labels are:
            // Coord_Open_S_fin
            // Coord_3_Open_S_fin
            // Coord_Subj_Ctrl_V
            // Coord_3_Subj_Ctrl_V

            //VbarSubjCtrl(Coord_Subj_Ctrl_V
            //VbarSubjCtrl(Coord_Subj_Ctrl_V
            Set<String> andInfRuleLabels = new HashSet<>();
            andInfRuleLabels.add("Coord_Open_S_inf");
            andInfRuleLabels.add("Coord_3_Open_S_inf");
            andInfRuleLabels.add("Coord_Subj_Ctrl_V");
            andInfRuleLabels.add("Coord_3_Subj_Ctrl_V");

            Set<String> forbiddenRuleLabels = new HashSet<>();
            forbiddenRuleLabels.add("VbarSubjCtrl");
            forbiddenRuleLabels.add("VbarObjCtrl");

            int countAbove = countAncestorDescendantPairsInTree(tree, forbiddenRuleLabels, andInfRuleLabels,
                    false);
            int countBelow = countAncestorDescendantPairsInTree(tree, andInfRuleLabels, forbiddenRuleLabels,
                    true);
            if (countAbove > 0 || countBelow > 0) {
                return false;
            }

            // also only allow one "and" in the whole sentence.
            long andCount = tree.getAllNodes().stream().map(Tree::getLabel).
                    filter(label -> label.startsWith("Coord")).count();
            if (andCount > 1) {
                return false;
            }
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

    @SuppressWarnings("unchecked")
    private int computeTreeSize(Tree<String> tree, String sizeType) {
        switch (sizeType) {
            case SIZE_TYPE_STRING_LENGTH:
                Object stringResult = stringInterp.interpret(tree);
                return ((List<String>) stringResult).size();
            case SIZE_TYPE_TREE_DEPTH:
                return tree.getHeight();
            case SIZE_TYPE_DEPTH_BELOW_RESOLVE_COREF:
                OptionalInt optionalInt = tree.getAllNodes().stream().filter(t -> t.getLabel().startsWith("TP_resolve_coref")).
                        mapToInt(Tree::getHeight).min();  // doesn't actually matter which one, there should be only one.

                if (optionalInt.isPresent()) {
                    return optionalInt.getAsInt();
                } else {
                    return Integer.MAX_VALUE;
                }
            case SIZE_TYPE_DEPTH_BELOW_RC:
                long recursion_count = tree.getAllNodes().stream().
                        filter(t -> t.getLabel().equals("CP_with_gap_recursive")).count();

                return (int)recursion_count;
            default:
                throw new RuntimeException("Unknown size type: " + sizeType);
        }
    }

    public static int countAncestorDescendantPairsInTree(Tree<String> tree, Set<String> ancestorLabels,
                                                          Set<String> descendantLabels,
                                                          boolean ignoreRightmostBranch) {
        int count = 0;
        for (Tree<String> subtree :  tree.getAllNodes()) {
            if (ancestorLabels.contains(subtree.getLabel())) {
                List<Tree<String>> branchesToConsider = subtree.getChildren();
                if (ignoreRightmostBranch) {
                    branchesToConsider = branchesToConsider.subList(0, branchesToConsider.size() - 1);
                }
                for (Tree<String> branch : branchesToConsider) {
                    for (Tree<String> nodeInBranch : branch.getAllNodes()) {
                        if (descendantLabels.contains(nodeInBranch.getLabel())) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

}
