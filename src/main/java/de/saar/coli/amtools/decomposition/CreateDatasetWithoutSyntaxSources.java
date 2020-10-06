package de.saar.coli.amtools.decomposition;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.*;
import de.saar.coli.amrtagging.formalisms.ucca.UCCABlobUtils;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

public class CreateDatasetWithoutSyntaxSources {

    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus ")//, required = true)
    private String corpusPath = "/Users/mariomagued/Desktop/thesis/actual_thesis/data/alto/original/training_no_remote_test.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/Users/mariomagued/Desktop/thesis/actual_thesis/data/create_corpus/";

    @Parameter(names = {"--companion"}, description = "Path to companion data.")//, required = true)
    private String companion = "/Users/mariomagued/Desktop/thesis/actual_thesis/data/companion/ewt01.conllu";

    @Parameter(names = {"--prefix", "-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")
    private String prefix = "testing_new_decomposition";

    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;

    @Parameter(names = {"--timeout"}, description = "maximum runtime of the tree-automaton step per thread, in seconds. Default = 1800 (=30 mins)")
    private int timeout = 1800;

    @Parameter(names = {"--write-frequency"}, description = "Write output corpus to disk after every <write-frequency> sentences")
    private int writeFrequency = 1;

    @Parameter(names = {"--sort"}, description = "Sort sentences by length")
    private boolean sort = true;

    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug = true;

    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz")
    private String stanfordNerFilename = null;

    @Parameter(names = {"--uiuc-ner-tagset"}, description = "Tagset to use for UIUC NER tagger; options: NER_CONLL (default), NER_ONTONOTES")
    private String uiucNerTagset = NER_CONLL;

    @Parameter(names = {"--merge-ner"}, description = "Merge named entities")
    private boolean mergeNamedEntities = false;


    public static void main(String[] args) throws FileNotFoundException, IOException, ParserException, CorpusReadingException, ClassNotFoundException {
        CreateDatasetWithoutSyntaxSources cli = new CreateDatasetWithoutSyntaxSources();
        JCommander commander = new JCommander(cli);
        System.out.println("parsing args");
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

        InterpretedTreeAutomaton loaderIRTG = new InterpretedTreeAutomaton(new ConcreteTreeAutomaton());
        Signature dummySig = new Signature();
        loaderIRTG.addInterpretation("id", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("flavor", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("framework", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("version", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("time", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("spans", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("input", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("string", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("graph", new Interpretation(new GraphAlgebra(), new Homomorphism(dummySig, dummySig)));
        loaderIRTG.addInterpretation("alignment", new Interpretation(new StringAlgebra(), new Homomorphism(dummySig, dummySig)));

        Corpus corpus = Corpus.readCorpusWithStrictFormatting(new FileReader(cli.corpusPath), loaderIRTG);

//        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
//        SupertagDictionary supertagDictionary = new SupertagDictionary();
//
//        if (cli.vocab != null) {
//            supertagDictionary.readFromFile(cli.vocab);
//        }
//        Reader fr = new FileReader(cli.corpusPath);

        List<Instance> instances = new ArrayList<>();
        corpus.iterator().forEachRemaining((Instance i) -> instances.add(i));

        if (cli.sort) {
            instances.sort((g1, g2) -> Integer.compare(((List) g1.getInputObjects().get("string")).size()
                    , ((List) g2.getInputObjects().get("string")).size()));
        }
        UCCA ucca = new UCCA();
        PreprocessedData preprocData = new MrpPreprocessedData(new File(cli.companion));
        System.out.println("preprocessing...");

        Set<String> companionIds = ConlluSentence.readFromFile(cli.companion).stream().map((ConlluSentence s) -> s.getId()).collect(Collectors.toSet());
        for (Instance corpusInstance : corpus) {
            String id = ((List<String>) corpusInstance.getInputObjects().get("id")).get(0);
            if (!companionIds.contains(id)) {
                System.err.println("Check companion data! We don't have an analysis for the sentence belonging to graph " + id);
                return;
            }
        }

       NamedEntityRecognizer neRecognizer;
        NamedEntityRecognizer neRecognizerForMerging;

        if (cli.stanfordNerFilename != null) {
            neRecognizer = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename), false);
        } else {
            neRecognizer = new UiucNamedEntityRecognizer(cli.uiucNerTagset);
        }






        // LOADING AND SETUP UNTIL HERE




        // THE THREE LISTS
        List<SGraph> graphCorpus = new ArrayList<>();
        List<DecompositionPackage> decompositionPackageList = new ArrayList<>();
        List<SourceAssigner> sourceAssignerList = new ArrayList<>();


        //instances.parallelStream().forEach((Instance corpusInstance) -> {
        for (Instance corpusInstance: corpus) {
            String id = ((List<String>) corpusInstance.getInputObjects().get("id")).get(0);
            String inputString = ((List<String>) corpusInstance.getInputObjects().get("input")).stream().collect(Collectors.joining(" "));
            String version = ((List<String>) corpusInstance.getInputObjects().get("version")).get(0);
            String time = ((List<String>) corpusInstance.getInputObjects().get("time")).get(0);
            String framework = ((List<String>) corpusInstance.getInputObjects().get("framework")).get(0);
            String flavor = ((List<String>) corpusInstance.getInputObjects().get("flavor")).get(0);

            List<String> tokenRanges = (List) corpusInstance.getInputObjects().get("spans");
            SGraph graph = (SGraph) corpusInstance.getInputObjects().get("graph");
            List<String> sentence = (List) corpusInstance.getInputObjects().get("string");

            assert (sentence.size() == preprocData.getTokens(id).size()); // actually, one would like to assert that they are the same list, but token normalization may have changed that

//            sentence = ucca.refineTokens(sentence); // no longer needed, see #67

            // read alignments

            List<String> als = (List) corpusInstance.getInputObjects().get("alignment");
            if (als.size() == 1 && als.get(0).equals("")) {
                //System.err.println("Repaired empty alignment!");
                als = new ArrayList<>();
            }

            String[] alStrings = als.toArray(new String[0]);
            List<Alignment> alignments = new ArrayList<>();

            for (String alString : alStrings) {
                Alignment al = Alignment.read(alString, 0);
                alignments.add(al);
            }


            // merge named entities
            /*NamedEntityMerger nemerger = new NamedEntityMerger(id, preprocData, neRecognizer);

            if (cli.mergeNamedEntities && neRecognizer != null) {
                sentence = nemerger.merge(sentence);
                alignments = nemerger.fixAlignments(alignments);
            }

             */

            MRInstance inst = new MRInstance(sentence, graph, alignments);
            List<String> posTags = new ArrayList<String>();
            List<TaggedWord> mappedPosTags = preprocData.getPosTags(id);
            for (TaggedWord posTag : mappedPosTags) {
                posTags.add(posTag.tag());
            }

            List<String> mappedLemmas = preprocData.getLemmas(id);
            List<CoreLabel> tokens = preprocData.getTokens(id); //Util.makeCoreLabelsForTokens(sentence);
            List<CoreLabel> netags = null;

            /*
            try {
                netags = neRecognizer.tag(tokens);
            } catch (PreprocessingException e) {
                e.printStackTrace();
            }
            List<String> mappedNeTags = nemerger.mapTags(de.up.ling.irtg.util.Util.mapToList(netags, CoreLabel::ner));

             */


            try {
                inst.checkEverythingAligned();
            } catch (Exception e) {
                System.err.println("Ignoring an exception:");
                System.err.println("id " + id);
                e.printStackTrace();
                //return; //skip this sentence
                continue;
            }

            SGraph sgraph = inst.getGraph();
            SGraph renamedSGraph = new SGraph();

            for (String nodeName: sgraph.getAllNodeNames()){
                renamedSGraph.addNode("n_" + nodeName, sgraph.getNode(nodeName).getLabel());
            }
            for (GraphEdge e:sgraph.getGraph().edgeSet()){
                renamedSGraph.addEdge(renamedSGraph.getNode("n_" + e.getSource().getName()), renamedSGraph.getNode("n_" +e.getTarget().getName()), e.getLabel());
            }

            renamedSGraph.addSource("root", "n_" + sgraph.getNodeForSource("root"));
            graphCorpus.add(renamedSGraph);


            List<Alignment> renamedAls = new ArrayList<>();

            for (String alstring:alStrings){
                String[] renamedAlString = alstring.split("\\|\\|");
                String actualAls = renamedAlString[0];
                String tokenSpans = renamedAlString[1];
                String weight = renamedAlString[2];

                String newAls = "";

                for(String n: actualAls.split("\\|")){
                    String newNodeName = "n_" + n;
                    if (newAls.equals("")){
                        newAls = newNodeName;
                    }

                    else{
                        newAls = "|" + newNodeName;
                    }
                }

                renamedAls.add(Alignment.read(newAls + "||" + tokenSpans + "||" + weight));
            }

            MRInstance renamedInst = new MRInstance(sentence, renamedSGraph, renamedAls);

            Object[] UCCADecompositionPackageBundle = new Object[5];
            UCCADecompositionPackageBundle[0] = renamedSGraph;
            UCCADecompositionPackageBundle[1] = renamedInst;
            UCCADecompositionPackageBundle[2] = tokens;
            UCCADecompositionPackageBundle[3] = posTags;
            UCCADecompositionPackageBundle[4] = mappedLemmas;

            AMRBlobUtils blobUtils;
            blobUtils = new UCCABlobUtils();
            decompositionPackageList.add(new UCCADecompositionPackage(UCCADecompositionPackageBundle, blobUtils));
            List<GraphEdge> renamedEdges = new ArrayList<GraphEdge>(renamedSGraph.getGraph().edgeSet());
            sourceAssignerList.add(new OldSourceAssigner(renamedEdges, renamedInst));


            // case distinction: if a supertag dictionary path is given, use it and call dev version (since for creating the dev set, we use the training set supertag path)
            // if no supertag dictionary path is given, make one and call training version.

        }


        String amConllOutPath = cli.outPath + "/" + cli.prefix + ".amconll";
        if (cli.vocab != null) {
            try {
                AmConllWithSourcesCreator.createDevCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, amConllOutPath, cli.vocab);
            } catch (IOException | ParserException e) {
                e.printStackTrace();
            }

        } else {
            String supertagDictionaryPath = cli.outPath + "/" + cli.prefix + "_supertagDictionary.txt";
            try {
                AmConllWithSourcesCreatorParallel.createTrainingCorpus(graphCorpus, decompositionPackageList, sourceAssignerList, amConllOutPath, supertagDictionaryPath);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }
}