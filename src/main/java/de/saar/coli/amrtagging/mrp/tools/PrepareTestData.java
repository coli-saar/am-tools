/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.*;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.TestSentence;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.ucca.NamedEntityMerger;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.ling.CoreLabel;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

/**
 *
 * @author matthias
 */
public class PrepareTestData {
    @Parameter(names = {"--mrp"}, description = "Path to the input.mrp")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/test/input.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data of test data, MRP file!")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/test/udpipe.mrp";
    
    @Parameter(names = {"--train-companion", "-tc"}, description = "Path to companion data that doesn't contain the test set but the training set (CONLLU!)")//, required = true)
    private String full_companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/test";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "test";
    
    @Parameter(names={"--formalism","-f"}, description = "Formalism whose graphbank to prepare")//, required=true)
    private String formalism = "ucca";

    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz")
    private String stanfordNerFilename = null;

    @Parameter(names = {"--uiuc-ner-model"}, description = "Use UIUC NER tagger")
    private boolean useUiucNer = false;

    @Parameter(names = {"--uiuc-ner-tagset"}, description = "Tagset to use for UIUC NER tagger; options: NER_CONLL (default), NER_ONTONOTES")
    private String uiucNerTagset = NER_CONLL;

    @Parameter(names = {"--merge-ner"}, description = "Merge named entities")
    private boolean mergeNamedEntities = false;

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException, ClassNotFoundException, PreprocessingException {
        PrepareTestData cli = new PrepareTestData();
        JCommander commander = new JCommander(cli);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occurred: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }
        
       
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        
        cli.formalism = cli.formalism.toLowerCase();
        Reader fr = new FileReader(cli.corpusPath);
        Map<String,TestSentence> id2testSent = TestSentence.read(fr);
        
        List<ConlluSentence> companionData = MRPGraph.readFromFile(cli.companion).stream().map((MRPGraph g) -> g.toConlluSentence()).collect(Collectors.toList());
        List<ConlluSentence> trainingDataForTagger = ConlluSentence.readFromFile(cli.full_companion);

        if( "amr".equals(cli.formalism)) {
            // Unlike the other formalisms, AMR produces only a *.amr corpus, which then needs to be processed further

            PrintWriter pw = new PrintWriter(new FileWriter(cli.outPath + "/" + cli.formalism + "_" + cli.prefix + ".amr"));

            // print sentences + dummy graph to *.amr file, in random order
            for( Map.Entry<String,TestSentence> entry : id2testSent.entrySet() ) {
                if (!entry.getValue().targets.contains(cli.formalism)) continue;
                pw.printf("# ::id %s\n", entry.getValue().id);
                pw.printf("# ::snt %s\n", entry.getValue().input);
                pw.printf("# dummy\n");
                pw.printf("(d / dummy)\n\n");
            }

            pw.flush();
            pw.close();
        } else {
            EDS eds = new EDS(trainingDataForTagger);

            NamedEntityRecognizer neRecognizer = null;
            if( cli.stanfordNerFilename != null ) {
                neRecognizer = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename), false); //might want to change to true? But we used the coarser tagset in MRP shared task
            } else if( cli.useUiucNer ) {
                neRecognizer = new UiucNamedEntityRecognizer(cli.uiucNerTagset);
            }

            for (ConlluSentence usentence : companionData) {
                String id = usentence.getId();
                ConlluSentence originalUsentence = usentence.copy(); // preserve all tokens here, even if NEs are merged in usentence
                TestSentence itsTestSentence = id2testSent.get(id);

                String input = itsTestSentence.input;
                Formalism formalism;
                NamedEntityMerger neMerger = new NamedEntityMerger(id, new MrpPreprocessedData(usentence), neRecognizer);

                fixWeirdLemmaErrors(usentence);

                if (cli.formalism.equals("dm")) {
                    DM dm = new DM();
                    formalism = dm;
                    usentence = dm.refine(usentence);
                    MRPUtils.addArtificialRootToSent(usentence);
                    input = MRPUtils.addArtificialRootToSent(input);
                } else if (cli.formalism.equals("psd")) {
                    PSD psd = new PSD();
                    formalism = psd;
                    usentence = psd.refine(usentence);
                    MRPUtils.addArtificialRootToSent(usentence);
                    input = MRPUtils.addArtificialRootToSent(input);
                } else if (cli.formalism.equals("eds")) {
                    formalism = eds;
                    usentence = eds.refine(usentence);
                    MRPUtils.addArtificialRootToSent(usentence);
                    input = MRPUtils.addArtificialRootToSent(input);
                } else if (cli.formalism.equals("ucca")) {
                    UCCA ucca = new UCCA();
                    formalism = ucca;
                    usentence = ucca.refine(usentence); //UCCA doesn't need artificial root

                    if( cli.mergeNamedEntities ) {
                        usentence = neMerger.merge(usentence);
                    }
                } else {
                    throw new IllegalArgumentException("Formalism/Framework " + cli.formalism + " not supported yet.");
                }

                if (!itsTestSentence.targets.contains(cli.formalism)) continue;

                AmConllSentence sent = new AmConllSentence();
                int idx = 1;
                for (ConlluEntry e : usentence) {
                    sent.add(new AmConllEntry(idx, e.getForm()));
                    idx++;
                }
                sent.addRanges(usentence.ranges());
                sent.setAttr("git", AMToolsVersion.GIT_SHA);
                sent.setAttr("id", id);
                sent.setAttr("framework", cli.formalism);
                sent.setAttr("input", input);
                sent.setAttr("version", itsTestSentence.version);
                sent.setAttr("time", itsTestSentence.time);

                List<String> posTags = usentence.pos();
                sent.addPos(posTags);

                if( neRecognizer != null ) {
                    List<CoreLabel> tokens = Util.makeCoreLabelsForTokens(originalUsentence.words());
                    List<CoreLabel> netags = neRecognizer.tag(tokens);
                    List<String> mappedNeTags = neMerger.mapTags(de.up.ling.irtg.util.Util.mapToList(netags, CoreLabel::ner));
                    sent.addNEs(mappedNeTags);
                }

                List<String> lemmata = usentence.lemmas();
                sent.addLemmas(lemmata);
                formalism.refineDelex(sent);
                outCorpus.add(sent);

            }

            AmConllSentence.writeToFile(cli.outPath + "/" + cli.formalism + "_" + cli.prefix + ".amconll", outCorpus);
        }
    }   
    
    /**
     * Hand-correcting weird stuff in MRP companion data.
     * @param sent 
     */
    private static void fixWeirdLemmaErrors(ConlluSentence sent){
        for (ConlluEntry entry : sent){
            if (entry.getForm().equals("martin-in-the-fields")){
                entry.setLemma("martin-in-the-fields");
            } else if (entry.getForm().equals("india-brazil-south")){
                entry.setLemma("india-brazil-south");
            }
        }
        
    }
    
    
}
