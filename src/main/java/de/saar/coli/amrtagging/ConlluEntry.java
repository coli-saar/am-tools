/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents all information about a single token in a conllu dependency tree.
 * See https://universaldependencies.org/format.html
 * @author matthias
 */
public class ConlluEntry {
    
    public static final String DEFAULT_NULL = "_";
    
    
    private int id;
    private String form;
    private String lemma=DEFAULT_NULL;
    private String upos=DEFAULT_NULL;
    private String pos=DEFAULT_NULL;
    private String feats = DEFAULT_NULL;
    private int head;
    private String edgeLabel = DEFAULT_NULL;
    private String deps = DEFAULT_NULL;
    private String misc = DEFAULT_NULL;
    private Map<String,String> miscMap;
    
    private TokenRange tokenRange;
    
    public static final int NOID = -2;
    
    
    public ConlluEntry(int id, String form){
        this.id = id;
        this.form = form;
        miscMap = new HashMap<>();
    }
    
    public ConlluEntry(int id, String form, String lemma, String upos,
            String feats, int head, String edgeLabel, String deps,
            String misc, Map<String,Object> miscMap){
        this.id = id;
        this.form = form;
        setMisc(misc);
    }

    ConlluEntry() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public String getLemma() {
        return lemma;
    }

    public void setLemma(String lemma) {
        this.lemma = lemma;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }
    
    public String getUPos() {
        return upos;
    }

    public void setUPos(String pos) {
        this.upos = pos;
    }


    public int getHead() {
        return head;
    }

    public void setHead(int head) {
        this.head = head;
    }
    

    /**
     * @return the edgeLabel
     */
    public String getEdgeLabel() {
        return edgeLabel;
    }

    /**
     * @param edgeLabel the edgeLabel to set
     */
    public void setEdgeLabel(String edgeLabel) {
        this.edgeLabel = edgeLabel;
    }

    
    public String toString(){
        StringBuilder b = new StringBuilder();
        b.append(this.getId());
        b.append("\t");
        b.append(this.getForm());
        b.append("\t");
        b.append(this.getLemma());
        b.append("\t");
        b.append(this.getUPos());
        b.append("\t");
        b.append(this.getPos());
        b.append("\t");
        b.append(this.getFeats());
        b.append("\t");
        b.append(this.getHead());
        b.append("\t");
        b.append(this.getEdgeLabel());
        b.append("\t");
        b.append(this.getDeps());
        b.append("\t");
        b.append(this.getMisc());
        return b.toString();
        
    }

    private String getFeats() {
        return this.feats;
    }
    public void setFeats(String feats){
        this.feats = feats;
    }

    /**
     * @return the deps
     */
    public String getDeps() {
        return deps;
    }

    /**
     * @param deps the deps to set
     */
    public void setDeps(String deps) {
        this.deps = deps;
    }

    /**
     * @return the misc
     */
    public String getMisc() {
        return misc;
    }
    
    public Map<String,String> getMiscMap(){
        return miscMap;
    }
    
    public TokenRange getTokenRange(){
        return tokenRange;
    }
    
    public void setTokenRange(TokenRange tr){
        tokenRange = tr;
    }

    /**
     * @param misc the misc to set
     */
    public void setMisc(String misc) {
        this.misc = misc;
        for (String keyValPair : misc.split("\\|")){
            String[] keyVal = keyValPair.split("=");
            if (keyVal.length != 2){
                throw new IllegalArgumentException(misc+" is not an appropriately formated misc value");
            }
            if (keyVal[0].equals("TokenRange")){
                tokenRange = TokenRange.fromString(keyVal[1]);
            } 
            //enter value as string
            this.miscMap.put(keyVal[0],keyVal[1]);
        }
    }
    

}
