/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging.mrp.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 *
 * @author matthias
 */
public class ConlluSentence implements Iterable<ConlluEntry> {

    private int lineNr;
    private String id = null;
    private List<ConlluEntry> entries = new ArrayList<>();

    public ConlluEntry get(int i) {
        return entries.get(i);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public ConlluEntry set(int i, ConlluEntry e) {
        return entries.set(i, e);
    }

    public boolean add(ConlluEntry e) {
        return entries.add(e);
    }

    public boolean addAll(Collection<? extends ConlluEntry> clctn) {
        return entries.addAll(clctn);
    }

    public List<ConlluEntry> subList(int i, int i1) {
        return entries.subList(i, i1);
    }
    
    public ConlluSentence(){
        
    }
    
    public ConlluSentence(List<ConlluEntry> entries){
        this.entries = new ArrayList<>(entries);
    }

    public int getLineNr() {
        return lineNr;
    }

    public void setLineNr(int n) {
        lineNr = n;
    }

    public List<String> words() {
        ArrayList<String> r = new ArrayList<>();
        for (ConlluEntry e : entries) {
            r.add(e.getForm());
        }
        return r;
    }

    public List<String> lemmas() {
        ArrayList<String> r = new ArrayList<>();
        for (ConlluEntry e : entries) {
            r.add(e.getLemma());
        }
        return r;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (id != null){
            b.append(id);
            b.append("\n");
        }
        for (ConlluEntry aThi : entries) {
            b.append(aThi);
            b.append("\n");
        }
        return b.toString();
    }


    public void setPos(List<String> pos) {
        if (pos.size() != entries.size()) {
            throw new IllegalArgumentException("Size of pos list must be equal to sentence length");
        }
        for (int i = 0; i < pos.size(); i++) {
            entries.get(i).setPos(pos.get(i));
        }
    }
    
    public void setUPos(List<String> pos) {
        if (pos.size() != entries.size()) {
            throw new IllegalArgumentException("Size of pos list must be equal to sentence length");
        }
        for (int i = 0; i < pos.size(); i++) {
            entries.get(i).setUPos(pos.get(i));
        }
    }

    public void setLemmas(List<String> lemmas) {
        if (lemmas.size() != entries.size()) {
            throw new IllegalArgumentException("Size of lemma list must be equal to sentence length");
        }
        for (int i = 0; i < lemmas.size(); i++) {
            entries.get(i).setLemma(lemmas.get(i));
        }
    }
    
    
    /**
     * Returns the index (0-based) of the head of a given span.
     * If the span coveres multiple subtrees, it gives the head of the left most subtree.
     * @param from
     * @param to
     * @return an int head, such that from <= head <= to
     */
    public int headOfSpan(int from, int to){
        int guess = to;
        for (int position = from; position <= to; position++){
            guess = Integer.min(guess, mostDistantParent(position, from, to));
        }
        return guess; 
    }
    
    /**
     * Finds the most distant parent (0-based) of position index (different from aritifical root)
     * that is still in the span.
     * @param index
     * @param from
     * @param to
     * @return 
     */
    private int mostDistantParent(int index, int from, int to){
        int head = entries.get(index).getHead();
        if (head == 0){ //reached root
            return index;
        } else{
           head--; //convert to 0-based indexing
           if (head < from || head > to){ //we would leave the interval
                return index;
           } else {
                return mostDistantParent(head, from, to);
           }
        }
    }

    /**
     * Writes a list of ConllSentences to a file.
     * 
     * @see #write(java.io.Writer, java.util.List) 
     *
     * @param filename
     * @param sents
     * @throws IOException
     */
    public static void writeToFile(String filename, List<ConlluSentence> sents) throws IOException {
        write(new FileWriter(filename), sents);
    }
    
    /**
     * Writes a list of ConllSentences to a writer.<p>
     * 
     * TODO: might want to set the
     * line of the objects to where it was written to file.
     *
     * @param writer
     * @param sents
     * @throws IOException
     */
    public static void write(Writer writer, List<ConlluSentence> sents) throws IOException {
        BufferedWriter bw = new BufferedWriter(writer);
        
        for (ConlluSentence s : sents) {
            bw.write(s.toString());
            bw.write("\n");
        }
        bw.close();
    }
    
    /**
     * Reads a CoNLL corpus from a Reader and returns the list of instances.
     * 
     * @param reader
     * @return
     * @throws IOException
     */
    public static List<ConlluSentence> read(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        String l = "";
        ArrayList<ConlluSentence> sents = new ArrayList();
        ConlluSentence sent = new ConlluSentence();
        int lineNr = 1;
        sent.setLineNr(lineNr);
        
        while ((l = br.readLine()) != null) {
            if (l.startsWith("#")) {
                    sent.id = l.substring(1);
            } else if (l.replaceAll("\t", "").length() > 0) {
                String[] attr = l.split("\t");
                ConlluEntry c = new ConlluEntry(Integer.parseInt(attr[0]), attr[1]);
                c.setLemma(attr[2]);
                c.setUPos(attr[3]);
                c.setPos(attr[4]);
                c.setFeats(attr[5]);
                c.setHead(Integer.parseInt(attr[6]));
                c.setEdgeLabel(attr[7]);
                c.setDeps(attr[8]);
                c.setMisc(attr[9]);

                //System.out.println(c);
                sent.add(c);
            } else {
                sents.add(sent);
                sent = new ConlluSentence();
                sent.setLineNr(lineNr);
            }
            lineNr++;
        }
        
        if (!sent.isEmpty()) {
            sents.add(sent);
        }
        br.close();
        return sents;
    }

    /**
     * Reads a CoNLLu corpus from a file and returns the list of instances.
     *
     * @param filename
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static List<ConlluSentence> readFromFile(String filename) throws FileNotFoundException, IOException {
        return read(new FileReader(filename));
    }
    

    public String getId() {
        return id;
    }

    @Override
    public Iterator<ConlluEntry> iterator() {
        return entries.iterator();
    }
    
    
     /**
     * Returns the token index (0-based) that (sort of) fits the given TokenRange.
     * @param anchor
     * @param sent
     * @return 
     */
    public int getCorrespondingIndex(TokenRange anchor){
        int from = anchor.getFrom();
        int index = 0;
        for (ConlluEntry entry : this){
            TokenRange range = entry.getTokenRange();
            if (range.getFrom() >= from) return index;
            index++;
        }
        return index;
    }

    


}
