package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleSanityChecksFromTemplatesWithInfiniteLanguage {

    private final InterpretedTreeAutomaton irtg;
    private final Interpretation stringInterp;
    private final Interpretation graphInterp;
    private final String description;
    private final Set<String> forbiddenRuleLabels;
    private final boolean add_hate_like_edge_in_postprocessing;



    public static void main(String[] args) throws IOException, ParseException {

        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerDeepRecursion = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/deep_recursion_basic.irtg",
                "Systematically sampled sanity checks for the deep CP recursion (standard version) dataset." +
                        " Created by the grammar deep_recursion_basic.irtg. ",
                Collections.EMPTY_SET, // also want to have the CP verbs in, so one recursion is fine
                false
        );
        samplerDeepRecursion.sampleFromGrammar("examples/amr_template_grammars/deep_recursion_basic_sanity_check.txt");


        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerDeepRecursion3s = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/deep_recursion_3s.irtg",
                "Systematically sampled sanity checks for the deep CP recursion (with 3s pronoun + noun coreference) dataset." +
                        " Created by the grammar deep_recursion_3s.irtg. ",
                new HashSet<>(Arrays.asList("TP_CP", "TP_CP_coref")), // this grammar always has a CP, so we don't want the recursive rule at all.
                false
        );
        samplerDeepRecursion3s.sampleFromGrammar("examples/amr_template_grammars/deep_recursion_3s_sanity_check.txt");


        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerDeepRecursionPronouns = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/deep_recursion_pronouns.irtg",
                "Systematically sampled sanity checks for the deep CP recursion (with 1st/second person singular/plural pronoun coreference) dataset." +
                        " Created by the grammar deep_recursion_pronouns.irtg. ",
                new HashSet<>(Arrays.asList("TP_3p", "TP_1s", "TP_1p", "TP_2",
                        "TP_CP_1s", "TP_CP_1s_subj1p", "TP_CP_1s_subj2",
                        "TP_CP_1p", "TP_CP_1p_subj1s", "TP_CP_1p_subj2",
                        "TP_CP_2", "TP_CP_2_subj1s", "TP_CP_2_subj1p")), // this grammar always has a CP, so we don't want the recursive rules at all.
                false
        );
        samplerDeepRecursionPronouns.sampleFromGrammar("examples/amr_template_grammars/deep_recursion_pronouns_sanity_check.txt");


        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerDeepRecursionRC = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/deep_recursion_rc.irtg",
                "Systematically sampled sanity checks for the deep CP recursion (with relative clauses) dataset." +
                        " Created by the grammar deep_recursion_rc.irtg. ",
                new HashSet<>(Arrays.asList("TP_CP", "CP_with_gap_recursive")), // this grammar always has a CP, so we don't want the recursive rules at all.
                false
        );
        samplerDeepRecursionRC.sampleFromGrammar("examples/amr_template_grammars/deep_recursion_rc_sanity_check.txt");

        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerDeepRecursionRCCoref = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/deep_recursion_rc_contrastive_coref.irtg",
                "Systematically sampled sanity checks for the deep CP recursion (with relative clauses and contrastive coref) dataset." +
                        " Created by the grammar deep_recursion_rc_contrastive_coref.irtg. ",
                Collections.singleton("CP_with_gap_recursive"), // this grammar always has a CP, so we don't want the recursive rule at all.
                true
        );
        samplerDeepRecursionRCCoref.sampleFromGrammar("examples/amr_template_grammars/deep_recursion_rc_contrastive_coref_sanity_check.txt");
    }

    /**
     * A class that can sample from a grammar and write the samples to a file.
     * @param forbiddenRuleLabels A set of rule labels for which multiple instances are allowed. This is
     *                                        important for rules that are used recursively. But by default we do
     *                                        not allow duplicate rules, so that every lexical item can appear
     *                                        at most once.
     *
     **/
    public SampleSanityChecksFromTemplatesWithInfiniteLanguage(String irtgPath,
                                                               String description,
                                                               Set<String> forbiddenRuleLabels,
                                                               boolean add_hate_like_edge_in_postprocessing) throws IOException {
        this.irtg = InterpretedTreeAutomaton.fromPath(irtgPath);
        this.stringInterp = irtg.getInterpretation("string");
        this.graphInterp = irtg.getInterpretation("graph");
        this.description = description;
        this.forbiddenRuleLabels = forbiddenRuleLabels;
        this.add_hate_like_edge_in_postprocessing = add_hate_like_edge_in_postprocessing;
    }

    /**
     * Samples from a grammar and writes the samples to a file.
     * @param outputFile The path to the output file.
     */
    @SuppressWarnings({"rawtypes", "SameParameterValue"})
    private void sampleFromGrammar(String outputFile) throws IOException {

        List<Tree<String>> samples = getSamples();

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
    private List<Tree<String>> getSamples() {
        List<Tree<String>> samples = new ArrayList<>();
        for (int finalState : irtg.getAutomaton().getFinalStates()) {
            Map<Integer, List<Rule>> parentState2Rules = new HashMap<>();
            Map<Integer, Integer> parentState2CurrentRotationIndex = new HashMap<>();
            Map<Integer, Boolean> parentState2Done = new HashMap<>();
            // intitialize maps
            for (Rule rule : irtg.getAutomaton().getRuleSet()) {
                int parentState = rule.getParent();
                if (!parentState2Rules.containsKey(parentState)) {
                    parentState2Rules.put(parentState, new ArrayList<>());
                    parentState2CurrentRotationIndex.put(parentState, 0);
                    parentState2Done.put(parentState, false);
                }
                if (!forbiddenRuleLabels.contains(rule.getLabel(irtg.getAutomaton()))) {
                    parentState2Rules.get(parentState).add(rule);
                }
            }
            System.out.println(parentState2Rules);
            for (int parentState : parentState2Rules.keySet()) {
                if (parentState2Rules.get(parentState).isEmpty()) {
                    parentState2Done.put(parentState, true);
                }
            }
            // mark unreachable states as done
            boolean doAnotherRun = true;
            while (doAnotherRun) {
                doAnotherRun = false;
                for (int stateID : parentState2Rules.keySet()) {
                    if (parentState2Done.get(stateID) || irtg.getAutomaton().getFinalStates().contains(stateID)) {
                        continue;
                    }
                    boolean stateIsReachable = false;
                    for (int parentState : parentState2Rules.keySet()) {
                        if (!parentState2Done.get(parentState)) {
                            for (Rule parentRule : parentState2Rules.get(parentState)) {
                                if (Arrays.stream(parentRule.getChildren()).anyMatch(c -> c == stateID)) {
                                    stateIsReachable = true;
                                }
                            }
                        }
                    }
                    if (!stateIsReachable) {
                        System.out.println("state is not reachable "+ irtg.getAutomaton().getStateForId(stateID));
                        parentState2Done.put(stateID, true);
                        doAnotherRun = true;
                    }
                }
            }



            while (!parentState2Done.values().stream().allMatch(v -> v)) {
                // get next sample
                addSample(samples, getNextSampleBelowState(finalState,
                        parentState2Rules,
                        parentState2CurrentRotationIndex,
                        parentState2Done));
            }
        }
        return samples;
    }

    private Tree<String> getNextSampleBelowState(int state, Map<Integer, List<Rule>> parentState2Rules,
        Map<Integer, Integer> parentState2CurrentRotationIndex,
        Map<Integer, Boolean> parentState2Done) {
        int currentRotationIndex = parentState2CurrentRotationIndex.get(state);
        Rule nextRule = parentState2Rules.get(state).get(currentRotationIndex);
        boolean haveCycledThroughAllRulesForState = currentRotationIndex + 1 == parentState2Rules.get(state).size();
        if (haveCycledThroughAllRulesForState) {
            parentState2Done.put(state, true);
            parentState2CurrentRotationIndex.put(state, 0);
        } else {
            parentState2CurrentRotationIndex.put(state, currentRotationIndex + 1);
        }
        List<Tree<String>> treesBelow = new ArrayList<>();
        for (int childStateID : nextRule.getChildren()) {
            treesBelow.add(getNextSampleBelowState(childStateID, parentState2Rules, parentState2CurrentRotationIndex, parentState2Done));
        }
        return Tree.create(nextRule.getLabel(irtg.getAutomaton()), treesBelow);
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




}
