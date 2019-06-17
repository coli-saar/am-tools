/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.siblingfinder.SiblingFinder;
import de.up.ling.irtg.signature.Interner;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author JG
 */
public abstract class TypeInterner<E> implements Serializable {
    
    protected Interner<E> interner;
    
    protected IntList[][][] pos2edge2state2partners;
    protected int[][][] edge2left2right2result;
    protected Interner<String> edgeLabelLexicon;
    
    public TypeInterner(Interner<String> edgeLabelLexicon) {
        this.edgeLabelLexicon = edgeLabelLexicon;
        this.interner = new Interner();
        this.pos2edge2state2partners = new IntList[2][][];
        this.pos2edge2state2partners[0] = new IntList[edgeLabelLexicon.size()][];
        this.pos2edge2state2partners[1] = new IntList[edgeLabelLexicon.size()][];
        this.edge2left2right2result = new int[edgeLabelLexicon.size()+1][][];
    }
    
    
    public SiblingFinder makeSiblingFinder(int edgeLabelID) {
        return new IntSiblingFinder(interner.size(), edgeLabelID); //NOTE this assumes that the interner IDs are continuous!
    } 
    
    /**
     * Note: Only call if left and right actually exist and combine using edgeLabelID! This can be
     * guaranteed by getting the pair from a sibling finder for edgeLabelID.
     * @param left
     * @param right
     * @param edgeLabelID
     * @return 
     */
    public int combine(int edgeLabelID, int left, int right) {
        return edge2left2right2result[edgeLabelID][left][right];
    }
    
    public int resolveObject(E object) {
        return interner.resolveObject(object);
    }
    
    public E resolveID(int id) {
        return interner.resolveId(id);
    }
    
    private class IntSiblingFinder extends SiblingFinder {

        private final IntList[][] lookup;//first index is pos 0 or 1, second index is stateID for which we want to look up partners. 
        private final int edgeLabelID;
        
        public IntSiblingFinder(int maxStates, int edgeLabelID) {
            super(2);
            lookup = new IntList[2][];
            lookup[0] = new IntList[maxStates];
            lookup[1] = new IntList[maxStates];
            for (int i = 0; i<maxStates; i++) {
                lookup[0][i] = new IntArrayList();
                lookup[1][i] = new IntArrayList();
            }
            this.edgeLabelID = edgeLabelID;
        }

        @Override
        public Iterable<int[]> getPartners(int stateID, int pos) {
            return () -> {
                IntIterator it = lookup[pos][stateID-1].iterator();
                int[] ret = new int[2];
                ret[pos] = stateID;
                int otherPos = (pos+1)%2;
                
                return new Iterator<int[]>(){
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }
                    
                    @Override
                    public int[] next() {
                        ret[otherPos] = it.nextInt();
//                        if (pos == 0) {
//                            System.err.println(edgeLabelLexicon.resolveId(edgeLabelID)+":  "+interner.resolveId(stateID)+" || "+interner.resolveId(ret[otherPos]));
//                        } else {
//                            System.err.println(edgeLabelLexicon.resolveId(edgeLabelID)+":  "+interner.resolveId(ret[otherPos])+" || "+interner.resolveId(stateID));
//                        }
                        return ret;
                    }
                    
                };
            };
        }

        @Override
        protected void performAddState(int stateID, int pos) {
            int otherPos = (pos+1)%2;
            if (stateID >= 1) {
                for (int partner : pos2edge2state2partners[pos][edgeLabelID-1][stateID-1]) {
                    lookup[otherPos][partner-1].add(stateID);
                }
            }
            //else the state does not exist, and we ignore it
        }
        
    }
    
    public static class AMAlgebraTypeInterner extends TypeInterner<Type> implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 
         * @param types
         * @param edgeLabelLexicon 
         */
        public AMAlgebraTypeInterner(Set<Type> types, Interner<String> edgeLabelLexicon) {
            super(edgeLabelLexicon);
            
            //add all types to interner
            types.forEach(t -> t.getAllSubtypes().forEach(t_rec -> interner.addObject(t_rec)));
//            interner.getKnownObjects().forEach(o -> System.err.println(o));
            
            
            //check all possible type operations and store them accordingly
            for (int edgeID : edgeLabelLexicon.getKnownIds()) {
                int edgeID0 = edgeID-1;
                String edgeLabel = edgeLabelLexicon.resolveId(edgeID);
                pos2edge2state2partners[0][edgeID0] = new IntList[interner.size()];
                pos2edge2state2partners[1][edgeID0] = new IntList[interner.size()];
                edge2left2right2result[edgeID] = new int[interner.size()+1][];//these are all one-based
                for (Type t1 : interner.getKnownObjects()) {
                    int id1 = interner.resolveObject(t1);
                    pos2edge2state2partners[0][edgeID0][id1-1] = new IntArrayList();
                    pos2edge2state2partners[1][edgeID0][id1-1] = new IntArrayList();
                    edge2left2right2result[edgeID][id1] = new int[interner.size()+1];
                    for (Type t2 : interner.getKnownObjects()) {
                        int id2 = interner.resolveObject(t2);
                        Type result = Type.evaluateOperation(t1, t2, edgeLabel);
                        if (result != null) {
                            int resultID = interner.resolveObject(result);
                            //System.err.println("adding "+interner.resolveId(id1)+" || "+interner.resolveId(id2)+" --"+edgeLabel+"-> "+interner.resolveId(resultID));
                            pos2edge2state2partners[0][edgeID0][id1-1].add(id2);
                            edge2left2right2result[edgeID][id1][id2] = resultID;
                        }
                        if (Type.evaluateOperation(t2, t1, edgeLabel) != null) {
                            //System.err.println("adding "+interner.resolveId(id2)+" || "+interner.resolveId(id1)+" --"+edgeLabel+"-> ??");
                            pos2edge2state2partners[1][edgeID0][id1-1].add(id2);
                        }
                    }
                }
            }
        }
        
        /**
         * Serializes this type interner to an output stream.
         * 
         * @param os
         * @throws IOException 
         */
        public void save(OutputStream os) throws IOException {
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(this);
            oos.flush();
        }
        
        /**
         * Deserializes a type interner from an input stream.
         * This assumes that a type interner was previously saved to
         * the file backing this input stream using {@link #save(java.io.OutputStream) }.
         * 
         * @param is
         * @return
         * @throws IOException
         * @throws ClassNotFoundException 
         */
        public static AMAlgebraTypeInterner read(InputStream is) throws IOException, ClassNotFoundException {
            ObjectInputStream ois = new ObjectInputStream(is);
            Object x = ois.readObject();
            
            if( x instanceof AMAlgebraTypeInterner ) {
                return (AMAlgebraTypeInterner) x;
            } else {
                throw new ClassNotFoundException("Serialized object is not an AMAlgebraTypeInterner, but " + x.getClass());
            }
        }
        
    }
    
}
