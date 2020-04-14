package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.internal.$Gson$Types;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.SGraphConverter;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.IntList;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static de.saar.coli.amrtagging.AlignedAMDependencyTree.decodeNode;

public class ModifyPrepsInDependencyTrees {

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

    private int preps220 = 0;
    private int preps220Fixed = 0;
    private int failedPreps220Fixes = 0;
    private int preps020 = 0;
    private int preps020FixedDM = 0;
    private int preps020FixedPSD = 0;
    private int failedPreps020Fixes = 0;
    private int noUniqueEdgeDM020 = 0;
    private int needToPercolateSourcesDM020 = 0;
    private int sourceNotInGraphDM020 = 0;
    private int noUniqueEdgePSD020 = 0;
    private int needToPercolateSourcesPSD020 = 0;
    private int sourceNotInGraphPSD020 = 0;
    private int mods = 0;
    private int apps = 0;
    private int noUniqueEdge = 0;
    private int needToPercolateSources = 0;
    private int sourceNotInGraph = 0;
    private Counter<Type> typesToPercolate = new Counter<>();
    public Counter<String> failLogger = new Counter<>();

    /**
     *
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws ParserException
     * @throws AlignedAMDependencyTree.ConllParserException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, AlignedAMDependencyTree.ConllParserException, ParserException, Exception {
        //just getting command line args
        ModifyPrepsInDependencyTrees cli = new ModifyPrepsInDependencyTrees();
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

        ModifyPrepsInDependencyTrees treeModifier = new ModifyPrepsInDependencyTrees();

        for (String id : decomposedIDs) {
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
            treeModifier.fixPreps220(psdDep, dmDep, pasDep);
            //treeModifier.fixPreps020(psdDep, dmDep, pasDep);


            SGraph newdmSGraph = null;
            SGraph newpsdSGraph = null;
            SGraph newpasSGraph = null;
            try {
                newdmSGraph = AlignedAMDependencyTree.fromSentence(dmDep).evaluate(true);
                onlyIndicesAsLabels(newdmSGraph);
                newpsdSGraph = AlignedAMDependencyTree.fromSentence(psdDep).evaluate(true);
                onlyIndicesAsLabels(newpsdSGraph);
                newpasSGraph = AlignedAMDependencyTree.fromSentence(pasDep).evaluate(true);
                onlyIndicesAsLabels(newpasSGraph);
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
            } catch (Exception e) {
                treeModifier.failedPreps220Fixes++;
                System.err.println(psdDep);
                e.printStackTrace();
            }



            newAmDM.add(dmDep);
            newAmPAS.add(pasDep);
            newAmPSD.add(psdDep);
        }

        AmConllSentence.write(new OutputStreamWriter(new FileOutputStream(cli.outputPath+"/dm.amconll"), StandardCharsets.UTF_8.newEncoder()), newAmDM);
        AmConllSentence.write(new OutputStreamWriter(new FileOutputStream(cli.outputPath+"/pas.amconll"), StandardCharsets.UTF_8.newEncoder()), newAmPAS);
        AmConllSentence.write(new OutputStreamWriter(new FileOutputStream(cli.outputPath+"/psd.amconll"), StandardCharsets.UTF_8.newEncoder()), newAmPSD);

        System.out.println("Prepositions (220):");
        System.out.println(treeModifier.preps220);
        System.out.println("APP operations in PSD:");
        System.out.println(treeModifier.apps);
        System.out.println("MOD operations in PSD:");
        System.out.println(treeModifier.mods);
        System.out.println();
        System.out.println("Fixed (in PSD):");
        System.out.println(treeModifier.preps220Fixed-treeModifier.failedPreps220Fixes);
        System.out.println("Could not identify edge:");
        System.out.println(treeModifier.noUniqueEdge);
        System.out.println("Source not in graph:");
        System.out.println(treeModifier.sourceNotInGraph);
        System.out.println("Would need to percolate sources:");
        System.out.println(treeModifier.needToPercolateSources);

        System.out.println("Prepositions (020):");
        System.out.println(treeModifier.preps020);
        System.out.println("Fixed (in PSD):");
        System.out.println(treeModifier.preps020FixedPSD-treeModifier.failedPreps220Fixes);
        System.out.println("Could not identify edge:");
        System.out.println(treeModifier.noUniqueEdgePSD020);
        System.out.println("Source not in graph:");
        System.out.println(treeModifier.sourceNotInGraphPSD020);
        System.out.println("Would need to percolate sources:");
        System.out.println(treeModifier.needToPercolateSourcesPSD020);
        
        System.out.println("Fixed (in DM):");
        System.out.println(treeModifier.preps020FixedDM-treeModifier.failedPreps220Fixes);
        System.out.println("Could not identify edge:");
        System.out.println(treeModifier.noUniqueEdgeDM020);
        System.out.println("Source not in graph:");
        System.out.println(treeModifier.sourceNotInGraphDM020);
        System.out.println("Would need to percolate sources:");
        System.out.println(treeModifier.needToPercolateSourcesDM020);

        treeModifier.typesToPercolate.printAllSorted();
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

    public void fixPreps220(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParserException, ParseException {
        int index = 0;
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);

            if (psdEntry.getPos().equals("IN") || psdEntry.getPos().equals("TO")) {
                List<AmConllEntry> dmChildren = dmDep.getChildren(index);
                List<AmConllEntry> pasChildren = pasDep.getChildren(index);
                //TODO make below check the pattern check
                if (dmChildren.size()==1 && dmChildren.get(0).getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)
                    && pasChildren.size()==1 && pasChildren.get(0).getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)
                    && dmEntry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)
                    && pasEntry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)
                    && psdEntry.getEdgeLabel().equals(AmConllEntry.IGNORE)) {

                    //we count all of these as matching the preposition pattern
                    preps220++;

                    // now we try to fix them
                    // first find edge
                    int dmLeft = Math.min(dmEntry.getHead(), dmChildren.get(0).getId());
                    int dmRight = Math.max(dmEntry.getHead(), dmChildren.get(0).getId());
                    int pasLeft = Math.min(pasEntry.getHead(), pasChildren.get(0).getId());
                    int pasRight = Math.max(pasEntry.getHead(), pasChildren.get(0).getId());
//                    IntList matchingEdges = HeadAndConstituentAnalysis.getHeadMatchEdges(psdDep, dmDep, pasDep,
//                            dmLeft, dmRight, pasLeft, pasRight);
                    // just look at PAS structures for simplicity
                    IntList matchingEdges = HeadAndConstituentAnalysis.getHeadMatchEdges(psdDep, pasDep, pasDep,
                            pasLeft, pasRight, pasLeft, pasRight);

                    if (matchingEdges.size() == 1) {
                        AmConllEntry psdEdgeTarget = psdDep.get(matchingEdges.getInt(0) - 1);
                        AmConllEntry psdEdgeOrigin = psdDep.get(psdEdgeTarget.getHead() - 1);

                        if (psdEdgeTarget.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                            mods++;
                            String modSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                            if (Type.EMPTY_TYPE.equals(psdEdgeTarget.getType().getRequest(modSourcePSD))) {
                                if (psdEdgeTarget.delexGraph().getNodeForSource(modSourcePSD) != null) {
                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeTarget.delexGraph(), modSourcePSD);
                                    //TODO get DM sources -- EDIT: for now keep psd sources

                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + modSourcePSD);
                                    graphAndEdge.right.addSource("prep", graphAndEdge.right.getNodeForSource("root")); // add prep source at root node
                                    psdEntry.setDelexSupertag(graphAndEdge.right.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("(prep," + modSourcePSD + ")"));


                                    psdEdgeTarget.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    psdEdgeTarget.setType(psdEdgeTarget.getType().performApply(modSourcePSD));//TODO this may cause error
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    psdEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+"prep");
                                    preps220Fixed++;
                                    failLogger.add("220 success");
                                } else {
                                    System.err.println(psdDep);
                                    System.err.println(psdEdgeTarget.getId());
                                    sourceNotInGraph++;
                                    failLogger.add("220 source not in graph");
                                }
                            } else {
                                typesToPercolate.add(psdEdgeOrigin.getType().getRequest(modSourcePSD));
                                needToPercolateSources++;
                                failLogger.add("220 need to percolate sources");
                            }
                        } else {
                            apps++;
                            String appSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
                            if (Type.EMPTY_TYPE.equals(psdEdgeOrigin.getType().getRequest(appSourcePSD))) {
                                if (psdEdgeOrigin.delexGraph().getNodeForSource(appSourcePSD) != null) {
                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeOrigin.delexGraph(), appSourcePSD);
                                    //TODO get DM sources -- EDIT: for now keep psd sources

                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + "prep");
                                    graphAndEdge.right.addSource("prep", graphAndEdge.right.getNodeForSource("root")); // add prep source at root node
                                    psdEntry.setDelexSupertag(graphAndEdge.right.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("(prep," + appSourcePSD + ")"));

                                    psdEdgeOrigin.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    psdEdgeOrigin.setType(psdEdgeOrigin.getType().performApply(appSourcePSD));// this only works as long as we don't have to percolate types
                                    //TODO line above may cause error
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    preps220Fixed++;
                                    failLogger.add("220 success");
                                } else {
                                    sourceNotInGraph++;
                                    failLogger.add("220 source not in graph");
                                }
                            } else {
                                typesToPercolate.add(psdEdgeOrigin.getType().getRequest(appSourcePSD));
                                needToPercolateSources++;
                                failLogger.add("220 need to percolate sources");
                            }
                        }

                    } else {
                        noUniqueEdge++;
                        failLogger.add("220 no unique edge");
                    }

                }
            }

            index++;
        }
    }

    public void fixPreps020(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParserException, ParseException {
        int index = 0;
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);

            if (psdEntry.getPos().equals("IN") || psdEntry.getPos().equals("TO")) {
                List<AmConllEntry> pasChildren = pasDep.getChildren(index);
                if (dmEntry.getEdgeLabel().equals(AmConllEntry.IGNORE)
                        && pasChildren.size()==1 && pasChildren.get(0).getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)
                        && pasEntry.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)
                        && psdEntry.getEdgeLabel().equals(AmConllEntry.IGNORE)) {

                    //we count all of these as matching the preposition pattern
                    preps020++;

                    // now we try to fix them
                    // first find edge
                    int pasLeft = Math.min(pasEntry.getHead(), pasChildren.get(0).getId());
                    int pasRight = Math.max(pasEntry.getHead(), pasChildren.get(0).getId());
                    AmConllEntry dmEdgeTarget = getMatchingEdge(dmDep, pasLeft, pasRight);
                    AmConllEntry psdEdgeTarget = getMatchingEdge(psdDep, pasLeft, pasRight);

                    if (psdEdgeTarget != null && !psdDep.get(psdEdgeTarget.getHead() - 1).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                        AmConllEntry psdEdgeOrigin = psdDep.get(psdEdgeTarget.getHead() - 1);

                        if (psdEdgeTarget.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                            String modSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                            if (Type.EMPTY_TYPE.equals(psdEdgeTarget.getType().getRequest(modSourcePSD))) {
                                if (psdEdgeTarget.delexGraph().getNodeForSource(modSourcePSD) != null) {
                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeTarget.delexGraph(), modSourcePSD);
                                    //TODO get DM sources -- EDIT: for now keep psd sources

                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + modSourcePSD);
                                    graphAndEdge.right.addSource("prep", graphAndEdge.right.getNodeForSource("root")); // add prep source at root node
                                    psdEntry.setDelexSupertag(graphAndEdge.right.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("(prep," + modSourcePSD + ")"));


                                    psdEdgeTarget.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    psdEdgeTarget.setType(psdEdgeTarget.getType().performApply(modSourcePSD));
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    psdEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+"prep");
                                    preps020FixedPSD++;
                                    failLogger.add("020 success PSD");
                                } else {
                                    sourceNotInGraphPSD020++;
                                    failLogger.add("020 source not in graph PSD");
                                }
                            } else {
                                typesToPercolate.add(psdEdgeOrigin.getType().getRequest(modSourcePSD));
                                needToPercolateSourcesPSD020++;
                                failLogger.add("020 need to percolate sources PSD");
                            }
                        } else {
                            String appSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
                            if (psdEdgeOrigin.getType() == null) {
                                System.err.println(psdDep);
                                System.err.println(pasDep);
                                System.err.println(pasLeft);
                                System.err.println(pasRight);
                            }
                            if (Type.EMPTY_TYPE.equals(psdEdgeOrigin.getType().getRequest(appSourcePSD))) {
                                if (psdEdgeOrigin.delexGraph().getNodeForSource(appSourcePSD) != null) {
                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeOrigin.delexGraph(), appSourcePSD);
                                    //TODO get DM sources -- EDIT: for now keep psd sources

                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + "prep");
                                    graphAndEdge.right.addSource("prep", graphAndEdge.right.getNodeForSource("root")); // add prep source at root node
                                    psdEntry.setDelexSupertag(graphAndEdge.right.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("(prep," + appSourcePSD + ")"));

                                    psdEdgeOrigin.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    psdEdgeOrigin.setType(psdEdgeOrigin.getType().performApply(appSourcePSD));// this only works as long as we don't have to percolate types

                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    preps020FixedPSD++;
                                    failLogger.add("020 success PSD");
                                } else {
                                    sourceNotInGraphPSD020++;
                                    failLogger.add("020 source not in graph PSD");
                                }
                            } else {
                                typesToPercolate.add(psdEdgeOrigin.getType().getRequest(appSourcePSD));
                                needToPercolateSourcesPSD020++;
                                failLogger.add("020 need to percolate sources PSD");
                            }
                        }

                    } else {
                        noUniqueEdgePSD020++;
                        failLogger.add("020 no unique edge PSD");
                    }


                    if (dmEdgeTarget != null && !dmDep.get(dmEdgeTarget.getHead() - 1).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                        AmConllEntry dmEdgeOrigin = dmDep.get(dmEdgeTarget.getHead() - 1);

                        if (dmEdgeTarget.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                            String modSourceDM = dmEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                            if (Type.EMPTY_TYPE.equals(dmEdgeTarget.getType().getRequest(modSourceDM))) {
                                if (dmEdgeTarget.delexGraph().getNodeForSource(modSourceDM) != null) {
                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(dmEdgeTarget.delexGraph(), modSourceDM);
                                    //TODO get DM sources -- EDIT: for now keep dm sources

                                    dmEntry.setHead(dmEdgeOrigin.getId());
                                    dmEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + modSourceDM);
                                    graphAndEdge.right.addSource("prep", graphAndEdge.right.getNodeForSource("root")); // add prep source at root node
                                    dmEntry.setDelexSupertag(graphAndEdge.right.toIsiAmrStringWithSources());
                                    dmEntry.setType(new Type("(prep," + modSourceDM + ")"));


                                    dmEdgeTarget.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    dmEdgeTarget.setType(dmEdgeTarget.getType().performApply(modSourceDM));
                                    dmEdgeTarget.setHead(dmEntry.getId());
                                    dmEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+"prep");
                                    preps020FixedDM++;
                                    failLogger.add("020 success DM");
                                } else {
                                    sourceNotInGraphDM020++;
                                    failLogger.add("020 source not in graph DM");
                                }
                            } else {
                                typesToPercolate.add(dmEdgeOrigin.getType().getRequest(modSourceDM));
                                needToPercolateSourcesDM020++;
                                failLogger.add("020 need to percolate sources DM");
                            }
                        } else {
                            String appSourceDM = dmEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
                            if (dmEdgeOrigin.getType() == null) {
                                System.err.println(dmDep);
                                System.err.println(pasDep);
                                System.err.println(pasLeft);
                                System.err.println(pasRight);
                            }
                            if (Type.EMPTY_TYPE.equals(dmEdgeOrigin.getType().getRequest(appSourceDM))) {
                                if (dmEdgeOrigin.delexGraph().getNodeForSource(appSourceDM) != null) {
                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(dmEdgeOrigin.delexGraph(), appSourceDM);
                                    //TODO get DM sources -- EDIT: for now keep dm sources

                                    dmEntry.setHead(dmEdgeOrigin.getId());
                                    dmEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + "prep");
                                    graphAndEdge.right.addSource("prep", graphAndEdge.right.getNodeForSource("root")); // add prep source at root node
                                    dmEntry.setDelexSupertag(graphAndEdge.right.toIsiAmrStringWithSources());
                                    dmEntry.setType(new Type("(prep," + appSourceDM + ")"));

                                    dmEdgeOrigin.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    dmEdgeOrigin.setType(dmEdgeOrigin.getType().performApply(appSourceDM));// this only works as long as we don't have to percolate types

                                    dmEdgeTarget.setHead(dmEntry.getId());
                                    preps020FixedDM++;
                                    failLogger.add("020 success DM");
                                } else {
                                    sourceNotInGraphDM020++;
                                    failLogger.add("020 source not in graph DM");
                                }
                            } else {
                                typesToPercolate.add(dmEdgeOrigin.getType().getRequest(appSourceDM));
                                needToPercolateSourcesDM020++;
                                failLogger.add("020 need to percolate sources DM");
                            }
                        }

                    } else {
                        failLogger.add("020 no unique edge DM");
                        noUniqueEdgeDM020++;
                    }
                }
            }

            index++;
        }
    }

    /**
     * returns entry that is target of edge between given IDs. Returns null if no such edge exists.
     * @param depWithEdge
     * @param leftID
     * @param rightID
     * @return
     */
    private static AmConllEntry getMatchingEdge(AmConllSentence depWithEdge, int leftID, int rightID) {
        AmConllEntry leftEntry = depWithEdge.get(leftID-1);
        if (leftEntry.getHead() == rightID) {
            return leftEntry;
        }
        AmConllEntry rightEntry = depWithEdge.get(rightID-1);
        if (rightEntry.getHead() == leftID) {
            return rightEntry;
        }
        return null;
    }

    /**
     * returns a graph with the edge to sourceOnEdge removed, and a graph containing just that edge (with empty root node)
     * @param graph
     * @return
     */
    private static Pair<SGraph, SGraph> splitEdgeFromGraph(SGraph graph, String sourceOnEdge) {
        SGraph retGraph = new SGraph();
        SGraph retEdge = new SGraph();
        String slotNode = graph.getNodeForSource(sourceOnEdge);
        for (GraphNode node : graph.getGraph().vertexSet()) {
            Collection<String> sources = graph.getSourcesAtNode(node.getName());
            if (sources.contains("root")) {
                retGraph.addNode(node.getName(), node.getLabel());
                retEdge.addNode(node.getName(), null);
                for (String source : sources) {
                    retGraph.addSource(source, node.getName());
                }
                retEdge.addSource("root", node.getName());
            } else if (sources.contains(sourceOnEdge)) {
                if (sources.size() > 1) {
                    System.err.println("More than one source on prep edge!");
                }
                if (node.getLabel() != null) {
                    System.err.println("non-null label found: "+node.getLabel());
                }
                retEdge.addNode(node.getName(), node.getLabel());
                retEdge.addSource(sourceOnEdge, node.getName());
            } else {
                retGraph.addNode(node.getName(), node.getLabel());
                for (String source : sources) {
                    retGraph.addSource(source, node.getName());
                }
            }
        }
        for (GraphEdge edge : graph.getGraph().edgeSet()) {
            if (edge.getSource().getName().equals(slotNode) || edge.getTarget().getName().equals(slotNode)) {
                retEdge.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
            } else {
                retGraph.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
            }
        }
        return new Pair<>(retGraph, retEdge);
    }

}
