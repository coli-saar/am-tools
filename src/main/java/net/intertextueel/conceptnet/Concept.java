package net.intertextueel.conceptnet;

import java.util.Optional;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.math3.linear.RealVector;

/**
 * This class describes a concept in its primitive form.
 *
 * A user of the library can create an Concept instance and pass it to several of the ConceptNet classes methods to query
 * ConceptNet about inter-concept relations.
 *
 * A concept is an immutable object; once created, it cannot be modified.
 */
public class Concept {
    protected final String language;
    protected final String wordphrase;
    protected final Pos pos;
    //protected final String wordsense;
    protected final RealVector vector;

    /**
     * The part-of-speech of the wordphrase (if known)
     */
    public enum Pos {
        /**
         * Undefined/unknown part-of-speech
         */
        UNSPECIFIED,
        /**
         * The wordphrase is a noun
         */
        NOUN,
        /**
         * The wordphrase is an adjective
         */
        ADJECTIVE,
        /**
         * The wodphrase is a verb
         */
        VERB,
        /**
         * The wordphrase is a preposition
         */
        PREPOSITION
    }

    /**
     * Construct the concept by providing a string in the ConceptNet URI format.
     *
     * @param conceptnetstring The concept as a single string. For example: /c/nl/aards/a/of_earth
     */
    public Concept(String conceptnetstring) {
        String[] parts = conceptnetstring.split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Got " + parts.length + " elements in ConceptNet string, which is too little (" + conceptnetstring + ")");
        } else {
            language = parts[2];
            wordphrase = parts[3].replaceAll("_", " ");
            if (parts.length >= 5) {
                switch(parts[4]) {
                    case "n": { pos = Pos.NOUN; break; }
                    case "a": { pos = Pos.ADJECTIVE; break; }
                    case "v": { pos = Pos.VERB; break; }
                    case "r": { pos = Pos.PREPOSITION; break; }
                    default: { pos = Pos.UNSPECIFIED; break; }
                }
            } else {
                pos = Pos.UNSPECIFIED;
            }
//            if (parts.length >= 6) {
//                wordsense = parts[5].replaceAll("_", " ");
//            } else {
//                wordsense = null;
//            }
        }
        vector = null;
    }

    /**
     * Construct the concept by providing a string in the ConceptNet URI format and its associated Numberbatch vector.
     *
     * @param conceptnetstring The concept as a single string. For example: /c/nl/aards/a/of_earth
     * @param vector           The Numberbatch vector as an instance of RealVector from the Apache Commons math3 library.
     */
    public Concept(String conceptnetstring, RealVector vector) {
        String[] parts = conceptnetstring.split("/");
        if (parts.length < 4) {
            throw new IllegalArgumentException();
        } else {
            language = parts[2];
            wordphrase = parts[3].replaceAll("_", " ");
            if (parts.length >= 5) {
                switch(parts[4]) {
                    case "n": { pos = Pos.NOUN; break; }
                    case "a": { pos = Pos.ADJECTIVE; break; }
                    case "v": { pos = Pos.VERB; break; }
                    case "r": { pos = Pos.PREPOSITION; break; }
                    default: { pos = Pos.UNSPECIFIED; break; }
                }
            } else {
                pos = Pos.UNSPECIFIED;
            }
//            if (parts.length >= 6) {
//                wordsense = parts[5].replaceAll("_", " ");
//            } else {
//                wordsense = null;
//            }
        }
        this.vector = vector;
    }


    /**
     * Construct the concept by providing the wordphrase and the language it is written in.
     *
     * @param language   The language in two-character lowercase format. For example: "en"
     * @param wordphrase The wordphrase describing the concept. Use whitespaces, no underscores. For example: "world weariness"
     */
    public Concept(String language, String wordphrase) {
        this.language = language;
        this.wordphrase = wordphrase;
        pos = Pos.UNSPECIFIED;
//        wordsense = null;
        vector = null;
    }

    /**
     * Get the language of the concept.
     *
     * @return The language in two-character lowercase format. For example: "en"
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Return the wordphrase of the concept.
     *
     * @return The wordphrase describing the concept, with whitespaces, no underscores. For example: "world weariness"
     */
    public String getWordphrase() {
        return wordphrase;
    }

    /**
     * Return the part-of-speech tag of the concept, if known.
     *
     * @return The constant POS tag of the concept. For example: Pos.NOUN
     */
    public Pos getPOS() {
        return pos;
    }

//    /**
//     * Return the word sense of the concept, if defined.
//     *
//     * @return The word sense of the concept, with whitespaces, no underscores. For example: "an expression of surprise", or empty string if not defined
//     */
//    public String getWordsense() {
//        return (wordsense == null) ? "" : wordsense;
//    }

    /**
     * Return the optional Numberbatch vector associated with this concept, if loaded and defined.
     *
     * @return The Numberbatch vector as an instance of RealVector from the Apache Commons math3 library
     */
    public Optional<RealVector> getVector() {
        if (vector == null) {
            return Optional.empty();
        }
        return Optional.of(vector);
    }

    @Override
    public int hashCode() {
        /* NOTICE: we do *not* append the vector to the hash, as we assume it is dependent on the rest of the data */
        return new HashCodeBuilder().append(this.language)
                                    .append(this.wordphrase)
                                    .append(this.pos)
//                                    .append(this.wordsense)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Concept == false) {
            return false;
        }
        if (this == obj) {
            return true;
        }

        final Concept concept = (Concept) obj;
        /* NOTICE: we do *not* take into account the vector during the comparison, as we assume it is dependent on the rest of the data */
        return new EqualsBuilder().append(this.language, concept.language)
                                  .append(this.wordphrase, concept.wordphrase)
                                  .append(this.pos, concept.pos)
//                                  .append(this.wordsense, concept.wordsense)
                                  .isEquals();
    }

    @Override
    public String toString() {
        /* NOTICE: we do *not* represent the vector information in the toString() output */
        String nativewordphrase = wordphrase.replaceAll(" ", "_");
        if (pos != Pos.UNSPECIFIED) {
            String poschar = "";
            switch(pos) {
                case NOUN: { poschar = "n"; break; }
                case ADJECTIVE: { poschar = "a"; break; }
                case VERB: { poschar = "v"; break; }
                case PREPOSITION: { poschar = "r"; break; }
            }
//            if (wordsense == null) {
                return "/c/" + language + "/" + nativewordphrase + "/" + poschar;
//            } else {
//                String nativewordsense = wordsense.replaceAll(" ", "_");
//                return "/c/" + language + "/" + nativewordphrase + "/" + poschar + "/" + nativewordsense;
//            }
        } else {
            return "/c/" + language + "/" + nativewordphrase;
        }
    }

}
