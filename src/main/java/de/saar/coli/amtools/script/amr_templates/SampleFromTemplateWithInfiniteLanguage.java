package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeVisitor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleFromTemplateWithInfiniteLanguage {

    static final int numSamples = 25;
    static final int minDepth = 4;
    static final int maxDepth = 8;

    static final int depthStep = 2;

    static final String irtgPath = "examples/amr_template_grammars/centre_embedding.irtg";
    static final String outputFile = "examples/amr_template_grammars/centre_embedding.txt";
    static final String description = "Randomly sampled examples of centre embedding. Created by a grammar.";

    static final Set<String> ruleLabelsWithDuplicatesAllowed = new HashSet<>(Arrays.asList("obj_rel", "NP_RC"));



    static int null_pointer_exception_count = 0;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws IOException, ParseException {


        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath(irtgPath);


        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");

        List<Tree<String>> samples = new ArrayList<>();
        Map<Integer, Double> inside = irtg.getAutomaton().inside();
        for (int state_id : inside.keySet()) {
            System.out.println(irtg.getAutomaton().getStateForId(state_id) + " " + inside.get(state_id));
        }
        inside.put(irtg.getAutomaton().getIdForState("ObjRC"), 0.5); // we can override erroneous inside values with a sort of made up version (doesn't have to be exact; inside may in fact be undefined)
        for (int targetDepth = minDepth; targetDepth <= maxDepth; targetDepth += depthStep) {
            int backupCounter = 0;
            null_pointer_exception_count = 0;
            List<Tree<String>> samplesHere = new ArrayList<>();
            while (samplesHere.size() < numSamples && backupCounter < numSamples * 1000) {
                Tree<String> tree = irtg.getAutomaton().getRandomTree(inside);
                if (checkTree(tree, samplesHere, targetDepth)) {
                    samplesHere.add(tree);
                    System.out.println(tree);
                    Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(tree));
                    Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(tree));
                    String sentenceString = stringInterp.getAlgebra().representAsString(stringResult);
                    System.out.println(sentenceString);
                    String graphString = SampleFromTemplate.fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>) graphResult).left.toIsiAmrString());
                    System.out.println(graphString);
                }
                backupCounter++;
            }
            samples.addAll(samplesHere);
            System.err.println("Samples obtained: " + samplesHere.size());
            System.err.println("Attempts: " + backupCounter);
            System.err.println("Null pointer exceptions: " + null_pointer_exception_count);
        }

        SampleFromTemplate.writeSamplesToFile(outputFile, samples, description, irtg);
    }


    static int total_depths_printed = 0;
    public static boolean checkTree(Tree<String> tree, Collection<Tree<String>> samples, int targetDepth) {
        if (samples.contains(tree)) { return false; }

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
