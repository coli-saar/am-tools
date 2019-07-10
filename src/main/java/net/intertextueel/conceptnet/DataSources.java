package net.intertextueel.conceptnet;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class provides preliminary support for working with ConceptNet data sources.
 * Currently it is just a wrapper around the strings representing the data sources of an assertion.
 * The separate fields are not parsed or decoded, but the text (in ConceptNet URI format) can be accessed and queried.
 *
 * A DataSources object is an immutable object; once created, it cannot be modified.
 */
public class DataSources {

    protected final String dataset;
    protected final String license;
    protected final String contributor;
    protected final String activity;
    protected final String process;

    /**
     * Construct the instance by specifying the minimal amount of information which is normally available.
     *
     * @param dataset     The data set definition. For example: /d/wiktionary/en
     * @param license     The license definition. For example: cc:by-sa/4.0
     * @param contributor The contributor. For example: /s/resource/dbpedia/2015/en
     */
    public DataSources(String dataset, String license, String contributor) {
        this.dataset = dataset;
        this.license = license;
        this.contributor = contributor;
        activity = null;
        process = null;
    }

    /**
     * Construct the instance by specifying five fields.
     *
     * @param dataset     The dataset definition. For example: /d/wiktionary/en
     * @param license     The license definition. For example: cc:by-sa/4.0
     * @param contributor The contributor. For example: /s/resource/dbpedia/2015/en
     * @param activity    The activity. For example: /s/activity/omcs/omcs1_possibly_free_text
     * @param process     The process. For example: /s/process/wikiparsec/1
     */
    public DataSources(String dataset, String license, String contributor, String activity, String process) {
        this.dataset = dataset;
        this.license = license;
        this.contributor = contributor;
        this.activity = activity;
        this.process = process;
    }

    /**
     * Get the dataset URI string.
     *
     * @return The dataset which is connected to the assertion. For example: /d/wiktionary/en
     */
    public String getDataSetAsString() {
        return this.dataset;
    }

    /**
     * Get the license string.
     *
     * @return The license under which this assertion is distributed. For example: cc:by-sa/4.0
     */
    public String getLicenseAsString() {
        return this.license;
    }

    /**
     * Get the contributor URI string.
     *
     * @return The contributor which is connected to the assertion. For example: /s/activity/omcs/omcs1_possibly_free_text
     */
    public String getContributorAsString() {
        return this.contributor;
    }

    /**
     * Get the activity URI string.
     *
     * @return The acivity which is connected to the assertion. May ben null if not available. For example: /s/resource/dbpedia/2015/en
     */
    public String getActivityAsString() {
        return this.activity;
    }

    /**
     * Get the process URI string.
     *
     * @return The acivity which is connected to the assertion. May ben null if not available. For example: /s/process/wikiparsec/1
     */
    public String getProcessAsString() {
        return this.process;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(this.dataset)
                                    .append(this.license)
                                    .append(this.contributor)
                                    .append(this.activity)
                                    .append(this.process)
                                    .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DataSources == false) {
            return false;
        }
        if (this == obj) {
            return true;
        }

        final DataSources datasources = (DataSources) obj;
        return new EqualsBuilder().append(this.dataset, datasources.dataset)
                                  .append(this.license, datasources.license)
                                  .append(this.contributor, datasources.contributor)
                                  .append(this.activity, datasources.activity)
                                  .append(this.process, datasources.process)
                                  .isEquals();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                    .append("dataset", dataset)
                    .append("license", license)
                    .append("contributor", contributor)
                    .append("activity", activity)
                    .append("process", process)
                    .toString();
    }

}
