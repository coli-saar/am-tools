/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools.aligner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
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

    private ConceptNet conceptnet;
    private WordnetEnumerator wordnet;

    private final Map<String, Set<String>> word2Hypers = new HashMap<>(); // cache for previously calculated hypernyms

    public static void main(String[] args) throws Exception {
        IWordnet cenum = new ConceptnetEnumerator(new File("/Users/koller/Downloads/conceptnet-assertions-5.7.0.csv.gz"), "amr-dependency-july2019/data/wordnet/dict");
        System.err.println(cenum.getAllNounHypernyms("baker"));
    }

    public ConceptnetEnumerator(File conceptnetFilename, String wordnetDictPath) throws IOException, MalformedURLException, InterruptedException {
        File pickleFilename = new File(conceptnetFilename.getAbsolutePath() + ".pkl");
        conceptnet = null;
        wordnet = new WordnetEnumerator(wordnetDictPath);

        // check if conceptnet can be unpickled
        try {
            if (pickleFilename.exists()) {
                System.err.printf("Reading ConceptNet from pickle in %s ...\n", pickleFilename.toString());
//                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pickleFilename));

                try (FSTObjectInput in = new FSTObjectInput(new FileInputStream(pickleFilename))) {
                    conceptnet = (ConceptNet) in.readObject();
                }

                System.err.println("Done.");
            }
        } catch (Exception e) {
        }

        // otherwise, read from gzipped CSV file
        if (conceptnet == null) {
            System.err.printf("Reading ConceptNet from CSV in %s ...\n", conceptnetFilename.toString());
            ConceptNetFilter filter = new ConceptNetFilter(false, false, new String[]{"en"}, new String[]{"IsA"});
            conceptnet = new ConceptNet(conceptnetFilename.getAbsolutePath(), filter);

            System.err.printf("Done, pickling to %s ...\n", pickleFilename.toString());
            try (FSTObjectOutput out = new FSTObjectOutput(new FileOutputStream(pickleFilename))) {

//            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(pickleFilename));
                out.writeObject(conceptnet);
                out.flush();
            }
//            oos.close();

            System.err.println("Done.");
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
                    if (nounHypernyms.add(hypernym)) {
//                        System.err.printf("%s IsA %s\n", concept.getWordphrase(), hypernym.getWordphrase());
                        hypernymRecursion(hypernym, nounHypernyms);
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double scoreRel(String word, String lemma2check) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
