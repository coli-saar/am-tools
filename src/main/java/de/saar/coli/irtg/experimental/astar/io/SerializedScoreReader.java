package de.saar.coli.irtg.experimental.astar.io;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.saar.coli.amrtagging.Util;
import de.up.ling.tree.ParseException;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SerializedScoreReader implements ScoreReader {
    private ZipFile probsZipFile;

    public SerializedScoreReader(File probsZipFilename) throws IOException {
        probsZipFile = new ZipFile(probsZipFilename);
    }

    @Override
    public List<List<List<AnnotatedSupertag>>> getSupertagScores() throws IOException {
        return readList("tagProbs.ser");
    }

    @Override
    public List<List<List<Pair<String, Double>>>> getEdgeScores() throws IOException {
        return readList("opProbs.ser");
    }

    private List readList(String entryName) throws IOException {
        ZipEntry supertagsZipEntry = probsZipFile.getEntry(entryName);
        FSTObjectInput in = new FSTObjectInput(probsZipFile.getInputStream(supertagsZipEntry));

        try {
            List result = (List) in.readObject();
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

    /**
     * Convert a regular scores.zip file to a serialized-scores.zip file.
     *
     * @param args
     */
    public static void main(String[] args) throws IOException, ParseException {
        String scoresZipName = args[0];
        String serializedScoresZipName = args[1];

        System.err.println("Reading original ...");
        TextScoreReader tsr = new TextScoreReader(new File(scoresZipName));

        FileOutputStream fos = new FileOutputStream(serializedScoresZipName);
        ZipOutputStream zos = new ZipOutputStream(fos);

        serialize(zos, "tagProbs.ser", tsr.getSupertagScores());
        serialize(zos, "opProbs.ser", tsr.getEdgeScores());

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
