/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.ConlluSentence;
import static de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractorCLI.LITERAL_JOINER;
import de.saar.coli.amrtagging.mrp.MRPOutputCodec;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.mrp.utils.Fuser;
import de.up.ling.tree.ParseException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * This class is intended for one-time use until the preprocessing of the AMR corpus is run the next time.
 * It should split off some dev data of an amconll corpus and collect the corresponding gold graphs.
 * July 9
 * @author matthias
 */
public class AMRSplitSomeDevData {
    @Parameter(names={"--amconll","-p"}, description = "Prefix to amconll file")//, required=true)
    private String amconll = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/everything/amconll/corpus.amconll";
    
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/amr/amr.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data.")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/all.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/our-amr/everything/amconll/split/";
    
    @Parameter(names = {"--devSize"}, description = "Number of graphs in devset")//, required = true)
    private int devSize = 1500;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException{
        
        AMRSplitSomeDevData cli = new AMRSplitSomeDevData();
        JCommander commander = new JCommander(cli);

        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        
        Reader fr = new FileReader(cli.corpusPath);
        Reader sentReader = new FileReader(cli.companion);
        List<Pair<MRPGraph, ConlluSentence>> pairs = Fuser.fuse(fr, sentReader);
        
        List<AmConllSentence> sentences = AmConllSentence.readFromFile(cli.amconll);
        
        HashMap<List<String>,Pair<MRPGraph,ConlluSentence>> wordsToGraph = new HashMap<>();
        for (Pair<MRPGraph, ConlluSentence> p : pairs){
            wordsToGraph.put(p.right.words(), p);
        }
        
        List<AmConllSentence> dev = new ArrayList<>();
        List<AmConllSentence> train = new ArrayList<>();
        List<MRPGraph> devGraphs = new ArrayList<>();
        System.err.println("Comparing...");
        int i = 0;
        int inDev = 0;
        
        Random random = new Random(121);
        Collections.shuffle(sentences, random);
        for (AmConllSentence sent : sentences){
            if (i % 1000 == 0) System.err.println(i);
            Pair<MRPGraph, ConlluSentence> g = wordsToGraph.get(getWords(sent));
            if (g != null && inDev < cli.devSize){
                MRPGraph graph = g.left;
                sent.setAttr("id", graph.getId());
                sent.setAttr("framework", graph.getFramework());
                sent.setAttr("raw",graph.getInput());
                sent.setAttr("version", graph.getVersion());
                sent.setAttr("time", graph.getTime());
                dev.add(sent);
                devGraphs.add(g.left);
                inDev++;
            } else {
                train.add(sent);
            }
            
            i++;
        }
        AmConllSentence.writeToFile(cli.outPath+"/dev.amconll", dev);
        AmConllSentence.writeToFile(cli.outPath+"/train.amconll", train);
        MRPOutputCodec outputCodec = new MRPOutputCodec();
        OutputStream output = new FileOutputStream(cli.outPath+"/dev.mrp");
        for (MRPGraph devGraph : devGraphs){
            outputCodec.write(devGraph, output);
        }
        output.close();
        
    }
    
    private static List<String> getWords(AmConllSentence sent){
        List<String> words = new ArrayList<>();
        for (int i = 0; i < sent.size(); i++){
            String w = sent.get(i).getForm();
            for (String part : w.split(LITERAL_JOINER)){
                words.add(part);
            }
        }
        return words;
    }
    
}
