/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.formalisms.amr.tools;

import de.saar.coli.amrtagging.formalisms.GeneralBlobUtils;
import de.saar.coli.amrtagging.formalisms.amr.tools.wordnet.ConceptnetEnumerator;
import de.saar.coli.amrtagging.formalisms.amr.tools.wordnet.IWordnet;
import de.saar.coli.amrtagging.formalisms.amr.tools.wordnet.WordnetEnumerator;
import de.up.ling.irtg.algebra.StringAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.IsiAmrInputCodec;
import de.up.ling.irtg.util.Counter;
import de.up.ling.irtg.util.Util;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Takes the graphs created by de.saar.coli.amrtaggin.Parser and fills in labels
 * at lexical nodes.
 *
 * @author Jonas
 */
public class Relabel {

    public static final String LEXMARKER = "LEX@";

    public static int allNameCount = 0;
    public static int seenNameCount = 0;
    public static Counter<String> seenNETypeCounter = new Counter<>();
    public static Counter<String> unseenNETypeCounter = new Counter<>();

    private Pattern MMddYY = Pattern.compile("([0-9]+)/([0-9]+)/([0-9]+)");
    private Pattern MMdd = Pattern.compile("([0-9]+)/([0-9]+)");

    /**
     * Takes the graphs created by de.saar.coli.amrtaggin.Parser and fills in
     * labels at lexical nodes. Also makes sure that the gold graphs and
     * predicted graphs are in the same order. Needs three parameters, fourth is
     * optional: 1) path to parser output, must contain files parserOut.txt
     * (output of parser, including root markers, one AMR per line),
     * sentences.txt, literal.txt, labels.txt (all from the supertagger) and a
     * file indices.txt that contains an index for each instance of parserOut
     * that refers to its original index in the sentences.txt etc files. Also
     * requires a file goldAMR.txt with the original gold AMRs. 2) path to
     * lookup folder (nameLookup.txt etc) 3) path to wordnet (3.0/dict/ folder)
     * 4) (optional) threshold for when to use the backup over the neural
     * predictions
     *
     * @param args
     * @throws IOException
     * @throws MalformedURLException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, MalformedURLException, InterruptedException {

        if (args.length < 3) {
            System.err.println("Needs three parameters, fourth is optional");
            System.err.println("1) path to parser output, must contain files parserOut.txt (output of parser, including root markers, one AMR per line), sentences.txt, literal.txt, labels.txt (all from the supertagger) "
                    + " and a file indices.txt that contains an index for each instance of parserOut that refers to its original index in the sentences.txt etc files. Also goldAMR.txt");
            System.err.println("2) path to lookup folder (nameLookup.txt etc)");
            System.err.println("3) path to wordnet (3.0/dict/ folder)");
            System.err.println("4) (optional) threshold for when to use the backup over the neural predictions");
            System.err.println("5) (optional) path to conceptnet (.csv.gz file)");
        }

        String parserOutPath = args[0]; // must contain files parserOut.txt (output of parser, including root markers, one AMR per line), sentences.txt, literal.txt, labels.txt (all from the supertagger)
        // and a file indices.txt that contains an index for each instance of parserOut that refers to its original index in the sentences.txt etc files. Also goldAMR.txt
        String lookupPath = args[1];
        String wordnetPath = args[2];

        int threshold = 10;
        if (args.length > 3) {
            threshold = Integer.parseInt(args[3]);
        } else {
            System.err.println("No threshold parameter given, using default " + threshold);
        }

        String conceptnetPath = null;
        if (args.length > 4) {
            conceptnetPath = args[4];
        }

        Relabel relabel = new Relabel(wordnetPath, conceptnetPath, lookupPath, threshold, 0);

        BufferedReader sentBR = new BufferedReader(new FileReader(parserOutPath + "sentences.txt"));
        List<String> sentences = sentBR.lines().collect(Collectors.toList());
        BufferedReader litBR = new BufferedReader(new FileReader(parserOutPath + "literal.txt"));
        List<String> literals = litBR.lines().collect(Collectors.toList());
        BufferedReader labelBR = new BufferedReader(new FileReader(parserOutPath + "labels.txt"));
        List<String> nnLabels = labelBR.lines().collect(Collectors.toList());
        BufferedReader goldBR = new BufferedReader(new FileReader(parserOutPath + "goldAMR.txt"));
        List<String> golds = goldBR.lines().collect(Collectors.toList());

        BufferedReader indicesBR = new BufferedReader(new FileReader(parserOutPath + "indices.txt"));
        BufferedReader parserOutBR = new BufferedReader(new FileReader(parserOutPath + "parserOut.txt"));

        FileWriter w = new FileWriter(parserOutPath + "relabeled.txt");
        FileWriter goldW = new FileWriter(parserOutPath + "gold_orderedAsRelabeled.txt");

        int orphanDateEdges = 0;

        StringAlgebra alg = new StringAlgebra();
        while (parserOutBR.ready() && indicesBR.ready()) {
            SGraph graph = new IsiAmrInputCodec().read(parserOutBR.readLine());
            int index = Integer.parseInt(indicesBR.readLine());
            try {
                relabel.fixGraph(graph, alg.parseString(sentences.get(index)), alg.parseString(literals.get(index)), alg.parseString(nnLabels.get(index)));
            } catch (java.lang.Exception ex) {
                ex.printStackTrace();
            }
            for (GraphEdge e : graph.getGraph().edgeSet()) {
                if ((e.getLabel().equals("day") || e.getLabel().equals("month") || e.getLabel().equals("year"))
                        && !e.getSource().getLabel().equals("date-entity")) {
                    orphanDateEdges++;
                }
            }
            w.write(graph.toIsiAmrString() + "\n\n");
            goldW.write(golds.get(index) + "\n\n");
        }

        System.err.println("orphan date edges: " + orphanDateEdges);

        w.close();
        goldW.close();

//        System.err.println(allNameCount);
//        System.err.println(seenNameCount);
//        System.err.println(seenNameCount/(double)allNameCount);
//        System.err.println("Seen NE types (predicted):");
//        seenNETypeCounter.printAllSorted();
//        System.err.println("Unseen NE types (predicted):");
//        unseenNETypeCounter.printAllSorted();
//        List<String> sent = new StringAlgebra().parseString("_name_ runs");
//        List<String> lit = new StringAlgebra().parseString("Obama runs");
//        List<String> nnLabels = new StringAlgebra().parseString("_NAME_ run-09");
//        relabel.fixGraph(graph, sent, lit, nnLabels);
//        
//        System.err.println(graph.toIsiAmrStringWithSources());
//        relabel.getLabel("prefer");
//        relabel.getLabel("dogs");
    }

    private final IWordnet wordnet;

    private final Map<String, String> lit2name;
    private final Map<String, String> lit2wiki;
    private final Map<String, String> lit2type;
    private final Object2IntMap<String> word2count;
    private final Map<String, String> word2label;
    private final int nnThreshold;
    private final int lookupThreshold;

    public Relabel(String wordnetPath, String conceptnetPath, String mapsPath, int nnThreshold, int lookupThreshold)
            throws IOException, MalformedURLException, InterruptedException {

        if (conceptnetPath == null) {
            System.err.println("Reading full Wordnet.");
            wordnet = new WordnetEnumerator(wordnetPath);
        } else {
            System.err.println("Reading ConceptNet + Wordnet stemmer.");
            wordnet = new ConceptnetEnumerator(new File(conceptnetPath), wordnetPath);
        }

        lit2name = readMap(mapsPath + "nameLookup.txt");
        lit2wiki = readMap(mapsPath + "wikiLookup.txt");
        lit2type = readMap(mapsPath + "nameTypeLookup.txt");
        word2label = readMap(mapsPath + "words2labelsLookup.txt");
        word2count = readCounts(mapsPath + "words2labelsLookup.txt");
        this.nnThreshold = nnThreshold;
        this.lookupThreshold = lookupThreshold;
    }

    public void fixGraph(SGraph graph, List<String> sent, List<String> lit, List<String> nnLabels) {

        for (GraphNode node : new HashSet<>(graph.getGraph().vertexSet())) {
            if (node.getLabel().matches(LEXMARKER + "[0-9]+")) {
                int i = Integer.parseInt(node.getLabel().substring(LEXMARKER.length()));
                String nextWord = (i + 1 < sent.size()) ? sent.get(i + 1) : null;
                if (i >= sent.size() || i >= nnLabels.size()) {
                    System.err.println(sent);
                    System.err.println(nnLabels);
                    System.err.println(graph);
                }
                fixLabel(sent.get(i), lit.get(i), nextWord, nnLabels.get(i), graph, node);
            }
        }

    }

    private void fixLabel(String word, String lit, String nextWord,
            String nnLabel, SGraph graph, GraphNode lexNode) {

        if (word.equals(RareWordsAnnotator.NAME_TOKEN.toLowerCase())) {

            allNameCount++;

            lexNode.setLabel("name");
            String lookupName = lit2name.get(lit);
            String[] ops;
            boolean seen = false;
            if (lookupName != null && !lookupName.equals("")) {
                seenNameCount++;
                seen = true;
                ops = lookupName.split(":");
            } else {
                ops = lit.split("_");
            }
            int i = 1;
            for (String op : ops) {
                GraphNode opNode = graph.addNode(Util.gensym("explicitanon"), op);
                graph.addEdge(lexNode, opNode, "op" + i);
                i++;
            }

            //add wiki node
            GraphEdge nameEdge = null;
            for (GraphEdge edge : graph.getGraph().incomingEdgesOf(lexNode)) {
                if (edge.getLabel().equals("name")) {
                    nameEdge = edge;
                    break;
                }
            }
            if (nameEdge != null) {
                String wikiEntry = lit2wiki.containsKey(lit) ? lit2wiki.get(lit) : "-";
                GraphNode wikiNode = graph.addNode(Util.gensym("explicitanon"), wikiEntry);
                GraphNode neNode = GeneralBlobUtils.otherNode(lexNode, nameEdge);
                if (lit2type.containsKey(lit) && !neNode.getLabel().startsWith("LEX@")) {
                    neNode.setLabel(lit2type.get(lit));
                }
                graph.addEdge(neNode, wikiNode, "wiki");
            }

        } else if (word.equals(RareWordsAnnotator.DATE_TOKEN.toLowerCase())) {

            lexNode.setLabel("date-entity");

            Integer year = 0;
            Integer month = 0;
            Integer day = 0;
            Matcher m;
            if (lit.contains("-")) {
                //yyyy-MM-dd
                year = Integer.parseInt(lit.substring(0, 4));
                month = Integer.parseInt(lit.substring(5, 7));
                day = Integer.parseInt(lit.substring(8, 10));
            } else if (lit.contains("/")) {
                m = MMddYY.matcher(lit);
                boolean foundSomething = false;
                if (m.matches()) {
                    //MM/dd/yy
                    year = fixYear(Integer.parseInt(m.group(3)));
                    month = Integer.parseInt(m.group(1));
                    day = Integer.parseInt(m.group(2));
                    foundSomething = true;
                }
                m = MMdd.matcher(lit);
                if (m.matches()) {
                    //MM/dd
                    month = Integer.parseInt(m.group(1));
                    day = Integer.parseInt(m.group(2));
                    foundSomething = true;
                }
                if (!foundSomething) {
                    System.err.println("[Relabeler] Couldn't parse this date expression " + lit);
                    year = 0;
                    month = 0;
                    day = 0;
                }

            } else {
                //yyMMdd
                year = fixYear(Integer.parseInt(lit.substring(0, 2)));
                month = Integer.parseInt(lit.substring(2, 4));
                day = Integer.parseInt(lit.substring(4, 6));
            }

            if (year > 0) {
                GraphNode node = graph.addNode(Util.gensym("explicitanon"), String.valueOf(year));
                graph.addEdge(lexNode, node, "year");
            }
            if (month > 0) {
                GraphNode node = graph.addNode(Util.gensym("explicitanon"), String.valueOf(month));
                graph.addEdge(lexNode, node, "month");
            }
            if (day > 0) {
                GraphNode node = graph.addNode(Util.gensym("explicitanon"), String.valueOf(day));
                graph.addEdge(lexNode, node, "day");
            }

        } else if (word.equals(RareWordsAnnotator.NUMBER_TOKEN.toLowerCase())) {

            //recover large numbers
            if (nextWord == null) {
                lexNode.setLabel(lit);
            } else {
                if (nextWord.toLowerCase().startsWith("hundred")) {
                    lexNode.setLabel(shiftZero(lit, 2));
                } else if (nextWord.toLowerCase().startsWith("thousand")) {
                    lexNode.setLabel(shiftZero(lit, 3));
                } else if (nextWord.toLowerCase().startsWith("million")) {
                    lexNode.setLabel(shiftZero(lit, 6));
                } else if (nextWord.toLowerCase().startsWith("billion")) {
                    lexNode.setLabel(shiftZero(lit, 9));
                } else if (nextWord.toLowerCase().startsWith("trillion")) {
                    lexNode.setLabel(shiftZero(lit, 12));
                } else {
                    //else use number directly
                    lexNode.setLabel(lit.replaceAll("[,.]", ""));
                }
            }

        } else {
            // find verb stems

            int count = word2count.getInt(word);

            if (count >= nnThreshold) {
                lexNode.setLabel(nnLabel);
            } else if (count > lookupThreshold && word2label.containsKey(word)) {
                lexNode.setLabel(word2label.get(word));
            } else {
                boolean hasArgs = false;
                for (GraphEdge edge : graph.getGraph().outgoingEdgesOf(lexNode)) {
                    if (edge.getLabel().startsWith("ARG")) {
                        hasArgs = true;
                        break;
                    }
                }

                if (hasArgs) {
                    String verbStem = wordnet.findVerbStem(word);

                    if (verbStem != null) {
                        lexNode.setLabel(verbStem + "-01");
                    } else {
                        String relatedVerbStem = wordnet.findRelatedVerbStem(word);

                        if (relatedVerbStem != null) {
                            lexNode.setLabel(relatedVerbStem + "-01");
                        } else {
                            lexNode.setLabel(word + "-01");
                        }
                    }

                } else {
                    String nounStem = wordnet.findNounStem(word);
                    lexNode.setLabel(nounStem != null ? nounStem : word);
                }
            }

        }
    }

    private static Map<String, String> readMap(String filePath) throws IOException {
        Map<String, String> ret = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        while (br.ready()) {
            String[] parts = br.readLine().split("\t");
            if (parts.length > 1) {
                ret.put(parts[0], parts[1]);
            }
        }
        return ret;
    }

    private static Object2IntMap<String> readCounts(String filePath) throws IOException {
        Object2IntMap<String> ret = new Object2IntOpenHashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        while (br.ready()) {
            String[] parts = br.readLine().split("\t");
            if (parts.length > 2) {
                ret.put(parts[0], Integer.parseInt(parts[2]));
            }
        }
        return ret;
    }

    private static int fixYear(int twoDigitYear) {
        return (twoDigitYear < 20) ? twoDigitYear + 2000 : twoDigitYear + 1900;
    }

    private static String shiftZero(String number, int amount) {
        for (int i = 0; i < amount; i++) {
            int dotIndex = number.indexOf(".");
            if (dotIndex == -1) {
                number = number + "0";
            } else {
                number = number.replace(".", "");
                if (dotIndex < number.length() - 1) {
                    //re-insert dot one further
                    number = number.substring(0, dotIndex + 1) + "." + number.substring(dotIndex + 1);
                }
                //else keep it as is
            }
        }
        return number;
    }

}
