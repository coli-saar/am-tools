/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.*;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.*;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.ucca.NamedEntityMerger;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.ling.CoreLabel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static edu.illinois.cs.cogcomp.core.datastructures.ViewNames.NER_CONLL;

/**
 * Creates amconll corpus from MRP data.
 * 
 * @author matthias
 */
public class PrepareDevData {
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/UCCA/very_first/dev/out.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/really_everything.conllu";
    
    @Parameter(names = {"--train-companion", "-tc"}, description = "Path to companion data that doesn't contain the test set but the training set")//, required = true)
    private String full_companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/ucca/all_ucca.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/am-parser/data/MRP/UCCA/very_first/dev/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "dev";
    
    @Parameter(names = {"--stanford-ner-model"}, description = "Filename of Stanford NER model english.conll.4class.distsim.crf.ser.gz")
    private String stanfordNerFilename = null;

    @Parameter(names = {"--uiuc-ner-model"}, description = "Use UIUC NER tagger")
    private boolean useUiucNer = false;

    @Parameter(names = {"--uiuc-ner-tagset"}, description = "Tagset to use for UIUC NER tagger; options: NER_CONLL (default), NER_ONTONOTES")
    private String uiucNerTagset = NER_CONLL;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;

    @Parameter(names = {"--merge-ner"}, description = "Merge named entities (in UCCA)")
    private boolean mergeNamedEntities = false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException, ClassNotFoundException, PreprocessingException{      
        PrepareDevData cli = new PrepareDevData();
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
        
       
        ArrayList<AmConllSentence> outCorpus = new ArrayList<>();
        
        NamedEntityRecognizer neRecognizer = null;
        NamedEntityRecognizer neRecognizerForMerging = null;
        
        if( cli.stanfordNerFilename != null ) {
            neRecognizerForMerging = new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename), false);
            neRecognizer = neRecognizerForMerging; //new StanfordNamedEntityRecognizer(new File(cli.stanfordNerFilename), true); //probably want to use the version with true
            
        } else if( cli.useUiucNer ) {
            neRecognizer = new UiucNamedEntityRecognizer(cli.uiucNerTagset);
            neRecognizerForMerging = neRecognizer;
        }
        
        Reader fr = new FileReader(cli.corpusPath);
        Reader sentReader = new FileReader(cli.companion);
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        List<ConlluSentence> trainingDataForTagger = ConlluSentence.readFromFile(cli.full_companion);
        EDS eds = new EDS(trainingDataForTagger);

        for (Pair<MRPGraph, ConlluSentence> pair : pairs){
            MRPGraph mrpGraph = pair.getLeft();
            ConlluSentence usentence = pair.getRight();
            ConlluSentence originalUsentence = usentence.copy(); // preserve all tokens here, even if NEs are merged in usentence
            String input = mrpGraph.getInput();
            Formalism formalism;

            NamedEntityMerger neMerger = new NamedEntityMerger(mrpGraph.getId(), new MrpPreprocessedData(usentence), neRecognizerForMerging);

            if (mrpGraph.getFramework().equals("dm")){
                DM dm = new DM();
                formalism = dm;
                usentence = dm.refine(usentence);
                MRPUtils.addArtificialRootToSent(usentence);
                input = MRPUtils.addArtificialRootToSent(input);
            } else if (mrpGraph.getFramework().equals("psd")){
                PSD psd = new PSD();
                formalism = psd;
                usentence = psd.refine(usentence);
                MRPUtils.addArtificialRootToSent(usentence);
                input = MRPUtils.addArtificialRootToSent(input);
            } else if (mrpGraph.getFramework().equals("eds")){
                formalism = eds;
                usentence = eds.refine(usentence);
                MRPUtils.addArtificialRootToSent(usentence);
                input = MRPUtils.addArtificialRootToSent(input);
           } else if (mrpGraph.getFramework().equals("ucca")) {
                UCCA ucca = new UCCA();
                formalism = ucca;
                usentence = ucca.refine(usentence); //UCCA doesn't need artificial root

                if( cli.mergeNamedEntities ) {
                    usentence = neMerger.merge(usentence);
                }
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+mrpGraph.getFramework()+" not supported yet.");
            }
            
            AmConllSentence sent = new AmConllSentence();
            int idx = 1;
            for (ConlluEntry e : usentence){
                sent.add(new AmConllEntry(idx, e.getForm()));
                idx++;
            }
            sent.addRanges(usentence.ranges());
            sent.setAttr("git", AMToolsVersion.GIT_SHA);
            sent.setAttr("id", mrpGraph.getId());
            sent.setAttr("framework", mrpGraph.getFramework());
            sent.setAttr("input",input);
            sent.setAttr("version", mrpGraph.getVersion());
            sent.setAttr("time", mrpGraph.getTime());

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
        AmConllSentence.writeToFile(cli.outPath+"/"+cli.prefix+".amconll", outCorpus);
        
    }   
    
}
