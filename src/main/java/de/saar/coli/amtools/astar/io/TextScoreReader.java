package de.saar.coli.amtools.astar.io;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.saar.coli.amrtagging.Util;
import de.saar.coli.amtools.astar.*;
import de.up.ling.irtg.algebra.Algebra;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.ArrayMap;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static de.saar.coli.amtools.astar.Astar.FAKE_NEG_INFINITY;

public class TextScoreReader implements ScoreReader {
    private ZipFile probsZipFile;
    private List<SupertagProbabilities> tagp = new ArrayList<>();  // one per sentence
    private Interner<SupertagWithType> supertagLexicon = new Interner<>();
    private Int2ObjectMap<SupertagWithType> idToSupertag = new ArrayMap<>();
    private Set<ApplyModifyGraphAlgebra.Type> types = new HashSet<>();
    private Interner<String> edgeLabelLexicon = new Interner<>();
    private List<EdgeProbabilities> edgep = new ArrayList<>();

    public TextScoreReader(File probsZipFilename, String ROOT_EDGELABEL, String IGNORE_EDGELABEL) throws IOException, ParseException, ParserException {
        probsZipFile = new ZipFile(probsZipFilename);

        System.err.println("Reading supertags ...");
        CpuTimeStopwatch watch = new CpuTimeStopwatch();
        watch.record();
        processSupertagProbabilities();

        System.err.println("Reading edge label scores ...");
        watch.record();
        processEdgeProbabilities(ROOT_EDGELABEL, IGNORE_EDGELABEL);

        watch.record();
        watch.printMillisecondsX("loading done", "supertags", "edges");
    }

    private void processEdgeProbabilities(String ROOT_EDGELABEL, String IGNORE_EDGELABEL) throws IOException {
        List<List<List<Pair<String, Double>>>> edges = getEdgeScores();


        for (List<List<Pair<String, Double>>> sentence : edges) {
            for (List<Pair<String, Double>> b : sentence) {
                for (Pair<String, Double> edge : b) {
                    Edge e = Edge.parse(edge.left);
                    edgeLabelLexicon.addObject(e.getLabel());
                }
            }
        }

        // build edge array
        for (List<List<Pair<String, Double>>> sentence : edges) {
            for (List<Pair<String, Double>> b : sentence) {
                EdgeProbabilities edgepHere = new EdgeProbabilities(FAKE_NEG_INFINITY, edgeLabelLexicon.resolveObject(IGNORE_EDGELABEL), edgeLabelLexicon.resolveObject(ROOT_EDGELABEL));

                for (Pair<String, Double> edge : b) {
                    Edge e = Edge.parse(edge.left);
                    int edgeLabelId = edgeLabelLexicon.resolveObject(e.getLabel());

                    try {
                        edgepHere.set(e.getFrom(), e.getTo(), edgeLabelId, edge.right);
                    } catch (ArrayIndexOutOfBoundsException ee) {
                        throw ee;
                    }
                }

                edgep.add(edgepHere);
            }
        }
    }

    private void processSupertagProbabilities() throws ParseException, IOException, ParserException {
        int nullSupertagId = -1;
        List<List<List<AnnotatedSupertag>>> supertags = getSupertagScores();
        Algebra<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> alg = new ApplyModifyGraphAlgebra();

        int sentenceId = 0;
        int tokenId = 0;

        // calculate supertag lexicon
        for (List<List<AnnotatedSupertag>> sentence : supertags) {
            sentenceId++;
            tokenId = 0;

            for (List<AnnotatedSupertag> token : sentence) {
                tokenId++;

                for (AnnotatedSupertag st : token) {
                    try {
                        assert st.type != null || "NULL".equals(st.graph) : String.format("Null type for supertag %s", st.graph);

                        String supertag = st.graph;
                        SupertagWithType stt = SupertagWithType.fromAnnotatedSupertag(st, alg);

                        if (!supertagLexicon.isKnownObject(stt)) {
                            int id = supertagLexicon.addObject(stt);

                            idToSupertag.put(id, stt);
                            types.add(stt.getType());

                            if ("NULL".equals(supertag)) {
                                nullSupertagId = id;
                            }
                        } else {
                            assert types.contains(stt.getType()) : "unk type: " + stt.getType();
                        }
                    } catch(IllegalArgumentException e) { // https://github.com/coli-saar/am-tools/issues/9
                        System.err.printf("Skipping supertag %s with invalid type '%s'.\n", st.graph, st.type);
                    }
                }
            }
        }

        if (nullSupertagId < 0) {
            System.err.println("Did not find an entry for the NULL supertag - exiting.");
            System.exit(1);
        }

        // build supertag array
        for (List<List<AnnotatedSupertag>> sentence : supertags) {
            SupertagProbabilities tagpHere = new SupertagProbabilities(FAKE_NEG_INFINITY, nullSupertagId);

            for (int tokenPos = 0; tokenPos < sentence.size(); tokenPos++) {
                List<AnnotatedSupertag> token = sentence.get(tokenPos);

                // for (int stPos = 0; stPos < token.size(); stPos++) {
                for (int stPos = 0; stPos < 6; stPos++) {
                    try {
                        AnnotatedSupertag st = token.get(stPos);
                        SupertagWithType stt = SupertagWithType.fromAnnotatedSupertag(st, alg);
                        int supertagId = supertagLexicon.resolveObject(stt);
                        tagpHere.put(tokenPos + 1, supertagId, st.probability);
                    } catch(IllegalArgumentException e) { // https://github.com/coli-saar/am-tools/issues/9
                        // Just skip it silently; warning was already printed above
                    }
                }
            }

            tagp.add(tagpHere);
        }
    }

    private List<List<List<AnnotatedSupertag>>> getSupertagScores() throws IOException {
        ZipEntry supertagsZipEntry = probsZipFile.getEntry("tagProbs.txt");
        Reader supertagsReader = new InputStreamReader(probsZipFile.getInputStream(supertagsZipEntry));
        return Util.readSupertagProbs(supertagsReader, false);
    }

    private List<List<List<Pair<String, Double>>>> getEdgeScores() throws IOException {
        ZipEntry edgeZipEntry = probsZipFile.getEntry("opProbs.txt");
        Reader edgeReader = new InputStreamReader(probsZipFile.getInputStream(edgeZipEntry));
        return Util.readEdgeProbs(edgeReader, false, true, Double.NEGATIVE_INFINITY, 7, false);  // TODO make these configurable  // was: 0.1, 5
    }

    @Override
    public List<AmConllSentence> getInputCorpus() throws IOException, ParseException {
        ZipEntry inputEntry = probsZipFile.getEntry("corpus.amconll");
        return AmConllSentence.read(new InputStreamReader(probsZipFile.getInputStream(inputEntry)));
    }

    @Override
    public List<SupertagProbabilities> getSupertagProbabilities()  {
        return tagp;
    }

    @Override
    public Set<ApplyModifyGraphAlgebra.Type> getAllTypes() {
        return types;
    }

    @Override
    public Interner<SupertagWithType> getSupertagLexicon() {
        return supertagLexicon;
    }

    @Override
    public Int2ObjectMap<SupertagWithType> getIdToSupertag() {
        return idToSupertag;
    }

    @Override
    public Interner<String> getEdgeLabelLexicon() {
        return edgeLabelLexicon;
    }

    @Override
    public List<EdgeProbabilities> getEdgeProbabilities() {
        return edgep;
    }
}
