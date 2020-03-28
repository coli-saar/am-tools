package de.saar.coli.amtools.analysis;

import de.saar.coli.amrtagging.AmConllEntry;
import de.saar.coli.amrtagging.AmConllSentence;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;

import java.util.List;

public class AmConllComparator {

    /**
     * computes unlabeled f score between two AmConllSentences. The lists must not have equal size (ignores sentences
     * in the longer list that are not in shorter list) but must have same order (i-th entry in each list must be exact
     * same sentence).
     * checkModApp is only relevant if checkFullLabel is false (checkModApp means only the MOD/APP part of the label
     * must match).
     * @param list1
     * @param list2
     * @return
     */
    public static double getF(List<AmConllSentence> list1, List<AmConllSentence> list2,
                                       boolean checkModApp, boolean checkFullLabel) {
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
                    String edgeLabel1 = sent1.get(wordIndex).getEdgeLabel();
                    String edgeLabel2 = sent2.get(wordIndex).getEdgeLabel();
                    if (checkFullLabel) {
                        if (edgeLabel1.equals(edgeLabel2)) {
                            matchingEdges++;
                        }
                    } else if (checkModApp) {
                        if ((edgeLabel1.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)
                                && edgeLabel2.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION))
                                || (edgeLabel1.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)
                                && edgeLabel2.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION))) {
                            matchingEdges++;
                        }
                    } else {
                        matchingEdges++;
                    }
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
    @Deprecated
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


//    public static double getExactMatchPercentage(List<AmConllSentence> list1, List<AmConllSentence> list2,
//                              boolean checkModApp, boolean checkFullLabel) {
//        for (int sentenceIndex = 0; sentenceIndex < Math.min(list1.size(), list2.size()); sentenceIndex++) {
//            boolean allMatch = true;
//            AmConllSentence sent1 = list1.get(sentenceIndex);
//            AmConllSentence sent2 = list2.get(sentenceIndex);
//            for (int wordIndex = 0; wordIndex < sent1.size(); wordIndex++) {
//                boolean sent1Ignores = sent1.get(wordIndex).getEdgeLabel().equals(AmConllEntry.IGNORE);
//                boolean sent2Ignores = sent1.get(wordIndex).getEdgeLabel().equals(AmConllEntry.IGNORE);
//                if (oneDoesNotIgnore && sent1.get(wordIndex).getHead() == sent2.get(wordIndex).getHead()) {
//                    String edgeLabel1 = sent1.get(wordIndex).getEdgeLabel();
//                    String edgeLabel2 = sent2.get(wordIndex).getEdgeLabel();
//                    if (checkFullLabel) {
//                        if (edgeLabel1.equals(edgeLabel2)) {
//                            matchingEdges++;
//                        }
//                    } else if (checkModApp) {
//                        if ((edgeLabel1.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)
//                                && edgeLabel2.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION))
//                                || (edgeLabel1.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION)
//                                && edgeLabel2.startsWith(ApplyModifyGraphAlgebra.OP_APPLICATION))) {
//                            matchingEdges++;
//                        }
//                    } else {
//                        matchingEdges++;
//                    }
//                }
//            }
//        }
//
//        double recall = (double)matchingEdges/(double)totalNonIgnoreEdges1;
//        double precision = (double)matchingEdges/(double)totalNonIgnoreEdges2;
//        if (recall + precision < 0.0000001) {
//            return 0;
//        } else {
//            return 2*recall*precision/(recall+precision);
//        }
//    }

}
