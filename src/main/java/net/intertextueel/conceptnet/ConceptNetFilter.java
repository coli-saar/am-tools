package net.intertextueel.conceptnet;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A ConceptNetFilter instance can be created before creating a ConceptNet object and loading the database into memory.
 * This filter can be passed to the ConceptNet constructor and is used to reduce the memory footprint of your application.
 * You can restrict the data you wish to access to certain languages, relation types and/or data sources.
 * You can also enable or disable loading of meta-assertions (assertions about assertions) and disable storing data source information
 * (which takes up a lot of RAM, while your application may not need it).
 *
 * An empty or missing value for a specific filter field will result in all possible values being loaded.
 */
public class ConceptNetFilter {

    protected boolean load_meta;
    protected boolean load_datasources;
    protected String[] languages;
    protected String[] relationtypes;
    protected Pattern regex_datasources;

    /**
     * The empty constructor defines the default behaviour to load everything. 
     */
    public ConceptNetFilter() {
        load_meta = true;
        load_datasources = true;
        languages = new String[0];
        relationtypes = new String[0];
        regex_datasources = null;
    }

    /**
     * The full constructor creates a filter to only load assertions about concepts in certain languages, with
     * certain relation types and data sources; and tells whether or not to load meta assertions and store data source information in memory.
     *
     * @param load_meta         Accept and load meta relations: assertions about other assertions.
     * @param load_datasources  Store data source information in memory. If this flag is set to false, you may still effectively use the datasources_regex parameter. 
     * @param languages         Accept only only languages in the array (format: 2-character lowercase). Empty array means accept everything.
     * @param relationtypes     Accept only only relation types in the array (format: simple string, no slashes). Empty array means accept everything.
     * @param datasources_regex Regular expression used to filter by data sources. Must match in at least one field of the {@link DataSources DataSources} class
     */
    public ConceptNetFilter(boolean load_meta, boolean load_datasources, String[] languages, String[] relationtypes, String datasources_regex) {
        this.load_meta = load_meta;
        this.load_datasources = load_datasources;
        this.languages = languages;
        this.relationtypes = relationtypes;
        this.regex_datasources = Pattern.compile(datasources_regex);
    }

    /**
     * This constructor creates a filter to tell the library to only load assertions about concepts in on or more languages
     * and/or with one or more relation types.
     *
     * @param load_meta         Accept and load meta relations: assertions about other assertions.
     * @param load_datasources  Store data source information in memory. If this flag is set to false, you may still effectively use the datasources_regex parameter. 
     * @param languages         Accept only only languages in the array (format: 2-character lowercase). Empty array means accept everything.
     * @param relationtypes     Accept only only relation types in the array (format: simple string, no slashes). Empty array means accept everything.
     */
    public ConceptNetFilter(boolean load_meta, boolean load_datasources, String[] languages, String[] relationtypes) {
        this.load_meta = load_meta;
        this.load_datasources = load_datasources;
        this.languages = languages;
        this.relationtypes = relationtypes;
        this.regex_datasources = null;
    }

    /**
     * This constructor creates a filter to tell the library to only load assertions about concepts in on or more languages.
     *
     * @param load_meta        Accept and load meta relations: assertions about other assertions.
     * @param load_datasources Store data source information in memory. If this flag is set to false, you may still effectively use the datasources_regex parameter. 
     * @param languages        Accept only only languages in the array (format: 2-character lowercase). Empty array means accept everything.
     */
    public ConceptNetFilter(boolean load_meta, boolean load_datasources, String[] languages) {
        this.load_meta = load_meta;
        this.load_datasources = load_datasources;
        this.languages = languages;
        this.relationtypes = new String[0];
        this.regex_datasources = null;
    }

    /**
     * This constructor creates a filter to tell the library whether or not to load meta-assertions and/or store data source information in memory.
     *
     * @param load_meta        Accept and load meta relations: assertions about other assertions.
     * @param load_datasources Store data source information in memory. If this flag is set to false, you may still effectively use the datasources_regex parameter. 
     */
    public ConceptNetFilter(boolean load_meta, boolean load_datasources) {
        this.load_meta = load_meta;
        this.load_datasources = load_datasources;
        this.languages = new String[0];
        this.relationtypes = new String[0];
        this.regex_datasources = null;
    }

    /**
     * This constructor creates a filter to tell the library whether or not to load meta-assertions.
     *
     * @param load_meta        Accept and load meta relations: assertions about other assertions.
     */
    public ConceptNetFilter(boolean load_meta) {
        this.load_meta = load_meta;
        this.load_datasources = true;
        this.languages = new String[0];
        this.relationtypes = new String[0];
        this.regex_datasources = null;
    }

     
    /**
     * Set the languages the filter should allow.
     *
     * @param languages Accept only only languages in the array (format: 2-character lowercase). Empty array means accept everything.
     */
    public void setLanguages(String[] languages) {
        this.languages = languages;
    }

    /**
     * Get a string with all the languages the filter should allow.
     *
     * @return An array with languages (format: 2-character lowercase). Empty array means accept everything.
     */
    public String[] getLanguages() {
        return languages;

    }

    /**
     * Set the relation types the filter should allow.
     *
     * @param relationtypes Accept only only relation types in the array (format: simple string, no slashes). Empty array means accept everything.
     */
    public void setRelationTypes(String[] relationtypes) {
        this.relationtypes = relationtypes;
    }

    /**
     * Get the relation types the filter should allow.
     *
     * @return An array with filter types (format: simple string, no slashes). Empty array means accept everything.
     */
    public String[] getRelationTypes() {
        return relationtypes;
    }

    /**
     * Sets the flag to load meta relations. Meta relations contain assertions about assertions. Such relations are typically
     * used to translate entire statements from one language into another.
     *
     * @param load_meta Accept and load meta relations: assertions about other assertions.
     */
    public void setMeta(boolean load_meta) {
        this.load_meta = load_meta;
    }

    /**
     * Gets the flag to load meta relations. Meta relations contain assertions about assertions. Such relations are typically
     * used to translate entire statements from one language into another.
     *
     * @return Boolean value indicating whether or not to accept and load meta relations, which are assertions about other assertions.
     */
    public boolean getMeta() {
        return load_meta;
    }

    /**
     * Sets the flag to load data sources. Information about the data sources of an assertion will not be accessible without loading it first.
     * For many applications, this data is not needed however, and setting this flag to false will improve memory efficiency.
     *
     * NOTICE: Not loading the data sources permanently in memory, does not mean we cannot apply a data sources filters (@see ConceptNetFilter#setDataSourcesRegex())
     *         The regular expression in setDataSourcesRegex() determines which concepts and assertions to load based on their data sources.
     *         The flag in setLoadDataSources() on the other hand, determines whether or not to store the data sources in memory.
     *
     * @param load_datasources Set whether to load information about data sources or not.
     */
    public void setLoadDataSources(boolean load_datasources) {
        this.load_datasources = load_datasources;
    }

    /**
     * Gets the flag to load data sources. Data sources will not be accessible if this flag has been set tot false.
     *
     * @return Boolean value indicating whether or not data sources will be loaded.
     */
    public boolean getLoadDataSources() {
        return load_datasources;
    }

    /**
     * Sets the regular expression used to filter data sources.
     * Using a regular expression does slow down the initial loading of the CSV data significantly.
     *
     * @param regex Regular expression used to filter data sources, working on the raw text strings as found in the ConceptNet CSV file.
     */
    public void setDataSourcesRegex(String regex) {
        this.regex_datasources = Pattern.compile(regex);
    }

    /**
     * Verifies whether a regular expression to filter data sources has been set.
     *
     * @return true if a data sources filter has been set, false if it has not been set.
     */
    public boolean hasDataSourcesRegex() {
        return (this.regex_datasources != null);
    }

    /**
     * Gets the regular expression used to filter data sources.
     *
     * @return Regular expression used to filter data sources, working on the raw text strings as found in the ConceptNet CSV file.
     */
    public String getDataSourcesRegex() {
        return this.regex_datasources.pattern();
    }

    /**
     * Apply the regular expression used to filter data sources on this particular data source.
     *
     * @param ds Data sources object to apply the filteArray     
     * @return true if the filter is applicable, false if not.
     */
    public boolean applyDataSourcesRegex(DataSources ds) {
        /* If no data sources filter has been set, we consider everything to match. */
        if (!hasDataSourcesRegex()) { return true; }
        /*
         * Unfortunately, creating the matcher creates some synchronization-effects, breaking parallelized processing of the CSV file
         * with ConceptNet data. As a result, applying a regular expression to filter the data sources is an expensive operation.
         */
        String[] ds_individual_fields = new String[] { ds.getDataSetAsString(), ds.getLicenseAsString(), ds.getContributorAsString(),
                                                       ds.getActivityAsString(), ds.getProcessAsString() };
        for (String field : ds_individual_fields) {
            Matcher matcher = regex_datasources.matcher(field);
            if (matcher.find()) {
                /* data applies to filter */
                return true;
            }
        }
        return false;
    }

}
