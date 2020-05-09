package de.saar.coli.irtg.experimental.astar.io;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.saar.coli.amrtagging.Util;
import de.up.ling.tree.ParseException;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TextScoreReader implements ScoreReader {
    private ZipFile probsZipFile;

    public TextScoreReader(File probsZipFilename) throws IOException {
        probsZipFile = new ZipFile(probsZipFilename);
    }

    @Override
    public List<List<List<AnnotatedSupertag>>> getSupertagScores() throws IOException {
        ZipEntry supertagsZipEntry = probsZipFile.getEntry("tagProbs.txt");
        Reader supertagsReader = new InputStreamReader(probsZipFile.getInputStream(supertagsZipEntry));
        return Util.readSupertagProbs(supertagsReader, true);
    }

    @Override
    public List<List<List<Pair<String, Double>>>> getEdgeScores() throws IOException {
        ZipEntry edgeZipEntry = probsZipFile.getEntry("opProbs.txt");
        Reader edgeReader = new InputStreamReader(probsZipFile.getInputStream(edgeZipEntry));
        return Util.readEdgeProbs(edgeReader, true, 0.0, 7, false);  // TODO make these configurable  // was: 0.1, 5
    }

    @Override
    public List<AmConllSentence> getInputCorpus() throws IOException, ParseException {
        ZipEntry inputEntry = probsZipFile.getEntry("corpus.amconll");
        return AmConllSentence.read(new InputStreamReader(probsZipFile.getInputStream(inputEntry)));
    }
}
