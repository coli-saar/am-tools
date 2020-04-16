package de.saar.coli.amtools.script;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import static de.saar.coli.amrtagging.AlignedAMDependencyTree.decodeNode;
import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.sdp.dm.DMBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.pas.PASBlobUtils;
import de.saar.coli.amrtagging.formalisms.sdp.psd.PSDBlobUtils;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.*;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.*;
import java.util.*;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type.Edge;
import java.util.stream.Collectors;

public class ModifyDependencyTreesDetCopNeg {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\sdp2014_2015\\data\\2015\\en.dm.sdp";
    // "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.dm.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\sdp2014_2015\\data\\2015\\en.pas.sdp";
    //"/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.pas.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\data\\sdp\\sdp2014_2015\\data\\2015\\en.psd.sdp";
    //"/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.psd.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\original_decompositions\\dm\\gold-dev\\gold-dev.amconll";
    //"/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/dm/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\original_decompositions\\pas\\gold-dev\\gold-dev.amconll";
    //"/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/pas/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\original_decompositions\\psd\\gold-dev\\gold-dev.amconll";
    //"/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/psd/gold-dev/gold-dev.amconll";

    @Parameter(names = {"--outputPath", "-o"}, description = "Path to the output folder")
    private String outputPath = "C:\\Users\\Jonas\\Documents\\Work\\experimentData\\uniformify2020\\";
    //"../../github/";



    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

    //private int determiner = 0;
    //private int determinerFixedPSD = 0;

    private int negations = 0;
    private int negationsFixedPSD = 0;
    private int negationsFixedPAS = 0;
    private int negationsAllFixed = 0;
    private Counter<String> negationPatterns = new Counter<>();
    private Counter<String> fixedNegationPatterns = new Counter<>();
    
    private int never = 0;
    private int neverFixedPSD = 0;
    private int neverFixedPAS = 0;
    private int neverAllFixed = 0;
    private Counter<String> neverPatterns = new Counter<>();

    private Counter<String> pos566 = new Counter<>();
    private Counter<String> lemma566 = new Counter<>();
   
    private int copula = 0;
    private int copulaFixedDM = 0;

    public int getNegations() {
        return negations;
    }

    public int getNegationsFixedPSD() {
        return negationsFixedPSD;
    }

    public int getNegationsFixedPAS() {
        return negationsFixedPAS;
    }

    public int getNegationsAllFixed() {
        return negationsAllFixed;
    }

    public int getNever() {
        return never;
    }

    public int getNeverFixedPSD() {
        return neverFixedPSD;
    }

    public int getNeverFixedPAS() {
        return neverFixedPAS;
    }

    public int getNeverAllFixed() {
        return neverAllFixed;
    }

    public int getCopula() {
        return copula;
    }

    public int getCopulaFixedDM() {
        return copulaFixedDM;
    }

    //TODO fix inconsitent access / getters
    public int punctuation = 0;
    public int punctuationFixedPSD = 0;
    public int punctuationFixedDM = 0;
    public int punctuationAllFixed = 0;

    private int binaryconjunction = 0;
    private int binaryconjunctionFixedDM = 0;
    private Counter<String> binaryConjunctionPatterns = new Counter<>();

    public Counter<String> patternLogger = new Counter<>();
    public Counter<String> failLogger = new Counter<>();

    private int determiner = 0;
    private int determinerFixedPSD = 0;

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
        ModifyDependencyTreesDetCopNeg cli = new ModifyDependencyTreesDetCopNeg();
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
        
        ModifyDependencyTreesDetCopNeg treeModifier = new ModifyDependencyTreesDetCopNeg();

        int decomposableGraphs = 0;
        int problems = 0;
        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null) {
            if (decomposedIDs.contains(dmGraph.id)) {
                //now we know the graph was decomposed in all graphbanks, and we have all three AM dep trees for it.
                // we can also look at the original graphs (dmGraph etc) if we need to.
                decomposableGraphs++;
                if (decomposableGraphs % 2000 == 1) System.out.print(".");

                String id = dmGraph.id;
                AmConllSentence dmDep = id2amDM.get(id);
                AmConllSentence pasDep = id2amPAS.get(id);
                AmConllSentence psdDep = id2amPSD.get(id);
                String originalDMDepStr = dmDep.toString();
                String originalPSDDepStr = psdDep.toString();
                String originalPASDepStr = pasDep.toString();

                try {
                    SGraph dmSGraph = AlignedAMDependencyTree.fromSentence(dmDep).evaluate(true);
                    onlyIndicesAsLabels(dmSGraph);
                    SGraph psdSGraph = AlignedAMDependencyTree.fromSentence(psdDep).evaluate(true);
                    onlyIndicesAsLabels(psdSGraph);
                    SGraph pasSGraph = AlignedAMDependencyTree.fromSentence(pasDep).evaluate(true);
                    onlyIndicesAsLabels(pasSGraph);
                    //System.out.println(dmSGraph);


                    for (AmConllEntry psdEntry : psdDep) {
                        if (FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, psdEntry.getId()).equals("566")) {
                            treeModifier.pos566.add(psdEntry.getPos());
                            treeModifier.lemma566.add(psdEntry.getLemma()+"_"+psdEntry.getPos());
                        }
                    }

                    //modify new dep trees here
                    //treeModifier.fixDeterminer(psdDep, dmDep, pasDep);
                    //fixDeterminer(psdDep, dmDep, pasDep);
                    //treeModifier.fixNegation(psdDep, dmDep, pasDep);
                    //treeModifier.fixNever(psdDep, dmDep, pasDep);
                    //treeModifier.fixPunctuation(psdDep, dmDep, pasDep);
                    //treeModifier.fixAdjCopula(psdDep, dmDep, pasDep);
                    treeModifier.fixBinaryConjuction(psdDep, dmDep, pasDep);




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
                        StringJoiner sj = new StringJoiner(" ");
                        for (String word : dmDep.words()) { sj.add(word); }
                        System.err.println("Graph ID "+ id + ": Sentence: " + sj.toString());
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
                } catch (IllegalArgumentException | NullPointerException ex){ // evaluation of original AM dep tree failed
                    System.err.println("(Graph ID "+ id + "): Skipping sentence because:");
                    ex.printStackTrace();
                    //System.err.println(psdDep); //TODO REMOVE
                    problems++;
                }
            }
        }

        AmConllSentence.write(new FileWriter(cli.outputPath+"/dm.amconll"), newAmDM);
        AmConllSentence.write(new FileWriter(cli.outputPath+"/pas.amconll"), newAmPAS);
        AmConllSentence.write(new FileWriter(cli.outputPath+"/psd.amconll"), newAmPSD);

        System.out.println("Decomposable graphs: " + decomposableGraphs);
        System.out.println("Problems "+ problems);

        //System.out.println("Determiner: " + treeModifier.determiner + " found, " + treeModifier.determinerFixedPSD + " fixed (all)");

        System.out.println("Negations:");
        System.out.println(treeModifier.negations);
        System.out.println("Fixed in PSD:");
        System.out.println(treeModifier.negationsFixedPSD);
        System.out.println("Fixed in PAS:");
        System.out.println(treeModifier.negationsFixedPAS);
        System.out.println("Cases where all could be fixed "+treeModifier.negationsAllFixed);
        
        System.out.println("\nNever");
        System.out.println(treeModifier.never);
        System.out.println("Fixed in PSD:");
        System.out.println(treeModifier.neverFixedPSD);
        System.out.println("Fixed in PAS:");
        System.out.println(treeModifier.neverFixedPAS);
        System.out.println("Cases where all could be fixed "+treeModifier.neverAllFixed);

        System.out.println("\nCopula found: "+treeModifier.copula
                + " fixed: " + treeModifier.copulaFixedDM
                + " : %fixed " + (treeModifier.copula == 0 ? 100 : 100*treeModifier.copulaFixedDM / (float)treeModifier.copula));

        System.out.println("\nPunctuations:");
        System.out.println(treeModifier.punctuation);
        System.out.println("Fixed in PSD:");
        System.out.println(treeModifier.punctuationFixedPSD
                + "\t\tin percent: " + (treeModifier.punctuation == 0 ? 100 : 100*treeModifier.punctuationFixedPSD / (float)treeModifier.punctuation));
        System.out.println("Fixed in DM:");
        System.out.println(treeModifier.punctuationFixedDM
                + "\t\tin percent: " + (treeModifier.punctuation == 0 ? 100 : 100*treeModifier.punctuationFixedDM / (float)treeModifier.punctuation));
        System.out.println("Cases where all could be fixed " + treeModifier.punctuationAllFixed
                + "\t\tin percent: " + (treeModifier.punctuation == 0 ? 100 : 100*treeModifier.punctuationAllFixed / (float)treeModifier.punctuation));


        System.out.println("Binary coordination");
        System.out.println(treeModifier.binaryconjunction);
        System.out.println("Fixed in DM");
        System.out.println(treeModifier.binaryconjunctionFixedDM);


        System.err.println("Negation patterns:");
        treeModifier.negationPatterns.printAllSorted();
        System.err.println("Fixed negation patterns:");
        treeModifier.fixedNegationPatterns.printAllSorted();
        System.err.println("Never patterns:");
        treeModifier.neverPatterns.printAllSorted();
        System.err.println("566 pos:");
        treeModifier.pos566.printAllSorted();
        System.err.println("566 lemmas:");
        treeModifier.lemma566.printAllSorted();
        System.err.println("binary coord patterns:");
        treeModifier.binaryConjunctionPatterns.printAllSorted();

    }
    
    /**
     * Takes an s-graph in which node names and labels are encoded into the labels and strips off the node names
     * and only keeps the alignment
     * @param sg 
     */
    public static void onlyIndicesAsLabels(SGraph sg){
         for (String nodeName : sg.getAllNodeNames()) {
            Pair<Integer, Pair<String, String>> infos = decodeNode(sg.getNode(nodeName));
            sg.getNode(nodeName).setLabel(Integer.toString(infos.left));
        }
    }

    public void fixDeterminer(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Determiners for PSD
        // we find all words that are determiners (have DT POS tag)
        // and that are ignored in the PSD graph (have incoming IGNOREn edge).
        // Then we change the PSD entry to have an empty modifier graph,
        // and attach it to the head it has in DM.
        int index = 0;
        for (AmConllEntry word : psdDep){
            if (word.getPos().equals("DT") && FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, word.getId()).equals("660")){
                determiner++;
                // System.err.println(index);
                // System.err.println(dmDep.getParent(index));
                if (dmDep.getParent(index) == null) {
                    failLogger.add("det ignored in DM");
                    continue; // if DM ignored determiner as well, skip this
                }
                int head = dmDep.getParent(index).getId();// index is 0-based, head is 1-based
                if (AmConllEntry.IGNORE.equals(psdDep.get(head-1).getEdgeLabel())) {
                    failLogger.add("det head ignored in PSD");
                    continue;
                }
                word.setHead(head);
                word.setEdgeLabel("MOD_det");
                word.setDelexSupertag("(u<root, det>)");// empty modifier graph: one unlabeled node with root and det source.
                word.setType(new ApplyModifyGraphAlgebra.Type("(det)")); // the type of the DelexSupertag
                determinerFixedPSD++;
                failLogger.add("det success");
            }
            index++;
        }
    }
    
    
    /**
     * if we have MOD_x(oldHead, oldDependent) we can turn that into
     * APP_source(oldDependent, oldHead)
     * @param deptree
     * @param oldDependent
     * @param oldHead
     * @param source 
     */
    public void swapHead(AlignedAMDependencyTree deptree, AmConllEntry oldDependent, AmConllEntry oldHead, String source){
        if (!oldDependent.getEdgeLabel().startsWith("MOD")) throw new IllegalArgumentException("Dependent must be attached with MOD");
        
        String modifierSource = oldDependent.getEdgeLabel().split("_")[1]; 
        
        int incomingEdge = oldHead.getHead();
        String incomingLabel = oldHead.getEdgeLabel();
        
        // Manipulate the type of the dependent to contain the new source and require the type at the head
        
        Type oldHeadType = deptree.getTermTypeAt(oldHead);
        if (!oldHeadType.equals(Type.EMPTY_TYPE)) {
            failLogger.add("neg percolating sources");
        }
        Set<String> origins = oldHeadType.getOrigins();
        Type newHeadType = oldHeadType.addSource(source);
        for (String o : origins){
            newHeadType = newHeadType.setDependency(source, o, o);
        }
        
        // Take the original s-graph, rename the x-source (corresponding to MOD_x) to the new source name
        // and move the root source to the node that we used to modify with.
        
        SGraph oldDependentFragment = new IsiAmrInputCodec().read(oldDependent.getDelexSupertag());
        String modifierNode = oldDependentFragment.getNodeForSource(modifierSource);
        
        oldDependentFragment = oldDependentFragment.renameSource(modifierSource, source);
        
        Set<String> sources = oldDependentFragment.getAllSources();
        sources.remove("root");
        oldDependentFragment = oldDependentFragment.forgetSourcesExcept(sources);
        
        oldDependentFragment.addSource("root", modifierNode);
        
        // make old head an APP dependent
        oldHead.setHead(oldDependent.getId());
        oldHead.setEdgeLabel("APP_"+source);
        
        //make old dependent the head
        oldDependent.setHead(incomingEdge);
        oldDependent.setEdgeLabel(incomingLabel);
        oldDependent.setType(newHeadType);
        oldDependent.setDelexSupertag(oldDependentFragment.toIsiAmrStringWithSources());
        
        
    }
    
    /**
     * Renames a source in a type.
     * @param t
     * @param oldName
     * @param newName
     * @return 
     */
    public Type renameSource(Type t, String oldName, String newName){
        Type ret = Type.EMPTY_TYPE;
        
        
        if (t.getAllSources().contains(newName)) throw new IllegalArgumentException("Couldn't rename source because new source name is already in use");
        
        for (String node : t.getAllSources()){
            if (! node.equals(oldName)){
                ret = ret.addSource(node);
            } else {
                ret = ret.addSource(newName);
            }
        }
        for (Type.Edge e : t.getAllEdges()){
            String source = e.getSource();
            if (source.equals(oldName)) source = newName;

            String target = e.getTarget();
            if (target.equals(oldName)) target = newName;

            String label = e.getLabel();
            if (label.equals(oldName)) label = newName;

            ret = ret.setDependency(source, target, label);
        }

        return ret;
        
    }
    
    public void fixNever(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException, AlignedAMDependencyTree.ConllParserException {
        int index = 0;
        
        SGraph desiredPSDSupertag = new IsiAmrInputCodec().read("(i_8<root> / --LEX--  :TWHEN-of (i_2<mod>))");
        SGraph desiredPASSupertag = new IsiAmrInputCodec().read("(i_6<root> / --LEX--  :adj_ARG1 (i_8<mod>))");
        SGraph desiredDMSupertag = new IsiAmrInputCodec().read("(i_3<root> / --LEX--  :ARG1 (i_2<s>))");
        
        
        AlignedAMDependencyTree dmTree = AlignedAMDependencyTree.fromSentence(dmDep);
        AlignedAMDependencyTree psdTree = AlignedAMDependencyTree.fromSentence(psdDep);
        AlignedAMDependencyTree pasTree = AlignedAMDependencyTree.fromSentence(pasDep);
        
        boolean fixedPSD = false;
        
        for (AmConllEntry dmEntry : dmDep) {
            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, dmEntry.getId());
            if (dmEntry.getLemma().equals("never") && pattern.equals("566")){
                this.never++;
                neverPatterns.add(FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, dmEntry.getId()));
                
                if ((new IsiAmrInputCodec().read(dmEntry.getDelexSupertag())).equals(desiredDMSupertag)) {
                Optional<AmConllEntry> potential_argument = dmDep.getChildren(index).stream().filter(child -> child.getEdgeLabel().equals("APP_s")).findFirst();
                if (potential_argument.isPresent()) {
                    AmConllEntry dmArgument = potential_argument.get();
                    
                    
                    // now dmEntry is "never"
                    // there is an APP_s edge to the dmArgument
                    
                    // rename s source to neg
                    dmArgument.setEdgeLabel("APP_neg");
                    dmEntry.setType(renameSource(dmEntry.getType(), "s", "neg"));
                    dmEntry.setDelexSupertag("(i_3<root> / --LEX--  :ARG1 (i_2<neg>))");
                    
                    fixedPSD = false;
                    
                    //fix PSD where we have head -- MOD_mod --> never
                    
                    AmConllEntry psdEntry = psdDep.get(index);
                    if (psdEntry.getEdgeLabel().equals("MOD_mod") && desiredPSDSupertag.equals(new IsiAmrInputCodec().read(psdEntry.getDelexSupertag()))
                            && psdEntry.getHead() == dmArgument.getId() // do we want this condition? Perhaps, we should systematically swap?
                            ){
                        // we indeed have the situation as described above in PSD
                        if (!psdTree.getTermTypeAt(psdDep.getParent(index)).equals(Type.EMPTY_TYPE)) {
                            System.out.println("Never percolation PAS: "+psdEntry.getId());
                            AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);
                        }
                        swapHead(psdTree, psdEntry, psdDep.getParent(index), "neg");
                        neverFixedPSD++;
                        fixedPSD = true;
                        psdTree = AlignedAMDependencyTree.fromSentence(psdDep);
                        
                    }
                    
                    // fix PAS, same situation as in PSD
                    AmConllEntry pasEntry = pasDep.get(index);
                    if (pasEntry.getEdgeLabel().equals("MOD_mod") && desiredPASSupertag.equals(new IsiAmrInputCodec().read(pasEntry.getDelexSupertag()))
                            && pasEntry.getHead() == dmArgument.getId() // do we want this condition?
                            ){

                        if (!pasTree.getTermTypeAt(pasDep.getParent(index)).equals(Type.EMPTY_TYPE)) {
                            System.out.println("Never percolation PAS: "+pasEntry.getId());
                            AMExampleFinder.printExample(pasDep, pasEntry.getId(), 5);
                        }
                        swapHead(pasTree, pasEntry, pasDep.getParent(index), "neg");
                        neverFixedPAS++;
                        pasTree = AlignedAMDependencyTree.fromSentence(pasDep);
                        
                        if (fixedPSD) neverAllFixed++; 
                        
                    }
                    
                    
                }
                
            }}
            index++;
         
        }
    }
   
    
    
    public void fixNegation(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException, AlignedAMDependencyTree.ConllParserException {
        int index = 0;
        
        SGraph desiredPSDSupertag = new IsiAmrInputCodec().read("(i<root> / --LEX--  :RHEM-of (j<mod>))");
        SGraph desiredPASSupertag = new IsiAmrInputCodec().read("(i_2<root> / --LEX--  :adj_ARG1 (i_3<mod>))");
        SGraph desiredDMSupertag = new IsiAmrInputCodec().read("(i_13<root> / --LEX--  :neg (i_12<mod>))");
        
        
        
        boolean fixedPSD = false;
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);
            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, psdEntry.getId());
            if (psdEntry.getLemma().equals("#Neg") && pattern.equals("566")) { // psdEntry is Negation word
                this.negations ++;
                negationPatterns.add(pattern);
                fixedPSD = false;
                // In DM we can be in the situation that the negation word is the head with outgoing APP_mod edge
                // or - in relative clauses - , the negation would be the depndent of the verb and we have an incoming MOD_mod edge
                
                if (dmDep.getChildren(index).isEmpty() && new IsiAmrInputCodec().read(dmEntry.getDelexSupertag()).equals(desiredDMSupertag) && dmEntry.getEdgeLabel().equals("MOD_mod")){
                    // we are probably in a relative clause
                    // so we first make DM consistent that the negation word is the head, then we can apply the transformation for PSD and PAS
                    
                    // let's make sure that we are in a relative clause and see what the clause modifies
                    AmConllEntry negatedDMverb = dmDep.getParent(index);
                    
                    AlignedAMDependencyTree dmTree = AlignedAMDependencyTree.fromSentence(dmDep);
                    
                    Type termTypeofNegatedDMverb = dmTree.getTermTypeAt(negatedDMverb);
                    if (negatedDMverb.getEdgeLabel().equals("MOD_s") || negatedDMverb.getEdgeLabel().equals("MOD_o")) {
                        // subject or object relative clause
                        // make negation child of what the clause modifies
                        swapHead(dmTree, dmEntry, negatedDMverb, "neg");
                    }
                    
                }
                
                // find verb or thing that is negated in DM: could be none, therefore use Optional
                // outgoing dep. edges in DM from negation: if it's an APPmod edge, its target is the negated thing
                
                Optional<AmConllEntry> potential_argument = dmDep.getChildren(index).stream().filter(child -> child.getEdgeLabel().equals("APP_mod") || child.getEdgeLabel().equals("APP_neg")).findFirst();
                if (potential_argument.isPresent()) {
                    AmConllEntry dmArgument = potential_argument.get();
                    // found DM negation
                    
                    // DM: rename mod source to neg source
                    if (new IsiAmrInputCodec().read(dmEntry.getDelexSupertag()).equals(desiredDMSupertag)) {
                        dmArgument.setEdgeLabel("APP_neg");
                        dmEntry.setDelexSupertag("(i_13<root> / --LEX--  :neg (i_12<neg>))");
                        Type negationType = Type.EMPTY_TYPE;
                        negationType = negationType.addSource("neg");
                        dmEntry.setType(negationType);
                    
                    }
                    
                    // PSD
                    //  - currently   --> argument --MOD_mod--> psdEntry (negation)
                    //  - would like:   argument <--APP_mod-- psdEntry (Negation) <--
                    //        plus changed Negation supertag (root source added at mod source place)
                    if (psdEntry.getEdgeLabel().equals("MOD_mod")) {// &&
                            //psdEntry.getHead() == dmArgument.getId()) {
                        // fix PSD
                        AlignedAMDependencyTree psdAlignedDeptree = AlignedAMDependencyTree.fromSentence(psdDep);
                        AmConllEntry psdNegated = psdDep.getParent(index); // verb or sth else

                        SGraph supertag = new IsiAmrInputCodec().read(psdEntry.getDelexSupertag());
                        
                        if (desiredPSDSupertag.equals(supertag)) {
                            // only change if negated element doesn't have a mod source
                            try {
                                // take term type of negated element, add neg source and create dependencies such that the requirement 
                                // at the neg source is the type of the negated element.
                                if (!psdAlignedDeptree.getTermTypeAt(psdNegated).equals(Type.EMPTY_TYPE)) {
                                    System.out.println("Negation percolation PSD: "+psdEntry.getId());
                                    AMExampleFinder.printExample(psdDep, psdEntry.getId(), 5);
                                }
                                swapHead(psdAlignedDeptree, psdEntry, psdNegated, "neg");
                                negationsFixedPSD++;
                                fixedPSD = true;
                             } catch (IllegalArgumentException ex) { // introduces a cycle by adding the mod source and the dependencies
                                failLogger.add("neg cycle PSD");
                             }

                        } else { //PSD has unexpected supertag, no case in dev data.
                            failLogger.add("neg unexpected supertag PSD");
                        }


                    } else { //PSD doesn't have MOD_mod edge in right place:
                        failLogger.add("neg missing MOD_mod PSD");
//                        System.out.println(dmEntry.getId());
//                        System.out.println("DM");
//                        AMExampleFinder.printExample(dmDep, dmEntry.getId(), 3);
//                        System.out.println("PSD");
//                        AMExampleFinder.printExample(psdDep, dmEntry.getId(), 3);
//                        System.out.println("PAS");
//                        AMExampleFinder.printExample(pasDep, dmEntry.getId(), 3);
                    }

                    // PAS
                    //  - currently   --> argument --MOD_mod--> pasEntry (negation)
                    //  - would like:   argument <--APP_mod-- pasEntry (Negation) <--
                    //        plus changed Negation supertag (root source added at mod source place)
                    
                    if (pasEntry.getEdgeLabel().equals("MOD_mod")) {// &&
                            //pasEntry.getHead() == dmArgument.getId()) {
                        // fix PAS
                        AlignedAMDependencyTree pasAlignedDeptree = AlignedAMDependencyTree.fromSentence(pasDep);
                        AmConllEntry pasNegated = pasDep.getParent(index); // verb or sth else

                        SGraph supertag = new IsiAmrInputCodec().read(pasEntry.getDelexSupertag());
                        if (desiredPASSupertag.equals(supertag)) {
                            try {
                                if (!pasAlignedDeptree.getTermTypeAt(pasNegated).equals(Type.EMPTY_TYPE)) {
                                    System.out.println("Negation percolation PAS: "+pasEntry.getId());
                                    AMExampleFinder.printExample(pasDep, pasEntry.getId(), 5);
                                }
                                swapHead(pasAlignedDeptree, pasEntry, pasNegated, "neg");
                                
                                negationsFixedPAS++;
                                if (fixedPSD) {
                                    String afterFixPattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, psdEntry.getId());
                                    fixedNegationPatterns.add(pattern+"|"+afterFixPattern);
                                    negationsAllFixed++;
                                    failLogger.add("neg success");
                                }
                            } catch (IllegalArgumentException ex) { // introduces a cycle by adding the mod source and the dependencies
                                failLogger.add("neg cycle PAS");
                            }
                            
                        } else { //PAS uses unexpected supertag, no cases in gold dev.
                            failLogger.add("neg unexpected supertag PAS");
                        }


                    } else { //PAS doesn't have MOD_mod edge in expected place:
                        failLogger.add("neg missing MOD_mod PAS");
                    }
                    
                    // DEBUG TODO maybe look at what is actually negated this way? only verbs?
                } // found negated argument 
                else {
                    failLogger.add("neg no potential argument");
                   // System.err.println(dmDep.getId());
                   // System.err.println(dmDep);
                }

            } // found #Neg lemma
            index++;
        } // for psdEntry
    }


    public void fixPASOnlyModifiers(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Punctuation
        // in PAS sometimes punctuation integrated in graph, but not in PSD and DM:
        // fix: DM, PSD create one-node blob with pnct and root source for them
        // todo avoid magic strings if possible (import static strings from elsewhere?)
        String ignore_edge = "IGNORE";
        List<String> allowedPOS = Arrays.asList(",","``","''",":","-RRB-","LRB-","TO","IN","RP");
        int index = 0;
        for (AmConllEntry word : pasDep){
            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, word.getId());
            AmConllEntry psdWord = psdDep.get(index); // punctuation is PSD
            if (pattern.equals("060") && allowedPOS.contains(psdWord.getPos())) {
            //if (word.getEdgeLabel().equals(mod_punct)){ // TODO maybe check if APPpnct exists?
                punctuation++;
                boolean fixedPSD = false;
                boolean fixedDM = false;
                // variable index now points to punctuation symbol, AmConnlEntry word is punctuation entry
                //assert (pasDep.getParent(index) != null) // parent should be head of the MOD_punct relation
                int parentID_pas = pasDep.getParent(index).getId(); // index is 0-based, parentID_pas is 1-based
                String pasSource = word.getEdgeLabel().substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());

                // A. PSD if ignored, change to PAS like structure
                //assert psdWord != null; // same sentence , same size
                if (psdWord.getEdgeLabel().equals(ignore_edge)
                        && !psdDep.get(parentID_pas-1).getEdgeLabel().equals(ignore_edge)) {
                    // second term: skip if parent in PAS is ignored in PSD??? todo should i do this?
                    // delete ignore edge, create edge parent-punctuation
                    // create supertag for punctuation
                    psdWord.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION+pasSource); // was previously ignore
                    psdWord.setHead(parentID_pas); // was previously artificial root probably
                    psdWord.setDelexSupertag("(u<root, "+pasSource+">)");// empty modifier graph: one unlabeled node with root and det source.
                    psdWord.setType(new ApplyModifyGraphAlgebra.Type("("+pasSource+")"));
                    punctuationFixedPSD++;
                    fixedPSD = true;
                } else {
                    failLogger.add("punct PSD fail");
                }

                // B. DM if ignored, change to pas like structure
                // todo copy pasted code, but what is the best way in java here to avoid it?
                AmConllEntry dmWord = dmDep.get(index);
                if (dmWord.getEdgeLabel().equals(ignore_edge) && !dmDep.get(parentID_pas-1).getEdgeLabel().equals(ignore_edge)) {
                    // second term: skip if parent in PAS is ignored in DM???
                    // delete ignore edge, create edge parent-punctuation
                    // create supertag for punctuation
                    dmWord.setEdgeLabel(ApplyModifyGraphAlgebra.OP_MODIFICATION+pasSource); // was previously ignore
                    dmWord.setHead(parentID_pas); // was previously artificial root
                    dmWord.setDelexSupertag("(u<root, "+pasSource+">)");// empty modifier graph: one unlabeled node with root and det source.
                    dmWord.setType(new ApplyModifyGraphAlgebra.Type("("+pasSource+")"));
                    punctuationFixedDM++;
                    fixedDM = true;
                } else {
                    failLogger.add("punct DM fail");
                }

                // if both DM and PSD fixed : increment counter
                if (fixedDM && fixedPSD) {
                    failLogger.add("punct success");
                    punctuationAllFixed++;
                }
            }
            index++;
        }
    }


    public void fixAdjCopula(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Adjective copula
        // DM
        // - APPs from adjective to subject, adjective is root
        // - ignore the to-be, directly connects adjective and subject (like in adnominal case)
        // PSD
        // - APPs from to-be to subject, APPo from to-be to object, to-be is root
        // - treats this construction like a transitive verb one (to-be with ACT-arg and PAT-arg edge)
        // PAS
        // - (same as PSD: but control-structure forces more complicated type for to-be: o(mod_UNIFY_s), s
        // - uses control-like structure (kind of DM and PSD merged) verb_ARG1/2 edges from to-be, adj_ARG1 edge from adjective to subject
        // solution:
        // o[s] blob for to-be in DM, so that PSD dep tree structure works
        // todo change only if all frameworks show pattern (might miss some instances), is this the pattern we quantified (how frequent was it?)
        ApplyModifyGraphAlgebra.Type unproblematic_adj_type = new ApplyModifyGraphAlgebra.Type("(s)");

        String apps = "APP_s";
        String appo = "APP_o";
        String appoo = "APP_oo";
        String ignore = "IGNORE";
        int index = 0;
        for (AmConllEntry word : psdDep){
            // word lemma be
            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, word.getId());
            if (word.getLemma().equals("be") && pattern.equals("031")) {
                //todo check examples for patterns 038 and 033?
                copula++;
                patternLogger.add("copula "+pattern);

                AmConllEntry subjectPAS = null;
                AmConllEntry adjectivePAS = null;
                // 1.find arguments of to be
                for (AmConllEntry child: psdDep.getChildren(index)) {
                    if (child.getEdgeLabel().equals(apps)) {
                        subjectPAS = child;
                    }
                    else if (child.getEdgeLabel().equals(appo) || child.getEdgeLabel().equals(appoo)) {
                        adjectivePAS = child;
                    }
                }
                if (subjectPAS == null || adjectivePAS == null) {
                    failLogger.add("copula missing PAS arg");
                    System.err.println(psdDep.get(index).getType());
                    continue; // at least one argument not found -> skip
                }
                if (!adjectivePAS.getPos().startsWith("JJ")) {
//                    System.err.println(adjectivePAS.getPos());
                    failLogger.add("copula not JJ");
                    continue; // only do adjectives for now
                }
                int subj0pas = subjectPAS.getId()-1; // 0-based
                int adj0pas = adjectivePAS.getId()-1;

                // 2. check if psd and dm have expected structure
                // 2.1 PSD:   verb--APPs->subject and verb--APPo->adjective
                AmConllEntry verbPSD = psdDep.get(index);
                AmConllEntry subjPSD = psdDep.get(subj0pas);
                AmConllEntry adjPSD = psdDep.get(adj0pas);
//                if (subjPSD.getHead() != verbPSD.getId() || !subjPSD.getEdgeLabel().equals(apps)) {
//                    failLogger.add("copula PSD bad subject");
//                    continue;
//                }
//                if (adjPSD.getHead() != verbPSD.getId() || !adjPSD.getEdgeLabel().equals(appo)) {
//                    failLogger.add("copula PSD bad object");
//                    continue;
//                }
                // todo check 0-based, 1-based not confused? getHead, getId comparable?

                // 2.2 DM:    verb ignored,   adjective--APPs-> subject
                AmConllEntry verbDM = dmDep.get(index);
                AmConllEntry subjDM = dmDep.get(subj0pas);
                AmConllEntry adjDM = dmDep.get(adj0pas);
                if (!verbDM.getEdgeLabel().equals(ignore)) {
                    failLogger.add("copula DM not ignored");
                    continue;
                }
                if (!subjDM.getEdgeLabel().equals(apps) || subjDM.getHead() != adjDM.getId()) {
                    failLogger.add("copula no good DM edge");
                    continue;
                }
                // todo check 0-based, 1-based not confused? getHead, getId comparable?
                // todo check if incoming edge is the same across PSD, DM, PAS? -> other phenomena?

                AlignedAMDependencyTree dmAlignedDepTree;
                try {
                    dmAlignedDepTree = AlignedAMDependencyTree.fromSentence(dmDep);
                } catch (AlignedAMDependencyTree.ConllParserException e) {
                   failLogger.add("copula error in getting aligned dep tree");
                   continue;
                }
                if (!(subjDM.getEdgeLabel().equals(ApplyModifyGraphAlgebra.OP_APPLICATION+"s")
                        && dmAlignedDepTree.getTermTypeAt(adjDM).equals(Type.EMPTY_TYPE))) {
                    //System.err.println("Can't fix copula for now: problematic adjective type: " + adjDM.getType().toString() + " for adjective " + adjDM.getForm());
                    failLogger.add("copula bad DM type");
                    System.out.println("Copula bad DM type: "+verbDM.getId());
                    AMExampleFinder.printExample(dmDep, verbDM.getId(), 5);
                    continue;
                }





                // 3. change DM
                // 3.1. heads (from h-->adj-->subj  to  h-->to-be-->adj and to-be-->subj )
                int headDM = adjDM.getHead();
                verbDM.setHead(headDM);
                adjDM.setHead(verbDM.getId());
                subjDM.setHead(verbDM.getId());
                // 3.2 incoming dependency edges
                verbDM.setEdgeLabel(adjDM.getEdgeLabel()); // todo side effects?
                adjDM.setEdgeLabel(appo);
                // subjDM.setEdgeLabel(apps); // already the case
                // 3.3. types - only need to change verb
                verbDM.setType(new ApplyModifyGraphAlgebra.Type("(o(s))"));
                // 3.4 delex supertags - only need to change verb
                verbDM.setDelexSupertag("(u<root, o>)"); // one node  s-annotation not part of supertag
                copulaFixedDM++;
                failLogger.add("copula success");
            }
            index++;
        }
    }

    
    
    private Type addSourceWithRequest(Type t, String source, Type request){
        
        Set<String> oldSources = t.getAllSources();
        if (oldSources.contains(source)) throw new IllegalArgumentException("source already there");
        
        Type r = t.addSource(source);
        for (String s : request.getAllSources()){
            r = r.addSource(s);
        }
        for (Edge reqDep : request.getAllEdges()){
            r = r.setDependency(reqDep.getSource(), reqDep.getTarget(), reqDep.getLabel());
        }
        // add edges from source to request
        for (String origin : request.getOrigins()){
            r = r.setDependency(source, origin, origin);
        }
        
        return r;
    }

    private Type addSourceWithRequestAndRenames(Type typeToAddTo, String sourceToAdd, Type oldType, String oldSource){

        Set<String> oldSources = typeToAddTo.getAllSources();
        if (oldSources.contains(sourceToAdd)) throw new IllegalArgumentException("source already there");

        Type r = typeToAddTo.addSource(sourceToAdd);
        Type request = oldType.getRequest(oldSource);
        if (request == null) throw new IllegalArgumentException("oldSource not in oldType");
        //add all new sources, including renames
        for (String s : request.getAllSources()){
            String sAfterRename = oldType.getRenameTarget(oldSource, s);
            r = r.addSource(sAfterRename);
            r = r.setDependency(sourceToAdd, sAfterRename, s);
        }
        for (Edge reqDep : request.getAllEdges()){
            r = r.setDependency(oldType.getRenameTarget(oldSource, reqDep.getSource()),
                    oldType.getRenameTarget(oldSource, reqDep.getTarget()), reqDep.getLabel());
        }

        return r;
    }
    
    /**
     * Fixing Binary conjunction:
     * detection pattern:
     * - CC pos tag in PSD with APP_op and APP_op2 (and no other APP_opX edges) +
     * DM ignores the conjunction and conjuncts are connected directly using MOD_coord
     * TODO: [enhancement] rewrite detection (DM centered, look at PAS too)
     * - might be the source of current IllegalArgumentExceptions (Couldn't find a binarization ...) later on
     * TODO: [fix this] source change op to op1 in PSDs supertag  - do it right, not the string-find-replace way
     * Changes:
     * - DM MOD_coord structure changed to APP_op1, APP_op2 structure like PSD/PAS (conjunction no longer ignored)
     * - PSD's APP_op changed to APP_op1 (plus subsequent changes in the types and supertags)
     * */
    public void fixBinaryConjuction(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException, ParserException, AlignedAMDependencyTree.ConllParserException {
        String op1source = "op1";
        String op2source = "op2";
        String coordsource = "coord";
        String appop = "APP_op";
        String appop1 = "APP_op1";
        String appop2 = "APP_op2";
        String modcoord = "MOD_coord";
        String appcoord = "APP_coord";
        String ignore = "IGNORE";
        // todo avoid magic strings for edge labels and also sources? maybe import sources from BlobUtils?
        int index = 0;
        
        AlignedAMDependencyTree dmDepTree = AlignedAMDependencyTree.fromSentence(dmDep);
        
        for (AmConllEntry word : psdDep){
            // CC postag (conjunction) and pattern
            String pattern = FindAMPatternsAcrossSDP.getPatternCombination(dmDep, pasDep, psdDep, word.getId());
            if (word.getPos().equals("CC") && (pattern.equals("011") || pattern.equals("088"))) {
                // PSD 2 APP children (APP_op and APP_op1)
                // DM 1 coord edge

                AmConllEntry firstConjunctPSD = null;
                AmConllEntry secondConjunctPSD = null;
                boolean multiconj = false;
                // TODO: [ENHANCEMENT] maybe start with finding an dependency edge in DM with coord source and
                //  then try to find conjunction in PSD _and/or_ PAS. Currently we don't look at the PAS structure at all

                // 1.find arguments of conjunction in PSD
                for (AmConllEntry child: psdDep.getChildren(index)) {
                    if (child.getEdgeLabel().equals(appop)) {
                        firstConjunctPSD = child;
                    }
                    else if (child.getEdgeLabel().equals(appop2)) {
                        secondConjunctPSD = child;
                    }
                    else if (child.getEdgeLabel().startsWith(appop)) {  // more conjuncts?
                        multiconj = true;
                    }
                }
                if (multiconj) continue;  // skip multiconj for now  // maybe count them still?

                if (firstConjunctPSD == null || secondConjunctPSD == null) continue; // skip if not two conjuncts found


                // now we have coordination
                patternLogger.add("coord "+pattern);
                binaryconjunction++;



                int psdconj1idx = firstConjunctPSD.getId()-1; // 0-based
                int psdconj2idx = secondConjunctPSD.getId()-1;
                // do we need to check the type of PSD's conjunction? e.g. equals (op,op2) or related

                // 2. Find DM structure - coord edge between conjuncts?
                AmConllEntry firstConjunctDM = dmDep.get(psdconj1idx);
                AmConllEntry secondConjunctDM = dmDep.get(psdconj2idx);
                AmConllEntry conjunctionDM = dmDep.get(index);
                // a) conjunction node ignored in DM
                if (!conjunctionDM.getEdgeLabel().equals(ignore)) {
                    failLogger.add("coord no DM IGNORE");
                    continue;
                }
                // b) APPcoord or MODcoord egde between the two conjuncts
                // Most frequent structure is MODcoord with first conjunct as head, but others are possible too.
                // Depending on the structure (MOD or APP, first or second as head), the new DM supertag for the
                // conjunction looks slightly different.
                boolean usesModCoord = false;
                boolean coordEdgeInFirstConjunct = false;
                if (secondConjunctDM.getEdgeLabel().equals(modcoord) && secondConjunctDM.getHead() == firstConjunctDM.getId()) {
                   // MODcoord from first to second conjunct, first conjunct is Head --> coord source in second conjunct
                   usesModCoord = true;
                   // coordEdgeInFirstConjunct = false;
                   assert(secondConjunctDM.getType().getAllSources().contains(coordsource));
                }
                else if (secondConjunctDM.getEdgeLabel().equals(appcoord) && secondConjunctDM.getHead() == firstConjunctDM.getId()) {
                    // APPcoord from first to second conjunct, first conjunct is head --> coord source in first conjunct
                    //usesModCoord = false;
                    coordEdgeInFirstConjunct = true;
                    assert(firstConjunctDM.getType().getAllSources().contains(coordsource));
                }
                else if (firstConjunctDM.getEdgeLabel().equals(appcoord) && firstConjunctDM.getHead() == secondConjunctDM.getId()) {
                    // APPcoord from second to first conjunct, second conjunct is head --> coord souce in second conjunct
                    // usesModCoord = false;
                    // coordEdgeInFirstConjunct = false;
                    assert(secondConjunctDM.getType().getAllSources().contains(coordsource));
                }
                else if (firstConjunctDM.getEdgeLabel().equals(modcoord) && firstConjunctDM.getHead() == secondConjunctDM.getId()) {
                    // MODcoord from second to first conjunct, second conjunct is head --> coord source in first conjunct
                    usesModCoord = true;
                    coordEdgeInFirstConjunct = true;
                    assert(firstConjunctDM.getType().getAllSources().contains(coordsource));
                }
                else {
                    // no APP/MOD coord edge between conjuncts in DM -> not a DM coordination
                    failLogger.add("coord no DM _c edge");
                    continue;
                }
                boolean firstConjunctIsHead = usesModCoord ^ coordEdgeInFirstConjunct; // ^ is logical XOR
                // c) if we would like to be conservative: head of PSD conjunction node is the same as head of the
                // DM conjunct with the outgoing APP/MODcoord edge
                //if (firstConjunctIsHead && firstConjunctDM.getHead() != word.getHead()) continue;
                //if (!firstConjunctIsHead && secondConjunctDM.getHead() != word.getHead()) continue;
                AmConllEntry headConjunctDM = firstConjunctIsHead ? firstConjunctDM : secondConjunctDM;
                AmConllEntry coordSrcConjunctDM = coordEdgeInFirstConjunct ? firstConjunctDM : secondConjunctDM;


                // 3. change DM (and PSD source names...

                
                // 3. actual fix: change DM (and PSD source names)...
                
                if (usesModCoord){
                    //Matthias' part
                    Type depTermType = dmDepTree.getTermTypeAt(coordSrcConjunctDM);
                    Type headLexType = headConjunctDM.getType();
                    
                    Type coordinationType = addSourceWithRequest(Type.EMPTY_TYPE, "op2", depTermType.performApply("coord"));
                    
                    //find common arguments:
                    Set<AmConllEntry> commonArgumentChildren = dmDep.getChildren(headConjunctDM.getId()-1).stream().filter(child -> child.getEdgeLabel().startsWith("APP_")) //get apply edges
                                .filter(child -> depTermType.getOrigins().contains(child.getEdgeLabel().split("_")[1])).collect(Collectors.toSet());
                    
                    //make common arguments children of coordination
                    for (AmConllEntry commonChild : commonArgumentChildren){
                        commonChild.setHead(conjunctionDM.getId());
                    }
                    
                    // retrieve Type of head when common arguments are removed (that subtree is well-typed but the entire tree is not at this point!)
                    Type headTermType = AlignedAMDependencyTree.fromSentence(dmDep).getTermTypeAt(headConjunctDM);
                    
                    coordinationType = addSourceWithRequest(coordinationType, "op1", headTermType);
                    
                    conjunctionDM.setType(coordinationType); 
                    
                } else { //APP_coord
                    // Jonas' part
                    Type headRequest = headConjunctDM.getType().getRequest(coordsource);

                    Type coordinationType = addSourceWithRequest(Type.EMPTY_TYPE, "op1", headRequest);
                    coordinationType = addSourceWithRequestAndRenames(coordinationType, "op2", headConjunctDM.getType(), coordsource);
                    //TODO get op1 and op2 right
                    conjunctionDM.setType(coordinationType);
                }
                

                // - [DM] change head from first conjunct to conjunction
                int headDM = headConjunctDM.getHead();
                String toconjlabel = headConjunctDM.getEdgeLabel();
                conjunctionDM.setHead(headDM);
                conjunctionDM.setEdgeLabel(toconjlabel); // was previously ignore
                // - [DM] conjuncts receive incoming edge from conjunction node
                firstConjunctDM.setHead(conjunctionDM.getId());
                secondConjunctDM.setHead(conjunctionDM.getId());
                firstConjunctDM.setEdgeLabel(appop1);
                secondConjunctDM.setEdgeLabel(appop2); // was previously MODcoord
                // - [DM] change supertag of conjunct with coord source (delete conjunction edge in supertag plus incident coord node)
                String coordRepl = coordEdgeInFirstConjunct ? op2source : op1source;
                String lexRepl = coordEdgeInFirstConjunct ? op1source : op2source;
                Pair<SGraph, SGraph> supertags =  prepareNewDMSupertags(coordSrcConjunctDM.delexGraph(), usesModCoord, coordRepl, lexRepl);
                coordSrcConjunctDM.setDelexSupertag(supertags.left.toIsiAmrStringWithSources());
                // - [DM] change type of conjunct with coord source: remove coord from type
                coordSrcConjunctDM.setType(secondConjunctDM.getType().performApply(coordsource));// todo is this the right way to remove the coord source?
                // - [DM] create supertag for conjunction in DM (use edge deleted from secondConj supertag) plus type
                conjunctionDM.setDelexSupertag(supertags.right.toIsiAmrStringWithSources());

                // something like conjunctionDM.setDelexSupertag("(u<root, op1> :_and_c (v<op2>))");  but not just for :_and_c edge,
                // not sure about directionaly (_and_c or _and_c-of ?)
                

                //Type conjunctionTypeDM = addSourceWithRequest(Type.EMPTY_TYPE, "op1", conjunctDMTermType);
                //conjunctionTypeDM = addSourceWithRequest(conjunctionTypeDM, "op2", conjunctDMTermType); //TODO: secondConjunct?
                
                //conjunctionDM.setType(new ApplyModifyGraphAlgebra.Type("(op1, op2)"));
                //conjunctionDM.setType(conjunctionTypeDM);
                
                //reload dependency tree, we might make other changes to tree:
                dmDepTree = AlignedAMDependencyTree.fromSentence(dmDep);

                // Type firsttype = firstConjunctDM.getType();
                // Type secondtype = secondConjunctDM.getType();
                // String conjtype = "(op1" + firsttype + ", op2" + secondtype + ")" ;  // dirty hack and only an approximation of the desired solution
                //String conjtype = "(op1, op2)";
                //conjunctionDM.setType(new ApplyModifyGraphAlgebra.Type(conjtype));

                // todo [BUG] the previous line is problematic if the type should be something like (op1(mod), op2(mod)
                // - [PSD] change APP_op to APP_op1 and corresponding type & supertag change
                firstConjunctPSD.setEdgeLabel(appop1); // was previously APP_op  note the absence of the number 1
                SGraph oldSupertag = new IsiAmrInputCodec().read(word.getDelexSupertag());
                oldSupertag = oldSupertag.renameSource("op", "op1");
                word.setDelexSupertag(oldSupertag.toIsiAmrStringWithSources());
                word.setType(renameSource(word.getType(), "op", "op1"));

                binaryconjunctionFixedDM++;
                failLogger.add("coord success");
            }
            index++;
        }
    }

    /**
     * (copied and modified from ModifyPrepsInDependencyTrees.java)
     *
     * @param ConjunctGraph Supertag for the conjunct with the coord source and an incident edge representing conjunction
     * @return Pair of modified supertag graph for second conjunct and supertag for conjunction
     */
    private static Pair<SGraph, SGraph> prepareNewDMSupertags(SGraph ConjunctGraph, boolean changeRootPosition, String coordRepl, String LexRepl) {
        String sourceNameToGetRidOf = "coord";
        SGraph newConjunctGraph = new SGraph();
        SGraph ConjunctionGraph = new SGraph();
        // todo avoid magic strings
        GraphNode conjunctionRootNode = null;
        GraphNode oldRootNode = null;

        String slotNode = ConjunctGraph.getNodeForSource(sourceNameToGetRidOf);
        for (GraphNode node : ConjunctGraph.getGraph().vertexSet()) {
            Collection<String> sources = ConjunctGraph.getSourcesAtNode(node.getName());
            if (sources.contains("root")) {
                // not checked whether also contains coord, but assume that this never happens? maybe worth checking with assert..
                oldRootNode = node;
                newConjunctGraph.addNode(node.getName(), node.getLabel());
                conjunctionRootNode = ConjunctionGraph.addNode(node.getName(), null);
                for (String source : sources) {
                    newConjunctGraph.addSource(source, node.getName());
                }
                if (!changeRootPosition) ConjunctionGraph.addSource("root", node.getName());
                ConjunctionGraph.addSource(LexRepl, node.getName());
            } else if (sources.contains(sourceNameToGetRidOf)) {
                if (sources.size() > 1) {
                    System.err.println("More than one source on coord node!");
                }
                if (node.getLabel() != null) {
                    System.err.println("non-null label found: "+node.getLabel());
                }
                ConjunctionGraph.addNode(node.getName(), null);
                if (changeRootPosition) ConjunctionGraph.addSource("root", node.getName());
                ConjunctionGraph.addSource(coordRepl, node.getName());
            } else {
                newConjunctGraph.addNode(node.getName(), node.getLabel());
                for (String source : sources) {
                    newConjunctGraph.addSource(source, node.getName());
                }
            }
        }
        // add edges either to retGraph or retEdge graph:
        // all edges with slotNode as incident node are added to retEdge, all others to retGraph
        for (GraphEdge edge : ConjunctGraph.getGraph().edgeSet()) {
            if (edge.getSource().getName().equals(slotNode) || edge.getTarget().getName().equals(slotNode)) {
                GraphNode esrc = edge.getSource();
                GraphNode etrg = edge.getTarget();
                if (esrc == oldRootNode) esrc = conjunctionRootNode; // otherwise label--LEX-- is set implicitly!
                if (etrg == oldRootNode) etrg = conjunctionRootNode;
                ConjunctionGraph.addEdge(esrc, etrg, edge.getLabel());
            } else {
                newConjunctGraph.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
            }
        }
        return new Pair<>(newConjunctGraph, ConjunctionGraph);
    }

    public int getDeterminerFixedPSD() {
        return determinerFixedPSD;
    }

    public int getDeterminer() {
        return determiner;
    }
}
