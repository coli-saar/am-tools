/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.owlike.genson.Genson;
import de.saar.coli.amrtagging.AMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AlignmentTrackingAutomaton;
import de.saar.coli.amrtagging.ConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.SupertagDictionary;
import de.saar.coli.amrtagging.formalisms.amr.AMRSignatureBuilder;
import de.saar.coli.amrtagging.mrp.sdp.dm.DM;
import de.saar.coli.amrtagging.mrp.utils.ConlluSentence;
import de.saar.coli.amrtagging.mrp.graphs.MRPGraph;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.mrp.Formalism;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import edu.stanford.nlp.simple.Sentence;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Creates amconll corpus from MRP data.
 * 
 * @author matthias
 */
public class CreateCorpus {
    @Parameter(names = {"--mrp"}, description = "Path to the input corpus  or subset thereof")//, required = true)
    private String corpusPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/training/dm/wsj.mrp";
    
    @Parameter(names = {"--companion", "-c"}, description = "Path to companion data")//, required = true)
    private String companion = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/companion/dm/dm_full.conllu";

    @Parameter(names = {"--outPath", "-o"}, description = "Path for output files")//, required = true)
    private String outPath = "/home/matthias/Schreibtisch/Hiwi/Koller/MRP/data/output/DM/";
    
    @Parameter(names={"--prefix","-p"}, description = "Prefix for output file names (e.g. train --> train.amconll)")//, required=true)
    private String prefix = "bla";
    
    @Parameter(names = {"--vocab", "-v"}, description = "vocab file containing supertags (e.g. points to training vocab when doing dev/test files)")
    private String vocab = null;
    
    @Parameter(names = {"--debug"}, description = "Enables debug mode, i.e. ")
    private boolean debug=false;
    
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
        
       
        int counter = 0;
        int problems = 0;
        ArrayList<ConllSentence> outCorpus = new ArrayList<>();
        SupertagDictionary supertagDictionary = new SupertagDictionary();
        SGraphConverter.READABLE_NODE_LABELS = false; //if false -> node names are senses, if true -> nodenames also contain word forms
        
        if (cli.vocab != null){
            supertagDictionary.readFromFile(cli.vocab);
        }
        BufferedReader graphReader = new BufferedReader(new FileReader(cli.corpusPath));
        List<ConlluSentence> conlluSents = ConlluSentence.readFromFile(cli.companion);

        String line;
        Genson genson = new Genson();
        while ((line = graphReader.readLine()) != null){
            if (counter % 10 == 0 && counter>0){
                System.err.println(counter);
                System.err.println("decomposable so far " + 100*(1.0 - (problems / (float) counter))+ "%");
            }
            if (counter % 1000 == 0 && counter>0){ //every now and then write intermediate results.
                cli.write(outCorpus, supertagDictionary);
            }
            counter ++;
            MRPGraph mrpGraph = genson.deserialize(line, MRPGraph.class);
            Formalism formalism;
            if (mrpGraph.getFramework().equals("dm")){
                formalism = new DM();
            } else {
                throw new IllegalArgumentException("Formalism/Framework "+mrpGraph.getFramework()+" not supported yet.");
            }

            String id = mrpGraph.getId();
            try {
                //AlignmentTrackingAutomaton auto = AlignmentTrackingAutomaton.create(inst,sigBuilder, false);
                //auto.processAllRulesBottomUp(null);
                //Tree<String> t = auto.viterbi();

                //if (t != null){
                    //SGraphDrawer.draw(inst.getGraph(), "");
                    //ConllSentence sent = ConllSentence.fromIndexedAMTerm(t, inst, supertagDictionary);
                    //sent.setAttr("id", sdpGraph.id);
                    //Sentence stanfAn = new Sentence(inst.getSentence().subList(0, inst.getSentence().size()-1)); //remove artifical root "word"

                    //List<String> posTags = SGraphConverter.extractPOS(sdpGraph);
                    //posTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    //sent.addPos(posTags);

                    //List<String> neTags = new ArrayList<>(stanfAn.nerTags());
                    //neTags.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    //sent.addNEs(neTags);

                    //List<String> lemmata = SGraphConverter.extractLemmas(sdpGraph);
                    //lemmata.add(SGraphConverter.ARTIFICAL_ROOT_LABEL);
                    //sent.addLemmas(lemmata);

                    //outCorpus.add(sent);
                    //AMDependencyTree amdep = AMDependencyTree.fromSentence(sent);
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getDelexSupertag() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();
                    //amdep.getTree().map(ent -> ent.getForm() + " " + ent.getType().toString() +" "+ent.getEdgeLabel()).draw();

                    //SGraph alignedGraph = amdep.evaluate(true);
                //} else {
                 //   problems ++;
                    //System.err.println("not decomposable " + inst.getSentence());
                    //if (cli.debug){
                    //    for (Alignment al : inst.getAlignments()){
                    //        System.err.println(inst.getSentence().get(al.span.start));
                    //        System.err.println(sigBuilder.getConstantsForAlignment(al, inst.getGraph(), false));
                    //    }
                    //}
                    if (problems > 1){ //ignore the first problems
                        //SGraphDrawer.draw(inst.getGraph(), "");
                        //break;
                    }
                //}
            } catch (Exception ex){
                System.err.println("Ignoring an exception:");
                ex.printStackTrace();
            }
        }
        System.err.println("ok: "+(counter-problems));
        System.err.println("total: "+counter);
        System.err.println("i.e. " + 100*(1.0 - (problems / (float) counter))+ "%");
        cli.write(outCorpus,supertagDictionary);
        
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
