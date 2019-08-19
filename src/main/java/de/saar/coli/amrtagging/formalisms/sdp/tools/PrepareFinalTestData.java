/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import edu.stanford.nlp.simple.Sentence;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import se.liu.ida.nlp.sdp.toolkit.io.ParagraphReader;

/**
 * Prepare test data to be in the format we use for the parser.
 * @author matthias
 */
public class PrepareFinalTestData {
    @Parameter(names = {"--corpus", "-c"}, description = "Points to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/test/en.id.dm.tt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/test/funkts";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. test.id --> test.id.amconll)")//, required=true)
    private String prefix = "test.id";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException{
        PrepareFinalTestData cli = new PrepareFinalTestData();
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
        
        ParagraphReader reader = new ParagraphReader(cli.corpusPath);
        List<String> para;
        ArrayList<AmConllSentence> out = new ArrayList<>();
        while ((para = reader.readParagraph()) != null){
            AmConllSentence currentSent = new AmConllSentence();
            para = para.stream().filter(line -> !line.startsWith("#")).collect(Collectors.toList());
            for (String info : para){
                String[] fields = info.split("\t");
                AmConllEntry e = new AmConllEntry(Integer.parseInt(fields[0]), fields[1]);
                e.setLemma(fields[2]);
                e.setPos(fields[3]);
                currentSent.add(e);
            }
            List<String> words = currentSent.words();
            Sentence stanfordSent = new Sentence(words);
            
            List<String> neTags = new ArrayList<>(stanfordSent.nerTags());
            neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            
            AmConllEntry artRoot = new AmConllEntry(para.size()+1,SGraphConverter.ARTIFICAL_ROOT_LABEL);
            artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            currentSent.add(artRoot);
            
            currentSent.addNEs(neTags);
            out.add(currentSent);
        } 
        AmConllSentence.writeToFile(cli.outPath+"/"+cli.prefix+".amconll", out);
    }
}
