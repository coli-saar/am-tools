package de.saar.coli.irtg.experimental.astar.io;

import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.irtg.experimental.astar.EdgeProbabilities;
import de.saar.coli.irtg.experimental.astar.SupertagProbabilities;
import de.saar.coli.irtg.experimental.astar.SupertagWithType;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.tree.ParseException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface ScoreReader {
//    public List<List<List<AnnotatedSupertag>>> getSupertagScores() throws IOException;
//    public List<List<List<Pair<String, Double>>>> getEdgeScores() throws IOException;
    public List<AmConllSentence> getInputCorpus() throws IOException, ParseException;


    public List<SupertagProbabilities> getSupertagProbabilities() throws IOException;
    public Set<Type> getAllTypes() throws IOException;
    public Interner<SupertagWithType> getSupertagLexicon() throws IOException;
    public Int2ObjectMap<SupertagWithType> getIdToSupertag() throws IOException;

    public Interner<String> getEdgeLabelLexicon() throws IOException;
    public List<EdgeProbabilities> getEdgeProbabilities() throws IOException;


}
