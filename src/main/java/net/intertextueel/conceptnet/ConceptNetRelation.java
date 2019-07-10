package net.intertextueel.conceptnet;

import org.apache.commons.lang3.ArrayUtils;

/**
 * A ConceptNetRelation object describes how something (like a concept or an assertion) has a relation
 * to some other thing (again, a concept or an assertion) according to ConceptNet.
 * It describes what type of relation to a target node exists. To end-users of the library,
 * the ConceptNetRelation is an important object because it is returned by the ConceptNet interface query methods.
 *
 * Unlike the primitive Assertion object (which is used to query ConceptNet about assertions), an
 * instance of ConceptNetRelation contains data about the weight of the described relation and the
 * data sources from which the relation was originally learned.
 *
 * Notice an instance of ConceptNetRelation has no source attribute, only a target. The end-user of
 * the library should however always know the source of the relation because he or she has used it to
 * lookup the relation. The path finding methods from the ConceptNet class return a List of
 * ConceptNetRelation objects as steps in the path from the input Concept to the final destination.
 */
public class ConceptNetRelation {

    /*
     * Future considerations (for developers):
     * 
     * The ConceptNetRelation class could in theory be made immutable, though there could be situations
     * where modifying weights dynamically makes sense. In general, we need to keep the option open of
     * dynamically creating and updating a ConceptNet graph.
     *
     * To make multi-threading more manageable, we could create a lock at the highest possible level, namely in
     * the ConceptNet class itself. This would mean: ConceptNet needs to destroy an entire node to modify
     * any of its links or attributes. If we opt for such a scenario, ConceptNetRelation could be made
     * immutable.
     */

    protected boolean meta;
    protected float weight;
    protected String relationtype;
    protected Object target;
    protected DataSources datasources;

    /**
     * This is the pre-defined list of all relation types which will be ignored when specifying non_negative search behavior in the path finding functions.
     * Additionally, if the weight of a relation is negative, this will count as negative as well. The final result could be something like this:
     * Assertion: "Man is not an antonym of woman" suggests a positive relation between the concept man and the concept woman.
     */
    final protected String[] negative_relations = {
            "Antonym",
            "DistinctFrom",
            "NotCapableOf",
            "NotCauses",
            "NotDesires",
            "NotHasA",
            "NotHasProperty",
            "NotIsA",
            "NotMadeOf",
            "NotUsedFor",
            "ObstructedBy",
    };

    /**
     * This functions verifies whether the relation described by this instance of ConceptNetRelation is negative, such as "Antonym" for example.
     *
     * @return true if the relation is negative, false if the relation is not negative
     */
    final public boolean isNegative() {
        return ArrayUtils.contains(negative_relations, relationtype);
    }

    /**
     * The full constructor of a relation between concepts takes a relation type, a Concept object, a floating point weight and a DataSources object.
     * It contains all the information of a ConceptNet CSV line *except* the source node
     * 
     * @param relationtype The relation type as a simple string. For example: "isA" (without the slashes found in a ConceptNet URI string)
     * @param concept      An instance of Concept. This is the target of the relation being described.
     * @param weight       The strength of the relation being described. Can be negative to indicate an opposite.
     * @param datasources  An instance of DataSources to point to the creative sources of the ConceptNet relation being described.
     */
    public ConceptNetRelation(String relationtype, Concept concept, float weight, DataSources datasources) {
        meta = false;
        this.relationtype = relationtype;
        this.target = concept;
        this.weight = weight;
        this.datasources = datasources;
    }

    /**
     * This constructor of a relation between concepts takes a relation type, a target Concept and a foating point weight. Data sources are ommited.
     *
     * @param relationtype The relation type as a simple string. For example: "isA" (without the slashes found in a ConceptNet URI string)
     * @param concept      An instance of Concept. This is the target of the relation being described.
     * @param weight       The strength of the relation being described. Can be negative to indicate an opposite.
     */
    public ConceptNetRelation(String relationtype, Concept concept, float weight) {
        this(relationtype, concept, weight, null);
    }

    /**
     * This constructor of a relation between concepts takes a relation type and a target Concept. The weight is set to a default of 1.0 and data sources are ommited.
     *
     * @param relationtype The relation type as a simple string. For example: "isA" (without the slashes found in a ConceptNet URI string)
     * @param concept      An instance of Concept. This is the target of the relation being described.
     */
    public ConceptNetRelation(String relationtype, Concept concept) {
        this(relationtype, concept, (float) 1.0, null);
    }

    /**
     * The full constructor of a relation between assertions takes a relation type, an Assertion object, a floating point weight and a DataSources object.
     * It contains all the information of a ConceptNet CSV-line *except* the source node
     *
     * @param relationtype The relation type as a simple string. For example: "translationOf" (without the slashes found in a ConceptNet URI string)
     * @param assertion    An instance of Assertion. This is the target of the relation being described.
     * @param weight       The strength of the relation being described. Can be negative to indicate an opposite.
     * @param datasources  An instance of DataSources to point to the creative sources of the ConceptNet relation being described.
     */
    public ConceptNetRelation(String relationtype, Assertion assertion, float weight, DataSources datasources) {
        meta = true;
        this.relationtype = relationtype;
        this.target = assertion;
        this.weight = weight;
        this.datasources = datasources;
    }

    /**
     * This constructor of a relation between assertions takes a relation type, a target Assertion and a foating point weight. Data sources are ommited.
     *
     * @param relationtype The relation type as a simple string. For example: "translationOf" (without the slashes found in a ConceptNet URI string)
     * @param assertion    An instance of Assertion. This is the target of the relation being described.
     * @param weight       The strength of the relation being described. Can be negative to indicate an opposite.
     */
    public ConceptNetRelation(String relationtype, Assertion assertion, float weight) {
        this(relationtype, assertion, weight, null);
    }

    /**
     * This constructor of a relation between assertions takes a relation type and a target Assertion. The weight is set to a default of 1.0 and data sources are ommited.
     *
     * @param relationtype The relation type as a simple string. For example: "translationOf" (without the slashes found in a ConceptNet URI string)
     * @param assertion    An instance of Assertion. This is the target of the relation being described.
     */
    public ConceptNetRelation(String relationtype, Assertion assertion) {
        this(relationtype, assertion, (float) 1.0, null);
    }


    /**
     * The copy constructor makes a copy of an existing ConceptNetRelation object.
     *
     * <b>Developers warning</b>: if you will be extending any of the classes in the ConceptNet library, you may need to extend this class
     * and override this function as well. This method expects the relation type and the target object to be immutable and therefore
     * does not attempt to make a deep copy of them.
     *
     * @param cr The existing ConceptNetRelation object to be copied.
     */
    public ConceptNetRelation(ConceptNetRelation cr) {
        this.meta = cr.meta;
        this.relationtype = cr.relationtype;
        this.target = cr.target;
        this.weight = cr.weight;
        this.datasources = cr.datasources;
    }

    /**
     * A meta relation is a ConceptNet relation between two assertions.
     * The isMeta() method answers whether or not the current relation is such a meta relation.
     *
     * @return A boolean indicating whether or not this relation is a meta relation.
     */
    public boolean isMeta() {
        return meta;
    }

    /**
     * Get the weight of the ConceptNet relation.
     *
     * @return The weight of the relation as a floating point. It could be negative to indicate belief in an opposite relation.
     */
    public float getWeight() {
        return weight;
    }

    /**
     * Set the weight of the ConceptNet relation.
     *
     * @param weight The weight of the relation as a floating point. It could be negative to indicate belief in an opposite relation.
     */
    public void setWeight(float weight) {
        this.weight = weight;
    }

    /**
     * Get the type of relation as a simple string.
     *
     * @return The type of relation as a simple string. For example: "isA"
     */
    public String getRelationType() {
        return relationtype;
    }

    /**
     * Set the type of relation as a simple string.
     *
     * @param relationtype The type of relation as a simple string. For example: "isA"
     */
    public void setRelationType(String relationtype) {
        this.relationtype = relationtype;
    }

    /**
     * Get the DataSources object associated with this relation.
     *
     * @return An instance of DataSources
     */
    public DataSources getDataSources() {
        return datasources;
    }

    /**
     * Set the DataSources object associated with this relation.
     *
     * @param datasources An instance of DataSources
     */
    public void setDataSources(DataSources datasources) {
        this.datasources = datasources;
    }

    /**
     * Get the target of this relation.
     *
     * The target can be either a Concept, or an Assertion. 
     * Use the isMeta() method to verify which object type shall be returned
     * or use Java's native instanceof operator on the result.
     *
     * The user of the library can expect a query on concepts to return
     * a Concept target and a query on Assertions to return an Assertion target object.
     *
     * @return An instance of Concept, or an instance of Assertion
     */
    public Object getTarget() {
        if (meta) {
            return (Assertion) target;
        } else {
            return (Concept) target;
        }
    }

    /**
     * Get the target concept of this relation, assuming it is a concept.
     * Otherwise null will be returned.
     *
     * @return An instance of Concept, or null if this relation does not have a Concept as a target
     */
    public Concept getTargetConcept() {
        if (meta) {
            return null;
        } else {
            return (Concept) target;
        }
    }

    /**
     * Get the target assertion of this relation, assuming it is a relation between two assertions (a meta relation).
     * Otherwise null will be returned.
     *
     * @return An instance of Assertion, or null if this relation does not have a Assertion as a target
     */
    public Assertion getTargetAssertion() {
        if (meta) {
            return (Assertion) target;
        } else {
            return null;
        }
    }



    /**
     * Set the target Concept of this relation.
     *
     * @param target An instance of Concept
     */
    public void setTarget(Concept target) {
        meta = false;
        this.target = target;
    }

    /**
     * Set the target Assertion of this relation.
     *
     * @param target An instance of Assertion
     */
    public void setTarget(Assertion target) {
        meta = true;
        this.target = target;
    }

}
