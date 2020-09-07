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
            //treeModifier.fixPreps220(psdDep, dmDep, pasDep);
            treeModifier.fixPreps020(psdDep, dmDep, pasDep);


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

        AmConllSentence.writeToFile(cli.outputPath+"/dm.amconll", newAmDM);
        AmConllSentence.writeToFile(cli.outputPath+"/pas.amconll", newAmPAS);
        AmConllSentence.writeToFile(cli.outputPath+"/psd.amconll", newAmPSD);

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

    //CLEANUP there is massive code duplcation below, with essentially the same code for PSD 220, PSD 020 and DM 020

    public void fixPreps220(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParserException, ParseException {
        int index = 0;
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);

            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, psdEntry.getId());
            if (pattern.equals("220")) {
                ModifyDependencyTreesDetCopNeg.patternCoverageLogger.add("220 pattern");
                if (psdEntry.getPos().equals("IN") || psdEntry.getPos().equals("TO")) {
                    ModifyDependencyTreesDetCopNeg.patternCoverageLogger.add("220 restricted");

                    //we count all of these as matching the preposition pattern
                    preps220++;

                    // now we try to fix them
                    // first find PSD edge
                    AmConllEntry dmAppChild = getFirstAppChild(dmDep, psdEntry.getId());
                    int dmLeft = Math.min(dmEntry.getHead(), dmAppChild.getId());
                    int dmRight = Math.max(dmEntry.getHead(), dmAppChild.getId());
                    AmConllEntry pasAppChild = getFirstAppChild(pasDep, psdEntry.getId());
                    int pasLeft = Math.min(pasEntry.getHead(), pasAppChild.getId());
                    int pasRight = Math.max(pasEntry.getHead(), pasAppChild.getId());
//                    IntList matchingEdges = HeadAndConstituentAnalysis.getHeadMatchEdges(psdDep, dmDep, pasDep,
//                            dmLeft, dmRight, pasLeft, pasRight);
                    // just look at PAS structures for simplicity
                    IntList matchingEdges = HeadAndConstituentAnalysis.getHeadMatchEdges(psdDep, pasDep, pasDep,
                            pasLeft, pasRight, pasLeft, pasRight);

                    //get PAS source names for later
                    String pasHeadSource = pasEntry.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                    String pasChildSource = pasAppChild.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());


                    //check that there is a unique PSD edge
                    if (matchingEdges.size() == 1) {
                        AmConllEntry psdEdgeTarget = psdDep.get(matchingEdges.getInt(0) - 1);
                        AmConllEntry psdEdgeOrigin = psdDep.get(psdEdgeTarget.getHead() - 1);

                        // case distinction: mod or app edge in PSD?
                        if (psdEdgeTarget.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                            mods++;
                            String modSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());

                            //set up getting the term type, which we need to rule out having to pass up sources.
                            AlignedAMDependencyTree psdAlignedDepTree;
                            try {
                                psdAlignedDepTree = AlignedAMDependencyTree.fromSentence(psdDep);
                            } catch (AlignedAMDependencyTree.ConllParserException e) {
                                failLogger.add("020 error getting aligned dep tree");
                                continue;
                            }

                            if (psdAlignedDepTree.getTermTypeAt(psdEdgeTarget).getAllSources().size() == 1) {
                                if (psdEdgeTarget.delexGraph().getNodeForSource(modSourcePSD) != null) {

                                    //now we can actually fix the pattern

                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeTarget.delexGraph(), modSourcePSD);

                                    //edge constant and its Conll entry. Create the constant first.
                                    SGraph edgeConst = graphAndEdge.right;
                                    // sources. First, rename old psd source to PAS head source.
                                    edgeConst = edgeConst.renameSource(modSourcePSD, pasHeadSource);
                                    // add PAS child source at root node
                                    edgeConst.addSource(pasChildSource, edgeConst.getNodeForSource("root"));
                                    // now set Conll entry correctly
                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + pasHeadSource);
                                    psdEntry.setDelexSupertag(edgeConst.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("("+pasChildSource+"," + pasHeadSource + ")"));

                                    // graph without edge. Constant is already correct, just need to update Conll entry
                                    psdEdgeTarget.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    //don't have the old PSD source anymore
                                    psdEdgeTarget.setType(psdEdgeTarget.getType().performApply(modSourcePSD));//CLEANUP this may cause error (fixed now?)
                                    // the incoming edge is the apply from the edge constant
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    psdEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+pasChildSource);
                                    preps220Fixed++;
                                    failLogger.add("220 success");
                                } else {
//                                    System.err.println(psdDep);
//                                    System.err.println(psdEdgeTarget.getId());
                                    sourceNotInGraph++;
                                    failLogger.add("220 source not in graph");
                                }
                            } else {
//                                System.out.println("prep 220 need percolate sources PSD: "+psdEntry.getId());
//                                System.out.println(psdAlignedDepTree.getTermTypeAt(psdEdgeTarget));
//                                AMExampleFinder.printExample(psdDep, psdEntry.getId(), 15);
//                                System.out.println();
                                typesToPercolate.add(psdEdgeOrigin.getType().getRequest(modSourcePSD));
                                needToPercolateSources++;
                                failLogger.add("220 need to percolate sources");
                            }
                        } else {
                            apps++;
                            String appSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
                            if (Type.EMPTY_TYPE.equals(psdEdgeOrigin.getType().getRequest(appSourcePSD))) {
                                if (psdEdgeOrigin.delexGraph().getNodeForSource(appSourcePSD) != null) {

                                    //now we can fix the pattern
//                                    System.out.println("PAS APP "+psdEntry.getId());
//                                    AMExampleFinder.printExample(pasDep, psdEntry.getId(), 5);
//                                    System.out.println("PSD APP "+psdEntry.getId());
//                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);


                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeOrigin.delexGraph(), appSourcePSD);

                                    //edge constant and its Conll entry. Create the constant first.
                                    SGraph edgeConst = graphAndEdge.right;
                                    // sources. First, get old psd source to PAS child source.
                                    edgeConst = edgeConst.renameSource(appSourcePSD, pasChildSource);
                                    // add PAS head source at root node
                                    edgeConst.addSource(pasHeadSource, edgeConst.getNodeForSource("root"));
                                    // now set Conll entry correctly
                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + pasHeadSource);
                                    psdEntry.setDelexSupertag(edgeConst.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("("+pasHeadSource+"," + pasChildSource + ")"));

                                    //update entry where we took the edge out of the constant
                                    psdEdgeOrigin.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    // this only works as long as we don't have to percolate types
                                    psdEdgeOrigin.setType(psdEdgeOrigin.getType().performApply(appSourcePSD));//CLEANUP this may cause error (fixed now?)

                                    //update child head information (is now app incoming from edge constant)
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    psdEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+pasChildSource);
                                    preps220Fixed++;
                                    failLogger.add("220 success");
//                                    System.out.println("PSD APP "+psdEntry.getId() + " after");
//                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);
//                                    System.out.println();
                                } else {
                                    sourceNotInGraph++;
                                    failLogger.add("220 source not in graph");
                                }
                            } else {
//                                System.out.println("prep 220 need percolate sources PSD: "+psdEntry.getId());
//                                System.out.println(psdEdgeOrigin.getType().getRequest(appSourcePSD));
//                                AMExampleFinder.printExample(psdDep, psdEntry.getId(), 15);
//                                System.out.println();
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

            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, psdEntry.getId());
            if (pattern.equals("020")) {
                ModifyDependencyTreesDetCopNeg.patternCoverageLogger.add("020 pattern");
                if (psdEntry.getPos().equals("IN") || psdEntry.getPos().equals("TO")) {
                    ModifyDependencyTreesDetCopNeg.patternCoverageLogger.add("020 restricted");

                    //we count all of these as matching the preposition pattern
                    preps020++;
//                    System.out.println("PAS "+psdEntry.getId());
//                    AMExampleFinder.printExample(pasDep, psdEntry.getId(), 5);

                    // now we try to fix them
                    // first find edge
                    AmConllEntry pasAppChild = getFirstAppChild(pasDep, pasEntry.getId());
                    int pasLeft = Math.min(pasEntry.getHead(), pasAppChild.getId());
                    int pasRight = Math.max(pasEntry.getHead(), pasAppChild.getId());
                    AmConllEntry dmEdgeTarget = getMatchingEdge(dmDep, pasLeft, pasRight);
                    AmConllEntry psdEdgeTarget = getMatchingEdge(psdDep, pasLeft, pasRight);


                    //get PAS source names for later
                    String pasHeadSource = pasEntry.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                    String pasChildSource = pasAppChild.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());

                    if (psdEdgeTarget != null && !psdDep.get(psdEdgeTarget.getHead() - 1).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                        AmConllEntry psdEdgeOrigin = psdDep.get(psdEdgeTarget.getHead() - 1);

                        if (psdEdgeTarget.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                            String modSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
                            AlignedAMDependencyTree psdAlignedDepTree;
                            try {
                                psdAlignedDepTree = AlignedAMDependencyTree.fromSentence(psdDep);
                            } catch (AlignedAMDependencyTree.ConllParserException e) {
                                failLogger.add("020 error getting aligned dep tree");
                                continue;
                            }

                            if (psdAlignedDepTree.getTermTypeAt(psdEdgeTarget).getAllSources().size() == 1) {
                                if (psdEdgeTarget.delexGraph().getNodeForSource(modSourcePSD) != null) {
                                    //now we can actually fix the pattern
//                                    System.out.println("PSD MOD "+psdEntry.getId());
//                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);

                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeTarget.delexGraph(), modSourcePSD);

                                    //edge constant and its Conll entry. Create the constant first.
                                    SGraph edgeConst = graphAndEdge.right;
                                    // sources. First, rename old psd source to PAS head source.
                                    edgeConst = edgeConst.renameSource(modSourcePSD, pasHeadSource);
                                    // add PAS child source at root node
                                    edgeConst.addSource(pasChildSource, edgeConst.getNodeForSource("root"));
                                    // now set Conll entry correctly
                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + pasHeadSource);
                                    psdEntry.setDelexSupertag(edgeConst.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("("+pasChildSource+"," + pasHeadSource + ")"));

                                    // graph without edge. Constant is already correct, just need to update Conll entry
                                    psdEdgeTarget.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    //don't have the old PSD source anymore
                                    psdEdgeTarget.setType(psdEdgeTarget.getType().performApply(modSourcePSD));//CLEANUP this may cause error (fixed now?)
                                    // the incoming edge is the apply from the edge constant
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    psdEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+pasChildSource);
                                    preps020FixedPSD++;
                                    failLogger.add("020 success PSD");
//                                    System.out.println("PSD MOD after "+psdEntry.getId());
//                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);
                                } else {
                                    sourceNotInGraphPSD020++;
                                    failLogger.add("020 source not in graph PSD");
                                }
                            } else {
//                                System.out.println("prep 020 need percolate sources PSD: "+psdEntry.getId());
//                                System.out.println(psdAlignedDepTree.getTermTypeAt(psdEdgeTarget));
//                                AMExampleFinder.printExample(psdDep, psdEntry.getId(), 15);
//                                System.out.println();
                                typesToPercolate.add(psdEdgeOrigin.getType().getRequest(modSourcePSD));
                                needToPercolateSourcesPSD020++;
                                failLogger.add("020 need to percolate sources PSD");
                            }
                        } else {
                            String appSourcePSD = psdEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());

                            if (Type.EMPTY_TYPE.equals(psdEdgeOrigin.getType().getRequest(appSourcePSD))) {
                                if (psdEdgeOrigin.delexGraph().getNodeForSource(appSourcePSD) != null) {
                                    //now we can fix the pattern
//                                    System.out.println("PSD APP "+psdEntry.getId());
//                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);


                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(psdEdgeOrigin.delexGraph(), appSourcePSD);

                                    //edge constant and its Conll entry. Create the constant first.
                                    SGraph edgeConst = graphAndEdge.right;
                                    // sources. First, get old psd source to PAS child source.
                                    edgeConst = edgeConst.renameSource(appSourcePSD, pasChildSource);
                                    // add PAS head source at root node
                                    edgeConst.addSource(pasHeadSource, edgeConst.getNodeForSource("root"));
                                    // now set Conll entry correctly
                                    psdEntry.setHead(psdEdgeOrigin.getId());
                                    psdEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + pasHeadSource);
                                    psdEntry.setDelexSupertag(edgeConst.toIsiAmrStringWithSources());
                                    psdEntry.setType(new Type("("+pasHeadSource+"," + pasChildSource + ")"));

                                    //update entry where we took the edge out of the constant
                                    psdEdgeOrigin.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    // this only works as long as we don't have to percolate types
                                    psdEdgeOrigin.setType(psdEdgeOrigin.getType().performApply(appSourcePSD));//CLEANUP this may cause error (fixed now?)

                                    //update child head information (is now app incoming from edge constant)
                                    psdEdgeTarget.setHead(psdEntry.getId());
                                    psdEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+pasChildSource);
                                    preps020FixedPSD++;
                                    failLogger.add("020 success PSD");
//                                    System.out.println("PSD APP after"+psdEntry.getId());
//                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);
                                } else {
                                    sourceNotInGraphPSD020++;
                                    failLogger.add("020 source not in graph PSD");
                                }
                            } else {
//                                System.out.println("prep 020 need percolate sources PSD: "+psdEntry.getId());
//                                System.out.println(psdEdgeOrigin.getType().getRequest(appSourcePSD));
//                                AMExampleFinder.printExample(psdDep, psdEntry.getId(), 15);
//                                System.out.println();
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
                            AlignedAMDependencyTree dmAlignedDepTree;
                            try {
                                dmAlignedDepTree = AlignedAMDependencyTree.fromSentence(dmDep);
                            } catch (AlignedAMDependencyTree.ConllParserException e) {
                                failLogger.add("020 error getting aligned dep tree");
                                continue;
                            }

                            if (dmAlignedDepTree.getTermTypeAt(dmEdgeTarget).getAllSources().size() == 1) {
                                if (dmEdgeTarget.delexGraph().getNodeForSource(modSourceDM) != null) {
                                    //now we can actually fix the pattern
//                                    System.out.println("DM MOD "+psdEntry.getId());
//                                    AMExampleFinder.printExample(dmDep, psdEntry.getId(), 5);

                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(dmEdgeTarget.delexGraph(), modSourceDM);

                                    //edge constant and its Conll entry. Create the constant first.
                                    SGraph edgeConst = graphAndEdge.right;
                                    // sources. First, rename old dm source to PAS head source.
                                    edgeConst = edgeConst.renameSource(modSourceDM, pasHeadSource);
                                    // add PAS child source at root node
                                    edgeConst.addSource(pasChildSource, edgeConst.getNodeForSource("root"));
                                    // now set Conll entry correctly
                                    dmEntry.setHead(dmEdgeOrigin.getId());
                                    dmEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + pasHeadSource);
                                    dmEntry.setDelexSupertag(edgeConst.toIsiAmrStringWithSources());
                                    dmEntry.setType(new Type("("+pasChildSource+"," + pasHeadSource + ")"));

                                    // graph without edge. Constant is already correct, just need to update Conll entry
                                    dmEdgeTarget.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    //don't have the old DM source anymore
                                    dmEdgeTarget.setType(dmEdgeTarget.getType().performApply(modSourceDM));//CLEANUP this may cause error (fixed now?)
                                    // the incoming edge is the apply from the edge constant
                                    dmEdgeTarget.setHead(dmEntry.getId());
                                    dmEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+pasChildSource);
                                    preps020FixedDM++;
                                    failLogger.add("020 success DM");
//                                    System.out.println("DM MOD after "+psdEntry.getId());
//                                    AMExampleFinder.printExample(dmDep, psdEntry.getId(), 5);
                                } else {
                                    sourceNotInGraphDM020++;
                                    failLogger.add("020 source not in graph DM");
                                }
                            } else {
//                                System.out.println("prep 020 need percolate sources DM: "+dmEntry.getId());
//                                System.out.println(dmAlignedDepTree.getTermTypeAt(dmEdgeTarget));
//                                AMExampleFinder.printExample(dmDep, dmEntry.getId(), 15);
//                                System.out.println();
                                typesToPercolate.add(dmEdgeOrigin.getType().getRequest(modSourceDM));
                                needToPercolateSourcesDM020++;
                                failLogger.add("020 need to percolate sources DM");
                            }
                        } else {
                            String appSourceDM = dmEdgeTarget.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
                            if (Type.EMPTY_TYPE.equals(dmEdgeOrigin.getType().getRequest(appSourceDM))) {
                                if (dmEdgeOrigin.delexGraph().getNodeForSource(appSourceDM) != null) {
                                    //now we can fix the pattern
//                                    System.out.println("DM APP "+psdEntry.getId());
//                                    AMExampleFinder.printExample(dmDep, psdEntry.getId(), 5);


                                    Pair<SGraph, SGraph> graphAndEdge = splitEdgeFromGraph(dmEdgeOrigin.delexGraph(), appSourceDM);

                                    //edge constant and its Conll entry. Create the constant first.
                                    SGraph edgeConst = graphAndEdge.right;
                                    // sources. First, get old dm source to PAS child source.
                                    edgeConst = edgeConst.renameSource(appSourceDM, pasChildSource);
                                    // add PAS head source at root node
                                    edgeConst.addSource(pasHeadSource, edgeConst.getNodeForSource("root"));
                                    // now set Conll entry correctly
                                    dmEntry.setHead(dmEdgeOrigin.getId());
                                    dmEntry.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION + pasHeadSource);
                                    dmEntry.setDelexSupertag(edgeConst.toIsiAmrStringWithSources());
                                    dmEntry.setType(new Type("("+pasHeadSource+"," + pasChildSource + ")"));

                                    //update entry where we took the edge out of the constant
                                    dmEdgeOrigin.setDelexSupertag(graphAndEdge.left.toIsiAmrStringWithSources());
                                    // this only works as long as we don't have to percolate types
                                    dmEdgeOrigin.setType(dmEdgeOrigin.getType().performApply(appSourceDM));//CLEANUP this may cause error (fixed now?)

                                    //update child head information (is now app incoming from edge constant)
                                    dmEdgeTarget.setHead(dmEntry.getId());
                                    dmEdgeTarget.setEdgeLabel(ApplyModifyGraphAlgebra.OP_APPLICATION+pasChildSource);
                                    preps020FixedDM++;
                                    failLogger.add("020 success DM");
//                                    System.out.println("DM APP after "+psdEntry.getId());
//                                    AMExampleFinder.printExample(dmDep, psdEntry.getId(), 5);
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

    /**
     * returns the first apply-child of the entry (as ordered in getChildren()), or null if there is none.
     * @param dep dependency tree
     * @param id 1-based
     * @return
     */
    private static AmConllEntry getFirstAppChild(AmConllSentence dep, int id) {
        for (AmConllEntry child : dep.getChildren(id-1)) {
            if (child.getEdgeLabel().startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
                return child;
            }
        }
        return null;
    }

}
