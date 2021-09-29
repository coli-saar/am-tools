package de.saar.coli.amrtagging.formalisms.cogs.tools;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm;
import de.saar.coli.amrtagging.formalisms.cogs.LogicalFormConverter;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.util.Counter;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.math.NumberUtils.min;


/**
 * Converts an AM Dependency corpus (amconll) into COGS format (.tsv) (tokens, logical form, generalization type)
 *
 * Also reports exact match accuracy and average token-level edit distance if gold corpus is given.
 * You can get a lot more detailed evaluation output (logical form mismatches, exact match per generalization type) if
 * you provide add the verbosity option
 * @author pia (weissenh)
 */
public class ToCOGSCorpus {
    @Parameter(names = {"--corpus", "-c"}, description = "Path to the input corpus with decoded AM dependency trees")//, required = true)
    private String corpusPath = "/home/wurzel/HiwiAK/cogs2021/toy_model_run/prediction_output_astar/COGS_pred.amconll";

    @Parameter(names = {"--outFile", "-o"}, description = "Path for output tsv file")//, required = true)
    private String outPath = "/home/wurzel/HiwiAK/cogs2021/toy_model_run/prediction_output_fixedt/predictions.tsv";

    @Parameter(names={"--gold","-g"}, description = "Path to gold corpus. Make sure it contains exactly the same instances, in the same order.")//, required=true)
    private String goldCorpus = null; // "/PATH/TO/TSVFILE.tsv";
    //private String goldCorpus = "/home/wurzel/HiwiAK/cogs2021/small/gen100.tsv";

    @Parameter(names = {"--verbose"}, description = "if this flag is set, prints more information (to the error stream")
    private boolean verbose=false;

    @Parameter(names = {"--excludePrimitives"}, description = "(deprecated): if set, strips primitives from training data (3rd col == 'primitive')")
    private boolean excludePrimitives=false;
    // Deprecated: was necessary for pre-experiments. Note that this flag will only strip 1-word samples (3rd column is
    // 'primitive' then), but does NOT strip anything from the gen.tsv file (all full sentences, 3rd column can be
    // 'prim_to_*', but not 'primitive').

    @Parameter(names = {"--help", "-?","-h"}, description = "displays help if this is the only command", help = true)
    private boolean help=false;


    public static void main(String[] args) throws IOException, ParseException, ParserException, AlignedAMDependencyTree.ConllParserException{
        ToCOGSCorpus cli = new ToCOGSCorpus();
        JCommander commander = new JCommander(cli);
        try {
            commander.parse(args);
        } catch (com.beust.jcommander.ParameterException ex) {
            System.err.println("An error occured: " + ex.toString());
            System.err.println("\n Available options: ");
            commander.usage();
            return;
        }

        if (cli.help) {
            commander.usage();
            return;
        }

        System.out.println("Excluding primitives? " + cli.excludePrimitives);
        System.out.println("Input file: " + cli.corpusPath);

        List<AmConllSentence> sents = AmConllSentence.readFromFile(cli.corpusPath);

        RawCOGSReader goldReader = null;
        if (cli.goldCorpus != null) {
            System.out.println("Gold file: " + cli.goldCorpus);
            goldReader = new RawCOGSReader(cli.goldCorpus);
        }
        PrintWriter graphWriter = null;
        if (cli.outPath != null) {
            System.out.println("Output file: " + cli.outPath);
            graphWriter = new PrintWriter(cli.outPath);
        }
        if (cli.verbose) {
            System.err.println("Verbosity flag has been set: " +
                    "will print information about gold-prediction mismatches to the error stream!");
        }

        // todo scorer?
        int totalSentencesSeen = 0;
        int totalExactMatches = 0;
        int totalEditDistance = 0;
        int illformednessErrors = 0;
        // for each type and for each metric in (Exact match, edit distance) compute score
        Map<String, Float> type2editdsum = new HashMap<>();  // active_to_passive  -> 5.7 (summation of edit distances)
        Map<String, Float> type2exactmsum = new HashMap<>();  // active_to_passive  -> 3.0 (summation of exact matches)
        Counter<String> type2count = new Counter<>(); // active_to_passive -> 12 (seen 12 samples)
        Counter<String> illformedErrorsPerGenTypeCntr = new Counter<>();
        Counter<Integer> ppRecursionDepths2count = new Counter<>();  // recursion depth evaluation
        Counter<Integer> cpRecursionDepths2count = new Counter<>();
        Counter<Integer> ppRecursionDepths2success = new Counter<>();
        Counter<Integer> cpRecursionDepths2success = new Counter<>();

        for (AmConllSentence amsent : sents) {
            totalSentencesSeen += 1;
            String id = amsent.getAttr("id") != null ? amsent.getAttr("id") : "NO-ID";
            //if (! id.startsWith("#")) id = "#" + id;
            List<String> tokens = amsent.words();  // todo any art-root I have to exclude?

            boolean isIllformed = false;
            /*try {*/
            // Step 1: Evaluate predicted AM dep tree to graph
            // AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(amsent);
            // SGraph evaluatedGraph = amdep.evaluate(true);
            // Step 2: Convert this graph to logical form
            COGSLogicalForm predictedLF;
            String predicedLFString = "";  // "INVALIDLF"
            try {
                predictedLF = LogicalFormConverter.toLogicalForm(amsent);
                predicedLFString = predictedLF.toString();
            }
            catch (IllegalArgumentException illegalArgumentException) {  // todo can I use a cogs specific conversion exception instead?
                // Exception in thread "main" java.lang.IllegalArgumentException: Ill-formed logical form? First argument of term can't be proper name.
                // todo maybe not raise exception there?
                // sentence number " + totalSentencesSeen + " at input file line "+ amsent.getLineNr()
                isIllformed = true;
                illformednessErrors += 1;
                System.err.println("Sentence no. "+ totalSentencesSeen+" at input file line: " + amsent.getLineNr() +
                        ": Error during graph to COGS logical form conversion: " +
                        "Proceed with '"+predicedLFString+"' as dummy logical form output. " +
                        "Error message: '"+illegalArgumentException.getMessage()+"'.");
                //illegalArgumentException.printStackTrace();
                predictedLF = null;
            }
            catch (AlignedAMDependencyTree.ConllParserException ex) {
                // e.g. "There seems to be no root for this AmConllSentence. Line .."
                // (once occurred when it wasn't properly trained)
                isIllformed = true;
                illformednessErrors += 1;
                System.err.println("Sentence no. "+ totalSentencesSeen+" at input file line: " + amsent.getLineNr() +
                        ": AM dependency tree has a problem: " +
                        "Proceed with '"+predicedLFString+"' as dummy logical form output. " +
                        "Error message: '"+ex.getMessage()+"'.");
                //ex.printStackTrace();
                predictedLF = null;
            }
            // Graph outputSent = SGraphConverter.toSDPGraph(evaluatedGraph, sdpSent); //add edges

            // Step 3: if gold data available: get graph there too and evaluate
            String genType = "";  // used for writing 3rd column in the output file
            if (goldReader != null) {
                // (a) Input validation
                if (!goldReader.hasNext()) {
                    throw new RuntimeException("No more samples in gold reader (but still amconll sentences left!)");
                }
                // read = true;
                RawCOGSReader.CogsSample sample = goldReader.getNextSample();
                genType = sample.generalizationType;
                if (cli.excludePrimitives && genType.equals("primitive")) {
                    if (!goldReader.hasNext()) {
                        throw new RuntimeException("No more samples in gold reader (but still amconll sentences left!)");
                    }
                    sample = goldReader.getNextSample();
                    genType = sample.generalizationType;
                }
                if (cli.verbose && !sample.src_tokens.equals(tokens)) {
                    System.err.println("--Currently processing the nth sentence, where n=" + totalSentencesSeen);
                    System.err.println("--Tokens from Gold:    " + sample.src_tokens);
                    System.err.println("--Tokens from amconll: " + tokens);
                    throw new RuntimeException("Sentence of amconll and gold file don't match! " +
                            "AmConLL sentence line no: " + amsent.getLineNr());
                }
                if (isIllformed) {
                    illformedErrorsPerGenTypeCntr.add(genType);
                }

                // (b) get logical form
                COGSLogicalForm goldLF = new COGSLogicalForm(sample.tgt_tokens);
                // MRInstance mr = LogicalFormConverter.toSGraph(lf, sample.src_tokens);
                // SGraph goldGraph = mr.getGraph();

                // (c) evaluate gold against predicted logical form
                // todo edit distance: where to get tokens from? toString and then whitespace split?
                // -- Levenshtein token-level edit distance
                String[] tokens1 = goldLF.toString().split(" ");
                String[] tokens2 = predicedLFString.split(" ");
                int editDistance = cli.getEditDistance(tokens1, tokens2);
                totalEditDistance += editDistance;
                Float current = type2editdsum.getOrDefault(genType, 0.0F);
                type2editdsum.put(genType, current+editDistance);
                type2count.add(genType);

                // -- Exact match: todo no need to compare strings here, just rely on token-edit distance == 0 or not?
                boolean exactMatch = goldLF.toString().equals(predicedLFString);
                assert((editDistance == 0) == exactMatch);  // edit distance is 0 if and only if we have an exact match
                if (!type2exactmsum.containsKey(genType)) {
                    type2exactmsum.put(genType, 0.0F);
                }
                if (exactMatch) {
                    totalExactMatches += 1;
                    current = type2exactmsum.get(genType);
                    type2exactmsum.put(genType, current+1);
                }
                else if (cli.verbose) {
                    System.err.println("--No exact match for sentence number " + totalSentencesSeen + " at input file line "+ amsent.getLineNr()+" :");
                    System.err.println("  Edit distance: " + editDistance + " || Gen type: " + genType);
                    System.err.println("  Gold:   " + goldLF.toString());
                    System.err.println("  System: " + predicedLFString);
                }
                if (cli.verbose && genType.endsWith("recursion")) { // cp/pp recursion: depth
                    int depth = cli.getRecursionDepth(genType, sample.src_tokens);
                    if (genType.startsWith("cp_recursion")) {
                        cpRecursionDepths2count.add(depth);
                        if (exactMatch) cpRecursionDepths2success.add(depth);
                    }
                    if (genType.startsWith("pp_recursion")) {
                        ppRecursionDepths2count.add(depth);
                        if (exactMatch) ppRecursionDepths2success.add(depth);
                    }
                }
            }

            // Step 4: Write the graph (the one based on the amconll sentence) to file [if output path was provided]
            if (graphWriter != null) {
                // todo use string builder? check correctness?
                graphWriter.println(String.join(" ", tokens)+"\t"+predicedLFString+"\t"+genType);
            }
            /*} catch (Exception ex) {
                System.err.printf("In line %d, id=%s: ignoring exception.\n", amsent.getLineNr(), id);
                ex.printStackTrace();
                if (graphWriter != null) {
                    System.err.println("Writing dummy logical form instead\n");
                    graphWriter.println(String.join(" ", tokens) + "\tDUMMY\tERROR");
                }
                // if (!read && goldReader != null){ // update scores? }
            }*/
        }  // for each amconll sentence

        if (graphWriter != null) {
            graphWriter.close();
        }
        if (goldReader != null) {
            double result;

            // NB: numbers will be extracted using regexes in eval_commands.libsonnet of the am-parser
            // System.out.println("Exact match accuracy");
            result = totalSentencesSeen > 0 ? (float) totalExactMatches / totalSentencesSeen : 0;
            result *= 100;  // 95% should be displayed as '95' instead of 0.95
            System.out.println("Exact match accuracy: " + String.format(java.util.Locale.US,"%.2f", result));
            // 2 decimal places, use US locale to determine character for decimal point

            // System.out.println("Average token-level edit distance");
            result = totalSentencesSeen > 0 ? (float) totalEditDistance / totalSentencesSeen : 0;
            System.out.println("Average token-level edit distance: " + String.format(java.util.Locale.US,"%.4f", result));

            System.out.println("Ill-formedness errors: " + illformednessErrors);

            // metrics per generalization type if verbose output requested
            if (cli.verbose) {
                System.out.println("Ill-formedness errors per generalization type:");
                // errorsPerGenTypeCntr.printAllSorted(); // writes to stderr
                List<Object2IntMap.Entry<String>> entryList = illformedErrorsPerGenTypeCntr.getAllSorted();
                for (Object2IntMap.Entry<String> entry : entryList) {
                    System.out.println("\t" + entry.getIntValue() + "\t" + entry.getKey() );
                }

                System.out.println("Per generalization type scores:");
                System.out.println("\t#samples\t#exactMatches\tgenType\texact match\tavg. edit distance\tavg. edit distance error cases only");
                // preparation: get all seen types in the right order
                ArrayList<String> genTypesSorted = cli.getGenTypesSorted();
                Set<String> seenTypes = type2count.getAllSeen();
                Set<String> noDupl = new HashSet<>();
                for (String type: seenTypes) {  // todo: don't do this: for each seenType iterate over 21-sized list!
                    if (!genTypesSorted.contains(type)) {
                        noDupl.add(type);
                    }
                }
                genTypesSorted.addAll(noDupl);

                // Print one line per seen generalization type
                for (String type: genTypesSorted) {
                    int count = type2count.get(type);
                    if (count == 0) {
                        continue;
                    }
                    Float exactMatchCount = type2exactmsum.get(type);
                    Float exactmAvg = exactMatchCount / count;
                    Float editdAvg = type2editdsum.get(type) / count;
                    Float editdAvgErrorOnly = exactMatchCount == count ? 0.0F : type2editdsum.get(type) / (count-exactMatchCount);
                    System.out.println("\t"+count + "\t" + exactMatchCount
                            +"\t"+ type
                            +"\t"+ String.format(java.util.Locale.US,"%.2f", exactmAvg)
                            +"\t"+ String.format(java.util.Locale.US,"%.2f", editdAvg)
                            +"\t"+ String.format(java.util.Locale.US,"%.2f", editdAvgErrorOnly)
                            );
                }

                // Recursion depths:
                System.out.println("Recursion depths:\n" +
                        "Depth\tCP (count,successes,success rate)\tPP (count,successes,success rate) ");
                Set<Integer> depths = new HashSet<>(cpRecursionDepths2count.getAllSeen());
                depths.addAll(ppRecursionDepths2count.getAllSeen());
                List<Integer> depthsSorted = depths.stream().sorted().collect(Collectors.toList());
                for (Integer depth: depthsSorted) {
                    int cp_count = cpRecursionDepths2count.get(depth);
                    int pp_count = ppRecursionDepths2count.get(depth);
                    int cp_successes = cpRecursionDepths2success.get(depth);
                    int pp_successes = ppRecursionDepths2success.get(depth);
                    float cp_success_rate = cp_count > 0 ? cp_successes / (float) cp_count : 0.0f;
                    float pp_success_rate = pp_count > 0 ? pp_successes / (float) pp_count : 0.0f;
                    System.out.println("\t"+depth+
                            "\t"+cp_count+"\t"+cp_successes+"\t"+String.format(java.util.Locale.US,"%.3f", cp_success_rate)+
                            "\t"+pp_count+"\t"+pp_successes+"\t"+String.format(java.util.Locale.US,"%.3f", pp_success_rate)
                    );
                }
            } // if verbose
        }


    }

    // todo test this implementation (including whether the loops are off-by-one)
    public int getEditDistance(String[] tokens1, String[] tokens2) {
        Objects.requireNonNull(tokens1);
        Objects.requireNonNull(tokens2);
        // todo can I use a default implementation of Levenshtein distance?
        // often, it's about a String of characters, like here:
        // https://commons.apache.org/proper/commons-text/apidocs/org/apache/commons/text/similarity/LevenshteinDistance.html
        // but we need to compare an array of Strings. The current implementation is adapted from the algorithm here
        // https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_two_matrix_rows  (it's using char[] )
        // String[] tokens1  // s  char s[0..m-1],
        // String[] tokens2  // t  char t[0..n-1]
        int length1 = tokens1.length;  // m
        int length2 = tokens2.length;  // n
        // create two work vectors of integer distances
        int[] v0 = new int[length2+1];  // declare int v0[n + 1]
        int[] v1 = new int[length2+1];  // declare int v1[n + 1]
        // initialize v0 (the previous row of distances)
        // this row is A[0][i]: edit distance for an empty s
        // the distance is just the number of characters to delete from t
        for (int i=0; i <= length2; ++i) {  // for i from 0 to n:
            v0[i] = i;  // v0[i] = i
        }
        for (int i=0; i <= length1-1; ++i) {  // for i from 0 to m-1:
            // calculate v1 (current row distances) from the previous row v0
            // first element of v1 is A[i+1][0]
            //   edit distance is delete (i+1) chars from s to match empty t
            v1[0] = i + 1;  // v1[0] = i + 1
            // use formula to fill in the rest of the row
            for (int j=0; j <= length2-1; ++j) {  // for j from 0 to n -1:
                // calculating costs for A[i+1][j+1]
                int deletionCost = v0[j + 1] + 1;  // deletionCost:=v0[j + 1] + 1
                int insertionCost = v1[j] + 1; // insertionCost:=v1[j] + 1
                int substitutionCost;
                if (tokens1[i].equals(tokens2[j])) { // if s[i] = t[j]:
                    substitutionCost = v0[j];  // substitutionCost:=v0[j]
                }
                else {  // else
                    substitutionCost = v0[j] + 1; // substitutionCost:=v0[j] + 1
                }

            v1[j + 1] = min(deletionCost, insertionCost, substitutionCost);
            // v1[j + 1]  = minimum(deletionCost, insertionCost, substitutionCost);
            }
            // copy v1 (current row) to v0 (previous row) for next iteration
            // since data in v1 is always invalidated, a swap without copy could be more efficient

            // swap v0 with v1  todo does this work in java? by reference vs by value?
            int[] tmp = v0;
            v0 = v1;
            v1 = tmp;
        }
        // after the last swap, the results of v1 are now in v0
        // return v0[n]
        return v0[length2];
    }

    public ArrayList<String> getGenTypesSorted() {
        // generalization types sorted as in COGS paper
        return new ArrayList<>(Arrays.asList(
                "subj_to_obj_common", "subj_to_obj_proper", "obj_to_subj_common", "obj_to_subj_proper",
                "prim_to_subj_common", "prim_to_subj_proper", "prim_to_obj_common", "prim_to_obj_proper",
                "prim_to_inf_arg", "obj_pp_to_subj_pp", "cp_recursion", "pp_recursion",
                "active_to_passive", "passive_to_active", "obj_omitted_transitive_to_transitive", "unacc_to_transitive",
                "do_dative_to_pp_dative", "pp_dative_to_do_dative",
                "only_seen_as_transitive_subj_as_unacc_subj", "only_seen_as_unacc_subj_as_obj_omitted_transitive_subj",
                "only_seen_as_unacc_subj_as_unerg_subj"
        ));
    }

    public int getRecursionDepth(String genType, List<String> src_tokens) {
        int depth = -1;
        // todo: for counting can I use some built in count function on list, should i use regexes?
        // todo: test this function
        if (genType.startsWith("cp")) { // count number of "that"
            depth = 0;
            for (String token: src_tokens) {
                if (token.equals("that")) { depth++; }
            }
        }
        else if (genType.startsWith("pp")) { // count number of prepositions
            depth = 0;
            for (String token: src_tokens) {
                if (token.equals("on") || token.equals("in") || token.equals("beside")) { depth++; }
            }
        }
        return depth;
    }


}
