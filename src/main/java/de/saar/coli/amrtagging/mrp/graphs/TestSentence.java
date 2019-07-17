/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.graphs;

import com.owlike.genson.Genson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Representation for a sentence from the test data.
 * @author matthias
 */
public class TestSentence {
    
    //Example:
    // {"id": "102990", "version": 1.0, "time": "2019-06-23", "source": "lpps", "targets": ["dm", "psd", "eds", "ucca", "amr"], "input": "I was very much worried, for it was becoming clear to me that the breakdown of my plane was extremely serious."}
    
    public String id;
    public String version;
    public String time;
    public String source;
    public List<String> targets;
    public String input;
    
    /**
     * reads TestSentences and returns a map, mapping ids to TestSentences.
     * @param r
     * @return
     * @throws IOException 
     */
    public static Map<String,TestSentence> read(Reader r) throws IOException{
        Genson genson = new Genson();
        BufferedReader reader = new BufferedReader(r);
        Map<String,TestSentence> sents = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null){
            TestSentence sent = genson.deserialize(line,TestSentence.class);
            sents.put(sent.id,sent);
        }
        return sents;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.id);
        hash = 53 * hash + Objects.hashCode(this.version);
        hash = 53 * hash + Objects.hashCode(this.time);
        hash = 53 * hash + Objects.hashCode(this.source);
        hash = 53 * hash + Objects.hashCode(this.targets);
        hash = 53 * hash + Objects.hashCode(this.input);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TestSentence other = (TestSentence) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        if (!Objects.equals(this.time, other.time)) {
            return false;
        }
        if (!Objects.equals(this.source, other.source)) {
            return false;
        }
        if (!Objects.equals(this.input, other.input)) {
            return false;
        }
        if (!Objects.equals(this.targets, other.targets)) {
            return false;
        }
        return true;
    }
    
    
    
}
