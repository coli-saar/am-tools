/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.saar.coli.amrtagging.amconll

import de.saar.coli.amrtagging.AmConllEntry
import de.saar.coli.amrtagging.AmConllSentence

import static org.junit.Assert.*
import org.junit.*
import static de.up.ling.irtg.util.TestingTools.*;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type
import de.up.ling.tree.Tree


/**
 * This entire test suite needs to be redone, because setDependenciesFromAmTerm now takes
 * a Tree<Or<String,SupertagWithType>> as input. The essential test cases should still work,
 * but both the tree construction and the assertIncoming need to be changed.
 *
 * @author koller
 */
class AmConllSentenceTest {
    /*
    @Test
    public void testAmTermToConllSentence() {
        AmConllSentence sent = s([e(1, "John"), e(2, "likes"), e(3, "Mary")]);
        Tree<String> amterm = pt("APP_s(APP_o(xxlikes, xxmary), xxjohn)");
        List leafOrderToStringOrder = [1, 2, 0];
        Map stToType = ["xxlikes": new Type("(s(),o())"), "xxmary": new Type("()"), "xxjohn": new Type("()")];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder, { st -> stToType[st] });
        
        assertIncoming(sent[0], 2, "APP_s", "xxjohn")
        assertIncoming(sent[1], 0, AmConllEntry.ROOT_SYM, "xxlikes")
        assertIncoming(sent[2], 2, "APP_o", "xxmary")
    }
    
    @Test
    public void testComplexAmTermToConllSentence() {
        AmConllSentence sent = s([e(1, "John"), e(2, "thinks"), e(3, "that"), e(4, "Mary"), e(5, "sleeps")]);
        Tree<String> amterm = pt("APP_s(APP_v(xxthinks, APP_s(xxsleeps, xxMary)), xxJohn)")
        List leafOrderToStringOrder = [1, 4, 3, 0];
        Map stToType = ["xxthinks": new Type("(s(),v())"), "xxMary": new Type("()"), "xxJohn": new Type("()"), "xxsleeps": new Type("(s())")];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder, { st -> stToType[st] });
        
        assertIncoming(sent[0], 2, "APP_s", "xxJohn")
        assertIncoming(sent[1], 0, AmConllEntry.ROOT_SYM, "xxthinks")
        assertIncoming(sent[2], 0, AmConllEntry.IGNORE, AmConllEntry.DEFAULT_NULL)
        assertIncoming(sent[3], 5, "APP_s", "xxMary")
        assertIncoming(sent[4], 2, "APP_v", "xxsleeps")
    }
    
    @Test
    public void testEncodeDecode() {
        List<AmConllSentence> sents = AmConllSentence.read(new StringReader(AMCONLL));
        StringWriter w = new StringWriter();
        AmConllSentence.write(w, sents);
        w.close()
        
        String result = w.toString().trim();
        result = result.replaceAll("true", "True");
        
        assertEquals(AMCONLL.trim(), result);
    }
    
    @Test
    public void testAmTermInfoLoss() {
        List<AmConllSentence> sents = AmConllSentence.read(new StringReader(AMCONLL));
        
        AmConllEntry e = sents.get(0).get(0);
        int oldHead = e.getHead();
        String oldEdgeLabel = e.getEdgeLabel();
        String oldSupertag = e.getDelexSupertag();
        e.setHead(999);
        e.setEdgeLabel("foo");
        e.setDelexSupertag("hello supertag with space");
        
        StringWriter w = new StringWriter();
        AmConllSentence.write(w, sents);
        w.close()
        
        String result = w.toString().trim();
        result = result.replaceAll("true", "True");
        result = result.replaceAll("999", Integer.toString(oldHead));
        result = result.replaceAll("foo", oldEdgeLabel);
        result = result.replaceAll("hello supertag with space", oldSupertag);
        
        assertEquals(AMCONLL.trim(), result)
    }
    
    public void assertIncoming(AmConllEntry entry, int expectedHead, String expectedEdgeLabel, String expectedSupertag) {
        assertEquals(expectedHead, entry.getHead());
        assertEquals(expectedEdgeLabel, entry.getEdgeLabel());
        assertEquals(expectedSupertag, entry.getDelexSupertag());
    }
    
    public AmConllSentence s(List<AmConllEntry> entries) {
        AmConllSentence ret = new AmConllSentence();
        ret.addAll(entries);
        return ret;
    }
    
    public AmConllEntry e(int pos, String form) {
        return new AmConllEntry(pos, form);
    }
    
    private static final String AMCONLL = '''#id:#22000001
1	Rockwell	_	_generic_proper_ne_	NNP	ORGANIZATION	_	~~named:x-c	_	0	_	True
2	International	_	_generic_proper_ne_	NNP	ORGANIZATION	_	~~named:x-c	_	0	_	True
3	Corp.	_	corp.	NNP	ORGANIZATION	_	~~n:x	_	0	_	True
4	’s	_	’s	VBZ	O	_	_	_	0	_	True
5	Tulsa	_	_generic_proper_ne_	NNP	CITY	_	~~named:x-c	_	0	_	True
6	unit	_	unit	NN	O	_	~~n_of:x-i	_	0	_	True
7	said	_	say	VBD	O	_	~~v_to:e-i-h-i	_	0	_	True
8	it	_	it	PRP	O	_	~~pron:x	_	0	_	True
9	signed	_	sign	VBD	O	_	~~v:e-i-p	_	0	_	True
10	a	_	a	DT	O	_	~~q:i-h-h	_	0	_	True
11	tentative	_	tentative	JJ	O	_	~~a:e-p	_	0	_	True
12	agreement	_	agreement	NN	O	_	~~n:x	_	0	_	True
13	extending	_	extend	VBG	O	_	~~v:e-i-p	_	0	_	True
14	its	_	its	PRP$	O	_	~~q:i-h-h	_	0	_	True
15	contract	_	contract	NN	O	_	~~n:x-h	_	0	_	True
16	with	_	with	IN	O	_	~~p:e-u-i	_	0	_	True
17	Boeing	_	Boeing	NNP	ORGANIZATION	_	~~named:x-c	_	0	_	True
18	Co.	_	co.	NNP	ORGANIZATION	_	~~n:x	_	0	_	True
19	to	_	to	TO	O	_	_	_	0	_	True
20	provide	_	provide	VB	O	_	~~v:e-i-p	_	0	_	True
21	structural	_	structural	JJ	O	_	~~a:e-p	_	0	_	True
22	parts	_	part	NNS	O	_	~~n:x	_	0	_	True
23	for	_	for	IN	O	_	~~p:e-u-i	_	0	_	True
24	Boeing	_	Boeing	NNP	ORGANIZATION	_	~~named:x-c	_	0	_	True
25	’s	_	’s	VBZ	O	_	_	_	0	_	True
26	747	_	_generic_card_ne_	CD	NUMBER	_	~~card:i-i-c	_	0	_	True
27	jetliners	_	_generic_nns_	NNS	O	_	~~n:x	_	0	_	True
28	.	_	_	.	O	_	_	_	0	_	True
29	ART-ROOT	_	ART-ROOT	ART-ROOT	ART-ROOT	_	$LEMMA$	_	0	_	True

''';

     */
}

