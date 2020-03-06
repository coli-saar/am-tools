package de.saar.coli.amrtagging.mrp.ucca;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.ConlluEntry;
import de.saar.coli.amrtagging.ConlluSentence;
import de.saar.coli.amrtagging.TokenRange;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.NamedEntityRecognizer;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessedData;
import de.saar.coli.amrtagging.formalisms.amr.tools.preproc.PreprocessingException;
import de.up.ling.irtg.util.Util;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NamedEntityMerger {
    public static final String NE_SEPARATOR = "_";

    private String id;
    private PreprocessedData preprocData;
    private NamedEntityRecognizer neRecognizer;
    private List<String> revisedSentence = null;
    private int[] oldPosToNewPos = null;
    private int[] newPosToLastOldPos = null;
    private ListMultimap<Integer, Integer> newPosToAllOldPositions;

    public NamedEntityMerger(String id, PreprocessedData preprocData, NamedEntityRecognizer neRecognizer) {
        this.id = id;
        this.preprocData = preprocData;
        this.neRecognizer = neRecognizer;
    }

    /**
     * Merges tokens in the sentence that are part of the same named entity.
     * Returns a "revised string", in which tokens were merged together using
     * the separator symbol {@link #NE_SEPARATOR}.
     *
     * @param sentence
     * @return
     */
    public List<String> merge(List<String> sentence) {
        try {
            List<CoreLabel> companionTokens = preprocData.getTokens(id);
            List<CoreLabel> neTags = neRecognizer.tag(companionTokens);

            revisedSentence = new ArrayList<>();
            oldPosToNewPos = new int[sentence.size()];
            newPosToAllOldPositions = ArrayListMultimap.create();
            int[] tmpNewPosToLastOldPos = new int[sentence.size()];
            int newPos = 0;
            String previousNE = null;

            for (int oldPos = 0; oldPos < sentence.size(); oldPos++) {
                String neHere = neTags.get(oldPos).ner();
                if (previousNE != null && !NamedEntityRecognizer.NER_NULL.equals(neHere) && neHere.equals(previousNE)) {
                    // extend previous NE
                    int previousNewPos = newPos - 1;
                    revisedSentence.set(previousNewPos, revisedSentence.get(previousNewPos) + NE_SEPARATOR + sentence.get(oldPos));
                    oldPosToNewPos[oldPos] = previousNewPos;
                    tmpNewPosToLastOldPos[previousNewPos] = oldPos;
                    newPosToAllOldPositions.put(previousNewPos, oldPos);
                } else {
                    // start new token
                    revisedSentence.add(sentence.get(oldPos));
                    tmpNewPosToLastOldPos[newPos] = oldPos;
                    newPosToAllOldPositions.put(newPos, oldPos);
                    oldPosToNewPos[oldPos] = newPos++;
                    previousNE = neHere;
                }
            }

            newPosToLastOldPos = Arrays.copyOf(tmpNewPosToLastOldPos, newPos);

//            System.err.printf("\nRevised sentence: %s\n", revisedSentence);
//            System.err.printf("Old-to-new map: %s\n", Arrays.toString(oldPosToNewPos));
//            System.err.printf("New-to-last-old map: %s\n", Arrays.toString(newPosToLastOldPos));
//            System.err.printf("Old to all new: %s\n", newPosToAllOldPositions);
            return revisedSentence;
        } catch (PreprocessingException e) {
            e.printStackTrace();
            return sentence;
        }
    }

    /**
     * Merge the tokens of a CoNLL-U sentence and all the information they are annotated with.
     *
     * @param sentence
     * @return
     */
    public ConlluSentence merge(ConlluSentence sentence) {
        List<String> mergedWords = merge(sentence.words());
        List<TokenRange> mergedTokenRanges = combineTokenRanges(sentence.ranges());
        ConlluSentence ret = new ConlluSentence();

        assert mergedTokenRanges.size() == mergedWords.size();

        for (int newPos = 0; newPos < mergedWords.size(); newPos++) {
            ConlluEntry e = sentence.get(newPosToLastOldPos[newPos]);  // inherit all fields from last corresponding element of original sentence
            e.setForm(mergedWords.get(newPos));                        // ... except overwrite word-form with potentially merged multiword token
            e.setTokenRange(mergedTokenRanges.get(newPos));            // ... and combine the token ranges
            ret.add(e);
        }

        return ret;
    }

    /**
     * Returns "fixed" alignments in which all string positions were mapped to
     * their corresponding positions in the revised string.
     *
     * @param alignments
     * @return
     */
    public List<Alignment> fixAlignments(List<Alignment> alignments) {
        List<Alignment> ret = new ArrayList<>();

        for (Alignment al : alignments) {
            Alignment.Span span = al.span;
            assert span.end == span.start + 1;  // Mario promised this would be true

            int newPos = oldPosToNewPos[span.start];
            Alignment.Span newSpan = new Alignment.Span(newPos, newPos + 1);
            Alignment fixed = new Alignment(al.nodes, newSpan, al.lexNodes, al.color, al.getWeight());
            ret.add(fixed);
        }

        return ret;
    }

    public boolean hasData() {
        return revisedSentence != null;
    }

    /**
     * Returns a suitable selection of the given tags for the revised string.
     * The argument is a list of tags for the original string. The method returns
     * a list of tags; if multiple tokens of the original string were merged
     * into one token, then the tag of the last corresponding token in the
     * original string is returned.<p>
     * <p>
     * If the {@link #merge(List)} method has not yet been called on this object,
     * this method returns the original tag list unchanged.
     *
     * @param tagsInOriginalString
     * @return
     */
    public List<String> mapTags(List<String> tagsInOriginalString) {
        if (hasData()) {
            List<String> ret = new ArrayList<>();

            for (int newPos = 0; newPos < revisedSentence.size(); newPos++) {
                ret.add(tagsInOriginalString.get(newPosToLastOldPos[newPos]));
            }

            return ret;
        } else {
            return tagsInOriginalString;
        }
    }

    /**
     * Returns the token ranges of the original sentence, combined appropriately.
     * If multiple tokens were combined into one, the token range for the combined token
     * becomes the "concatenation" of the original token ranges, i.e. from is the min
     * of the original froms, and to is the max of the original tos.
     *
     * @param originalTokenRanges
     * @return
     */
    public List<TokenRange> combineTokenRanges(List<TokenRange> originalTokenRanges) {
        if (hasData()) {
            List<TokenRange> ret = new ArrayList<>();

            for (int newPos = 0; newPos < revisedSentence.size(); newPos++) {
                List<Integer> oldPositions = newPosToAllOldPositions.get(newPos);
                int minFrom = Integer.MAX_VALUE;
                int maxTo = Integer.MIN_VALUE;

                for (int oldPosition : oldPositions) {
                    TokenRange tr = originalTokenRanges.get(oldPosition);
                    minFrom = Math.min(minFrom, tr.getFrom());
                    maxTo = Math.max(maxTo, tr.getTo());
                }

                ret.add(new TokenRange(minFrom, maxTo));
            }

            return ret;
        } else {
            return originalTokenRanges;
        }
    }
}
