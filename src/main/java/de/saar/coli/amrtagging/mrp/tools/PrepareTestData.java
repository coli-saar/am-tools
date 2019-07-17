/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.graphs.TestSentence;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
import de.saar.coli.amrtagging.mrp.ucca.UCCA;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.saar.coli.amrtagging.mrp.utils.MRPUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        PrepareTestData cli = new PrepareTestData();
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
        
       
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        
        cli.formalism = cli.formalism.toLowerCase();
        Reader fr = new FileReader(cli.corpusPath);
        Map<String,TestSentence> id2testSent = TestSentence.read(fr);
        
        List<ConlluSentence> companionData =MRPGraph.readFromFile(cli.companion).stream().map((MRPGraph g) -> g.toConlluSentence()).collect(Collectors.toList());
        
        List<ConlluSentence> trainingDataForTagger = ConlluSentence.readFromFile(cli.full_companion);
        EDS eds = new EDS(trainingDataForTagger);
        
        for (ConlluSentence usentence : companionData){
            String id = usentence.getId();
            TestSentence itsTestSentence = id2testSent.get(id);
            String input = itsTestSentence.input;
            Formalism formalism;
            fixWeirdLemmaErrors(usentence);
            if (cli.formalism.equals("dm")){
                DM dm = new DM();
                formalism = dm;
                usentence = dm.refine(usentence);
                MRPUtils.addArtificialRootToSent(usentence);
                input = MRPUtils.addArtificialRootToSent(input);
            } else if (cli.formalism.equals("psd")){
                PSD psd = new PSD();
                formalism = psd;
                usentence = psd.refine(usentence);
                MRPUtils.addArtificialRootToSent(usentence);
                input = MRPUtils.addArtificialRootToSent(input);
            } else if (cli.formalism.equals("eds")){
                formalism = eds;
                usentence = eds.refine(usentence);
                MRPUtils.addArtificialRootToSent(usentence);
                input = MRPUtils.addArtificialRootToSent(input);
            } else if (cli.formalism.equals("ucca")){
                UCCA ucca = new UCCA();
                formalism = ucca;
                usentence = ucca.refine(usentence); //UCCA doesn't need artificial root
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+cli.formalism+" not supported yet.");
            }
            if (!itsTestSentence.targets.contains(cli.formalism)) continue;

            ConllSentence sent = new ConllSentence();
            int idx = 1;
            for (ConlluEntry e : usentence){
                sent.add(new ConllEntry(idx, e.getForm()));
                idx++;
            }
            sent.addRanges(usentence.ranges());
            sent.setAttr("id", id);
            sent.setAttr("framework", cli.formalism);
            sent.setAttr("input",input);
            sent.setAttr("version", itsTestSentence.version);
            sent.setAttr("time", itsTestSentence.time);

            List<String> posTags = usentence.pos();
            sent.addPos(posTags);
            
            //TODO: NER
            List<String> lemmata = usentence.lemmas();
            sent.addLemmas(lemmata);
            formalism.refineDelex(sent);
            outCorpus.add(sent);

        }
        ConllSentence.writeToFile(cli.outPath+"/"+cli.formalism+"_"+cli.prefix+".amconll", outCorpus);
        
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
