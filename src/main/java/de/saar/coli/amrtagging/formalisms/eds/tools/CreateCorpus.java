/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.eds.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.ConcreteAlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.GraphvizUtils;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.ConcreteAlignmentSignatureBuilder;
import de.saar.coli.amrtagging.formalisms.amr.tools.ReadRawCorpus;
import de.saar.coli.amrtagging.formalisms.eds.EDSBlobUtils;
import de.saar.coli.amrtagging.formalisms.eds.EDSConverter;
import de.saar.coli.amrtagging.formalisms.eds.EDSUtils;
import de.saar.coli.amrtagging.formalisms.eds.PostprocessLemmatize;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.simple.Sentence;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  Create EDS training data.
 * @author matthias
 */
public class CreateCorpus {
     @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/EDS-toy/toy.txt";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/Datensets_sammeln/SDP/sdp2014_2015/data/2015/meine_Daten/EDS-toy/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "bla";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=true;
    
    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;
   
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, ParserException, AMDependencyTree.ConllParserException{      
        CreateCorpus cli = new CreateCorpus();
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
        
        
        List<SGraph> allGraphs = ReadRawCorpus.readGraphs(cli.corpusPath);
        BufferedReader br = new BufferedReader(new FileReader(cli.corpusPath));
        String line;
        List<String> allSents = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        while ((line = br.readLine()) != null){
            if (line.startsWith("# ::id ")){
                ids.add(line.substring("# ::id ".length()));
            }
            if (line.startsWith("# ::snt ")) {
                allSents.add(line.substring("# ::snt ".length()));
            }
        }
        br.close();
        
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        SupertagDictionary multipleRootFragments = new SupertagDictionary();
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        int problems = 0;
        int counter;
        int totalCounter = 0;
        int toOutside = 0;
        int moreIncoming = 0;
        PrintWriter goldEDM = new PrintWriter(cli.outPath+cli.prefix+"-gold.edm");
        PrintWriter goldAMR = new PrintWriter(cli.outPath+cli.prefix+"-gold.amr.txt");
        
        for (counter = 0; counter < allSents.size(); counter++){
            if (counter % 10 == 0 && counter>0){
                System.err.println(counter);
                System.err.println("decomposable so far " + 100*(1.0 - (problems / (float) counter))+ "%");
            }
            
            if (counter % 1000 == 0 && counter>0){ //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            
            totalCounter ++;
            MRInstance inst = EDSConverter.toSGraph(allGraphs.get(counter), allSents.get(counter));
            
            try {
                ConcreteAlignmentSignatureBuilder sigBuilder = new ConcreteAlignmentSignatureBuilder(inst.getGraph(), inst.getAlignments(), new EDSBlobUtils());
                ConcreteAlignmentTrackingAutomaton auto = ConcreteAlignmentTrackingAutomaton.create(inst, sigBuilder, false);
                //AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(inst,sigBuilder, false);
                auto.processAllRulesBottomUp(null);
                Tree<String> t = auto.viterbi();

                if (t != null){
                    //SGraphDrawer.draw(inst.getGraph(), "");
                    ConllSentence sent = ConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    sent.setAttr("id", ids.get(counter));
                    sent.setAttr("raw", allSents.get(counter));
                    Sentence stanfAn = new Sentence(inst.getSentence());

                    List<String> posTags = new ArrayList<>(stanfAn.posTags());
                    sent.addPos(posTags);

                    //List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                    //sent.addNEs(neTags);

                    List<String> lemmata = new ArrayList<>(stanfAn.lemmas());
                    sent.addLemmas(lemmata);
                    PostprocessLemmatize.edsLemmaPostProcessing(sent);
                    
                    EDSUtils.checkSentence(sent); //check if something bad is happening and print if that's the case
                    outCorpus.add(sent);
                    goldEDM.println(EDSConverter.toEDM(allGraphs.get(counter)));
                    //in AMR notation: make sure that node names are displayed correctly
                    SGraph amr = EDSConverter.undoExplicitAnon(EDSConverter.makeNodeNamesExplicit(allGraphs.get(counter)));
                    amr = EDSUtils.stripLnks(amr);
                    goldAMR.println(amr.toIsiAmrString());
                    goldAMR.println();
                    AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();

                    SGraph alignedGraph = amdep.evaluate(true);
                    //SGraphDrawer.draw(allGraphs.get(counter), "original");
                    //SGraphDrawer.draw(alignedGraph, "evaluated one");
                    //amdep.getTree().draw();
                } else {
                    problems ++;
                    System.err.println(inst.getGraph());
                    System.err.println("not decomposable " + inst.getSentence());
                    if (cli.debug){
                        for (Alignment al : inst.getAlignments()){
                            System.err.println(inst.getSentence().get(al.span.start));
                            System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                        }
                        System.err.println(GraphvizUtils.simpleAlignViz(inst));
                    }
                }
            } catch (Exception ex){
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
                problems++;
                if (ex.getMessage().contains("More than one node with edges from outside")){
                    moreIncoming++;
                    Pattern p = Pattern.compile("\\|\\|([0-9]+)-([0-9]+)\\|\\|1,0");
                    Matcher m = p.matcher(ex.getMessage());
                    if (m.find()){
                        int pos = Integer.parseInt(m.group(1));
                        Optional<Alignment> errAl = inst.getAlignments().stream().filter(al -> al.span.start == pos).findFirst();
                        if (errAl.isPresent()){
                            //now construct the sub s-graph that belongs to this alignment 
                            SGraph errGraph = new SGraph();
                            for (String node : errAl.get().nodes){
                                errGraph.addNode(node, inst.getGraph().getNode(node).getLabel());
                            }
                            for (GraphEdge e : inst.getGraph().getGraph().edgeSet()){ //add blob internal edges
                               if (errAl.get().nodes.contains(e.getSource().getName()) && errAl.get().nodes.contains(e.getTarget().getName())){
                                   errGraph.addEdge(e.getSource(),e.getTarget(),e.getLabel()); 
                                }
    
                            }
                            try {
                            multipleRootFragments.getRepr(errGraph); //count this graph pattern
                             System.err.println(errGraph);
                            } catch (UnsupportedOperationException e){
                                
                            }
                        }
                       
                    }
                    //System.err.println(EDSUtils.simpleAlignViz(inst));
                } else {
                    //System.err.println(EDSUtils.simpleAlignViz(inst));
                }
                
            }
        }
        System.err.println("ok: "+(totalCounter-problems));
        System.err.println("total: "+totalCounter);
        System.err.println("i.e. " + 100*(1.0 - (problems / (float) totalCounter))+ "%");
        System.err.println("Caused by more than one node with outgoing edges " + toOutside);
        System.err.println("Caused by more than one node with incoming edges "+moreIncoming);
        cli.write(outCorpus,supertagDictionary);
        goldEDM.close();
        goldAMR.close();
        System.err.println("Graph fragments which had multiple 'incoming' edges and require multiple roots");
        for (SGraph errGraph : multipleRootFragments.counts.keySet()){
            System.err.println(multipleRootFragments.counts.get(errGraph)+ "\t"+errGraph.toIsiAmrString());
        }
        
    }
        private void write(ArrayList<ConllSentence> outCorpus, SupertagDictionary supertagDictionary) throws IOException{
            if (outPath != null && prefix != null){
                ConllSentence.writeToFile(outPath+"/"+prefix+".amconll", outCorpus);
                if (vocab == null){ //only write vocab if it wasn't restored.
                    supertagDictionary.writeToFile(outPath+"/"+prefix+"-supertags.txt");
                }
            }
        }
        
 
        
}
    

    

