/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.util.MutableInteger;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 *
 * @author matthias
 */
public class ConllSentence extends ArrayList<ConllEntry> {

    private int lineNr;
    private HashMap<String, String> attributes = new HashMap<>();

    public void setAttr(String key, String val) {
        if (key.contains("\n")) {
            throw new IllegalArgumentException("Linebreak forbidden in attribute key");
        }
        if (val.contains("\n")) {
            throw new IllegalArgumentException("Linebreak forbidden in attribute value");
        }
        attributes.put(key, val);
    }

    public String getAttr(String key) throws IndexOutOfBoundsException{
        if (! attributes.containsKey(key)){
            throw new IndexOutOfBoundsException("ConllSentence with id "+this.getId()+" in line "+lineNr+" doesn't have attribute \""+key+"\"");
        }
        return attributes.get(key);
    }

    public int getLineNr() {
        return lineNr;
    }

    public void setLineNr(int n) {
        lineNr = n;
    }

    public ArrayList<String> words() {
        ArrayList<String> r = new ArrayList<>();
        for (ConllEntry e : this) {
            r.add(e.getForm());
        }
        return r;
    }

    public ArrayList<String> lemmas() {
        ArrayList<String> r = new ArrayList<>();
        for (ConllEntry e : this) {
            r.add(e.getLemma());
        }
        return r;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (ConllEntry aThi : this) {
            b.append(aThi);
            b.append("\n");
        }
        return b.toString();
    }

    /**
     * Turns the output of the AlignmentTrackingAutomaton into an ConllSentence
     * with just the words for now. The ConllSentence will be delexicalized
     * using the alignment info from the instance. To make the supertags
     * consistent, it looks the graph fragment up in the given
     * SupertagDictionary.
     *
     * @param indexedAM
     * @param instance
     * @param lookup
     * @return
     * @throws de.up.ling.tree.ParseException
     * @throws de.up.ling.irtg.algebra.ParserException
     */
    public static ConllSentence fromIndexedAMTerm(Tree<String> indexedAM, MRInstance instance, SupertagDictionary lookup) throws ParseException, ParserException {
        Map<Integer, Set<String>> index2lexNodes = new HashMap(); //maps an index to a set of lexical nodes for delexicalization.
        for (Alignment al : instance.getAlignments()) {
            index2lexNodes.put(al.span.start, al.lexNodes);
        }

        ConllSentence conllSent = new ConllSentence();
        int index = 1; //one-based indexing
        for (String word : instance.getSentence()) {
            ConllEntry ent = new ConllEntry(index, word);
            ent.setAligned(true); //for now, change that in the future - ml
            ent.setHead(0);
            ent.setEdgeLabel(ConllEntry.IGNORE);
            conllSent.add(ent);

            index++;
        }

        for (Tree<String> t : indexedAM.getAllNodes()) {

            if (t.getChildren().isEmpty()) {
                String[] graphAndType = t.getLabel().split(ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP);
                String[] positionAndFragment = graphAndType[0].split(AlignmentTrackingAutomaton.SEPARATOR);
                int position = Integer.parseInt(positionAndFragment[0]);
                conllSent.get(position).setAligned(true);
                conllSent.get(position).setSupertag(positionAndFragment[1], index2lexNodes.get(position), lookup); //set supertag and delexcalize it.
                conllSent.get(position).setType(new ApplyModifyGraphAlgebra.Type(graphAndType[1]));

                if (t.equals(indexedAM)) { //we are at the root (no term, just single constant)
                    conllSent.get(position).setEdgeLabel(ConllEntry.ROOT_SYM);
                }

            } else if (t.getChildren().size() == 2) {

                String[] fromToAndOp = t.getLabel().split(AlignmentTrackingAutomaton.SEPARATOR);
                String[] fromTo = fromToAndOp[0].split(AlignmentTrackingAutomaton.FROM_TO_SEPARATOR);
                int from = Integer.parseInt(fromTo[0]);
                int to = Integer.parseInt(fromTo[1]);
                conllSent.get(to).setHead(from + 1); //within conll we use 1 addressing
                conllSent.get(to).setEdgeLabel(fromToAndOp[1]);

                if (t.equals(indexedAM)) {
                    conllSent.get(from).setEdgeLabel(ConllEntry.ROOT_SYM);
                }
            }
        }
        return conllSent;
    }

    /*
    public Tree<String> toAMTerm() {
        throw new UnsupportedOperationException();
    }
    */

    public void addPos(List<String> pos) {
        if (pos.size() != this.size()) {
            throw new IllegalArgumentException("Size of pos list must be equal to sentence length");
        }
        for (int i = 0; i < pos.size(); i++) {
            this.get(i).setPos(pos.get(i));
        }
    }

    public void addLemmas(List<String> lemmas) {
        if (lemmas.size() != this.size()) {
            throw new IllegalArgumentException("Size of lemma list must be equal to sentence length");
        }
        for (int i = 0; i < lemmas.size(); i++) {
            this.get(i).setLemma(lemmas.get(i));
        }
    }

    public void addReplacement(List<String> replacements) {
        if (replacements.size() != this.size()) {
            throw new IllegalArgumentException("Size of replacement list must be equal to sentence length");
        }
        for (int i = 0; i < replacements.size(); i++) {
            if (!this.get(i).getForm().equals(replacements.get(i))) {
                this.get(i).setReplacement(replacements.get(i));
            }
        }
    }

    public void addNEs(List<String> nes) {
        if (nes.size() != this.size()) {
            throw new IllegalArgumentException("Size of NE tag list must be equal to sentence length");
        }
        for (int i = 0; i < nes.size(); i++) {
            this.get(i).setNe(nes.get(i));
        }
    }
    
    /**
     * Sets token ranges.
     * @param ranges 
     */
    public void addRanges(List<TokenRange> ranges) {
        if (ranges.size() != this.size()) {
            throw new IllegalArgumentException("Size of TokenRange list must be equal to sentence length");
        }
        for (int i = 0; i < ranges.size(); i++) {
            this.get(i).setRange(ranges.get(i));
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
    public static void writeToFile(String filename, List<ConllSentence> sents) throws IOException {
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
    public static void write(Writer writer, List<ConllSentence> sents) throws IOException {
        BufferedWriter bw = new BufferedWriter(writer);
        
        for (ConllSentence s : sents) {
            for (String key : s.attributes.keySet()) {
                bw.write("#");
                bw.write(key);
                bw.write(":");
                bw.write(s.getAttr(key));
                bw.write("\n");
            }
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
     * @throws ParseException 
     */
    public static List<ConllSentence> read(Reader reader) throws IOException, ParseException {
        BufferedReader br = new BufferedReader(reader);
        String l = "";
        ArrayList<ConllSentence> sents = new ArrayList();
        ConllSentence sent = new ConllSentence();
        int lineNr = 1;
        sent.setLineNr(lineNr);
        while ((l = br.readLine()) != null) {
            if (l.startsWith("#")) {
                if (l.contains(":")) {
                    int index = l.indexOf(":");
                    sent.setAttr(l.substring(1, index), l.substring(index + 1));
                }
            } else if (l.replaceAll("\t", "").length() > 0) {
                String[] attr = l.split("\t");
                ConllEntry c = new ConllEntry(Integer.parseInt(attr[0]), attr[1]);
                c.setReplacement(attr[2]);
                c.setLemma(attr[3]);
                c.setPos(attr[4]);
                c.setNe(attr[5]);
                c.setDelexSupertag(attr[6]);
                c.setLexLabelWithoutReplacing(attr[7]);
                if (!attr[8].equals("_")) {
                    c.setType(new ApplyModifyGraphAlgebra.Type(attr[8]));
                }

                c.setHead(Integer.parseInt(attr[9]));
                c.setEdgeLabel(attr[10]);
                c.setAligned(Boolean.valueOf(attr[11]));
                if (attr.length > 12){
                    c.setRange(TokenRange.fromString(attr[12]));
                }

                //System.out.println(c);
                sent.add(c);
            } else {
                sents.add(sent);
                sent = new ConllSentence();
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
     * Reads a CoNLL corpus from a file and returns the list of instances.
     *
     * @param filename
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static List<ConllSentence> readFromFile(String filename) throws FileNotFoundException, IOException, ParseException {
        return read(new FileReader(filename));
    }
    
    
    public void setDependenciesFromAmTerm(Tree<String> amTerm, List<Integer> leafOrderToStringOrder, Function<String,Type> supertagToType) {
        MutableInteger nextLeafPosition = new MutableInteger(0);
        
        // all tokens that are not mentioned in the term will be ignored
        for( ConllEntry e : this ) {
            e.setHead(0);
            e.setEdgeLabel(ConllEntry.IGNORE);
            e.setDelexSupertag(ConllEntry.DEFAULT_NULL);
            e.setType(null);
        }
        
        // perform left-to-right DFS over term and assign incoming edges
        int rootPos = amTerm.dfs((Tree<String> node, List<Integer> childrenValues) -> {
            if( childrenValues.isEmpty() ) {
                // leaf
                int leafPosition = nextLeafPosition.incValue();
                int stringPosition = leafOrderToStringOrder.get(leafPosition);
                
                ConllEntry entry = this.get(stringPosition);
                entry.setDelexSupertag(node.getLabel());
                entry.setType(supertagToType.apply(node.getLabel()));
                
                return stringPosition;
            } else {
                assert childrenValues.size() == 2;
                int headStringPosition = childrenValues.get(0);
                int secondaryStringPosition = childrenValues.get(1);
                String edgeLabel = node.getLabel();
                ConllEntry childEntry = this.get(secondaryStringPosition);
                
                childEntry.setEdgeLabel(edgeLabel);
                childEntry.setHead(headStringPosition+1);  // convert 0-based (in array) to 1-based (in CoNLL file)
                
                return headStringPosition;
            }
        });
        
        // mark root token as root
        ConllEntry rootEntry = this.get(rootPos);
        rootEntry.setHead(0);
        rootEntry.setEdgeLabel(ConllEntry.ROOT_SYM);
    }

    public String getId() {
        if( getAttr("id") != null ) {
            return getAttr("id");
        } else {
            return "#NO-ID";
        }
    }
}
