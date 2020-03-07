package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import static de.saar.coli.amrtagging.AlignedAMDependencyTree.decodeNode;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.amr.AMRBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Edge;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.graph.Node;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static de.saar.coli.amtools.script.FindPatternsAcrossSDP.*;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;

public class ModifyDependencyTrees {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/dm/dev/dev.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/pas/dev/dev.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/psd/dev/dev.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/dm/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/pas/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/psd/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--outputPath", "-o"}, description = "Path to the output folder")
    private String outputPath = "../../github/";



    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

    /**
     * prints CSV tables for all auxiliary verbs according to wikipedia. Information includes total counts, and counts of
     * edge patterns.
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws ParserException
     * @throws AlignedAMDependencyTree.ConllParserException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, AlignedAMDependencyTree.ConllParserException, ParserException, Exception {
        //just getting command line args
        ModifyDependencyTrees cli = new ModifyDependencyTrees();
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


        //setup
        new File(cli.outputPath).mkdirs();
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph pasGraph;
        Graph psdGraph;
        List<AmConllSentence> amDM = AmConllSentence.read(new FileReader(cli.amconllPathDM));
        List<AmConllSentence> amPSD = AmConllSentence.read(new FileReader(cli.amconllPathPSD));
        List<AmConllSentence> amPAS = AmConllSentence.read(new FileReader(cli.amconllPathPAS));
        // map IDs to AmConllSentences so we can look the AmConllSentences up
        Map<String, AmConllSentence> id2amDM = new HashMap<>();
        amDM.stream().forEach(sent -> id2amDM.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPAS = new HashMap<>();
        amPAS.stream().forEach(sent -> id2amPAS.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPSD = new HashMap<>();
        amPSD.stream().forEach(sent -> id2amPSD.put(sent.getId(), sent));
        Set<String> decomposedIDs = Sets.intersectAll(id2amDM.keySet(), id2amPAS.keySet(), id2amPSD.keySet());

        List<AmConllSentence> newAmDM = new ArrayList<>();
        List<AmConllSentence> newAmPAS = new ArrayList<>();
        List<AmConllSentence> newAmPSD = new ArrayList<>();


        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null) {
            if (decomposedIDs.contains(dmGraph.id)) {
                //now we know the graph was decomposed in all graphbanks, and we have all three AM dep trees for it.
                // we can also look at the original graphs (dmGraph etc) if we need to.
                String id = dmGraph.id;
                AmConllSentence dmDep = id2amDM.get(id);
                AmConllSentence pasDep = id2amPAS.get(id);
                AmConllSentence psdDep = id2amPSD.get(id);
                String originalDMDepStr = dmDep.toString();
                String originalPSDDepStr = psdDep.toString();
                String originalPASDepStr = pasDep.toString();


                SGraph dmSGraph = AlignedAMDependencyTree.fromSentence(dmDep).evaluate(true);
                onlyIndicesAsLabels(dmSGraph);
                SGraph psdSGraph = AlignedAMDependencyTree.fromSentence(psdDep).evaluate(true);
                onlyIndicesAsLabels(psdSGraph);
                SGraph pasSGraph = AlignedAMDependencyTree.fromSentence(pasDep).evaluate(true);
                onlyIndicesAsLabels(pasSGraph);
                //System.out.println(dmSGraph);

                //modify new dep trees here
                fixDeterminer(psdDep, dmDep, pasDep);
                fixNegation(psdDep, dmDep, pasDep);


                SGraph newdmSGraph = null;
                SGraph newpsdSGraph = null;
                SGraph newpasSGraph = null;
                //try {
                newdmSGraph = AlignedAMDependencyTree.fromSentence(dmDep).evaluate(true);
                onlyIndicesAsLabels(newdmSGraph);
                newpsdSGraph = AlignedAMDependencyTree.fromSentence(psdDep).evaluate(true);
                onlyIndicesAsLabels(newpsdSGraph);
                newpasSGraph = AlignedAMDependencyTree.fromSentence(pasDep).evaluate(true);
                onlyIndicesAsLabels(newpasSGraph);
                
                //} catch (IllegalArgumentException e) {
                //    System.err.println(psdDep);
                //    System.err.println(pasDep);
                //    e.printStackTrace();
                //}

                if (!newdmSGraph.equals(dmSGraph)) {
                    System.err.println(originalDMDepStr);
                    System.err.println(dmDep);
                    System.err.println(dmSGraph.toIsiAmrStringWithSources());
                    System.err.println(newdmSGraph.toIsiAmrStringWithSources());
                    SGraphDrawer.draw(dmSGraph, "original");
                    SGraphDrawer.draw(newdmSGraph,"modified");
                    
                    throw new Exception("Difference in DM");
                }
                if (!newpsdSGraph.equals(psdSGraph)) {
                    System.err.println(originalPSDDepStr);
                    System.err.println(psdDep);
                    System.err.println(psdSGraph.toIsiAmrStringWithSources());
                    System.err.println(newpsdSGraph.toIsiAmrStringWithSources());
                    SGraphDrawer.draw(psdSGraph, "original");
                    SGraphDrawer.draw(newpsdSGraph,"modified");
                    throw new Exception("Difference in PSD");
                }
                if (!newpasSGraph.equals(pasSGraph)) {
                    System.err.println(originalPASDepStr);
                    System.err.println(pasDep);
                    System.err.println(pasSGraph.toIsiAmrStringWithSources());
                    System.err.println(newpasSGraph.toIsiAmrStringWithSources());
                    SGraphDrawer.draw(pasSGraph, "original");
                    SGraphDrawer.draw(newpasSGraph,"modified");
                    throw new Exception("Difference in PAS");
                }
                
                newAmDM.add(dmDep);
                newAmPAS.add(pasDep);
                newAmPSD.add(psdDep);
            }
        }

        AmConllSentence.write(new FileWriter(cli.outputPath+"/dm.amconll"), newAmDM);
        AmConllSentence.write(new FileWriter(cli.outputPath+"/pas.amconll"), newAmPAS);
        AmConllSentence.write(new FileWriter(cli.outputPath+"/psd.amconll"), newAmPSD);


    }
    
    /**
     * Takes an s-graph in which node names and labels are encoded into the labels and strips off the node names
     * and only keeps the alignment
     * @param sg 
     */
    private static void onlyIndicesAsLabels(SGraph sg){
         for (String nodeName : sg.getAllNodeNames()) {
            Pair<Integer, Pair<String, String>> infos = decodeNode(sg.getNode(nodeName));
            sg.getNode(nodeName).setLabel(Integer.toString(infos.left));
        }
    }

    public static void fixDeterminer(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Determiners for PSD
        // we find all words that are determiners (have DT POS tag)
        // and that are ignored in the PSD graph (have incoming IGNOREn edge).
        // Then we change the PSD entry to have an empty modifier graph,
        // and attach it to the head it has in DM.
        int index = 0;
        for (AmConllEntry word : psdDep){
            if (word.getPos().equals("DT") && word.getEdgeLabel().equals("IGNORE")){
                // System.err.println(index);
                // System.err.println(dmDep.getParent(index));
                if (dmDep.getParent(index) == null) continue; // if DM ignored determiner as well, skip this
                int head = dmDep.getParent(index).getId();// index is 0-based, head is 1-based
                word.setHead(head);
                word.setEdgeLabel("MOD_det");
                word.setDelexSupertag("(u<root, det>)");// empty modifier graph: one unlabeled node with root and det source.
                word.setType(new ApplyModifyGraphAlgebra.Type("(det)")); // the type of the DelexSupertag
            }
            index++;
        }
    }
    public static void fixNegation(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException, AlignedAMDependencyTree.ConllParserException {
        int index = 0;
        
        SGraph desiredPSDSupertag = new IsiAmrInputCodec().read("(i<root> / --LEX--  :RHEM-of (j<mod>))");
        
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);
            if (psdEntry.getLemma().equals("#Neg")){ // psdEntry is Negation word
                // find verb or thing that is negated in DM: could be none, therefore use Optional
                // outgoing dep. edges in DM from negation: if it's an APPmod edge, its target is the negated thing
                Optional<AmConllEntry> potential_argument = dmDep.getChildren(index).stream().filter(child -> child.getEdgeLabel().equals("APP_mod")).findFirst();
                if (potential_argument.isPresent()) {
                    AmConllEntry dmArgument = potential_argument.get();
                    // found DM negation

                    // PSD
                    //  - currently   --> argument --MOD_mod--> psdEntry (negation)
                    //  - would like:   argument <--APP_mod-- psdEntry (Negation) <--
                    //        plus changed Negation supertag (root source added at mod source place)
                    if (psdEntry.getEdgeLabel().equals("MOD_mod") &&
                            psdEntry.getHead() == dmArgument.getId()) {
                        // fix PSD
                        AlignedAMDependencyTree psdAlignedDeptree = AlignedAMDependencyTree.fromSentence(psdDep);
                        AmConllEntry psdNegated = psdDep.getParent(index); // verb or sth else
                        ApplyModifyGraphAlgebra.Type negatedType = psdAlignedDeptree.getTermTypeAt(psdNegated);

                        SGraph supertag = new IsiAmrInputCodec().read(psdEntry.getDelexSupertag());
                        if (negatedType.equals(ApplyModifyGraphAlgebra.Type.EMPTY_TYPE) && desiredPSDSupertag.equals(supertag)) {
                            // only change if negation has empty type (no upward percolation) // TODO do upward percolation

                            // change head
                            psdEntry.setHead(psdNegated.getHead());
                            psdEntry.setEdgeLabel(psdNegated.getEdgeLabel());
                            // flip edge
                            psdNegated.setHead(psdEntry.getId());
                            psdNegated.setEdgeLabel("APP_mod");

                            // supertag:
                            //   negation supertag has additional source at root!
                            psdEntry.setDelexSupertag("(i / --LEX--  :RHEM-of (j<root, mod>))");
                            // TODO THIS IS NOT WORKING RIGHT NOW ('Difference in PSD':
                            // old: :RHEM (u_1590 / "3@@i_14@@~~_")  new:  :RHEM (i / "3@@i@@~~_")

                            // type
                            // TODO fix type
                        }


                    }

                    // PAS TODO fix pas!
                    // DEBUG TODO maybe look at what is actually negated this way? only verbs?
                } // found negated argument

            } // found #Neg lemma
            index++;
        } // for psdEntry
    }


}
