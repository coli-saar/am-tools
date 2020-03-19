package de.saar.coli.amtools.analysis;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;

import java.util.List;

public class AmConllComparator {

    /**
     * computes unlabeled f score between two AmConllSentences. The lists must not have equal size (ignores sentences
     * in the longer list that are not in shorter list) but must have same order (i-th entry in each list must be exact
     * same sentence).
     * @param list1
     * @param list2
     * @return
     */
    public static double getUnlabeledF(List<AmConllSentence> list1, List<AmConllSentence> list2) {
        int totalNonIgnoreEdges1 = 0;
        int totalNonIgnoreEdges2 = 0;
        int matchingEdges = 0;
        for (int sentenceIndex = 0; sentenceIndex < Math.min(list1.size(), list2.size()); sentenceIndex++) {
            AmConllSentence sent1 = list1.get(sentenceIndex);
            AmConllSentence sent2 = list2.get(sentenceIndex);
            for (int wordIndex = 0; wordIndex < sent1.size(); wordIndex++) {
                boolean oneDoesNotIgnore = false;
                if (!sent1.get(wordIndex).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                    totalNonIgnoreEdges1++;
                    oneDoesNotIgnore = true;
                }
                if (!sent2.get(wordIndex).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                    totalNonIgnoreEdges2++;
                    oneDoesNotIgnore = true;
                }
                if (oneDoesNotIgnore && sent1.get(wordIndex).getHead() == sent2.get(wordIndex).getHead()) {
                    matchingEdges++;
                }
            }
        }

        double recall = (double)matchingEdges/(double)totalNonIgnoreEdges1;
        double precision = (double)matchingEdges/(double)totalNonIgnoreEdges2;
        if (recall + precision < 0.0000001) {
            return 0;
        } else {
            return 2*recall*precision/(recall+precision);
        }
    }

    /**
     * computes labeled f score between two AmConllSentences. The lists must not have equal size (ignores sentences
     * in the longer list that are not in shorter list) but must have same order (i-th entry in each list must be exact
     * same sentence).
     * @param list1
     * @param list2
     * @return
     */
    public static double getLabeledF(List<AmConllSentence> list1, List<AmConllSentence> list2) {
        int totalNonIgnoreEdges1 = 0;
        int totalNonIgnoreEdges2 = 0;
        int matchingEdges = 0;
        for (int sentenceIndex = 0; sentenceIndex < Math.min(list1.size(), list2.size()); sentenceIndex++) {
            AmConllSentence sent1 = list1.get(sentenceIndex);
            AmConllSentence sent2 = list2.get(sentenceIndex);
            for (int wordIndex = 0; wordIndex < sent1.size(); wordIndex++) {
                boolean oneDoesNotIgnore = false;
                if (!sent1.get(wordIndex).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                    totalNonIgnoreEdges1++;
                    oneDoesNotIgnore = true;
                }
                if (!sent2.get(wordIndex).getEdgeLabel().equals(AmConllEntry.IGNORE)) {
                    totalNonIgnoreEdges2++;
                    oneDoesNotIgnore = true;
                }
                if (oneDoesNotIgnore && sent1.get(wordIndex).getEdgeLabel().equals(sent2.get(wordIndex).getEdgeLabel())
                        && sent1.get(wordIndex).getHead() == sent2.get(wordIndex).getHead()) {
                    matchingEdges++;
                }
            }
        }

        double recall = (double)matchingEdges/(double)totalNonIgnoreEdges1;
        double precision = (double)matchingEdges/(double)totalNonIgnoreEdges2;
        if (recall + precision < 0.0000001) {
            return 0;
        } else {
            return 2*recall*precision/(recall+precision);
        }
    }
}
