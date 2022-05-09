package de.saar.coli.amtools.decomposition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amtools.decomposition.analysis.SupertagEntropy;
import de.saar.coli.amtools.decomposition.automata.component_analysis.ComponentAnalysisToAMDep;
import de.saar.coli.amtools.decomposition.automata.component_analysis.ComponentAutomaton;
import de.saar.coli.amtools.decomposition.automata.component_analysis.DAGComponent;
import de.saar.coli.amtools.decomposition.automata.source_assignment.SAAState;
import de.saar.coli.amtools.decomposition.automata.source_assignment.SourceAssignmentAutomaton;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.saar.coli.amtools.decomposition.formalisms.toolsets.GraphbankDecompositionToolset;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.BinaryIrtgOutputCodec;
import de.up.ling.irtg.util.Counter;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.irtg.util.ProgressListener;
import de.up.ling.tree.Tree;
import org.jetbrains.annotations.NotNull;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CreateEvaluationInput {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input training corpus (*.sdp file)", required = true)
    private String corpusPath;

    @Parameter(names = {"--outputFilename", "-o"}, description = "Filename where amconll file are created", required = true)
    private String outputFilename;


    @Parameter(names = {"--decompositionToolset", "-dt"}, description = "Classname for the GraphbankDecompositionToolset to be used." +
            "If the classpath is in de.saar.coli.amtools.decomposition.formalisms.toolsets, that prefix can be omitted.", required = true)
    private String decompositionToolset;


    @Parameter(names = {"--fasterModeForTesting"}, description = "skips computation of e.g. named entity tags if this flag is set; this can save a lot of time.")
    private boolean fasterModeForTesting =false;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    public static void main(String[] args) throws IOException, ParserException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        // read command line arguments
        // cli stands for CommandLineInterface
        CreateEvaluationInput cli = new CreateEvaluationInput();
        JCommander commander = new JCommander(cli);
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

        GraphbankDecompositionToolset decompositionToolset = cli.getGraphbankDecompositionToolset();

        List<MRInstance> corpus = readCorpus(cli, decompositionToolset);

        List<AmConllSentence> outputCorpus = createBaseAmConllSentences(decompositionToolset, corpus);

        writeOutput(cli, outputCorpus);

    }

    private static void writeOutput(CreateEvaluationInput cli, List<AmConllSentence> outputCorpus) throws IOException {
        Files.createDirectories(Paths.get(cli.outputFilename).getParent());
        AmConllSentence.writeToFile(cli.outputFilename, outputCorpus);
    }

    @NotNull
    private static List<AmConllSentence> createBaseAmConllSentences(GraphbankDecompositionToolset decompositionToolset, List<MRInstance> corpus) {
        List<AmConllSentence> outputCorpus = new ArrayList<>();
        for (MRInstance instance : corpus) {
            AmConllSentence sent= decompositionToolset.makeDecompositionPackage(instance).makeBaseAmConllSentence();
            outputCorpus.add(sent);
        }
        return outputCorpus;
    }

    private static List<MRInstance> readCorpus(CreateEvaluationInput cli, GraphbankDecompositionToolset decompositionToolset) throws IOException {
        List<MRInstance> corpus = decompositionToolset.readCorpusOnlyInput(cli.corpusPath);
        decompositionToolset.applyPreprocessingOnlyInput(corpus);
        return corpus;
    }

    @NotNull
    private GraphbankDecompositionToolset getGraphbankDecompositionToolset() throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> clazz;
        try {
            clazz = Class.forName(decompositionToolset);
        } catch (ClassNotFoundException ex) {
            try {
                clazz = Class.forName("de.saar.coli.amtools.decomposition.formalisms.toolsets." + decompositionToolset);
            } catch (ClassNotFoundException ex2) {
                throw new RuntimeException("Neither class "+ decompositionToolset+
                        " nor de.saar.coli.amtools.decomposition.formalisms.toolsets." + decompositionToolset+" could be found! Aborting.");
            }
        }
        Constructor<?> ctor = clazz.getConstructor(Boolean.class);
        GraphbankDecompositionToolset decompositionToolset = (GraphbankDecompositionToolset)ctor.newInstance(new Object[] { fasterModeForTesting});
        return decompositionToolset;
    }

}
