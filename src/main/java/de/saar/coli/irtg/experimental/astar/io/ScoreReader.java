package de.saar.coli.irtg.experimental.astar.io;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.AnnotatedSupertag;
import de.up.ling.tree.ParseException;

import java.io.IOException;
import java.util.List;

public interface ScoreReader {
    public List<List<List<AnnotatedSupertag>>> getSupertagScores() throws IOException;
    public List<List<List<Pair<String, Double>>>> getEdgeScores() throws IOException;
    public List<AmConllSentence> getInputCorpus() throws IOException, ParseException;
}
