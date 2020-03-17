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
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
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
    
    private int negations = 0;
    private int negationsFixedPSD = 0;
    private int negationsFixedPAS = 0;

    private int copula = 0;
    private int copulaFixedDM = 0;

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
        
        ModifyDependencyTrees treeModifier = new ModifyDependencyTrees();

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
                treeModifier.fixNegation(psdDep, dmDep, pasDep);
                treeModifier.fixPunctuation(psdDep, dmDep, pasDep);
                treeModifier.fixAdjCopula(psdDep, dmDep, pasDep);


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
        
        System.out.println("Negations:");
        System.out.println(treeModifier.negations);
        System.out.println("Fixed in PSD:");
        System.out.println(treeModifier.negationsFixedPSD);
        System.out.println("Fixed in PAS:");
        System.out.println(treeModifier.negationsFixedPAS);

        System.out.println("Copula found: "+treeModifier.copula
                + " fixed: " + treeModifier.copulaFixedDM
                + " : %fixed " + (treeModifier.copula == 0 ? 100 : 100*treeModifier.copulaFixedDM / (float)treeModifier.copula));


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
    
   
    
    public void fixNegation(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException, AlignedAMDependencyTree.ConllParserException {
        int index = 0;
        
        SGraph desiredPSDSupertag = new IsiAmrInputCodec().read("(i<root> / --LEX--  :RHEM-of (j<mod>))");
        SGraph desiredPASSupertag = new IsiAmrInputCodec().read("(i_2<root> / --LEX--  :adj_ARG1 (i_3<mod>))");
        SGraph desiredDMSupertag = new IsiAmrInputCodec().read("(i_13<root> / --LEX--  :neg (i_12<mod>))");
        
        //TODO: "never"?
        
        for (AmConllEntry psdEntry : psdDep) {
            AmConllEntry dmEntry = dmDep.get(index);
            AmConllEntry pasEntry = pasDep.get(index);
            if (psdEntry.getLemma().equals("#Neg")){ // psdEntry is Negation word
                // find verb or thing that is negated in DM: could be none, therefore use Optional
                // outgoing dep. edges in DM from negation: if it's an APPmod edge, its target is the negated thing
                this.negations ++;
                Optional<AmConllEntry> potential_argument = dmDep.getChildren(index).stream().filter(child -> child.getEdgeLabel().equals("APP_mod")).findFirst();
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
                        ApplyModifyGraphAlgebra.Type negatedType = psdAlignedDeptree.getTermTypeAt(psdNegated); // type of the negated thing

                        SGraph supertag = new IsiAmrInputCodec().read(psdEntry.getDelexSupertag());
                        
                        if (desiredPSDSupertag.equals(supertag)) {
                            // only change if negated element doesn't have a mod source
                            try {
                                // take term type of negated element, add neg source and create dependencies such that the requirement 
                                // at the neg source is the type of the negated element.
                                Set<String> origins = negatedType.getOrigins();
                                Type negationType = negatedType.addSource("neg");
                                for (String orignalOrigin : origins){
                                    negationType = negationType.setDependency("neg", orignalOrigin, orignalOrigin);
                                }
                                // change head
                                psdEntry.setHead(psdNegated.getHead());
                                psdEntry.setEdgeLabel(psdNegated.getEdgeLabel());
                                // flip edge
                                psdNegated.setHead(psdEntry.getId());
                                psdNegated.setEdgeLabel("APP_neg");

                                psdEntry.setType(negationType);

                                // supertag:
                                //   negation supertag has additional source at root!
                                psdEntry.setDelexSupertag("(i / --LEX--  :RHEM-of (j<root, neg>))");
                                negationsFixedPSD++;
                             } catch (IllegalArgumentException ex) { // introduces a cycle by adding the mod source and the dependencies
                             }

                        }


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
                        ApplyModifyGraphAlgebra.Type negatedType = pasAlignedDeptree.getTermTypeAt(pasNegated);

                        SGraph supertag = new IsiAmrInputCodec().read(pasEntry.getDelexSupertag());
                        if (desiredPASSupertag.equals(supertag)) {
                            try {
                                // see PSD
                                Set<String> origins = negatedType.getOrigins();
                                Type negationType = negatedType.addSource("neg");
                                for (String orignalOrigin : origins){
                                    negationType = negationType.setDependency("neg", orignalOrigin, orignalOrigin);
                                }

                                // change head
                                pasEntry.setHead(pasNegated.getHead());
                                pasEntry.setEdgeLabel(pasNegated.getEdgeLabel());
                                // flip edge
                                pasNegated.setHead(pasEntry.getId());
                                pasNegated.setEdgeLabel("APP_neg");

                                // supertag:
                                //   negation supertag has additional source at root!
                                pasEntry.setDelexSupertag("(i / --LEX--  :adj_ARG1 (j<root, neg>))");
                                pasEntry.setType(negationType);
                                
                                negationsFixedPAS++;
                            } catch (IllegalArgumentException ex) { // introduces a cycle by adding the mod source and the dependencies
                            }
                            
                        }


                    }
                    
                    // DEBUG TODO maybe look at what is actually negated this way? only verbs?
                } // found negated argument

            } // found #Neg lemma
            index++;
        } // for psdEntry
    }


    public void fixPunctuation(AmConllSentence psdDep, AmConllSentence dmDep, AmConllSentence pasDep) throws ParseException{
        // Punctuation
        // in PAS sometimes punctuation integrated in graph, but not in psd and dm:
        // fix: DM, psd create one-node blob with pnct and root source for them
        // todo avoid magic strings if possible (import static strings from elsewhere?)
        String ignore_edge = "IGNORE";
        String mod_punct = "MOD_pnct";
        int index = 0;
        for (AmConllEntry word : pasDep){
            if (word.getEdgeLabel().equals(mod_punct)){
                //assert (pasDep.getParent(index) != null) // parent should be head of the MOD_punct relation
                int parentID_pas = pasDep.getParent(index).getId(); // index is 0-based, parentID_pas is 1-based

                // A. PSD if ignored, change to pas like structure
                AmConllEntry psdWord = psdDep.get(index);
                //assert psdWord != null; // same sentence , same size
                if (psdWord.getEdgeLabel().equals(ignore_edge)
                        && !psdDep.get(parentID_pas-1).getEdgeLabel().equals(ignore_edge)) {
                    // second term: skip if parent in PAS is ignored in PSD??? todo should i do this?
                    // delete ignore edge, create edge parent-punctuation
                    // create supertag for punctuation
                    psdWord.setEdgeLabel(mod_punct); // was previously ignore
                    psdWord.setHead(parentID_pas); // was previously artificial root
                    psdWord.setDelexSupertag("(u<root, pnct>)");// empty modifier graph: one unlabeled node with root and det source.
                    psdWord.setType(new ApplyModifyGraphAlgebra.Type("(pnct)"));
                }

                // B. DM if ignored, change to pas like structure
                // todo copy pasted code, but what is the best way in java here to avoid it?
                AmConllEntry dmWord = psdDep.get(index);
                if (dmWord.getEdgeLabel().equals(ignore_edge) && !dmDep.get(parentID_pas-1).getEdgeLabel().equals(ignore_edge)) {
                    // second term: skip if parent in PAS is ignored in DM??? todo should i do this?
                    // delete ignore edge, create edge parent-punctuation
                    // create supertag for punctuation
                    dmWord.setEdgeLabel(mod_punct); // was previously ignore
                    dmWord.setHead(parentID_pas); // was previously artificial root
                    dmWord.setDelexSupertag("(u<root, pnct>)");// empty modifier graph: one unlabeled node with root and det source.
                    word.setType(new ApplyModifyGraphAlgebra.Type("(pnct)"));
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


}
