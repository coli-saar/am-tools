/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.saar.coli.irtg.experimental.astar


import static org.junit.Assert.*
import org.junit.*
import static de.up.ling.irtg.util.TestingTools.*;
import de.up.ling.irtg.signature.Interner
import de.up.ling.irtg.signature.Signature
import de.up.ling.irtg.util.MutableInteger
import de.up.ling.tree.Tree
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList


/**
 *
 * @author koller
 */
class AstarTest {
    @Test
    public void testDecode() {
        Interner<String> supertagLex = intern(["(u<root> / john)":1, "(u<root> / mary)" : 2, "(u<root> / likes :ARG0 (v<s>) :ARG1 (w<o>)":3])
        Interner<String> edgeLex = intern(["APP_s":1, "APP_o":2])
        Astar a = new Astar(null, new SupertagProbabilities(0,0), new Int2ObjectOpenHashMap(), supertagLex, edgeLex, null);
        // public Astar(EdgeProbabilities edgep, SupertagProbabilities tagp, Int2ObjectMap<Pair<SGraph, Type>> idToAsGraph, Interner<String> supertagLexicon, Interner<String> edgeLabelLexicon, AMAlgebraTypeInterner typeLexicon) {
        
        Item itJohn = li(0, 1, 1);
        Item itLikes = li(1, 2, 3);
        Item itMary = li(2, 3, 2);
        
        Item itLikesMary = opi(1, 3, 2, itLikes, itMary);
        Item itAll = opi(0, 3, 1, itLikesMary, itJohn);
        
        IntList leafOrderToStringOrder = new IntArrayList(3);
        for( int i = 0; i < 3; i++ ) {
            leafOrderToStringOrder.add(0);
        }
        
        Tree<String> amTerm = a.decode(itAll, 0, leafOrderToStringOrder, new MutableInteger(0));
        
        assertEquals([1,2,0], leafOrderToStringOrder)
        assertEquals("APP_s(APP_o('(u<root> / likes :ARG0 (v<s>) :ARG1 (w<o>)','(u<root> / mary)'),'(u<root> / john)')", amTerm.toString())
//        System.err.println(amTerm)
    }
    
    private Interner<String> intern(Map<String,Integer> symbols) {
        Interner<String> ret = new Interner<String>();
        ret.setTrustingMode(true);
        
        for (String sym : symbols.keySet()) {
            ret.addObjectWithIndex(symbols.get(sym), sym);
        }
        
        return ret;
    }
    
    
    private Item li(int start, int end, int op) {
        Item ret = new Item(start,end,start,0,0);
        ret.setCreatedBySupertag(op)
        return ret
    }
    
    private Item opi(int start, int end, int op, Item left, Item right) {
        Item ret = new Item(start,end,start,0,0);
        ret.setCreatedByOperation(op, left, right)
        return ret;
    }
}

/*
        SGraph g1 = pg("(w<root> / want-01  :ARG0 (b/b1)  :ARG1 (g/g))")
        SGraph g2 = pg("(g<root> :ARG2 (b/b2))")
        SGraph gold = pg("(w<root> / want-01 :ARG0 (b/b1)  :ARG1 (g/g) :ARG2 (b2/b2))")

*/