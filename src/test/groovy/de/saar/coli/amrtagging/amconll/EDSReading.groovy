package de.saar.coli.amrtagging.amconll

import com.google.common.collect.Lists
import de.saar.coli.amrtagging.AmConllSentence
import de.saar.coli.amrtagging.TokenRange
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertEquals


class EDSReading {

    @Test
    public void testEDS1(){
        AmConllSentence edsSent = AmConllSentence.read(new StringReader(eds1)).get(0)
        assertEquals(ranges,edsSent.ranges());
        //check some head
        assertEquals(3,edsSent.get(3).getHead());
        assertEquals('card__carg=$REPL$',edsSent.get(5).getLexLabel());
        //check if relexicalization works
        assertEquals("card__carg=1000000",edsSent.get(5).getReLexLabel());


    }

    @Test
    public void testEDS2(){
        //second sentence
        AmConllSentence edsSent = AmConllSentence.read(new StringReader(eds2)).get(0)
        assertEquals(ranges,edsSent.ranges());


    }

    @Test
    public void testEDS3(){
        //third sentence
        AmConllSentence edsSent = AmConllSentence.read(new StringReader(eds3)).get(0)
        assertEquals(ranges,edsSent.ranges());
        assertEquals("bla",edsSent.get(0).getFurtherAttribute("Something"))
    }




    final List<TokenRange> ranges = Lists.newArrayList(new TokenRange(0,3), new TokenRange(4,10), new TokenRange(11,17), new TokenRange(18,19),
            new TokenRange(19,21),new TokenRange(22,29),new TokenRange(30,32),new TokenRange(33,35),new TokenRange(36,40),
            new TokenRange(41,46),new TokenRange(45,46),new TokenRange(47,55));

    final String eds1 = '''#framework:eds
#raw:CBS Sports earned $50 million or so last year. ART-ROOT
#id:21057133
#time:2019-04-10 (20:22)
#version:0.9
1\tCBS\t_\tcbs\tNNP\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2-of (n1 / compound  :lnk (an1 / COMPLEX)  :ARG1 (n4<s>))  :BV-of (n2 / proper_q  :lnk (an2 / SIMPLE)))\tnamed__carg=$FORM$\t(s())\t2\tMOD_s\ttrue\t0:3
2\tSports\t_\tsports\tNNPS\t_\t(n8<root> / --LEX--  :lnk (an8 / SIMPLE)  :BV-of (n4 / proper_q  :lnk (an4 / COMPLEX)))\tnamed__carg=$FORM$\t()\t3\tAPP_s\ttrue\t4:10
3\tearned\t_\tearn\tVBD\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))\t_$LEMMA$_v_1\t(o(), s())\t12\tAPP_art-snt1\ttrue\t11:17
4\t$\tdollar\t$\t$\t_\t(n2<root> / --LEX--  :lnk (an2 / SIMPLE)  :BV-of (n0 / udef_q  :lnk (an0 / COMPLEX)))\t_$REPL$_n_1\t()\t3\tAPP_o\ttrue\t18:19
5\t50\t_\t50\tCD\t_\t(n11<root> / --LEX--  :lnk (an11 / SIMPLE))\tcard__carg=$LEMMA$\t()\t6\tAPP_o\ttrue\t19:21
6\tmillion\t1000000\tmillion\tCD\t_\t(n9<root> / --LEX--  :lnk (an9 / SIMPLE)  :ARG1 (n7<s>)  :ARG3-of (n10 / times  :lnk (an10 / SIMPLE)  :ARG2 (n8<o>)))\tcard__carg=$REPL$\t(o(), s())\t4\tMOD_s\ttrue\t22:29
7\tor\t_\tor\tCC\t_\t_\t_\t_\t0\tIGNORE\ttrue\t30:32
8\tso\t_\tso\tRB\t_\t_\t_\t_\t0\tIGNORE\ttrue\t33:35
9\tlast\t_\tlast\tJJ\t_\t(n13<root> / --LEX--  :lnk (an13 / SIMPLE)  :ARG1 (n14<s>  :BV-of (n12 / def_implicit_q  :lnk (an12 / SIMPLE))))\t_$LEMMA$_a_1\t(s())\t10\tMOD_s\ttrue\t36:40
10\tyear\t_\tyear\tNN\t_\t(n14<root> / --LEX--  :lnk (an14 / SIMPLE)  :ARG2-of (n11 / loc_nonsp  :lnk (an11 / COMPLEX)  :ARG1 (n5<s>)))\t_$LEMMA$_n_1\t(s())\t3\tMOD_s\ttrue\t41:46
11\t.\t_\t.\t.\t_\t_\t_\t_\t0\tIGNORE\ttrue\t45:46
12\tART-ROOT\t_\tART-ROOT\tART-ROOT\t_\t(artroot<root> / --LEX--  :lnk (artrootlnk / SIMPLE)  :art-snt1 (n3<art-snt1>))\t$LEMMA$\t(art-snt1())\t0\tROOT\ttrue\t47:55
'''
    final String eds2='''#framework:eds
#raw:CBS Sports earned $50 million or so last year. ART-ROOT
#id:21057133
#time:2019-04-10 (20:22)
#version:0.9
1\tCBS\t_\tcbs\tNNP\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2-of (n1 / compound  :lnk (an1 / COMPLEX)  :ARG1 (n4<s>))  :BV-of (n2 / proper_q  :lnk (an2 / SIMPLE)))\tnamed__carg=$FORM$\t(s())\t2\tMOD_s\ttrue\tTokenRange=0:3
2\tSports\t_\tsports\tNNPS\t_\t(n8<root> / --LEX--  :lnk (an8 / SIMPLE)  :BV-of (n4 / proper_q  :lnk (an4 / COMPLEX)))\tnamed__carg=$FORM$\t()\t3\tAPP_s\ttrue\tTokenRange=4:10
3\tearned\t_\tearn\tVBD\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))\t_$LEMMA$_v_1\t(o(), s())\t12\tAPP_art-snt1\ttrue\tTokenRange=11:17
4\t$\tdollar\t$\t$\t_\t(n2<root> / --LEX--  :lnk (an2 / SIMPLE)  :BV-of (n0 / udef_q  :lnk (an0 / COMPLEX)))\t_$REPL$_n_1\t()\t3\tAPP_o\ttrue\tTokenRange=18:19
5\t50\t_\t50\tCD\t_\t(n11<root> / --LEX--  :lnk (an11 / SIMPLE))\tcard__carg=$LEMMA$\t()\t6\tAPP_o\ttrue\tTokenRange=19:21
6\tmillion\t1000000\tmillion\tCD\t_\t(n9<root> / --LEX--  :lnk (an9 / SIMPLE)  :ARG1 (n7<s>)  :ARG3-of (n10 / times  :lnk (an10 / SIMPLE)  :ARG2 (n8<o>)))\tcard__carg=$REPL$\t(o(), s())\t4\tMOD_s\ttrue\tTokenRange=22:29
7\tor\t_\tor\tCC\t_\t_\t_\t_\t0\tIGNORE\ttrue\tTokenRange=30:32
8\tso\t_\tso\tRB\t_\t_\t_\t_\t0\tIGNORE\ttrue\tTokenRange=33:35
9\tlast\t_\tlast\tJJ\t_\t(n13<root> / --LEX--  :lnk (an13 / SIMPLE)  :ARG1 (n14<s>  :BV-of (n12 / def_implicit_q  :lnk (an12 / SIMPLE))))\t_$LEMMA$_a_1\t(s())\t10\tMOD_s\ttrue\tTokenRange=36:40
10\tyear\t_\tyear\tNN\t_\t(n14<root> / --LEX--  :lnk (an14 / SIMPLE)  :ARG2-of (n11 / loc_nonsp  :lnk (an11 / COMPLEX)  :ARG1 (n5<s>)))\t_$LEMMA$_n_1\t(s())\t3\tMOD_s\ttrue\tTokenRange=41:46
11\t.\t_\t.\t.\t_\t_\t_\t_\t0\tIGNORE\ttrue\tTokenRange=45:46
12\tART-ROOT\t_\tART-ROOT\tART-ROOT\t_\t(artroot<root> / --LEX--  :lnk (artrootlnk / SIMPLE)  :art-snt1 (n3<art-snt1>))\t$LEMMA$\t(art-snt1())\t0\tROOT\ttrue\tTokenRange=47:55
'''
    final String eds3='''#framework:eds
#raw:CBS Sports earned $50 million or so last year. ART-ROOT
#id:21057133
#time:2019-04-10 (20:22)
#version:0.9
1\tCBS\t_\tcbs\tNNP\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2-of (n1 / compound  :lnk (an1 / COMPLEX)  :ARG1 (n4<s>))  :BV-of (n2 / proper_q  :lnk (an2 / SIMPLE)))\tnamed__carg=$FORM$\t(s())\t2\tMOD_s\ttrue\tTokenRange=0:3|Something=bla
2\tSports\t_\tsports\tNNPS\t_\t(n8<root> / --LEX--  :lnk (an8 / SIMPLE)  :BV-of (n4 / proper_q  :lnk (an4 / COMPLEX)))\tnamed__carg=$FORM$\t()\t3\tAPP_s\ttrue\tTokenRange=4:10|Something=bla
3\tearned\t_\tearn\tVBD\t_\t(n3<root> / --LEX--  :lnk (an3 / SIMPLE)  :ARG2 (n8<o>)  :ARG1 (n2<s>))\t_$LEMMA$_v_1\t(o(), s())\t12\tAPP_art-snt1\ttrue\tTokenRange=11:17|Something=bla
4\t$\tdollar\t$\t$\t_\t(n2<root> / --LEX--  :lnk (an2 / SIMPLE)  :BV-of (n0 / udef_q  :lnk (an0 / COMPLEX)))\t_$REPL$_n_1\t()\t3\tAPP_o\ttrue\tTokenRange=18:19|Something=bla
5\t50\t_\t50\tCD\t_\t(n11<root> / --LEX--  :lnk (an11 / SIMPLE))\tcard__carg=$LEMMA$\t()\t6\tAPP_o\ttrue\tTokenRange=19:21|Something=bla
6\tmillion\t1000000\tmillion\tCD\t_\t(n9<root> / --LEX--  :lnk (an9 / SIMPLE)  :ARG1 (n7<s>)  :ARG3-of (n10 / times  :lnk (an10 / SIMPLE)  :ARG2 (n8<o>)))\tcard__carg=$REPL$\t(o(), s())\t4\tMOD_s\ttrue\tTokenRange=22:29|Something=bla
7\tor\t_\tor\tCC\t_\t_\t_\t_\t0\tIGNORE\ttrue\tTokenRange=30:32|Something=bla
8\tso\t_\tso\tRB\t_\t_\t_\t_\t0\tIGNORE\ttrue\tTokenRange=33:35|Something=bla
9\tlast\t_\tlast\tJJ\t_\t(n13<root> / --LEX--  :lnk (an13 / SIMPLE)  :ARG1 (n14<s>  :BV-of (n12 / def_implicit_q  :lnk (an12 / SIMPLE))))\t_$LEMMA$_a_1\t(s())\t10\tMOD_s\ttrue\tTokenRange=36:40
10\tyear\t_\tyear\tNN\t_\t(n14<root> / --LEX--  :lnk (an14 / SIMPLE)  :ARG2-of (n11 / loc_nonsp  :lnk (an11 / COMPLEX)  :ARG1 (n5<s>)))\t_$LEMMA$_n_1\t(s())\t3\tMOD_s\ttrue\tTokenRange=41:46
11\t.\t_\t.\t.\t_\t_\t_\t_\t0\tIGNORE\ttrue\tTokenRange=45:46
12\tART-ROOT\t_\tART-ROOT\tART-ROOT\t_\t(artroot<root> / --LEX--  :lnk (artrootlnk / SIMPLE)  :art-snt1 (n3<art-snt1>))\t$LEMMA$\t(art-snt1())\t0\tROOT\ttrue\tTokenRange=47:55
'''

}
