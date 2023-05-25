package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeParser;
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

    private final static List<Tree<String>> NESTED_CONTROL_SAMPLES;
    private final static List<Tree<String>> ADJECTIVES_SAMPLES;
    private final static List<Tree<String>> CENTRE_EMBEDDING_SAMPLES;

    static {
        try {
            NESTED_CONTROL_SAMPLES = Arrays.asList(
                    TreeParser.parse("Sent(SubjCtrlTbar(attempted, sleep), girl)"),
                    TreeParser.parse("Sent(SubjCtrlTbar(wanted, jump), boy)"),
                    TreeParser.parse("Sent(SubjCtrlTbar(hated, attend), girl)"),
                    TreeParser.parse("Sent(SubjCtrlTbar(loved, eat), boy)"),
                    TreeParser.parse("Sent(SubjCtrlTbar(refused, focus), girl)"),
                    TreeParser.parse("Sent(ObjCtrlTbar(persuaded, sleep, you), girl)"),
                    TreeParser.parse("Sent(ObjCtrlTbar(asked, jump, me), boy)"),
                    TreeParser.parse("Sent(ObjCtrlTbar(begged, attend, monster), girl)"),
                    TreeParser.parse("Sent(ObjCtrlTbar(forced, eat, doctor), boy)"),
                    TreeParser.parse("Sent(ObjCtrlTbar(persuaded, focus, politician), girl)"),
                    TreeParser.parse("Sent(Coord_Open_S_fin(and_open_s, slept, jumped), boy)")
            );
            ADJECTIVES_SAMPLES = Arrays.asList(
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion(NP_size_null(" +
                            "NP_age_null(NP_shape_null(NP_colour_null(NP_material_null(skirt))))), fantastic)))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion(NP_size_null(" +
                            "NP_age_null(NP_shape_null(NP_colour_null(NP_material_null(curtain))))), beautiful)))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion(NP_size_null(" +
                            "NP_age_null(NP_shape_null(NP_colour_null(NP_material_null(skirt))))), strange)))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size(" +
                            "NP_age_null(NP_shape_null(NP_colour_null(NP_material_null(curtain)))), long))))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size(" +
                            "NP_age_null(NP_shape_null(NP_colour_null(NP_material_null(skirt)))), short))))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size_null(" +
                            "NP_age(NP_shape_null(NP_colour_null(NP_material_null(curtain))), new)))))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size_null(" +
                            "NP_age(NP_shape_null(NP_colour_null(NP_material_null(skirt))), antique)))))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size_null(" +
                            "NP_age_null(NP_shape_null(NP_colour(NP_material_null(skirt), pale)))))))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size_null(" +
                            "NP_age_null(NP_shape_null(NP_colour(NP_material_null(curtain), dark)))))))"),
                    TreeParser.parse("DP(NP_opinion_2_null(NP_opinion_null(NP_size_null(" +
                            "NP_age_null(NP_shape_null(NP_colour_null(NP_material(skirt, silken))))))))")
            );
            CENTRE_EMBEDDING_SAMPLES = Arrays.asList(
                    TreeParser.parse("TP(Vbar_tr(likes, girl), finish_NP(doctor))"),
                    TreeParser.parse("TP(Vbar_tr(saw, child), finish_NP(woman))"),
                    TreeParser.parse("TP(Vbar_tr(met, boy), finish_NP(astronaut))"),
                    TreeParser.parse("TP(Vbar_tr(hugged, mechanic), finish_NP(doctor))"),
                    TreeParser.parse("TP(Vbar_tr(taught, girl), finish_NP(woman))"),
                    TreeParser.parse("TP(Vbar_tr(amused, child), finish_NP(astronaut))"),
                    TreeParser.parse("TP(Vbar_intr(left), finish_NP(boy))"),
                    TreeParser.parse("TP(Vbar_intr(slept), finish_NP(mechanic))"),
                    TreeParser.parse("TP(Vbar_intr(laughed), finish_NP(astronaut))")
            );
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws IOException, ParseException {

        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerNestedControl = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/nested_control.irtg",
                "Systematically sampled sanity checks for the nested control dataset." +
                        " Created by the grammar nested_control.irtg. ",
                null,
                false
        );
        samplerNestedControl.writeSamplesToFile("examples/amr_template_grammars/nested_control_sanity_check.txt",
                 NESTED_CONTROL_SAMPLES);

        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerAdjectives = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/adjectives.irtg",
                "Systematically sampled sanity checks for the nested adjectives dataset." +
                        " Created by the grammar adjectives.irtg. ",
                null,
                false
        );
        samplerAdjectives.writeSamplesToFile("examples/amr_template_grammars/adjectives_sanity_check.txt",
                ADJECTIVES_SAMPLES);

        SampleSanityChecksFromTemplatesWithInfiniteLanguage samplerCentreEmbedding = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/centre_embedding.irtg",
                "Systematically sampled sanity checks for the nested centre embedding dataset." +
                        " Created by the grammar centre_embedding.irtg. ",
                null,
                false
        );
        samplerCentreEmbedding.writeSamplesToFile("examples/amr_template_grammars/centre_embedding_sanity_check.txt",
                CENTRE_EMBEDDING_SAMPLES);

        SampleSanityChecksFromTemplatesWithInfiniteLanguage longListCounted = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/i_counted.irtg",
                "Systematically sampled sanity checks for the long lists ('I counted') dataset." +
                        " Created by the grammar i_counted.irtg. ",
                Collections.EMPTY_SET,  // there's no actual recursion for the long lists, so we don't have to block rules.
                false
        );
        longListCounted.addSingletonRuleToGrammar("NP_full");
        longListCounted.sampleFromGrammar("examples/amr_template_grammars/i_counted_sanity_check.txt");

        SampleSanityChecksFromTemplatesWithInfiniteLanguage longListVisited = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/she_visited_countries.irtg",
                "Systematically sampled sanity checks for the long lists ('she visited countries') dataset." +
                        " Created by the grammar she_visited_countries.irtg. ",
                Collections.EMPTY_SET,  // there's no actual recursion for the long lists, so we don't have to block rules.
                false
        );
        longListVisited.addSingletonRuleToGrammar("NP_country");
        longListVisited.sampleFromGrammar("examples/amr_template_grammars/she_visited_countries_sanity_check.txt");

        SampleSanityChecksFromTemplatesWithInfiniteLanguage longListBuy = new SampleSanityChecksFromTemplatesWithInfiniteLanguage(
                "examples/amr_template_grammars/please_buy.irtg",
                "Systematically sampled sanity checks for the long lists ('please buy') dataset." +
                        " Created by the grammar please_buy.irtg. ",
                Collections.EMPTY_SET,  // there's no actual recursion for the long lists, so we don't have to block rules.
                false
        );
        longListBuy.addSingletonRuleToGrammar("NP_thing");
        longListBuy.sampleFromGrammar("examples/amr_template_grammars/please_buy_sanity_check.txt");

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

        writeSamplesToFile(outputFile, samples);
        System.out.println("\nTotal samples: " + samples.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeSamplesToFile(String fileName, Iterable<Tree<String>> samples) throws IOException {
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

    private void addSingletonRuleToGrammar(String coordState) {
        Rule single_rule = irtg.getAutomaton().createRule("NP_and", "singleton", new String[]{coordState});
        //noinspection rawtypes
        ((ConcreteTreeAutomaton)irtg.getAutomaton()).addRule(single_rule);
        String singleRuleLabel = "singleton";
        irtg.getAutomaton().getSignature().addSymbol(singleRuleLabel, 1);
        Tree<String> single_tree = Tree.create("?1");
        stringInterp.getHomomorphism().add(singleRuleLabel, single_tree);
        graphInterp.getHomomorphism().add(singleRuleLabel, single_tree);
    }



}
