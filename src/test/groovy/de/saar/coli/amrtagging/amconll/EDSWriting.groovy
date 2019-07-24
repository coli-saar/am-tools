package de.saar.coli.amrtagging.amconll

import com.google.common.collect.Lists
import de.saar.coli.amrtagging.AmConllEntry
import de.saar.coli.amrtagging.AmConllSentence
import de.saar.coli.amrtagging.SupertagDictionary
import de.saar.coli.amrtagging.TokenRange
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra
import org.junit.Test

import static org.junit.Assert.assertArrayEquals
import static org.junit.Assert.assertEquals

class EDSWriting {

    private final SupertagDictionary dict = new SupertagDictionary();
    @Test
    public void testEDS1(){
        AmConllEntry e = new AmConllEntry(3,"earned");

        e.setSupertag("(n3<root> / _earn_v_1  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))",["n3"].toSet(),dict)
        e.setLemma("earn")
        e.setAligned(true)
        e.setPos("VBD")
        e.setType(new ApplyModifyGraphAlgebra.Type("(o(), s())"))
        e.setHead(12)
        e.setEdgeLabel("APP_art-snt1")

        assertEquals(eds1,e.toString())

    }

    @Test
    public void testEDS2(){
        AmConllEntry e = new AmConllEntry(3,"earned");

        e.setSupertag("(n3<root> / _earn_v_1  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))",["n3"].toSet(),dict)
        e.setLemma("earn")
        e.setAligned(true)
        e.setPos("VBD")
        e.setType(new ApplyModifyGraphAlgebra.Type("(o(), s())"))
        e.setHead(12)
        e.setEdgeLabel("APP_art-snt1")
        e.setRange(new TokenRange(11,17))

        assertEquals(eds2,e.toString())

    }

    @Test
    public void testEDS3(){
        AmConllEntry e = new AmConllEntry(3,"earned");

        e.setSupertag("(n3<root> / _earn_v_1  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))",["n3"].toSet(),dict)
        e.setLemma("earn")
        e.setAligned(true)
        e.setPos("VBD")
        e.setType(new ApplyModifyGraphAlgebra.Type("(o(), s())"))
        e.setHead(12)
        e.setEdgeLabel("APP_art-snt1")
        e.setRange(new TokenRange(11,17))
        e.setFurtherAttribute("Something","blub")

        assertEquals(eds3,e.toString())

    }


    final String eds1 = '''3\tearned\t_\tearn\tVBD\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))\t_$LEMMA$_v_1\t(o(), s())\t12\tAPP_art-snt1\ttrue'''

    final String eds2 = '''3\tearned\t_\tearn\tVBD\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))\t_$LEMMA$_v_1\t(o(), s())\t12\tAPP_art-snt1\ttrue\t11:17'''

    final String eds3 = '''3\tearned\t_\tearn\tVBD\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))\t_$LEMMA$_v_1\t(o(), s())\t12\tAPP_art-snt1\ttrue\tSomething=blub|TokenRange=11:17'''


}
