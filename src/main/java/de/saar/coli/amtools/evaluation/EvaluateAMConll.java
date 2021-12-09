package de.saar.coli.amtools.evaluation;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.PropertyDetection;


import de.saar.coli.amrtagging.formalisms.amr.tools.Relabel;
import de.saar.coli.amtools.decomposition.formalisms.toolsets.GraphbankDecompositionToolset;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.BlobUtils;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.*;

import java.util.stream.Collectors;


/**
 * Evaluates the AM Dependency Terms in an AMConll corpus and outputs the graphs in a new corpus file. This works in
 * concert with an EvaluationToolset class that specifies the output corpus format and any postprocessing.
 *
 * @author JG, based on previous de.saar.coli.amrtagging.formalisms.amr.tools.EvaluateCorpus
 */
public class EvaluateAMConll {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees", required = true)
    private String corpusPath = null;

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files", required = true)
    private String outPath = null;

    @Parameter(names = {"--evaluationToolset", "-ts"}, description = "Classname of the EvaluationToolset class to be used. Default applies no postprocessing and writes the files in ISI AMR format")
    private String evaluationToolset = "de.saar.coli.amtools.evaluation.EvaluationToolset";


    @Parameter(names = {"--extras", "-e"}, description = "Additional parameters to the constructor of the Evaluation toolset, as a single string. Optional.")
    private String toolsetExtras = null;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;






    public static void main(String[] args) throws IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, InterruptedException {
        EvaluateAMConll cli = new EvaluateAMConll();
        JCommander commander = new JCommander(cli);
        commander.setProgramName("constraint_extractor");

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }





        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);


        Class<?> clazz;
        try {
            clazz = Class.forName(cli.evaluationToolset);
        } catch (ClassNotFoundException ex) {
            try {
                clazz = Class.forName("de.saar.coli.amtools.evaluation.toolsets." + cli.evaluationToolset);
            } catch (ClassNotFoundException ex2) {
                throw new RuntimeException("Neither class "+cli.evaluationToolset+
                        " nor de.saar.coli.amtools.decomposition.formalisms.toolsets." + cli.evaluationToolset+" could be found! Aborting.");
            }
        }

        // creating the toolset. Calling a different parameter depending on whether the --extra option was used.
        EvaluationToolset evaluationToolset;
        if (cli.toolsetExtras != null) {
            Constructor<?> ctor = clazz.getConstructor(String.class);
            evaluationToolset = (EvaluationToolset)ctor.newInstance(new Object[] { cli.toolsetExtras});
        } else {
            Constructor<?> ctor = clazz.getConstructor();
            evaluationToolset = (EvaluationToolset)ctor.newInstance(new Object[] {});
        }


        List<MRInstance> outputCorpus = new ArrayList<>();

        for (AmConllSentence s : sents) {

            //TODO is this the right place for this? Or is it AMR specific?
            //fix the REPL problem:
            //the NN was trained with data where REPL was used for some nouns because the lexical label was lower-cased
            //we don't want $REPL$ in our output, so let's replace predictions that contain REPL but where there is no replacement field
            //with the word form.

            for (AmConllEntry e : s) {
                if (e.getLexLabel().contains(AmConllEntry.REPL_PLACEHOLDER) && e.getReplacement().equals(AmConllEntry.DEFAULT_NULL)) {
                    e.setLexLabel(e.getReLexLabel().replace(AmConllEntry.REPL_PLACEHOLDER, AmConllEntry.FORM_PLACEHOLDER));
                }
            }

            AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(s);
            SGraph evaluatedGraph = amdep.evaluateWithoutRelex(true);

            List<Alignment> alignments = null;

            evaluatedGraph = evaluatedGraph.withFreshNodenames();
            alignments = AlignedAMDependencyTree.extractAlignments(evaluatedGraph);

            //rename nodes names from 1@@m@@--LEX-- to LEX@0
            List<String> labels = s.lemmas();
            for (String n : evaluatedGraph.getAllNodeNames()) {
                if (evaluatedGraph.getNode(n).getLabel().contains("LEX")) {
                    Pair<Integer, Pair<String, String>> info = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                    labels.set(info.left - 1, s.get(info.left - 1).getReLexLabel());
                    evaluatedGraph.getNode(n).setLabel(Relabel.LEXMARKER + (info.left - 1));
                } else {
                    Pair<Integer, Pair<String, String>> info = AlignedAMDependencyTree.decodeNode(evaluatedGraph.getNode(n));
                    evaluatedGraph.getNode(n).setLabel(info.right.right);
                }
            }

            MRInstance mrInst = new MRInstance(s.words(), evaluatedGraph, alignments);
            mrInst.setPosTags(s.getFields(AmConllEntry::getPos));
            mrInst.setLemmas(s.lemmas());
            mrInst.setNeTags(s.getFields(AmConllEntry::getNe));

            mrInst = evaluationToolset.applyPostprocessing(mrInst, s);

            outputCorpus.add(mrInst);
        }

        evaluationToolset.writeCorpus(cli.outPath, outputCorpus);

    }



}
