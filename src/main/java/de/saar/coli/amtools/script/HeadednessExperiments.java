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
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.eclipse.collections.impl.factory.Sets;
import se.liu.ida.nlp.sdp.toolkit.graph.Graph;
import se.liu.ida.nlp.sdp.toolkit.io.GraphReader2015;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class HeadednessExperiments {

    //SDP corpora (i.e. original graphs)
    @Parameter(names = {"--corpusDM", "-dm"}, description = "Path to the input corpus (en.dm.sdp) or subset thereof")
    private String corpusPathDM = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.dm.sdp";

    @Parameter(names = {"--corpusPAS", "-pas"}, description = "Path to the input corpus (en.pas.sdp) or subset thereof")
    private String corpusPathPAS = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.pas.sdp";

    @Parameter(names = {"--corpusPSD", "-psd"}, description = "Path to the input corpus (en.psd.sdp) or subset thereof")
    private String corpusPathPSD = "../../data/corpora/semDep/sdp2014_2015/data/2015/en.psd.sdp";

    // amconll files (i.e. AM dependency trees)
    @Parameter(names = {"--amconllDM", "-amdm"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathDM = "../../data/corpora/semDep/uniformify2020/original_decompositions/dm/train/train.amconll";

    @Parameter(names = {"--amconllPAS", "-ampas"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPAS = "../../data/corpora/semDep/uniformify2020/original_decompositions/pas/train/train.amconll";

    @Parameter(names = {"--amconllPSD", "-ampsd"}, description = "Path to the input corpus (.amconll) or subset thereof")
    private String amconllPathPSD = "../../data/corpora/semDep/uniformify2020/original_decompositions/psd/train/train.amconll";


    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    private static DMBlobUtils dmBlobUtils = new DMBlobUtils();
    private static PASBlobUtils pasBlobUtils = new PASBlobUtils();
    private static PSDBlobUtils psdBlobUtils = new PSDBlobUtils();

    /**
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException {
        //just getting command line args
        HeadednessExperiments cli = new HeadednessExperiments();
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
        GraphReader2015 grDM = new GraphReader2015(cli.corpusPathDM);
        GraphReader2015 grPAS = new GraphReader2015(cli.corpusPathPAS);
        GraphReader2015 grPSD = new GraphReader2015(cli.corpusPathPSD);
        Graph dmGraph;
        Graph pasGraph;
        Graph psdGraph;
        List<AmConllSentence> amDM = AmConllSentence.read(new FileReader(cli.amconllPathDM));
        System.err.println("read DM amconll");
        List<AmConllSentence> amPAS = AmConllSentence.read(new FileReader(cli.amconllPathPAS));
        System.err.println("read PAS amconll");
        List<AmConllSentence> amPSD = AmConllSentence.read(new FileReader(cli.amconllPathPSD));
        System.err.println("read PSD amconll");
        // map IDs to AmConllSentences so we can look the AmConllSentences up
        Map<String, AmConllSentence> id2amDM = new HashMap<>();
        amDM.stream().forEach(sent -> id2amDM.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPAS = new HashMap<>();
        amPAS.stream().forEach(sent -> id2amPAS.put(sent.getId(), sent));
        Map<String, AmConllSentence> id2amPSD = new HashMap<>();
        amPSD.stream().forEach(sent -> id2amPSD.put(sent.getId(), sent));
        Set<String> decomposedIDs = Sets.intersectAll(id2amDM.keySet(), id2amPAS.keySet(), id2amPSD.keySet());

        Counter<Integer> chainLengths = new Counter<>();

        int count = 0;
        while ((dmGraph = grDM.readGraph()) != null && (pasGraph = grPAS.readGraph()) != null && (psdGraph = grPSD.readGraph()) != null) {
            count++;
            System.err.println(count);
            if (count%100 == 0) {
                System.err.println(count);
            }
            if (decomposedIDs.contains(dmGraph.id)) {
                //now we know the graph was decomposed in all graphbanks, and we have all three AM dep trees for it.
                String id = dmGraph.id;
                AmConllSentence dmDep = id2amDM.get(id);
                AmConllSentence pasDep = id2amPAS.get(id);
                AmConllSentence psdDep = id2amPSD.get(id);
                //ignore 0 in next loop, since it is the artificial root of the SDP graph
                Set<IntList> chains = new HashSet<>();
                for (int i = 1; i < psdGraph.getNNodes(); i++) {
                    IntList chain = increaseChain(dmDep, pasDep, psdDep, i, 0, 0);
                    chains.add(chain);
                    if (chain.size() > 5) {
                        System.err.println(chain);
                    }
                }
                Set<IntList> maximalChains = chains.stream().filter(chain -> containsSuperset(chains, chain)).collect(Collectors.toSet());
                for (IntList chain : maximalChains) {
                    chainLengths.add(chain.size());
                }
            }
        }

        chainLengths.printAllSorted();

    }

    /**
     * returns true iff chains contains a strict superset of chain.
     * @param chains
     * @param chain
     * @return
     */
    private static boolean containsSuperset(Set<IntList> chains, IntList chain) {
        for (IntList possibleSuper : chains) {
            if (!possibleSuper.equals(chains) && possibleSuper.containsAll(chain)) {
                return true;
            }
        }
        return false;
    }

    private static IntList increaseChain(AmConllSentence dmDep, AmConllSentence pasDep, AmConllSentence psdDep,
                                         int dmHead, int pasDirection, int psdDirection) {
        IntList ret = new IntArrayList();
        ret.add(dmHead);
        AmConllEntry newDMHead = dmDep.getParent(dmHead-1);
        if (newDMHead == null) {
            return ret;
        }
        int newDMHeadId = newDMHead.getId();
        if (newDMHeadId > 0) {
            if (hasEdgeBetween(psdDep, newDMHeadId, dmHead) && hasEdgeBetween(pasDep, newDMHeadId, dmHead)) {
                int newPASDirection = hasInverseEdge(pasDep, newDMHeadId, dmHead) ? -1 : 1;// -1 means the chain goes the opposite direction as DM, 1 means the same direction
                int newPSDDirection = hasInverseEdge(psdDep, newDMHeadId, dmHead) ? -1 : 1;
                if ((newPASDirection == pasDirection || pasDirection == 0)//direction must be consistent (set direction if pasDirection was 0, i.e. unset)
                    && (newPSDDirection == psdDirection || psdDirection == 0)
                    && !(newPASDirection == 1 && newPSDDirection == 1)) {//avoid chains that are the same direction in all graphs
                    ret.addAll(increaseChain(dmDep, pasDep, psdDep, newDMHeadId, newPASDirection, newPSDDirection));
                }
            }
        }
        return ret;
    }

    private static boolean hasInverseEdge(AmConllSentence dep, int headId, int childId) {
        if (dep.getParent(headId-1) == null) {
            return false;
        }
        return dep.getParent(headId-1).getId() == childId;
    }


    private static boolean hasEdgeBetween(AmConllSentence dep, int id1, int id2) {
        return hasEdgeBetween(dep, Collections.singleton(id1), Collections.singleton(id2));
    }

    private static boolean hasEdgeBetween(AmConllSentence dep, Collection<Integer> ids1, Collection<Integer> ids2) {
        for (Integer i : ids1) {
            for (Integer j : ids2) {
                if (dep.get(i-1).getHead() == j || dep.get(j-1).getHead() == i) {
                    return true;
                }
            }
        }
        return false;
    }



}
