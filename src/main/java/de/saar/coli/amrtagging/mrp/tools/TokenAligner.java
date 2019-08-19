package de.saar.coli.amrtagging.mrp.tools;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import de.saar.basic.Pair;
import de.saar.coli.amrtagging.TokenRange;
import de.up.ling.irtg.util.Util;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Aligns the token ranges of the MRP input with the token ranges of the
 * MRP companion data.<p>
 * <p>
 * Based on implementation of the Wagner–Fischer algorithm on
 * https://codereview.stackexchange.com/questions/126236/levenshtein-distance-with-edit-sequence-and-alignment-in-java
 */
public class TokenAligner {
    /**
     * Denotes the fact that one character in one input string was removed.
     */
    public static final String GAP = "-";

    public static interface EditCosts {
        public double substitutionCost(char originalCharacter, char substitutedCharacter);

        public double insertionCost(char character);

        public double deletionCost(char character);
    }

    private static class DefaultSubstitutionCosts implements EditCosts {
        @Override
        public double substitutionCost(char originalCharacter, char substitutedCharacter) {
            return 1;
        }

        @Override
        public double insertionCost(char character) {
            return 1;
        }

        @Override
        public double deletionCost(char character) {
            return 1;
        }
    }

    private EditCosts editCosts;

    public TokenAligner(EditCosts editCosts) {
        this.editCosts = editCosts;
    }

    public TokenAligner() {
        this(new DefaultSubstitutionCosts());
    }

    /**
     * An alignment between the two (tokenized) strings.
     *
     */
    public static final class TokenAlignment {
        private final double distance;
        private final int leftLength;
        private final int rightLength;
        private List<EditOperation> edits;

        private static List<EditOperation> buildEditSequence(String leftString, String rightString, Map<Point, Point> parentMap, double[][] d) {
            List<EditOperation> ret = new ArrayList<>();
            int leftLength = leftString.length();
            int rightLength = rightString.length();
            Point current = new Point(rightLength, leftLength);

            while (true) {
                Point predecessor = parentMap.get(current);

                if (predecessor == null) {
                    break;
                }

                if (current.x != predecessor.x && current.y != predecessor.y) {
                    final char schar = leftString.charAt(predecessor.y);
                    final char zchar = rightString.charAt(predecessor.x);

                    ret.add(schar != zchar ? EditOperation.SUBSTITUTE : EditOperation.COPY);
                } else if (current.x != predecessor.x) {
                    ret.add(EditOperation.INSERT);
                } else {
                    ret.add(EditOperation.DELETE);
                }

                current = predecessor;
            }

            // Remove the last characters that correspond to the very beginning
            // of the alignments and edit sequence (since the path reconstructoin
            // proceeds from the "end" to the "beginning" of the distance matrix.
            ret.remove(ret.size()-1);

            Collections.reverse(ret);

            return ret;
        }

        private static TokenAlignment build(String leftString, String rightString, Map<Point, Point> parentMap, double[][] d) {
            int leftLength = leftString.length();
            int rightLength = rightString.length();
            List<EditOperation> edits = buildEditSequence(leftString, rightString, parentMap, d);

            return new TokenAlignment(leftLength, rightLength, d[rightLength][leftLength], edits);
        }

        private TokenAlignment(int leftLength, int rightLength, double distance, List<EditOperation> edits) {
            this.distance = distance;
            this.leftLength = leftLength;
            this.rightLength = rightLength;
            this.edits = edits;
        }

        public double getDistance() {
            return distance;
        }

        public List<EditOperation> getEditSequence() {
            return edits;
        }

        public Pair<String,String> visualize(String leftString, String rightString) {
            final StringBuilder leftLineBuilder = new StringBuilder(leftLength + rightLength);
            final StringBuilder rightLineBuilder = new StringBuilder(leftLength + rightLength);
            int leftPos = 0;
            int rightPos = 0;

            for( EditOperation op : edits ) {
                switch(op) {
                    case COPY:
                    case SUBSTITUTE:
                        leftLineBuilder.append(leftString.charAt(leftPos++));
                        rightLineBuilder.append(rightString.charAt(rightPos++));
                        break;

                    case INSERT:
                        leftLineBuilder.append(GAP);
                        rightLineBuilder.append(rightString.charAt(rightPos++));
                        break;

                    case DELETE:
                        leftLineBuilder.append(leftString.charAt(leftPos++));
                        rightLineBuilder.append(GAP);
                        break;
                }
            }

            return new Pair(leftLineBuilder.toString(), rightLineBuilder.toString());
        }

        /**
         * Returns mappings from the characters of the left string
         * to those of the right string, and vice versa.
         * The first int array maps character positions in the left string
         * to corresponding character positions in the right string.
         * The second int array maps character positions in the right string
         * to corresponding character positions in the left string.<p>
         *
         * The length of both arrays is string length + 1. array[stringlength]
         * points to the character just beyond the end of the other string,
         * i.e. the value is the length of the other string.
         *
         * @return
         */
        public Pair<int[],int[]> characterMappings() {
            int[] leftToRight = new int[leftLength];
            int[] rightToLeft = new int[rightLength];
            int leftPos = 0;
            int rightPos = 0;

            for( EditOperation op : edits ) {
                switch (op) {
                    case SUBSTITUTE:
                    case COPY:
                        rightToLeft[rightPos] = leftPos;
                        leftToRight[leftPos++] = rightPos++;
                        break;

                    case DELETE:
                        leftToRight[leftPos++] = rightPos-1;  // align to previous right character; move only left ahead
                        break;

                    case INSERT:
                        rightToLeft[rightPos++] = leftPos-1;
                        break;
                }
            }

            leftToRight[leftPos] = rightPos;
            rightToLeft[rightPos] = leftPos;

            assert leftPos == leftLength-1 : String.format("ended with left pos %d in left string of length %d", leftPos, leftLength);
            assert rightPos == rightLength-1 : String.format("ended with right pos %d in right string of length %d", leftPos, leftLength);

            return new Pair(leftToRight, rightToLeft);
        }
    }

    private static enum EditOperation {
        INSERT("I"),
        SUBSTITUTE("S"),
        DELETE("D"),
        COPY("C");

        private final String s;

        private EditOperation(String s) {
            this.s = s;
        }

        @Override
        public String toString() {
            return s;
        }
    }

    /**
     * Computes a character-by-character alignment of the given strings.
     *
     * @param leftString
     * @param rightString
     * @return
     */
    public TokenAlignment compute(String leftString, String rightString) {
        // This is required to keep the parent map invariant. If we did not do
        // this, the very first edit operation would not end up in the output.
        // For more details, comment out the following two rows and see what
        // happens.
        String paddedLeftString = "\u0000" + leftString;
        String paddedRightString = "\u0000" + rightString;

        final int leftLength = paddedLeftString.length();
        final int rightLength = paddedRightString.length();
        final double[][] d = new double[rightLength + 1][leftLength + 1];
        final Map<Point, Point> parentMap = new HashMap<>();

        for (int i = 1; i <= rightLength; ++i) {
            d[i][0] = i;
        }

        for (int j = 1; j <= leftLength; ++j) {
            d[0][j] = j;
        }

        for (int leftPos = 1; leftPos <= leftLength; ++leftPos) {
            for (int rightPos = 1; rightPos <= rightLength; ++rightPos) {
                final double substitutionCost = (paddedLeftString.charAt(leftPos - 1) == paddedRightString.charAt(rightPos - 1)) ? 0 : editCosts.substitutionCost(paddedLeftString.charAt(leftPos - 1), paddedRightString.charAt(rightPos - 1));
                double deletionCost = editCosts.deletionCost(paddedLeftString.charAt(leftPos - 1));
                double insertionCost = editCosts.insertionCost(paddedRightString.charAt(rightPos - 1));

                double tentativeDistance = d[rightPos - 1][leftPos] + insertionCost;
                EditOperation editOperation = EditOperation.INSERT;

                if (tentativeDistance > d[rightPos][leftPos - 1] + deletionCost) {
                    tentativeDistance = d[rightPos][leftPos - 1] + deletionCost;
                    editOperation = EditOperation.DELETE;
                }

                if (tentativeDistance > d[rightPos - 1][leftPos - 1] + substitutionCost) {
                    tentativeDistance = d[rightPos - 1][leftPos - 1] + substitutionCost;
                    editOperation = EditOperation.SUBSTITUTE;
                }

                d[rightPos][leftPos] = tentativeDistance;

                switch (editOperation) {
                    case SUBSTITUTE:
                        parentMap.put(new Point(rightPos, leftPos), new Point(rightPos - 1, leftPos - 1));
                        break;

                    case INSERT:
                        parentMap.put(new Point(rightPos, leftPos), new Point(rightPos - 1, leftPos));
                        break;

                    case DELETE:
                        parentMap.put(new Point(rightPos, leftPos), new Point(rightPos, leftPos - 1));
                        break;
                }
            }
        }

        return TokenAlignment.build(paddedLeftString, paddedRightString, parentMap, d);
    }


    public static class TokenAlignmentEditCosts implements EditCosts {
        private static final List<String> SUBSTITUTION_PAIRS = Arrays.asList(".…", "\'’", "`’", "\'“", "`“", "\'”", "`”", "-–", "\"“", "\"”");
        private SetMultimap<Character,Character> substitutionPairs;

        private Set<Character> deletableChars;
        private Character[] aDeletableChars = new Character[]{'.', '\'', '`', '-'}; // Latex-style characters that have multiple occurrences corresponding to a fancy character

        private Set<Character> insertableChars;
        private Character[] aInsertableChars = new Character[]{' '};

        public TokenAlignmentEditCosts() {
            substitutionPairs = HashMultimap.create();
            for( String sp : SUBSTITUTION_PAIRS ) {
                substitutionPairs.put(sp.charAt(0), sp.charAt(1));
            }

            deletableChars = new HashSet<>(Arrays.asList(aDeletableChars));
            insertableChars = new HashSet<>(Arrays.asList(aInsertableChars));
        }

        @Override
        public double substitutionCost(char originalCharacter, char substitutedCharacter) {
            Set<Character> subst = substitutionPairs.get(originalCharacter);

            if( subst == null ) {
                return 1;
            } else if( subst.contains(substitutedCharacter)) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public double insertionCost(char character) {
            if (insertableChars.contains(character)) {
                return 0.1;
            } else {
                return 1;
            }
        }

        @Override
        public double deletionCost(char character) {
            if (deletableChars.contains(character)) {
                return 0.1;
            } else {
                return 1;
            }
        }
    }

    public static void main(String[] args) {
        TokenAligner al = new TokenAligner(new TokenAlignmentEditCosts());
//        String leftString = "Hams on Friendly ... RIP.";
//        String rightString = "Hams on Friendly … RIP .";

        String leftString = "Released the same year and containing re-recorded tracks from Omikron, his album 'Hours ...' featured a song with lyrics by the winner of his \"Cyber Song Contest\" Internet competition, Alex Grant."; // # 509008 input
        String rightString = "Released the same year and containing re-recorded tracks from Omikron , his album ’Hours … ’ featured a song with lyrics by the winner of his “ Cyber Song Contest ” Internet competition , Alex Grant .";    // # 509008 companion tokens


        System.out.printf("left: %d, right: %d\n", leftString.length(), rightString.length());

        TokenAlignment result = al.compute(leftString, rightString);
        System.out.println("Distance: " + result.getDistance());
        System.out.println("Edit sequence: " + result.getEditSequence());

        System.out.println();
        Pair<String,String> alignment = result.visualize(leftString, rightString);
        System.out.println(alignment.left);
        System.out.println(String.join("", Util.mapToList(result.getEditSequence(), EditOperation::toString)));
        System.out.println(alignment.right);

        System.out.println("\nChar mappings:");
        Pair<int[], int[]> mappings = result.characterMappings();
        System.out.printf("left to right %d: %s\n", mappings.left.length, Arrays.toString(mappings.left));
        System.out.println("right to left: " + Arrays.toString(mappings.right));
    }
}
