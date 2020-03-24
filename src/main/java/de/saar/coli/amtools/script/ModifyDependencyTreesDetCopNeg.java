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
import de.up.ling.tree.ParseException;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.*;
import java.util.*;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;

public class ModifyDependencyTreesDetCopNeg {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.dm.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.pas.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "/home/matthias/Schreibtisch/Hiwi/Koller/uniformify2020/original_decompositions/en.psd.sdp";

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

    //private int determiner = 0;
    //private int determinerFixedPSD = 0;

    private int negations = 0;
    private int negationsFixedPSD = 0;
    private int negationsFixedPAS = 0;
    private int negationsAllFixed = 0;
    
    private int never = 0;
    private int neverFixedPSD = 0;
    private int neverFixedPAS = 0;
    private int neverAllFixed = 0;
   
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
	
    private int punctuation = 0;
    private int punctuationFixedPSD = 0;
    private int punctuationFixedDM = 0;
    private int punctuationAllFixed = 0;

    private int binaryconjunction = 0;
    private int binaryconjunctionFixedDM = 0;

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


                    //modify new dep trees here
                    //treeModifier.fixDeterminer(psdDep, dmDep, pasDep);
                    fixDeterminer(psdDep, dmDep, pasDep);
                    treeModifier.fixNegation(psdDep, dmDep, pasDep);
                    treeModifier.fixNever(psdDep, dmDep, pasDep);
                    treeModifier.fixPunctuation(psdDep, dmDep, pasDep);
                    treeModifier.fixAdjCopula(psdDep, dmDep, pasDep);
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
                } catch (IllegalArgumentException ex){ // evaluation of original AM dep tree failed
                    System.err.println("(Graph ID "+ id + "): Skipping sentence because:");
                    ex.printStackTrace();
                }
            }
        }

        AmConllSentence.write(new FileWriter(cli.outputPath+"/dm.amconll"), newAmDM);
        AmConllSentence.write(new FileWriter(cli.outputPath+"/pas.amconll"), newAmPAS);
        AmConllSentence.write(new FileWriter(cli.outputPath+"/psd.amconll"), newAmPSD);

        System.out.println("Decomposable graphs: " + decomposableGraphs);

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

    public static void fixDeterminer(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Determiners for PSD
        // we find all words that are determiners (have DT POS tag)
        // and that are ignored in the PSD graph (have incoming IGNOREn edge).
        // Then we change the PSD entry to have an empty modifier graph,
        // and attach it to the head it has in DM.
        int index = 0;
        for (AmConllEntry word : psdDep){
            if (word.getPos().equals("DT") && word.getEdgeLabel().equals("IGNORE")){
                // determiner++;
                // System.err.println(index);
                // System.err.println(dmDep.getParent(index));
                if (dmDep.getParent(index) == null) continue; // if DM ignored determiner as well, skip this
                int head = dmDep.getParent(index).getId();// index is 0-based, head is 1-based
                word.setHead(head);
                word.setEdgeLabel("MOD_det");
                word.setDelexSupertag("(u<root, det>)");// empty modifier graph: one unlabeled node with root and det source.
                word.setType(new ApplyModifyGraphAlgebra.Type("(det)")); // the type of the DelexSupertag
                // determinerFixedPSD++;
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
            
            if (dmEntry.getLemma().equals("never")){
                this.never++;
                
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
                        
                        swapHead(psdTree, psdEntry, psdDep.getParent(index), "neg");
                        neverFixedPSD++;
                        fixedPSD = true;
                        
                    }
                    
                    // fix PAS, same situation as in PSD
                    AmConllEntry pasEntry = pasDep.get(index);
                    if (pasEntry.getEdgeLabel().equals("MOD_mod") && desiredPASSupertag.equals(new IsiAmrInputCodec().read(pasEntry.getDelexSupertag()))
                            && pasEntry.getHead() == dmArgument.getId() // do we want this condition?
                            ){
                        
                        swapHead(pasTree, pasEntry, pasDep.getParent(index), "neg");
                        neverFixedPAS++;
                        
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
        
        
        AlignedAMDependencyTree dmTree = AlignedAMDependencyTree.fromSentence(dmDep);
        
        boolean fixedPSD = false;
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);
            if (psdEntry.getLemma().equals("#Neg")){ // psdEntry is Negation word
                this.negations ++;
                fixedPSD = false;
                // In DM we can be in the situation that the negation word is the head with outgoing APP_mod edge
                // or - in relative clauses - , the negation would be the depndent of the verb and we have an incoming MOD_mod edge
                
                if (dmDep.getChildren(index).isEmpty() && new IsiAmrInputCodec().read(dmEntry.getDelexSupertag()).equals(desiredDMSupertag) && dmEntry.getEdgeLabel().equals("MOD_mod")){
                    // we are probably in a relative clause
                    // so we first make DM consistent that the negation word is the head, then we can apply the transformation for PSD and PAS
                    
                    // let's make sure that we are in a relative clause and see what the clause modifies
                    AmConllEntry negatedDMverb = dmDep.getParent(index);
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
                    if (psdEntry.getEdgeLabel().equals("MOD_mod") &&
                            psdEntry.getHead() == dmArgument.getId()) {
                        // fix PSD
                        AlignedAMDependencyTree psdAlignedDeptree = AlignedAMDependencyTree.fromSentence(psdDep);
                        AmConllEntry psdNegated = psdDep.getParent(index); // verb or sth else

                        SGraph supertag = new IsiAmrInputCodec().read(psdEntry.getDelexSupertag());
                        
                        if (desiredPSDSupertag.equals(supertag)) {
                            // only change if negated element doesn't have a mod source
                            try {
                                // take term type of negated element, add neg source and create dependencies such that the requirement 
                                // at the neg source is the type of the negated element.
                                swapHead(psdAlignedDeptree, psdEntry, psdNegated, "neg");
                                negationsFixedPSD++;
                                fixedPSD = true;
                             } catch (IllegalArgumentException ex) { // introduces a cycle by adding the mod source and the dependencies
                             }

                        } else { //PSD has unexpected supertag, no case in dev data.

                        }


                    } else { //PSD doesn't have MOD_mod edge in right place:

                        
                    }

                    // PAS
                    //  - currently   --> argument --MOD_mod--> pasEntry (negation)
                    //  - would like:   argument <--APP_mod-- pasEntry (Negation) <--
                    //        plus changed Negation supertag (root source added at mod source place)
                    
                    if (pasEntry.getEdgeLabel().equals("MOD_mod") &&
                            pasEntry.getHead() == dmArgument.getId()) {
                        // fix PAS
                        AlignedAMDependencyTree pasAlignedDeptree = AlignedAMDependencyTree.fromSentence(pasDep);
                        AmConllEntry pasNegated = pasDep.getParent(index); // verb or sth else

                        SGraph supertag = new IsiAmrInputCodec().read(pasEntry.getDelexSupertag());
                        if (desiredPASSupertag.equals(supertag)) {
                            try {
                                swapHead(pasAlignedDeptree, pasEntry, pasNegated, "neg");
                                
                                negationsFixedPAS++;
                                if (fixedPSD) negationsAllFixed++;
                            } catch (IllegalArgumentException ex) { // introduces a cycle by adding the mod source and the dependencies
                            }
                            
                        } else { //PAS uses unexpected supertag, no cases in gold dev.
                          
                        }


                    } else { //PAS doesn't have MOD_mod edge in expected place:

                    }
                    
                    // DEBUG TODO maybe look at what is actually negated this way? only verbs?
                } // found negated argument 
                else {
                   // System.err.println(dmDep.getId());
                   // System.err.println(dmDep);
                }

            } // found #Neg lemma
            index++;
        } // for psdEntry
    }


    public void fixPunctuation(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Punctuation
        // in PAS sometimes punctuation integrated in graph, but not in PSD and DM:
        // fix: DM, PSD create one-node blob with pnct and root source for them
        // todo avoid magic strings if possible (import static strings from elsewhere?)
        String ignore_edge = "IGNORE";
        String mod_punct = "MOD_pnct";
        int index = 0;
        for (AmConllEntry word : pasDep){
            if (word.getEdgeLabel().equals(mod_punct)){ // TODO maybe check if APPpnct exists?
                punctuation++;
                boolean fixedPSD = false;
                boolean fixedDM = false;
                // variable index now points to punctuation symbol, AmConnlEntry word is punctuation entry
                //assert (pasDep.getParent(index) != null) // parent should be head of the MOD_punct relation
                int parentID_pas = pasDep.getParent(index).getId(); // index is 0-based, parentID_pas is 1-based

                // A. PSD if ignored, change to PAS like structure
                AmConllEntry psdWord = psdDep.get(index); // punctuation is PSD
                //assert psdWord != null; // same sentence , same size
                if (psdWord.getEdgeLabel().equals(ignore_edge)
                        && !psdDep.get(parentID_pas-1).getEdgeLabel().equals(ignore_edge)) {
                    // second term: skip if parent in PAS is ignored in PSD??? todo should i do this?
                    // delete ignore edge, create edge parent-punctuation
                    // create supertag for punctuation
                    psdWord.setEdgeLabel(mod_punct); // was previously ignore
                    psdWord.setHead(parentID_pas); // was previously artificial root probably
                    psdWord.setDelexSupertag("(u<root, pnct>)");// empty modifier graph: one unlabeled node with root and det source.
                    psdWord.setType(new ApplyModifyGraphAlgebra.Type("(pnct)"));
                    punctuationFixedPSD++;
                    fixedPSD = true;
                }

                // B. DM if ignored, change to pas like structure
                // todo copy pasted code, but what is the best way in java here to avoid it?
                AmConllEntry dmWord = dmDep.get(index);
                if (dmWord.getEdgeLabel().equals(ignore_edge) && !dmDep.get(parentID_pas-1).getEdgeLabel().equals(ignore_edge)) {
                    // second term: skip if parent in PAS is ignored in DM???
                    // delete ignore edge, create edge parent-punctuation
                    // create supertag for punctuation
                    dmWord.setEdgeLabel(mod_punct); // was previously ignore
                    dmWord.setHead(parentID_pas); // was previously artificial root
                    dmWord.setDelexSupertag("(u<root, pnct>)");// empty modifier graph: one unlabeled node with root and det source.
                    dmWord.setType(new ApplyModifyGraphAlgebra.Type("(pnct)"));
                    punctuationFixedDM++;
                    fixedDM = true;
                }

                // if both DM and PSD fixed : increment counter
                if (fixedDM && fixedPSD) punctuationAllFixed++;
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
        String ignore = "IGNORE";
        int index = 0;
        for (AmConllEntry word : psdDep){
            // word lemma be
            if (word.getLemma().equals("be")){ // todo choose other framework for lemma - does this make a difference?
                AmConllEntry subjectPAS = null;
                AmConllEntry adjectivePAS = null;
                // 1.find arguments of to be
                for (AmConllEntry child: pasDep.getChildren(index)) {
                    if (child.getEdgeLabel().equals(apps)) {
                        subjectPAS = child;
                    }
                    else if (child.getEdgeLabel().equals(appo) && child.getPos().startsWith("JJ")) {
                        adjectivePAS = child;
                    }
                }
                if (subjectPAS == null || adjectivePAS == null) continue; // at least one argument not found -> skip
                int subj0pas = subjectPAS.getId()-1; // 0-based
                int adj0pas = adjectivePAS.getId()-1;

                // 2. check if psd and dm have expected structure
                // 2.1 PSD:   verb--APPs->subject and verb--APPo->adjective
                AmConllEntry verbPSD = psdDep.get(index);
                AmConllEntry subjPSD = psdDep.get(subj0pas);
                AmConllEntry adjPSD = psdDep.get(adj0pas);
                if (subjPSD.getHead() != verbPSD.getId() || !subjPSD.getEdgeLabel().equals(apps)) continue;
                if (adjPSD.getHead() != verbPSD.getId() || !adjPSD.getEdgeLabel().equals(appo)) continue;
                // todo check 0-based, 1-based not confused? getHead, getId comparable?

                // 2.2 DM:    verb ignored,   adjective--APPs-> subject
                AmConllEntry verbDM = dmDep.get(index);
                AmConllEntry subjDM = dmDep.get(subj0pas);
                AmConllEntry adjDM = dmDep.get(adj0pas);
                if (!verbDM.getEdgeLabel().equals(ignore)) continue;
                if (!subjDM.getEdgeLabel().equals(apps) || subjDM.getHead() != adjDM.getId()) continue;
                // todo check 0-based, 1-based not confused? getHead, getId comparable?
                // todo check if incoming edge is the same across PSD, DM, PAS? -> other phenomena?

                copula++;
                if (!adjDM.getType().equals(unproblematic_adj_type)) { // TODO: skip for now, but maybe find better solution?
                    //System.err.println("Can't fix copula for now: problematic adjective type: " + adjDM.getType().toString() + " for adjective " + adjDM.getForm());
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
            }
            index++;
        }
    }

    /**
     * Fixing Binary conjunction:
     * detection pattern:
     * - CC pos tag in PSD with APP_op and APP_op2 (and no other APP_opX edges) +
     * DM ignores the conjunction and conjuncts are connected directly using MOD_coord +
     * the head of PSD's conjunction is the same as that of DM's first conjunct
     * Changes:
     * - DM MOD_coord structure changed to APP_op1, APP_op2 structure like PSD/PAS (conjunction no longer ignored)
     * - PSD's APP_op changed to APP_op1 (plus subsequent changes in the types and supertags)
     * TODO: APP_coord (not only MOD_coord); order (op, op2 vs op2,op) shouldn't matter?;
     * */
    public void fixBinaryConjuction(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException, ParserException {
        ApplyModifyGraphAlgebra.Type unproblematic_coord_type = new ApplyModifyGraphAlgebra.Type("(coord)");

        String appop = "APP_op";
        String appop1 = "APP_op1";
        String appop2 = "APP_op2";
        String modcoord = "MOD_coord";
        String ignore = "IGNORE";
        // todo avoid magic strings for edge labels and also sources? maybe import sources from BlobUtils?
        int index = 0;
        for (AmConllEntry word : psdDep){
            // CC postag (conjunction)
            if (word.getPos().equals("CC")){
                // PSD 2 APP children (APP_op and APP_op1)
                // DM 1 coord edge

                AmConllEntry firstConjunctPSD = null;
                AmConllEntry secondConjunctPSD = null;
                boolean multiconj = false;

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
                int psdconj1idx = firstConjunctPSD.getId()-1; // 0-based
                int psdconj2idx = secondConjunctPSD.getId()-1;
                // do we need to check the type of PSD's conjunction? e.g. equals (op,op2) or related
                // todo maybe verify if also same structure in PAS?

                // 2. Find DM structure
                // coord edge between conjuncts?
                AmConllEntry firstConjunctDM = dmDep.get(psdconj1idx);
                AmConllEntry secondConjunctDM = dmDep.get(psdconj2idx);
                AmConllEntry conjunctionDM = dmDep.get(index);
                // a) conjunction node ignored in DM
                if (!conjunctionDM.getEdgeLabel().equals(ignore)) continue;
                // b) MOD_coord edge between first and second // todo check reverse direction of op, op2 ! and app coord!
                if (!secondConjunctDM.getEdgeLabel().equals(modcoord) || secondConjunctDM.getHead() != firstConjunctDM.getId()) continue;
                // c) head of firstConjunctDM is same as head of PSD conjunction node
                if (firstConjunctDM.getHead() != word.getHead()) continue;

                binaryconjunction++;
                /*if (!secondConjunctDM.getType().equals(unproblematic_coord_type)) {
                    // skip for now, but maybe find better solution?  (source percolation)
                    // when these instances are no longer excluded, also change the setType for secondConjunct (further down) accordingly
                    System.err.println("Can't fix coordination for now: problematic type in DM: "
                            + secondConjunctDM.getType().toString() + " for second conjunct " + secondConjunctDM.getForm());
                    continue;
                }*/

                // 3. change DM (and PSD source names...
                // - [DM] change head from first conjunct to conjunction
                int headDM = firstConjunctDM.getHead();
                String toconjlabel = firstConjunctDM.getEdgeLabel();
                conjunctionDM.setHead(headDM);
                conjunctionDM.setEdgeLabel(toconjlabel); // was previously ignore
                // - [DM] conjuncts receive incoming edge from conjunction node
                firstConjunctDM.setHead(conjunctionDM.getId());
                secondConjunctDM.setHead(conjunctionDM.getId());
                firstConjunctDM.setEdgeLabel(appop1);
                secondConjunctDM.setEdgeLabel(appop2); // was previously MODcoord
                // - [DM] change supertag of second conjunct (delete conjunction edge in supertag plus incident coord node)
                Pair<SGraph, SGraph> supertags =  prepareNewDMSupertags(secondConjunctDM.delexGraph());
                secondConjunctDM.setDelexSupertag(supertags.left.toIsiAmrStringWithSources());
                // - [DM] change type of second conjunct from coord, to nothing
                //secondConjunctDM.setType(new ApplyModifyGraphAlgebra.Type("()")); // was  "(coord)"  previously
                secondConjunctDM.setType(secondConjunctDM.getType().performApply("coord"));// todo is this the right way to remove the coord source?
                // - [DM] create supertag for conjunction in DM (use edge deleted from secondConj supertag) plus type
                conjunctionDM.setDelexSupertag(supertags.right.toIsiAmrStringWithSources());
                // something like conjunctionDM.setDelexSupertag("(u<root, op1> :_and_c (v<op2>))");  but not just for :_and_c edge,
                // not sure about directionaly (_and_c or _and_c-of ?)
                conjunctionDM.setType(new ApplyModifyGraphAlgebra.Type("(op1, op2)"));// todo is this problematic?
                // - [PSD] change APP_op to APP_op1 and corresponding type change
                firstConjunctPSD.setEdgeLabel(appop1); // was previously APP_op  note the absence of the number 1
                String oldsupertag = word.getDelexSupertag();
                String newsupertag = oldsupertag.replaceFirst("<op>", "<op1>");
                // todo this is kind of a hack and a bit dangerous: we assume that there is only one <op> in the supertag
                word.setDelexSupertag(newsupertag);
                word.setType(renameSource(word.getType(), "op", "op1"));

                binaryconjunctionFixedDM++;
            }
            index++;
        }
    }

    /**
     * (copied and modified from ModifyPrepsInDependencyTrees.java)
     *
     * @param secondConjunctGraph Supertag for second conjunct (that one with the coord source)
     * @return Pair of modified supertag graph for second conjunct and supertag for conjunction
     */
    private static Pair<SGraph, SGraph> prepareNewDMSupertags(SGraph secondConjunctGraph) {
        String sourceNameToGetRidOf = "coord";
        SGraph newSecondConjunctGraph = new SGraph();
        SGraph ConjunctionGraph = new SGraph();
        // todo what about --LEX-- for new conjunction supertag?
        // todo avoid magic strings

        String slotNode = secondConjunctGraph.getNodeForSource(sourceNameToGetRidOf);
        for (GraphNode node : secondConjunctGraph.getGraph().vertexSet()) {
            Collection<String> sources = secondConjunctGraph.getSourcesAtNode(node.getName());
            if (sources.contains("root")) {
                // not checked whether also contains coord, but assume that this never happens? maybe worth checking with assert..
                newSecondConjunctGraph.addNode(node.getName(), node.getLabel());
                ConjunctionGraph.addNode(node.getName(), null); // todo label for root node of conjunction graph?
                for (String source : sources) {
                    newSecondConjunctGraph.addSource(source, node.getName());
                }
                //ConjunctionGraph.addSource("root", node.getName());
                ConjunctionGraph.addSource("op2", node.getName());
            } else if (sources.contains(sourceNameToGetRidOf)) {
                if (sources.size() > 1) {
                    System.err.println("More than one source on coord node!");
                }
                if (node.getLabel() != null) {
                    System.err.println("non-null label found: "+node.getLabel());
                }
                ConjunctionGraph.addNode(node.getName(), null);
                ConjunctionGraph.addSource("root", node.getName());
                ConjunctionGraph.addSource("op1", node.getName());
            } else {
                newSecondConjunctGraph.addNode(node.getName(), node.getLabel());
                for (String source : sources) {
                    newSecondConjunctGraph.addSource(source, node.getName());
                }
            }
        }
        // add edges either to retGraph or retEdge graph:
        // all edges with slotNode as incident node are added to retEdge, all others to retGraph
        for (GraphEdge edge : secondConjunctGraph.getGraph().edgeSet()) {
            if (edge.getSource().getName().equals(slotNode) || edge.getTarget().getName().equals(slotNode)) {
                ConjunctionGraph.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
            } else {
                newSecondConjunctGraph.addEdge(edge.getSource(), edge.getTarget(), edge.getLabel());
            }
        }
        return new Pair<>(newSecondConjunctGraph, ConjunctionGraph);
    }

}
