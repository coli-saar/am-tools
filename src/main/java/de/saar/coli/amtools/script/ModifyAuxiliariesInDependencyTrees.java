package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.*;
import java.util.*;

import static de.saar.coli.amrtagging.AlignedAMDependencyTree.decodeNode;

public class ModifyAuxiliariesInDependencyTrees {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\dm\\dev\\dev.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\pas\\dev\\dev.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\psd\\dev\\dev.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\dm\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\pas\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\new_psd_preprocessing\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--outputPath", "-o"}, description = "Path to the output folder")
    private String outputPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\";



    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

    private int temporalAuxiliaries = 0;
    private int unusualType = 0;
    private int temporalAuxiliariesFixed = 0;

    Counter<String> failLogger = new Counter<>();

    /**
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws ParserException
     * @throws AlignedAMDependencyTree.ConllParserException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, AlignedAMDependencyTree.ConllParserException, ParserException, Exception {
        //just getting command line args
        ModifyAuxiliariesInDependencyTrees cli = new ModifyAuxiliariesInDependencyTrees();
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

        ModifyAuxiliariesInDependencyTrees treeModifier = new ModifyAuxiliariesInDependencyTrees();

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
                treeModifier.fixTemporalAuxiliaries(psdDep, dmDep, pasDep);


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

        System.out.println("temp aux:");
        System.out.println(treeModifier.temporalAuxiliaries);
        System.out.println("unusual types:");
        System.out.println(treeModifier.unusualType);
        System.out.println("Fixed (in both DM and PSD):");
        System.out.println(treeModifier.temporalAuxiliariesFixed);


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


    public void fixTemporalAuxiliaries(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep)
            throws ParseException {
        int index = 0;

        String desiredSupertagTemplate = "(i<root, --SOURCE-->)";


        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);
//            if (FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, dmGraph, pasGraph, psdGraph, psdEntry.getId()).equals("040")
//                    && pasEntry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, psdEntry.getId());
            if (pattern.equals("040") && pasEntry.getPos().startsWith("V")) {
                this.temporalAuxiliaries ++;

                System.out.println("PAS "+psdEntry.getId());
                AMExampleFinder.printExample(pasDep, psdEntry.getId(), 2);

                if (!new Type("(s,o)").equals(pasEntry.getType())) {
                    this.unusualType++;
//                    System.err.println(pasEntry.getId());
//                    System.err.println(pasDep);
                }

                String source = pasEntry.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                String desiredSupertag = desiredSupertagTemplate.replace("--SOURCE--", source);
                Type desiredType = new Type("("+source+")");

                if (psdDep.get(pasEntry.getHead()-1).getEdgeLabel().equals(AmConllEntry.IGNORE)
                    || dmDep.get(pasEntry.getHead()-1).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                    failLogger.add("aux head ignored");
                    continue;
                }

                System.out.println("PSD "+psdEntry.getId());
                AMExampleFinder.printExample(psdDep, psdEntry.getId(), 2);

                psdEntry.setDelexSupertag(desiredSupertag);
                psdEntry.setType(desiredType);
                psdEntry.setHead(pasEntry.getHead());
                psdEntry.setEdgeLabel(pasEntry.getEdgeLabel());

                System.out.println("PSD after "+psdEntry.getId());
                AMExampleFinder.printExample(psdDep, psdEntry.getId(), 2);

                System.out.println("DM "+psdEntry.getId());
                AMExampleFinder.printExample(dmDep, psdEntry.getId(), 2);

                dmEntry.setDelexSupertag(desiredSupertag);
                dmEntry.setType(desiredType);
                dmEntry.setHead(pasEntry.getHead());
                dmEntry.setEdgeLabel(pasEntry.getEdgeLabel());
                failLogger.add("aux success");
                temporalAuxiliariesFixed++;

                System.out.println("DM after "+psdEntry.getId());
                AMExampleFinder.printExample(dmDep, psdEntry.getId(), 2);
                System.out.println();

            }
            index++;
        } // for psdEntry
    }


}
