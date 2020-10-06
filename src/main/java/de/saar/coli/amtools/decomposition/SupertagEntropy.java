package de.saar.coli.amtools.decomposition;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupertagEntropy {

    public static void main(String[] args) throws IOException, ParseException {
        Map<String, String> corpusName2path = new HashMap<>();
        corpusName2path.put("original heuristics", "../../data/sdp/uniformify2020/original_decompositions/dm/train/train.amconll");
        corpusName2path.put("syntax based source names", "../../experimentData/unsupervised2020/03-17/DM/train/train.amconll");

        for (Map.Entry<String, String> entry : corpusName2path.entrySet()) {
            List<AmConllSentence> corpus = AmConllSentence.read(new FileReader(entry.getValue()));
            double entropy = computeSupertagEntropy(corpus);
            System.out.println(entry.getKey()+": "+entropy);
        }

    }

    public static double computeSupertagEntropy(List<AmConllSentence> corpus) {
        Counter<String> supertagCounter = new Counter<>();
        for (AmConllSentence sent : corpus) {
            for (AmConllEntry word : sent) {
                supertagCounter.add(word.getDelexSupertag());
            }
        }
        double entropy = 0;
        double normalizingConstant = (double)supertagCounter.sum();
        for (Object2IntMap.Entry<String> entry : supertagCounter.getAllSorted()) {
            double p = entry.getIntValue()/normalizingConstant;
            entropy += -p*Math.log(p);
        }
        return entropy;
    }

}
