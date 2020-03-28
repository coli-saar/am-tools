package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.saar.coli.amtools.analysis.AmConllComparator;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.algebra.graph.SGraphDrawer;
import de.up.ling.tree.ParseException;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class AllDependencyChanges {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\sdp2014_1015\\data\\2015\\en.dm.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\sdp2014_1015\\data\\2015\\en.pas.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\sdp2014_1015\\data\\2015\\en.psd.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\dm\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\pas\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\uniformify2020\\original_decompositions\\psd\\gold-dev\\gold-dev.amconll";

    @Parameter(names = {"--outputPath", "-o"}, description = "Path to the output folder")
    private String outputPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\dev_03-28\\";

    @Parameter(names = {"--onlyDeterminers"}, description = "only fix determiners (for testing purposes)")
    private boolean onlyDeterminers=false;


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

    private int dmFails = 0;
    private int pasFails = 0;
    private int psdFails = 0;

    private final List<AmConllSentence> intersectedDepsDM = new ArrayList<>();
    private final List<AmConllSentence> intersectedDepsPAS = new ArrayList<>();
    private final List<AmConllSentence> intersectedDepsPSD = new ArrayList<>();

    private void addData(List<AmConllSentence> amDM, List<AmConllSentence> amPAS, List<AmConllSentence> amPSD) {
        Map<String, AmConllSentence> id2amDM = new HashMap<>();
        amDM.stream().forEach(sent -> id2amDM.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPAS = new HashMap<>();
        amPAS.stream().forEach(sent -> id2amPAS.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPSD = new HashMap<>();
        amPSD.stream().forEach(sent -> id2amPSD.put(sent.getId(), sent));
        Set<String> decomposedIDs = Sets.intersectAll(id2amDM.keySet(), id2amPAS.keySet(), id2amPSD.keySet());
        for (String id : decomposedIDs) {
            intersectedDepsDM.add(id2amDM.get(id));
            intersectedDepsPAS.add(id2amPAS.get(id));
            intersectedDepsPSD.add(id2amPSD.get(id));
        }
    }

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
        AllDependencyChanges changer = new AllDependencyChanges();
        JCommander commander = new JCommander(changer);
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }
        if (changer.help) {
            commander.usage();
            return;
        }


        //setup
        new File(changer.outputPath).mkdirs();
//        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
//        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
//        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
//        Graph dmGraph;
//        Graph pasGraph;
//        Graph psdGraph;
        //TODO move this to constructor
        List<AmConllSentence> amDM = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(changer.amconllPathDM), StandardCharsets.UTF_8));
        List<AmConllSentence> amPAS = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(changer.amconllPathPAS), StandardCharsets.UTF_8));
        List<AmConllSentence> amPSD = AmConllSentence.read(new InputStreamReader(
                new FileInputStream(changer.amconllPathPSD), StandardCharsets.UTF_8));
        // add the read files to our changer object
        changer.addData(amDM, amPAS, amPSD);
        System.out.println("Before modifications");
        changer.printComparisons();

        ModifyDependencyTreesDetCopNeg treeModifier = new ModifyDependencyTreesDetCopNeg();


        //error handling below could maybe be done better, but Java is weird about exceptions and lambdas...

        //determiners
        System.out.println("Fixing determiners");
        changer.applyFix(dm -> pas -> psd -> {
            try {
                treeModifier.fixDeterminer(psd, dm, pas);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        });
        changer.printComparisons();
        System.err.println(treeModifier.getDeterminer());
        System.err.println(treeModifier.getDeterminerFixedPSD());
        if (!changer.onlyDeterminers) {


            //temporal auxiliaries
            System.out.println("Fixing temporal auxiliaries");
            ModifyAuxiliariesInDependencyTrees auxTreeFixer = new ModifyAuxiliariesInDependencyTrees();
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    auxTreeFixer.fixTemporalAuxiliaries(psd, dm, pas);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
            changer.printComparisons();

            //prepositions
            System.out.println("Fixing prepositions (220 pattern)");
            ModifyPrepsInDependencyTrees prepTreeFixer = new ModifyPrepsInDependencyTrees();
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    prepTreeFixer.fixPreps220(psd, dm, pas);
                } catch (ParserException | ParseException e) {
                    throw new RuntimeException(e);
                }
            });
            changer.printComparisons();
            System.out.println("Fixing prepositions (020 pattern)");
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    prepTreeFixer.fixPreps020(psd, dm, pas);
                } catch (ParserException | ParseException e) {
                    throw new RuntimeException(e);
                }
            });
            changer.printComparisons();

            //negation
            System.out.println("Fixing negations");
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    treeModifier.fixNegation(psd, dm, pas);
                    treeModifier.fixNever(psd, dm, pas);
                } catch (ParseException | AlignedAMDependencyTree.ConllParserException e) {
                    throw new RuntimeException(e);
                }
            });
            changer.printComparisons();

            //adjective copula
            System.out.println("Fixing adjective copula");
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    treeModifier.fixAdjCopula(psd, dm, pas);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
            changer.printComparisons();

            //binary coordination
            System.out.println("Fixing binary coordination ");
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    treeModifier.fixBinaryConjuction(psd, dm, pas);
                } catch (ParseException | ParserException | AlignedAMDependencyTree.ConllParserException e) {
                    throw new RuntimeException(e);
                }
            });
            changer.printComparisons();

            //PAS-only modifiers
            System.out.println("fixing PAS-only modifiers");
            changer.applyFix(dm -> pas -> psd -> {
                try {
                    treeModifier.fixPASOnlyModifiers(psd, dm, pas);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
            System.err.println(treeModifier.punctuation);
            System.err.println(treeModifier.punctuationAllFixed);
            System.err.println(treeModifier.punctuationFixedDM);
            System.err.println(treeModifier.punctuationFixedPSD);
            changer.printComparisons();

            //TODO check equality!
        }
        System.out.println("DM fails: "+changer.dmFails);
        System.out.println("PAS fails: "+changer.pasFails);
        System.out.println("PSD fails: "+changer.psdFails);


        AmConllSentence.write(new OutputStreamWriter(new FileOutputStream(changer.outputPath+"/dm.amconll"), StandardCharsets.UTF_8), changer.intersectedDepsDM);
        AmConllSentence.write(new OutputStreamWriter(new FileOutputStream(changer.outputPath+"/pas.amconll"), StandardCharsets.UTF_8), changer.intersectedDepsPAS);
        AmConllSentence.write(new OutputStreamWriter(new FileOutputStream(changer.outputPath+"/psd.amconll"), StandardCharsets.UTF_8), changer.intersectedDepsPSD);
    }

    private void applyFix(Function<AmConllSentence, Function<AmConllSentence, Consumer<AmConllSentence>>> fixingFunction)
            throws AlignedAMDependencyTree.ConllParserException, ParseException, ParserException {

        for (int i = 0; i < intersectedDepsDM.size(); i++) {
            AmConllSentence dmDep = intersectedDepsDM.get(i);
            AmConllSentence pasDep = intersectedDepsPAS.get(i);
            AmConllSentence psdDep = intersectedDepsPSD.get(i);

            //create backups in case things don't work out
            AmConllSentence dmBackup = (AmConllSentence)dmDep.clone();
            AmConllSentence pasBackup = (AmConllSentence)pasDep.clone();
            AmConllSentence psdBackup = (AmConllSentence)psdDep.clone();

            //save original evaluation results for later checking
//            SGraph dmSGraph = AlignedAMDependencyTree.fromSentence(dmDep).evaluate(true);
//            ModifyDependencyTreesDetCopNeg.onlyIndicesAsLabels(dmSGraph);
//            SGraph psdSGraph = AlignedAMDependencyTree.fromSentence(psdDep).evaluate(true);
//            ModifyDependencyTreesDetCopNeg.onlyIndicesAsLabels(psdSGraph);
//            SGraph pasSGraph = AlignedAMDependencyTree.fromSentence(pasDep).evaluate(true);
//            ModifyDependencyTreesDetCopNeg.onlyIndicesAsLabels(pasSGraph);

            //modify new dep trees here
            try {
                fixingFunction.apply(dmDep).apply(pasDep).accept(psdDep);
            } catch (Exception ex) {
                //restore backups
                intersectedDepsDM.remove(i);
                intersectedDepsDM.add(i, dmBackup);
                intersectedDepsPAS.remove(i);
                intersectedDepsPAS.add(i, pasBackup);
                intersectedDepsPSD.remove(i);
                intersectedDepsPSD.add(i, psdBackup);
                dmFails++;
                pasFails++;
                psdFails++;
            }

            // try to evaluate new graph, and use backup if it fails or if result differs
            //DM
            try {
                SGraph newdmSGraph = AlignedAMDependencyTree.fromSentence(dmDep).evaluate(true);
                ModifyDependencyTreesDetCopNeg.onlyIndicesAsLabels(newdmSGraph);
                //if (!newdmSGraph.equals(dmSGraph)) {
                if (newdmSGraph == null) {
                    //restore backup
                    intersectedDepsDM.remove(i);
                    intersectedDepsDM.add(i, dmBackup);
//                    System.err.println(dmDep.getId() + " DM graph changed");
                    dmFails++;
                }
            } catch (Exception ex) {
                //restore backup
                intersectedDepsDM.remove(i);
                intersectedDepsDM.add(i, dmBackup);
//                System.err.println(dmDep.getId());
//                ex.printStackTrace();
                dmFails++;
            }
            //PAS
            try {
                SGraph newpasSGraph = AlignedAMDependencyTree.fromSentence(pasDep).evaluate(true);
                ModifyDependencyTreesDetCopNeg.onlyIndicesAsLabels(newpasSGraph);
                //if (!newpasSGraph.equals(pasSGraph)) {
                if (newpasSGraph == null) {
                    //restore backup
                    intersectedDepsPAS.remove(i);
                    intersectedDepsPAS.add(i, pasBackup);
//                    System.err.println(pasDep.getId() + " PAS graph changed");
                    pasFails++;
                }
            } catch (Exception ex) {
                //restore backup
                intersectedDepsPAS.remove(i);
                intersectedDepsPAS.add(i, pasBackup);
//                System.err.println(pasDep.getId());
//                ex.printStackTrace();
                pasFails++;
            }
            //PSD
            try {
                SGraph newpsdSGraph = AlignedAMDependencyTree.fromSentence(psdDep).evaluate(true);
                ModifyDependencyTreesDetCopNeg.onlyIndicesAsLabels(newpsdSGraph);
                //if (!newpsdSGraph.equals(psdSGraph)) {
                if (newpsdSGraph == null) {
                    //restore backup
                    intersectedDepsPSD.remove(i);
                    intersectedDepsPSD.add(i, psdBackup);
//                    System.err.println(psdDep.getId() + " PSD graph changed");
                    psdFails++;
                }
            } catch (Exception ex) {
                //restore backup
                intersectedDepsPSD.remove(i);
                intersectedDepsPSD.add(i, psdBackup);
//                System.err.println(psdDep.getId());
//                ex.printStackTrace();
                psdFails++;
            }
        }
    }


    private void printComparisons() {
        System.out.println("DM-PAS DM-PSD PAS-PSD");
        double dmPasUnlabeledF = AmConllComparator.getF(intersectedDepsDM, intersectedDepsPAS, false, false);
        double dmPsdUnlabeledF = AmConllComparator.getF(intersectedDepsDM, intersectedDepsPSD, false, false);
        double psdPasUnlabeledF = AmConllComparator.getF(intersectedDepsPSD, intersectedDepsPAS, false, false);
        System.out.println(String.format("%.0f",dmPasUnlabeledF*100)+"     "+String.format("%.0f",dmPsdUnlabeledF*100)
                +"     "+String.format("%.0f",psdPasUnlabeledF*100)+ "   unlabeled F");
        double dmPasAMF = AmConllComparator.getF(intersectedDepsDM, intersectedDepsPAS, true, false);
        double dmPsdAMF = AmConllComparator.getF(intersectedDepsDM, intersectedDepsPSD, true, false);
        double psdPasAMF = AmConllComparator.getF(intersectedDepsPSD, intersectedDepsPAS, true, false);
        System.out.println(String.format("%.0f",dmPasAMF*100)+"     "+String.format("%.0f",dmPsdAMF*100)
                +"     "+String.format("%.0f",psdPasAMF*100)+ "   APP/MOD F");
        double dmPasLabeledF = AmConllComparator.getF(intersectedDepsDM, intersectedDepsPAS, false, true);
        double dmPsdLabeledF = AmConllComparator.getF(intersectedDepsDM, intersectedDepsPSD, false, true);
        double psdPasLabeledF = AmConllComparator.getF(intersectedDepsPSD, intersectedDepsPAS, false, true);
        System.out.println(String.format("%.0f",dmPasLabeledF*100)+"     "+String.format("%.0f",dmPsdLabeledF*100)
                +"     "+String.format("%.0f",psdPasLabeledF*100)+ "   labeled F");
    }

}
