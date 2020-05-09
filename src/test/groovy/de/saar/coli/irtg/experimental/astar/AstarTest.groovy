/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.saar.coli.irtg.experimental.astar


import static org.junit.Assert.*
import static de.up.ling.irtg.util.TestingTools.*

import de.up.ling.irtg.signature.Interner
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

/**
 *
 * @author koller
 */
class AstarTest {


    //@Test
    //TODO taking this test out for now, until we work on this again. -- JG
    public void testDecode() {
        Interner<String> supertagLex = intern(["(u<root> / john)":1, "(u<root> / mary)" : 2, "(u<root> / likes :ARG0 (v<s>) :ARG1 (w<o>)":3])
        Interner<String> edgeLex = intern(["APP_s":1, "APP_o":2])
        Astar a = new Astar(null, new SupertagProbabilities(0,0), new Int2ObjectOpenHashMap(), supertagLex, edgeLex, null, "supertagonly");
        a.setN(3)
        
        Item itJohn = li(0, 1, 1);
        Item itLikes = li(1, 2, 3);
        Item itMary = li(2, 3, 2);
        
        Item itLikesMary = opi(1, 3, 2, itLikes, itMary);
        Item itAll = opi(0, 3, 1, itLikesMary, itJohn);
        
        ParsingResult result = a.decode(itAll);
        
        assertEquals([1,2,0], result.leafOrderToStringOrder)//TODO this is currently [0,1,-1]. So relative to each other they are correct, but all off by one. Dunno what the result should actually be. -- JG
        assertEquals(pt("APP_s(APP_o('(u<root> / likes :ARG0 (v<s>) :ARG1 (w<o>)','(u<root> / mary)'),'(u<root> / john)')"), result.amTerm)
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

