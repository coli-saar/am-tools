/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.wordnet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import de.up.ling.irtg.util.Util;
import edu.mit.jwi.item.POS;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.intertextueel.conceptnet.Concept;
import net.intertextueel.conceptnet.Concept.Pos;
import net.intertextueel.conceptnet.ConceptNet;
import net.intertextueel.conceptnet.ConceptNetFilter;
import net.intertextueel.conceptnet.ConceptNetRelation;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

/**
 *
 * @author koller
 */
public class ConceptnetEnumerator implements IWordnet {

    private final static int SEARCH_DEPTH = 3;
    private final static double BAD_COST = 1.2;
    private final static double GOOD_COST = 0.2;
    private final static double COST_THRESHOLD = 1.8; //with current SEARCH_DEPTH, worst score with only one BAD_COST is 1.6, so use this to test for more than 1 BAD_COST.

//    private static final Set<Pointer> GOOD_POINTERS = new HashSet<>(Arrays.asList(new Pointer[]{Pointer.PERTAINYM, Pointer.PARTICIPLE,
//        Pointer.DERIVATIONALLY_RELATED, Pointer.DERIVED_FROM_ADJ, Pointer.SIMILAR_TO, Pointer.ATTRIBUTE}));
    private final static Set<String> GOOD_RELATIONS = ImmutableSet.of("DerivedFrom", "SimilarTo", "Synonym");

    private Map<POS, Pos> wordnetPosToConceptnetPos;

    private ConceptNet conceptnet;
    private WordnetEnumerator wordnet;

    private final Map<String, Set<String>> word2Hypers = new HashMap<>(); // cache for previously calculated hypernyms
    private final Map<String, Object2DoubleMap<String>> wordPairScores;   // score for path between two lemmas

    public static void main(String[] args) throws Exception {
        IWordnet cenum = new ConceptnetEnumerator(new File("/Users/koller/Downloads/conceptnet-assertions-5.7.0.csv.gz"), "amr-dependency-july2019/data/wordnet/dict");
        System.err.println(cenum.getAllNounHypernyms("baker"));
    }

    public ConceptnetEnumerator(File conceptnetFilename, String wordnetDictPath) throws IOException, MalformedURLException, InterruptedException {
        File pickleFilename = new File(conceptnetFilename.getAbsolutePath() + ".pkl");
        conceptnet = null;
        wordnet = new WordnetEnumerator(wordnetDictPath);
        this.wordPairScores = new HashMap<>();

        // create POS map
        wordnetPosToConceptnetPos = ImmutableMap.of(POS.NOUN, Pos.NOUN, POS.VERB, Pos.VERB, POS.ADJECTIVE, Pos.ADJECTIVE);

        // check if conceptnet can be unpickled
        try {
            if (pickleFilename.exists()) {
                System.err.printf("Reading ConceptNet from pickle in %s ...\n", pickleFilename.toString());
                long t1 = System.nanoTime();

                try (FSTObjectInput in = new FSTObjectInput(new FileInputStream(pickleFilename))) {
                    conceptnet = (ConceptNet) in.readObject();
                }

                long t2 = System.nanoTime();
                System.err.printf("Done: read pickle in %s.\n", Util.formatTime(t2-t1));
            }
        } catch (Exception e) {
        }

        // otherwise, read from gzipped CSV file
        if (conceptnet == null) {
            System.err.printf("Reading ConceptNet from CSV in %s ...\n", conceptnetFilename.toString());
            long t1 = System.nanoTime();
            
            ConceptNetFilter filter = new ConceptNetFilter(false, false, new String[]{"en"}, new String[]{"IsA", "HasA", "HasProperty", "Synonym", "DerivedFrom", "MannerOf", "SimilarTo"});
            conceptnet = new ConceptNet(conceptnetFilename.getAbsolutePath(), filter);

            System.err.printf("Done, pickling to %s ...\n", pickleFilename.toString());
            long t2 = System.nanoTime();
            
            try (FSTObjectOutput out = new FSTObjectOutput(new FileOutputStream(pickleFilename))) {
                out.writeObject(conceptnet);
                out.flush();
            }

            long t3 = System.nanoTime();
            System.err.printf("Done: read CSV in %s, pickled in %s.\n", Util.formatTime(t2-t1), Util.formatTime(t3-t2));
        }
    }

    private Concept findFirstConcept(String word, Pos pos) {
        Set<Concept> conceptsForWord = conceptnet.findConcepts(word, pos);

        for (Concept c : conceptsForWord) {
            if (word.equals(c.getWordphrase())) {
                return c;
            }
        }

        return null;
    }

    private void hypernymRecursion(Concept concept, Set<Concept> nounHypernyms) {
        for (ConceptNetRelation rel : conceptnet.getRelationsFrom(concept)) {
            if (rel.getRelationType().equals("IsA")) {
                Concept hypernym = rel.getTargetConcept();

                if (hypernym.getPOS() == Concept.Pos.NOUN) {
                    if (!hypernym.getWordphrase().contains(" ")) {
                        if (nounHypernyms.add(hypernym)) {
                            hypernymRecursion(hypernym, nounHypernyms);
                        }
                    }
                }
            }
        }
    }

    @Override
    public Set<String> getAllNounHypernyms(String word) {
        // TODO - does this take wordforms or stems as arguemnt??

        if (word2Hypers.containsKey(word)) {
            return word2Hypers.get(word);
        }

        Set<Concept> nounHypernyms = new HashSet<>();
        Concept conceptForWord = findFirstConcept(word, Pos.NOUN);
        hypernymRecursion(conceptForWord, nounHypernyms);

        Set<String> ret = de.up.ling.irtg.util.Util.mapToSet(nounHypernyms, concept -> concept.getWordphrase());
        word2Hypers.put(word, ret);
        return ret;
    }

    @Override
    public Set<String> getWNCandidates(String word) {
        if (wordPairScores.containsKey(word)) {
            return wordPairScores.get(word).keySet();
        }

        Set<String> ret = null;
        Object2DoubleMap lemma2score = new Object2DoubleOpenHashMap();
        lemma2score.defaultReturnValue(-1000);
        wordPairScores.put(word, lemma2score);

        if (word.matches("[0-9,.]+")) {
            // word is a number => candidates are all prefixes without trailing zeroes
            ret = new HashSet<>();
            String noCommas = word.replaceAll("[.,]", "");

            while (noCommas.endsWith("0")) {
                ret.add(noCommas);
                noCommas = noCommas.substring(0, noCommas.length() - 1);
            }

            if (noCommas.length() > 0) {
                ret.add(noCommas);
            }
        } else {
            // collect Conceptnet entries for the stems of the given word
            Set<Concept> concepts = new HashSet<>();
            for (POS pos : POS.values()) {
                Pos cnPos = wordnetPosToConceptnetPos.get(pos);

                if (cnPos == null) {
                    continue;
                }

                try {
                    for (String stem : wordnet.findStems(word, pos)) {
                        Concept c = findFirstConcept(stem, cnPos);
                        if (c != null) {
                            concepts.add(c);
                        }
                    }
                } catch (java.lang.IllegalArgumentException ex) {
                    System.err.println("*** WARNING *** " + de.up.ling.irtg.util.Util.getStackTrace(ex));
                }
            }

            // initialize cost table
            Object2DoubleMap<Concept> foundCosts = new Object2DoubleOpenHashMap<>();
            foundCosts.defaultReturnValue(Double.MAX_VALUE);
            for (Concept iW : concepts) {
                foundCosts.put(iW, 0);
            }

            // iterate neighborhood of words
            Set<Concept> explored = new HashSet<>();
            for (int k = 0; k < SEARCH_DEPTH; k++) {
                Set<Concept> toIterate = new HashSet<>(Sets.difference(foundCosts.keySet(), explored));

                for (Concept iW : toIterate) {
                    double costIW = foundCosts.getDouble(iW);
                    explored.add(iW);

                    for (ConceptNetRelation edge : conceptnet.getRelationsFrom(iW)) {
                        double localCost = GOOD_RELATIONS.contains(edge.getRelationType()) ? GOOD_COST : BAD_COST;
                        double newCost = costIW + localCost;
                        if (newCost < COST_THRESHOLD) {
                            foundCosts.put(edge.getTargetConcept(), Math.min(foundCosts.getDouble(edge.getTargetConcept()), newCost));
                        }
                    }

                    for (ConceptNetRelation edge : conceptnet.getRelationsTo(iW)) {
                        double localCost = GOOD_RELATIONS.contains(edge.getRelationType()) ? GOOD_COST : BAD_COST;
                        double newCost = costIW + localCost;
                        if (newCost < COST_THRESHOLD) {
                            foundCosts.put(edge.getTargetConcept(), Math.min(foundCosts.getDouble(edge.getTargetConcept()), newCost));
                        }
                    }
                }
            }

            for (Concept iW : foundCosts.keySet()) {
                lemma2score.put(iW.getWordphrase(), -foundCosts.getDouble(iW));
            }

            ret = Util.mapToSet(foundCosts.keySet(), iWord -> iWord.getWordphrase());
        }

        //add the word itself//TODO think about whether we want that -- I think yes, to capture e.g. pronouns
        ret.add(word.toLowerCase());
        lemma2score.put(word.toLowerCase(), 0);
        return ret;
    }

    @Override
    public double scoreRel(String word, String lemma2check) {
        if (!wordPairScores.containsKey(word)) {
            getWNCandidates(word);//this will fill add an entry for word to the wordPairScores, and set its default return value.
        }
        
        return wordPairScores.get(word).getDouble(lemma2check);
    }

    @Override
    public String findVerbStem(String word) {
        return wordnet.findVerbStem(word);
    }

    @Override
    public String findRelatedVerbStem(String word) {
        return wordnet.findRelatedVerbStem(word);
    }

    @Override
    public String findNounStem(String word) {
        return wordnet.findNounStem(word);
    }
}
