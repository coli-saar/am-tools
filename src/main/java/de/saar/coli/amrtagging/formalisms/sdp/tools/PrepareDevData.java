/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.ConllEntry;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import edu.stanford.nlp.simple.Sentence;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;

import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 * Prepare test data to be in the format we use for the parser. Strips off all gold information but the tokenization.
 * @author matthias
 */
public class PrepareDevData {
    @Parameter(names = {"--corpus", "-c"}, description = "Points to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp/snt.sdp";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "test_like";
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException{
        PrepareDevData cli = new PrepareDevData();
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
        
        GraphReader2015 reader = new GraphReader2015(cli.corpusPath);
        Graph sdpGraph;
        ArrayList<ConllSentence> out = new ArrayList<ConllSentence>();
        while ((sdpGraph = reader.readGraph()) != null){
            ConllSentence currentSent = new ConllSentence();
            currentSent.setAttr("id", sdpGraph.id);
            for (Node n : sdpGraph.getNodes()){
                if (n.id > 0){
                    ConllEntry e = new ConllEntry(n.id,n.form);
                    e.setPos(n.pos);
                    e.setLemma(n.lemma);
                    currentSent.add(e);
                }
            }
            ArrayList<String> words = currentSent.words();
            Sentence stanfordSent = new Sentence(words);
            
            ArrayList<String> neTags = new ArrayList<>(stanfordSent.nerTags());
            neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            
            ConllEntry artRoot = new ConllEntry(sdpGraph.getNNodes(),SGraphConverter.ARTIFICAL_ROOT_LABEL);
            artRoot.setLemma(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            artRoot.setPos(SGraphConverter.ARTIFICAL_ROOT_LABEL);
            currentSent.add(artRoot);
            
            currentSent.addNEs(neTags);
            out.add(currentSent);
        } 
        ConllSentence.writeFile(cli.outPath+"/"+cli.prefix+".amconll", out);
    }
}
