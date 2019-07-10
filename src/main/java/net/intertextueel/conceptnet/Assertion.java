package net.intertextueel.conceptnet;

import java.util.Optional;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class describes an assertion in its primitive form.
 *
 * The primitive assertions are missing the full information of a ConceptNet edge, specifically 'weight' and 'data sources'.
 * This type of assertion carries the same information ConceptNet provides when creating 'assertions about assertions'
 * Typically, an assertion about an assertion may be something like: 'man is an antonym of woman' (in English) translates
 * into 'man is een antoniem van vrouw' (in Dutch). When such assertions are referenced inside ConceptNet, they do not
 * contain a weight, or data sources.
 *
 * A user of the library can create an Assertion instance and pass it to several of the ConceptNet classes methods to query
 * ConceptNet about inter-assertion relations.
 *
 * An assertion is an immutable object; once created, it cannot be modified.
 */
public class Assertion {
    /* We assume source and targets are concepts, because ConceptNet does not allow assertions about assertions about assertions */
    protected final Concept source;
    protected final Concept target;
    protected final String relationtype;

   /**
     * Construct the assertion by providing a string in the ConceptNet URI format.
     *
     * @param conceptnetstring The constructor as a single string. For example: /a/[/r/IsA/,/c/en/alphabet/,/c/en/letter/]
     */
    public Assertion(String conceptnetstring) {
        String[] segments = conceptnetstring.substring(4, conceptnetstring.length() - 1).split(",");
        String conceptnetrelationstring = segments[0];
        String[] parts = conceptnetrelationstring.split("/");
        if (parts.length < 3) {
            throw new IllegalArgumentException();
        } else {
            relationtype = parts[2];
        }
        source = new Concept(segments[1]);
        target = new Concept(segments[2]);
    }

    /**
     * Get the source concept.
     *
     * @return Returns the concept which is the source of the assertion.
     */
    public Concept getSourceConcept() {
        return source;
    }

    /**
     * Get the target concept.
     *
     * @return Returns the concept which is the target of the assertion.
     */
    public Concept getTargetConcept() {
        return target;
    }

    /**
     * Get the relation type.
     *
     * @return Returns the relation type as a simple string. For example: "isA" (without the slashes found in a ConceptNet URI string)
     */
    public String getRelationType() {
        return relationtype;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(source.getLanguage())
                                    .append(source.getWordphrase())
                                    .append(target.getLanguage())
                                    .append(target.getWordphrase())
                                    .append(this.relationtype)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Assertion == false) {
            return false;
        }
        if (this == obj) {
            return true;
        }

        final Assertion assertion = (Assertion) obj;
        return new EqualsBuilder().append(source.getLanguage(), assertion.getSourceConcept().getLanguage())
                                  .append(source.getWordphrase(), assertion.getSourceConcept().getWordphrase())
                                  .append(target.getLanguage(), assertion.getTargetConcept().getLanguage())
                                  .append(target.getWordphrase(), assertion.getTargetConcept().getWordphrase())
                                  .append(this.relationtype, assertion.getRelationType())
                                  .isEquals();
    }

    @Override
    public String toString() {
        /*
         * NOTICE: the Concept.toString() method never produces a string with a terminating slash, while
         * the Assertion.toString() method does use terminating slashes. This is to resemble the CSV format.
         */
        return "/a/[/r/" + relationtype + "/," + source.toString() + "/," + target.toString() + "/]";
    }

}
