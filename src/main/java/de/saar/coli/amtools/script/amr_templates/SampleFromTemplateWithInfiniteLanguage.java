package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import de.saar.coli.amtools.script.amr_templates.SampleFromTemplate;

public class SampleFromTemplateWithInfiniteLanguage {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws IOException, ParseException {
        int numSamples = 25;

        String output_file = "examples/amr_template_grammars/sampling_test.txt";
        String description = "Randomly sampled examples. Created by a grammar.";

        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/see_with.irtg");


        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");

        List<Tree<String>> samples = new ArrayList<>();
        int backupCounter = 0;
        Map<Integer, Double> inside = irtg.getAutomaton().inside();
        while (samples.size() < numSamples && backupCounter < 10000) {
            Tree<String> tree = irtg.getAutomaton().getRandomTree(inside);
            if (checkTree(tree, samples)) {
                samples.add(tree);
                Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(tree));
                Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(tree));
                String sentenceString = stringInterp.getAlgebra().representAsString(stringResult);
                System.out.println(sentenceString);
                String graphString = SampleFromTemplate.fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>)graphResult).left.toIsiAmrString());
                System.out.println(graphString);
            }
            backupCounter++;
        }

        System.err.println("Attempts: " + backupCounter);

        SampleFromTemplate.writeSamplesToFile(output_file, samples, description, irtg);
    }


    public static boolean checkTree(Tree<String> tree, Collection<Tree<String>> samples) {
        if (samples.contains(tree)) {
            return false;
        }

        int minDepth = 1;
        int maxDepth = 6;

        int depth = tree.getHeight();

        if (depth < minDepth || depth > maxDepth) {
            return false;
        }

        List<String> allLabels = tree.getAllNodes().stream().map(Tree::getLabel).collect(Collectors.toList());
        boolean hasDuplicates = allLabels.stream().anyMatch(label -> Collections.frequency(allLabels, label) > 1);

        return !hasDuplicates;

    }

}
