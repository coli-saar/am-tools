/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.ucca.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.*;
import de.saar.coli.amrtagging.formalisms.ucca.UCCABlobUtils;
import de.saar.coli.amrtagging.mrp.ucca.NamedEntityMerger;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.up.ling.irtg.Interpretation;
import de.up.ling.irtg.InterpretedTreeAutomaton;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.automata.ConcreteTreeAutomaton;
import de.up.ling.irtg.corpus.Corpus;
import de.up.ling.irtg.corpus.CorpusReadingException;
import de.up.ling.irtg.corpus.Instance;
import de.up.ling.irtg.hom.Homomorphism;
import de.up.ling.irtg.signature.Signature;
import de.up.ling.irtg.util.Util;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

/**
 * Creates amconll corpus from MRP data.
 *
 * @author matthias
 */
public class CreateCorpusParallel {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus ")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Mario/alto_fixed 2/alto_fixed/100.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Mario/alto_fixed 2/alto_fixed/";

    @Parameter(names = {"--companion"}, description = "Path to companion data.")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/ucca/all_ucca.conllu";

    @Parameter(names = {"--prefix", "-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")
    private String prefix = "train";

    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;

    @Parameter(names = {"--help", "-?", "-h"}, description = "displays help if this is the only command", help = true)
    private boolean help = false;

    @Parameter(names = {"--timeout"}, description = "maximum runtime of the tree-automaton step per thread, in seconds. Default = 1800 (=30 mins)")
    private int timeout = 1800;

    @Parameter(names = {"--write-frequency"}, description = "Write output corpus to disk after every <write-frequency> sentences")
    private int writeFrequency = 1000;

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
        CreateCorpusParallel cli = new CreateCorpusParallel();
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

        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();

        if (cli.vocab != null) {
            supertagDictionary.readFromFile(cli.vocab);
        }
        Reader fr = new FileReader(cli.corpusPath);

        List<Instance> instances = new ArrayList<>();
        corpus.iterator().forEachRemaining((Instance i) -> instances.add(i));

        if (cli.sort) {
            instances.sort((g1, g2) -> Integer.compare(((List) g1.getInputObjects().get("string")).size()
                    , ((List) g2.getInputObjects().get("string")).size()));
        }
        UCCA ucca = new UCCA();
        PreprocessedData preprocData = new MrpPreprocessedData(new File(cli.companion));

        Set<String> companionIds = ConlluSentence.readFromFile(cli.companion).stream().map((ConlluSentence s) -> s.getId()).collect(Collectors.toSet());
        for (Instance corpusInstance : corpus) {
            String id = ((List<String>) corpusInstance.getInputObjects().get("id")).get(0);
            if (!companionIds.contains(id)) {
                System.err.println("Check companion data! We don't have an analysis for the sentence belonging to graph " + id);
                return;
            }
        }

        NamedEntityRecognizer neRecognizer;
        if (cli.stanfordNerFilename != null) {
            neRecognizer = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename));
        } else {
            neRecognizer = new UiucNamedEntityRecognizer(cli.uiucNerTagset);
        }

        instances.parallelStream().forEach((Instance corpusInstance) -> {
            String id = ((List<String>) corpusInstance.getInputObjects().get("id")).get(0);
            String inputString = ((List<String>) corpusInstance.getInputObjects().get("input")).stream().collect(Collectors.joining(" "));
            String version = ((List<String>) corpusInstance.getInputObjects().get("version")).get(0);
            String time = ((List<String>) corpusInstance.getInputObjects().get("time")).get(0);
            String framework = ((List<String>) corpusInstance.getInputObjects().get("framework")).get(0);
            String flavor = ((List<String>) corpusInstance.getInputObjects().get("flavor")).get(0);

            List<String> tokenRanges = (List) corpusInstance.getInputObjects().get("spans");
            SGraph graph = (SGraph) corpusInstance.getInputObjects().get("graph");
            List<String> sentence = (List) corpusInstance.getInputObjects().get("string");

            assert(sentence.size() == preprocData.getTokens(id).size()); // actually, one would like to assert that they are the same list, but token normalization may have changed that

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
            NamedEntityMerger nemerger = new NamedEntityMerger(id, preprocData, neRecognizer);

            if (cli.mergeNamedEntities && neRecognizer != null) {
                sentence = nemerger.merge(sentence);
                alignments = nemerger.fixAlignments(alignments);
            }


            MRInstance inst = new MRInstance(sentence, graph, alignments);
            try {
                inst.checkEverythingAligned();
            } catch (Exception e) {
                System.err.println("Ignoring an exception:");
                System.err.println("id " + id);
                e.printStackTrace();
                return; //skip this sentence
            }
            //create MRInstance object that bundles the three:

            try {
                ConcreteAlignmentSignatureBuilder sigBuilder = new ConcreteAlignmentSignatureBuilder(inst.getGraph(), inst.getAlignments(), new UCCABlobUtils());
                ConcreteAlignmentTrackingAutomaton auto = ConcreteAlignmentTrackingAutomaton.create(inst, sigBuilder, false);
                try {
                    auto.processAllRulesBottomUp(null, cli.timeout * 1000);
                } catch (InterruptedException ex) {
                    System.err.println("Decomposition of graph " + id + " interrupted after " + cli.timeout + " seconds. Will be excluded in output.");
                }
                Tree<String> t = auto.viterbi();

                if (t != null) { //graph can be decomposed
                    //SGraphDrawer.draw(inst.getGraph(), ""); //display graph
                    AmConllSentence sent = AmConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    sent.setAttr("git", AMToolsVersion.GIT_SHA);
                    sent.setAttr("id", id);
                    sent.setAttr("input", inputString);
                    sent.setAttr("flavor", flavor);
                    sent.setAttr("time", time);
                    sent.setAttr("framework", framework);
                    sent.setAttr("version", version);

                    List<String> mappedPosTags = nemerger.mapTags(de.up.ling.irtg.util.Util.mapToList(preprocData.getPosTags(id), TaggedWord::tag));
                    List<String> mappedLemmas = nemerger.mapTags(preprocData.getLemmas(id));
                    List<CoreLabel> tokens = preprocData.getTokens(id); //Util.makeCoreLabelsForTokens(sentence);
                    List<CoreLabel> netags = neRecognizer.tag(tokens);
                    List<String> mappedNeTags = nemerger.mapTags(de.up.ling.irtg.util.Util.mapToList(netags, CoreLabel::ner));

                    sent.addPos(mappedPosTags);
                    sent.addLemmas(mappedLemmas);
                    sent.addNEs(mappedNeTags);

                    // token ranges for multi-word tokens should be merged, but can't do this yet
                    List<TokenRange> originalTokenRanges = Util.mapToList(tokenRanges, range -> TokenRange.fromString(range));
                    List<TokenRange> mappedTokenRanges = nemerger.combineTokenRanges(originalTokenRanges);
                    sent.addRanges(mappedTokenRanges);


//                    sent.addRanges(tokenRanges.stream().map((String range) -> TokenRange.fromString(range)).collect(Collectors.toList()));

//                    sent.addPos(preprocData.getPosTags(id).stream().map((TaggedWord w) -> w.tag()).collect(Collectors.toList()));
//                    sent.addLemmas(preprocData.getLemmas(id));

                    ucca.refineDelex(sent);

                    synchronized (outCorpus) {
                        outCorpus.add(sent);

                        if (outCorpus.size() % cli.writeFrequency == 0 && outCorpus.size() > 0) {
                            synchronized (supertagDictionary) {
                                cli.write(outCorpus, supertagDictionary);
                            }
                            System.err.println(outCorpus.size());
                        }
                    }
                } else {
                    System.err.println("not decomposable " + id);
                }
            } catch (Exception ex) {
                System.err.println("Ignoring an exception:");
                System.err.println("id " + id);
                System.err.println(inputString);
                ex.printStackTrace();
            }
        });
        System.err.println("ok: " + (outCorpus.size()));
        System.err.println("total: " + instances.size());
        System.err.println("i.e. " + 100 * (outCorpus.size() / (float) instances.size()) + "%");
        synchronized (outCorpus) {
            synchronized (supertagDictionary) {
                cli.write(outCorpus, supertagDictionary);
            }
        }


    }

    private void write(ArrayList<AmConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException {
        if (outPath != null && prefix != null) {
            AmConllSentence.writeToFile(outPath + "/" + prefix + ".amconll", outCorpus);
            if (vocab == null) { //only write vocab if it wasn't restored.
                supertagDictionary.writeToFile(outPath + "/" + prefix + "-supertags.txt");
            }
        }
    }


}
