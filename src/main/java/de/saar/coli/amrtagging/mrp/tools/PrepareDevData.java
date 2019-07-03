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
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.mrp.sdp.DM;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.eds.EDS;
import de.saar.coli.amrtagging.mrp.sdp.PSD;
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

/**
 * Creates amconll corpus from MRP data.
 * 
 * @author matthias
 */
public class PrepareDevData {
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/dm/wsj.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";
    
    @Parameter(names = {"--train-companion", "-tc"}, description = "Path to companion data that doesn't contain the test set but the training set")//, required = true)
    private String full_companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/DM/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "dev";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
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
        
       
        int counter = 0;
        int problems = 0;
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        
        Reader fr = new FileReader(cli.corpusPath);
        Reader sentReader = new FileReader(cli.companion);
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        List<ConlluSentence> trainingDataForTagger = ConlluSentence.readFromFile(cli.full_companion);
        EDS eds = new EDS(trainingDataForTagger);
        for (Pair<MRPGraph, ConlluSentence> pair : pairs){
            MRPGraph mrpGraph = pair.getLeft();
            ConlluSentence usentence = pair.getRight();
            counter ++;
            String input = mrpGraph.getInput();
            Formalism formalism;
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
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+mrpGraph.getFramework()+" not supported yet.");
            }

            String id = mrpGraph.getId();
            
            ConllSentence sent = new ConllSentence();
            int idx = 1;
            for (ConlluEntry e : usentence){
                sent.add(new ConllEntry(idx, e.getForm()));
                idx++;
            }
            sent.addRanges(usentence.ranges());
            sent.setAttr("id", mrpGraph.getId());
            sent.setAttr("framework", mrpGraph.getFramework());
            sent.setAttr("raw",input);
            sent.setAttr("version", mrpGraph.getVersion());
            sent.setAttr("time", mrpGraph.getTime());

            List<String> posTags = usentence.pos();
            sent.addPos(posTags);
            
            //TODO: NER
            List<String> lemmata = usentence.lemmas();
            sent.addLemmas(lemmata);
            formalism.refineDelex(sent);
            outCorpus.add(sent);

        }
        ConllSentence.writeToFile(cli.outPath+"/"+cli.prefix+".amconll", outCorpus);
        
    }   
    
}
