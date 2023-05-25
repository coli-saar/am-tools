package de.saar.coli.amtools.script.amr_templates;

import de.saar.basic.Pair;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeParser;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SampleFromTemplate {

    public static void main(String[] args) throws IOException, ParseException {
//        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath("examples/amr_template_grammars/give_up_in.irtg");
//        Tree<String> tree = TreeParser.parse("templateGiveUpAmbitionInEmotionalState(my_sister,give_up,ambition,a_moment_of_clarity)");
//        System.out.println(irtg.getInterpretation("graph").getHomomorphism().apply(tree));
//        System.out.println(((Pair<SGraph, Object>)irtg.getInterpretation("graph").interpret(tree)).left.toIsiAmrStringWithSources());

        // replace double quotes with single quotes
//        System.out.println(":wiki \"\"J._K._Rowling\"\"".replaceAll("\"\"", "\""));

        int numSamples = 25;

        sample(numSamples, "examples/amr_template_grammars/see_with.irtg",
                "examples/amr_template_grammars/see_with.txt",
                "Prepositional Phrase attachment ambiguities. Created by a grammar.");
        sample(numSamples, "examples/amr_template_grammars/read_by.irtg",
                "examples/amr_template_grammars/read_by.txt",
                "Prepositional Phrase attachment ambiguities. Created by a grammar.");
        sample(numSamples, "examples/amr_template_grammars/bought_for.irtg",
                "examples/amr_template_grammars/bought_for.txt",
                "Prepositional Phrase attachment ambiguities. Created by a grammar.");
        sample(numSamples, "examples/amr_template_grammars/keep_from.irtg",
                "examples/amr_template_grammars/keep_from.txt",
                "Prepositional Phrase attachment ambiguities. Created by a grammar.");
        sample(numSamples, "examples/amr_template_grammars/give_up_in.irtg",
                "examples/amr_template_grammars/give_up_in.txt",
                "Prepositional Phrase attachment ambiguities. Created by a grammar.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void sample(int numSamples, String grammar_path, String output_file, String description) throws IOException {
        InterpretedTreeAutomaton irtg = InterpretedTreeAutomaton.fromPath(grammar_path);
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        Map<String, List<Tree<String>>> templateCounter = new HashMap<>();
        for (Tree<String> grammarTree : irtg.getAutomaton().language()) {
            templateCounter.computeIfAbsent(grammarTree.getLabel(), s -> new ArrayList<>()).add(grammarTree);
        }
        List<Tree<String>> samples = new ArrayList<>();
        for (String template : templateCounter.keySet()) {
            System.out.println(template);
            System.out.println(templateCounter.get(template).size());
            List<Tree<String>> trees = templateCounter.get(template);
            Collections.shuffle(trees);
            for (int i = 0; i < Math.min(numSamples, trees.size()); i++) {
                Tree<String> tree = trees.get(i);
                samples.add(tree);
                Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(tree));
                Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(tree));
                String sentenceString = stringInterp.getAlgebra().representAsString(stringResult);
                System.out.println(sentenceString);
                String graphString = fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>)graphResult).left.toIsiAmrString());
                System.out.println(graphString);
            }
            System.out.println("\n\n\n");
        }

        writeSamplesToFile(output_file, samples, description, irtg);
    }


    public static String fixAMRString(String amrString) {
        amrString = amrString.replaceAll("(:op[0-9]+) ([^ \"()]+)", "$1 \"$2\"");
        amrString = amrString.replaceAll("\"\\+\"", "+");
//        amrString = amrString.replaceAll("\\\"\\\"", "\"");
        amrString = amrString.replaceAll("(:wiki) ([^ \"()]+)", "$1 \"$2\"");
        amrString = amrString.replaceAll("\"-\"", "-");
        return amrString;
    }


    public static void writeSamplesToFile(String fileName, Iterable<Tree<String>> samples, String description, InterpretedTreeAutomaton irtg) throws IOException {
        FileWriter w = new FileWriter(fileName);
        w.write("# " + description+"\n\n");
        Interpretation stringInterp = irtg.getInterpretation("string");
        Interpretation graphInterp = irtg.getInterpretation("graph");
        for (Tree<String> sample : samples) {
            Object stringResult = stringInterp.getAlgebra().evaluate(stringInterp.getHomomorphism().apply(sample));
            Object graphResult = graphInterp.getAlgebra().evaluate(graphInterp.getHomomorphism().apply(sample));
            String sentenceString = postprocessString((List<String>)stringResult);
            w.write("# ::snt " + sentenceString+"\n");
            w.write("# ::tree " + sample.toString()+"\n");
            String graphString = fixAMRString(((Pair<SGraph, ApplyModifyGraphAlgebra.Type>)graphResult).left.toIsiAmrString());
            w.write(graphString+"\n\n");
        }
        w.close();
    }

    public static String postprocessString(List<String> tokens) {
        String detokenizedString = tokens.stream().collect(Collectors.joining(" ")).replaceAll(" , ", ", ")
                .replaceAll(" \\.", ".");
        // make first token uppercase
        return detokenizedString.substring(0, 1).toUpperCase() + detokenizedString.substring(1);
    }
}
