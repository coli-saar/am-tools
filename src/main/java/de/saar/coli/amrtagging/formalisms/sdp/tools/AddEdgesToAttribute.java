/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.sdp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMToolsVersion;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.up.ling.tree.ParseException;
import edu.stanford.nlp.simple.Sentence;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import javassist.compiler.CodeGen;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;

import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

/**
 * Take an existing amconll file with ids and an SDP corpus and add for each sentence the attribute edges
 * whose value is a list of triples (from_index, to_index, label).
 * Also adds tops.
 * @author matthias
 */
public class AddEdgesToAttribute {
    @Parameter(names = {"--sdp"}, description = "Points to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.dm.sdp";
    
    @Parameter(names = {"--amconll"}, description = "Points to the input corpus, amconll.")//, required = true)
    private String amconllPath = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/dm/train/train.amconll";

    @Parameter(names = {"-o"}, description = "Output file name")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/baseline/DM-train.amconll";
    
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
    
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException{
        AddEdgesToAttribute cli = new AddEdgesToAttribute();
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
        
        Map<String, AmConllSentence> id2Sent = new HashMap<>();
        
        List<AmConllSentence> in = AmConllSentence.readFromFile(cli.amconllPath);
        
        for (AmConllSentence sent : in){
            id2Sent.put(sent.getId(), sent);
            
        }
        
        Map<String, Graph> id2SDP = new HashMap<>();
        
        while ((sdpGraph = reader.readGraph()) != null){
            id2SDP.put(sdpGraph.id, sdpGraph);
        } 
        
        Set<String> ids = id2Sent.keySet();
        ids.retainAll(id2SDP.keySet());
        
        for (String id : ids){
            // edges
            StringJoiner joiner = new StringJoiner(",");
            
            for (Edge e : id2SDP.get(id).getEdges()){
                joiner.add("["+e.source+","+e.target+",\""+e.label+"\"]");
            }
            id2Sent.get(id).setAttr("edges", "[" + joiner.toString() + "]");
            
            // tops
            StringJoiner tops = new StringJoiner(",");
            for (Node n : id2SDP.get(id).getTops()){
                tops.add(Integer.toString(n.id));
            }
            id2Sent.get(id).setAttr("tops", "[" + tops.toString() +"]");
            
        }
        
        AmConllSentence.writeToFile(cli.outPath, in);
    }
}
