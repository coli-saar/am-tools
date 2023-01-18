/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.formalisms.amr.tools.DependencyExtractor;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;
import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.stanford.nlp.ling.CoreLabel;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions, mostly data reading.
 *
 * @author jonas
 */
public class Util {

    /**
     * Reads a file, where each line is supposed to be some tokens separated by a single space bar.
     * Returns a list of lines, where each line is represented as an array of tokens.
     *
     * @param path
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static List<String[]> readFile(String path) throws FileNotFoundException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(path));
        List<String[]> ret = new ArrayList<>();
        while (br.ready()) {
            String[] parts = br.readLine().split(" ");
            if (parts.length == 1 && parts[0].equals("")) {
                parts = new String[0];
            }
            ret.add(parts);
        }
        return ret;
    }

    private static Pattern typePattern = Pattern.compile("(.*?)--TYPE--(.*)");

    /**
     * Reads supertag probabilities from a file.<p>
     * <p>
     * Result dimensions are [sentences][words in the sentence][available choices for the word].
     * Text file is expected to be one sentence per line, words separated by tabs
     * and choices per word separated by single spaces. Generally reads string-probability
     * pairs in the format s|p (with no whitespace allowed in s).<p>
     * <p>
     * Applies raw2readable on all strings (replacing whitespace marker by whitespace).<p>
     * <p>
     * Update June 2019, AK: This method no longer silently returns null if the
     * input file does not exist.
     *
     * @param reader  Reader from which to read
     * @param areLogs whether probabilities are logs or not.
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static List<List<List<AnnotatedSupertag>>> readSupertagProbs(Reader reader, boolean areLogs) throws FileNotFoundException, IOException {
        /*
        if (!new File(path).exists()) {
            System.err.println("Info: file '"+path+"' does not exist, trying to proceed without it. (This is ok for the edges file)");
            return null;
        }
        */

        BufferedReader br = new BufferedReader(reader);
        List<List<List<AnnotatedSupertag>>> ret = new ArrayList<>();
        int l = 0;

        while (br.ready()) {
            String line = br.readLine();
            String[] parts = split(line, "\t");//one part per word
            List<List<AnnotatedSupertag>> sentList = new ArrayList<>();

            for (String part : parts) {
                List<AnnotatedSupertag> wordList = new ArrayList<>();
                String[] tAndPs = split(part, " ");//the possibilities for this word (each: token with prob)

                sentList.add(wordList);

                for (String tAndP : tAndPs) {
                    int sepInd = tAndP.lastIndexOf("|");
                    if (sepInd >= 0) {
                        String t = raw2readable(tAndP.substring(0, sepInd));

                        Matcher m = typePattern.matcher(t);

                        Double p = Double.valueOf(tAndP.substring(sepInd + 1));

                        if (areLogs) {
                            p = Math.exp(p);
                        }
                        AnnotatedSupertag st = m.matches() ? new AnnotatedSupertag(m.group(1), m.group(2), p) : new AnnotatedSupertag(t, null, p);

                        wordList.add(st);
                    } else {
                        System.err.println("***WARNING*** could not read probability for token " + org.apache.commons.lang3.StringEscapeUtils.escapeJava(tAndP));
                        System.err.println(l);
                        System.err.println(Arrays.toString(tAndPs));
                        System.err.println(org.apache.commons.lang3.StringEscapeUtils.escapeJava(line));
                        System.exit(2);
                    }
                }
            }

            ret.add(sentList);
            l++;
        }

        return ret;
    }


    /**
     * expects tagProbs to have probabilities, not logs.
     *
     * @param tagProbs
     * @return
     */
    public static List<List<List<AnnotatedSupertag>>> groupTagsByType(List<List<List<AnnotatedSupertag>>> tagProbs) {
        //System.err.println("All graph types found:");
        List<List<List<AnnotatedSupertag>>> ret = new ArrayList<>();
        for (List<List<AnnotatedSupertag>> sentence : tagProbs) {
            List<List<AnnotatedSupertag>> newSent = new ArrayList<>();
            StringJoiner sj = new StringJoiner("  |||  ");
            for (List<AnnotatedSupertag> word : sentence) {
                List<AnnotatedSupertag> newWord = new ArrayList<>();
                Object2DoubleMap<String> type2total = new Object2DoubleOpenHashMap<>();
                Object2DoubleMap<String> type2bestScore = new Object2DoubleOpenHashMap<>();
                Map<String, String> type2bestTag = new HashMap<>();

                for (AnnotatedSupertag tAndP : word) {
                    String type;
                    if (tAndP.graph.contains(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP)) {
                        type = tAndP.graph.split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP)[1];
                    } else {
                        type = "NULL";
                    }
                    type2total.put(type, type2total.getDouble(type) + tAndP.probability);
                    if (tAndP.probability > type2bestScore.getDouble(type)) {
                        type2bestTag.put(type, tAndP.graph);
                        type2bestScore.put(type, tAndP.probability);
                    }
                }

                for (String type : type2bestTag.keySet()) {
                    AnnotatedSupertag st = new AnnotatedSupertag(type2bestTag.get(type), type, type2total.getDouble(type));
                    newWord.add(st);
//                    newWord.add(new Pair(type2bestTag.get(type), type2total.getDouble(type)));
                }
                newWord.sort((AnnotatedSupertag o1, AnnotatedSupertag o2) -> -Double.compare(o1.probability, o2.probability)); //sort in descending order
                newSent.add(newWord);
                sj.add(type2bestTag.keySet().toString());
            }
            ret.add(newSent);
            //System.err.println(sj);
        }
        return ret;
    }


    public static List<List<List<Pair<String, Double>>>> readEdgeProbs(Reader reader, boolean exponentiate,
                                                                       double threshold, int maxLabels, boolean shift) throws FileNotFoundException, IOException {
        return readEdgeProbs(reader, exponentiate, false, threshold, maxLabels, shift);
    }


    /**
     * Reads edge probabilities from a text file. Dimensions of the result are
     * [sentences][1][edges for the sentence] (the 1 is a historical remnant).<p>
     * <p>
     * Edges with label o from i to j with probability p are expected to be in the
     * format o[i,j]|p , with one sentence per line and edges per sentence separated by tabs.<p>
     * <p>
     * Update June 2019, AK: This method no longer silently returns null if the
     * input file does not exist.
     *
     * @param reader    Reader from which to read
     * @param exponentiate   Convert every score in the file to exp(score). This can be useful if the scores in the file
     *                     are log probabilities, but you want the original probabilities.
     * @param addProbs  Add edge existence + label probs. This is useful if the values stored in the file are actually
     *                  logarithms, but you set exponentiate=false because you want the raw log probabilities instead of the
     *                  probabilities themselves. If addProbs=false (the default), then the probabilities are multiplied.
     * @param threshold threshold where to cut off probabilities for edges (non-log values).
     *                  Edges with scores below this threshold are not added to the resulting list. Try something like 0.01.
     * @param maxLabels Maximum number of labels considered per edge. If this is k,
     *                  for each unlabeled edge only the labeled versions for the k highest scoring labels are added to the list.
     *                  Try something like 5.
     * @param shift     Set to true if the edge indices are 1-based (with 0 indicating
     *                  e.g. the 'ROOT' edge or 'IGNORE' edges. Set to 0 if the edge indices are 0-based.
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static List<List<List<Pair<String, Double>>>> readEdgeProbs(Reader reader, boolean exponentiate, boolean addProbs,
                                                                       double threshold, int maxLabels, boolean shift) throws FileNotFoundException, IOException {
        /*
        if (!new File(path).exists()) {
            System.err.println("Info: file '"+path+"' does not exist, trying to proceed without it. (This is ok for the edges file)");
            return null;
        }
        */

        BufferedReader br = new BufferedReader(reader);
        List<List<List<Pair<String, Double>>>> ret = new ArrayList<>();

        sentenceLoop:
        while (br.ready()) {
            List<Pair<String, Double>> edgesHere = new ArrayList<>();
            String[] edgeStrings = br.readLine().split("\t");

            edgesInSentenceLoop:
            for (String es : edgeStrings) {
                String[] parts = es.split(" ");

                if (parts.length > 1) {
                    int sepInd = parts[0].lastIndexOf("|");
                    String edge = parts[0].substring(0, sepInd);

                    if (shift) {
                        int first = Integer.parseInt(edge.substring(1, edge.indexOf(",")));
                        int second = Integer.parseInt(edge.substring(edge.indexOf(",") + 1, edge.indexOf("]")));

                        if (first != 0 && second != 0) {
                            edge = "[" + (first - 1) + "," + (second - 1) + "]";
                        } else {
                            continue edgesInSentenceLoop;
                        }
                    }

                    // P(edge from first to second exists)
                    double p = Double.parseDouble(parts[0].substring(sepInd + 1));

                    if (exponentiate) {
                        p = Math.exp(p);
                    }

                    if (p >= threshold) {
                        for (int i = 1; i <= maxLabels && i < parts.length; i++) {
                            sepInd = parts[i].lastIndexOf("|");
                            String label = parts[i].substring(0, sepInd);

                            // P(label | edge from first to second)
                            double pl = Double.parseDouble(parts[i].substring(sepInd + 1));
                            if (exponentiate) {
                                pl = Math.exp(pl);
                            }

                            if( addProbs ) {
                                p += pl;
                            } else {
                                p *= pl;
                            }

                            edgesHere.add(new Pair(label + edge, p));
                        }
                    }
                }
            }

            ret.add(Collections.singletonList(edgesHere));
        }

        return ret;
    }

    /**
     * Converts a string of format label[headID,childID] into a nested pair structure [label, [headID, ChildID]].
     * @param edgeString
     * @return
     */
    public static Pair<String, Pair<Integer, Integer>> edgeString2Edge(String edgeString) {
        String label = edgeString.split("\\[")[0];
        //NOTE in earlier supertagger output, first and second was swapped. To properly evaluate that old output, maybe switch it here temporarily.
        Integer first = Integer.valueOf(edgeString.split("\\[")[1].split(",")[0]);
        Integer second = Integer.valueOf(edgeString.split(",")[1].split("\\]")[0]);
        return new Pair(label, new Pair(first, second));
    }


    /**
     * Returns the type part of the string representation of an as-graph.
     *
     * @param graph
     * @return
     */
    public static String getType(String graph) {
        if (graph.contains(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP)) {
            return graph.split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP)[1];
        } else {
            return graph;
        }
    }

    /**
     * splits the string 'input' with the separator 'sep', but other than the
     * String#split method, returns an empty array if the string is empty.
     *
     * @param input
     * @param sep
     * @return
     */
    public static String[] split(String input, String sep) {
        String[] ret = input.split(sep);
        if (ret.length == 1 && ret[0].equals("")) {
            ret = new String[0];
        }
        return ret;
    }

    /**
     * Call on strings from {@link #readSupertagProbs} to
     * remove whitespace markers.
     *
     * @param raw
     * @return
     */
    public static String raw2readable(String raw) {
        return raw.replaceAll(DependencyExtractor.WHITESPACE_MARKER, " ").replaceAll(" +", " ");//.replaceAll("[<>/\\\"\'_]", " ") in between
    }

    /**
     * Removes all whitespace markers from rawGraph and parses it into an s-graph.
     *
     * @param rawGraph
     * @return
     * @throws ParserException
     */
    public static SGraph graph2graph(String rawGraph) throws ParserException {
        return new GraphAlgebra().parseString(rawGraph.replaceAll(DependencyExtractor.WHITESPACE_MARKER, " "));
    }

    public static void count(Map<String, Counter<String>> map, String key, String value) {
        Counter<String> c = map.get(key);
        if (c == null) {
            c = new Counter<>();
            map.put(key, c);
        }
        c.add(value);
    }

    public static void write(Writer w, Map<String, Counter<String>> map, List<String> orderedKeys, Counter<String> totalKeyCounter) throws IOException {
        for (String label : orderedKeys) {
            w.write(label + " (" + map.get(label).sum() + "/" + totalKeyCounter.get(label) + "):   ");
            for (Object2IntMap.Entry<String> entry : map.get(label).getAllSorted()) {
                w.write(entry.getKey() + " (" + entry.getIntValue() + ")  ");
            }
            w.write("\n");
        }
    }

    public static void write(Writer w, Map<String, Counter<String>> map1, Map<String, Counter<String>> map2, List<String> orderedKeys) throws IOException {
        for (String label : orderedKeys) {
            w.write(label + " [" + map1.get(label).sum() + "]:\n");
            for (Object2IntMap.Entry<String> entry : map1.get(label).getAllSorted()) {
                w.write(entry.getKey() + "[" + entry.getIntValue() + "],  ");
            }
            w.write("\n");
            for (Object2IntMap.Entry<String> entry : map2.get(label).getAllSorted()) {
                w.write(entry.getKey() + "[" + entry.getIntValue() + "],  ");
            }
            w.write("\n\n\n");
        }
    }

    /**
     * AMR ISI codec can't cope with these characters :( :(
     *
     * @param s
     * @return
     */
    public static String fixPunct(String s) {
        return s.replaceAll("’", "'").replaceAll("–", "--").replaceAll("…", "...").replaceAll("“", "66''").replaceAll("”", "''99");
    }

    public static String unfixPunct(String s) {
        return s.replaceAll("66''", "“").replaceAll("''99", "”").replaceAll("'", "’").replaceAll("--", "–").replaceAll("\\.\\.\\.", "…");
    }

    /**
     * Converts a list of words to a list of CoreLabels with the correct
     * words and begin and end offsets.
     *
     * @param tokens
     * @return
     */
    public static List<CoreLabel> makeCoreLabelsForTokens(List<String> tokens) {
        List<CoreLabel> ret = new ArrayList<>();
        int sentlengthsofar = 0;

        for (String s : tokens) {
            int toksize = s.length();

            CoreLabel lab = CoreLabel.wordFromString(s);
            lab.setBeginPosition(sentlengthsofar);
            lab.setEndPosition(sentlengthsofar + toksize);
            ret.add(lab);

            sentlengthsofar += toksize + 1;
        }

        return ret;
    }

    /**
     * In the ISI AMR string representation, a node cannot have the label " because it means something.
     * @param reLex
     * @return 
     */
    public static String isiAMREscape(String reLex) {
        return reLex.replace("\"","\\\"");
    }

    public static String getTimeStamp() {
        Timestamp t = new Timestamp(System.currentTimeMillis());
        Date d = new Date(t.getTime());
        return d.toString();
    }

}
