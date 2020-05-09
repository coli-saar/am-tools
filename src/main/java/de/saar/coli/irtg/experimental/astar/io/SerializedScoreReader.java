package de.saar.coli.irtg.experimental.astar.io;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.saar.coli.amrtagging.Util;
import de.saar.coli.irtg.experimental.astar.Astar;
import de.saar.coli.irtg.experimental.astar.EdgeProbabilities;
import de.saar.coli.irtg.experimental.astar.SupertagProbabilities;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.codec.SGraphInputCodec;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.CpuTimeStopwatch;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SerializedScoreReader implements ScoreReader {
    private ZipFile probsZipFile;

    public SerializedScoreReader(File probsZipFilename) throws IOException {
        probsZipFile = new ZipFile(probsZipFilename);
        readAll();
    }

    /*
    @Override
    public List<List<List<AnnotatedSupertag>>> getSupertagScores() throws IOException {
        return readList("tagProbs.ser");
    }

    @Override
    public List<List<List<Pair<String, Double>>>> getEdgeScores() throws IOException {
        return readList("opProbs.ser");
    }

     */

    private List readList(String entryName) throws IOException {
        return readFromZip(entryName, List.class);

//        ZipEntry supertagsZipEntry = probsZipFile.getEntry(entryName);
//        FSTObjectInput in = new FSTObjectInput(probsZipFile.getInputStream(supertagsZipEntry));
//
//        try {
//            List result = (List) in.readObject();
//            return result;
//        } catch (ClassNotFoundException e) {
//            throw new IOException(e);
//        } finally {
//            in.close(); // required !
//        }
    }

    private <E> E readFromZip(String entryName, Class<E> clazz) throws IOException {
        ZipEntry supertagsZipEntry = probsZipFile.getEntry(entryName);
        FSTObjectInput in = new FSTObjectInput(probsZipFile.getInputStream(supertagsZipEntry));

        try {
            E result = (E) in.readObject();
            return result;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        } finally {
            in.close(); // required !
        }
    }

    @Override
    public List<AmConllSentence> getInputCorpus() throws IOException, ParseException {
        ZipEntry inputEntry = probsZipFile.getEntry("corpus.amconll");
        return AmConllSentence.read(new InputStreamReader(probsZipFile.getInputStream(inputEntry)));
    }
    
    private List<SupertagProbabilities> tagp;
    private List<EdgeProbabilities> edgep;
    private Set<ApplyModifyGraphAlgebra.Type> types;
    private Interner<String> supertagLexicon;
    private Interner<String> edgeLabelLexicon;
    private Int2ObjectMap<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> idToSupertag;

    private void readAll() throws IOException {
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        w.record();
        tagp = readList("tagProbs.ser");
        edgep = readList("edgeProbs.ser");
        types = readFromZip("types.ser", Set.class);
        supertagLexicon = readFromZip("supertagLex.ser", Interner.class);
        edgeLabelLexicon = readFromZip("edgeLabelLex.ser", Interner.class);

        w.record();

        Int2ObjectMap<Pair<String, ApplyModifyGraphAlgebra.Type>> idToSuperTagStr = readFromZip("idToSupertagStr.ser", Int2ObjectMap.class);
        idToSupertag = new Int2ObjectOpenHashMap<>();
        SGraphInputCodec c = new SGraphInputCodec();

        for( Int2ObjectMap.Entry<Pair<String, ApplyModifyGraphAlgebra.Type>> entry : idToSuperTagStr.int2ObjectEntrySet() ) {
            SGraph g = c.read(entry.getValue().left);
            idToSupertag.put(entry.getIntKey(), new Pair(g, entry.getValue().right));
        }

        w.record();
        w.printMillisecondsX("done loading", "deserialize", "graphs");
    }

    @Override
    public List<SupertagProbabilities> getSupertagProbabilities() throws IOException {
        return tagp;
    }

    @Override
    public List<EdgeProbabilities> getEdgeProbabilities() throws IOException {
        return edgep;
    }

    @Override
    public Set<ApplyModifyGraphAlgebra.Type> getAllTypes() throws IOException {
        return types;
    }

    @Override
    public Interner<String> getSupertagLexicon() throws IOException {
        return supertagLexicon;
    }

    @Override
    public Int2ObjectMap<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> getIdToSupertag() throws IOException {
        return idToSupertag;
    }

    @Override
    public Interner<String> getEdgeLabelLexicon() throws IOException {
        return edgeLabelLexicon;
    }

    public static Int2ObjectMap<Pair<String, ApplyModifyGraphAlgebra.Type>> makeStringMap(Int2ObjectMap<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> idToSupertag) {
        Int2ObjectMap<Pair<String, ApplyModifyGraphAlgebra.Type>> idToSuperTagStr = new Int2ObjectOpenHashMap<>();

        for( Int2ObjectMap.Entry<Pair<SGraph, ApplyModifyGraphAlgebra.Type>> entry : idToSupertag.int2ObjectEntrySet() ) {
            idToSuperTagStr.put(entry.getIntKey(), new Pair(entry.getValue().left.toString(), entry.getValue().right));
        }

        return idToSuperTagStr;
    }



    /**
     * Convert a regular scores.zip file to a serialized-scores.zip file.
     *
     * @param args
     */
    public static void main(String[] args) throws IOException, ParseException, ParserException {
        String scoresZipName = args[0];
        String serializedScoresZipName = args[1];

        System.err.println("Reading original ...");
        TextScoreReader tsr = new TextScoreReader(new File(scoresZipName), Astar.ROOT_EDGELABEL, Astar.IGNORE_EDGELABEL);

        FileOutputStream fos = new FileOutputStream(serializedScoresZipName);
        ZipOutputStream zos = new ZipOutputStream(fos);

        serialize(zos, "tagProbs.ser", tsr.getSupertagProbabilities());
        serialize(zos, "edgeProbs.ser", tsr.getEdgeProbabilities());
        serialize(zos, "types.ser", tsr.getAllTypes());
        serialize(zos, "supertagLex.ser", tsr.getSupertagLexicon());
        serialize(zos, "edgeLabelLex.ser", tsr.getEdgeLabelLexicon());
        serialize(zos, "idToSupertagStr.ser", makeStringMap(tsr.getIdToSupertag())); // SGraph not serializable -> convert graphs to strings

        System.err.println("Writing corpus ...");
        ZipEntry ze = new ZipEntry("corpus.amconll");
        zos.putNextEntry(ze);
        Writer w = new OutputStreamWriter(zos);
        AmConllSentence.write(w, tsr.getInputCorpus());

        System.err.println("Done.");
        zos.close();
    }

    private static void serialize(ZipOutputStream zos, String filename, Object o) throws IOException {
        System.err.printf("Writing %s ...\n", filename);
        ZipEntry ze = new ZipEntry(filename);
        zos.putNextEntry(ze);

        FSTObjectOutput out = new FSTObjectOutput(zos);
        out.writeObject(o);
        out.flush(); // required !
    }
}
