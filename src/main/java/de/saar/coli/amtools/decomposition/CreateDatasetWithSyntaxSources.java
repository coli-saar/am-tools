package de.saar.coli.amtools.decomposition;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.ConjHandler;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.graph.SGraph;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileReader;
import java.util.*;

public class CreateDatasetWithSyntaxSources {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\smallDev.sdp";

    @Parameter(names = {"--syntaxScores", "-s"}, description = "Path to the opProbs.txt file containing syntax edge scores")//, required = true)
    private String syntaxEdgeScoresPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\ud_scores_march2020\\smallDev\\opProbs.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path to output folder where")//, required = true)
    private String outPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\";

    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "smallDev";

    @Parameter(names = {"--vocab", "-v"}, description = "existing vocab file containing supertags (e.g. points to training vocab when doing dev/test files). Using this flag means dev set mode, don't use it for the training set")
    private String vocab = null;

    @Parameter(names = {"--corpusType", "-ct"}, description = "values can be DM, PAS or PSD, default is DM")//, required = true)
    private String corpusType = "DM";

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    public static void main(String[] args) throws Exception {

        // read command line arguments
        // cli stands for CommandLineInterface
        CreateDatasetWithSyntaxSources cli = new CreateDatasetWithSyntaxSources();
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

        //read graphs from file
        GraphReader2015 gr = new GraphReader2015(cli.corpusPath);

        // read syntax edge scores from file (use them later to obtain source names).
        List<List<List<Pair<String, Double>>>> syntaxEdgeScores = Util.readEdgeProbs(new FileReader(cli.syntaxEdgeScoresPath),
                true, 0, 5, false);//indices are 1-based, like in the am-dependency tree
        //the following lines work around weird legacy issue for edge scores
        Iterator<List<Pair<String, Double>>> syntaxEdgeScoresIterator = new Iterator<List<Pair<String, Double>>>() {
            Iterator<List<List<Pair<String, Double>>>> it = syntaxEdgeScores.iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }
            @Override
            public List<Pair<String, Double>> next() {
                return it.next().get(0);
            }
        };

        AMRBlobUtils blobUtils;
        switch (cli.corpusType) {
            case "DM": blobUtils = new DMBlobUtils(); break;
            case "PAS": blobUtils = new PASBlobUtils(); break;
            case "PSD": blobUtils = new PSDBlobUtils(); break;
            default: throw new IllegalArgumentException("Illegal corpus type '"+cli.corpusType+"'. Legal are 'DM', 'PAS' and 'PSD'.");
        }

        //AmConllWithSourcesCreator wants three lists, so we shall make them.
        List<SGraph> graphCorpus = new ArrayList<>();
        List<DecompositionPackage> decompositionPackageList = new ArrayList<>();
        List<SourceAssigner> sourceAssignerList = new ArrayList<>();
        Graph sdpGraph;
        // filling the lists with one entry each for each graph in the corpus.
        while ((sdpGraph = gr.readGraph()) != null) {
            MRInstance inst = SGraphConverter.toSGraph(sdpGraph);
            SGraph graph = inst.getGraph();
            if (cli.corpusType.equals("PSD")) {
                graph = ConjHandler.handleConj(graph, (PSDBlobUtils)blobUtils, false);  // TODO make consistent with SourceAutomataCLI
            }
            graphCorpus.add(graph);
            decompositionPackageList.add(new SDPDecompositionPackage(inst, blobUtils, false));
            sourceAssignerList.add(new SyntaxSourceAssigner(syntaxEdgeScoresIterator.next()));
        }

        // case distinction: if a supertag dictionary path is given, use it and call dev version (since for creating the dev set, we use the training set supertag path)
        // if no supertag dictionary path is given, make one and call training version.
        String amConllOutPath = cli.outPath+"/"+cli.prefix+".amconll";

        if (cli.vocab != null) {
            AmConllWithSourcesCreator.createDevCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, amConllOutPath, cli.vocab);
        } else {
            String supertagDictionaryPath = cli.outPath+"/"+cli.prefix+"_supertagDictionary.txt";
            AmConllWithSourcesCreator.createTrainingCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, amConllOutPath, supertagDictionaryPath);
        }


    }



}
