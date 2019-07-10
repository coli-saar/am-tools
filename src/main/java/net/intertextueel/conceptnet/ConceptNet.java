package net.intertextueel.conceptnet;

import java.util.Optional;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;

/**
 * The ConceptNet class provides the main interface to query your local ConceptNet database loaded from a CSV file.
 * A filter object of class ConceptNetFilter can optionally be provided to limit the amount of data loaded into memory.
 * Be prepared for the initial loading of the data to take a <b>long</b> time and a <b>lot</b> of RAM.
 * <p>
 *
 * Example code to initialize a ConceptNet interface, with a filter to use only English concepts and the IsA relation, and do a simple word lookup:
 * <pre>
 * <code>
 *  ConceptNet conceptnet = null;
 *  try {
 *      String[] languages = { "en" };
 *      String[] relationtypes = { "IsA" };
 *      boolean load_meta = false;
 *      boolean load_datasources = false;
 *      ConceptNetFilter filter = new ConceptNetFilter(load_meta, load_datasources, languages, relationtypes);
 *      conceptnet = new ConceptNet("/path/to/local/conceptnet/conceptnet-assertions-5.5.0.csv", filter);
 *  } catch (IOException e) {
 *      System.err.println(e.getMessage());
 *      System.exit(1);
 *  }
 *  Concept dog = new Concept("en", "dog");
 * {@code List<ConceptNetRelation>} cnr = conceptnet.getRelationsFrom(dog);
 *  System.out.println("Displaying relations for word 'dog'");
 *  for (ConceptNetRelation relation : cnr) {
 *      Concept target = relation.getTargetConcept();
 *      System.out.println(relation.getRelationType() + " " + target.getWordphrase());
 *  }
 * </code>
 * </pre>
 */
public class ConceptNet {

    protected final boolean verbose;
    protected final String config_conceptnet_csv;
    protected final ConceptNetFilter filter;

    protected HashMap<String, RealVector> load_vectors;                            /* this hash map is used as a temporary storage of Numberbatch data during the initial loading process */
    protected HashMap<String, DataSources> load_datasources;                       /* this hash map is used as a temporary storage of data sources data, during the initial loading process */

    /* storing relationships in both directions is neccessary because the ConceptNet database contains directed edges */
    protected HashMap<Concept, ConceptNetRelations> relations_from_concept;        /* all relations originating from a certain keyword (keyword is key in hash map) */
    protected HashMap<Concept, ConceptNetRelations> relations_to_concept;          /* all relations ending in a certain keyword (keyword is target in ConceptNetRelation object */
    /* store relationships between assertions in two separate hashmaps */
    protected HashMap<Assertion, ConceptNetRelations> relations_from_assertion;    /* all relations originating from a certain assertion (assertion is key in hash map) */
    protected HashMap<Assertion, ConceptNetRelations> relations_to_assertion;      /* all relations ending in a certain assertion (assertion is target in ConceptNetRelation object */

    /**
     * The basic constructor of a ConceptNet instance requires the location of the CSV file containing ConceptNet data
     * which will be loaded into memory entirely. Consider using filters to reduce the memory footprint of your application.
     *
     * @param config_conceptnet_csv Path to the CSV file containing ConceptNet data.
     * @throws IOException if the ConceptNet file could not be read.
     */
    public ConceptNet(String config_conceptnet_csv) throws IOException {
        this(config_conceptnet_csv, null, new ConceptNetFilter(), false);
    }

    /**
     * This constructor expects the location of the CSV file containing ConceptNet data and an instance of ConceptNetFilter.
     * Using the ConceptNetFilter object given as the second argument, you can filter what data you would like to retain in memory and work with.
     * By only loading concepts in one or more languages, and/or by restricting the network to several types of relations,
     * you can reduce the memory footprint of your application.
     *
     * @param config_conceptnet_csv  Path to the CSV file containing ConceptNet data.
     * @param filter                 An instance of ConceptNetFilter details which specific data you would like to retain in memory.
     * @throws IOException if the ConceptNet file could not be read.
     */
    public ConceptNet(String config_conceptnet_csv, ConceptNetFilter filter) throws IOException {
        this(config_conceptnet_csv, null, filter, false);
    }


    /**
     * This constructor expects the location of the CSV file containing ConceptNet data and the location of the Numberbatch text file.
     * The numberbatch data will be loaded into memory and automatically enables the functionality to work with word vectors.
     * Both datasets will be loaded into memory without any filtering applied on them.
     *
     * @param config_conceptnet_csv  Path to the CSV file containing ConceptNet data.
     * @param config_numberbatch_txt Path to the text file containing ConceptNet Numberbatch data. Set this to either an empty string "" or to null to ignore.
     * @throws IOException if the ConceptNet file could not be read.
     */
    public ConceptNet(String config_conceptnet_csv, String config_numberbatch_txt) throws IOException {
        this(config_conceptnet_csv, config_numberbatch_txt, new ConceptNetFilter(), false);
    }

    /**
     * This constructor expects the location of the ConceptNet CSV file, the NumberBatch text file and an instance of ConceptNetFilter.
     * The numberbatch data will be loaded into memory and automatically enables the functionality to work with word vectors.
     * The third argument points to an instance of ConceptNetFilter. Using an instance of this class, you can
     * filter what data you would like to retain in memory and work with. By only loading concepts in one or more languages,
     * and/or by restricting the network to several types of relations, you can reduce the memory footprint of
     * your application.
     *
     * @param config_conceptnet_csv  Path to the CSV file containing ConceptNet data.
     * @param config_numberbatch_txt Path to the text file containing ConceptNet Numberbatch data. Set this to either an empty string "" or to null to ignore.
     * @param filter                 An instance of ConceptNetFilter details which specific data you would like to retain in memory.
     * @throws IOException if the ConceptNet file could not be read.
     */
    public ConceptNet(String config_conceptnet_csv, String config_numberbatch_txt, ConceptNetFilter filter) throws IOException {
        this(config_conceptnet_csv, config_numberbatch_txt, filter, false);
    }

    /**
     * The full constructor expects the location of the CSV file on disk containing ConceptNet data.
     * The numberbatch data will be loaded into memory and automatically enables the functionality to work with word vectors.
     * The third argument points to an instance of ConceptNetFilter. Using an instance of this class, you can
     * filter what data you would like to retain in memory and work with. By only loading concepts in one or more languages,
     * and/or by restricting the network to several types of relations, you can reduce the memory footprint of
     * your application.
     * The final arguments indicates whether or not you would like to see verbose (debugging) output from the library
     * appear on STDERR.
     *  
     * @param config_conceptnet_csv  Path to the CSV file containing ConceptNet data.
     * @param config_numberbatch_txt Path to the text file containing ConceptNet Numberbatch data. Set this to either an empty string "" or to null to ignore.
     * @param filter                 An instance of ConceptNetFilter details which specific data you would like to retain in memory.
     * @param verbose                A boolean to indicate whether you would like to see verbose (debugging) output on STDERR.
     * @throws IOException if the ConceptNet file could not be read.
     */
    public ConceptNet(String config_conceptnet_csv, String config_numberbatch_txt, ConceptNetFilter filter, boolean verbose) throws IOException {
        this.config_conceptnet_csv = config_conceptnet_csv;
        this.verbose = verbose;
        this.filter = filter;
        relations_from_concept = new HashMap<Concept, ConceptNetRelations>();
        relations_to_concept = new HashMap<Concept, ConceptNetRelations>();
        relations_from_assertion = new HashMap<Assertion, ConceptNetRelations>();
        relations_to_assertion = new HashMap<Assertion, ConceptNetRelations>();
        load_vectors = new HashMap<String, RealVector>();
        if (config_numberbatch_txt != null && !config_numberbatch_txt.equals("")) {
            /* cache Numberbatch information */
            try {
                Files.lines(new File(config_numberbatch_txt).toPath())
                     .skip(1)
                     .forEach(s -> processNumberbatchLine(s));
            } catch (IOException e) {
                if (verbose) {
                    System.err.println("Error. General I/O exception: " + e.getMessage());
                }
                throw new IOException("Numberbatch txt file could not be opened.");
            } catch (NumberFormatException e) {
                if (verbose) {
                    System.err.println("Error. " + e.getMessage());
                }
                throw new IOException("Numberbatch txt file could not be opened.");
            }
        }
        load_datasources = new HashMap<String, DataSources>();
        /* parse ConceptNet CSV file */
        try {
            Files.lines(new File(config_conceptnet_csv).toPath())
                 .forEach(s -> processCSVLine(s));
        } catch (IOException e) {
            if (verbose) {
                System.err.println("Error. General I/O exception: " + e.getMessage());
            }
            throw new IOException("ConceptNet CSV file could not be opened.");
        }
        load_vectors = null;
        load_datasources = null;
        System.gc();                /* free the non-used memory as soon as possible */
    }

    /* Until Java has a native JSON API, we rely on this dangerously high-speed, low-level method of extracting the data from JSON text. */
    private final String extractStringFromJSON(String json, String field) {
        String seek = "\"" + field + "\": \"";
        int index_value_start = json.indexOf(seek) != -1 ? json.indexOf(seek) + seek.length() : -1;
        if (index_value_start == -1) {
            if (verbose) {
                System.err.println("Warning. Could not retrieve attribute '" + field + "' in JSON string: \"" + json + "\" on load.");
            }
            return null;
        }
        int index_value_end = json.indexOf("\"", index_value_start);
        if (index_value_end == -1) {
            if (verbose) {
               System.err.println("Warning. Could not retrieve attribute '" + field + "' in JSON string: \"" + json + "\" on load.");
            }
            return null;
        }
        return json.substring(index_value_start, index_value_end);
    }

    /* This method extract the weight from the JSON text of a ConceptNet CSV file, by relying on our knowledge on how it is produced. */
    private final float extractWeightFromJSON(String json) {
        String seek = ", \"weight\": ";
        int index_value_start = json.indexOf(seek) != -1 ? json.indexOf(seek) + seek.length() : -1;
        if (index_value_start == -1) {
            if (verbose) {
                System.err.println("Warning. Could not parse weight in JSON string: \"" + json + "\" on load. Assuming 1.0");
            }
            return (float) 1.0;
        }
        int index_value_end = json.indexOf("}", index_value_start);
        if (index_value_end == -1) {
            if (verbose) {
                System.err.println("Warning. Could not parse weight in JSON string: \"" + json + "\" on load. Assuming 1.0");
            }
            return (float) 1.0;
        }
        return Float.parseFloat(json.substring(index_value_start, index_value_end));
    }

    /* This function controls the loading and caching of Numberbatch data */
    protected void processNumberbatchLine(String line) {
        /* split space-delimited fields */ 
        String[] columns = line.split(" ");
        if (columns.length != 301) {
            throw new NumberFormatException("Unexpected column length inside Numberbatch file.");
        }
        String conceptstring = columns[0];
        /* We have the ability to apply a language filter here. This will slow down the loading process, but may prevent our cache structure from growing too big. */
        if (filter.getLanguages().length > 0) {
            Concept concept = new Concept(conceptstring);
            if (!Arrays.asList(filter.getLanguages()).contains(concept.getLanguage())) {
                return;
            }
            concept = null;                 /* we don't need the concept object anymore */
        }
        double[] coords = Arrays.stream(columns).skip(1).mapToDouble(Double::parseDouble).toArray();
        RealVector vector = new ArrayRealVector(coords);
        load_vectors.put(conceptstring, vector);
    }

    /* This function controls the data loading and parsing process of ConceptNet data */
    protected void processCSVLine(String line) {
        /* split tab-delimited CSV fields */
        String[] columns = line.split("\t");

        /*
            ConceptNet 5.5 format

            0: /a/[/r/Antonym/,/c/af/laag/a/,/c/af/hoog/]
            1: /r/Antonym
            2: /c/af/laag/a
            3: /c/af/hoog
            4: {"dataset": "/d/wiktionary/en", "license": "cc:by-sa/4.0", "sources": [{"contributor": "/s/resource/wiktionary/en", "process": "/s/process/wikiparsec/1"}], "weight": 1.0}
        */

        String relationtype = columns[1];
        String[] parts = relationtype.split("/");
        if (parts.length < 3) {
            if (verbose) {
                System.err.println("Warning. Could not parse relation type string: \"" + relationtype + "\" on load. Ignoring assertion.");
            }
            return;
        } else {
            relationtype = parts[2];
        }
        if (filter.getRelationTypes().length > 0 && !Arrays.asList(filter.getRelationTypes()).contains(relationtype)) {
            return;
        }
        float weight;
        DataSources datasources;
        weight = extractWeightFromJSON(columns[4]);
        /* only extract the data sources if we need them */
        if (filter.hasDataSourcesRegex() || filter.getLoadDataSources()) {
            datasources = new DataSources(extractStringFromJSON(columns[4], "dataset"),
                                          extractStringFromJSON(columns[4], "license"),
                                          extractStringFromJSON(columns[4], "contributor"),
                                          extractStringFromJSON(columns[4], "activity"),
                                          extractStringFromJSON(columns[4], "process"));
        } else {
            datasources = null;
        }
        if (filter.hasDataSourcesRegex()) {
            if (!filter.applyDataSourcesRegex(datasources)) {
                /* filter is not applicable, skipping this line */
                return;
            }
            if (!filter.getLoadDataSources()) {
                /* we only needed the data sources to filter */
                datasources = null;
            }
        }

        boolean meta;
        Object source;
        Object target;

        if (columns[1].equals("/r/ExternalURL")) {
            /* this line does no describe an internal ConceptNet relation, but links a word to an external database */
            return;
        }

        if (!columns[2].substring(0,3).equals("/c/") || (!columns[3].substring(0,3).equals("/c/"))) {
            if (columns[2].substring(0,3).equals("/a/") && columns[2].substring(0,3).equals("/a/")) {
                /* source and target are assertions */
                /* /a/[/r/IsA/,/c/en/alphabet/,/c/en/letter/] */
                if (filter.getMeta()) {
                    meta = true;
                    /*
                     * TODO: In principle, there is a further opportunity to optimise here, as the source and destination concepts *inside* the
                     * assertion objects we construct, may or may not be known already. Currently such concepts will be duplicated 
                     * in memory if they exist *inside* the meta-assertion. The Assertion class does describe itself as containing 'simplified'
                     * concepts as attributes (i.e. the source/target concepts therein don't contain Numberbatch information or data sources for example).
                     */
                    try {
                        source = new Assertion(columns[2]);
                    } catch (IllegalArgumentException e) {
                        if (verbose) {
                            System.err.println("Warning. Could not parse assertion as assertion source string: \"" + columns[2] + "\" on load. Ignoring assertion.");
                        }
                        return;
                    }
                    try {
                        target = new Assertion(columns[3]);
                    } catch (IllegalArgumentException e) {
                        if (verbose) {
                            System.err.println("Warning. Could not parse assertion as assertion target string: \"" + columns[3] + "\" on load. Ignoring assertion.");
                        }
                        return;
                    }
                    if (filter.getLanguages().length > 0 &&
                        (!Arrays.asList(filter.getLanguages()).contains(((Assertion) source).getSourceConcept().getLanguage()) ||
                         !Arrays.asList(filter.getLanguages()).contains(((Assertion) source).getTargetConcept().getLanguage()) ||
                         !Arrays.asList(filter.getLanguages()).contains(((Assertion) target).getSourceConcept().getLanguage()) ||
                         !Arrays.asList(filter.getLanguages()).contains(((Assertion) target).getTargetConcept().getLanguage()))) {
                        /* meta assertion filtered by language */
                        return;
                    }
                } else {
                    /* meta relations are ignored */
                    return;
                }
            } else {
                if (verbose) {
                    System.err.println("Warning! Unrecognized ConceptNet definition found. Column 1: \"" + columns[2] + "\", Column 2: \"" + columns[3] + "\". Ignoring.");
                }
                return;
            }
        } else {
            /* source and target are concepts */
            meta = false;
            try {
                source = new Concept(columns[2]);
            } catch (IllegalArgumentException e) {
                if (verbose) {
                    System.err.println("Warning. Could not parse source concept string: \"" + columns[2] + "\" on load. Ignoring assertion.");
                }
                return;
            }
            try {
                target = new Concept(columns[3]);
            } catch (IllegalArgumentException e) {
                if (verbose) {
                    System.err.println("Warning. Could not parse target concept string: \"" + columns[2] + "\" on load. Ignoring assertion.");
                }
                return;
            }
            String source_language = ((Concept) source).getLanguage();
            String target_language = ((Concept) target).getLanguage();
            if (filter.getLanguages().length > 0 &&
                (!Arrays.asList(filter.getLanguages()).contains(source_language) ||
                 !Arrays.asList(filter.getLanguages()).contains(target_language))) {
                return;
            }
        }

        /*
         * Now that we know the nature of our objects and have verified they should be stored in the database,
         * we first lookup whether these primitive objects are already referenced in it. If this is the case,
         * we should lookup and use those existing references instead of making a duplicate and consume more memory.
         */

        if (meta) {
            /* Look for an existing assertion, as a key in one of our two relations hash maps, and use that assertion if found */
            ConceptNetRelations test1 = relations_from_assertion.get(source);
            ConceptNetRelations test2 = relations_to_assertion.get(source);
            if (test1 != null || test2 != null) {
                source = (test1 != null) ? test1.key : test2.key;   /* source now references already existing object */
            }
            test1 = relations_from_assertion.get(target);
            test2 = relations_to_assertion.get(target);
            if (test1 != null || test2 != null) {
                target = (test1 != null) ? test1.key : test2.key;   /* target now references already existing object */
            }
        } else {
            /* Look for an existing concept, as a key in one of our two relations hash maps, and use that concept if found */
            ConceptNetRelations test1 = relations_from_concept.get(source);
            ConceptNetRelations test2 = relations_to_concept.get(source);
            if (test1 != null || test2 != null) {
                source = (test1 != null) ? test1.key : test2.key;   /* source now references already existing object */
            } else {
                /* This is a new source concept. If we have cached Numberbatch data for it, recreate the object with this data attached to it */
                String simplified = "/c/" + ((Concept) source).getLanguage() + "/" + ((Concept) source).getWordphrase().replace(' ', '_');
                RealVector vector = load_vectors.get(simplified);
                if (vector != null) {
                    source = new Concept(columns[2], vector);
                }
            }
            test1 = relations_from_concept.get(target);
            test2 = relations_to_concept.get(target);
            if (test1 != null || test2 != null) {
                target = (test1 != null) ? test1.key : test2.key;   /* target now references already existing object */
            } else {
                /* This is a new target concept. If we have cached Numberbatch data for it, recreate the object with this data attached to it */
                String simplified = "/c/" + ((Concept) target).getLanguage() + "/" + ((Concept) target).getWordphrase().replace(' ', '_');
                RealVector vector = load_vectors.get(simplified);
                if (vector != null) {
                    target = new Concept(columns[3], vector);
                }
            }
        }

        if (filter.getLoadDataSources()) {
            DataSources test = load_datasources.get(datasources.toString());
            if (test != null) {
                datasources = test;         /* datasources now references already existing object */
            } else {
                load_datasources.put(datasources.toString(), datasources);
            }
        }

        /* store data in relations hash maps */

        if (meta) {

            ConceptNetRelation relation_from_source = new ConceptNetRelation(relationtype, (Assertion) target, weight, datasources);
            ConceptNetRelation relation_on_target = new ConceptNetRelation(relationtype, (Assertion) source, weight, datasources);

            ConceptNetRelations cnr = relations_from_assertion.get(source);
            if (cnr == null) {
                cnr = new ConceptNetRelations((Assertion) source);
                relations_from_assertion.put((Assertion) source, cnr);        /* this creates the reference to an empty ConceptNetRelations object */
            }
            cnr.addRelation(relation_from_source);

            cnr = relations_to_assertion.get(target);
            if (cnr == null) {
                cnr = new ConceptNetRelations((Assertion) target);
                relations_to_assertion.put((Assertion) target, cnr);          /* this creates the reference to an empty ConceptNetRelations object */
            }
            cnr.addRelation(relation_on_target);

        } else {

            ConceptNetRelation relation_from_source = new ConceptNetRelation(relationtype, (Concept) target, weight, datasources);
            ConceptNetRelation relation_on_target = new ConceptNetRelation(relationtype, (Concept) source, weight, datasources);

            ConceptNetRelations cnr = relations_from_concept.get(source);
            if (cnr == null) {
                cnr = new ConceptNetRelations((Concept) source);
                relations_from_concept.put((Concept) source, cnr);        /* this creates the reference to an empty ConceptNetRelations object */
            }
            cnr.addRelation(relation_from_source);

            cnr = relations_to_concept.get(target);
            if (cnr == null) {
                cnr = new ConceptNetRelations((Concept) target);
                relations_to_concept.put((Concept) target, cnr);          /* this creates the reference to an empty ConceptNetRelations object */
            }
            cnr.addRelation(relation_on_target);

        }

    }

    /**
     * Get a list of all relations originating from either a concept, or an assertion, formatted as a ConceptNet URI string.
     *
     * @param conceptnetstring A string describing the concept or assertion in ConceptNet URI format (Example: /c/en/night/)
     * @return                 A list of ConceptNetRelation objects
     */
    public List<ConceptNetRelation> getRelationsFrom(String conceptnetstring) {
        ConceptNetRelations cnr;
        if (conceptnetstring.substring(0,3).equals("/a/")) {
            Assertion assertion = new Assertion(conceptnetstring);
            cnr = relations_from_assertion.get(assertion);
        } else {
            Concept concept = new Concept(conceptnetstring);
            cnr = relations_from_concept.get(concept);
        }
        if (cnr == null) {
            /* return an empty relation list */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }
        return cnr.getUnmodifiableRelations();
    }

    /**
     * Get a list of all relations originating from a concept - an instance of the Concept class. 
     *
     * @param concept An instance of Concept
     * @return        A list of ConceptNetRelation objects
     */
    public List<ConceptNetRelation> getRelationsFrom(Concept concept) {
        ConceptNetRelations cnr = relations_from_concept.get(concept);
        if (cnr == null) {
            /* return an empty relation list */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }
        return cnr.getUnmodifiableRelations();
    }

    /**
     * Get a list of all relations originating from an assertion - an instance of the Assertion class.
     *
     * @param assertion An instance of Assertion
     * @return          A list of ConceptNetRelation objects
     */
    public List<ConceptNetRelation> getRelationsFrom(Assertion assertion) {
        ConceptNetRelations cnr = relations_from_assertion.get(assertion);
        if (cnr == null) {
            /* return an empty relation list */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }
        return cnr.getUnmodifiableRelations();
    }

    /**
     * Get a list of all relations ending with either a concept, or an assertion, formatted as a ConceptNet URI string.
     *
     * @param conceptnetstring A string describing the concept in ConceptNet URI format (Example: /a/[/r/AtLocation/,/c/en/border_theater/,/c/en/mission_texas/])
     * @return                 A list of ConceptNetRelation objects
     */
    public List<ConceptNetRelation> getRelationsTo(String conceptnetstring) {
        ConceptNetRelations cnr;
        if (conceptnetstring.substring(0,3).equals("/a/")) {
            Assertion assertion = new Assertion(conceptnetstring);
            cnr = relations_to_assertion.get(assertion);
        } else {
            Concept concept = new Concept(conceptnetstring);
            cnr = relations_to_concept.get(concept);
        }
        if (cnr == null) {
            /* return an empty relation list */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }
        return cnr.getUnmodifiableRelations();

    }

    /**
     * Get a list of all relations ending with a concept - an instance of the Concept class.
     *
     * @param concept An instance of Concept
     * @return        A list of ConceptNetRelation objects
     */
    public final List<ConceptNetRelation> getRelationsTo(Concept concept) {
        ConceptNetRelations cnr = relations_to_concept.get(concept);
        if (cnr == null) {
            /* return an empty relation list */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }
        return cnr.getUnmodifiableRelations();
    }

    /**
     * Get a list of all relations ending with an assertion - an instance of the Assertion class. 
     *
     * @param assertion An instance of Assertion
     * @return          A list of ConceptNetRelation objects
     */
    public List<ConceptNetRelation> getRelationsTo(Assertion assertion) {
        ConceptNetRelations cnr = relations_to_assertion.get(assertion);
        if (cnr == null) {
            /* return an empty relation list */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }
        return cnr.getUnmodifiableRelations();
    }

    /**
     * Get the set of all registered concepts whose wordphrase contains a case-insensitive (sub)string. Be advised this is not an efficient lookup.
     * This library has been developed for high-speed graph traversal; yet there is no fulltext index (at the moment) of concept names.
     *
     * @param  searchstring Part of the wordphrase describing the concept, case-insensitive. Use whitespaces, no underscores. For example: "world"
     * @return              A set of unique concepts
     */
    public Set<Concept> findConcepts(String searchstring) {
        /*
         * Notice we can safely search on just a single hashmap (either relations_from_concept or relations_to_concept) here
         * because we are are certain all available concepts will be stored in both maps.
         *
         * We could improve performance at the cost of using even more memory, by optionally creating an additional caching/reference structure,
         * such as a dictionary sorted trie.
         */
        return Collections.unmodifiableSet(relations_from_concept.keySet().parallelStream().filter(c -> StringUtils.containsIgnoreCase(c.getWordphrase(), searchstring)).collect(Collectors.toSet()));
    }

    /**
     * Get the set of all registered concepts with a particular part-of-speech tag and whose wordphrase contains a case-insensitive (sub)string.
     * Be advised this is not an efficient lookup. This library has been developed for high-speed graph traversal;
     * yet there is no fulltext index (at the moment) of concept names.
     *
     * @param  searchstring Part of the wordphrase describing the concept, case-insensitive. Use whitespaces, no underscores. For example: "world"
     * @param  searchpos    The constant POS tag of the concept. For example: Concept.Pos.VERB
     * @return              A set of unique concepts
     */
    public Set<Concept> findConcepts(String searchstring, Concept.Pos searchpos) {
        /*
         * Notice we can safely search on just a single hashmap (either relations_from_concept or relations_to_concept) here
         * because we are are certain all available concepts will be stored in both maps.
         */
        return Collections.unmodifiableSet(relations_from_concept.keySet().parallelStream().filter(c -> c.getPOS() == searchpos).filter(c -> StringUtils.containsIgnoreCase(c.getWordphrase(), searchstring)).collect(Collectors.toSet()));
    }

    /**
     * Verify whether a concept is known inside ConceptNet. This function seeks an exact, unique match.
     * It takes into account all relevant concept attributes. The additional Numberbatch vector information is not taken into account, however.
     *
     * So, if the query concept is /c/en/direct, this function will not return true for /c/en/direct/v or /c/en/direct/a concepts.
     *
     * @param  concept Concept whose existence is to be verified.
     * @return         True if concept is known, and false if concept is not known.
     */
    public boolean known(Concept concept) {
        if (relations_from_concept.get(concept) != null || relations_to_concept.get(concept) != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Lookup a specific concept inside ConceptNet. This function seeks an exact, unique match.
     * It takes into account all relevant concept attributes. The additional Numberbatch vector information is not taken into account, however.
     *
     * The returned object is often nearly identical to the concept which is passed as the parameter, which would make it a redundant function
     * compared to the {@link #known(Concept concept) known()} method, but it *may* be richer.
     *
     * Specifically: the returned object can contain the Numberbatch vector data for the concept we queried.
     *
     * @param  concept Concept to lookup
     * @return         an instance of the concept if it is found in ConceptNet
     */
    public Optional<Concept> get(Concept concept) {
        ConceptNetRelations in_from = relations_from_concept.get(concept);
        if (in_from != null) { return Optional.of((Concept) in_from.key); }
        ConceptNetRelations in_to = relations_to_concept.get(concept);
        if (in_to != null) { return Optional.of((Concept) in_to.key); }
        return Optional.empty();
    }

    /**
     * Returns all concepts in the database as a Java stream.
     * 
     * @return  a stream of Concept
     */
    public Stream<Concept> stream() {
        Set<Concept> concepts = Stream.concat(relations_from_concept.keySet().stream(), relations_to_concept.keySet().stream()).collect(Collectors.toSet());
        return concepts.stream();
    }

    /**
     * Returns all concepts in the database as a Java stream. Attempts to parallelize.
     * 
     * @return  a stream of Concept
     */
    public Stream<Concept> parallelStream() {
        Set<Concept> concepts = Stream.concat(relations_from_concept.keySet().stream(), relations_to_concept.keySet().stream()).collect(Collectors.toSet());
        return concepts.parallelStream();
    }

    /**
     * Calculate the Euclidean distance between two concepts according to their Numberbatch vectors.
     *
     * @param  c1 The first concept
     * @param  c2 The second concept
     * @return    a Double of the Euclidean distance if it could be calculated
     */
    public Optional<Double> getDistance(Concept c1, Concept c2) {

        /* Verify both concepts are known and have Numberbatch vectors first */

        Optional<Concept> oc1 = get(c1);
        if (!oc1.isPresent()) {
            return Optional.empty();
        }
        Optional<Concept> oc2 = get(c2);
        if (!oc2.isPresent()) {
            return Optional.empty();
        }
        Concept concept1 = oc1.get();
        Concept concept2 = oc2.get();
        Optional<RealVector> ov1 = concept1.getVector();
        if (!ov1.isPresent()) {
            return Optional.empty();
        }
        Optional<RealVector> ov2 = concept2.getVector();
        if (!ov2.isPresent()) {
            return Optional.empty();
        }
        RealVector vector1 = ov1.get();
        RealVector vector2 = ov2.get();

        /* After verification, calculate and return Euclidean distance */

        return Optional.of(vector1.getDistance(vector2));

    }        

    /**
     * Calculate the cosine similarity between two concepts according to their Numberbatch vectors.
     *
     * @param  c1 The first concept
     * @param  c2 The second concept
     * @return    a Double of the cosine similarity if it could be calculated
     */
    public Optional<Double> getCosineSimilarity(Concept c1, Concept c2) {

        /* Verify both concepts are known and have Numberbatch vectors first */

        Optional<Concept> oc1 = get(c1);
        if (!oc1.isPresent()) {
            return Optional.empty();
        }
        Optional<Concept> oc2 = get(c2);
        if (!oc2.isPresent()) {
            return Optional.empty();
        }
        Concept concept1 = oc1.get();
        Concept concept2 = oc2.get();
        Optional<RealVector> ov1 = concept1.getVector();
        if (!ov1.isPresent()) {
            return Optional.empty();
        }
        Optional<RealVector> ov2 = concept2.getVector();
        if (!ov2.isPresent()) {
            return Optional.empty();
        }
        RealVector vector1 = ov1.get();
        RealVector vector2 = ov2.get();

        /* Calculate the cosine similarity */

        double dotproduct = vector1.dotProduct(vector2);
        Double computation = dotproduct / vector1.getNorm() * vector2.getNorm();
        return Optional.of(computation);
    }        

    /**
     * Find the shortest path (if any exists) in terms of ConceptNet relations from the start concept to the destination concept.
     * In the event multiple routes of equal length exist, and the weighted parameter is set to false, this function does not specify which of those routes will be returned.
     * If the weighted parameter is true and multiple routes with an identical high weight sum are available, this function does not specify which of those routes will be returned.
     * Therefore this function is not completely deterministic. If you want to have a deterministic function, you should use {@link #findOptimalPaths(Concept start, Concept destination, int maxdepth, boolean non_negative) findOptimalPaths()}
     *
     * @param start        The concept to start the search from.
     * @param destination  The destination concept to find a path towards.
     * @param maxdepth     The maximum search depth after which the search function returns with simply an empty list. The number zero specifies exhaustive search..
     * @param non_negative If true, this causes the search to ignore all negatively defined relations (such as NotIsA, or relations with a negative weight).
     * @param weighted     If true, sum the weights for each step in the set of shortest paths (all paths having the minimal amount of steps) and return the path with the highest weight.
     * @return             An ordered list of ConceptNetRelation instances describing the path from the input concept to the output concept.
     */
    public List<ConceptNetRelation> findShortestPath(Concept start, Concept destination, int maxdepth, boolean non_negative, boolean weighted) {
        if (maxdepth == 0) {
            maxdepth = Integer.MAX_VALUE;
        }
        List<List<ConceptNetRelation>> successful_paths = weighted ? new ArrayList<List<ConceptNetRelation>>() : null;

        List<List<ConceptNetRelation>> running_paths = new ArrayList<List<ConceptNetRelation>>();
        Set<Concept> concepts_reached = new HashSet<Concept>();
        concepts_reached.add(start);     /* we have already reached our start node */
        if (relations_to_concept.get(destination) == null || relations_from_concept.get(start) == null) {
            /* non-existent node. return an empty relation list as result */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }

        /* verify our destination node is reachable */

        List<ConceptNetRelation> relations_end = relations_to_concept.get(destination).getRelations();
        if (relations_end.isEmpty()) {
            /* no paths. return an empty relation list as result */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }

        /* add all initial paths as separate and individual paths to our running_paths variable */

        List<ConceptNetRelation> relations_start = relations_from_concept.get(start).getRelations();
        if (relations_start.isEmpty()) {
            /* no paths. return an empty relation list as result */
            List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
            return Collections.unmodifiableList(relations);
        }

        for (ConceptNetRelation cr : relations_start) {
            if (non_negative && cr.isNegative()) { continue; }
            /* make a list with a single element and add is as path */
            List<ConceptNetRelation> newlist = new ArrayList<ConceptNetRelation>();
            newlist.add(cr);
            /* perhaps the destination is reachable in a single step */
            if (((Concept)cr.getTarget()).equals(destination)) {
                 /* here we exit early if leaf = destination, because we have found the shortest path. */
                return Collections.unmodifiableList(newlist);
            }
            running_paths.add(newlist);
        }

        /*
         * NOTICE: at least the Oracle JVM succeeds in auto-parallelizing some of this code, which speeds up the
         * search a lot. We should verify this auto-parallelization is not lost with any optimization attempt we make.
         */

        for (int depth = 2; depth <= maxdepth; depth++) {
            /*
             * Expand existing running paths, thereby executing a breadth first search.
             * To keep everything simple, we reconstruct running_paths with all paths with newer lengths.
             */
            List<List<ConceptNetRelation>> expanded_paths = new ArrayList<List<ConceptNetRelation>>();
            boolean found_shortest_path = false;
            for (List<ConceptNetRelation> path : running_paths) {
                Concept last = (Concept) path.get(path.size() - 1).getTarget();
                ConceptNetRelations cnr = relations_from_concept.get(last);
                if (cnr == null) {
                    /* destination concept is not in database */
                    continue; 
                }
                List<ConceptNetRelation> leafs = cnr.getRelations();
                if (leafs.isEmpty()) {
                    /* no relations extend onwards from this path */
                    continue;
                }

                for (ConceptNetRelation leaf : leafs) {
                    if (non_negative && leaf.isNegative()) { continue; }

                    if (concepts_reached.contains((Concept) leaf.getTarget())) {
                        continue;
                    }

                    concepts_reached.add((Concept) leaf.getTarget());

                    /* create a new, longer path */
                    List<ConceptNetRelation> new_path = new ArrayList<ConceptNetRelation>();
                    for (ConceptNetRelation step : path) {
                        ConceptNetRelation copy_step = new ConceptNetRelation(step);
                        new_path.add(copy_step);
                    }

                    new_path.add(leaf);

                    if (leaf.getTarget().equals(destination)) {
                        /* here we exit early if leaf = destination, because we have found the shortest path and are not interested in weights. */
                        if (!weighted) {
                            return Collections.unmodifiableList(new_path);
                        }
                        found_shortest_path = true;
                        successful_paths.add(new_path);
                    }

                    /* it only makes sense to schedule this path for further expansion if we haven't reached the maximum depth yet, or have not found a shorter path */
                    if (!found_shortest_path && depth < maxdepth) {
                        expanded_paths.add(new_path);
                    }
                }
            }
            if (found_shortest_path) {
                /* apply weighting function */
                List<ConceptNetRelation> bestpath = null;
                boolean have_best_weight = false;
                float bestweight = 0;
                for (List<ConceptNetRelation> path : successful_paths) {
                    float sumweight = 0;
                    for (ConceptNetRelation cr : path) {
                        sumweight += cr.getWeight();
                    }
                    if (!have_best_weight) {
                        bestweight = sumweight;
                        bestpath = path;
                        have_best_weight = true;
                    } else if (sumweight > bestweight) {
                        bestweight = sumweight;
                        bestpath = path;
                    }
                }
                return Collections.unmodifiableList(bestpath);
            }
            running_paths = expanded_paths;
            if (running_paths.isEmpty()) {
                /* all paths are dead ends */
                break;
            }
        }     
        /* return an empty relation list as result */
        List<ConceptNetRelation> relations = new ArrayList<ConceptNetRelation>();
        return Collections.unmodifiableList(relations);
    }

    /**
     * Find all paths (if any exist) in terms of ConceptNet relations from the start concept to the destination concept.
     * Only 'optimal' paths are returned. This means: the result set shall not contain any paths whose nodes could be reached in a shorter
     * way through a different route. If the same nodes could be reached through a different route of equal length however, those routes
     * will be present in the result set.
     *
     * @param start        The concept to start the search from.
     * @param destination  The destination concept to find a path towards.
     * @param maxdepth     The maximum search depth after which the search function returns with simply an empty list. The number zero specifies exhaustive search.
     * @param non_negative If true, this causes the search to ignore all negatively defined relations (such as NotIsA, or relations with a negative weight)
     * @return             An ordered list of ConceptNetRelation instances describing the path from the input concept to the output concept
     */
    public List<List<ConceptNetRelation>> findOptimalPaths(Concept start, Concept destination, int maxdepth, boolean non_negative) {
        if (maxdepth == 0) {
            maxdepth = Integer.MAX_VALUE;
        }

        List<List<ConceptNetRelation>> successful_paths = new ArrayList<List<ConceptNetRelation>>();

        List<List<ConceptNetRelation>> running_paths = new ArrayList<List<ConceptNetRelation>>();
        Set<Concept> concepts_reached = new HashSet<Concept>();
        concepts_reached.add(start);     /* we have already reached our start node */
        if (relations_to_concept.get(destination) == null || relations_from_concept.get(start) == null) {
            /* non-existent node. return an empty list as result */
            return Collections.unmodifiableList(successful_paths);
        }

        /* verify our destination node is reachable */

        List<ConceptNetRelation> relations_end = relations_to_concept.get(destination).getRelations();
        if (relations_end.isEmpty()) {
            /* no paths. return an empty list as result */
            return Collections.unmodifiableList(successful_paths);
        }

        /* add all initial paths as separate and individual paths to our running_paths variable */

        List<ConceptNetRelation> relations_start = relations_from_concept.get(start).getRelations();
        if (relations_start.isEmpty()) {
            /* no paths. return an empty list as result */
            return Collections.unmodifiableList(successful_paths);
        }

        for (ConceptNetRelation cr : relations_start) {
            if (non_negative && cr.isNegative()) { continue; }
            /* make a list with a single element and add is as path */
            List<ConceptNetRelation> newlist = new ArrayList<ConceptNetRelation>();
            newlist.add(cr);
            /* perhaps the destination is reachable in a single step */
            if (((Concept)cr.getTarget()).equals(destination)) {
                /* add to the successful paths list and do *not* extend this path any further, because it has reached its destination */
                successful_paths.add(newlist);
            } else {
                running_paths.add(newlist);
            }
        }

        /*
         * NOTICE: at least the Oracle JVM succeeds in auto-parallelizing some of this code, which speeds up the
         * search a lot. We should verify this auto-parallelization is not lost with any optimization attempt we make.
         */

        for (int depth = 2; depth <= maxdepth; depth++) {

            /*
             * Expand existing running paths, thereby executing a breadth first search.
             * To keep everything simple, we reconstruct running_paths with all paths with newer lengths.
             */
            List<List<ConceptNetRelation>> expanded_paths = new ArrayList<List<ConceptNetRelation>>();
            Set<Concept> concepts_reached_during_this_expand = new HashSet<Concept>();
            for (List<ConceptNetRelation> path : running_paths) {
                Concept last = (Concept) path.get(path.size() - 1).getTarget();
                ConceptNetRelations cnr = relations_from_concept.get(last);
                if (cnr == null) {
                    /* destination concept is not in database */
                    continue; 
                }
                List<ConceptNetRelation> leafs = cnr.getRelations();
                if (leafs.isEmpty()) {
                    /* no relations extend onwards from this path */
                    continue;
                }

                for (ConceptNetRelation leaf : leafs) {
                    if (non_negative && leaf.isNegative()) { continue; }

                    if (concepts_reached.contains((Concept) leaf.getTarget())) {
                        continue;
                    }
                    /* we register nodes already processed, but exempt the destination node, because we want to find all unique paths to this node */
                    if (!leaf.getTarget().equals(destination)) {
                        concepts_reached_during_this_expand.add((Concept) leaf.getTarget());
                    }

                    /* create a new, longer path */
                    List<ConceptNetRelation> new_path = new ArrayList<ConceptNetRelation>();
                    for (ConceptNetRelation step : path) {
                        ConceptNetRelation copy_step = new ConceptNetRelation(step);
                        new_path.add(copy_step);
                    }
                    new_path.add(leaf);

                    if (leaf.getTarget().equals(destination)) {
                        /* add to the successful paths list and do *not* extend this path any further, because it has reached its destination */
                        successful_paths.add(new_path);
                        continue;
                    }

                    /* It only makes sense to schedule this path for further expansion if we haven't reached the maximum depth yet */
                    if (depth < maxdepth) {
                        expanded_paths.add(new_path);
                    }
                }
            }
            concepts_reached.addAll(concepts_reached_during_this_expand);
            running_paths = expanded_paths;
            if (running_paths.isEmpty()) {
                /* all paths are dead ends */
                break;
            }
        }     
        return Collections.unmodifiableList(successful_paths);
    }

    protected class ConceptNetRelations {

        public Object key;                             /* this stores the associated key during construction of the object */
        public List<ConceptNetRelation> relations;

        public ConceptNetRelations(Object key) {
            /*
             * ConceptNetRelations objects are typically used as values in a HashMap.
             * We store the associated 'key' during construction of the object to allow us to efficiently lookup the corresponding key when
             * we access the value.
             */
            this.key = key;
            relations = new ArrayList<ConceptNetRelation>();
        }

        /* add an existing ConceptNetRelation object */
        public void addRelation(ConceptNetRelation relation) {
            relations.add(relation);
        }

        /* add relation to a concept (creates it on the fly) */
        public void addRelation(String relationtype, Concept concept) {
            relations.add(new ConceptNetRelation(relationtype, concept));
        }

        /* add relation to a concept, with a certain weight (creates it on the fly) */
        public void addRelation(String relationtype, Concept concept, float weight) {
            relations.add(new ConceptNetRelation(relationtype, concept, weight));
        }

        /* add relation to an assertion (a meta assertion) (creates it on the fly) */
        public void addRelation(String relationtype, Assertion target) {
            relations.add(new ConceptNetRelation(relationtype, target));
        }

        /* add relation to an assertion (a meta assertion), with a certain weight (creates it on the fly) */
        public void addRelation(String relationtype, Assertion target, float weight) {
            relations.add(new ConceptNetRelation(relationtype, target, weight));
        }

        /* return the relations referenced here */
        public List<ConceptNetRelation> getRelations() {
            return relations;
        }

        /*
         * This version of getRelations() returns the list of relations in a read-only class wrapper.
         * It is used to prevent, by default, the end-users of the library from modifying the database.
         * If a user *does* wish to modify the database, currently the best approach would be
         * to create a class which extends the ConceptNet class.
         */
        public List<ConceptNetRelation> getUnmodifiableRelations() {
            return Collections.unmodifiableList(relations);
        }

    }

}

